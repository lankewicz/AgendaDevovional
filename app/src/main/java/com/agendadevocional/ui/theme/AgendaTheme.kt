package com.agendadevocional.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 1. Classic Gold (Padrão) Color Schemes
private val LightGoldColors = lightColorScheme(
    primary = ElegantGold,
    onPrimary = PureWhite,
    primaryContainer = GoldSoft,
    onPrimaryContainer = DeepCharcoal,

    secondary = DeepCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = CreamSurface,
    onSecondaryContainer = DeepCharcoal,

    background = PaperWhite,
    onBackground = DeepCharcoal,

    surface = PureWhite,
    onSurface = DeepCharcoal,
    surfaceVariant = CreamSurface,
    onSurfaceVariant = DeepCharcoal,

    outline = ElegantGold.copy(alpha = 0.5f),
    outlineVariant = CreamSurface.copy(alpha = 0.8f),

    error = Color(0xFFB00020),
    onError = PureWhite
)

private val DarkGoldColors = darkColorScheme(
    primary = GoldAccentDark,
    onPrimary = MidnightBackground,
    primaryContainer = ObsidianSurfaceVariant,
    onPrimaryContainer = GoldAccentDark,

    secondary = ObsidianSurfaceVariant,
    onSecondary = TextLight,
    secondaryContainer = ObsidianSurface,
    onSecondaryContainer = TextLight,

    background = MidnightBackground,
    onBackground = TextLight,

    surface = ObsidianSurface,
    onSurface = TextLight,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextMuted,

    outline = GoldAccentDark.copy(alpha = 0.3f),
    outlineVariant = ObsidianSurfaceVariant.copy(alpha = 0.5f),

    error = Color(0xFFCF6679),
    onError = MidnightBackground
)

// 2. Olive Garden (Sage Green) Color Schemes
private val LightOliveColors = lightColorScheme(
    primary = SageGreen,
    onPrimary = PureWhite,
    primaryContainer = SageSoft,
    onPrimaryContainer = DeepCharcoal,

    secondary = DeepCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = OliveSurfaceLight,
    onSecondaryContainer = DeepCharcoal,

    background = OliveBackgroundLight,
    onBackground = DeepCharcoal,

    surface = PureWhite,
    onSurface = DeepCharcoal,
    surfaceVariant = OliveSurfaceLight,
    onSurfaceVariant = DeepCharcoal,

    outline = SageGreen.copy(alpha = 0.5f),
    outlineVariant = OliveSurfaceLight.copy(alpha = 0.8f),

    error = Color(0xFFB00020),
    onError = PureWhite
)

private val DarkOliveColors = darkColorScheme(
    primary = SageDarkAccent,
    onPrimary = ForestBackgroundDark,
    primaryContainer = ForestSurfaceVariantDark,
    onPrimaryContainer = SageDarkAccent,

    secondary = ForestSurfaceVariantDark,
    onSecondary = TextLight,
    secondaryContainer = ForestSurfaceDark,
    onSecondaryContainer = TextLight,

    background = ForestBackgroundDark,
    onBackground = TextLight,

    surface = ForestSurfaceDark,
    onSurface = TextLight,
    surfaceVariant = ForestSurfaceVariantDark,
    onSurfaceVariant = TextMuted,

    outline = SageDarkAccent.copy(alpha = 0.3f),
    outlineVariant = ForestSurfaceVariantDark.copy(alpha = 0.5f),

    error = Color(0xFFCF6679),
    onError = ForestBackgroundDark
)

// 3. Royal Blue (Indigo / Navy) Color Schemes
private val LightRoyalColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = BlueSoft,
    onPrimaryContainer = DeepCharcoal,

    secondary = DeepCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = BlueSurfaceLight,
    onSecondaryContainer = DeepCharcoal,

    background = BlueBackgroundLight,
    onBackground = DeepCharcoal,

    surface = PureWhite,
    onSurface = DeepCharcoal,
    surfaceVariant = BlueSurfaceLight,
    onSurfaceVariant = DeepCharcoal,

    outline = RoyalBlue.copy(alpha = 0.5f),
    outlineVariant = BlueSurfaceLight.copy(alpha = 0.8f),

    error = Color(0xFFB00020),
    onError = PureWhite
)

private val DarkRoyalColors = darkColorScheme(
    primary = BlueDarkAccent,
    onPrimary = NavyBackgroundDark,
    primaryContainer = NavySurfaceVariantDark,
    onPrimaryContainer = BlueDarkAccent,

    secondary = NavySurfaceVariantDark,
    onSecondary = TextLight,
    secondaryContainer = NavySurfaceDark,
    onSecondaryContainer = TextLight,

    background = NavyBackgroundDark,
    onBackground = TextLight,

    surface = NavySurfaceDark,
    onSurface = TextLight,
    surfaceVariant = NavySurfaceVariantDark,
    onSurfaceVariant = TextMuted,

    outline = BlueDarkAccent.copy(alpha = 0.3f),
    outlineVariant = NavySurfaceVariantDark.copy(alpha = 0.5f),

    error = Color(0xFFCF6679),
    onError = NavyBackgroundDark
)

// 4. Rose & Wine (Rose / Plum) Color Schemes
private val LightRoseColors = lightColorScheme(
    primary = RoseWine,
    onPrimary = PureWhite,
    primaryContainer = RoseSoft,
    onPrimaryContainer = DeepCharcoal,

    secondary = DeepCharcoal,
    onSecondary = PureWhite,
    secondaryContainer = RoseSurfaceLight,
    onSecondaryContainer = DeepCharcoal,

    background = RoseBackgroundLight,
    onBackground = DeepCharcoal,

    surface = PureWhite,
    onSurface = DeepCharcoal,
    surfaceVariant = RoseSurfaceLight,
    onSurfaceVariant = DeepCharcoal,

    outline = RoseWine.copy(alpha = 0.5f),
    outlineVariant = RoseSurfaceLight.copy(alpha = 0.8f),

    error = Color(0xFFB00020),
    onError = PureWhite
)

private val DarkRoseColors = darkColorScheme(
    primary = RoseDarkAccent,
    onPrimary = WineBackgroundDark,
    primaryContainer = WineSurfaceVariantDark,
    onPrimaryContainer = RoseDarkAccent,

    secondary = WineSurfaceVariantDark,
    onSecondary = TextLight,
    secondaryContainer = WineSurfaceDark,
    onSecondaryContainer = TextLight,

    background = WineBackgroundDark,
    onBackground = TextLight,

    surface = WineSurfaceDark,
    onSurface = TextLight,
    surfaceVariant = WineSurfaceVariantDark,
    onSurfaceVariant = TextMuted,

    outline = RoseDarkAccent.copy(alpha = 0.3f),
    outlineVariant = WineSurfaceVariantDark.copy(alpha = 0.5f),

    error = Color(0xFFCF6679),
    onError = WineBackgroundDark
)

@Composable
fun AgendaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeStyle: String = "gold", // "gold", "olive", "royal", "rose"
    dynamicColor: Boolean = false, // Desabilitado para manter o design consistente
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (themeStyle) {
            "olive" -> DarkOliveColors
            "royal" -> DarkRoyalColors
            "rose" -> DarkRoseColors
            else -> DarkGoldColors
        }
        else -> when (themeStyle) {
            "olive" -> LightOliveColors
            "royal" -> LightRoyalColors
            "rose" -> LightRoseColors
            else -> LightGoldColors
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}