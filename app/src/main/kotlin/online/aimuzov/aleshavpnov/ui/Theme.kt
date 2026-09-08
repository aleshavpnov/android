package online.aimuzov.aleshavpnov.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Статусные цвета одинаковы в обеих темах.
val Emerald = Color(0xFF10B981)
val EmeraldPressed = Color(0xFF34D399)
val Amber = Color(0xFFF59E0B)
val Red = Color(0xFFEF4444)

/** Палитра shadcn Stone с лендинга — то, чего нет в ColorScheme Material. */
@Immutable
data class AppPalette(
	val background: Color,
	val surface: Color,
	val onBackground: Color,
	val muted: Color,
	val outline: Color,
	val divider: Color,
	val idle: Color,
)

private val DarkPalette =
	AppPalette(
		background = Color(0xFF0C0A09),
		surface = Color(0xFF1C1917),
		onBackground = Color(0xFFFAFAF9),
		muted = Color(0xFFA8A29E),
		outline = Color.White.copy(alpha = 0.10f),
		divider = Color.White.copy(alpha = 0.06f),
		idle = Color(0xFF44403C),
	)

private val LightPalette =
	AppPalette(
		background = Color.White,
		surface = Color(0xFFF5F5F4),
		onBackground = Color(0xFF1C1917),
		muted = Color(0xFF79716B),
		outline = Color(0xFFE7E5E4),
		divider = Color(0xFFF0EFEE),
		idle = Color(0xFFA8A29E),
	)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val palette = if (dark) DarkPalette else LightPalette
	val scheme =
		if (dark) {
			darkColorScheme(
				primary = Emerald,
				background = palette.background,
				surface = palette.background,
				onBackground = palette.onBackground,
				onSurface = palette.onBackground,
				error = Red,
			)
		} else {
			lightColorScheme(
				primary = Emerald,
				background = palette.background,
				surface = palette.background,
				onBackground = palette.onBackground,
				onSurface = palette.onBackground,
				error = Red,
			)
		}
	androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
		MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
	}
}
