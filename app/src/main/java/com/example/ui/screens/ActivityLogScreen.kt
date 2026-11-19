package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AuditEventType
import com.example.data.AuditLogEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.LogFilter
import com.example.viewmodel.ModirManUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogScreen(
    uiState: ModirManUiState,
    onSelectFilter: (LogFilter) -> Unit,
    onClearLogsRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs = uiState.filteredLogs

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.log_header),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "تاریخچه محلی قفل‌گذاری و تلاش‌های ناموفق ورود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.logs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearLogsRequest,
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.log_clear_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.logFilter == LogFilter.ALL,
                    onClick = { onSelectFilter(LogFilter.ALL) },
                    label = { Text(stringResource(R.string.log_filter_all)) },
                    modifier = Modifier.testTag("log_filter_all")
                )
                FilterChip(
                    selected = uiState.logFilter == LogFilter.ALERTS,
                    onClick = { onSelectFilter(LogFilter.ALERTS) },
                    label = { Text(stringResource(R.string.log_filter_alerts)) },
                    modifier = Modifier.testTag("log_filter_alerts")
                )
                FilterChip(
                    selected = uiState.logFilter == LogFilter.LOCKS,
                    onClick = { onSelectFilter(LogFilter.LOCKS) },
                    label = { Text(stringResource(R.string.log_filter_locks)) },
                    modifier = Modifier.testTag("log_filter_locks")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.log_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.log_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { entry ->
                        AuditLogItemCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogItemCard(entry: AuditLogEntity) {
    val eventType = remember(entry.eventType) { AuditEventType.fromId(entry.eventType) }
    val timeText = remember(entry.timestampMillis) {
        val formatter = SimpleDateFormat("HH:mm:ss · yyyy/MM/dd", Locale.US)
        formatter.format(Date(entry.timestampMillis))
    }

    val (icon, tint) = eventVisuals(eventType)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (eventType.isAlert) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (eventType.isAlert) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timeText,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun eventVisuals(type: AuditEventType): Pair<ImageVector, Color> {
    return when (type) {
        AuditEventType.LOCKED -> Icons.Filled.Lock to MaterialTheme.colorScheme.primary
        AuditEventType.AUTO_RELOCKED -> Icons.Filled.LockClock to MaterialTheme.colorScheme.primary
        AuditEventType.UNLOCKED_PIN -> Icons.Filled.LockOpen to MaterialTheme.colorScheme.secondary
        AuditEventType.UNLOCKED_BIOMETRIC -> Icons.Filled.Fingerprint to MaterialTheme.colorScheme.secondary
        AuditEventType.FAILED_AUTH -> Icons.Filled.GppBad to MaterialTheme.colorScheme.error
        AuditEventType.NETWORK_BLOCKED_ATTEMPT -> Icons.Filled.WarningAmber to MaterialTheme.colorScheme.error
        AuditEventType.PIN_CHANGED, AuditEventType.SCOPE_CHANGED -> Icons.Filled.Tune to MaterialTheme.colorScheme.tertiary
    }
}
