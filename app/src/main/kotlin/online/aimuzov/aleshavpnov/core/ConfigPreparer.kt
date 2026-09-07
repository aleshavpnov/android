package online.aimuzov.aleshavpnov.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Из профиля подписки делает конфиг, который ядро поднимет поверх TUN от VpnService.
 * Всё, что про маршруты, балансеры и observatory, остаётся как пришло от бота.
 */
object ConfigPreparer {
	private const val PRIMARY_INBOUND = "socks"

	fun prepare(profiles: JsonArray, tunFd: Int, mtu: Int): JsonObject {
		val profile = profiles.first().jsonObject
		return buildJsonObject {
			profile.forEach { (key, value) ->
				when (key) {
					"remarks", "meta", "log", "env" -> Unit
					"inbounds" -> put(key, JsonArray(convertInbounds(value.jsonArray, mtu)))
					"outbounds" -> put(key, JsonArray(value.jsonArray.map { fixTls(it.jsonObject) }))
					else -> put(key, value)
				}
			}
			put("log", buildJsonObject { put("loglevel", "warning") })
			put("env", buildJsonObject { put("xray.tun.fd", tunFd.toString()) })
		}
	}

	/** Убирает аутбаунды с этими протоколами и правила, которые вели прямо на них. */
	fun withoutProtocols(config: JsonObject, protocols: Set<String>): JsonObject {
		val outbounds = config["outbounds"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
		val gone = outbounds.filter { it.str("protocol") in protocols }.mapNotNull { it.str("tag") }.toSet()
		val kept = outbounds.filter { it.str("protocol") !in protocols }
		val routing =
			config["routing"]?.jsonObject?.let { r ->
				val rules = r["rules"]?.jsonArray?.filter { it.jsonObject.str("outboundTag") !in gone }
				buildJsonObject {
					r.forEach { (k, v) -> if (k != "rules") put(k, v) }
					if (rules != null) put("rules", JsonArray(rules))
				}
			}
		return buildJsonObject {
			config.forEach { (k, v) ->
				when (k) {
					"outbounds" -> put(k, JsonArray(kept))
					"routing" -> if (routing != null) put(k, routing)
					else -> put(k, v)
				}
			}
		}
	}

	// Тег «socks» сохраняем нарочно: правила каскада ограничены inboundTag socks/http,
	// и это их защита от петли через loopback-инбаунды entry-*.
	private fun convertInbounds(inbounds: JsonArray, mtu: Int): List<JsonElement> =
		inbounds.mapNotNull { element ->
			val inbound = element.jsonObject
			when (inbound.str("tag")) {
				PRIMARY_INBOUND ->
					buildJsonObject {
						put("tag", PRIMARY_INBOUND)
						put("protocol", "tun")
						put("port", 0)
						put("settings", buildJsonObject {
							put("name", "xray0")
							put("mtu", mtu)
						})
						inbound["sniffing"]?.let { put("sniffing", it) }
					}
				"http" -> null
				else -> inbound
			}
		}

	// Форк Happ проверяет серт по serverNameToVerify при чужом SNI; ванильное ядро такого
	// поля не знает, поэтому SNI становится реальным именем — иначе TLS не пройдёт.
	private fun fixTls(outbound: JsonObject): JsonObject {
		val stream = outbound["streamSettings"]?.jsonObject ?: return outbound
		val tls = stream["tlsSettings"]?.jsonObject ?: return outbound
		val verify = tls.str("serverNameToVerify") ?: return outbound
		val fixedTls = buildJsonObject {
			tls.forEach { (k, v) ->
				when (k) {
					"serverNameToVerify" -> Unit
					"serverName" -> put(k, JsonPrimitive(verify))
					else -> put(k, v)
				}
			}
		}
		val fixedStream = buildJsonObject { stream.forEach { (k, v) -> put(k, if (k == "tlsSettings") fixedTls else v) } }
		return buildJsonObject { outbound.forEach { (k, v) -> put(k, if (k == "streamSettings") fixedStream else v) } }
	}

	private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
}
