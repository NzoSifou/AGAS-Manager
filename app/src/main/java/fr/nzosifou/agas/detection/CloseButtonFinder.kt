package fr.nzosifou.agas.detection

import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import fr.nzosifou.agas.rules.AdRules
import fr.nzosifou.agas.rules.containsMatch
import fr.nzosifou.agas.rules.matchesFully
import kotlin.math.hypot

/** Une fenêtre de la pub à analyser (la pub plein écran elle-même, ou une popup par-dessus). */
class ScanWindow(val root: AccessibilityNodeInfo, val bounds: Rect)

/** Un bouton qui ressemble à un bouton de fermeture. */
class Candidate(
    /** Nœud sur lequel faire ACTION_CLICK : le bouton lui-même ou un petit parent cliquable. */
    val clickTarget: AccessibilityNodeInfo,
    val bounds: Rect,
    val score: Int,
    /** false = bouton sans libellé repéré seulement par sa forme et sa position (moins sûr). */
    val strong: Boolean,
    val reason: String,
    /** Identifie ce bouton d'un passage à l'autre, pour compter les tentatives. */
    val key: String,
    /**
     * Un appui simulé à [center] atteint bien ce bouton. Faux pour un élément HTML non déclaré
     * cliquable recouvert par un autre élément cliquable de la page (l'appui irait sur la pub) :
     * seul le clic d'accessibilité, que Chromium remet à l'élément lui-même, est alors permis.
     */
    var tapAllowed: Boolean = true,
    /** Identité du bouton d'une pub à l'autre (id ou libellé), pour la mémoire des pièges. */
    val trapKey: String = "",
    /**
     * Élément d'une page web : les pages ignorent souvent le clic d'accessibilité (seul un vrai
     * toucher déclenche leurs gestionnaires), on commence donc par l'appui simulé s'il est sûr.
     */
    val inWebView: Boolean = false,
) {
    val center: Point get() = Point(bounds.centerX(), bounds.centerY())
}

sealed interface ScanResult {
    /** « Vous allez perdre votre récompense » : on clique sur « Reprendre » (si on le trouve). */
    class RewardWarning(val resume: Candidate?) : ScanResult

    /** Un compte à rebours de récompense ou de fermeture est affiché : on attend. */
    class Countdown(val text: String) : ScanResult

    class Found(val candidate: Candidate) : ScanResult

    /**
     * [playable] : la pub affiche un mini-jeu (voir AdRules.playableMarkerIdPatterns), et aucune
     * vidéo ne le recouvre (les pubs AppLovin chargent le mini-jeu derrière leur vidéo).
     * [storeExit] : bouton « Google Play » / « Ouvrir la boutique », seule sortie de certains
     * mini-jeux (voir AdRules.storeExitTextPatterns), à n'utiliser qu'en dernier recours.
     */
    class Nothing(val nodeCount: Int, val playable: Boolean = false, val storeExit: Candidate? = null) : ScanResult
}

/**
 * Cherche le bouton de fermeture d'une pub dans l'arbre d'accessibilité.
 *
 * Principes :
 * - un texte, une description ou un identifiant de type « close / skip / fermer » est un signal fort ;
 * - un texte de type « installer / en savoir plus / ouvrir » exclut toujours le nœud ;
 * - les grands éléments sont exclus (cliquer dessus reviendrait à cliquer sur la pub) ;
 * - un bouton pas encore cliquable n'est jamais pris : un appui simulé le traverserait jusqu'à la
 *   pub dessous (souvent sans que la zone de la pub soit déclarée cliquable), qui ouvrirait alors
 *   le Play Store / AliExpress. On attend qu'il devienne cliquable ;
 * - les nœuds dans une WebView sont pénalisés : les fausses croix sont en général dessinées dans le
 *   contenu de la pub, alors que la vraie croix est un bouton natif de la régie.
 */
class CloseButtonFinder(private val rules: AdRules) {

    fun scan(
        windows: List<ScanWindow>,
        /** Positions ayant déjà provoqué l'ouverture d'une autre appli : on ne clique plus à cet endroit. */
        blacklist: List<Point>,
        /** Boutons abandonnés (trop de tentatives sans effet). */
        ignoredKeys: Set<String>,
        allowUnlabeled: Boolean,
        /** Pièges appris (voir TrapMemory), d'après [Candidate.trapKey]. */
        isTrap: (String) -> Boolean = { false },
    ): ScanResult {
        val screen = Rect().apply { windows.forEach { union(it.bounds) } }
        if (screen.isEmpty) return ScanResult.Nothing(0)

        val texts = mutableListOf<String>()
        val candidates = mutableListOf<Candidate>()
        val resumeCandidates = mutableListOf<Candidate>()
        /** Éléments cliquables des pages web (liens de la pub…), pour savoir où un appui atterrirait. */
        val webClickables = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        var nodeCount = 0
        var playable = false
        var videoOnScreen = false
        var countdownOnScreen = false
        var storeExit: Candidate? = null

        windows.forEachIndexed { windowRank, window ->
            val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
            stack.addLast(window.root to false)
            while (stack.isNotEmpty() && nodeCount < MAX_NODES) {
                val (node, parentInWebView) = stack.removeLast()
                // Avant Android 14, le cache d'accessibilité ne peut pas être désactivé : on force la
                // relecture (voir AdSkipperService.onServiceConnected).
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) node.refresh()
                nodeCount++
                val inWebView = parentInWebView || node.className?.contains("WebView") == true
                val markerId = node.viewIdResourceName?.substringAfter(":id/")
                if (markerId != null) {
                    if (rules.playableMarkerIdPatterns.containsMatch(markerId)) playable = true
                    // Vidéo qui occupe l'essentiel de la pub : c'est elle qu'on voit, pas le mini-jeu.
                    if (!videoOnScreen && node.isVisibleToUser && rules.videoMarkerIdPatterns.containsMatch(markerId)) {
                        val b = boundsOf(node)
                        videoOnScreen = b.width().toLong() * b.height() >
                            window.bounds.width().toLong() * window.bounds.height() / 2
                    }
                }

                // Un nœud invisible peut avoir des enfants visibles : Compose signale par exemple
                // ses conteneurs de vues Android (ViewFactoryHolder) comme invisibles, alors que le
                // bouton « Skip » d'une pub Moloco est dedans. On l'ignore, mais on descend quand même.
                if (node.isVisibleToUser) {
                    val labels = labelsOf(node)
                    texts += labels
                    // Petit nombre seul (« 26 ») : compte à rebours avant le bouton de sortie.
                    if (!countdownOnScreen && labels.any { COUNTDOWN_NUMBER.matches(it) }) {
                        countdownOnScreen = boundsOf(node).width() < window.bounds.width() * 0.15f
                    }
                    if (parentInWebView && node.isClickable && node.className?.contains("WebView") != true) {
                        webClickables += node to boundsOf(node)
                    }

                    evaluate(node, labels, inWebView, window.bounds, screen, windowRank, allowUnlabeled)
                        ?.takeIf { it.key !in ignoredKeys && !isBlacklisted(it.center, blacklist) && !isTrap(it.trapKey) }
                        ?.let { candidates += it }

                    if (storeExit == null) {
                        labels.firstOrNull { rules.storeExitTextPatterns.matchesFully(it) }?.let { label ->
                            val bounds = boundsOf(node)
                            clickableTarget(node, screen)?.takeIf { window.bounds.contains(bounds.centerX(), bounds.centerY()) }
                                ?.let { target ->
                                    storeExit = Candidate(
                                        target, bounds, 0, true, "bouton de sortie « $label »", keyOf(node, bounds, labels),
                                        trapKey = label, inWebView = inWebView,
                                    )
                                }
                        }
                    }

                    if (labels.any { rules.rewardResumePatterns.matchesFully(it) }) {
                        val bounds = boundsOf(node)
                        clickableTarget(node, screen)?.let { target ->
                            resumeCandidates += Candidate(
                                target, bounds, 0, true, "« ${labels.first()} »", keyOf(node, bounds, labels),
                            )
                        }
                    }
                }

                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.addLast(it to inWebView) }
                }
            }
        }

        // Élément HTML non cliquable : l'appui simulé n'est permis que si aucun autre élément
        // cliquable de la page ne recouvre son centre.
        for (c in candidates) {
            if (c.clickTarget.isClickable) continue
            c.tapAllowed = webClickables.none { (other, r) -> other != c.clickTarget && r.contains(c.center.x, c.center.y) }
        }

        if (texts.any { rules.rewardWarningPatterns.containsMatch(it) }) {
            return ScanResult.RewardWarning(resumeCandidates.firstOrNull())
        }
        texts.firstOrNull { rules.rewardCountdownPatterns.containsMatch(it) }?.let {
            return ScanResult.Countdown(it)
        }
        val best = candidates.filter { it.strong }.maxByOrNull { it.score }
            ?: candidates.maxByOrNull { it.score }
        // Réveiller un mini-jeu n'a de sens que s'il attend un toucher : pas pendant une vidéo, ni
        // pendant un compte à rebours (le bouton viendra de lui-même à la fin).
        val wakeable = playable && !videoOnScreen && !countdownOnScreen
        return best?.let { ScanResult.Found(it) } ?: ScanResult.Nothing(nodeCount, wakeable, storeExit)
    }

    /**
     * Point « neutre » pour réveiller un mini-jeu d'un seul appui : à l'intérieur de la pub, mais en
     * dehors de tout bouton précis (« Installer », « PLAY », icône de la boutique…) et loin des
     * points [avoid] (appuis qui ont déjà ouvert une autre appli). Null si aucun point n'est sûr.
     */
    fun neutralPoint(windows: List<ScanWindow>, avoid: List<Point>): Point? {
        val window = windows.firstOrNull() ?: return null
        val w = window.bounds
        val buttons = mutableListOf<Rect>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { addLast(window.root) }
        var count = 0
        while (stack.isNotEmpty() && count++ < MAX_NODES) {
            val node = stack.removeLast()
            if (node.isVisibleToUser && node.isClickable) {
                val b = boundsOf(node)
                // Les grandes zones cliquables (toute la pub, le canevas du jeu) ne sont pas des boutons.
                if (b.width().toLong() * b.height() < w.width().toLong() * w.height() * NEUTRAL_MAX_BUTTON_AREA) {
                    buttons += b.apply { inset(-NEUTRAL_MARGIN_PX, -NEUTRAL_MARGIN_PX) }
                }
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it) }
        }
        return NEUTRAL_SPOTS
            .map { (fx, fy) -> Point((w.left + w.width() * fx).toInt(), (w.top + w.height() * fy).toInt()) }
            .firstOrNull { p -> buttons.none { it.contains(p.x, p.y) } && !isBlacklisted(p, avoid) }
    }

    private fun evaluate(
        node: AccessibilityNodeInfo,
        labels: List<String>,
        inWebView: Boolean,
        windowBounds: Rect,
        screen: Rect,
        windowRank: Int,
        allowUnlabeled: Boolean,
    ): Candidate? {
        if (!node.isEnabled) return null
        val bounds = boundsOf(node)
        if (bounds.width() < MIN_SIZE_PX || bounds.height() < MIN_SIZE_PX) return null
        // En cours d'animation (entrée / sortie d'écran) : on attendra qu'il soit en place.
        if (!windowBounds.contains(bounds.centerX(), bounds.centerY())) return null
        // Trop grand : c'est la zone cliquable de la pub, pas un bouton de fermeture.
        if (bounds.width() > screen.width() * MAX_WIDTH_RATIO) return null
        if (bounds.width().toLong() * bounds.height() > screen.width().toLong() * screen.height() * MAX_AREA_RATIO) return null

        val id = node.viewIdResourceName?.substringAfter(":id/").orEmpty()
        if (id.isNotEmpty() && rules.negativeIdPatterns.containsMatch(id)) return null
        if (labels.any { rules.negativeTextPatterns.containsMatch(it) }) return null
        // Un nombre seul (« 5 », « 12s ») est un compte à rebours, pas encore un bouton.
        if (labels.any { COUNTDOWN_NUMBER.matches(it) }) return null

        val textMatch = labels.firstOrNull { rules.closeTextPatterns.matchesFully(it) }
        val symbolMatch = labels.firstOrNull { rules.closeSymbolPatterns.matchesFully(it) }
            ?: rawLabelsOf(node).firstOrNull { SvgIcons.isSvgDataUri(it) && SvgIcons.looksLikeCross(it) }
                ?.let { "icône SVG ×" }
        val idMatch = id.isNotEmpty() && rules.closeIdPatterns.containsMatch(id)

        var score: Int
        val reason: String
        val strong: Boolean
        when {
            textMatch != null -> { score = 60; reason = "texte « $textMatch »"; strong = true }
            idMatch -> { score = 50; reason = "id « $id »"; strong = true }
            symbolMatch != null -> { score = 45; reason = "symbole « $symbolMatch »"; strong = true }
            // Aussi dans les WebView (croix des écrans de fin AppLovin, par exemple) : le score y est
            // plus bas (pénalité WebView), et les fausses croix sont couvertes par la protection
            // anti-détournement et la mémoire des pièges.
            allowUnlabeled && labels.isEmpty() && looksLikeUnlabeledCloseIcon(node, bounds, windowBounds) -> {
                score = 20; reason = "icône sans libellé en haut ${cornerName(bounds, windowBounds)}"; strong = false
            }
            else -> return null
        }
        if (idMatch && textMatch != null) score += 15

        val ratio = bounds.width().toFloat() / bounds.height()
        if (ratio in 0.6f..1.6f) score += 10
        if (isInCorner(bounds, windowBounds)) score += 15
        if (bounds.centerY() < windowBounds.top + windowBounds.height() * CORNER_RATIO_Y) score += 5
        if (inWebView) score -= 15
        if (windowRank == 0) score += 5

        val target = clickableTarget(node, screen)
        if (target === node) score += 10
        // Pas (encore) cliquable : on attend (voir la documentation de la classe). Exception : dans
        // une WebView, les boutons HTML à gestionnaire JavaScript ne sont jamais déclarés cliquables.
        // Là, le navigateur remet un appui à l'élément visible à cet endroit (pas de « traversée »
        // comme avec les vues Android) : c'est sûr tant qu'aucun autre élément cliquable de la page
        // ne recouvre ce point (vérifié à la fin du parcours, voir scan).
        if (target == null && !(inWebView && strong)) return null

        val trapKey = node.viewIdResourceName?.substringAfter(":id/")
            ?: labels.firstOrNull()
            ?: "icône ${cornerName(bounds, windowBounds)}"
        return Candidate(
            target ?: node, bounds, score, strong, reason, keyOf(node, bounds, labels),
            trapKey = trapKey, inWebView = inWebView,
        )
    }

    private fun looksLikeUnlabeledCloseIcon(node: AccessibilityNodeInfo, bounds: Rect, window: Rect): Boolean {
        val cls = node.className?.toString().orEmpty()
        // TextView vide : croix dessinée en CSS dans les pubs HTML (Moloco MRAID, par exemple).
        val iconLike = cls.endsWith("ImageView") || cls.endsWith("ImageButton") ||
            cls.endsWith(".View") || cls.endsWith("Button") || cls.endsWith("TextView")
        val ratio = bounds.width().toFloat() / bounds.height()
        return iconLike && node.isClickable &&
            ratio in 0.6f..1.6f &&
            bounds.width() <= window.width() * 0.15f &&
            isInCorner(bounds, window) &&
            bounds.centerY() < window.top + window.height() * CORNER_RATIO_Y
    }

    /** Le nœud s'il est cliquable, sinon un parent proche cliquable et petit, sinon null. */
    private fun clickableTarget(node: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var current = node.parent
        repeat(MAX_PARENT_LEVELS) {
            val parent = current ?: return null
            if (parent.isClickable) {
                val b = boundsOf(parent)
                val small = b.width() <= screen.width() * MAX_WIDTH_RATIO &&
                    b.width().toLong() * b.height() <= screen.width().toLong() * screen.height() * MAX_AREA_RATIO
                return if (small) parent else null
            }
            current = parent.parent
        }
        return null
    }

    private fun isInCorner(b: Rect, w: Rect): Boolean {
        val left = b.centerX() < w.left + w.width() * CORNER_RATIO_X
        val right = b.centerX() > w.right - w.width() * CORNER_RATIO_X
        val top = b.centerY() < w.top + w.height() * CORNER_RATIO_Y
        val bottom = b.centerY() > w.bottom - w.height() * CORNER_RATIO_Y
        return (left || right) && (top || bottom)
    }

    private fun cornerName(b: Rect, w: Rect) = if (b.centerX() < w.centerX()) "à gauche" else "à droite"

    private fun isBlacklisted(p: Point, blacklist: List<Point>) =
        blacklist.any { hypot((it.x - p.x).toDouble(), (it.y - p.y).toDouble()) < BLACKLIST_RADIUS_PX }

    companion object {
        private const val MAX_NODES = 3000
        private const val MIN_SIZE_PX = 8
        private const val MAX_WIDTH_RATIO = 0.45f
        private const val MAX_AREA_RATIO = 0.12f
        private const val CORNER_RATIO_X = 0.25f
        private const val CORNER_RATIO_Y = 0.20f
        private const val MAX_PARENT_LEVELS = 3
        private const val BLACKLIST_RADIUS_PX = 48.0
        private const val NEUTRAL_MAX_BUTTON_AREA = 0.25f
        private const val NEUTRAL_MARGIN_PX = 40
        /** Emplacements essayés pour l'appui de réveil, en fractions de la fenêtre (milieu d'abord). */
        private val NEUTRAL_SPOTS = listOf(
            0.5f to 0.5f, 0.5f to 0.4f, 0.3f to 0.55f, 0.7f to 0.55f, 0.5f to 0.65f, 0.3f to 0.4f, 0.7f to 0.4f,
        )
        private val COUNTDOWN_NUMBER = Regex("""\d{1,3}\s*(s|sec)?""", RegexOption.IGNORE_CASE)

        private val BASE64_FRAGMENT = Regex("""[A-Za-z0-9+/%=]{16,}""")

        private fun rawLabelsOf(node: AccessibilityNodeInfo): List<String> =
            listOfNotNull(node.text, node.contentDescription)
                .map { it.toString().trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        /**
         * Texte et description lisibles. Les images des pubs HTML exposent parfois leur contenu
         * encodé (`svg+xml;base64,…`, fragments base64) : ce n'est pas du texte, et on risquerait
         * d'y « lire » par hasard « shop » ou « install ».
         */
        fun labelsOf(node: AccessibilityNodeInfo): List<String> =
            rawLabelsOf(node).filterNot { SvgIcons.isSvgDataUri(it) || BASE64_FRAGMENT.matches(it) }

        fun boundsOf(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }

        private fun keyOf(node: AccessibilityNodeInfo, b: Rect, labels: List<String>): String {
            // Arrondi pour rester stable si la vue bouge de quelques pixels (animation).
            fun r(v: Int) = v / 16
            return "${node.viewIdResourceName}|${r(b.left)},${r(b.top)},${r(b.right)},${r(b.bottom)}|$labels"
        }
    }
}
