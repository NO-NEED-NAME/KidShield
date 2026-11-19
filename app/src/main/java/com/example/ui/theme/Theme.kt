package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ShieldEmeraldBright,
    onPrimary = Color(0xFF002116),
    primaryContainer = ShieldEmeraldContainer,
    onPrimaryContainer = ShieldEmeraldOnContainer,
    secondary = AlertAmberBright,
    onSecondary = Color(0xFF281900),
    secondaryContainer = AlertAmberContainer,
    onSecondaryContainer = AlertAmberOnContainer,
    tertiary = SapphireAccent,
    onTertiary = Color(0xFF001F30),
    tertiaryContainer = SapphireContainer,
    onTertiaryContainer = SapphireOnContainer,
    error = CrimsonBright,
    onError = Color(0xFF3B080C),
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonOnContainer,
    background = MidnightBg,
    onBackground = IceTextPrimary,
    surface = MidnightSurface,
    onSurface = IceTextPrimary,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = IceTextSecondary,
    surfaceContainerLowest = MidnightBg,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceHigh,
    surfaceContainerHigh = MidnightSurfaceHighest,
    outline = MidnightBorder,
    outlineVariant = Color(0xFF1A2D4A)
)

private val LightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightEmeraldContainer,
    onPrimaryContainer = LightEmeraldOnContainer,
    secondary = LightAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = LightAmberOnContainer,
    tertiary = LightSapphireTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightSapphireContainer,
    onTertiaryContainer = LightSapphireOnContainer,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = AlabasterBg,
    onBackground = DeepSlateText,
    surface = AlabasterSurface,
    onSurface = DeepSlateText,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

private val ModirManShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ModirManTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = ModirManShapes,
            content = content
        )
    }
}
