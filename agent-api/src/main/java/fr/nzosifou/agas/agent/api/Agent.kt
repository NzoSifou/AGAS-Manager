package fr.nzosifou.agas.agent.api

import android.view.accessibility.AccessibilityEvent

/**
 * La logique de passage des pubs, chargée à la volée par AGAS Manager.
 *
 * Le Manager possède le service d'accessibilité et lui transmet tout : l'Agent lit l'écran et agit
 * à travers [AgentHost.service]. Tous les appels se font sur le thread principal.
 */
interface Agent {

    /** Réglages propres à cet Agent, affichés par le Manager dans l'onglet Réglages. */
    val settings: List<AgentSetting>

    /** Démarrage : service d'accessibilité connecté et AGAS activé. */
    fun start(host: AgentHost)

    fun onAccessibilityEvent(event: AccessibilityEvent)

    /**
     * Arrêt : service coupé, AGAS désactivé, ou remplacement par une autre version de l'Agent.
     * Doit annuler tout ce qui est programmé (Handler…) : l'instance ne sera plus utilisée.
     */
    fun stop()

    /** Enregistre l'écran actuel pour le diagnostic (structure et capture). */
    fun dumpScreen()
}
