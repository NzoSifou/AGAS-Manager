package fr.nzosifou.agas.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Règles de détection chargées depuis `assets/ad_rules.json`.
 *
 * Elles sont séparées du code pour pouvoir, plus tard, être remplacées par une version
 * téléchargée depuis le serveur sans republier l'application.
 */
class AdRules(
    val version: Int,
    private val adActivityPrefixes: List<String>,
    private val adActivityExcludedPrefixes: List<String>,
    private val landingPageActivityPatterns: List<Regex>,
    private val knownEarlyTraps: Set<String>,
    /** Préfixe de classe → nom affiché de la régie (ordre du fichier conservé). */
    private val networkNames: List<Pair<String, String>>,
    val playableMarkerIdPatterns: List<Regex>,
    val videoMarkerIdPatterns: List<Regex>,
    val storeExitTextPatterns: List<Regex>,
    val closeIdPatterns: List<Regex>,
    val closeTextPatterns: List<Regex>,
    val closeSymbolPatterns: List<Regex>,
    val negativeIdPatterns: List<Regex>,
    val negativeTextPatterns: List<Regex>,
    val rewardCountdownPatterns: List<Regex>,
    val rewardWarningPatterns: List<Regex>,
    val rewardResumePatterns: List<Regex>,
    val hijackIgnoredPackages: Set<String>,
) {

    fun isAdActivity(className: String): Boolean =
        adActivityPrefixes.any { className.startsWith(it) } &&
            adActivityExcludedPrefixes.none { className.startsWith(it) } &&
            !isLandingPage(className)

    /**
     * Nom affiché de la régie d'une Activity de pub (« Unity », « AppLovin »…). À défaut, le
     * deuxième segment du nom de paquet (« com.acme.ads.X » → « Acme »).
     */
    fun networkName(adActivity: String): String =
        networkNames.firstOrNull { adActivity.startsWith(it.first) }?.second
            ?: adActivity.split('.').getOrNull(1)?.replaceFirstChar { it.uppercase() }
            ?: adActivity

    /** Bouton connu pour ouvrir la boutique si on clique trop tôt (voir TrapMemory.GRACE_MS). */
    fun isKnownEarlyTrap(adActivity: String, trapKey: String) = "$adActivity|$trapKey" in knownEarlyTraps

    /** Page d'atterrissage d'une pub (fiche Play Store intégrée, navigateur interne…). */
    fun isLandingPage(className: String): Boolean =
        adActivityPrefixes.any { className.startsWith(it) } && landingPageActivityPatterns.containsMatch(className)

    companion object {
        private const val ASSET_NAME = "ad_rules.json"

        @Volatile
        private var cached: AdRules? = null

        fun get(context: Context): AdRules =
            cached ?: synchronized(this) {
                cached ?: load(context).also { cached = it }
            }

        private fun load(context: Context): AdRules {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            return parse(JSONObject(json))
        }

        fun parse(o: JSONObject): AdRules = AdRules(
            version = o.optInt("version", 0),
            adActivityPrefixes = o.strings("adActivityPrefixes"),
            adActivityExcludedPrefixes = o.strings("adActivityExcludedPrefixes"),
            landingPageActivityPatterns = o.regexes("landingPageActivityPatterns"),
            knownEarlyTraps = o.strings("knownEarlyTraps").toSet(),
            networkNames = o.optJSONObject("networkNames")?.let { names ->
                names.keys().asSequence().map { it to names.getString(it) }.toList()
            }.orEmpty(),
            playableMarkerIdPatterns = o.regexes("playableMarkerIdPatterns"),
            videoMarkerIdPatterns = o.regexes("videoMarkerIdPatterns"),
            storeExitTextPatterns = o.regexes("storeExitTextPatterns"),
            closeIdPatterns = o.regexes("closeIdPatterns"),
            closeTextPatterns = o.regexes("closeTextPatterns"),
            closeSymbolPatterns = o.regexes("closeSymbolPatterns"),
            negativeIdPatterns = o.regexes("negativeIdPatterns"),
            negativeTextPatterns = o.regexes("negativeTextPatterns"),
            rewardCountdownPatterns = o.regexes("rewardCountdownPatterns"),
            rewardWarningPatterns = o.regexes("rewardWarningPatterns"),
            rewardResumePatterns = o.regexes("rewardResumePatterns"),
            hijackIgnoredPackages = o.strings("hijackIgnoredPackages").toSet(),
        )

        private fun JSONObject.strings(key: String): List<String> {
            val array: JSONArray = optJSONArray(key) ?: return emptyList()
            return List(array.length()) { array.getString(it) }
        }

        private fun JSONObject.regexes(key: String): List<Regex> =
            strings(key).map { Regex(it, RegexOption.IGNORE_CASE) }
    }
}

/** Le texte entier correspond à l'un des motifs. */
fun List<Regex>.matchesFully(text: String): Boolean = any { it.matches(text) }

/** Le texte contient l'un des motifs. */
fun List<Regex>.containsMatch(text: String): Boolean = any { it.containsMatchIn(text) }
