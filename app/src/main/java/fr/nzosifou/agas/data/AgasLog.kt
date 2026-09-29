package fr.nzosifou.agas.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Journal affiché dans l'application, recopié dans logcat (tag « AGAS ») et dans
 * `Android/data/fr.nzosifou.agas/files/agas_log.txt` : certains téléphones (Xiaomi…) vident
 * logcat en quelques minutes.
 */
object AgasLog {

    enum class Level { DEBUG, INFO, ACTION, WARN }

    data class Entry(val id: Long, val time: Long, val level: Level, val message: String)

    private const val TAG = "AGAS"
    private const val MAX_ENTRIES = 400
    private const val MAX_FILE_BYTES = 1_000_000L

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private var nextId = 0L
    private var file: File? = null
    private val fileWriter = Executors.newSingleThreadExecutor()
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    /** Active l'écriture dans le fichier (appelé par le service et par l'activité). */
    @Synchronized
    fun attach(context: Context) {
        if (file == null) file = context.getExternalFilesDir(null)?.let { File(it, "agas_log.txt") }
    }

    fun d(message: String) = add(Level.DEBUG, message)
    fun i(message: String) = add(Level.INFO, message)
    fun action(message: String) = add(Level.ACTION, message)
    fun w(message: String) = add(Level.WARN, message)

    /** Vide le journal affiché et le fichier. */
    fun clear() {
        _entries.update { emptyList() }
        val target = file ?: return
        fileWriter.execute { runCatching { target.writeText("") } }
    }

    /** Le journal en texte brut, du plus ancien au plus récent (pour le partager). */
    fun exportText(): String = _entries.value.reversed().joinToString("\n") { format(it) }

    @Synchronized
    private fun add(level: Level, message: String) {
        when (level) {
            Level.DEBUG -> Log.d(TAG, message)
            Level.WARN -> Log.w(TAG, message)
            else -> Log.i(TAG, message)
        }
        val entry = Entry(nextId++, System.currentTimeMillis(), level, message)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }

        val target = file ?: return
        val line = format(entry) + "\n"
        fileWriter.execute {
            runCatching {
                if (target.length() > MAX_FILE_BYTES) target.writeText("")
                target.appendText(line)
            }
        }
    }

    @Synchronized
    private fun format(e: Entry) = "${fileDateFormat.format(Date(e.time))} ${e.level.name.first()} ${e.message}"
}
