package fr.nzosifou.agas.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dalvik.system.DexClassLoader
import fr.nzosifou.agas.agent.api.AdPhase
import fr.nzosifou.agas.agent.api.Agent
import fr.nzosifou.agas.agent.api.AgentHost
import fr.nzosifou.agas.agent.api.AgentSetting
import fr.nzosifou.agas.agent.api.LogLevel
import fr.nzosifou.agas.data.AgasEvents
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Charge AGAS Agent et le fait tourner dans le service d'accessibilité du Manager.
 *
 * L'Agent tourne tant que le service est connecté et qu'AGAS est activé. Une nouvelle version
 * remplace l'ancienne à chaud, sans redémarrer le service. Un Agent qui fait planter AGAS deux fois
 * est écarté : on revient à la version précédente (en général celle intégrée au Manager).
 *
 * Tout se passe sur le thread principal.
 */
object AgentRuntime {

    class State(
        /** Agent chargé, null si aucun n'est utilisable. */
        val agent: AgentPackage? = null,
        /** Réglages déclarés par l'Agent chargé. */
        val settings: List<AgentSetting> = emptyList(),
        /** L'Agent est démarré (service connecté et AGAS activé). */
        val running: Boolean = false,
        /** Pourquoi aucun Agent n'est chargé. */
        val error: String? = null,
        /** Une version a été écartée après des plantages. */
        val hasRejected: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var store: AgentStore
    private lateinit var settings: AgasSettings

    private var agent: Agent? = null
    private var loaded: AgentPackage? = null
    private var service: AccessibilityService? = null
    private var started = false
    private var scope: CoroutineScope? = null
    private var lastErrorLogAt = 0L

    /** Charge le meilleur Agent disponible, sans le démarrer (appelé par l'activité et le service). */
    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = AgentStore(appContext)
        settings = AgasSettings.get(appContext)
        installCrashGuard()
        loadBest()
    }

    /** Le service d'accessibilité est connecté. */
    fun attach(service: AccessibilityService) {
        init(service)
        this.service = service
        scope?.cancel()
        scope = MainScope().also { scope ->
            scope.launch {
                settings.values.map { it.enabled }.distinctUntilChanged().collect { syncRunning() }
            }
        }
        syncRunning()
    }

    fun detach() {
        scope?.cancel()
        scope = null
        stopAgent()
        service = null
        publish()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (started) guarded("événement") { agent?.onAccessibilityEvent(event) }
    }

    fun dumpScreen() {
        if (started) guarded("enregistrement") { agent?.dumpScreen() }
        else AgasLog.w("Enregistrement impossible : l'Agent n'est pas démarré")
    }

    /**
     * Vérifie [apk] et, s'il est acceptable, en fait l'Agent en vigueur, à chaud. Appelable depuis
     * n'importe quel thread ; le remplacement se fait sur le thread principal. Lève [AgentRejected].
     */
    fun install(apk: File): AgentPackage {
        val pkg = store.install(apk)
        appContext.mainExecutor.execute { reload() }
        return pkg
    }

    /** Abandonne la version téléchargée et revient à celle intégrée au Manager. */
    fun useBundled() {
        store.removeDownloaded()
        reload()
    }

    /** Redonne une chance aux versions écartées après des plantages. */
    fun retryRejected() {
        store.clearBad()
        reload()
    }

    private fun reload() {
        stopAgent()
        loadBest()
        syncRunning()
    }

    private fun loadBest() {
        agent = null
        loaded = null
        var lastError: String? = null
        for (pkg in store.candidates()) {
            val instance = runCatching { instantiate(pkg) }.getOrElse {
                lastError = "AGAS Agent ${pkg.versionName} inutilisable : ${it.javaClass.simpleName} ${it.message.orEmpty()}"
                AgasLog.w(lastError!!)
                continue
            }
            agent = instance
            loaded = pkg
            AgasLog.i("AGAS Agent ${pkg.versionName} chargé (${pkg.source.label})")
            publish()
            return
        }
        publish(error = lastError ?: "Aucun Agent utilisable")
    }

    private fun instantiate(pkg: AgentPackage): Agent {
        // Le Manager est le parent : l'Agent utilise sa copie du contrat et de la bibliothèque Kotlin.
        val loader = DexClassLoader(pkg.file.path, null, null, AgentRuntime::class.java.classLoader)
        return loader.loadClass(pkg.entryClass).getDeclaredConstructor().newInstance() as Agent
    }

    private fun syncRunning() {
        val shouldRun = service != null && agent != null && settings.values.value.enabled
        if (shouldRun && !started) {
            started = true
            guarded("démarrage") { agent?.start(Host) }
        } else if (!shouldRun && started) {
            stopAgent()
        }
        publish()
    }

    private fun stopAgent() {
        if (!started) return
        started = false
        guarded("arrêt") { agent?.stop() }
        AgasEvents.setAdStatus(null)
    }

    private fun publish(error: String? = _state.value.error.takeIf { agent == null }) {
        _state.value = State(
            agent = loaded,
            settings = agent?.let { a -> runCatching { a.settings }.getOrNull() }.orEmpty(),
            running = started,
            error = if (agent == null) error else null,
            hasRejected = store.hasBad(),
        )
    }

    /** Une erreur de l'Agent ne doit pas faire tomber le Manager : on la note (au plus toutes les 10 s). */
    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            val now = SystemClock.uptimeMillis()
            if (now - lastErrorLogAt > ERROR_LOG_INTERVAL_MS) {
                lastErrorLogAt = now
                AgasLog.w("Erreur de l'Agent ($what) : ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            }
        }
    }

    /**
     * Une exception non rattrapée dans le code de l'Agent (dans ses tâches programmées, par exemple)
     * fait planter le processus. On note le plantage ; au deuxième, cette version est écartée et le
     * redémarrage du service chargera la précédente.
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            val pkg = loaded
            if (pkg != null && e.comesFromAgent()) {
                runCatching {
                    if (store.recordCrash(pkg.sha256)) {
                        Log.e("AGAS", "AGAS Agent ${pkg.versionName} écarté après plusieurs plantages", e)
                    }
                }
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun Throwable.comesFromAgent(): Boolean = generateSequence(this) { it.cause }.any { t ->
        t.stackTrace.any { it.className.startsWith(AGENT_CODE_PREFIX) && !it.className.startsWith(API_PREFIX) }
    }

    private val AgentPackage.Source.label
        get() = when (this) {
            AgentPackage.Source.BUNDLED -> "version intégrée"
            AgentPackage.Source.DOWNLOADED -> "version téléchargée"
        }

    /** Ce que le Manager fournit à l'Agent. */
    private object Host : AgentHost {
        override val service: AccessibilityService
            get() = checkNotNull(AgentRuntime.service) { "Service d'accessibilité déconnecté" }

        override val dryRun: Boolean get() = settings.values.value.dryRun

        override fun setting(key: String): Boolean {
            val default = _state.value.settings.firstOrNull { it.key == key }?.default ?: false
            return settings.agentSetting(key, default)
        }

        override fun log(level: LogLevel, message: String) {
            when (level) {
                LogLevel.DEBUG -> AgasLog.d(message)
                LogLevel.INFO -> AgasLog.i(message)
                LogLevel.ACTION -> AgasLog.action(message)
                LogLevel.WARN -> AgasLog.w(message)
            }
        }

        override fun setAdStatus(network: String, phase: AdPhase) = AgasEvents.setAdStatus(AgasEvents.AdStatus(network, phase))

        override fun clearAdStatus() = AgasEvents.setAdStatus(null)

        override fun toast(message: String) = AgasEvents.toast(message)

        override fun recordAdSkipped() = settings.recordAdSkipped()

        override fun recordClick() = settings.recordClick()

        override fun recordHijackBlocked() = settings.recordHijackBlocked()
    }

    private const val AGENT_CODE_PREFIX = "fr.nzosifou.agas.agent."
    private const val API_PREFIX = "fr.nzosifou.agas.agent.api."
    private const val ERROR_LOG_INTERVAL_MS = 10_000L
}
