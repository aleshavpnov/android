package online.aimuzov.aleshavpnov.sub

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionHeadersTest {
	private fun b64(s: String) = "base64:" + Base64.getEncoder().encodeToString(s.toByteArray())

	@Test
	fun `base64 prefixed headers are decoded`() {
		val info =
			SubscriptionHeaders.parse(
				mapOf(
					"profile-title" to b64("🟢 Alesha Vpnov"),
					"announce" to b64("Обновите ссылку"),
				),
			)
		assertEquals("🟢 Alesha Vpnov", info.title)
		assertEquals("Обновите ссылку", info.announce)
	}

	@Test
	fun `plain headers pass through`() {
		val info = SubscriptionHeaders.parse(mapOf("profile-title" to "Plain"))
		assertEquals("Plain", info.title)
		assertNull(info.announce)
	}

	@Test
	fun `userinfo is parsed field by field`() {
		val info =
			SubscriptionHeaders.parse(
				mapOf("subscription-userinfo" to "upload=10; download=2048; total=1073741824; expire=1790000000"),
			)
		val u = info.userinfo!!
		assertEquals(10L, u.upload)
		assertEquals(2048L, u.download)
		assertEquals(1073741824L, u.total)
		assertEquals(1790000000L, u.expire)
	}

	@Test
	fun `partial userinfo keeps missing fields null`() {
		val u = SubscriptionHeaders.parse(mapOf("subscription-userinfo" to "expire=0; junk")).userinfo!!
		assertNull(u.upload)
		assertEquals(0L, u.expire)
	}

	@Test
	fun `header names are matched case insensitively`() {
		val info = SubscriptionHeaders.parse(mapOf("Profile-Title" to "X"))
		assertEquals("X", info.title)
	}
}
