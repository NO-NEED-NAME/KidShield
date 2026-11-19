package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VaultSettings(
    val isSetupComplete: Boolean = false,
    val isLocked: Boolean = false,
    val lockScope: LockScope = LockScope.ALL,
    val biometricEnabled: Boolean = false,
    val autoLockOnScreenOff: Boolean = true,
    val unlockExpiresAtMillis: Long = 0L,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L
)

class PreferencesStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(readFromDisk())
    val settingsFlow: StateFlow<VaultSettings> = _settingsFlow.asStateFlow()

    private fun readFromDisk(): VaultSettings {
        val pinHash = prefs.getString(KEY_PIN_HASH, "").orEmpty()
        val setupFlag = prefs.getBoolean(KEY_IS_SETUP_COMPLETE, false)
        return VaultSettings(
            isSetupComplete = setupFlag && pinHash.isNotEmpty(),
            isLocked = prefs.getBoolean(KEY_IS_LOCKED, false),
            lockScope = LockScope.fromId(prefs.getString(KEY_LOCK_SCOPE, LockScope.ALL.id)),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
            autoLockOnScreenOff = prefs.getBoolean(KEY_AUTO_LOCK_SCREEN_OFF, true),
            unlockExpiresAtMillis = prefs.getLong(KEY_UNLOCK_EXPIRES_AT, 0L),
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            lockoutUntilMillis = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        )
    }

    fun getSnapshot(): VaultSettings {
        val current = readFromDisk()
        _settingsFlow.value = current
        return current
    }

    @Synchronized
    fun completeSetup(
        pin: String,
        recoveryAnswer: String,
        biometricEnabled: Boolean
    ) {
        val pinSalt = SecurityCrypto.generateSalt()
        val pinHash = SecurityCrypto.hashSecret(pin, pinSalt)
        val recSalt = SecurityCrypto.generateSalt()
        val recHash = SecurityCrypto.hashSecret(recoveryAnswer, recSalt)

        prefs.edit()
            .putString(KEY_PIN_SALT, pinSalt)
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_RECOVERY_SALT, recSalt)
            .putString(KEY_RECOVERY_HASH, recHash)
            .putBoolean(KEY_BIOMETRIC_ENABLED, biometricEnabled)
            .putBoolean(KEY_AUTO_LOCK_SCREEN_OFF, true)
            .putString(KEY_LOCK_SCOPE, LockScope.ALL.id)
            .putBoolean(KEY_IS_SETUP_COMPLETE, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()

        _settingsFlow.value = readFromDisk()
    }

    @Synchronized
    fun updatePin(newPin: String) {
        val pinSalt = SecurityCrypto.generateSalt()
        val pinHash = SecurityCrypto.hashSecret(newPin, pinSalt)
        prefs.edit()
            .putString(KEY_PIN_SALT, pinSalt)
            .putString(KEY_PIN_HASH, pinHash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    fun verifyPin(candidate: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, "").orEmpty()
        val hash = prefs.getString(KEY_PIN_HASH, "").orEmpty()
        return SecurityCrypto.verifySecret(candidate, salt, hash)
    }

    fun verifyRecoveryAnswer(candidate: String): Boolean {
        val salt = prefs.getString(KEY_RECOVERY_SALT, "").orEmpty()
        val hash = prefs.getString(KEY_RECOVERY_HASH, "").orEmpty()
        return SecurityCrypto.verifySecret(candidate, salt, hash)
    }

    @Synchronized
    fun setLocked(locked: Boolean, unlockExpiresAtMillis: Long = 0L) {
        prefs.edit()
            .putBoolean(KEY_IS_LOCKED, locked)
            .putLong(KEY_UNLOCK_EXPIRES_AT, if (locked) 0L else unlockExpiresAtMillis)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    @Synchronized
    fun setLockScope(scope: LockScope) {
        prefs.edit()
            .putString(KEY_LOCK_SCOPE, scope.id)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    @Synchronized
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    @Synchronized
    fun setAutoLockOnScreenOff(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_LOCK_SCREEN_OFF, enabled)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    @Synchronized
    fun recordFailedAttempt(maxAttempts: Int = 3, lockoutDurationMs: Long = 30_000L): Pair<Int, Long> {
        val current = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val newAttempts: Int
        val newLockoutUntil: Long
        if (current >= maxAttempts) {
            newAttempts = 0
            newLockoutUntil = System.currentTimeMillis() + lockoutDurationMs
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, newLockoutUntil)
                .commit()
        } else {
            newAttempts = current
            newLockoutUntil = 0L
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, current)
                .commit()
        }
        _settingsFlow.value = readFromDisk()
        return newAttempts to newLockoutUntil
    }

    @Synchronized
    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
        _settingsFlow.value = readFromDisk()
    }

    companion object {
        private const val PREFS_NAME = "kidshield_vault_prefs"
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_SCOPE = "lock_scope"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_SCREEN_OFF = "auto_lock_screen_off"
        private const val KEY_UNLOCK_EXPIRES_AT = "unlock_expires_at"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
