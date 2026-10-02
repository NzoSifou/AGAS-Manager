package fr.nzosifou.agas.agent.api

/**
 * Version du contrat entre AGAS Manager et AGAS Agent.
 *
 * Règles de compatibilité :
 * - un Agent compilé contre la version N n'est chargé que par un Manager qui implémente N ou plus ;
 * - on ne modifie ni ne supprime jamais un élément existant de [Agent], [AgentHost] ou
 *   [AgentSetting] : on en ajoute (méthode avec implémentation par défaut côté [Agent]) et on
 *   augmente [VERSION].
 */
object AgentApi {
    const val VERSION = 1

    /** Identifiant (applicationId) de l'APK de l'Agent. */
    const val AGENT_PACKAGE = "fr.nzosifou.agas.agent"

    /** Meta-data du manifeste de l'Agent : version du contrat contre laquelle il a été compilé. */
    const val META_API_VERSION = "fr.nzosifou.agas.agent.API_VERSION"

    /** Meta-data du manifeste de l'Agent : classe qui implémente [Agent] (constructeur sans argument). */
    const val META_ENTRY_CLASS = "fr.nzosifou.agas.agent.ENTRY_CLASS"
}
