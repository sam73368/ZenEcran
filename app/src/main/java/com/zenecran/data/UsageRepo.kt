package com.zenecran.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import com.zenecran.service.GuardService
import java.time.LocalDate
import java.time.ZoneId

/** Lecture du temps d'écran via UsageStatsManager et petits utilitaires système. */
object UsageRepo {

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, GuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun dayStart(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Appli au premier plan selon le journal d'Android, mise à jour à chaque calcul du jour en cours. */
    @Volatile
    var lastForeground: String? = null

    private const val EV_RESUMED = 1
    private const val EV_PAUSED = 2
    private const val EV_SCREEN_OFF = 16
    private const val EV_KEYGUARD_SHOWN = 17
    private const val EV_STOPPED = 23
    private const val EV_SHUTDOWN = 26

    /**
     * Temps passé au premier plan, par appli, entre from et to (ms).
     * Une seule appli est au premier plan à la fois : on ferme la session en cours dès qu'une autre appli
     * passe devant, que l'écran s'éteint ou se verrouille. Une appli ne « s'arrête » que lorsque toutes
     * ses activités ont été mises en pause (changer d'écran dans une appli ne coupe pas le compteur).
     */
    fun perApp(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(from, to)
        } catch (e: Exception) {
            null
        } ?: return emptyMap()
        val ev = UsageEvents.Event()
        val totals = HashMap<String, Long>()
        var cur: String? = null
        var curStart = 0L
        val resumed = HashSet<String>()
        var seenActivity = false

        fun add(pkg: String, ms: Long) {
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }
        fun close(t: Long) {
            val p = cur ?: return
            add(p, t - curStart)
            cur = null
            resumed.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            val pkg = ev.packageName ?: continue
            val cls = ev.className ?: ""
            when (ev.eventType) {
                EV_RESUMED -> {
                    seenActivity = true
                    if (cur != pkg) {
                        close(t)
                        cur = pkg
                        curStart = t
                    }
                    resumed += cls
                }
                EV_PAUSED, EV_STOPPED -> {
                    if (cur == null && !seenActivity && ev.eventType == EV_PAUSED) {
                        // Appli déjà ouverte avant le début de la période (ex. avant minuit).
                        add(pkg, t - from)
                    }
                    seenActivity = true
                    if (cur == pkg) {
                        resumed -= cls
                        if (resumed.isEmpty()) close(t)
                    }
                }
                EV_SCREEN_OFF, EV_KEYGUARD_SHOWN, EV_SHUTDOWN -> close(t)
            }
        }
        if (System.currentTimeMillis() - to < 10_000) lastForeground = cur
        close(to)
        return totals
    }

    /** Dernière activité (écran) ouverte par cette appli depuis [since], d'après le journal d'Android. */
    fun lastResumedActivity(ctx: Context, pkg: String, since: Long): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(since, System.currentTimeMillis())
        } catch (e: Exception) {
            null
        } ?: return null
        val ev = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == EV_RESUMED && ev.packageName == pkg) last = ev.className
        }
        return last
    }

    fun isBatteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Totaux des 7 derniers jours (le dernier = aujourd'hui). */
    fun weekTotals(ctx: Context, countable: Set<String>): List<Long> {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        return (6 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            val from = dayStart(d)
            val to = if (i == 0) now else dayStart(d.plusDays(1))
            perApp(ctx, from, to).filterKeys { it in countable }.values.sum()
        }
    }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Suppress("DEPRECATION")
    fun homePackages(ctx: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Applis qui ne doivent jamais être bloquées : téléphone, lanceur, cette appli, réglages. */
    fun protectedPackages(ctx: Context): Set<String> {
        val set = mutableSetOf(ctx.packageName, "com.android.settings", "com.android.systemui", "android")
        set += homePackages(ctx)
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage?.let { set += it }
        } catch (_: Exception) {
        }
        return set
    }

    /** Applis « normales » installées (avec icône dans le lanceur). */
    @Suppress("DEPRECATION")
    fun launchableApps(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val protectedPkgs = protectedPackages(ctx)
        return pm.queryIntentActivities(launcherIntent(), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first !in protectedPkgs }
            .map { InstalledApp(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    /** Paquets comptés dans le temps d'écran total. */
    @Suppress("DEPRECATION")
    fun userPackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val skip = homePackages(ctx) + ctx.packageName
        return pm.queryIntentActivities(launcherIntent(), 0)
            .map { it.activityInfo.packageName }
            .filter { it !in skip }
            .toSet()
    }
}
