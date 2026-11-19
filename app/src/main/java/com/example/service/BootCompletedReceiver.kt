package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.data.ModirManRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ModirManRepository.getInstance(context.applicationContext)
                val settings = repo.preferencesStore.getSnapshot()
                if (settings.isSetupComplete && settings.isLocked) {
                    if (VpnService.prepare(context.applicationContext) == null) {
                        InternetLockVpnService.startLock(context.applicationContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
