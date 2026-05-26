package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = SaffronLight,
    secondary = GoldAccent,
    tertiary = VermilionRed,
    background = SacredCharcoal,
    surface = CharcoalSurface,
    onPrimary = SacredCharcoal,
    onSecondary = SacredCharcoal,
    onBackground = SoftWhite,
    onSurface = SoftWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BoldAccent,
    secondary = BoldSecondary,
    tertiary = VermilionRed,
    background = BoldBg,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = BoldDarkText,
    onBackground = BoldText,
    onSurface = BoldText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to ensure our highly curated design is visible
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
