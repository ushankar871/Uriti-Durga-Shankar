package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = KumkumPrimary,
    onPrimary = KumkumOnPrimary,
    primaryContainer = KumkumPrimaryContainer,
    onPrimaryContainer = KumkumOnPrimaryContainer,
    secondary = SaffronSecondary,
    onSecondary = SaffronOnSecondary,
    secondaryContainer = SaffronSecondaryContainer,
    onSecondaryContainer = SaffronOnSecondaryContainer,
    tertiary = TulsiTertiary,
    onTertiary = TulsiOnTertiary,
    tertiaryContainer = TulsiContainer,
    onTertiaryContainer = TulsiOnContainer,
    error = OutOfStockRed,
    errorContainer = OutOfStockContainer,
    onErrorContainer = OutOfStockOnContainer,
    background = WarmParchment,
    onBackground = CharcoalInk,
    surface = AlabasterSurface,
    onSurface = CharcoalInk,
    surfaceVariant = WarmSandContainer,
    onSurfaceVariant = MutedUmber,
    surfaceContainerLow = WarmParchment,
    surfaceContainer = WarmSandContainer,
    surfaceContainerHigh = WarmSandHigh,
    outline = SoftBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkKumkumPrimary,
    onPrimary = DarkKumkumOnPrimary,
    primaryContainer = DarkKumkumContainer,
    onPrimaryContainer = KumkumPrimaryContainer,
    secondary = DarkSaffronSecondary,
    onSecondary = CharcoalInk,
    secondaryContainer = DarkSaffronContainer,
    onSecondaryContainer = SaffronSecondaryContainer,
    tertiary = DarkTulsiTertiary,
    onTertiary = CharcoalInk,
    tertiaryContainer = DarkTulsiContainer,
    onTertiaryContainer = TulsiContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = SoftBorder,
    surfaceContainer = DarkSurfaceContainer
)

val ShubhMartShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ShubhMartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShubhMartTypography,
        shapes = ShubhMartShapes,
        content = content
    )
}
