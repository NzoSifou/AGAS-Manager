package fr.nzosifou.agas.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Boutons « pièges » appris : un clic dessus a ouvert une autre appli (Play Store, AliExpress…).
 *
 * Exemple réel : dans les pubs Fyber, `skip_img` est cliquable dès la première seconde, mais un
 * clic à ce moment ouvre la boutique. On mémorise le piège par type de pub (Activity de la régie) :
 * - vu une fois : ignoré pendant les [GRACE_MS] premières millisecondes des pubs suivantes (il peut
 *   devenir le vrai bouton plus tard) ;
 * - vu deux fois ou plus : ignoré définitivement pour ce type de pub.
 */
class TrapMemory private constructor(private val prefs: SharedPreferences) {

    fun record(adActivity: String, trapKey: String) {
        val key = key(adActivity, trapKey)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /** Ce bouton est-il un piège connu, [adAgeMs] après l'ouverture de la pub ? */
    fun isTrap(adActivity: String, trapKey: String, adAgeMs: Long): Boolean {
        val count = prefs.getInt(key(adActivity, trapKey), 0)
        return count >= PERMANENT_AFTER || (count > 0 && adAgeMs < GRACE_MS)
    }

    fun count(): Int = prefs.all.size

    fun clear() = prefs.edit().clear().apply()

    private fun key(adActivity: String, trapKey: String) = "$adActivity|$trapKey"

    companion object {
        const val GRACE_MS = 30_000L
        private const val PERMANENT_AFTER = 2

        @Volatile
        private var instance: TrapMemory? = null

        fun get(context: Context): TrapMemory =
            instance ?: synchronized(this) {
                instance ?: TrapMemory(
                    context.applicationContext.getSharedPreferences("agas_traps", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
