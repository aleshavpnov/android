package online.aimuzov.aleshavpnov.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Качает APK в кэш, сверяет sha256 и отдаёт системному установщику. */
class UpdateInstaller(private val context: Context) {
	suspend fun install(info: VersionInfo): Result<Unit> = runCatching {
		val file = withContext(Dispatchers.IO) { download(info) }
		val digest = withContext(Dispatchers.IO) { sha256(file) }
		if (!digest.equals(info.sha256, ignoreCase = true)) {
			file.delete()
			throw IllegalStateException("sha256 не совпал")
		}
		// Без права на «неизвестные источники» установщик молча не откроется — ведём в настройки.
		if (!context.packageManager.canRequestPackageInstalls()) {
			context.startActivity(
				Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
			)
			throw IllegalStateException("нужно разрешение на установку")
		}
		val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
		context.startActivity(
			Intent(Intent.ACTION_VIEW)
				.setDataAndType(uri, "application/vnd.android.package-archive")
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
		)
	}

	private fun download(info: VersionInfo): File {
		val dir = File(context.cacheDir, "updates").apply { mkdirs() }
		val file = File(dir, "app-${info.versionCode}.apk")
		val conn = URL(info.url).openConnection() as HttpURLConnection
		try {
			conn.connectTimeout = 15_000
			conn.readTimeout = 60_000
			conn.instanceFollowRedirects = true
			if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
			conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
		} finally {
			conn.disconnect()
		}
		return file
	}

	private fun sha256(file: File): String {
		val md = MessageDigest.getInstance("SHA-256")
		file.inputStream().use { input ->
			val buf = ByteArray(64 * 1024)
			while (true) {
				val n = input.read(buf)
				if (n < 0) break
				md.update(buf, 0, n)
			}
		}
		return md.digest().joinToString("") { "%02x".format(it) }
	}
}
