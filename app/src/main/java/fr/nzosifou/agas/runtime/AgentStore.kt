package fr.nzosifou.agas.runtime

import android.content.Context
import android.content.pm.PackageManager
import fr.nzosifou.agas.agent.api.AgentApi
import java.io.File
import java.security.MessageDigest

/** Un APK d'AGAS Agent dont la signature et le contrat ont été vérifiés. */
class AgentPackage(
    val file: File,
    val versionName: String,
    val versionCode: Long,
    val apiVersion: Int,
    val entryClass: String,
    val sha256: String,
    val source: Source,
) {
    enum class Source { BUNDLED, DOWNLOADED }

    val version: SemVer? get() = SemVer.parse(versionName)
}

/** Refus d'un APK d'Agent, avec un message affichable. */
class AgentRejected(message: String, val needsNewerManager: Boolean = false) : Exception(message)

/**
 * Les APK d'Agent disponibles sur le téléphone :
 * - la version intégrée au Manager (`assets/agent/agas-agent.apk`), pour fonctionner hors ligne ;
 * - la dernière version téléchargée, si elle est plus récente.
 *
 * Avant d'être chargé, un APK doit être signé par la clé d'AGAS (ou par celle du Manager lui-même,
 * pour les versions de développement) : l'Agent lit tout l'écran, on ne charge rien d'autre.
 */
class AgentStore(context: Context) {

    private val context = context.applicationContext
    private val dir = File(context.filesDir, "agent").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("agas_agent", Context.MODE_PRIVATE)

    /** Versions à essayer, de la meilleure à la moins bonne (hors versions défaillantes). */
    fun candidates(): List<AgentPackage> {
        val bad = badHashes()
        return listOfNotNull(downloaded(), bundled())
            .filter { it.sha256 !in bad }
            // À version égale, la version téléchargée l'emporte (versions de développement).
            .sortedWith(compareByDescending<AgentPackage> { it.versionCode }.thenByDescending { it.source })
    }

    fun bundled(): AgentPackage? = runCatching {
        // Extraite à chaque installation du Manager (date de mise à jour dans le nom).
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val file = File(dir, "bundled-$stamp.apk")
        if (!file.exists()) {
            dir.listFiles { f -> f.name.startsWith("bundled-") }?.forEach { it.delete() }
            val tmp = File(dir, "bundled.tmp")
            context.assets.open(BUNDLED_ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        }
        verify(file, AgentPackage.Source.BUNDLED)
    }.getOrNull()

    fun downloaded(): AgentPackage? {
        val name = prefs.getString(K_DOWNLOADED, null) ?: return null
        return runCatching { verify(File(dir, name), AgentPackage.Source.DOWNLOADED) }.getOrNull()
    }

    /**
     * Vérifie [apk] (téléchargé ou copié) et en fait la version téléchargée en vigueur. Lève
     * [AgentRejected] s'il n'est pas acceptable ; [apk] est supprimé dans tous les cas.
     */
    fun install(apk: File): AgentPackage {
        try {
            val checked = verify(apk, AgentPackage.Source.DOWNLOADED)
            val target = File(dir, "agent-${checked.versionCode}-${checked.sha256.take(12)}.apk")
            if (!target.exists()) apk.copyTo(target)
            val previous = prefs.getString(K_DOWNLOADED, null)
            prefs.edit().putString(K_DOWNLOADED, target.name).commit()
            if (previous != null && previous != target.name) File(dir, previous).delete()
            // Une nouvelle publication de la même version a droit à un nouvel essai.
            unmarkBad(checked.sha256)
            return verify(target, AgentPackage.Source.DOWNLOADED)
        } finally {
            apk.delete()
        }
    }

    /** Oublie la version téléchargée : retour à la version intégrée. */
    fun removeDownloaded() {
        val previous = prefs.getString(K_DOWNLOADED, null) ?: return
        prefs.edit().remove(K_DOWNLOADED).commit()
        File(dir, previous).delete()
    }

    /**
     * Note un plantage de cette version (écriture immédiate : le processus va mourir). Deux
     * plantages à moins de [CRASH_WINDOW_MS] d'intervalle l'écartent ; retourne true dans ce cas.
     */
    fun recordCrash(sha256: String): Boolean {
        val now = System.currentTimeMillis()
        val recent = now - prefs.getLong(K_LAST_CRASH + sha256, 0L) < CRASH_WINDOW_MS
        val count = if (recent) prefs.getInt(K_CRASHES + sha256, 0) + 1 else 1
        val editor = prefs.edit().putLong(K_LAST_CRASH + sha256, now).putInt(K_CRASHES + sha256, count)
        if (count >= MAX_CRASHES) editor.putStringSet(K_BAD, badHashes() + sha256)
        editor.commit()
        return count >= MAX_CRASHES
    }

    fun clearBad() {
        val editor = prefs.edit().remove(K_BAD)
        prefs.all.keys.filter { it.startsWith(K_CRASHES) || it.startsWith(K_LAST_CRASH) }.forEach(editor::remove)
        editor.commit()
    }

    fun hasBad(): Boolean = badHashes().isNotEmpty()

    private fun unmarkBad(sha256: String) {
        prefs.edit().putStringSet(K_BAD, badHashes() - sha256).commit()
    }

    private fun badHashes(): Set<String> = prefs.getStringSet(K_BAD, emptySet()).orEmpty()

    private fun verify(file: File, source: AgentPackage.Source): AgentPackage {
        if (!file.isFile) throw AgentRejected("Fichier introuvable")
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(
            file.path, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA,
        ) ?: throw AgentRejected("APK illisible")
        if (info.packageName != AgentApi.AGENT_PACKAGE) throw AgentRejected("Ce fichier n'est pas un AGAS Agent")

        val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }
        val trusted = trustedCertificates()
        if (signers.isEmpty() || signers.any { it !in trusted }) {
            throw AgentRejected("Signature inconnue : cet Agent n'a pas été publié par AGAS")
        }

        val meta = info.applicationInfo?.metaData ?: throw AgentRejected("Agent incomplet (meta-data)")
        val api = meta.get(AgentApi.META_API_VERSION)?.toString()?.toIntOrNull()
            ?: throw AgentRejected("Agent incomplet (version du contrat)")
        val entry = meta.getString(AgentApi.META_ENTRY_CLASS)
            ?: throw AgentRejected("Agent incomplet (classe d'entrée)")
        val versionName = info.versionName ?: "?"
        if (api > AgentApi.VERSION) {
            throw AgentRejected("AGAS Agent $versionName demande une version plus récente d'AGAS Manager", needsNewerManager = true)
        }
        // Android 14+ refuse de charger du code depuis un fichier modifiable.
        file.setReadOnly()
        return AgentPackage(file, versionName, info.longVersionCode, api, entry, sha256(file.readBytes()), source)
    }

    /** Clé de publication d'AGAS, et clé du Manager lui-même (versions de développement). */
    private fun trustedCertificates(): Set<String> {
        val own = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners.orEmpty()
            .map { sha256(it.toByteArray()) }
        return own.toSet() + RELEASE_CERTIFICATE_SHA256
    }

    companion object {
        /** Certificat de publication d'AGAS (SHA-256), le même pour le Manager et l'Agent. */
        const val RELEASE_CERTIFICATE_SHA256 = "8cb143147a9d67f813e749af1c6413240e02bb643eb1a8dfa3fc1a7dc19e5804"
        private const val BUNDLED_ASSET = "agent/agas-agent.apk"
        private const val K_DOWNLOADED = "downloaded"
        private const val K_BAD = "bad"
        private const val K_CRASHES = "crashes_"
        private const val K_LAST_CRASH = "last_crash_"
        private const val MAX_CRASHES = 2
        private const val CRASH_WINDOW_MS = 10 * 60_000L

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
