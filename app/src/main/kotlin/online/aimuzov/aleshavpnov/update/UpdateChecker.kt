package online.aimuzov.aleshavpnov.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.aimuzov.aleshavpnov.data.SubscriptionStore

/** Раз в сутки спрашивает у репозитория релизов, не вышла ли версия новее установленной. */
class UpdateChecker(private val store: SubscriptionStore, private val installedCode: Int) {
	suspend fun check(force: Boolean = false): VersionInfo? {
		val last = store.lastUpdateCheck()
		if (!force && System.currentTimeMillis() - last < DAY_MS) return null
		val info =
			withContext(Dispatchers.IO) {
				runCatching {
					val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
					try {
						conn.connectTimeout = 10_000
						conn.readTimeout = 10_000
						conn.setRequestProperty("Accept", "application/json")
						if (conn.responseCode != 200) null
						else VersionInfo.parse(conn.inputStream.bufferedReader().use { it.readText() })
					} finally {
						conn.disconnect()
					}
				}.getOrNull()
			}
		store.markUpdateCheck()
		return info?.takeIf { it.isNewerThan(installedCode) }
	}

	companion object {
		const val MANIFEST_URL =
			"https://github.com/aimuzov/aleshavpnov-releases/releases/latest/download/version.json"
		private const val DAY_MS = 24L * 60 * 60 * 1000
	}
}
