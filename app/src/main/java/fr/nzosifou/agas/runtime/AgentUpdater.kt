package fr.nzosifou.agas.runtime

import android.content.Context
import fr.nzosifou.agas.data.AgasEvents
import fr.nzosifou.agas.data.AgasLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Mises à jour depuis les releases GitHub : celles d'AGAS Agent (téléchargées et chargées à chaud)
 * et celles d'AGAS Manager (signalées seulement : un APK s'installe à la main).
 *
 * C'est la seule connexion d'AGAS à Internet : elle ne fait que télécharger, rien n'est envoyé.
 */
object AgentUpdater {

    /** Une release GitHub. */
    class Release(val version: SemVer, val pageUrl: String, val apkUrl: String?, val notes: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        class Available(val release: Release) : Status
        class Downloading(val release: Release) : Status
        class Installed(val versionName: String) : Status
        /** La nouvelle version de l'Agent demande un Manager plus récent. */
        class NeedsNewerManager(val version: SemVer) : Status
        class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _managerRelease = MutableStateFlow<Release?>(null)

    /** Version plus récente d'AGAS Manager publiée, null si à jour (ou pas encore vérifié). */
    val managerRelease: StateFlow<Release?> = _managerRelease.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()

    /** Vérification automatique, au plus une fois toutes les [AUTO_CHECK_INTERVAL_MS]. */
    fun checkIfDue(context: Context, install: Boolean) {
        val prefs = context.getSharedPreferences("agas_agent", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(K_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        check(context, install)
    }

    /** Cherche une nouvelle version ; l'installe directement si [install]. */
    fun check(context: Context, install: Boolean) {
        val app = context.applicationContext
        if (_status.value is Status.Checking || _status.value is Status.Downloading) return
        _status.value = Status.Checking
        executor.execute {
            try {
                app.getSharedPreferences("agas_agent", Context.MODE_PRIVATE).edit()
                    .putLong(K_LAST_CHECK, System.currentTimeMillis()).apply()

                val ownVersion = SemVer.parse(app.packageManager.getPackageInfo(app.packageName, 0).versionName)
                _managerRelease.value = latestRelease(MANAGER_REPO)
                    ?.takeIf { ownVersion == null || it.version > ownVersion }

                val current = AgentRuntime.state.value.agent?.version
                val latest = latestRelease(AGENT_REPO)
                when {
                    latest == null || latest.apkUrl == null || (current != null && latest.version <= current) -> {
                        _status.value = Status.UpToDate
                    }
                    install -> download(app, latest)
                    else -> {
                        AgasLog.i("AGAS Agent ${latest.version} disponible")
                        _status.value = Status.Available(latest)
                    }
                }
            } catch (e: IOException) {
                AgasLog.d("Recherche de mise à jour impossible : ${e.message}")
                _status.value = Status.Failed("Pas de connexion à GitHub")
            } catch (e: Exception) {
                AgasLog.w("Recherche de mise à jour : ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                _status.value = Status.Failed("Réponse de GitHub illisible")
            }
        }
    }

    /** Installe la version trouvée par [check]. */
    fun install(context: Context, release: Release) {
        val app = context.applicationContext
        executor.execute { download(app, release) }
    }

    private fun download(context: Context, release: Release) {
        val url = release.apkUrl ?: return
        _status.value = Status.Downloading(release)
        val tmp = File(context.cacheDir, "agent-download.apk")
        try {
            open(url).inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK_BYTES) throw IOException("fichier trop gros")
                        output.write(buffer, 0, n)
                    }
                }
            }
            val pkg = AgentRuntime.install(tmp)
            AgasLog.i("AGAS Agent ${pkg.versionName} installé")
            AgasEvents.toast("AGAS Agent ${pkg.versionName} installé")
            _status.value = Status.Installed(pkg.versionName)
        } catch (e: AgentRejected) {
            AgasLog.w("Mise à jour refusée : ${e.message}")
            _status.value = if (e.needsNewerManager) Status.NeedsNewerManager(release.version)
            else Status.Failed(e.message ?: "Mise à jour refusée")
        } catch (e: IOException) {
            AgasLog.w("Téléchargement de l'Agent impossible : ${e.message}")
            _status.value = Status.Failed("Téléchargement impossible")
        } finally {
            tmp.delete()
        }
    }

    /** Dernière release publiée (hors brouillons et préversions), null s'il n'y en a aucune. */
    private fun latestRelease(repo: String): Release? {
        val connection = open("https://api.github.com/repos/$OWNER/$repo/releases/latest")
        return when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
            HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw IOException("GitHub a répondu $code")
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AGAS-Manager")
        }

    /** Lit la réponse de l'API GitHub (`releases/latest`). */
    fun parseRelease(json: String): Release? {
        val o = JSONObject(json)
        val version = SemVer.parse(o.optString("tag_name")) ?: return null
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        return Release(
            version = version,
            pageUrl = o.optString("html_url"),
            apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
            notes = o.optString("body"),
        )
    }

    private const val OWNER = "NzoSifou"
    private const val AGENT_REPO = "AGAS-Agent"
    private const val MANAGER_REPO = "AGAS-Manager"
    private const val K_LAST_CHECK = "last_check"
    private const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60_000L
    private const val TIMEOUT_MS = 15_000
    private const val MAX_APK_BYTES = 20L * 1024 * 1024
}
