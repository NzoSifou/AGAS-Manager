package fr.nzosifou.agas

import android.graphics.Point
import fr.nzosifou.agas.guard.HijackGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HijackGuardTest {

    private var time = 0L
    private lateinit var guard: HijackGuard

    @Before
    fun setUp() {
        time = 1_000L
        guard = HijackGuard(OWN, clock = { time }).apply {
            launcherPackages = setOf(LAUNCHER)
            ignoredPackages = setOf("com.android.systemui", KEYBOARD)
        }
    }

    private fun armNow() = guard.arm(GAME, "com.google.android.gms.ads.AdActivity", Point(1000, 100))

    @Test
    fun `une autre appli juste après notre clic est un détournement`() {
        armNow()
        time += 600
        assertNull(guard.check(ALIEXPRESS))
        val hijack = guard.check(ALIEXPRESS)
        assertNotNull(hijack)
        assertEquals(GAME, hijack!!.gamePackage)
        // Une seule réaction par clic.
        assertNull(guard.check(ALIEXPRESS))
    }

    @Test
    fun `une redirection lente (page relais puis appli) est détectée`() {
        armNow()
        time += 1000
        assertNull(guard.check(GAME))
        time += 2500
        guard.check(ALIEXPRESS)
        assertNotNull(guard.check(ALIEXPRESS))
    }

    @Test
    fun `une appli qui n'apparaît qu'un instant n'est pas un détournement`() {
        armNow()
        time += 200
        assertNull(guard.check(PLAY_STORE))
        time += 300
        assertNull(guard.check(GAME))
        time += 300
        assertNull(guard.check(GAME))
    }

    @Test
    fun `passer par l'écran d'accueil n'est pas un détournement`() {
        armNow()
        time += 300
        assertNull(guard.check(LAUNCHER))
        time += 300
        assertNull(guard.check(ALIEXPRESS))
    }

    @Test
    fun `sans clic d'AGAS rien ne se passe`() {
        assertNull(guard.check(PLAY_STORE))
    }

    @Test
    fun `la protection expire`() {
        armNow()
        time += HijackGuard.ARM_DURATION_MS + 1
        assertNull(guard.check(PLAY_STORE))
        assertNull(guard.active())
    }

    @Test
    fun `le jeu, AGAS, la barre d'état, le clavier et l'inconnu sont ignorés`() {
        armNow()
        time += 200
        assertNull(guard.check(GAME))
        assertNull(guard.check(OWN))
        assertNull(guard.check("com.android.systemui"))
        assertNull(guard.check(KEYBOARD))
        assertNull(guard.check(null))
        // Toujours armée : le Play Store qui arrive ensuite est bien détecté.
        guard.check(PLAY_STORE)
        assertNotNull(guard.check(PLAY_STORE))
    }

    @Test
    fun `une page de la pub ouverte dans le jeu est un détournement`() {
        armNow()
        time += 500
        guard.check(GAME, isLandingPage = true)
        assertNotNull(guard.check(GAME, isLandingPage = true))
    }

    @Test
    fun `l'écho de notre propre clic ne désarme pas`() {
        armNow()
        time += 100
        guard.onViewClicked(GAME)
        guard.check(PLAY_STORE)
        assertNotNull(guard.check(PLAY_STORE))
    }

    @Test
    fun `un clic de l'utilisateur désarme`() {
        armNow()
        time += HijackGuard.OWN_CLICK_ECHO_MS + 200
        guard.onViewClicked(GAME)
        assertNull(guard.check(PLAY_STORE))
    }

    @Test
    fun `un clic de l'utilisateur après expiration ne signale rien`() {
        armNow()
        time += HijackGuard.ARM_DURATION_MS + 1
        assertFalse(guard.onViewClicked(GAME))
    }

    @Test
    fun `un clic dans une autre appli ne désarme pas`() {
        armNow()
        time += HijackGuard.OWN_CLICK_ECHO_MS + 200
        assertFalse(guard.onViewClicked("com.android.systemui"))
        guard.check(PLAY_STORE)
        assertNotNull(guard.check(PLAY_STORE))
    }

    private companion object {
        const val OWN = "fr.nzosifou.agas"
        const val GAME = "com.example.game"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val KEYBOARD = "com.google.android.inputmethod.latin"
        const val ALIEXPRESS = "com.alibaba.aliexpresshd"
        const val PLAY_STORE = "com.android.vending"
    }
}
