package com.kec.tnstc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kec.tnstc.data.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),          // Vibrant Sky Blue 400
    onPrimary = Color(0xFF0C2444),        // Deep Navy text on primary
    primaryContainer = Color(0xFF1E3A8A), // Rich Navy Blue 900
    onPrimaryContainer = Color(0xFFBAE6FD), // Soft Blue text
    secondary = Color(0xFF2DD4BF),        // Fresh Mint Teal 400
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFFFBBF24),         // Warm Amber 400
    onTertiary = Color(0xFF451A03),
    background = Color(0xFF0F172A),       // Deep Slate 900
    onBackground = Color(0xFFF8FAFC),     // Crisp off-white text
    surface = Color(0xFF1E293B),          // Slate 800 (Card / Bar surface)
    onSurface = Color(0xFFF1F5F9),        // High-contrast text on card
    surfaceVariant = Color(0xFF334155),   // Slate 700 (Input / sub-containers)
    onSurfaceVariant = Color(0xFF94A3B8), // Slate 400 (Readable muted subtitles)
    outline = Color(0xFF475569),          // Slate 600 borders
    outlineVariant = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),          // TNSTC Royal Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD), // Light Ice Blue
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF00796B),        // Tamil Nadu Green
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFFE65100),         // Amber orange accent
    onTertiary = Color.White,
    background = Color(0xFFF6F8FA),       // Clean light grey-white
    onBackground = Color(0xFF1A1A1A),     // Crisp dark charcoal
    surface = Color.White,                // Pure white cards
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECEFF1),   // Light slate input boxes
    onSurfaceVariant = Color(0xFF546E7A), // Muted dark grey subtitles
    outline = Color(0xFFCFD8DC),
    outlineVariant = Color(0xFFECEFF1)
)


@Composable
fun TNSTCTheme(
    appThemeMode: AppThemeMode = AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}