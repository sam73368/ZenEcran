package com.zenecran.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.zenecran.data.Config
import com.zenecran.data.FeatureDef
import com.zenecran.data.Presets
import com.zenecran.data.Store
import com.zenecran.data.UsageRepo
import com.zenecran.data.fmtClock
import com.zenecran.data.fmtMin
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private sealed interface Reason {
    data class Limit(val limitMin: Int, val usedMin: Int) : Reason
    data class Window(val name: String, val endMin: Int, val allDay: Boolean) : Reason
    data class Focus(val untilMs: Long) : Reason
}

/**
 * Cœur de l'appli : tourne en arrière-plan comme service d'accessibilité.
 *  - repère l'appli au premier plan
 *  - bloque une appli si : limite quotidienne atteinte, plage horaire interdite, ou mode concentration
 *  - repère Reels / Spotlight / Shorts et les bloque (ou compte un budget de minutes)
 */
class GuardService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var overlay: BlockOverlay? = null

    private var fgPkg: String? = null
    private var ignored: Set<String> = setOf("com.android.systemui", "android")
    private var protectedPkgs: Set<String> = emptySet()
    private var ignoredAt = 0L

    @Volatile private var usage: Map<String, Long> = emptyMap()
    @Volatile private var usageBusy = false
    private var usageAt = 0L

    private var lastEnforce = 0L
    private var lastFlush = 0L
    private var lastDump = 0L

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (t: Throwable) {
                Log.e(TAG, "tick", t)
            }
            main.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        overlay = BlockOverlay(this)
        refreshIgnored()
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        main.removeCallbacks(ticker)
        overlay?.hide()
        Store.flushDaily()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg in ignored) return
        if (pkg != fgPkg) {
            fgPkg = pkg
            tick()
        }
    }

    private fun refreshIgnored() {
        try {
            val imm = getSystemService(InputMethodManager::class.java)
            val imes = imm.enabledInputMethodList.map { it.packageName }
            ignored = imes.toSet() + setOf("com.android.systemui", "android")
        } catch (_: Exception) {
        }
        protectedPkgs = UsageRepo.protectedPackages(this)
        ignoredAt = SystemClock.elapsedRealtime()
    }

    // ---------------------------------------------------------------- boucle 1 s

    private fun tick() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - ignoredAt > 60_000) refreshIgnored()
        if (nowElapsed - lastFlush > 10_000) {
            Store.flushDaily()
            lastFlush = nowElapsed
        }

        val pkg = fgPkg ?: return
        if (pkg in protectedPkgs) return
        val cfg = Store.config.value
        val now = System.currentTimeMillis()

        refreshUsageIfNeeded(nowElapsed)

        if (cfg.diagnostic && nowElapsed - lastDump > 3000) {
            lastDump = nowElapsed
            rootInActiveWindow?.let { Store.setDump(Detector.dump(it)) }
        }

        // 1) Blocage de l'appli entière
        val reason = reasonFor(pkg, cfg, now)
        if (reason != null) {
            blockApp(pkg, reason)
            return
        }

        // 2) Blocage d'une fonctionnalité à l'intérieur de l'appli
        scanFeatures(pkg, cfg)
    }

    private fun refreshUsageIfNeeded(nowElapsed: Long) {
        if (usageBusy || nowElapsed - usageAt < 5000) return
        usageBusy = true
        usageAt = nowElapsed
        val ctx = applicationContext
        worker.execute {
            try {
                usage = UsageRepo.perApp(ctx, UsageRepo.dayStart(LocalDate.now()), System.currentTimeMillis())
            } catch (_: Throwable) {
            } finally {
                usageBusy = false
            }
        }
    }

    private fun reasonFor(pkg: String, cfg: Config, now: Long): Reason? {
        if (!cfg.isMonitored(pkg)) return null
        if (cfg.isFocus(now)) return Reason.Focus(cfg.focusUntil)

        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dow = LocalDate.now().dayOfWeek.value
        cfg.activeSchedule(pkg, dow, minute)?.let {
            return Reason.Window(it.name, it.endMin, it.startMin == it.endMin)
        }

        val limit = cfg.limits[pkg] ?: 0
        if (limit > 0) {
            val extras = Store.daily().extras[pkg] ?: 0
            val allowedMin = limit + extras * 5
            val used = usage[pkg] ?: 0L
            if (used >= allowedMin * 60_000L) return Reason.Limit(allowedMin, (used / 60_000L).toInt())
        }
        return null
    }

    // ---------------------------------------------------------------- blocage appli

    private fun blockApp(pkg: String, reason: Reason) {
        if (overlay?.isShowing == true) return
        val label = labelOf(pkg)
        val cfg = Store.config.value
        val extras = Store.daily().extras[pkg] ?: 0

        val title: String
        val detail: String
        var used = ""
        when (reason) {
            is Reason.Limit -> {
                title = "Limite atteinte"
                detail = "Tu as atteint ta limite de ${fmtMin(reason.limitMin)} sur $label aujourd'hui."
                used = "Temps passé : ${fmtMin(reason.usedMin)}"
            }
            is Reason.Window -> {
                title = "$label est bloqué"
                detail = if (reason.allDay) {
                    "Plage « ${reason.name} » : accès interdit toute la journée."
                } else {
                    "Plage « ${reason.name} » : accès interdit jusqu'à ${fmtClock(reason.endMin)}."
                }
            }
            is Reason.Focus -> {
                val left = ((reason.untilMs - System.currentTimeMillis()) / 60_000L).toInt() + 1
                title = "Mode concentration"
                detail = "$label est bloqué encore ${fmtMin(maxOf(left, 1))}."
            }
        }
        val canExtend = reason is Reason.Limit && !cfg.isStrict() && extras < 2

        performGlobalAction(GLOBAL_ACTION_HOME)
        fgPkg = null
        overlay?.show(
            BlockInfo(
                appLabel = label,
                pkg = pkg,
                title = title,
                detail = detail,
                usedLine = used,
                canExtend = canExtend,
                onExtend = {
                    Store.updateDaily { d -> d.copy(extras = d.extras + (pkg to ((d.extras[pkg] ?: 0) + 1))) }
                },
            )
        )
    }

    // ---------------------------------------------------------------- blocage fonctionnalité

    private fun scanFeatures(pkg: String, cfg: Config) {
        val defs = Presets.features.filter { it.pkg == pkg && cfg.rules[it.id]?.enabled == true }
        if (defs.isEmpty()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val hits = Detector.detect(root, defs)
        for (d in hits) {
            val rc = cfg.rules[d.id] ?: continue
            val used = Store.daily().featureSec[d.id] ?: 0
            if (rc.budgetMin == 0 || used >= rc.budgetMin * 60) {
                enforceFeature(d, rc.budgetMin == 0)
                return
            }
            Store.updateDaily(persist = false) { day ->
                day.copy(featureSec = day.featureSec + (d.id to ((day.featureSec[d.id] ?: 0) + 1)))
            }
        }
    }

    private fun enforceFeature(d: FeatureDef, hard: Boolean) {
        val now = SystemClock.elapsedRealtime()
        // Si le « retour » n'a pas suffi, on renvoie à l'accueil.
        val repeated = now - lastEnforce < 4000
        lastEnforce = now
        performGlobalAction(if (repeated) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        val appName = Presets.appLabel(d.pkg) ?: d.pkg
        val msg = if (hard) "${d.title} est bloqué sur $appName" else "Budget ${d.title} épuisé pour aujourd'hui"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun labelOf(pkg: String): String = try {
        @Suppress("DEPRECATION")
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        Presets.appLabel(pkg) ?: pkg
    }

    companion object {
        private const val TAG = "ZenEcran"
    }
}
