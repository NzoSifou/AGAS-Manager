package fr.nzosifou.agas.guard

import android.graphics.Point

/**
 * Détecte qu'un clic d'AGAS sur une « croix » a en réalité ouvert une autre application
 * (Play Store, navigateur, AliExpress…).
 *
 * Après chaque clic, le service interroge [check] régulièrement avec l'appli réellement au premier
 * plan (d'après les fenêtres à l'écran). On conclut à un détournement si TOUTES ces conditions sont
 * réunies :
 * 1. AGAS a cliqué il y a moins de [ARM_DURATION_MS] (les redirections en chaîne, page relais →
 *    AliExpress, prennent plusieurs secondes) ;
 * 2. l'utilisateur n'a pas touché la pub entre-temps ;
 * 3. l'écran d'accueil n'est pas apparu entre-temps : si l'utilisateur appuie sur Accueil puis ouvre
 *    une appli, le lanceur désarme la protection ;
 * 4. l'appli au premier plan n'est ni le jeu, ni AGAS, ni une fenêtre système (barre d'état,
 *    clavier…). Exception : une page de pub ouverte DANS le jeu (fiche Play Store intégrée,
 *    navigateur interne) compte aussi comme un détournement.
 */
class HijackGuard(
    private val ownPackage: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Un clic d'AGAS en attente de vérification. */
    data class Armed(val gamePackage: String, val adActivity: String, val clickPoint: Point, val armedAt: Long)

    /** Lanceurs (écran d'accueil / applis récentes) : leur apparition = navigation volontaire. */
    var launcherPackages: Set<String> = emptySet()

    /** Fenêtres qui peuvent apparaître par-dessus sans signifier un détournement. */
    var ignoredPackages: Set<String> = emptySet()

    private var armed: Armed? = null

    /** Appli suspecte vue au contrôle précédent : il faut la voir deux fois de suite pour conclure. */
    private var suspect: String? = null

    fun arm(gamePackage: String, adActivity: String, clickPoint: Point) {
        armed = Armed(gamePackage, adActivity, clickPoint, clock())
        suspect = null
    }

    fun disarm() {
        armed = null
        suspect = null
    }

    /** La protection en cours, ou null si elle a expiré (et la désarme alors). */
    fun active(): Armed? {
        val a = armed ?: return null
        if (clock() - a.armedAt > ARM_DURATION_MS) {
            armed = null
            return null
        }
        return a
    }

    /**
     * Un élément du paquet [packageName] a été cliqué. Nos propres clics en génèrent aussi, juste
     * après le clic : au-delà de [OWN_CLICK_ECHO_MS], c'est l'utilisateur qui interagit avec la pub.
     * Retourne true si la protection a été désarmée.
     */
    fun onViewClicked(packageName: String): Boolean {
        val a = active() ?: return false
        if (packageName == a.gamePackage && clock() - a.armedAt > OWN_CLICK_ECHO_MS) {
            armed = null
            return true
        }
        return false
    }

    /**
     * [topPackage] est au premier plan ; [isLandingPage] indique une page de pub ouverte dans le
     * jeu. Retourne le clic fautif s'il s'agit d'un détournement (et désarme), null sinon.
     *
     * Une appli suspecte doit être vue à deux contrôles consécutifs : certaines (Play Store…)
     * apparaissent une fraction de seconde sans que rien ne soit affiché.
     */
    fun check(topPackage: String?, isLandingPage: Boolean = false): Armed? {
        val a = active() ?: return null
        val suspicious = when (topPackage) {
            null, ownPackage -> false
            a.gamePackage -> isLandingPage
            in launcherPackages -> {
                armed = null
                return null
            }
            in ignoredPackages -> false
            else -> true
        }
        val key = if (isLandingPage) "$topPackage (page)" else topPackage
        if (!suspicious) {
            suspect = null
            return null
        }
        if (suspect != key) {
            suspect = key
            return null
        }
        armed = null
        suspect = null
        return a
    }

    companion object {
        const val ARM_DURATION_MS = 6000L
        /** Les mini-jeux (canevas HTML) signalent notre appui jusqu'à ~2 s plus tard. */
        const val OWN_CLICK_ECHO_MS = 3000L
    }
}
