package online.aimuzov.aleshavpnov.deeplink

import java.net.URI
import java.util.Base64

/**
 * Ссылка «добавить подписку». Две формы, одна суть: `u` — base64url от URL подписки.
 * У https она во фрагменте (не уходит на сервер), у своей схемы — в query.
 */
object AddLink {
	const val HOST = "durov.aimuzov.online"
	const val PATH = "/app/add"
	const val SCHEME = "aleshavpnov"

	fun parse(link: String?): String? {
		if (link.isNullOrBlank()) return null
		val uri = runCatching { URI(link) }.getOrNull() ?: return null
		val raw =
			when (uri.scheme?.lowercase()) {
				"https" -> if (uri.host == HOST && uri.path == PATH) param(uri.rawFragment) else null
				SCHEME -> if (uri.host == "add") param(uri.rawQuery) else null
				else -> null
			} ?: return null
		val decoded =
			runCatching { String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8) }.getOrNull()
				?: return null
		val target = runCatching { URI(decoded) }.getOrNull() ?: return null
		val ok = target.scheme?.lowercase() in setOf("http", "https") && !target.host.isNullOrBlank()
		return if (ok) decoded else null
	}

	private fun param(query: String?): String? =
		query?.split('&')?.firstNotNullOfOrNull { pair ->
			val (k, v) = pair.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
			if (k == "u") v else null
		}
}
