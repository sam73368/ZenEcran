package com.zenecran.data

/** Réglage d'une fonctionnalité bloquée (ex. Reels). budgetMin = 0 -> bloquée totalement. */
data class RuleConfig(val enabled: Boolean = false, val budgetMin: Int = 0)

/**
 * Plage horaire pendant laquelle des applis cochées sont interdites.
 * days : 1 = lundi ... 7 = dimanche (java.time.DayOfWeek.value).
 * Si start > end, la plage passe minuit (ex. 22:00 -> 07:00).
 * Si start == end, la plage couvre toute la journée.
 */
data class Schedule(
    val id: String,
    val name: String,
    val pkgs: Set<String> = emptySet(),
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMin: Int = 22 * 60,
    val endMin: Int = 7 * 60,
    val enabled: Boolean = true,
) {
    fun covers(dayOfWeek: Int, minute: Int): Boolean {
        val yesterday = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        return when {
            startMin == endMin -> dayOfWeek in days
            startMin < endMin -> dayOfWeek in days && minute in startMin until endMin
            else -> (dayOfWeek in days && minute >= startMin) || (yesterday in days && minute < endMin)
        }
    }
}

/** Règle de blocage créée par l'utilisateur pour n'importe quelle appli. */
data class CustomRule(
    val id: String,
    val pkg: String,
    val title: String,
    val selectedLabels: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
)

data class Config(
    /** App surveillée -> limite quotidienne en minutes (0 = aucune limite, juste surveillée). */
    val limits: Map<String, Int> = emptyMap(),
    val rules: Map<String, RuleConfig> = emptyMap(),
    val customRules: List<CustomRule> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val goalMin: Int = 180,
    val focusUntil: Long = 0L,
    val strictUntil: Long = 0L,
    val diagnostic: Boolean = false,
    // Personnalisation
    val accent: Int = 0,
    val amoled: Boolean = false,
    val quote: String = "",
    val extMinutes: Int = 5,
    val extMax: Int = 2,
    val extWaitSec: Int = 15,
    val warnMin: Int = 5,
    /** true : « retour » d'abord ; false : retour direct à l'accueil quand un contenu est bloqué. */
    val featureBack: Boolean = true,
) {
    /** Fonctionnalités prédéfinies + règles personnalisées. */
    fun allFeatures(): List<FeatureDef> = Presets.features + customRules.map {
        FeatureDef(
            id = it.id, pkg = it.pkg, title = it.title, subtitle = "Règle personnalisée",
            ids = it.ids, selectedLabels = it.selectedLabels,
        )
    }

    fun isStrict(now: Long = System.currentTimeMillis()) = strictUntil > now
    fun isFocus(now: Long = System.currentTimeMillis()) = focusUntil > now

    /** Plage horaire active qui interdit cette appli en ce moment, s'il y en a une. */
    fun activeSchedule(pkg: String, dayOfWeek: Int, minute: Int): Schedule? =
        schedules.firstOrNull { it.enabled && pkg in it.pkgs && it.covers(dayOfWeek, minute) }

    /** Toutes les applis concernées par une limite ou une plage horaire. */
    fun isMonitored(pkg: String): Boolean =
        pkg in limits || schedules.any { it.enabled && pkg in it.pkgs }
}

/** Compteurs remis à zéro chaque jour. */
data class Daily(
    val day: String = "",
    val featureSec: Map<String, Int> = emptyMap(),
    /** Nombre de « +5 min » accordés aujourd'hui, par appli. */
    val extras: Map<String, Int> = emptyMap(),
)

data class InstalledApp(val pkg: String, val label: String)

fun fmtMin(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${"%02d".format(m % 60)}"
}

fun fmtMs(ms: Long): String {
    val m = (ms / 60_000L).toInt()
    return if (m <= 0) "0 min" else fmtMin(m)
}

fun fmtClock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
