package online.aimuzov.aleshavpnov.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF22C55E)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFEF4444)

private val Light = lightColorScheme(primary = Green, error = Red)
private val Dark = darkColorScheme(primary = Green, error = Red)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) Dark else Light,
		content = content,
	)
}
