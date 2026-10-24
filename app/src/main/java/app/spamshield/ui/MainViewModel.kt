package app.spamshield.ui

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.spamshield.SpamShieldApp
import app.spamshield.core.Action
import app.spamshield.core.IncomingCall
import app.spamshield.core.NeighborMode
import app.spamshield.core.PhoneNumbers
import app.spamshield.core.Rule
import app.spamshield.core.Verdict
import app.spamshield.data.AppSettings
import app.spamshield.data.FtcSync
import app.spamshield.data.ListEntry
import app.spamshield.data.ListKind
import app.spamshield.data.LoggedCall
import app.spamshield.data.ThemeMode
import app.spamshield.work.FtcSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CheckResult(val label: String, val verdict: Verdict?, val message: String)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpamShieldApp
    private val repo = app.repository
    private val db = app.db
    private val store = app.settings

    val settings: StateFlow<AppSettings?> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val log: StateFlow<List<LoggedCall>> =
        db.callLog().observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockList: StateFlow<List<ListEntry>> =
        db.lists().observe(ListKind.BLOCK).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allowList: StateFlow<List<ListEntry>> =
        db.lists().observe(ListKind.ALLOW).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

    val blockedThisWeek: StateFlow<Int> =
        db.callLog().observeCount(Action.BLOCK.name, weekAgo).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val silencedThisWeek: StateFlow<Int> =
        db.callLog().observeCount(Action.SILENCE.name, weekAgo).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ftcNumbers: StateFlow<Int> = store.flow
        .map { it.ftcDays }
        .distinctUntilChanged()
        .flatMapLatest { days -> db.ftc().observeNumberCount(FtcSync.cutoff(days)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ftcProgress: StateFlow<String?> = FtcSync.progress

    private val _roleHeld = MutableStateFlow(false)
    val roleHeld: StateFlow<Boolean> = _roleHeld.asStateFlow()

    private val _roleRequested = MutableStateFlow(false)
    val roleRequested: StateFlow<Boolean> = _roleRequested.asStateFlow()

    private val _notificationsAllowed = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

    private val _checkResult = MutableStateFlow<CheckResult?>(null)
    val checkResult: StateFlow<CheckResult?> = _checkResult.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun refreshRole() {
        val roles = app.getSystemService(RoleManager::class.java)
        _roleHeld.value = roles?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    fun markRoleRequested() {
        _roleRequested.value = true
    }

    fun refreshPermissions() {
        _notificationsAllowed.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun check(raw: String) {
        val number = PhoneNumbers.normalize(raw)
        if (number == null) {
            _checkResult.value = CheckResult(raw, null, "That doesn't look like a phone number.")
            return
        }
        viewModelScope.launch {
            _checking.value = true
            try {
                val verdict = repo.evaluate(IncomingCall(number, hidden = false))
                _checkResult.value = CheckResult(PhoneNumbers.display(number), verdict, verdict.reason)
            } finally {
                _checking.value = false
            }
        }
    }

    fun setAction(rule: Rule, action: Action) = launch {
        store.setAction(rule, action)
        if (rule == Rule.FTC && action != Action.OFF) FtcSyncWorker.runNow(app)
    }

    fun setMyNumber(value: String) = launch { store.setMyNumber(value) }
    fun setNeighborMode(value: NeighborMode) = launch { store.setNeighborMode(value) }
    fun setFtcMinReports(value: Int) = launch { store.setFtcMinReports(value) }
    fun setFtcDays(value: Int) = launch {
        store.setFtcDays(value)
        FtcSyncWorker.runNow(app)
    }
    fun setCommunityMinVotes(value: Int) = launch { store.setCommunityMinVotes(value) }
    fun setCommunityToken(value: String) = launch { store.setCommunityToken(value) }
    fun setNotifyOnBlock(value: Boolean) = launch { store.setNotifyOnBlock(value) }
    fun setLogAllowed(value: Boolean) = launch { store.setLogAllowed(value) }
    fun setTheme(value: ThemeMode) = launch { store.setTheme(value) }

    fun syncFtcNow() = FtcSyncWorker.runNow(app)

    fun addEntry(kind: String, input: String, label: String) = launch { repo.addEntry(kind, input, label) }
    fun removeEntry(entry: ListEntry) = launch { db.lists().delete(entry) }
    fun blockNumber(number: String) = launch { repo.blockNumber(number) }
    fun allowNumber(number: String) = launch { repo.allowNumber(number) }
    fun deleteLog(call: LoggedCall) = launch { db.callLog().delete(call) }
    fun clearLog() = launch { db.callLog().clear() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
