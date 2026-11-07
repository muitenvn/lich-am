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
        val primaryContainerColor = when (accentPalette) {
            "jade_bamboo" -> Color(0xFF172D22)
            "indigo_moon" -> Color(0xFF19223B)
            "royal_lotus" -> Color(0xFF311828)
            else -> Color(0xFF33181E)
        }
        val secondaryContainerColor = Color(0xFF2B2415)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF1A0E10),
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = Color(0xFFFFECEE),
            secondary = secondary,
            onSecondary = Color(0xFF1C1708),
            secondaryContainer = secondaryContainerColor,
            onSecondaryContainer = Color(0xFFFFE599),
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
        val primaryContainerColor = when (accentPalette) {
            "jade_bamboo" -> Color(0xFFE1F3E8)
            "indigo_moon" -> Color(0xFFE3E8FA)
            "royal_lotus" -> Color(0xFFFCE4EC)
            else -> Color(0xFFFDE8E8)
        }
        val secondaryContainerColor = Color(0xFFFFF4D6)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainerColor,
            onSecondaryContainer = Color(0xFF5D4200),
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
