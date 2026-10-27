package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    themeMode: String = "dark",
    accentPalette: String = "crimson_gold",
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) {
        val (primary, secondary, tertiary) = when (accentPalette) {
            "jade_bamboo" -> Triple(JadePrimaryDark, JadeSecondaryDark, JadeTertiaryDark)
            "indigo_moon" -> Triple(IndigoPrimaryDark, IndigoSecondaryDark, IndigoTertiaryDark)
            "royal_lotus" -> Triple(LotusPrimaryDark, LotusSecondaryDark, LotusTertiaryDark)
            else -> Triple(CrimsonPrimaryDark, CrimsonSecondaryDark, CrimsonTertiaryDark)
        }
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF1A0E10),
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color(0xFF1C1708),
            secondaryContainer = secondary.copy(alpha = 0.2f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
            background = OledDarkBackground,
            onBackground = Color(0xFFF5F2EB),
            surface = OledDarkSurface,
            onSurface = Color(0xFFF5F2EB),
            surfaceVariant = OledDarkSurfaceVariant,
            onSurfaceVariant = Color(0xFFC5C8D4)
        )
    } else {
        val (primary, secondary, tertiary) = when (accentPalette) {
            "jade_bamboo" -> Triple(JadePrimaryLight, JadeSecondaryLight, JadeTertiaryLight)
            "indigo_moon" -> Triple(IndigoPrimaryLight, IndigoSecondaryLight, IndigoTertiaryLight)
            "royal_lotus" -> Triple(LotusPrimaryLight, LotusSecondaryLight, LotusTertiaryLight)
            else -> Triple(CrimsonPrimaryLight, CrimsonSecondaryLight, CrimsonTertiaryLight)
        }
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.15f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
            background = RicePaperBackground,
            onBackground = Color(0xFF1E1B18),
            surface = RicePaperSurface,
            onSurface = Color(0xFF1E1B18),
            surfaceVariant = RicePaperSurfaceVariant,
            onSurfaceVariant = Color(0xFF524B42)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
