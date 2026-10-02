package fr.nzosifou.agas.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Réglages et statistiques, partagés entre l'interface et le service.
 *
 * Les réglages du Manager sont dans [Values] ; ceux de l'Agent (déclarés par lui, voir
 * AgentSetting) sont mémorisés sous leur clé dans le même fichier, d'une version de l'Agent à l'autre.
 */
class AgasSettings private constructor(private val prefs: SharedPreferences) {

    data class Values(
        /** Interrupteur général. */
        val enabled: Boolean = true,
        /** Mode test : l'Agent détecte et journalise ce qui serait cliqué, sans cliquer. */
        val dryRun: Boolean = false,
        /** Installe automatiquement les nouvelles versions de l'Agent publiées sur GitHub. */
        val autoUpdateAgent: Boolean = true,
    )

    data class Stats(val adsSkipped: Int = 0, val clicks: Int = 0, val hijacksBlocked: Int = 0)

    private val _values = MutableStateFlow(readValues())
    val values: StateFlow<Values> = _values.asStateFlow()

    private val _agentValues = MutableStateFlow(readAgentValues())

    /** Réglages de l'Agent modifiés par l'utilisateur (les autres gardent leur valeur par défaut). */
    val agentValues: StateFlow<Map<String, Boolean>> = _agentValues.asStateFlow()

    private val _stats = MutableStateFlow(readStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun update(transform: (Values) -> Values) {
        val v = transform(_values.value)
        prefs.edit()
            .putBoolean(K_ENABLED, v.enabled)
            .putBoolean(K_DRY_RUN, v.dryRun)
            .putBoolean(K_AUTO_UPDATE, v.autoUpdateAgent)
            .apply()
        _values.value = v
    }

    fun agentSetting(key: String, default: Boolean): Boolean = _agentValues.value[key] ?: default

    fun setAgentSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        _agentValues.value = _agentValues.value + (key to value)
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
        autoUpdateAgent = prefs.getBoolean(K_AUTO_UPDATE, true),
    )

    /** Tous les booléens qui ne sont pas des réglages du Manager appartiennent à l'Agent. */
    private fun readAgentValues(): Map<String, Boolean> = prefs.all
        .filterKeys { it !in MANAGER_KEYS }
        .mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }
        .toMap()

    private fun readStats() = Stats(
        adsSkipped = prefs.getInt(K_ADS, 0),
        clicks = prefs.getInt(K_CLICKS, 0),
        hijacksBlocked = prefs.getInt(K_HIJACKS, 0),
    )

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_DRY_RUN = "dry_run"
        private const val K_AUTO_UPDATE = "auto_update_agent"
        private const val K_ADS = "stat_ads"
        private const val K_CLICKS = "stat_clicks"
        private const val K_HIJACKS = "stat_hijacks"
        private val MANAGER_KEYS = setOf(K_ENABLED, K_DRY_RUN, K_AUTO_UPDATE)

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
