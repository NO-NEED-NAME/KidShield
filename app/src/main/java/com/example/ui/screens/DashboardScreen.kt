package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LockScope
import com.example.ui.components.ShieldOrb
import com.example.viewmodel.ConnectivityTestStatus
import com.example.viewmodel.ModirManUiState

@Composable
fun DashboardScreen(
    uiState: ModirManUiState,
    onLockNow: () -> Unit,
    onUnlockRequest: () -> Unit,
    onSelectScope: (LockScope) -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onRunConnectivityTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = uiState.settings.isLocked
    val scope = uiState.settings.lockScope
    val net = uiState.networkState
    val isBlockingNow = uiState.vpnRuntime.isBlockingTraffic
    val vpnNeedsRebind = isLocked && scope == LockScope.ALL && !isBlockingNow
    val accent = if (isLocked && !vpnNeedsRebind) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val statusTitle = when {
        vpnNeedsRebind -> "نیاز به تایید فایروال محلی VPN"
        isLocked && scope == LockScope.ALL -> stringResource(R.string.status_locked_title)
        isLocked && scope == LockScope.WIFI_ONLY -> stringResource(R.string.status_locked_partial_wifi)
        isLocked && scope == LockScope.CELLULAR_ONLY -> stringResource(R.string.status_locked_partial_cellular)
        else -> stringResource(R.string.status_unlocked_title)
    }

    val statusSubtitle = when {
        vpnNeedsRebind -> "برای مسدودسازی واقعی ترافیک، دکمه فعال‌سازی فایروال را لمس کنید"
        isLocked -> stringResource(R.string.status_locked_subtitle)
        uiState.remainingUnlockSeconds > 0L -> stringResource(R.string.status_unlocked_timed)
        else -> stringResource(R.string.status_unlocked_manual)
    }

    val isWifiBlocked = isLocked && (scope == LockScope.ALL || scope == LockScope.WIFI_ONLY) && isBlockingNow
    val isCellBlocked = isLocked && (scope == LockScope.ALL || scope == LockScope.CELLULAR_ONLY) && isBlockingNow

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_persian_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLocked && !vpnNeedsRebind) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (vpnNeedsRebind) Icons.Filled.WarningAmber else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                vpnNeedsRebind -> "نیاز به تایید"
                                isLocked -> "محافظت فعال"
                                else -> "حالت آزاد"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLocked && !vpnNeedsRebind) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Hero Shield Orb
            ShieldOrb(
                isLocked = isLocked && !vpnNeedsRebind,
                isBlockingTraffic = isBlockingNow,
                remainingUnlockSeconds = uiState.remainingUnlockSeconds,
                blockedPacketsCount = uiState.vpnRuntime.blockedPacketsCount,
                onToggleClick = {
                    when {
                        vpnNeedsRebind -> onLockNow()
                        isLocked -> onUnlockRequest()
                        else -> onLockNow()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (uiState.vpnRuntime.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = uiState.vpnRuntime.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Action CTA Button
            Button(
                onClick = {
                    when {
                        vpnNeedsRebind -> onLockNow()
                        isLocked -> onUnlockRequest()
                        else -> onLockNow()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("primary_lock_action_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked && !vpnNeedsRebind) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isLocked && !vpnNeedsRebind) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                ),
                border = if (isLocked && !vpnNeedsRebind) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                } else null
            ) {
                Icon(
                    imageVector = if (isLocked && !vpnNeedsRebind) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = when {
                        vpnNeedsRebind -> "فعال‌سازی مجدد فایروال VPN"
                        isLocked -> stringResource(R.string.action_tap_to_unlock)
                        else -> stringResource(R.string.action_tap_to_lock)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Internet Block Verification Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (uiState.connectivityTestStatus) {
                                    ConnectivityTestStatus.BLOCKED_SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                                    ConnectivityTestStatus.INTERNET_OPEN -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.connectivityTestStatus == ConnectivityTestStatus.TESTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.NetworkCheck,
                                contentDescription = null,
                                tint = when (uiState.connectivityTestStatus) {
                                    ConnectivityTestStatus.BLOCKED_SUCCESS -> MaterialTheme.colorScheme.primary
                                    ConnectivityTestStatus.INTERNET_OPEN -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تست واقعی قطع اینترنت",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val resultText = when (uiState.connectivityTestStatus) {
                            ConnectivityTestStatus.IDLE -> "بررسی کنید آیا ترافیک شبکه واقعاً مسدود شده است"
                            ConnectivityTestStatus.TESTING -> "در حال ارسال بسته تست شبکه..."
                            ConnectivityTestStatus.BLOCKED_SUCCESS -> "موفق: دسترسی به اینترنت کاملاً مسدود است!"
                            ConnectivityTestStatus.INTERNET_OPEN -> "اینترنت در حال حاضر باز و قابل دسترس است."
                        }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.connectivityTestStatus) {
                                ConnectivityTestStatus.BLOCKED_SUCCESS -> MaterialTheme.colorScheme.primary
                                ConnectivityTestStatus.INTERNET_OPEN -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = onRunConnectivityTest,
                        enabled = uiState.connectivityTestStatus != ConnectivityTestStatus.TESTING,
                        modifier = Modifier.testTag("connectivity_test_button")
                    ) {
                        Text("تست")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Wi-Fi & Mobile Data Hardware Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NetworkMonitorCard(
                    title = stringResource(R.string.network_wifi_title),
                    isConnected = net.isWifiConnected,
                    isGuarded = isWifiBlocked,
                    activeIcon = Icons.Filled.Wifi,
                    offIcon = Icons.Filled.WifiOff,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wifi_status_card")
                )
                NetworkMonitorCard(
                    title = stringResource(R.string.network_data_title),
                    isConnected = net.isCellularConnected,
                    isGuarded = isCellBlocked,
                    activeIcon = Icons.Filled.SignalCellularAlt,
                    offIcon = Icons.Filled.PublicOff,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cellular_status_card")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Protected System Internet Panel Shortcut
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenNetworkSettings)
                    .testTag("open_network_panel_card"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsEthernet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.network_settings_button),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLocked) {
                                "نیازمند احراز هویت والدین برای باز کردن پنل وای‌فای و دیتا"
                            } else {
                                "دسترسی سریع به پنل وای‌فای و دیتای اندروید"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lock Scope Selector Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.scope_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                ScopeOptionCard(
                    title = stringResource(R.string.scope_all),
                    subtitle = "قطع کامل اینترنت چه روی وای‌فای و چه روی دیتای سیم‌کارت",
                    icon = Icons.Filled.Shield,
                    selected = scope == LockScope.ALL,
                    requiresAuthBadge = isLocked && scope != LockScope.ALL,
                    onClick = { onSelectScope(LockScope.ALL) },
                    tag = "scope_all_card"
                )
                ScopeOptionCard(
                    title = stringResource(R.string.scope_cellular_only),
                    subtitle = "فایروال فقط هنگام روشن شدن دیتای موبایل فعال می‌شود",
                    icon = Icons.Filled.CellTower,
                    selected = scope == LockScope.CELLULAR_ONLY,
                    requiresAuthBadge = isLocked && scope != LockScope.CELLULAR_ONLY,
                    onClick = { onSelectScope(LockScope.CELLULAR_ONLY) },
                    tag = "scope_cellular_card"
                )
                ScopeOptionCard(
                    title = stringResource(R.string.scope_wifi_only),
                    subtitle = "فایروال فقط هنگام اتصال به وای‌فای فعال می‌شود",
                    icon = Icons.Filled.Wifi,
                    selected = scope == LockScope.WIFI_ONLY,
                    requiresAuthBadge = isLocked && scope != LockScope.WIFI_ONLY,
                    onClick = { onSelectScope(LockScope.WIFI_ONLY) },
                    tag = "scope_wifi_card"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NetworkMonitorCard(
    title: String,
    isConnected: Boolean,
    isGuarded: Boolean,
    activeIcon: ImageVector,
    offIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    val statusColor: Color
    val statusLabel: String
    when {
        isConnected && isGuarded -> {
            statusColor = MaterialTheme.colorScheme.error
            statusLabel = stringResource(R.string.network_state_blocked)
        }
        isConnected -> {
            statusColor = MaterialTheme.colorScheme.secondary
            statusLabel = stringResource(R.string.network_state_active)
        }
        else -> {
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusLabel = stringResource(R.string.network_state_off)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected && isGuarded) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) activeIcon else offIcon,
                        contentDescription = title,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isGuarded) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "تحت محافظت",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun ScopeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    requiresAuthBadge: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (requiresAuthBadge) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "نیازمند رمز",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
