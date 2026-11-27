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

    /** Temps passé au premier plan, par appli, entre from et to (ms). */
    fun perApp(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            usm.queryEvents(from, to)
        } catch (e: Exception) {
            null
        } ?: return emptyMap()
        val ev = UsageEvents.Event()
        val starts = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val key = ev.packageName + "/" + (ev.className ?: "")
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> starts[key] = ev.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val s = starts.remove(key)
                    if (s != null && ev.timeStamp > s) {
                        totals[ev.packageName] = (totals[ev.packageName] ?: 0L) + (ev.timeStamp - s)
                    }
                }
            }
        }
        // Activités encore au premier plan à la fin de la période.
        starts.forEach { (key, s) ->
            val pkg = key.substringBefore('/')
            if (to > s) totals[pkg] = (totals[pkg] ?: 0L) + (to - s)
        }
        return totals
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
