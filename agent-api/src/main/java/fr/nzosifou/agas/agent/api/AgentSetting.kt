package fr.nzosifou.agas.agent.api

/**
 * Interrupteur déclaré par l'Agent. Le Manager l'affiche dans le groupe [group] (dans l'ordre de
 * déclaration) et mémorise sa valeur sous [key], d'une version de l'Agent à l'autre.
 */
class AgentSetting(
    val key: String,
    val group: String,
    val title: String,
    val summary: String,
    val default: Boolean,
)
