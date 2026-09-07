package online.aimuzov.aleshavpnov.sub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResponseTest {
	@Test
	fun `empty body with announce is a dead link`() {
		val r = SubscriptionResponse.classify("", mapOf("announce" to "Обновите ссылку"))
		assertTrue(r is SubscriptionResponse.DeadLink)
		assertEquals("Обновите ссылку", (r as SubscriptionResponse.DeadLink).announce)
	}

	@Test
	fun `single profile without real proxies is a stub`() {
		val body =
			"""
			[{"remarks":"Лимит устройств исчерпан","inbounds":[],"outbounds":[{"tag":"block","protocol":"blackhole"}],"routing":{"rules":[]}}]
			""".trimIndent()
		val r = SubscriptionResponse.classify(body, emptyMap())
		assertTrue(r is SubscriptionResponse.Stub)
		assertEquals("Лимит устройств исчерпан", (r as SubscriptionResponse.Stub).text)
	}

	@Test
	fun `profiles with a proxy outbound are usable`() {
		val body =
			"""
			[{"remarks":"🇪🇺 Всё и сразу 🧠","inbounds":[],"outbounds":[{"tag":"node-0","protocol":"vless","settings":{}},{"tag":"direct","protocol":"freedom"}],"routing":{"rules":[]}}]
			""".trimIndent()
		val r = SubscriptionResponse.classify(body, emptyMap())
		assertTrue(r is SubscriptionResponse.Profiles)
		assertEquals(1, (r as SubscriptionResponse.Profiles).profiles.size)
	}

	@Test
	fun `non json body is a dead link without text`() {
		val r = SubscriptionResponse.classify("<html>", emptyMap())
		assertTrue(r is SubscriptionResponse.DeadLink)
	}
}
