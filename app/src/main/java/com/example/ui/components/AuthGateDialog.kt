package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.UnlockDuration
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.AuthGateState
import com.example.viewmodel.AuthPurpose
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthGateDialog(
    state: AuthGateState,
    biometricAvailable: Boolean,
    lockoutRemainingSeconds: Long,
    onSelectDuration: (UnlockDuration) -> Unit,
    onSubmitPin: (String) -> Unit,
    onRequestBiometric: () -> Unit,
    onEnterRecovery: () -> Unit,
    onExitRecovery: () -> Unit,
    onSubmitRecoveryAnswer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember(state.isSettingNewPin, state.newPinFirstEntry, state.errorCounter) {
        mutableStateOf("")
    }
    var recoveryAnswer by remember(state.isRecoveryMode) { mutableStateOf("") }

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state.errorCounter) {
        if (state.errorCounter > 0) {
            enteredPin = ""
            val deltas = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
            for (d in deltas) {
                shakeOffset.animateTo(d, animationSpec = tween(42))
            }
        }
    }

    val titleText = when {
        state.isRecoveryMode -> "بازیابی رمز والدین"
        state.isSettingNewPin && state.newPinFirstEntry.isEmpty() -> "رمز جدید ۴ رقمی را وارد کنید"
        state.isSettingNewPin -> "تکرار رمز جدید برای تایید"
        else -> stringResource(R.string.auth_title_unlock)
    }

    val subtitleText = when {
        state.isRecoveryMode -> stringResource(R.string.setup_recovery_question)
        state.isSettingNewPin -> "رمز جدید بلافاصله برای محافظت اینترنت اعمال می‌شود."
        state.purpose is AuthPurpose.UnlockInternet -> stringResource(R.string.auth_subtitle_unlock)
        state.purpose is AuthPurpose.OpenNetworkSettings -> stringResource(R.string.auth_subtitle_network)
        else -> stringResource(R.string.auth_subtitle_settings)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isSettingNewPin) Icons.Filled.Shield else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Unlock Duration Selector when unlocking internet
                if (state.purpose is AuthPurpose.UnlockInternet && !state.isRecoveryMode && !state.isSettingNewPin) {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val durations = listOf(
                            UnlockDuration.MINUTES_15 to R.string.duration_15m,
                            UnlockDuration.MINUTES_30 to R.string.duration_30m,
                            UnlockDuration.MINUTES_60 to R.string.duration_60m,
                            UnlockDuration.UNLIMITED to R.string.duration_unlimited
                        )
                        durations.forEach { (duration, labelRes) ->
                            val selected = state.selectedDuration == duration
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDuration(duration) },
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("duration_chip_${duration.name.lowercase()}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.isRecoveryMode) {
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text(stringResource(R.string.setup_recovery_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_answer_input")
                    )

                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onSubmitRecoveryAnswer(recoveryAnswer) },
                        enabled = recoveryAnswer.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("recovery_verify_button")
                    ) {
                        Text(stringResource(R.string.auth_recovery_verify))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onExitRecovery,
                        modifier = Modifier.testTag("recovery_back_button")
                    ) {
                        Text("بازگشت به صفحه رمز")
                    }
                } else {
                    // PIN Dots Row (LTR for intuitive digit entry order)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                val filled = index < enteredPin.length
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (filled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (filled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }

                    // Error / Lockout Banner
                    AnimatedVisibility(visible = lockoutRemainingSeconds > 0L || state.errorMessage != null) {
                        val message = if (lockoutRemainingSeconds > 0L) {
                            stringResource(R.string.auth_lockout_message, lockoutRemainingSeconds)
                        } else {
                            state.errorMessage.orEmpty()
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Numeric Keypad (LTR layout 1-2-3 / 4-5-6 / 7-8-9 / bio-0-back)
                    PinKeypad(
                        enabled = lockoutRemainingSeconds == 0L,
                        showBiometric = biometricAvailable && !state.isSettingNewPin,
                        onDigit = { digit ->
                            if (enteredPin.length < 4) {
                                val next = enteredPin + digit
                                enteredPin = next
                                if (next.length == 4) {
                                    onSubmitPin(next)
                                }
                            }
                        },
                        onBackspace = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        },
                        onBiometricClick = onRequestBiometric
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!state.isSettingNewPin) {
                            TextButton(
                                onClick = onEnterRecovery,
                                modifier = Modifier.testTag("forgot_pin_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_forgot_pin),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("auth_cancel_button")
                        ) {
                            Text(stringResource(R.string.auth_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinKeypad(
    enabled: Boolean,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { digit ->
                        KeypadCircleButton(
                            enabled = enabled,
                            onClick = { onDigit(digit) },
                            tag = "pin_key_$digit"
                        ) {
                            Text(
                                text = digit,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBiometric) {
                    KeypadCircleButton(
                        enabled = enabled,
                        highlighted = true,
                        onClick = onBiometricClick,
                        tag = "pin_key_biometric"
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = stringResource(R.string.auth_use_biometric),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(64.dp))
                }

                KeypadCircleButton(
                    enabled = enabled,
                    onClick = { onDigit("0") },
                    tag = "pin_key_0"
                ) {
                    Text(
                        text = "0",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                KeypadCircleButton(
                    enabled = enabled,
                    onClick = onBackspace,
                    tag = "pin_key_backspace"
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "حذف رقم",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    enabled: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    tag: String,
    content: @Composable () -> Unit
) {
    val bgColor = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.9f else 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.85f else 0.35f)
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
