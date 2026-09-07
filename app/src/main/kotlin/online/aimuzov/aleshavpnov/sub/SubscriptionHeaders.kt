package online.aimuzov.aleshavpnov.sub

import java.util.Base64

/** Разбор HAPP-заголовков подписки. Кириллицу бот шлёт с префиксом `base64:`. */
object SubscriptionHeaders {
	fun parse(headers: Map<String, String>): SubscriptionInfo {
		val lower = headers.entries.associate { (k, v) -> k.lowercase() to v }
		return SubscriptionInfo(
			title = lower["profile-title"]?.let(::decode),
			announce = lower["announce"]?.let(::decode)?.takeIf { it.isNotBlank() && it != "0" },
			userinfo = lower["subscription-userinfo"]?.let(::parseUserInfo),
		)
	}

	fun decode(value: String): String {
		if (!value.startsWith("base64:")) return value
		return runCatching {
			String(Base64.getDecoder().decode(value.removePrefix("base64:").trim()), Charsets.UTF_8)
		}.getOrDefault(value)
	}

	private fun parseUserInfo(value: String): UserInfo {
		val fields =
			value.split(';').mapNotNull { part ->
				val (k, v) = part.trim().split('=', limit = 2).let { it[0] to it.getOrNull(1) }
				v?.trim()?.toLongOrNull()?.let { k.trim().lowercase() to it }
			}.toMap()
		return UserInfo(
			upload = fields["upload"],
			download = fields["download"],
			total = fields["total"],
			expire = fields["expire"],
		)
	}
}
