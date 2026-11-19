package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.security.BiometricAuthHelper
import com.example.ui.components.AuthGateDialog
import com.example.ui.screens.ActivityLogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SetupScreen
import com.example.viewmodel.MainTab
import com.example.viewmodel.ModirManViewModel
import com.example.viewmodel.UiEffect

@Composable
fun ModirManApp(
    viewModel: ModirManViewModel,
    activity: FragmentActivity,
    pendingTileAction: String?,
    onTileActionConsumed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    // Handle one-shot effects from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.RequestVpnConsent -> {
                    vpnConsentLauncher.launch(effect.intent)
                }
                UiEffect.OpenSystemInternetSettings -> {
                    val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    } else {
                        Intent(Settings.ACTION_WIFI_SETTINGS)
                    }
                    runCatching { context.startActivity(panelIntent) }
                        .onFailure {
                            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                        }
                }
                is UiEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                UiEffect.LaunchBiometricPrompt -> {
                    BiometricAuthHelper.authenticate(
                        activity = activity,
                        title = context.getString(R.string.auth_title_unlock),
                        subtitle = context.getString(R.string.auth_use_biometric),
                        onSuccess = { viewModel.onBiometricSucceeded() },
                        onError = { err -> viewModel.onBiometricError(err) }
                    )
                }
            }
        }
    }

    // Handle Quick Settings Tile launch intents once setup state is loaded
    LaunchedEffect(uiState.isLoaded, uiState.settings.isSetupComplete, pendingTileAction) {
        if (uiState.isLoaded && uiState.settings.isSetupComplete && pendingTileAction != null) {
            when (pendingTileAction) {
                com.example.MainActivity.ACTION_FROM_TILE_UNLOCK -> {
                    if (uiState.settings.isLocked) {
                        viewModel.requestUnlockInternet()
                    }
                }
                com.example.MainActivity.ACTION_FROM_TILE_LOCK -> {
                    if (!uiState.settings.isLocked) {
                        viewModel.requestLockInternet()
                    }
                }
            }
            onTileActionConsumed()
        }
    }

    if (!uiState.isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.settings.isSetupComplete) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            SetupScreen(
                isBiometricSupported = uiState.isBiometricSupported,
                onSetupComplete = { pin, recovery, bio, lockNow ->
                    viewModel.completeInitialSetup(pin, recovery, bio, lockNow)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
        return
    }

    // Back button returns from secondary tabs to Shield dashboard
    BackHandler(enabled = uiState.activeTab != MainTab.SHIELD) {
        viewModel.selectTab(MainTab.SHIELD)
    }

    val navItems = listOf(
        Triple(MainTab.SHIELD, R.string.nav_shield, Icons.Filled.Shield to Icons.Outlined.Shield),
        Triple(MainTab.RULES, R.string.nav_rules, Icons.Filled.Tune to Icons.Outlined.Tune),
        Triple(MainTab.ACTIVITY, R.string.nav_activity, Icons.Filled.History to Icons.Outlined.History)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        navItems.forEach { (tab, labelRes, icons) ->
                            val selected = uiState.activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = stringResource(labelRes)
                                    )
                                },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { (tab, labelRes, icons) ->
                            val selected = uiState.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = stringResource(labelRes)
                                    )
                                },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (uiState.activeTab) {
                        MainTab.SHIELD -> DashboardScreen(
                            uiState = uiState,
                            onLockNow = { viewModel.requestLockInternet() },
                            onUnlockRequest = { viewModel.requestUnlockInternet() },
                            onSelectScope = { viewModel.requestChangeScope(it) },
                            onOpenNetworkSettings = { viewModel.requestOpenNetworkSettings() },
                            onRunConnectivityTest = { viewModel.runInternetBlockTest() }
                        )
                        MainTab.RULES -> RulesScreen(
                            uiState = uiState,
                            notificationsGranted = notificationsGranted,
                            onToggleScreenOffLock = { viewModel.requestToggleAutoLockScreenOff(it) },
                            onToggleBiometric = { viewModel.requestToggleBiometric(it) },
                            onChangePinClick = { viewModel.requestChangePin() },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenVpnSettings = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                                }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                            }
                        )
                        MainTab.ACTIVITY -> ActivityLogScreen(
                            uiState = uiState,
                            onSelectFilter = { viewModel.selectLogFilter(it) },
                            onClearLogsRequest = { viewModel.requestClearLogs() }
                        )
                    }
                }
            }
        }
    }

    // Parent Authentication Modal Gate
    val authGate = uiState.authGate
    if (authGate != null) {
        AuthGateDialog(
            state = authGate,
            biometricAvailable = uiState.settings.biometricEnabled && uiState.isBiometricSupported,
            lockoutRemainingSeconds = uiState.lockoutRemainingSeconds,
            onSelectDuration = { viewModel.updateSelectedUnlockDuration(it) },
            onSubmitPin = { viewModel.submitPin(it) },
            onRequestBiometric = { viewModel.triggerBiometricFromGate() },
            onEnterRecovery = { viewModel.enterRecoveryMode() },
            onExitRecovery = { viewModel.exitRecoveryMode() },
            onSubmitRecoveryAnswer = { viewModel.submitRecoveryAnswer(it) },
            onDismiss = { viewModel.dismissAuthGate() }
        )
    }
}
