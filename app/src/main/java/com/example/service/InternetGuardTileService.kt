package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.data.AuditEventType
import com.example.data.ModirManRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InternetGuardTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val repository = ModirManRepository.getInstance(applicationContext)
        tileScope.launch {
            val settings = repository.preferencesStore.getSnapshot()
            if (!settings.isSetupComplete) {
                openMainActivity(null)
                return@launch
            }

            if (!settings.isLocked) {
                if (VpnService.prepare(applicationContext) == null) {
                    repository.preferencesStore.setLocked(locked = true, unlockExpiresAtMillis = 0L)
                    repository.logEvent(
                        type = AuditEventType.LOCKED,
                        title = "قفل سریع از نوار اعلان",
                        detail = "سپر اینترنت با دکمه سریع KidShield فعال شد."
                    )
                    InternetLockVpnService.startLock(applicationContext)
                    refreshTile()
                } else {
                    openMainActivity(MainActivity.ACTION_FROM_TILE_LOCK)
                }
            } else {
                openMainActivity(MainActivity.ACTION_FROM_TILE_UNLOCK)
            }
        }
    }

    private fun refreshTile() {
        val repository = ModirManRepository.getInstance(applicationContext)
        val settings = repository.preferencesStore.getSnapshot()
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        if (settings.isLocked) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "اینترنت قفل است"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "اینترنت باز است"
            }
        }
        tile.updateTile()
    }

    private fun openMainActivity(actionExtra: String?) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (actionExtra != null) {
                putExtra(MainActivity.EXTRA_TILE_ACTION, actionExtra)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                300,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
