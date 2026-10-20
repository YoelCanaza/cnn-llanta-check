package pe.edu.unap.llantacheck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5A524),
    onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF4A3608),
    onPrimaryContainer = Color(0xFFFFDDA8),
    secondary = Color(0xFFA9B3BF),
    onSecondary = Color(0xFF1B1F24),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE7E9EC),
    surface = Color(0xFF14171A),
    onSurface = Color(0xFFE7E9EC),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB9C1CB),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1E2227),
    surfaceContainerHigh = Color(0xFF262B31),
    outline = Color(0xFF59616B),
    outlineVariant = Color(0xFF353B42),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2B3),
    onPrimaryContainer = Color(0xFF2C1B00),
    secondary = Color(0xFF55606C),
    onSecondary = Color.White,
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E4E7),
    onSurfaceVariant = Color(0xFF454B52),
    surfaceContainerLow = Color(0xFFF0F0EE),
    surfaceContainer = Color(0xFFEAEAE8),
    surfaceContainerHigh = Color(0xFFE3E3E1),
    outline = Color(0xFF7A828B),
    outlineVariant = Color(0xFFCBD0D5),
)

object VerdictColors {
    val defectuosa = Color(0xFFE5484D)
    val buena = Color(0xFF30A46C)
    val dudosa = Color(0xFF8B95A1)
}

@Composable
fun LlantaCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
