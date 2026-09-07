package online.aimuzov.aleshavpnov.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import libXray.DialerController
import libXray.LibXray

/** Ядро за одним интерфейсом: сервису всё равно, gomobile под ним или заглушка в тестах. */
interface XrayCore {
	fun test(configPath: String): Result<Unit>

	fun run(configPath: String): Result<Unit>

	fun stop(): Result<Unit>

	val isRunning: Boolean

	fun version(): String

	fun setDns(server: String): Result<Unit>

	fun resetDns()
}

/**
 * libXray принимает JSON-конверт `{apiVersion, method, payload}` и отвечает
 * `{success, data, error}` — одна точка входа на все методы.
 */
object LibXrayCore : XrayCore {
	// Само приложение исключено из VPN через addDisallowedApplication, поэтому защищать
	// сокеты нечем и незачем — но libXray без контроллера DNS не поднимет.
	private val noProtect = DialerController { true }

	override fun test(configPath: String): Result<Unit> =
		invoke("testXray", buildJsonObject { put("configPath", configPath) }).map {}

	override fun run(configPath: String): Result<Unit> =
		invoke("runXray", buildJsonObject { put("configPath", configPath) }).map {}

	override fun stop(): Result<Unit> = invoke("stopXray", null).map {}

	override val isRunning: Boolean
		get() =
			invoke("getXrayState", null)
				.getOrNull()?.get("running")?.jsonPrimitive?.content == "true"

	override fun version(): String =
		invoke("xrayVersion", null).getOrNull()?.get("version")?.jsonPrimitive?.content ?: "?"

	override fun setDns(server: String): Result<Unit> =
		runCatching { LibXray.setDNS(noProtect, server) }

	override fun resetDns() {
		runCatching { LibXray.resetDNS() }
	}

	private fun invoke(method: String, payload: JsonObject?): Result<JsonObject> {
		val request = buildJsonObject {
			put("apiVersion", 1)
			put("method", method)
			if (payload != null) put("payload", payload)
		}
		val raw = LibXray.invoke(request.toString())
		val response = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrElse { return Result.failure(it) }
		val ok = response["success"]?.jsonPrimitive?.content == "true"
		if (!ok) return Result.failure(XrayException(response["error"]?.jsonPrimitive?.content ?: raw))
		val data = response["data"]
		return Result.success(if (data is JsonObject) data else JsonObject(emptyMap()))
	}
}

class XrayException(message: String) : Exception(message)
