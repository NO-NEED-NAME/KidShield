package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AuditEventType
import com.example.data.LockScope
import com.example.data.ModirManRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.Locale

data class VpnRuntimeState(
    val isServiceRunning: Boolean = false,
    val isBlockingTraffic: Boolean = false,
    val blockedPacketsCount: Long = 0L,
    val lastError: String? = null
)

class InternetLockVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ModirManRepository
    private lateinit var networkMonitor: NetworkStatusMonitor

    private var vpnInterface: ParcelFileDescriptor? = null
    private var drainJob: Job? = null
    private var timerJob: Job? = null
    private var stateObserverJob: Job? = null
    private var hasEnteredForeground = false

    private var lastWifiState: Boolean? = null
    private var lastCellularState: Boolean? = null
    private var lastBlockedLogTimeMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            serviceScope.launch {
                val settings = repository.preferencesStore.getSnapshot()
                if (settings.isSetupComplete && settings.autoLockOnScreenOff && !settings.isLocked) {
                    repository.preferencesStore.setLocked(locked = true, unlockExpiresAtMillis = 0L)
                    repository.logEvent(
                        type = AuditEventType.AUTO_RELOCKED,
                        title = "قفل خودکار با خاموش شدن صفحه",
                        detail = "پس از قفل شدن صفحه گوشی، سپر اینترنت KidShield دوباره فعال شد."
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ModirManRepository.getInstance(applicationContext)
        networkMonitor = NetworkStatusMonitor(applicationContext)
        networkMonitor.start()
        createNotificationChannel()
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        _runtimeState.update { it.copy(isServiceRunning = true, lastError = null) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL for Android 12-16: Always enter foreground immediately on startForegroundService
        val initialSettings = repository.preferencesStore.getSnapshot()
        val initialNotif = when (intent?.action) {
            ACTION_TIMED_UNLOCK -> {
                val exp = intent.getLongExtra(EXTRA_EXPIRES_AT, initialSettings.unlockExpiresAtMillis)
                if (exp > System.currentTimeMillis()) {
                    buildTimedUnlockNotification(exp)
                } else {
                    buildStandbyNotification()
                }
            }
            else -> buildLockedNotification(initialSettings.lockScope, true)
        }
        startForegroundCompat(initialNotif)

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopTunnel()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ENGAGE_LOCK -> {
                if (!initialSettings.isLocked) {
                    repository.preferencesStore.setLocked(locked = true, unlockExpiresAtMillis = 0L)
                }
                applyPolicyNow(repository.preferencesStore.getSnapshot(), networkMonitor.networkState.value)
                ensureObservingState()
            }
            ACTION_TIMED_UNLOCK -> {
                val expiresAt = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L)
                stopTunnel()
                startTimedCountdown(expiresAt)
                ensureObservingState()
            }
            else -> {
                // Started by OS (e.g., Always-on VPN)
                applyPolicyNow(initialSettings, networkMonitor.networkState.value)
                ensureObservingState()
            }
        }
        syncQuickSettingsTile()
        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        repository.preferencesStore.setLocked(locked = false, unlockExpiresAtMillis = 0L)
        _runtimeState.update { it.copy(isBlockingTraffic = false) }
        serviceScope.launch {
            repository.logEvent(
                type = AuditEventType.FAILED_AUTH,
                title = "قطع شدن دسترسی VPN سیستم",
                detail = "سپر محلی توسط سیستم یا برنامه VPN دیگری غیرفعال شد."
            )
            syncQuickSettingsTile()
        }
        stopForegroundCompat()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        networkMonitor.stop()
        timerJob?.cancel()
        stateObserverJob?.cancel()
        stopTunnel()
        serviceScope.cancel()
        _runtimeState.value = VpnRuntimeState(isServiceRunning = false, isBlockingTraffic = false)
        syncQuickSettingsTile()
        super.onDestroy()
    }

    private fun ensureObservingState() {
        if (stateObserverJob?.isActive == true) return
        stateObserverJob = serviceScope.launch {
            combine(
                repository.settingsFlow,
                networkMonitor.networkState
            ) { settings, net -> settings to net }.collect { (settings, net) ->
                detectBlockedNetworkActivation(settings.isLocked, settings.lockScope, net)
                applyPolicyNow(settings, net)
                syncQuickSettingsTile()
            }
        }
    }

    private fun applyPolicyNow(settings: com.example.data.VaultSettings, net: DeviceNetworkState) {
        if (settings.isLocked) {
            timerJob?.cancel()
            val shouldBlockNow = when (settings.lockScope) {
                LockScope.ALL -> true
                LockScope.CELLULAR_ONLY -> net.isCellularConnected
                LockScope.WIFI_ONLY -> net.isWifiConnected
            }
            if (shouldBlockNow) {
                ensureTunnelEstablished()
            } else {
                stopTunnel()
            }
            updateNotification(buildLockedNotification(settings.lockScope, shouldBlockNow))
        } else {
            stopTunnel()
            val expiresAt = settings.unlockExpiresAtMillis
            if (expiresAt > System.currentTimeMillis()) {
                startTimedCountdown(expiresAt)
            } else if (settings.autoLockOnScreenOff) {
                timerJob?.cancel()
                updateNotification(buildStandbyNotification())
            } else {
                timerJob?.cancel()
                if (hasEnteredForeground) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    private suspend fun detectBlockedNetworkActivation(
        isLocked: Boolean,
        scope: LockScope,
        net: DeviceNetworkState
    ) {
        val prevWifi = lastWifiState
        val prevCell = lastCellularState
        lastWifiState = net.isWifiConnected
        lastCellularState = net.isCellularConnected

        if (!isLocked) return

        val now = System.currentTimeMillis()
        if (now - lastBlockedLogTimeMs < 5_000L) return

        val wifiTurnedOn = prevWifi == false && net.isWifiConnected &&
            (scope == LockScope.ALL || scope == LockScope.WIFI_ONLY)
        val cellTurnedOn = prevCell == false && net.isCellularConnected &&
            (scope == LockScope.ALL || scope == LockScope.CELLULAR_ONLY)

        if (wifiTurnedOn) {
            lastBlockedLogTimeMs = now
            repository.logEvent(
                type = AuditEventType.NETWORK_BLOCKED_ATTEMPT,
                title = "اتصال وای‌فای مسدود شد",
                detail = "وای‌فای هنگام فعال بودن قفل روشن شد، اما ترافیک اینترنت توسط سپر KidShield مسدود ماند."
            )
        } else if (cellTurnedOn) {
            lastBlockedLogTimeMs = now
            repository.logEvent(
                type = AuditEventType.NETWORK_BLOCKED_ATTEMPT,
                title = "اتصال دیتای موبایل مسدود شد",
                detail = "دیتای موبایل هنگام فعال بودن قفل روشن شد، اما ترافیک اینترنت توسط سپر KidShield مسدود ماند."
            )
        }
    }

    private fun startTimedCountdown(expiresAtMillis: Long) {
        timerJob?.cancel()
        if (expiresAtMillis <= 0L) return
        timerJob = serviceScope.launch {
            while (isActive) {
                val remaining = expiresAtMillis - System.currentTimeMillis()
                if (remaining <= 0L) {
                    repository.preferencesStore.setLocked(locked = true, unlockExpiresAtMillis = 0L)
                    repository.logEvent(
                        type = AuditEventType.AUTO_RELOCKED,
                        title = "پایان زمان موقت · قفل خودکار",
                        detail = "زمان مجاز استفاده از اینترنت به پایان رسید و سپر دوباره فعال شد."
                    )
                    break
                }
                updateNotification(buildTimedUnlockNotification(expiresAtMillis))
                delay(1_000L)
            }
        }
    }

    @Synchronized
    private fun ensureTunnelEstablished() {
        if (vpnInterface != null) return
        if (prepare(this) != null) {
            _runtimeState.update {
                it.copy(
                    isBlockingTraffic = false,
                    lastError = "مجوز VPN سیستم تایید نشده است"
                )
            }
            return
        }

        // First try IPv4 + IPv6 full sinkhole; if emulator rejects IPv6 VPN, fall back cleanly to IPv4-only sinkhole
        var established = tryEstablishTunnel(includeIpv6 = true)
        if (established == null) {
            established = tryEstablishTunnel(includeIpv6 = false)
        }

        if (established != null) {
            vpnInterface = established
            _runtimeState.update {
                it.copy(
                    isBlockingTraffic = true,
                    lastError = null
                )
            }
            startPacketSinkDrain(established)
        } else {
            _runtimeState.update {
                it.copy(
                    isBlockingTraffic = false,
                    lastError = "ایجاد تونل فایروال محلی با خطا مواجه شد"
                )
            }
        }
    }

    private fun tryEstablishTunnel(includeIpv6: Boolean): ParcelFileDescriptor? {
        return runCatching {
            val builder = Builder()
                .setSession("KidShield Internet Lock")
                .setMtu(1500)
                .addAddress("10.215.173.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.215.173.2")
                .setBlocking(true)

            if (includeIpv6) {
                builder.addAddress("fd00:215:173::1", 128)
                builder.addRoute("::", 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            // Route ALL device traffic (including browsers, games, and self-test) into the local sinkhole
            builder.establish()
        }.onFailure { err ->
            Log.w(TAG, "tryEstablishTunnel(ipv6=$includeIpv6) failed: ${err.message}")
        }.getOrNull()
    }

    private fun startPacketSinkDrain(pfd: ParcelFileDescriptor) {
        drainJob?.cancel()
        drainJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteBuffer.allocate(32767)
            val input = FileInputStream(pfd.fileDescriptor)
            runCatching {
                while (isActive && vpnInterface == pfd) {
                    val readBytes = input.read(buffer.array())
                    if (readBytes > 0) {
                        _runtimeState.update {
                            it.copy(blockedPacketsCount = it.blockedPacketsCount + 1L)
                        }
                        buffer.clear()
                    } else if (readBytes < 0) {
                        break
                    }
                }
            }
        }
    }

    @Synchronized
    private fun stopTunnel() {
        drainJob?.cancel()
        drainJob = null
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        _runtimeState.update { it.copy(isBlockingTraffic = false) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasEnteredForeground = true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed: ${t.message}")
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        hasEnteredForeground = false
    }

    private fun updateNotification(notification: Notification) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildLockedNotification(
        scope: LockScope = LockScope.ALL,
        blockingNow: Boolean = true
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = when {
            scope == LockScope.ALL -> getString(R.string.vpn_notif_locked_text)
            scope == LockScope.CELLULAR_ONLY && blockingNow -> "دیتای موبایل شناسایی و مسدود شد."
            scope == LockScope.CELLULAR_ONLY -> "در حالت آماده‌باش برای مسدودسازی دیتای موبایل."
            scope == LockScope.WIFI_ONLY && blockingNow -> "وای‌فای شناسایی و مسدود شد."
            else -> "در حالت آماده‌باش برای مسدودسازی وای‌فای."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_tile)
            .setContentTitle(getString(R.string.vpn_notif_locked_title))
            .setContentText(subtitle)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .build()
    }

    private fun buildTimedUnlockNotification(expiresAtMillis: Long): Notification {
        val remainingSec = ((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000L
        val mins = remainingSec / 60L
        val secs = remainingSec % 60L
        val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)

        val lockNowIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, InternetLockVpnService::class.java).apply {
                action = ACTION_ENGAGE_LOCK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_tile)
            .setContentTitle(getString(R.string.vpn_notif_timed_title))
            .setContentText(getString(R.string.vpn_notif_timed_text, formatted))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(lockNowIntent)
            .addAction(0, "قفل فوری", lockNowIntent)
            .build()
    }

    private fun buildStandbyNotification(): Notification {
        val lockNowIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, InternetLockVpnService::class.java).apply {
                action = ACTION_ENGAGE_LOCK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_tile)
            .setContentTitle("اینترنت باز است (قفل خودکار صفحه فعال)")
            .setContentText("با خاموش شدن صفحه یا لمس این اعلان، اینترنت فوراً قفل می‌شود.")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(lockNowIntent)
            .build()
    }

    private fun syncQuickSettingsTile() {
        runCatching {
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, InternetGuardTileService::class.java)
            )
        }
    }

    companion object {
        private const val TAG = "KidShieldVpn"
        const val CHANNEL_ID = "kidshield_vpn_shield"
        const val NOTIFICATION_ID = 4201
        const val ACTION_ENGAGE_LOCK = "com.example.action.ENGAGE_LOCK"
        const val ACTION_TIMED_UNLOCK = "com.example.action.TIMED_UNLOCK"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val EXTRA_EXPIRES_AT = "extra_expires_at"

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        fun startLock(context: Context) {
            val intent = Intent(context, InternetLockVpnService::class.java).apply {
                action = ACTION_ENGAGE_LOCK
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startTimedUnlock(context: Context, expiresAtMillis: Long) {
            val intent = Intent(context, InternetLockVpnService::class.java).apply {
                action = ACTION_TIMED_UNLOCK
                putExtra(EXTRA_EXPIRES_AT, expiresAtMillis)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopGuard(context: Context) {
            runCatching {
                context.stopService(Intent(context, InternetLockVpnService::class.java))
            }
        }
    }
}
