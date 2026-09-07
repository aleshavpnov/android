package online.aimuzov.aleshavpnov.deeplink

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddLinkTest {
	private val sub = "https://durov.aimuzov.online/s/abc123XYZ"
	private val b64url = Base64.getUrlEncoder().withoutPadding().encodeToString(sub.toByteArray())

	@Test
	fun `https app link carries the url in the fragment`() {
		assertEquals(sub, AddLink.parse("https://durov.aimuzov.online/app/add#u=$b64url"))
	}

	@Test
	fun `custom scheme carries the url in the query`() {
		assertEquals(sub, AddLink.parse("aleshavpnov://add?u=$b64url"))
	}

	@Test
	fun `padded base64 is accepted too`() {
		val padded = Base64.getUrlEncoder().encodeToString(sub.toByteArray())
		assertEquals(sub, AddLink.parse("aleshavpnov://add?u=$padded"))
	}

	@Test
	fun `non http payload is rejected`() {
		val bad = Base64.getUrlEncoder().withoutPadding().encodeToString("javascript:alert(1)".toByteArray())
		assertNull(AddLink.parse("aleshavpnov://add?u=$bad"))
	}

	@Test
	fun `garbage and foreign links are rejected`() {
		assertNull(AddLink.parse(null))
		assertNull(AddLink.parse(""))
		assertNull(AddLink.parse("aleshavpnov://add?u=%%%"))
		assertNull(AddLink.parse("https://durov.aimuzov.online/app"))
		assertNull(AddLink.parse("https://example.com/app/add#u=$b64url"))
	}
}
