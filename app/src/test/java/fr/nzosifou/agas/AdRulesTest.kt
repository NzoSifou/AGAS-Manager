package fr.nzosifou.agas

import fr.nzosifou.agas.rules.AdRules
import fr.nzosifou.agas.rules.containsMatch
import fr.nzosifou.agas.rules.matchesFully
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdRulesTest {

    private val rules = AdRules.parse(JSONObject(File("src/main/assets/ad_rules.json").readText()))

    @Test
    fun `activités de pub reconnues`() {
        assertTrue(rules.isAdActivity("com.google.android.gms.ads.AdActivity"))
        assertTrue(rules.isAdActivity("com.unity3d.services.ads.adunit.AdUnitActivity"))
        assertTrue(rules.isAdActivity("com.applovin.adview.AppLovinFullscreenActivity"))
        assertTrue(rules.isAdActivity("com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity"))
        assertTrue(rules.isAdActivity("com.mbridge.msdk.reward.player.MBRewardVideoActivity"))
    }

    @Test
    fun `le jeu lui-même n'est pas une pub`() {
        assertFalse(rules.isAdActivity("com.unity3d.player.UnityPlayerActivity"))
        assertFalse(rules.isAdActivity("com.google.firebase.MessagingUnityPlayerActivity"))
        assertFalse(rules.isAdActivity("com.example.game.MainActivity"))
        assertFalse(rules.isAdActivity("com.facebook.FacebookActivity"))
        assertFalse(rules.isAdActivity("com.applovin.mediation.MaxDebuggerActivity"))
    }

    @Test
    fun `pubs de Mob Control`() {
        for (cls in listOf(
            "io.bidmachine.rendering.ad.fullscreen.FullScreenActivity",
            "com.fyber.inneractive.sdk.activities.InneractiveFullscreenAdActivity",
            "com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.vast.VastActivity",
            "com.unity3d.ads.adplayer.FullScreenWebViewDisplay",
            "com.nefta.sdk.RendererPortrait",
            "io.adn.sdk.internal.ui.fullscreen.FullscreenAdActivity",
            "com.ysocorp.ysonetwork.webview.YNWebViewActivity",
        )) {
            assertTrue(cls, rules.isAdActivity(cls))
        }
    }

    @Test
    fun `marqueurs de mini-jeux`() {
        // Identifiants réellement vus : AppLovin, Unity, Moloco.
        for (id in listOf("GameCanvas", "Cocos3dGameContainer", "playable", "GameDiv", "application-canvas")) {
            assertTrue(id, rules.playableMarkerIdPatterns.containsMatch(id))
        }
        for (id in listOf("ad_video", "endcard", "closeButton", "skip_img")) {
            assertFalse(id, rules.playableMarkerIdPatterns.containsMatch(id))
        }
    }

    @Test
    fun `boutons de sortie boutique`() {
        for (label in listOf("Google Play", "Ouvrir la boutique", "Open Store", "Visit the store")) {
            assertTrue(label, rules.storeExitTextPatterns.matchesFully(label))
        }
        for (label in listOf("Installer", "JOUEZ MAINTENANT", "Get it on Google Play", "PLAY")) {
            assertFalse(label, rules.storeExitTextPatterns.matchesFully(label))
        }
    }

    @Test
    fun `pièges connus`() {
        assertTrue(rules.isKnownEarlyTrap("com.fyber.inneractive.sdk.activities.InneractiveFullscreenAdActivity", "skip_img"))
        assertFalse(rules.isKnownEarlyTrap("com.fyber.inneractive.sdk.activities.InneractiveFullscreenAdActivity", "ia_clickable_close_button"))
    }

    @Test
    fun `pages d'atterrissage`() {
        for (cls in listOf(
            "com.bytedance.sdk.openadsdk.activity.TTLandingPageActivity",
            "com.fyber.inneractive.sdk.activities.InneractiveInternalBrowserActivity",
            "com.fyber.inneractive.sdk.activities.InternalStoreWebpageActivity",
            "com.unity3d.ironsourceads.internal.services.InlineStoreActivity",
            "com.ironsource.sdk.controller.OpenUrlActivity",
        )) {
            assertTrue(cls, rules.isLandingPage(cls))
            assertFalse(cls, rules.isAdActivity(cls))
        }
        assertFalse(rules.isLandingPage("com.example.game.LandingPageActivity"))
    }

    @Test
    fun `libellés de fermeture`() {
        for (label in listOf("Close", "close ad", "Fermer", "Skip Ad", "Skip Video", "Passer", "Ignorer la vidéo", "Skip »", "Next", "Suivant ›")) {
            assertTrue(label, rules.closeTextPatterns.matchesFully(label))
        }
        for (symbol in listOf("X", "x", "×", "✕")) {
            assertTrue(symbol, rules.closeSymbolPatterns.matchesFully(symbol))
        }
        for (label in listOf("Install", "Close to you: shop now", "Skip in 5", "Next level")) {
            assertFalse(label, rules.closeTextPatterns.matchesFully(label))
        }
    }

    @Test
    fun `identifiants de fermeture`() {
        for (id in listOf("close_button", "mbridge_iv_close", "tt_video_ad_close_layout", "btn_skip", "iv_x", "x_button")) {
            assertTrue(id, rules.closeIdPatterns.containsMatch(id))
        }
        for (id in listOf("cta_button", "install_btn", "ad_choices_view", "mute_button")) {
            assertTrue(id, rules.negativeIdPatterns.containsMatch(id))
        }
    }

    @Test
    fun `textes interdits`() {
        for (label in listOf("INSTALL NOW", "En savoir plus", "Télécharger", "Open", "Get it on Google Play", "AdChoices")) {
            assertTrue(label, rules.negativeTextPatterns.containsMatch(label))
        }
        assertFalse(rules.negativeTextPatterns.containsMatch("Close"))
        assertFalse(rules.negativeTextPatterns.containsMatch("Fermer"))
    }

    @Test
    fun `compte à rebours de récompense`() {
        for (text in listOf("Reward in 25 seconds", "Récompense dans 5 s", "Skip in 5", "Passer dans 3", "12 seconds remaining")) {
            assertTrue(text, rules.rewardCountdownPatterns.containsMatch(text))
        }
        for (text in listOf("Reward granted", "Récompense obtenue", "Reward in 0 seconds", "Close")) {
            assertFalse(text, rules.rewardCountdownPatterns.containsMatch(text))
        }
    }

    @Test
    fun `avertissement de perte de récompense`() {
        assertTrue(rules.rewardWarningPatterns.containsMatch("Close video? You will lose your reward"))
        assertTrue(rules.rewardWarningPatterns.containsMatch("Si vous fermez maintenant, vous allez perdre votre récompense"))
        assertTrue(rules.rewardResumePatterns.matchesFully("RESUME VIDEO"))
        assertTrue(rules.rewardResumePatterns.matchesFully("Reprendre"))
    }
}
