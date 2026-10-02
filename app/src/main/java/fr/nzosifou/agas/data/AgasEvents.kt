package fr.nzosifou.agas.data

import fr.nzosifou.agas.agent.api.AdPhase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** État en direct du service, pour l'écran d'accueil (carte principale et messages éphémères). */
object AgasEvents {

    /** Pub en cours de traitement : régie (« Unity », « AppLovin »…) et étape. */
    data class AdStatus(val network: String, val phase: AdPhase)

    private val _adStatus = MutableStateFlow<AdStatus?>(null)
    val adStatus: StateFlow<AdStatus?> = _adStatus.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Messages éphémères (« Pub Unity passée », « Fausse croix évitée… »). */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    fun setAdStatus(status: AdStatus?) {
        _adStatus.value = status
    }

    fun toast(message: String) {
        _toasts.tryEmit(message)
    }
}
