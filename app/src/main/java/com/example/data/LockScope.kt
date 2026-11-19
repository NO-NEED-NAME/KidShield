package com.example.data

enum class LockScope(val id: String) {
    ALL("ALL"),
    CELLULAR_ONLY("CELLULAR_ONLY"),
    WIFI_ONLY("WIFI_ONLY");

    companion object {
        fun fromId(id: String?): LockScope =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}

enum class UnlockDuration(val minutes: Int) {
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_60(60),
    UNLIMITED(0)
}

enum class AuditEventType(val id: String, val isAlert: Boolean) {
    LOCKED("LOCKED", false),
    UNLOCKED_PIN("UNLOCKED_PIN", false),
    UNLOCKED_BIOMETRIC("UNLOCKED_BIOMETRIC", false),
    FAILED_AUTH("FAILED_AUTH", true),
    NETWORK_BLOCKED_ATTEMPT("NETWORK_BLOCKED_ATTEMPT", true),
    AUTO_RELOCKED("AUTO_RELOCKED", false),
    PIN_CHANGED("PIN_CHANGED", false),
    SCOPE_CHANGED("SCOPE_CHANGED", false);

    companion object {
        fun fromId(id: String): AuditEventType =
            entries.firstOrNull { it.id == id } ?: LOCKED
    }
}
