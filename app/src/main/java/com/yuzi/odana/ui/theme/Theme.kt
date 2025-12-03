package com.yuzi.odana.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════════════════
// WISTERIA DARK THEME - Primary Experience
// Deep, rich, cyberpunk vibes with purple accents
// ═══════════════════════════════════════════════════════════════════════════════
private val WisteriaDarkScheme = darkColorScheme(
    // Primary - The main wisteria purple
    primary = Wisteria400,
    onPrimary = Color.White,
    primaryContainer = Wisteria800,
    onPrimaryContainer = Wisteria100,
    
    // Secondary - Complementary accent
    secondary = CyberMint,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0D3D35),
    onSecondaryContainer = CyberMint,
    
    // Tertiary - Hot accent for highlights
    tertiary = CyberPink,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4A1942),
    onTertiaryContainer = CyberPink,
    
    // Background - Deep abyss
    background = VioletAbyss,
    onBackground = TextPrimaryDark,
    
    // Surface - Elevated cards
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = Wisteria400,
    
    // Inverse
    inverseSurface = Wisteria100,
    inverseOnSurface = Wisteria900,
    inversePrimary = Wisteria700,
    
    // Outline
    outline = Wisteria700,
    outlineVariant = SurfaceDarkVariant,
    
    // Semantic
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF3D0D0D),
    onErrorContainer = Color(0xFFFFB4AB),
    
    // Scrim
    scrim = Color.Black.copy(alpha = 0.7f)
)

// ═══════════════════════════════════════════════════════════════════════════════
// WISTERIA LIGHT THEME - Soft lavender aesthetic
// ═══════════════════════════════════════════════════════════════════════════════
private val WisteriaLightScheme = lightColorScheme(
    // Primary
    primary = Wisteria600,
    onPrimary = Color.White,
    primaryContainer = Wisteria100,
    onPrimaryContainer = Wisteria900,
    
    // Secondary
    secondary = Color(0xFF0D9488),  // Teal-600
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    
    // Tertiary
    tertiary = Color(0xFFDB2777),  // Pink-600
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF831843),
    
    // Background
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    
    // Surface
    surface = Color.White,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLightVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = Wisteria600,
    
    // Inverse
    inverseSurface = Wisteria900,
    inverseOnSurface = Wisteria100,
    inversePrimary = Wisteria300,
    
    // Outline
    outline = Wisteria300,
    outlineVariant = Wisteria100,
    
    // Semantic
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    // Scrim
    scrim = Color.Black.copy(alpha = 0.4f)
)

@Composable
fun ODANATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WisteriaDarkScheme else WisteriaLightScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Make status bar transparent for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
