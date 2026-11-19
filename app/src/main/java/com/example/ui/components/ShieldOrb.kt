package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun ShieldOrb(
    isLocked: Boolean,
    isBlockingTraffic: Boolean,
    remainingUnlockSeconds: Long,
    blockedPacketsCount: Long,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor by animateColorAsState(
        targetValue = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        animationSpec = tween(400),
        label = "orbPrimaryColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isLocked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = tween(400),
        label = "orbContainerColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shieldPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val coreScale by animateFloatAsState(
        targetValue = if (isLocked) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "coreScale"
    )

    val formattedTimer = remember(remainingUnlockSeconds) {
        if (remainingUnlockSeconds > 0L) {
            val mins = remainingUnlockSeconds / 60L
            val secs = remainingUnlockSeconds % 60L
            String.format(Locale.US, "%02d:%02d", mins, secs)
        } else {
            null
        }
    }

    val accessibilityLabel = if (isLocked) {
        "اینترنت قفل است. برای احراز هویت و باز کردن لمس کنید"
    } else {
        "اینترنت باز است. برای قفل فوری لمس کنید"
    }

    Box(
        modifier = modifier
            .size(244.dp)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        // Luminous pulsing aura canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.24f),
                        primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Pulsing radar ring
            drawCircle(
                color = primaryColor.copy(alpha = pulseAlpha),
                radius = maxRadius * 0.84f * pulseScale,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Precision dashed/segmented tech ring
            val ringRadius = maxRadius * 0.74f
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
            val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            drawArc(
                color = primaryColor.copy(alpha = 0.45f),
                startAngle = -90f,
                sweepAngle = if (isLocked) 360f else 280f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Tactile Interactive Core Button
        Box(
            modifier = Modifier
                .size(164.dp)
                .scale(coreScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            containerColor,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.85f),
                            primaryColor.copy(alpha = 0.25f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = primaryColor),
                    role = Role.Button,
                    onClick = onToggleClick
                )
                .testTag("shield_toggle_orb"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (formattedTimer != null && !isLocked) {
                    Text(
                        text = formattedTimer,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = primaryColor
                    )
                    Text(
                        text = "تا قفل خودکار",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (isLocked) "قفل فعال" else "اینترنت باز",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLocked) "لمس برای باز کردن" else "لمس برای قفل فوری",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor
                    )
                }
            }
        }

        // Bottom status pill badge
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp)
                )
                val badgeText = when {
                    isLocked && isBlockingTraffic && blockedPacketsCount > 0L ->
                        "فایروال فعال · $blockedPacketsCount بسته مسدود"
                    isLocked && isBlockingTraffic ->
                        "فایروال محلی فعال"
                    isLocked ->
                        "آماده‌باش هوشمند"
                    else ->
                        "فایروال متوقف"
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
