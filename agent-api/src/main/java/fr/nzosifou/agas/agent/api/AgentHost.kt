package fr.nzosifou.agas.agent.api

import android.accessibilityservice.AccessibilityService

/** Ce que le Manager met à disposition de l'Agent. */
interface AgentHost {

    /**
     * Le service d'accessibilité du Manager : fenêtres à l'écran, clics, gestes, touche Retour,
     * captures, lancement d'applis. C'est aussi le Context de l'Agent (préférences, fichiers).
     */
    val service: AccessibilityService

    /** Mode test : observer et journaliser ce qui serait fait, sans agir. */
    val dryRun: Boolean

    /** Valeur actuelle d'un réglage déclaré dans [Agent.settings]. */
    fun setting(key: String): Boolean

    fun log(level: LogLevel, message: String)

    /** Pub en cours, affichée sur l'écran d'accueil : régie (« Unity »…) et étape. */
    fun setAdStatus(network: String, phase: AdPhase)

    /** Plus aucune pub en cours. */
    fun clearAdStatus()

    /** Message éphémère dans le Manager (« Pub Unity passée »…). */
    fun toast(message: String)

    fun recordAdSkipped()

    fun recordClick()

    fun recordHijackBlocked()
}

enum class LogLevel { DEBUG, INFO, ACTION, WARN }

enum class AdPhase { SEARCHING, CLICKING, RETURNING }
