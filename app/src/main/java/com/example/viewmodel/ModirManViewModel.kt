package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuditEventType
import com.example.data.AuditLogEntity
import com.example.data.LockScope
import com.example.data.ModirManRepository
import com.example.data.UnlockDuration
import com.example.data.VaultSettings
import com.example.security.BiometricAuthHelper
import com.example.service.DeviceNetworkState
import com.example.service.InternetLockVpnService
import com.example.service.NetworkStatusMonitor
import com.example.service.VpnRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class MainTab {
    SHIELD,
    RULES,
    ACTIVITY
}

enum class LogFilter {
    ALL,
    ALERTS,
    LOCKS
}

enum class ConnectivityTestStatus {
    IDLE,
    TESTING,
    BLOCKED_SUCCESS,
    INTERNET_OPEN
}

sealed interface AuthPurpose {
    data object UnlockInternet : AuthPurpose
    data class ChangeLockScope(val targetScope: LockScope) : AuthPurpose
    data object OpenNetworkSettings : AuthPurpose
    data object ChangePin : AuthPurpose
    data class ToggleBiometric(val enable: Boolean) : AuthPurpose
    data class ToggleScreenOffLock(val enable: Boolean) : AuthPurpose
    data object ClearAuditLogs : AuthPurpose
}

data class AuthGateState(
    val purpose: AuthPurpose,
    val selectedDuration: UnlockDuration = UnlockDuration.MINUTES_30,
    val errorMessage: String? = null,
    val errorCounter: Int = 0,
    val isRecoveryMode: Boolean = false,
    val isSettingNewPin: Boolean = false,
    val newPinFirstEntry: String = ""
)

sealed interface UiEffect {
    data class RequestVpnConsent(val intent: Intent) : UiEffect
    data object OpenSystemInternetSettings : UiEffect
    data class ShowMessage(val message: String) : UiEffect
    data object LaunchBiometricPrompt : UiEffect
}

data class ModirManUiState(
    val isLoaded: Boolean = true,
    val settings: VaultSettings = VaultSettings(),
    val networkState: DeviceNetworkState = DeviceNetworkState(),
    val vpnRuntime: VpnRuntimeState = VpnRuntimeState(),
    val logs: List<AuditLogEntity> = emptyList(),
    val logFilter: LogFilter = LogFilter.ALL,
    val activeTab: MainTab = MainTab.SHIELD,
    val isBiometricSupported: Boolean = false,
    val remainingUnlockSeconds: Long = 0L,
    val lockoutRemainingSeconds: Long = 0L,
    val connectivityTestStatus: ConnectivityTestStatus = ConnectivityTestStatus.IDLE,
    val authGate: AuthGateState? = null
) {
    val filteredLogs: List<AuditLogEntity>
        get() = when (logFilter) {
            LogFilter.ALL -> logs
            LogFilter.ALERTS -> logs.filter { AuditEventType.fromId(it.eventType).isAlert }
            LogFilter.LOCKS -> logs.filter { !AuditEventType.fromId(it.eventType).isAlert }
        }
}

class ModirManViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModirManRepository.getInstance(application)
    private val networkMonitor = NetworkStatusMonitor(application)

    private val _uiState = MutableStateFlow(
        ModirManUiState(
            isLoaded = true,
            settings = repository.preferencesStore.getSnapshot(),
            networkState = networkMonitor.networkState.value,
            vpnRuntime = InternetLockVpnService.runtimeState.value,
            isBiometricSupported = BiometricAuthHelper.isAvailable(application)
        )
    )
    val uiState: StateFlow<ModirManUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    init {
        networkMonitor.start()

        // If user previously locked internet and VPN consent is already granted, ensure service is running
        val initialSettings = repository.preferencesStore.getSnapshot()
        if (initialSettings.isSetupComplete && initialSettings.isLocked) {
            if (VpnService.prepare(application) == null && !InternetLockVpnService.runtimeState.value.isServiceRunning) {
                InternetLockVpnService.startLock(application)
            }
        }

        viewModelScope.launch {
            combine(
                repository.settingsFlow,
                networkMonitor.networkState,
                InternetLockVpnService.runtimeState,
                repository.auditLogsFlow
            ) { settings, net, vpn, logs ->
                Quad(settings, net, vpn, logs)
            }.collect { (settings, net, vpn, logs) ->
                val now = System.currentTimeMillis()
                val unlockRem = if (!settings.isLocked && settings.unlockExpiresAtMillis > now) {
                    (settings.unlockExpiresAtMillis - now) / 1000L
                } else 0L
                val lockoutRem = if (settings.lockoutUntilMillis > now) {
                    ((settings.lockoutUntilMillis - now) + 999L) / 1000L
                } else 0L

                _uiState.update {
                    it.copy(
                        isLoaded = true,
                        settings = settings,
                        networkState = net,
                        vpnRuntime = vpn,
                        logs = logs,
                        remainingUnlockSeconds = unlockRem,
                        lockoutRemainingSeconds = lockoutRem
                    )
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val settings = _uiState.value.settings
                val now = System.currentTimeMillis()
                val unlockRem = if (!settings.isLocked && settings.unlockExpiresAtMillis > now) {
                    (settings.unlockExpiresAtMillis - now) / 1000L
                } else 0L
                val lockoutRem = if (settings.lockoutUntilMillis > now) {
                    ((settings.lockoutUntilMillis - now) + 999L) / 1000L
                } else 0L

                if (unlockRem != _uiState.value.remainingUnlockSeconds ||
                    lockoutRem != _uiState.value.lockoutRemainingSeconds
                ) {
                    _uiState.update {
                        it.copy(
                            remainingUnlockSeconds = unlockRem,
                            lockoutRemainingSeconds = lockoutRem
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        networkMonitor.stop()
        super.onCleared()
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectLogFilter(filter: LogFilter) {
        _uiState.update { it.copy(logFilter = filter) }
    }

    fun completeInitialSetup(
        pin: String,
        recoveryAnswer: String,
        enableBiometric: Boolean,
        lockImmediately: Boolean
    ) {
        // Synchronous atomic disk commit guarantees PIN is NEVER lost on next launch
        repository.preferencesStore.completeSetup(
            pin = pin,
            recoveryAnswer = recoveryAnswer,
            biometricEnabled = enableBiometric && _uiState.value.isBiometricSupported
        )
        _uiState.update {
            it.copy(
                isLoaded = true,
                settings = repository.preferencesStore.getSnapshot()
            )
        }

        viewModelScope.launch {
            repository.logEvent(
                type = AuditEventType.PIN_CHANGED,
                title = "راه‌اندازی سپر KidShield",
                detail = "رمز والدین با موفقیت ذخیره و قفل امنیتی آماده شد."
            )
            if (lockImmediately) {
                // Brief pause so Compose transitions cleanly to Dashboard before OS VPN dialog opens
                delay(250L)
                requestLockInternet()
            }
        }
    }

    /** Locking internet NEVER requires PIN so parents can lock in 1 tap before handing phone to a child */
    fun requestLockInternet() {
        val context = getApplication<Application>()
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            _effects.tryEmit(UiEffect.RequestVpnConsent(prepareIntent))
        } else {
            engageLockInternal()
        }
    }

    fun onVpnConsentResult(granted: Boolean) {
        if (granted) {
            engageLockInternal()
        } else {
            repository.preferencesStore.setLocked(locked = false, unlockExpiresAtMillis = 0L)
            _effects.tryEmit(
                UiEffect.ShowMessage("مجوز فایروال محلی VPN تایید نشد. برای مسدودسازی واقعی وای‌فای و دیتا باید گزینه OK را بزنید.")
            )
        }
    }

    private fun engageLockInternal() {
        repository.preferencesStore.setLocked(locked = true, unlockExpiresAtMillis = 0L)
        InternetLockVpnService.startLock(getApplication())
        _uiState.update { it.copy(connectivityTestStatus = ConnectivityTestStatus.IDLE) }

        viewModelScope.launch {
            repository.logEvent(
                type = AuditEventType.LOCKED,
                title = "قفل اینترنت فعال شد",
                detail = when (_uiState.value.settings.lockScope) {
                    LockScope.ALL -> "دسترسی اینترنت روی وای‌فای و دیتای موبایل مسدود شد."
                    LockScope.CELLULAR_ONLY -> "مسدودسازی هوشمند برای دیتای موبایل فعال شد."
                    LockScope.WIFI_ONLY -> "مسدودسازی هوشمند برای شبکه وای‌فای فعال شد."
                }
            )
            _effects.tryEmit(UiEffect.ShowMessage("سپر اینترنت KidShield فعال شد"))
        }
    }

    /** Real TCP connectivity probe through the Android network stack to verify blocking works */
    fun runInternetBlockTest() {
        if (_uiState.value.connectivityTestStatus == ConnectivityTestStatus.TESTING) return
        _uiState.update { it.copy(connectivityTestStatus = ConnectivityTestStatus.TESTING) }
        viewModelScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                probeHost("1.1.1.1", 53) || probeHost("8.8.8.8", 53)
            }
            val resultStatus = if (reachable) {
                ConnectivityTestStatus.INTERNET_OPEN
            } else {
                ConnectivityTestStatus.BLOCKED_SUCCESS
            }
            _uiState.update { it.copy(connectivityTestStatus = resultStatus) }
        }
    }

    private fun probeHost(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun requestUnlockInternet() {
        openAuthGate(AuthPurpose.UnlockInternet)
    }

    fun requestChangeScope(scope: LockScope) {
        if (scope == _uiState.value.settings.lockScope) return
        if (_uiState.value.settings.isLocked) {
            openAuthGate(AuthPurpose.ChangeLockScope(scope))
        } else {
            applyScopeChange(scope)
        }
    }

    fun requestOpenNetworkSettings() {
        if (_uiState.value.settings.isLocked) {
            openAuthGate(AuthPurpose.OpenNetworkSettings)
        } else {
            _effects.tryEmit(UiEffect.OpenSystemInternetSettings)
        }
    }

    fun requestToggleBiometric(enable: Boolean) {
        if (!_uiState.value.isBiometricSupported) {
            _effects.tryEmit(UiEffect.ShowMessage("حسگر بیومتریک یا قفل صفحه روی این گوشی تنظیم نشده است."))
            return
        }
        if (_uiState.value.settings.isLocked || !enable) {
            openAuthGate(AuthPurpose.ToggleBiometric(enable))
        } else {
            repository.preferencesStore.setBiometricEnabled(true)
        }
    }

    fun requestToggleAutoLockScreenOff(enable: Boolean) {
        if (_uiState.value.settings.isLocked || !enable) {
            openAuthGate(AuthPurpose.ToggleScreenOffLock(enable))
        } else {
            repository.preferencesStore.setAutoLockOnScreenOff(true)
        }
    }

    fun requestChangePin() {
        openAuthGate(AuthPurpose.ChangePin)
    }

    fun requestClearLogs() {
        if (_uiState.value.logs.isEmpty()) return
        openAuthGate(AuthPurpose.ClearAuditLogs)
    }

    fun updateSelectedUnlockDuration(duration: UnlockDuration) {
        _uiState.update { state ->
            state.copy(authGate = state.authGate?.copy(selectedDuration = duration))
        }
    }

    fun dismissAuthGate() {
        _uiState.update { it.copy(authGate = null) }
    }

    fun enterRecoveryMode() {
        _uiState.update { state ->
            state.copy(
                authGate = state.authGate?.copy(
                    isRecoveryMode = true,
                    errorMessage = null
                )
            )
        }
    }

    fun exitRecoveryMode() {
        _uiState.update { state ->
            state.copy(
                authGate = state.authGate?.copy(
                    isRecoveryMode = false,
                    errorMessage = null
                )
            )
        }
    }

    fun triggerBiometricFromGate() {
        val state = _uiState.value
        if (state.lockoutRemainingSeconds > 0L) return
        if (state.settings.biometricEnabled && state.isBiometricSupported) {
            _effects.tryEmit(UiEffect.LaunchBiometricPrompt)
        }
    }

    fun onBiometricSucceeded() {
        val gate = _uiState.value.authGate ?: return
        viewModelScope.launch {
            repository.preferencesStore.resetFailedAttempts()
            fulfillAuthPurpose(gate, viaBiometric = true)
        }
    }

    fun onBiometricError(message: String) {
        _uiState.update { state ->
            state.copy(
                authGate = state.authGate?.copy(
                    errorMessage = message,
                    errorCounter = (state.authGate.errorCounter) + 1
                )
            )
        }
    }

    fun submitPin(pin: String) {
        val gate = _uiState.value.authGate ?: return
        if (_uiState.value.lockoutRemainingSeconds > 0L) return

        viewModelScope.launch {
            if (gate.isSettingNewPin) {
                if (gate.newPinFirstEntry.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            authGate = gate.copy(
                                newPinFirstEntry = pin,
                                errorMessage = null
                            )
                        )
                    }
                } else if (gate.newPinFirstEntry == pin) {
                    repository.preferencesStore.updatePin(pin)
                    repository.logEvent(
                        type = AuditEventType.PIN_CHANGED,
                        title = "رمز والدین تغییر یافت",
                        detail = "رمز ۴ رقمی جدید با موفقیت جایگزین شد."
                    )
                    _uiState.update { it.copy(authGate = null) }
                    _effects.tryEmit(UiEffect.ShowMessage("رمز جدید والدین با موفقیت ذخیره شد"))
                } else {
                    _uiState.update {
                        it.copy(
                            authGate = gate.copy(
                                newPinFirstEntry = "",
                                errorMessage = "رمزهای واردشده یکسان نیستند. دوباره رمز جدید را وارد کنید.",
                                errorCounter = gate.errorCounter + 1
                            )
                        )
                    }
                }
                return@launch
            }

            val isValid = repository.preferencesStore.verifyPin(pin)
            if (isValid) {
                repository.preferencesStore.resetFailedAttempts()
                fulfillAuthPurpose(gate, viaBiometric = false)
            } else {
                val (attempts, lockoutUntil) = repository.preferencesStore.recordFailedAttempt()
                val remainingTries = (3 - attempts).coerceAtLeast(0)
                val isNowLockedOut = lockoutUntil > System.currentTimeMillis()
                repository.logEvent(
                    type = AuditEventType.FAILED_AUTH,
                    title = if (isNowLockedOut) "قفل امنیتی ۳۰ ثانیه‌ای فعال شد" else "تلاش ناموفق با رمز اشتباه",
                    detail = if (isNowLockedOut) {
                        "۳ بار رمز اشتباه وارد شد. ورودی به مدت ۳۰ ثانیه مسدود شد."
                    } else {
                        "رمز اشتباه برای باز کردن سپر اینترنت وارد شد."
                    }
                )
                val errText = if (isNowLockedOut) {
                    "۳ بار رمز اشتباه وارد شد! لطفاً ۳۰ ثانیه صبر کنید."
                } else {
                    "رمز اشتباه است! تلاش باقی‌مانده: $remainingTries"
                }
                _uiState.update { state ->
                    state.copy(
                        authGate = gate.copy(
                            errorMessage = errText,
                            errorCounter = gate.errorCounter + 1
                        )
                    )
                }
            }
        }
    }

    fun submitRecoveryAnswer(answer: String) {
        val gate = _uiState.value.authGate ?: return
        viewModelScope.launch {
            val valid = repository.preferencesStore.verifyRecoveryAnswer(answer)
            if (valid) {
                repository.preferencesStore.resetFailedAttempts()
                _uiState.update {
                    it.copy(
                        authGate = gate.copy(
                            isRecoveryMode = false,
                            isSettingNewPin = true,
                            newPinFirstEntry = "",
                            errorMessage = null
                        )
                    )
                }
            } else {
                repository.logEvent(
                    type = AuditEventType.FAILED_AUTH,
                    title = "تلاش ناموفق بازیابی رمز",
                    detail = "پاسخ سوال امنیتی بازیابی رمز نادرست بود."
                )
                _uiState.update {
                    it.copy(
                        authGate = gate.copy(
                            errorMessage = "پاسخ امنیتی نادرست است.",
                            errorCounter = gate.errorCounter + 1
                        )
                    )
                }
            }
        }
    }

    private fun openAuthGate(purpose: AuthPurpose) {
        val state = _uiState.value
        _uiState.update {
            it.copy(authGate = AuthGateState(purpose = purpose))
        }
        if (state.settings.biometricEnabled &&
            state.isBiometricSupported &&
            state.lockoutRemainingSeconds == 0L
        ) {
            _effects.tryEmit(UiEffect.LaunchBiometricPrompt)
        }
    }

    private suspend fun fulfillAuthPurpose(gate: AuthGateState, viaBiometric: Boolean) {
        when (val purpose = gate.purpose) {
            AuthPurpose.UnlockInternet -> {
                val duration = gate.selectedDuration
                val expiresAt = if (duration == UnlockDuration.UNLIMITED) {
                    0L
                } else {
                    System.currentTimeMillis() + duration.minutes * 60_000L
                }
                repository.preferencesStore.setLocked(locked = false, unlockExpiresAtMillis = expiresAt)
                val durationLabel = when (duration) {
                    UnlockDuration.MINUTES_15 -> "۱۵ دقیقه"
                    UnlockDuration.MINUTES_30 -> "۳۰ دقیقه"
                    UnlockDuration.MINUTES_60 -> "۱ ساعت"
                    UnlockDuration.UNLIMITED -> "تا قفل دستی"
                }
                repository.logEvent(
                    type = if (viaBiometric) AuditEventType.UNLOCKED_BIOMETRIC else AuditEventType.UNLOCKED_PIN,
                    title = "اینترنت با احراز هویت باز شد",
                    detail = "روش تایید: ${if (viaBiometric) "بیومتریک" else "رمز والدین"} · مدت: $durationLabel"
                )
                if (expiresAt > 0L) {
                    InternetLockVpnService.startTimedUnlock(getApplication(), expiresAt)
                } else if (_uiState.value.settings.autoLockOnScreenOff) {
                    InternetLockVpnService.startTimedUnlock(getApplication(), 0L)
                } else {
                    InternetLockVpnService.stopGuard(getApplication())
                }
                _uiState.update {
                    it.copy(
                        authGate = null,
                        connectivityTestStatus = ConnectivityTestStatus.IDLE
                    )
                }
                _effects.tryEmit(UiEffect.ShowMessage("اینترنت باز شد ($durationLabel)"))
            }

            is AuthPurpose.ChangeLockScope -> {
                applyScopeChange(purpose.targetScope)
                _uiState.update { it.copy(authGate = null) }
            }

            AuthPurpose.OpenNetworkSettings -> {
                _uiState.update { it.copy(authGate = null) }
                _effects.tryEmit(UiEffect.OpenSystemInternetSettings)
            }

            AuthPurpose.ChangePin -> {
                _uiState.update {
                    it.copy(
                        authGate = gate.copy(
                            isSettingNewPin = true,
                            newPinFirstEntry = "",
                            errorMessage = null
                        )
                    )
                }
            }

            is AuthPurpose.ToggleBiometric -> {
                repository.preferencesStore.setBiometricEnabled(purpose.enable)
                _uiState.update { it.copy(authGate = null) }
            }

            is AuthPurpose.ToggleScreenOffLock -> {
                repository.preferencesStore.setAutoLockOnScreenOff(purpose.enable)
                _uiState.update { it.copy(authGate = null) }
            }

            AuthPurpose.ClearAuditLogs -> {
                repository.clearLogs()
                _uiState.update { it.copy(authGate = null) }
                _effects.tryEmit(UiEffect.ShowMessage("گزارش فعالیت‌ها پاک شد"))
            }
        }
    }

    private fun applyScopeChange(scope: LockScope) {
        repository.preferencesStore.setLockScope(scope)
        val label = when (scope) {
            LockScope.ALL -> "همه شبکه‌ها (وای‌فای و دیتا)"
            LockScope.CELLULAR_ONLY -> "فقط دیتای موبایل"
            LockScope.WIFI_ONLY -> "فقط وای‌فای"
        }
        viewModelScope.launch {
            repository.logEvent(
                type = AuditEventType.SCOPE_CHANGED,
                title = "تغییر محدوده قفل شبکه",
                detail = "محدوده جدید: $label"
            )
            _effects.tryEmit(UiEffect.ShowMessage("محدوده قفل: $label"))
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
