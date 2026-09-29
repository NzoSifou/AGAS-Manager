package fr.nzosifou.agas.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages et statistiques, partagés entre l'interface et le service. */
class AgasSettings private constructor(private val prefs: SharedPreferences) {

    data class Values(
        /** Interrupteur général. */
        val enabled: Boolean = true,
        /** Mode test : détecte et journalise ce qui serait cliqué, sans cliquer. */
        val dryRun: Boolean = false,
        /** Autorise les boutons sans libellé (petite icône cliquable dans un coin) après un délai. */
        val unlabeledButtons: Boolean = true,
        /** Revient au jeu si un clic de fermeture ouvre une autre appli (Play Store, AliExpress…). */
        val hijackGuard: Boolean = true,
        /** Mini-jeux : un seul appui pour faire apparaître le bouton « Next » s'il ne vient pas. */
        val wakePlayables: Boolean = true,
        /** Mini-jeux dont la seule sortie est « Google Play » / « Ouvrir la boutique » : l'utiliser. */
        val storeExitButtons: Boolean = true,
        /** Tente la touche Retour si aucune croix n'est trouvée après un long moment. */
        val backFallback: Boolean = false,
        /** Enregistre l'arbre d'accessibilité des pubs non résolues (pour améliorer les règles). */
        val saveDumps: Boolean = true,
    )

    data class Stats(val adsSkipped: Int = 0, val clicks: Int = 0, val hijacksBlocked: Int = 0)

    private val _values = MutableStateFlow(readValues())
    val values: StateFlow<Values> = _values.asStateFlow()

    private val _stats = MutableStateFlow(readStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun update(transform: (Values) -> Values) {
        val v = transform(_values.value)
        prefs.edit()
            .putBoolean(K_ENABLED, v.enabled)
            .putBoolean(K_DRY_RUN, v.dryRun)
            .putBoolean(K_UNLABELED, v.unlabeledButtons)
            .putBoolean(K_HIJACK, v.hijackGuard)
            .putBoolean(K_WAKE, v.wakePlayables)
            .putBoolean(K_STORE_EXIT, v.storeExitButtons)
            .putBoolean(K_BACK, v.backFallback)
            .putBoolean(K_DUMPS, v.saveDumps)
            .apply()
        _values.value = v
    }

    fun recordAdSkipped() = updateStats { it.copy(adsSkipped = it.adsSkipped + 1) }
    fun recordClick() = updateStats { it.copy(clicks = it.clicks + 1) }
    fun recordHijackBlocked() = updateStats { it.copy(hijacksBlocked = it.hijacksBlocked + 1) }
    fun resetStats() = updateStats { Stats() }

    @Synchronized
    private fun updateStats(transform: (Stats) -> Stats) {
        val s = transform(_stats.value)
        prefs.edit()
            .putInt(K_ADS, s.adsSkipped)
            .putInt(K_CLICKS, s.clicks)
            .putInt(K_HIJACKS, s.hijacksBlocked)
            .apply()
        _stats.value = s
    }

    private fun readValues() = Values(
        enabled = prefs.getBoolean(K_ENABLED, true),
        dryRun = prefs.getBoolean(K_DRY_RUN, false),
        unlabeledButtons = prefs.getBoolean(K_UNLABELED, true),
        hijackGuard = prefs.getBoolean(K_HIJACK, true),
        wakePlayables = prefs.getBoolean(K_WAKE, true),
        storeExitButtons = prefs.getBoolean(K_STORE_EXIT, true),
        backFallback = prefs.getBoolean(K_BACK, false),
        saveDumps = prefs.getBoolean(K_DUMPS, true),
    )

    private fun readStats() = Stats(
        adsSkipped = prefs.getInt(K_ADS, 0),
        clicks = prefs.getInt(K_CLICKS, 0),
        hijacksBlocked = prefs.getInt(K_HIJACKS, 0),
    )

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_DRY_RUN = "dry_run"
        private const val K_UNLABELED = "unlabeled_buttons"
        private const val K_HIJACK = "hijack_guard"
        private const val K_WAKE = "wake_playables"
        private const val K_STORE_EXIT = "store_exit_buttons"
        private const val K_BACK = "back_fallback"
        private const val K_DUMPS = "save_dumps"
        private const val K_ADS = "stat_ads"
        private const val K_CLICKS = "stat_clicks"
        private const val K_HIJACKS = "stat_hijacks"

        @Volatile
        private var instance: AgasSettings? = null

        fun get(context: Context): AgasSettings =
            instance ?: synchronized(this) {
                instance ?: AgasSettings(
                    context.applicationContext.getSharedPreferences("agas", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
