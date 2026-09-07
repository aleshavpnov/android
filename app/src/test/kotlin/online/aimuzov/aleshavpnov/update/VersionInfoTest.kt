package online.aimuzov.aleshavpnov.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionInfoTest {
	private val json =
		"""{"versionCode":10203,"versionName":"1.2.3","url":"https://example.com/app-release.apk","sha256":"ab12"}"""

	@Test
	fun `parses the release manifest`() {
		val v = VersionInfo.parse(json)!!
		assertEquals(10203, v.versionCode)
		assertEquals("1.2.3", v.versionName)
		assertEquals("https://example.com/app-release.apk", v.url)
		assertEquals("ab12", v.sha256)
	}

	@Test
	fun `newer only when the code is strictly greater`() {
		val v = VersionInfo.parse(json)!!
		assertTrue(v.isNewerThan(10202))
		assertFalse(v.isNewerThan(10203))
	}

	@Test
	fun `broken manifest is null`() {
		assertNull(VersionInfo.parse("{}"))
		assertNull(VersionInfo.parse("nope"))
		assertNull(VersionInfo.parse("""{"versionCode":1,"versionName":"1","url":"http://insecure","sha256":"x"}"""))
	}
}
