package fr.nzosifou.agas.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun `numéros de version`() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
        assertEquals(SemVer(2, 0, 1), SemVer.parse("2.0.1-beta"))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse(null))
    }

    @Test
    fun `comparaison des versions`() {
        assertTrue(SemVer.parse("1.0.10")!! > SemVer.parse("1.0.9")!!)
        assertTrue(SemVer.parse("1.1.0")!! > SemVer.parse("1.0.99")!!)
        assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.9.9")!!)
        assertEquals(0, SemVer.parse("v1.0.0")!!.compareTo(SemVer.parse("1.0")!!))
    }

    @Test
    fun `release GitHub`() {
        val json = """
            {"tag_name":"v1.1.0","html_url":"https://github.com/NzoSifou/AGAS-Agent/releases/tag/v1.1.0",
             "body":"Nouvelles règles","assets":[
               {"name":"notes.txt","browser_download_url":"https://example.invalid/notes.txt"},
               {"name":"AGAS-Agent-v1.1.0.apk","browser_download_url":"https://example.invalid/agent.apk"}]}
        """.trimIndent()
        val release = AgentUpdater.parseRelease(json)!!
        assertEquals(SemVer(1, 1, 0), release.version)
        assertEquals("https://example.invalid/agent.apk", release.apkUrl)
        assertEquals("Nouvelles règles", release.notes)
    }

    @Test
    fun `release sans APK ni version`() {
        assertNull(AgentUpdater.parseRelease("""{"tag_name":"v1.0.0","assets":[]}""")!!.apkUrl)
        assertNull(AgentUpdater.parseRelease("""{"tag_name":"nightly"}"""))
    }
}
