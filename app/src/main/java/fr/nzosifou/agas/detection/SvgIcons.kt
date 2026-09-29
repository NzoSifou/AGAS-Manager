package fr.nzosifou.agas.detection

import java.util.Base64
import kotlin.math.abs

/**
 * Les pubs HTML dessinent souvent leur croix avec une image SVG intégrée : l'élément n'a alors
 * aucun libellé, seulement une URL `data:image/svg+xml;base64,…` que WebView expose comme texte.
 * On décode le SVG et on reconnaît une croix à sa forme.
 */
object SvgIcons {

    private const val MARKER = "svg+xml;base64,"
    private val PATH_DATA = Regex("""\bd="([^"]+)"""")
    private val NUMBER = Regex("""-?\d*\.?\d+""")
    private val LINE = Regex("""<line\b""")

    /** Le libellé est une image SVG intégrée (on ne doit alors pas le lire comme du texte). */
    fun isSvgDataUri(label: String) = label.contains(MARKER)

    fun looksLikeCross(label: String): Boolean {
        val svg = decode(label) ?: return false
        // Deux traits <line> qui se croisent.
        if (LINE.findAll(svg).count() == 2 && svg.contains("<path").not()) return true
        val paths = PATH_DATA.findAll(svg).map { it.groupValues[1] }.toList()
        return paths.size == 1 && isCrossPolygon(paths[0])
    }

    private fun decode(label: String): String? {
        val start = label.indexOf(MARKER).takeIf { it >= 0 } ?: return null
        val data = label.substring(start + MARKER.length).trim().trimEnd(')', '"', '\'')
        return runCatching { String(Base64.getMimeDecoder().decode(data)) }.getOrNull()
    }

    /**
     * La croix « Material » (et ses variantes) est un polygone de 12 sommets, en lignes droites
     * uniquement, dans un carré, symétrique par rapport à son centre.
     */
    internal fun isCrossPolygon(d: String): Boolean {
        if (d.any { it.isLetter() && it.uppercaseChar() !in "MLHVZ" }) return false
        // Coordonnées relatives (m, l, h, v) : non gérées. « z » (fermeture) n'a pas de coordonnées.
        if (d.any { it.isLetter() && it.isLowerCase() && it != 'z' }) return false
        val numbers = NUMBER.findAll(d).map { it.value.toDouble() }.toList()
        if (numbers.size !in 24..26) return false
        val points = numbers.take(24).chunked(2).map { it[0] to it[1] }
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val w = maxX - minX
        val h = maxY - minY
        if (w <= 0 || h <= 0 || abs(w - h) > 0.2 * w) return false
        // Symétrie centrale : chaque sommet a son opposé.
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val tolerance = 0.05 * w
        val symmetric = points.all { (x, y) ->
            points.any { (ox, oy) -> abs(ox - (2 * cx - x)) < tolerance && abs(oy - (2 * cy - y)) < tolerance }
        }
        // Une croix « × » touche les coins de son carré, un « + » (même forme tournée) jamais.
        val corner = 0.15 * w
        val touchesCorners = points.count { (x, y) ->
            (abs(x - minX) < corner || abs(x - maxX) < corner) && (abs(y - minY) < corner || abs(y - maxY) < corner)
        } >= 4
        return symmetric && touchesCorners
    }
}
