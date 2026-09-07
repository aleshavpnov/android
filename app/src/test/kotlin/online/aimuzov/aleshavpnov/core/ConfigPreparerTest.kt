package online.aimuzov.aleshavpnov.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigPreparerTest {
	private val profiles: JsonArray =
		Json.parseToJsonElement(
			javaClass.getResource("/profiles.json")!!.readText(),
		).jsonArray

	private fun prepared(): JsonObject = ConfigPreparer.prepare(profiles, tunFd = 42, mtu = 1500)

	private fun JsonObject.inbounds() = this["inbounds"]!!.jsonArray.map { it.jsonObject }
	private fun JsonObject.outbounds() = this["outbounds"]!!.jsonArray.map { it.jsonObject }
	private fun JsonObject.rules() = this["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }
	private fun JsonObject.tag() = this["tag"]!!.jsonPrimitive.content

	@Test
	fun `first profile is used and presentation fields are dropped`() {
		val c = prepared()
		assertNull(c["remarks"])
		assertNull(c["meta"])
		assertTrue(c.outbounds().any { it.tag() == "lte-0" })
	}

	@Test
	fun `socks inbound becomes tun with the same tag and sniffing`() {
		val socks = prepared().inbounds().single { it.tag() == "socks" }
		assertEquals("tun", socks["protocol"]!!.jsonPrimitive.content)
		assertEquals(1500, socks["settings"]!!.jsonObject["mtu"]!!.jsonPrimitive.content.toInt())
		assertEquals("true", socks["sniffing"]!!.jsonObject["enabled"]!!.jsonPrimitive.content)
		assertNull(socks["listen"])
	}

	@Test
	fun `http inbound is dropped and loopback entries survive`() {
		val tags = prepared().inbounds().map { it.tag() }
		assertEquals(listOf("socks", "entry-lte"), tags)
	}

	@Test
	fun `tun fd goes into the root env`() {
		val env = prepared()["env"]!!.jsonObject
		assertEquals("42", env["xray.tun.fd"]!!.jsonPrimitive.content)
	}

	@Test
	fun `log level is warning`() {
		assertEquals("warning", prepared()["log"]!!.jsonObject["loglevel"]!!.jsonPrimitive.content)
	}

	@Test
	fun `masked hysteria sni is replaced by the real host`() {
		val tls =
			prepared().outbounds().single { it.tag() == "hy-0" }["streamSettings"]!!
				.jsonObject["tlsSettings"]!!.jsonObject
		assertEquals("hy.example", tls["serverName"]!!.jsonPrimitive.content)
		assertNull(tls["serverNameToVerify"])
	}

	@Test
	fun `withoutProtocols removes outbounds and rules that point at them`() {
		val stripped = ConfigPreparer.withoutProtocols(prepared(), setOf("hysteria"))
		assertFalse(stripped.outbounds().any { it["protocol"]!!.jsonPrimitive.content == "hysteria" })
		// Балансеры и правила на теги других аутбаундов остаются.
		assertTrue(stripped.rules().any { it["balancerTag"]?.jsonPrimitive?.content == "lb_geo" })
		assertTrue(stripped.outbounds().any { it.tag() == "node-0" })
	}

	@Test
	fun `withoutProtocols drops rules whose outbound vanished`() {
		val stripped = ConfigPreparer.withoutProtocols(prepared(), setOf("freedom"))
		assertFalse(stripped.rules().any { it["outboundTag"]?.jsonPrimitive?.content == "direct" })
	}
}
