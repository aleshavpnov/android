package online.aimuzov.aleshavpnov.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import online.aimuzov.aleshavpnov.R

/**
 * Manrope, как на лендинге. Файл вариативный, и ось wght надо задавать явно: без неё
 * Android берёт дефолтный экземпляр шрифта — ExtraLight, и всё выглядит тонким.
 */
val Manrope =
	FontFamily(
		listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
			Font(
				R.font.manrope,
				weight = weight,
				variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
			)
		},
	)

private val default = Typography()

/** Все стили Material с Manrope: Text без явного fontFamily получает его автоматически. */
val AppTypography =
	Typography(
		displayLarge = default.displayLarge.withManrope(),
		displayMedium = default.displayMedium.withManrope(),
		displaySmall = default.displaySmall.withManrope(),
		headlineLarge = default.headlineLarge.withManrope(),
		headlineMedium = default.headlineMedium.withManrope(),
		headlineSmall = default.headlineSmall.withManrope(),
		titleLarge = default.titleLarge.withManrope(),
		titleMedium = default.titleMedium.withManrope(),
		titleSmall = default.titleSmall.withManrope(),
		bodyLarge = default.bodyLarge.withManrope(),
		bodyMedium = default.bodyMedium.withManrope(),
		bodySmall = default.bodySmall.withManrope(),
		labelLarge = default.labelLarge.withManrope(),
		labelMedium = default.labelMedium.withManrope(),
		labelSmall = default.labelSmall.withManrope(),
	)

private fun TextStyle.withManrope() = copy(fontFamily = Manrope)
