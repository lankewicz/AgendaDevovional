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

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

@Composable
fun AgendaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Desabilitado para manter o design consistente
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}