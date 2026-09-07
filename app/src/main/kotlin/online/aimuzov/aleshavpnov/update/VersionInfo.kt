package online.aimuzov.aleshavpnov.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `version.json` из репозитория релизов: что скачать и как проверить. */
@Serializable
data class VersionInfo(
	val versionCode: Int,
	val versionName: String,
	val url: String,
	val sha256: String,
) {
	fun isNewerThan(installedCode: Int): Boolean = versionCode > installedCode

	companion object {
		private val json = Json { ignoreUnknownKeys = true }

		fun parse(text: String): VersionInfo? {
			val v = runCatching { json.decodeFromString<VersionInfo>(text) }.getOrNull() ?: return null
			// APK только по https: по http его подменят на первом же хотспоте.
			if (!v.url.startsWith("https://") || v.sha256.isBlank() || v.versionCode <= 0) return null
			return v
		}
	}
}
