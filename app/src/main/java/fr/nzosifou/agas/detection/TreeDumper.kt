package fr.nzosifou.agas.detection

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistre l'arbre d'accessibilité (et une capture d'écran) d'une pub qu'AGAS n'a pas su fermer,
 * dans `Android/data/fr.nzosifou.agas/files/dumps/`. Ces fichiers servent à enrichir
 * `ad_rules.json` et à constituer le jeu de données du repli par vision.
 */
object TreeDumper {

    /** Nom de base (sans extension) pour un nouvel enregistrement : le .txt et le .png le partagent. */
    fun newBaseName(gamePackage: String): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date()) + "_" + gamePackage

    fun dump(context: Context, baseName: String, gamePackage: String, adActivity: String, windows: List<ScanWindow>): File? {
        val dir = context.getExternalFilesDir("dumps") ?: return null
        val file = File(dir, "$baseName.txt")
        val text = buildString {
            appendLine("jeu      : $gamePackage")
            appendLine("activity : $adActivity")
            windows.forEachIndexed { i, w ->
                appendLine()
                appendLine("=== fenêtre $i ${w.bounds.toShortString()} ===")
                appendNode(w.root, 0)
            }
        }
        return runCatching { file.writeText(text); file }.getOrNull()
    }

    /** Enregistre la capture d'écran associée à un dump (sert aussi de données pour la vision). */
    fun saveScreenshot(context: Context, baseName: String, bitmap: Bitmap): File? {
        val dir = context.getExternalFilesDir("dumps") ?: return null
        val file = File(dir, "$baseName.png")
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        }.getOrNull()
    }

    private fun StringBuilder.appendNode(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 60) return
        val b = CloseButtonFinder.boundsOf(node)
        append("  ".repeat(depth))
        append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { append(" id=").append(it.substringAfter(":id/")) }
        node.text?.let { append(" text=\"").append(it).append('"') }
        node.contentDescription?.let { append(" desc=\"").append(it).append('"') }
        append(' ').append(b.toShortString())
        if (node.isClickable) append(" [clic]")
        if (node.isLongClickable) append(" [clic-long]")
        if (!node.isEnabled) append(" [désactivé]")
        if (!node.isVisibleToUser) append(" [invisible]")
        appendLine()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { appendNode(it, depth + 1) }
        }
    }
}
