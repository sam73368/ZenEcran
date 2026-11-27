package com.zenecran.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenecran.data.CustomRule
import com.zenecran.data.InstalledApp
import com.zenecran.data.Schedule
import com.zenecran.data.RuleConfig
import com.zenecran.data.Store
import com.zenecran.data.UsageRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class UiState(
    val hasUsage: Boolean = true,
    val hasAccessibility: Boolean = true,
    val perApp: Map<String, Long> = emptyMap(),
    val total: Long = 0L,
    val week: List<Long> = List(7) { 0L },
    val installed: List<InstalledApp> = emptyList(),
    val loaded: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Messages courts affichés dans une Snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile
    var visible = false

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val usage = UsageRepo.hasUsageAccess(ctx)
            val acc = UsageRepo.isAccessibilityOn(ctx)
            val countable = UsageRepo.userPackages(ctx)
            var perApp: Map<String, Long> = emptyMap()
            var week: List<Long> = List(7) { 0L }
            if (usage) {
                perApp = UsageRepo.perApp(ctx, UsageRepo.dayStart(LocalDate.now()), System.currentTimeMillis())
                    .filterKeys { it in countable }
                week = UsageRepo.weekTotals(ctx, countable)
            }
            val installed = _ui.value.installed.ifEmpty { UsageRepo.launchableApps(ctx) }
            _ui.value = UiState(usage, acc, perApp, perApp.values.sum(), week, installed, true)
        }
    }

    // ------------------------------------------------------------ verrouillage « engagement »

    private fun locked() = Store.config.value.isStrict()

    private fun deny() {
        messages.tryEmit("Mode engagement actif : impossible d'assouplir tes règles pour le moment.")
    }

    // ------------------------------------------------------------ limites par appli

    fun addApp(pkg: String, minutes: Int = 30) {
        Store.updateConfig { it.copy(limits = it.limits + (pkg to minutes)) }
    }

    fun setLimit(pkg: String, min: Int) {
        val old = Store.config.value.limits[pkg]
        if (locked() && old != null && old > 0 && (min == 0 || min > old)) {
            deny()
            return
        }
        Store.updateConfig { it.copy(limits = it.limits + (pkg to min)) }
    }

    fun removeApp(pkg: String) {
        if (locked()) {
            deny()
            return
        }
        Store.updateConfig { it.copy(limits = it.limits - pkg) }
    }

    // ------------------------------------------------------------ fonctionnalités

    fun setRule(id: String, enabled: Boolean, budgetMin: Int) {
        val old = Store.config.value.rules[id] ?: RuleConfig()
        if (locked() && old.enabled && (!enabled || budgetMin > old.budgetMin)) {
            deny()
            return
        }
        Store.updateConfig { it.copy(rules = it.rules + (id to RuleConfig(enabled, budgetMin))) }
    }

    // ------------------------------------------------------------ plages horaires

    fun newScheduleId(): String = UUID.randomUUID().toString()

    fun saveSchedule(s: Schedule) {
        val exists = Store.config.value.schedules.any { it.id == s.id }
        if (locked() && exists) {
            deny()
            return
        }
        Store.updateConfig { c ->
            val list = if (exists) c.schedules.map { if (it.id == s.id) s else it } else c.schedules + s
            c.copy(schedules = list)
        }
    }

    fun deleteSchedule(id: String) {
        if (locked()) {
            deny()
            return
        }
        Store.updateConfig { c -> c.copy(schedules = c.schedules.filterNot { it.id == id }) }
    }

    fun toggleSchedule(id: String, on: Boolean) {
        if (locked() && !on) {
            deny()
            return
        }
        Store.updateConfig { c -> c.copy(schedules = c.schedules.map { if (it.id == id) it.copy(enabled = on) else it }) }
    }

    // ------------------------------------------------------------ concentration / engagement / réglages

    fun startFocus(minutes: Int) {
        val c = Store.config.value
        if (c.limits.isEmpty() && c.schedules.none { it.enabled }) {
            messages.tryEmit("Ajoute d'abord des applis dans « Limites » ou « Horaires ».")
            return
        }
        Store.updateConfig { it.copy(focusUntil = System.currentTimeMillis() + minutes * 60_000L) }
    }

    fun stopFocus() {
        if (locked()) {
            deny()
            return
        }
        Store.updateConfig { it.copy(focusUntil = 0L) }
    }

    fun setGoal(min: Int) {
        Store.updateConfig { it.copy(goalMin = min.coerceIn(30, 720)) }
    }

    /** hours = 0 -> jusqu'à minuit ce soir. */
    fun enableStrict(hours: Int) {
        val until = if (hours == 0) {
            UsageRepo.dayStart(LocalDate.now().plusDays(1))
        } else {
            System.currentTimeMillis() + hours * 3_600_000L
        }
        Store.updateConfig { it.copy(strictUntil = until) }
        messages.tryEmit("Mode engagement activé. Courage !")
    }

    fun setDiagnostic(on: Boolean) {
        Store.updateConfig { it.copy(diagnostic = on) }
        if (!on) Store.setDump("")
    }

    // ------------------------------------------------------------ personnalisation

    fun setAccent(i: Int) {
        Store.updateConfig { it.copy(accent = i) }
    }

    fun setAmoled(on: Boolean) {
        Store.updateConfig { it.copy(amoled = on) }
    }

    fun setQuote(s: String) {
        Store.updateConfig { it.copy(quote = s.take(120)) }
    }

    /** Réglages du bouton « +minutes ». Verrouillés pendant le mode engagement. */
    fun setExt(waitSec: Int? = null, minutes: Int? = null, max: Int? = null) {
        if (locked()) {
            deny()
            return
        }
        Store.updateConfig {
            it.copy(
                extWaitSec = waitSec ?: it.extWaitSec,
                extMinutes = minutes ?: it.extMinutes,
                extMax = max ?: it.extMax,
            )
        }
    }

    fun setWarn(min: Int) {
        Store.updateConfig { it.copy(warnMin = min) }
    }

    fun setFeatureBack(back: Boolean) {
        Store.updateConfig { it.copy(featureBack = back) }
    }

    // ------------------------------------------------------------ règles personnalisées

    fun addCustomRule(r: CustomRule) {
        Store.updateConfig { it.copy(customRules = it.customRules + r) }
    }

    fun deleteCustomRule(id: String) {
        val c = Store.config.value
        if (locked() && c.rules[id]?.enabled == true) {
            deny()
            return
        }
        Store.updateConfig { it.copy(customRules = it.customRules.filterNot { r -> r.id == id }, rules = it.rules - id) }
    }

    fun say(msg: String) {
        messages.tryEmit(msg)
    }
}
