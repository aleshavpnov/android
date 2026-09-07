package online.aimuzov.aleshavpnov.net

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.aimuzov.aleshavpnov.data.DeviceIdentity

/** Один GET за подпиской. Заголовки ответа нужны не меньше тела: в них статус и объявления. */
class SubscriptionFetcher(private val identity: DeviceIdentity) {
	data class Response(val body: String, val headers: Map<String, String>)

	suspend fun fetch(url: String): Response =
		withContext(Dispatchers.IO) {
			val conn = URL(url).openConnection() as HttpURLConnection
			try {
				conn.connectTimeout = 10_000
				conn.readTimeout = 20_000
				conn.instanceFollowRedirects = true
				identity.headers().forEach { (k, v) -> conn.setRequestProperty(k, v) }
				val code = conn.responseCode
				if (code !in 200..299) throw HttpException(code)
				val body = conn.inputStream.bufferedReader().use { it.readText() }
				val headers =
					conn.headerFields.entries
						.filter { it.key != null }
						.associate { (k, v) -> k!!.lowercase() to v.joinToString(", ") }
				Response(body, headers)
			} finally {
				conn.disconnect()
			}
		}

	class HttpException(val code: Int) : Exception("HTTP $code")
}
