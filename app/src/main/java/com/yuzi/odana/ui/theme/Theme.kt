package com.yuzi.odana.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = OceanBlue,
    onPrimary = NeutralWhite,
    primaryContainer = OceanBlueDark,
    onPrimaryContainer = NeutralWhite,
    
    secondary = SlateGray,
    onSecondary = NeutralWhite,
    
    tertiary = CoralPink,
    onTertiary = NeutralWhite,
    
    background = NeutralGrey900,
    surface = NeutralGrey800,
    onSurface = NeutralGrey100,
    
    surfaceVariant = SlateGrayDark,
    onSurfaceVariant = NeutralGrey100,
    
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = NeutralWhite,
    primaryContainer = OceanBlueLight,
    onPrimaryContainer = SlateGrayDark,
    
    secondary = SlateGray,
    onSecondary = NeutralWhite,
    
    tertiary = CoralPink,
    onTertiary = NeutralWhite,
    
    background = NeutralGrey50,
    surface = NeutralWhite,
    onSurface = SlateGrayDark,
    
    surfaceVariant = NeutralGrey100,
    onSurfaceVariant = SlateGrayDark,
    
    error = ErrorRed
)

@Composable
fun ODANATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color for consistent branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
