package com.zenecran.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenecran.data.CustomRule
import com.zenecran.update.UpdateInfo
import com.zenecran.update.Updater
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
    val batteryOk: Boolean = true,
    val perApp: Map<String, Long> = emptyMap(),
    val total: Long = 0L,
    val week: List<Long> = List(7) { 0L },
    val installed: List<InstalledApp> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface UpdState {
    data object Idle : UpdState
    data object Checking : UpdState
    data object UpToDate : UpdState
    data class Available(val info: UpdateInfo) : UpdState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdState
    data class Error(val msg: String) : UpdState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Messages courts affichés dans une Snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile
    var visible = false

    // ------------------------------------------------------------ mises à jour

    private val _upd = MutableStateFlow<UpdState>(UpdState.Idle)
    val upd: StateFlow<UpdState> = _upd.asStateFlow()

    init {
        checkUpdate(silent = true)
    }

    fun checkUpdate(silent: Boolean = false) {
        if (_upd.value is UpdState.Checking || _upd.value is UpdState.Downloading) return
        _upd.value = UpdState.Checking
        viewModelScope.launch {
            _upd.value = try {
                Updater.check()?.let { UpdState.Available(it) } ?: UpdState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdState.Idle else UpdState.Error(e.message ?: "Pas de connexion")
            }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        if (!Updater.canInstall(ctx)) {
            messages.tryEmit("Autorise Zen Écran à installer des applis, puis appuie de nouveau sur Installer.")
            Updater.openInstallPermission(ctx)
            return
        }
        _upd.value = UpdState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val apk = Updater.download(ctx, info) { p -> _upd.value = UpdState.Downloading(info, p) }
                Updater.install(ctx, apk)
                _upd.value = UpdState.Available(info)
            } catch (e: Exception) {
                _upd.value = UpdState.Error(e.message ?: "Échec du téléchargement")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val usage = UsageRepo.hasUsageAccess(ctx)
            val acc = UsageRepo.isAccessibilityOn(ctx)
            val battery = UsageRepo.isBatteryUnrestricted(ctx)
            val countable = UsageRepo.userPackages(ctx)
            var perApp: Map<String, Long> = emptyMap()
            var week: List<Long> = List(7) { 0L }
            if (usage) {
                perApp = UsageRepo.perApp(ctx, UsageRepo.dayStart(LocalDate.now()), System.currentTimeMillis())
                    .filterKeys { it in countable }
                week = UsageRepo.weekTotals(ctx, countable)
            }
            val installed = _ui.value.installed.ifEmpty { UsageRepo.launchableApps(ctx) }
            _ui.value = UiState(usage, acc, battery, perApp, perApp.values.sum(), week, installed, true)
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
        if (!on) Store.clearDumps()
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
