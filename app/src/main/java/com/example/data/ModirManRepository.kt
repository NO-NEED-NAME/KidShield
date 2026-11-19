package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ModirManRepository private constructor(context: Context) {

    val preferencesStore = PreferencesStore(context.applicationContext)
    private val auditLogDao = ModirManDatabase.getInstance(context.applicationContext).auditLogDao()

    val settingsFlow: Flow<VaultSettings> = preferencesStore.settingsFlow
    val auditLogsFlow: Flow<List<AuditLogEntity>> = auditLogDao.observeRecentLogs()

    suspend fun logEvent(type: AuditEventType, title: String, detail: String) {
        auditLogDao.insert(
            AuditLogEntity(
                eventType = type.id,
                title = title,
                detail = detail,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearLogs() {
        auditLogDao.clearAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: ModirManRepository? = null
        @Volatile
        private var INSTANCE_APP_CONTEXT: Context? = null

        fun getInstance(context: Context): ModirManRepository {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            if (existing != null && INSTANCE_APP_CONTEXT === appCtx) {
                return existing
            }
            return synchronized(this) {
                val doubleCheck = INSTANCE
                if (doubleCheck != null && INSTANCE_APP_CONTEXT === appCtx) {
                    doubleCheck
                } else {
                    ModirManRepository(appCtx).also {
                        INSTANCE = it
                        INSTANCE_APP_CONTEXT = appCtx
                    }
                }
            }
        }
    }
}
