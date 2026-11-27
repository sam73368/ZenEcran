package com.zenecran.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.zenecran.data.Config
import com.zenecran.data.FeatureDef
import com.zenecran.data.Presets
import com.zenecran.data.Store
import com.zenecran.data.UsageRepo
import com.zenecran.data.fmtClock
import com.zenecran.data.fmtMin
import com.zenecran.ui.Z
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
 *  - repère l'appli réellement au premier plan (fenêtre d'application qui a le focus)
 *  - bloque une appli si : plage horaire interdite, mode concentration, ou limite quotidienne atteinte
 *  - repère Reels / Stories / Spotlight / Shorts et les bloque (ou décompte un budget de minutes)
 */
class GuardService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var overlay: BlockOverlay? = null

    /** Dernier paquet vu dans un événement (secours si la liste des fenêtres est vide). */
    private var eventPkg: String? = null
    private val activityByPkg = HashMap<String, String>()
    private var ignored: Set<String> = setOf("com.android.systemui", "android")
    private var protectedPkgs: Set<String> = emptySet()
    private var listsAt = 0L

    @Volatile private var usage: Map<String, Long> = emptyMap()
    @Volatile private var usageBusy = false
    private var usageAt = 0L

    private val warned = HashSet<String>()
    private var lastFeatureAction = 0L
    private var lastFeatureId: String? = null
    private var lastAppBlock = 0L
    private var lastFlush = 0L
    private var lastDump = 0L
    private var lastFgLog = 0L
    private var lastCountAt = 0L
    private var lastBudgetCount = 0L
    private var scanPending = false

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

    private val quickScan = Runnable {
        scanPending = false
        try {
            val pkg = currentApp() ?: return@Runnable
            if (pkg in protectedPkgs) return@Runnable
            featureStep(pkg, Store.config.value, countBudget = false)
        } catch (t: Throwable) {
            Log.e(TAG, "scan", t)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        Z.apply(Store.config.value)
        overlay = BlockOverlay(this)
        refreshLists()
        main.removeCallbacks(ticker)
        main.post(ticker)
        startWatchdog()
        Log.i(TAG, "SERVICE_CONNECTED")
    }

    override fun onInterrupt() {}

    @Volatile private var alive = true

    /** Chien de garde : si la boucle principale ne tourne plus, note où elle est bloquée et la relance. */
    private fun startWatchdog() {
        val t = Thread {
            while (alive) {
                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val stale = SystemClock.elapsedRealtime() - Store.beat.value
                if (alive && stale > 6000) {
                    val stack = Looper.getMainLooper().thread.stackTrace.take(15).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    Log.w(TAG, "WATCHDOG boucle arrêtée depuis ${stale} ms ; fil principal : $stack")
                    main.removeCallbacks(ticker)
                    main.post(ticker)
                }
            }
        }
        t.isDaemon = true
        t.name = "zen-watchdog"
        t.start()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "SERVICE_UNBIND")
        alive = false
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        main.removeCallbacks(ticker)
        main.removeCallbacks(quickScan)
        overlay?.hide()
        Store.flushDaily()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = e.className?.toString()
            if (cls != null && cls.endsWith("Activity")) activityByPkg[pkg] = cls
            if (pkg !in ignored && pkg != packageName) {
                val changed = pkg != eventPkg
                eventPkg = pkg
                if (changed) safeTick()
            }
        }
        // Réagir vite quand le contenu de l'appli change (ouverture d'un Reel, d'une story…)
        if (pkg in protectedPkgs || pkg in ignored) return
        val cfg = Store.config.value
        if (cfg.rules.values.none { it.enabled }) return
        if (!scanPending) {
            scanPending = true
            main.postDelayed(quickScan, 250)
        }
    }

    private fun safeTick() {
        try {
            tick()
        } catch (t: Throwable) {
            Log.e(TAG, "tick", t)
        }
    }

    private fun refreshLists() {
        try {
            val imm = getSystemService(InputMethodManager::class.java)
            val imes = imm.enabledInputMethodList.map { it.packageName }
            ignored = imes.toSet() + setOf("com.android.systemui", "android")
        } catch (_: Exception) {
        }
        protectedPkgs = UsageRepo.protectedPackages(this)
        listsAt = SystemClock.elapsedRealtime()
    }

    // ---------------------------------------------------------------- appli au premier plan

    private fun appWindows(): List<AccessibilityWindowInfo> = try {
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
    } catch (e: Exception) {
        emptyList()
    }

    /** Appli que l'utilisateur utilise vraiment : la fenêtre d'application qui a le focus. */
    private fun currentApp(): String? {
        val apps = appWindows()
        val w = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive }
        val p = try {
            w?.root?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return p ?: eventPkg
    }

    private fun isVisible(pkg: String): Boolean = appWindows().any {
        try {
            it.root?.packageName?.toString() == pkg
        } catch (e: Exception) {
            false
        }
    }

    private fun screenFor(pkg: String, withActivity: Boolean): ScreenCtx {
        val wins = appWindows()
        val roots = ArrayList<AccessibilityNodeInfo>()
        val titles = ArrayList<String>()
        for (w in wins) {
            val r = try {
                w.root
            } catch (e: Exception) {
                null
            } ?: continue
            if (r.packageName?.toString() == pkg) {
                roots += r
                w.title?.toString()?.let { titles += it }
            }
        }
        if (roots.isEmpty()) {
            rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }?.let { roots += it }
        }
        var activity = activityByPkg[pkg]
        if (withActivity) {
            val fromLog = try {
                UsageRepo.lastResumedActivity(this, pkg, System.currentTimeMillis() - 30 * 60_000L)
            } catch (e: Exception) {
                null
            }
            if (fromLog != null) {
                activity = fromLog
                activityByPkg[pkg] = fromLog
            }
        }
        val dm = resources.displayMetrics
        return ScreenCtx(pkg, roots, activity, titles, dm.widthPixels, dm.heightPixels)
    }

    // ---------------------------------------------------------------- boucle 1 s

    private fun tick() {
        Store.beat()
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            lastCountAt = 0L
            val n = SystemClock.elapsedRealtime()
            if (n - lastFgLog > 5000) {
                lastFgLog = n
                Log.i(TAG, "FG écran éteint")
            }
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - listsAt > 60_000) refreshLists()
        if (nowElapsed - lastFlush > 10_000) {
            Store.flushDaily()
            lastFlush = nowElapsed
        }

        val winPkg = currentApp()
        val usagePkg = UsageRepo.lastForeground
        if (nowElapsed - lastFgLog > 5000) {
            lastFgLog = nowElapsed
            Log.i(TAG, "FG fenetre=$winPkg journal=$usagePkg evenement=$eventPkg")
        }
        val cfg = Store.config.value
        val now = System.currentTimeMillis()
        val winOk = winPkg != null && winPkg !in protectedPkgs && winPkg != packageName

        // Compteur interne du temps au premier plan (le journal d'Android peut avoir des minutes de retard).
        val delta = if (lastCountAt == 0L) 0L else (nowElapsed - lastCountAt).coerceIn(0L, 2000L)
        lastCountAt = nowElapsed
        if (winOk && delta > 0 && overlay?.isShowing != true) {
            val p = winPkg!!
            Store.updateDaily(persist = false) { d -> d.copy(appMs = d.appMs + (p to ((d.appMs[p] ?: 0) + delta.toInt()))) }
        }

        // Le temps d'écran se rafraîchit même si l'appli devant n'est pas reconnue (sinon les limites ne bougent plus).
        refreshUsageIfNeeded(nowElapsed, if (winOk) winPkg!! else usagePkg ?: "", cfg)

        // 1) Blocage de l'appli entière : l'appli devant, ou celle que le journal d'Android voit au premier plan
        //    (si une petite fenêtre d'une autre appli a le focus par-dessus).
        if (winOk) reasonFor(winPkg!!, cfg, now)?.let { blockApp(winPkg, it); return }
        if (usagePkg != null && usagePkg != winPkg && usagePkg !in protectedPkgs && usagePkg != packageName &&
            isVisible(usagePkg)
        ) {
            reasonFor(usagePkg, cfg, now)?.let { blockApp(usagePkg, it); return }
        }
        if (!winOk) return
        val pkg = winPkg!!

        if (cfg.diagnostic && nowElapsed - lastDump > 2500) {
            lastDump = nowElapsed
            val d = Detector.dump(screenFor(pkg, withActivity = true))
            if (d != Store.dumps.value[pkg]) {
                Store.setDump(pkg, d)
                d.lines().take(200).forEach { Log.i(TAG, "DUMP $it") }
            }
        }

        // 2) Blocage d'une fonctionnalité à l'intérieur de l'appli
        featureStep(pkg, cfg, countBudget = true)
    }

    private fun refreshUsageIfNeeded(nowElapsed: Long, fg: String, cfg: Config) {
        if (usageBusy || nowElapsed - usageAt < 4000) return
        usageBusy = true
        usageAt = nowElapsed
        val ctx = applicationContext
        val log = fg.isNotEmpty() && cfg.isMonitored(fg)
        worker.execute {
            try {
                usage = UsageRepo.perApp(ctx, UsageRepo.dayStart(LocalDate.now()), System.currentTimeMillis())
                if (log) Log.i(TAG, "USAGE pkg=$fg journal=${usage[fg] ?: 0L} interne=${Store.daily().appMs[fg] ?: 0}")
            } catch (t: Throwable) {
                Log.e(TAG, "usage", t)
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
            val allowedMin = limit + extras * cfg.extMinutes
            val used = maxOf(usage[pkg] ?: 0L, (Store.daily().appMs[pkg] ?: 0).toLong())
            if (used >= allowedMin * 60_000L) return Reason.Limit(allowedMin, (used / 60_000L).toInt())
            warnIfClose(pkg, cfg, allowedMin, used)
        }
        return null
    }

    /** Petit message quand il ne reste que quelques minutes (une seule fois par palier). */
    private fun warnIfClose(pkg: String, cfg: Config, allowedMin: Int, used: Long) {
        if (cfg.warnMin <= 0) return
        val remaining = allowedMin * 60_000L - used
        if (remaining <= 0 || remaining > cfg.warnMin * 60_000L) return
        val key = "${Store.today()}|$pkg|$allowedMin"
        if (warned.add(key)) {
            val left = ((remaining + 59_999L) / 60_000L).toInt()
            Toast.makeText(this, "Plus que ${fmtMin(left)} sur ${labelOf(pkg)}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- blocage appli

    private fun blockApp(pkg: String, reason: Reason) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (overlay?.isShowing == true) {
            // L'écran de blocage est déjà là : on renvoie quand même à l'accueil si l'appli est revenue devant.
            if (nowElapsed - lastAppBlock > 1500) {
                lastAppBlock = nowElapsed
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }
        lastAppBlock = nowElapsed
        val label = labelOf(pkg)
        val cfg = Store.config.value
        val extras = Store.daily().extras[pkg] ?: 0

        val title: String
        val detail: String
        var used = ""
        val tag: String
        when (reason) {
            is Reason.Limit -> {
                tag = "limit"
                title = "Limite atteinte"
                detail = "Tu as atteint ta limite de ${fmtMin(reason.limitMin)} sur $label aujourd'hui."
                used = "Temps passé : ${fmtMin(reason.usedMin)}"
            }
            is Reason.Window -> {
                tag = "schedule"
                title = "$label est bloqué"
                detail = if (reason.allDay) {
                    "Plage « ${reason.name} » : accès interdit toute la journée."
                } else {
                    "Plage « ${reason.name} » : accès interdit jusqu'à ${fmtClock(reason.endMin)}."
                }
            }
            is Reason.Focus -> {
                tag = "focus"
                val left = ((reason.untilMs - System.currentTimeMillis()) / 60_000L).toInt() + 1
                title = "Mode concentration"
                detail = "$label est bloqué encore ${fmtMin(maxOf(left, 1))}."
            }
        }
        Log.i(TAG, "BLOCK_APP pkg=$pkg reason=$tag")
        val canExtend = reason is Reason.Limit && !cfg.isStrict() && extras < cfg.extMax

        performGlobalAction(GLOBAL_ACTION_HOME)
        eventPkg = null
        val ok = overlay?.show(
            BlockInfo(
                appLabel = label,
                pkg = pkg,
                title = title,
                detail = detail,
                usedLine = used,
                canExtend = canExtend,
                extMinutes = cfg.extMinutes,
                waitSec = cfg.extWaitSec,
                quote = cfg.quote,
                onExtend = {
                    Store.updateDaily { d -> d.copy(extras = d.extras + (pkg to ((d.extras[pkg] ?: 0) + 1))) }
                },
            )
        ) ?: false
        if (!ok) Toast.makeText(this, "$title — $detail", Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------- blocage fonctionnalité

    private fun featureStep(pkg: String, cfg: Config, countBudget: Boolean) {
        val defs = cfg.allFeatures().filter { it.pkg == pkg && cfg.rules[it.id]?.enabled == true }
        if (defs.isEmpty()) return
        val nowElapsed = SystemClock.elapsedRealtime()
        // Laisse le temps au « retour » de faire son effet avant de re-vérifier.
        if (nowElapsed - lastFeatureAction < 1200) return
        val needActivity = countBudget && defs.any { it.activities.isNotEmpty() }
        val hits = Detector.detect(screenFor(pkg, needActivity), defs)
        if (hits.isEmpty()) return
        for (d in hits) {
            val rc = cfg.rules[d.id] ?: continue
            val used = Store.daily().featureSec[d.id] ?: 0
            if (rc.budgetMin == 0 || used >= rc.budgetMin * 60) {
                enforceFeature(d, rc.budgetMin == 0)
                return
            }
        }
        if (countBudget && nowElapsed - lastBudgetCount >= 950) {
            lastBudgetCount = nowElapsed
            val ids = hits.map { it.id }.toSet()
            Store.updateDaily(persist = false) { day ->
                var f = day.featureSec
                ids.forEach { id -> f = f + (id to ((f[id] ?: 0) + 1)) }
                day.copy(featureSec = f)
            }
        }
    }

    private fun enforceFeature(d: FeatureDef, hard: Boolean) {
        val now = SystemClock.elapsedRealtime()
        // Si le « retour » n'a pas suffi juste avant, on renvoie à l'accueil.
        val repeated = now - lastFeatureAction < 5000 && lastFeatureId == d.id
        val goHome = repeated || !Store.config.value.featureBack
        performGlobalAction(if (goHome) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        lastFeatureAction = now
        lastFeatureId = d.id
        // L'activité mémorisée ne doit pas redéclencher le blocage si l'utilisateur est déjà ressorti.
        if (d.activities.isNotEmpty()) activityByPkg.remove(d.pkg)
        Log.i(TAG, "BLOCK_FEATURE id=${d.id} action=${if (goHome) "home" else "back"}")
        if (!repeated) {
            val appName = labelOf(d.pkg)
            val msg = if (hard) "${d.title} est bloqué sur $appName" else "Budget ${d.title} épuisé pour aujourd'hui"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
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
