package online.aimuzov.aleshavpnov.data

import android.content.Context
import android.os.Build

/** Как приложение представляется боту: по этим заголовкам он узнаёт клиент и устройство. */
class DeviceIdentity(context: Context, private val store: SubscriptionStore) {
	private val versionName: String =
		runCatching {
			context.packageManager.getPackageInfo(context.packageName, 0).versionName
		}.getOrNull() ?: "0.0.0"

	val userAgent: String = "AleshaVpnov/$versionName (Android ${Build.VERSION.SDK_INT}; ${Build.MODEL})"

	suspend fun headers(): Map<String, String> =
		mapOf(
			"User-Agent" to userAgent,
			"Accept" to "application/json",
			"x-hwid" to store.hwid(),
			"x-device-os" to "Android ${Build.VERSION.RELEASE}",
			"x-ver-os" to Build.VERSION.RELEASE,
			"x-device-model" to "${Build.MANUFACTURER} ${Build.MODEL}",
		)
}
