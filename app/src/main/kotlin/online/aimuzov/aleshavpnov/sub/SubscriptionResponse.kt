package online.aimuzov.aleshavpnov.sub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Что бот прислал на `/s`: серверы, заглушку с текстом или пустой ответ на мёртвую ссылку. */
sealed interface SubscriptionResponse {
	data class DeadLink(val announce: String?) : SubscriptionResponse

	data class Stub(val text: String?) : SubscriptionResponse

	data class Profiles(val profiles: JsonArray) : SubscriptionResponse

	companion object {
		// Аутбаунды, которые действительно куда-то ведут. Остальное — служебные.
		private val PROXY_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "wireguard")

		fun classify(body: String, headers: Map<String, String>): SubscriptionResponse {
			val info = SubscriptionHeaders.parse(headers)
			val profiles =
				runCatching { Json.parseToJsonElement(body).jsonArray }.getOrNull()
					?: return DeadLink(info.announce)
			if (profiles.isEmpty()) return DeadLink(info.announce)
			val hasProxy = profiles.any { p -> p.jsonObject.outbounds().any { it.protocol() in PROXY_PROTOCOLS } }
			if (!hasProxy) {
				val text = profiles.first().jsonObject["remarks"]?.jsonPrimitive?.content
				return Stub(text ?: info.announce)
			}
			return Profiles(profiles)
		}

		private fun JsonObject.outbounds(): List<JsonObject> =
			this["outbounds"]?.jsonArray?.map { it.jsonObject } ?: emptyList()

		private fun JsonObject.protocol(): String? = this["protocol"]?.jsonPrimitive?.content
	}
}
