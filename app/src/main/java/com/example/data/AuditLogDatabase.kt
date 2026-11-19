package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val title: String,
    val detail: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC LIMIT 150")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAll()
}

@Database(
    entities = [AuditLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ModirManDatabase : RoomDatabase() {
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: ModirManDatabase? = null
        @Volatile
        private var INSTANCE_APP_CONTEXT: Context? = null

        fun getInstance(context: Context): ModirManDatabase {
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
                    Room.databaseBuilder(
                        appCtx,
                        ModirManDatabase::class.java,
                        "modirman_security.db"
                    ).build().also {
                        INSTANCE = it
                        INSTANCE_APP_CONTEXT = appCtx
                    }
                }
            }
        }
    }
}
