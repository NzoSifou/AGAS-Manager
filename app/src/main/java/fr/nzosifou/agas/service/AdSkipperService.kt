package fr.nzosifou.agas.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import fr.nzosifou.agas.MainActivity
import fr.nzosifou.agas.R
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.runtime.AgentRejected
import fr.nzosifou.agas.runtime.AgentRuntime
import fr.nzosifou.agas.runtime.AgentUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Service d'accessibilité d'AGAS Manager.
 *
 * Il ne sait pas passer une pub : il reste actif (service au premier plan), transmet tout à AGAS
 * Agent (voir [AgentRuntime]) et vérifie régulièrement si une nouvelle version de l'Agent est publiée.
 */
class AdSkipperService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        AgasLog.attach(this)
        _running.value = true
        AgasLog.i("Service démarré")
        startKeepAlive()
        AgentRuntime.attach(this)
        handler.postDelayed(updateCheck, FIRST_UPDATE_CHECK_DELAY_MS)
        if (isDebuggable) {
            ContextCompat.registerReceiver(
                this, debugReceiver,
                IntentFilter().apply {
                    addAction(ACTION_DEBUG_DUMP)
                    addAction(ACTION_DEBUG_LOAD_AGENT)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    /**
     * Passe le service au premier plan (notification permanente) : sans cela, HyperOS/MIUI gèle le
     * processus quelques secondes après qu'il passe en arrière-plan.
     */
    private fun startKeepAlive() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Service AGAS", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Notification permanente qui empêche le système de mettre AGAS en veille."
                setShowBadge(false)
            }
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_agas)
            .setContentTitle("AGAS est actif")
            .setContentText("Les pubs des jeux seront passées automatiquement.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        runCatching {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }.onSuccess {
            AgasLog.d("Service au premier plan : protégé contre la mise en veille")
        }.onFailure {
            AgasLog.w("Impossible de passer au premier plan (${it.javaClass.simpleName}) : le système risque de geler AGAS")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = AgentRuntime.onAccessibilityEvent(event)

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        if (!_running.value) return
        AgentRuntime.detach()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { unregisterReceiver(debugReceiver) }
        handler.removeCallbacksAndMessages(null)
        _running.value = false
    }

    /** Nouvelle version de l'Agent publiée ? Installée d'office si l'utilisateur l'a choisi. */
    private val updateCheck = object : Runnable {
        override fun run() {
            AgentUpdater.checkIfDue(this@AdSkipperService, install = AgasSettings.get(this@AdSkipperService).values.value.autoUpdateAgent)
            handler.postDelayed(this, UPDATE_CHECK_PERIOD_MS)
        }
    }

    /**
     * Version debug uniquement :
     * - `adb shell am broadcast -a fr.nzosifou.agas.DUMP -p fr.nzosifou.agas` enregistre l'écran
     *   actuel (sans passer par uiautomator, qui suspend les services d'accessibilité) ;
     * - `fr.nzosifou.agas.LOAD_AGENT` charge `Android/data/fr.nzosifou.agas/files/agent-dev.apk`
     *   (un Agent compilé en debug, signé avec la même clé que ce Manager).
     */
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DEBUG_DUMP -> AgentRuntime.dumpScreen()
                ACTION_DEBUG_LOAD_AGENT -> loadDevAgent()
            }
        }
    }

    private fun loadDevAgent() {
        val source = File(getExternalFilesDir(null), "agent-dev.apk")
        if (!source.isFile) return AgasLog.w("Agent de développement introuvable : ${source.path}")
        val copy = File(cacheDir, "agent-dev.apk").also { it.delete() }
        source.copyTo(copy)
        try {
            val pkg = AgentRuntime.install(copy)
            AgasLog.i("Agent de développement ${pkg.versionName} installé")
        } catch (e: AgentRejected) {
            AgasLog.w("Agent de développement refusé : ${e.message}")
        }
    }

    private val isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val CHANNEL_ID = "agas_service"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DEBUG_DUMP = "fr.nzosifou.agas.DUMP"
        private const val ACTION_DEBUG_LOAD_AGENT = "fr.nzosifou.agas.LOAD_AGENT"
        private const val FIRST_UPDATE_CHECK_DELAY_MS = 30_000L
        private const val UPDATE_CHECK_PERIOD_MS = 60 * 60_000L

        private val _running = MutableStateFlow(false)

        /** true tant que le service est lié par le système (activé dans l'accessibilité). */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
