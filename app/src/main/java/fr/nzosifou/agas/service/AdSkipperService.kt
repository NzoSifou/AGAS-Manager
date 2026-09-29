package fr.nzosifou.agas.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import fr.nzosifou.agas.MainActivity
import fr.nzosifou.agas.R
import fr.nzosifou.agas.data.AgasEvents
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.data.TrapMemory
import fr.nzosifou.agas.detection.Candidate
import fr.nzosifou.agas.detection.CloseButtonFinder
import fr.nzosifou.agas.detection.ScanResult
import fr.nzosifou.agas.detection.ScanWindow
import fr.nzosifou.agas.detection.TreeDumper
import fr.nzosifou.agas.guard.HijackGuard
import fr.nzosifou.agas.rules.AdRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service d'accessibilité d'AGAS.
 *
 * Boucle : pub détectée (Activity d'une régie au premier plan) → analyse de l'écran toutes les
 * [TICK_MS] ms → clic sur le bouton de fermeture dès qu'il apparaît → vérification (détournement
 * vers une autre appli ?) → on recommence tant que la fenêtre de la pub est affichée (pubs en
 * plusieurs étapes).
 *
 * La fin d'une pub est décidée d'après les fenêtres réellement à l'écran, pas d'après les
 * événements : certains jeux (Unity) envoient un événement « jeu au premier plan » au moment même
 * où la pub s'ouvre.
 */
class AdSkipperService : AccessibilityService() {

    /** Suivi d'une pub, de son apparition jusqu'au retour dans le jeu. */
    private class AdSession(
        val gamePackage: String,
        var adActivity: String,
        val startedAt: Long,
        /** Régie affichée dans l'appli (« Unity », « AppLovin »…). */
        val network: String,
    ) {
        /** Fenêtres d'accessibilité des Activity de pub (une pub peut en enchaîner plusieurs). */
        val adWindowIds = HashSet<Int>()
        /** Si l'événement n'a pas d'identifiant de fenêtre, on prend la plus haute jusqu'à cet instant. */
        var learnWindowsUntil = 0L
        var clicks = 0
        var lastClickAt = 0L
        /** Bouton du dernier clic (voir Candidate.trapKey), à mémoriser s'il s'avère être un piège. */
        var lastClickedTrapKey: String? = null
        /** Un de nos clics a ouvert une autre appli : la pub a pu être écourtée. */
        var hijacked = false
        val attempts = HashMap<String, Int>()
        /** Boutons mis en pause après plusieurs essais sans effet : clé → instant de reprise. */
        val cooldowns = HashMap<String, Long>()
        /** Icônes sans libellé essayées sans effet : clé → nombre d'échecs. */
        val weakFailures = HashMap<String, Int>()
        val blacklist = ArrayList<Point>()
        var nextActionAt = 0L
        var goneSince = 0L
        var countdownLogged = false
        var rewardWarnings = 0
        var dumpCount = 0
        var lastDumpNodeCount = -1
        var backTried = false
        var lastStatusLogAt = 0L
        val dryRunLogged = HashSet<String>()
        /** Dernier instant où la fenêtre de la pub était à l'écran. */
        var lastAdVisibleAt = 0L
        var redirectsHandled = 0
        var lastRedirectAt = 0L
        /** Appuis de réveil faits sur ce mini-jeu (voir [wakePlayable]). */
        var wakeTaps = 0
        var lastWakeTapAt = 0L
        /** Analyse rapprochée jusqu'à cet instant : le bouton révélé ne reste que quelques secondes. */
        var fastScanUntil = 0L
        /** Utilisations du bouton de sortie « boutique » (voir [useStoreExit]). */
        var storeExits = 0
        var lastStoreExitAt = 0L
        /** Jusqu'à cet instant, l'ouverture de la boutique est attendue : ce n'est pas un piège. */
        var expectStoreUntil = 0L
        /** Depuis quand une autre appli (Play Store…) est devant la pub, 0 si aucune. */
        var foreignSince = 0L
        var storeStuckCloses = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { runTick() }
    private var tickAt = NO_TICK
    private val activityCache = HashMap<String, Boolean>()

    private lateinit var rules: AdRules
    private lateinit var settings: AgasSettings
    private lateinit var finder: CloseButtonFinder
    private lateinit var guard: HijackGuard
    private lateinit var traps: TrapMemory

    private var foregroundPackage: String? = null
    private var foregroundActivity: String? = null
    private var session: AdSession? = null

    /** Dernier clic de l'utilisateur détecté (hors échos de nos propres clics). */
    private var lastUserClickAt = 0L

    /** Un retour au jeu est en cours (voir [ensureBackInGame]). */
    private var recovering = false

    override fun onServiceConnected() {
        AgasLog.attach(this)
        rules = AdRules.get(this)
        settings = AgasSettings.get(this)
        finder = CloseButtonFinder(rules)
        guard = HijackGuard(packageName, ::now)
        traps = TrapMemory.get(this)
        // Le cache d'accessibilité n'est pas mis à jour de façon fiable pour le contenu des WebView
        // (pubs HTML) : AGAS relisait l'écran tel qu'il était des dizaines de secondes plus tôt,
        // sans la croix apparue entre-temps. Sans cache, chaque lecture reflète l'écran réel.
        // Avant Android 14, le parcours de l'arbre rafraîchit chaque nœud à la place.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setCacheEnabled(false)
        refreshSystemPackages()
        _running.value = true
        AgasLog.i("Service démarré (règles v${rules.version})")
        startKeepAlive()
        if (isDebuggable) {
            ContextCompat.registerReceiver(
                this, debugDumpReceiver, IntentFilter(ACTION_DEBUG_DUMP), ContextCompat.RECEIVER_EXPORTED,
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::rules.isInitialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                onWindowStateChanged(pkg, event.className?.toString().orEmpty(), event.windowId)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val pkg = event.packageName?.toString().orEmpty()
                // Un clic qui n'est pas l'écho du nôtre vient de l'utilisateur (notification, jeu…).
                if (now() - (session?.lastClickAt ?: 0L) > HijackGuard.OWN_CLICK_ECHO_MS && pkg != packageName) {
                    lastUserClickAt = now()
                }
                if (guard.onViewClicked(pkg)) {
                    AgasLog.d("Tu as touché la pub toi-même : protection anti-détournement suspendue")
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                // L'analyse suivante est avancée si besoin, jamais repoussée (voir scheduleTick).
                if (session != null) scheduleTick(WINDOWS_CHANGED_DELAY_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private val clearAdStatus = Runnable { AgasEvents.setAdStatus(null) }

    /** Étape affichée sur l'écran d'accueil pendant la pub en cours. */
    private fun showPhase(s: AdSession, phase: AgasEvents.Phase) {
        if (AgasEvents.adStatus.value?.phase != phase) AgasEvents.setAdStatus(AgasEvents.AdStatus(s.network, phase))
    }

    private fun stop() {
        AgasEvents.setAdStatus(null)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { unregisterReceiver(debugDumpReceiver) }
        handler.removeCallbacksAndMessages(null)
        tickAt = NO_TICK
        session = null
        _running.value = false
    }

    // ---------------------------------------------------------------------------------------------
    // Suivi du premier plan
    // ---------------------------------------------------------------------------------------------

    private fun onWindowStateChanged(pkg: String, cls: String, windowId: Int) {
        // Surveillance après un clic : on vérifie tout de suite (elle continue ensuite d'elle-même).
        if (guard.active() != null) {
            handler.removeCallbacks(hijackWatch)
            handler.post(hijackWatch)
        }

        // Les popups, menus, toasts… déclenchent aussi cet événement : on ne garde que les Activity.
        if (!isActivity(pkg, cls)) return
        foregroundPackage = pkg
        foregroundActivity = cls

        val current = session
        if (rules.isAdActivity(cls)) {
            if (!settings.values.value.enabled) return
            val s = if (current == null || current.gamePackage != pkg) {
                if (current != null) endSession()
                AgasLog.i("Pub détectée dans ${appLabel(pkg)} (${cls.substringAfterLast('.')})")
                AdSession(pkg, cls, now(), rules.networkName(cls)).also {
                    session = it
                    handler.removeCallbacks(clearAdStatus)
                    AgasEvents.setAdStatus(AgasEvents.AdStatus(it.network, AgasEvents.Phase.SEARCHING))
                }
            } else {
                current.also { it.adActivity = cls }
            }
            if (windowId >= 0) s.adWindowIds += windowId
            s.learnWindowsUntil = now() + LEARN_WINDOWS_MS
            AgasLog.d("Fenêtre de la pub #$windowId ; ${describeWindows()}")
            scheduleTick(FIRST_TICK_DELAY_MS)
        } else if (current != null && pkg == current.gamePackage) {
            // Retour au jeu probable : la boucle vérifiera que la fenêtre de la pub a bien disparu.
            scheduleTick(WINDOWS_CHANGED_DELAY_MS)
        }
    }

    private fun isActivity(pkg: String, cls: String): Boolean {
        if (cls.isEmpty()) return false
        return activityCache.getOrPut("$pkg/$cls") {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun refreshSystemPackages() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        guard.launcherPackages = packageManager
            .queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
        val keyboards = getSystemService(InputMethodManager::class.java)
            ?.inputMethodList?.map { it.packageName }.orEmpty()
        guard.ignoredPackages = rules.hijackIgnoredPackages + keyboards
    }

    // ---------------------------------------------------------------------------------------------
    // Boucle d'analyse
    // ---------------------------------------------------------------------------------------------

    /**
     * Programme une analyse dans [delayMs], sauf si une analyse est déjà prévue plus tôt : les
     * événements fréquents (vidéo, animations) ne doivent jamais repousser indéfiniment l'analyse.
     */
    private fun scheduleTick(delayMs: Long) {
        val at = now() + delayMs
        if (tickAt != NO_TICK && tickAt <= at) return
        handler.removeCallbacks(tick)
        tickAt = at
        handler.postAtTime(tick, at)
    }

    private fun runTick() {
        tickAt = NO_TICK
        val s = session ?: return
        val values = settings.values.value
        if (!values.enabled) {
            AgasLog.i("AGAS désactivé : suivi de la pub arrêté")
            session = null
            AgasEvents.setAdStatus(null)
            return
        }
        val now = now()
        // On n'abandonne qu'une pub quittée depuis longtemps (l'utilisateur est ailleurs), jamais une
        // pub encore affichée : certains mini-jeux attendent indéfiniment qu'on les touche.
        if (now - maxOf(s.startedAt, s.lastAdVisibleAt) > SESSION_TIMEOUT_MS) {
            AgasLog.w("Pub hors écran depuis plus de 5 min : abandon")
            session = null
            AgasEvents.setAdStatus(null)
            return
        }

        val gameWindows = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .filter { it.root?.packageName?.toString() == s.gamePackage }
            .sortedByDescending { it.layer }
        val gameInFront = foregroundPackage == s.gamePackage
        // Événement sans identifiant de fenêtre : juste après l'ouverture de la pub, la fenêtre du jeu
        // la plus haute est la sienne. On ne s'y fie pas plus tard : au retour dans le jeu, Unity
        // n'envoie pas toujours d'événement, et on prendrait la fenêtre du jeu pour celle de la pub.
        if (s.adWindowIds.isEmpty() && now < s.learnWindowsUntil) {
            gameWindows.firstOrNull()?.let { s.adWindowIds += it.id }
        }
        val adVisible = gameWindows.any { it.id in s.adWindowIds }
        val onLandingPage = gameInFront && foregroundActivity?.let(rules::isLandingPage) == true

        if (onLandingPage) {
            // Page de la pub ouverte dans le jeu (par l'utilisateur ou un détournement) : pause.
            s.goneSince = 0L
            logStatus(s, "page de la pub ouverte (${foregroundActivity?.substringAfterLast('.')}) : pause")
            return scheduleTick(PAUSED_TICK_MS)
        }
        if (!adVisible) {
            if (gameWindows.isNotEmpty()) {
                // Le jeu est à l'écran sans la pub : elle est terminée (après une courte confirmation).
                if (s.goneSince == 0L) s.goneSince = now
                if (now - s.goneSince >= AD_GONE_CONFIRM_MS) return endSession()
                return scheduleTick(TICK_MS)
            }
            // Une autre appli est devant : redirection automatique de la pub, ou l'utilisateur est
            // ailleurs (accueil, autre appli), auquel cas on patiente sans rien toucher.
            s.goneSince = 0L
            if (handleForeignApp(s, now) || handleAutoRedirect(s, now)) return scheduleTick(PAUSED_TICK_MS)
            logStatus(s,"pub hors écran : pause (${describeWindows()}, 1er plan ${foregroundPackage}/${foregroundActivity?.substringAfterLast('.')})")
            return scheduleTick(PAUSED_TICK_MS)
        }
        s.goneSince = 0L
        // Popup du Play Store posée sur une partie de la pub (fenêtre de la pub encore visible).
        if (handleForeignApp(s, now)) return scheduleTick(PAUSED_TICK_MS)
        s.lastAdVisibleAt = now
        if (now < s.nextActionAt) return scheduleTick(s.nextActionAt - now)

        val scanWindows = gameWindows.mapNotNull { w ->
            // refresh() : on veut l'état actuel de l'écran, pas une copie en cache.
            w.root?.also { it.refresh() }?.let { ScanWindow(it, Rect().also(w::getBoundsInScreen)) }
        }
        val ignored = s.cooldowns.filterValues { it > now }.keys
        // Après un appui, « Bouton trouvé, appui… » reste affiché un instant, puis retour à la recherche.
        if (now - s.lastClickAt > CLICK_PHASE_MS) showPhase(s, AgasEvents.Phase.SEARCHING)
        val adAge = now - s.startedAt
        val isTrap = { key: String ->
            traps.isTrap(s.adActivity, key, adAge) ||
                (adAge < TrapMemory.GRACE_MS && rules.isKnownEarlyTrap(s.adActivity, key))
        }
        when (val result = finder.scan(scanWindows, s.blacklist, ignored, values.unlabeledButtons, isTrap)) {
            is ScanResult.RewardWarning -> onRewardWarning(s, result, values.dryRun)
            is ScanResult.Countdown -> if (!s.countdownLogged) {
                s.countdownLogged = true
                AgasLog.d("Compte à rebours affiché (« ${result.text} ») : on attend")
            }
            is ScanResult.Found -> onCandidate(s, result, values)
            is ScanResult.Nothing -> {
                onNothingFound(s, values, "${result.nodeCount} éléments lus, aucun bouton de fermeture")
                val usedStoreExit = result.storeExit?.let { useStoreExit(s, values, it) } == true
                if (!usedStoreExit && result.playable) wakePlayable(s, values, scanWindows)
                // Un enregistrement à chaque nouvel état de la pub (vidéo, mini-jeu, écran de fin…).
                if (values.saveDumps && s.dumpCount < MAX_DUMPS_PER_AD && now - s.startedAt > DUMP_AFTER_MS &&
                    result.nodeCount != s.lastDumpNodeCount
                ) {
                    s.dumpCount++
                    s.lastDumpNodeCount = result.nodeCount
                    dumpNow(s.gamePackage, s.adActivity, scanWindows)
                }
            }
        }
        if (session === s) scheduleTick(if (now() < s.fastScanUntil) FAST_TICK_MS else TICK_MS)
    }

    /**
     * Mini-jeu sans bouton : un seul appui n'importe où fait apparaître « Next » pendant quelques
     * secondes, mais un second appui ouvre la boutique (ou une popup qui y mène). On appuie donc UNE
     * fois, sur un point neutre, puis on analyse l'écran en rafale pour cliquer le bouton dès qu'il
     * apparaît. Le prochain appui n'est permis que bien plus tard.
     */
    private fun wakePlayable(s: AdSession, values: AgasSettings.Values, scanWindows: List<ScanWindow>) {
        if (!values.wakePlayables || values.dryRun) return
        val now = now()
        if (now - s.startedAt < WAKE_TAP_AFTER_MS || s.wakeTaps >= MAX_WAKE_TAPS) return
        if (now - s.lastWakeTapAt < WAKE_TAP_INTERVAL_MS || now - s.lastClickAt < WAKE_TAP_INTERVAL_MS) return
        val point = finder.neutralPoint(scanWindows, s.blacklist) ?: return
        s.wakeTaps++
        s.lastWakeTapAt = now
        // Compte comme un clic d'AGAS : écho ignoré, et protection anti-détournement armée. Ce n'est
        // pas un bouton : rien à mémoriser comme piège, seul ce point sera évité s'il ouvre la boutique.
        s.lastClickAt = now
        s.lastClickedTrapKey = null
        s.fastScanUntil = now + FAST_SCAN_WINDOW_MS
        s.nextActionAt = now + WAKE_TAP_SETTLE_MS
        tap(point)
        AgasLog.action("Mini-jeu sans bouton : un appui en (${point.x}, ${point.y}) pour faire apparaître « Next »")
        if (values.hijackGuard) {
            guard.arm(s.gamePackage, s.adActivity, point)
            handler.removeCallbacks(hijackWatch)
            handler.postDelayed(hijackWatch, HIJACK_WATCH_INTERVAL_MS)
        }
    }

    /**
     * Mini-jeu dont la seule sortie est un bouton « boutique » (« Google Play » à la place de
     * « Next » chez AppLovin, « Ouvrir la boutique »…). En dernier recours seulement ; la popup du
     * Play Store qui s'ouvre ensuite est attendue et refermée (voir [onHijack]). Retourne true si
     * on a cliqué.
     */
    private fun useStoreExit(s: AdSession, values: AgasSettings.Values, c: Candidate): Boolean {
        if (!values.storeExitButtons || values.dryRun) return false
        val now = now()
        if (now - s.startedAt < STORE_EXIT_AFTER_MS || s.storeExits >= MAX_STORE_EXITS) return false
        if (now - s.lastStoreExitAt < STORE_EXIT_INTERVAL_MS || now - s.lastClickAt < STORE_EXIT_MIN_GAP_MS) return false
        s.storeExits++
        s.lastStoreExitAt = now
        s.expectStoreUntil = now + EXPECT_STORE_MS
        // Un bouton de page web ignore souvent le clic d'accessibilité : appui simulé s'il est sûr.
        val how = click(c, preferAccessibilityClick = !(c.inWebView && c.tapAllowed))
        s.clicks++
        s.lastClickAt = now
        s.lastClickedTrapKey = null
        s.nextActionAt = now + AFTER_CLICK_DELAY_MS
        settings.recordClick()
        showPhase(s, AgasEvents.Phase.CLICKING)
        AgasLog.action("$how sur le ${c.reason} : seule sortie du mini-jeu, la boutique sera refermée")
        if (values.hijackGuard) {
            guard.arm(s.gamePackage, s.adActivity, c.center)
            handler.removeCallbacks(hijackWatch)
            handler.postDelayed(hijackWatch, HIJACK_WATCH_INTERVAL_MS)
        }
        return true
    }

    private fun onCandidate(s: AdSession, found: ScanResult.Found, values: AgasSettings.Values) {
        val c = found.candidate
        // Une icône sans libellé est moins sûre : on laisse d'abord le temps à la vraie croix d'apparaître.
        if (!c.strong && now() - s.startedAt < UNLABELED_DELAY_MS) {
            return logStatus(s, "Icône sans libellé repérée (${c.reason}), on attend ${UNLABELED_DELAY_MS / 1000} s")
        }
        if (values.dryRun) {
            if (s.dryRunLogged.add(c.key)) AgasLog.action("[test] Cliquerais sur ${c.reason} (score ${c.score})")
            return
        }

        val attempt = (s.attempts[c.key] ?: 0) + 1
        // Une icône sans libellé est une supposition : un seul essai. Si rien ne se passe, c'était
        // sans doute le son ou les infos, inutile d'insister.
        if (attempt > (if (c.strong) MAX_ATTEMPTS else 1)) {
            s.attempts.remove(c.key)
            if (c.strong) {
                // Pas d'abandon définitif : le bouton était peut-être simplement pas encore actif.
                s.cooldowns[c.key] = now() + COOLDOWN_MS
                AgasLog.w("${c.reason} : aucun effet après $MAX_ATTEMPTS essais, nouvel essai dans ${COOLDOWN_MS / 1000} s")
            } else {
                // Icône sans libellé sans effet : pas encore active, ou pas une croix (son, infos…).
                // On la retente plus tard, puis on l'abandonne.
                val failures = (s.weakFailures[c.key] ?: 0) + 1
                s.weakFailures[c.key] = failures
                if (failures >= MAX_WEAK_FAILURES) {
                    s.cooldowns[c.key] = Long.MAX_VALUE
                    AgasLog.d("${c.reason} : aucun effet après $failures essais, abandonnée pour cette pub")
                } else {
                    s.cooldowns[c.key] = now() + WEAK_COOLDOWN_MS
                    AgasLog.d("${c.reason} : aucun effet, nouvel essai dans ${WEAK_COOLDOWN_MS / 1000} s")
                }
            }
            return
        }
        s.attempts[c.key] = attempt

        // Premier essai : clic d'accessibilité, sauf dans une page web où il est souvent ignoré
        // (le bouton « Next » d'un mini-jeu ne reste que quelques secondes : pas de temps à perdre).
        val how = click(c, preferAccessibilityClick = attempt == 1 && !(c.inWebView && c.tapAllowed))
        s.clicks++
        s.lastClickAt = now()
        s.lastClickedTrapKey = c.trapKey
        s.nextActionAt = now() + AFTER_CLICK_DELAY_MS
        settings.recordClick()
        showPhase(s, AgasEvents.Phase.CLICKING)
        AgasLog.action("$how sur ${c.reason} (essai $attempt, score ${c.score})")
        if (values.hijackGuard) {
            guard.arm(s.gamePackage, s.adActivity, c.center)
            handler.removeCallbacks(hijackWatch)
            handler.postDelayed(hijackWatch, HIJACK_WATCH_INTERVAL_MS)
        }
    }

    private fun onRewardWarning(s: AdSession, r: ScanResult.RewardWarning, dryRun: Boolean) {
        val resume = r.resume
        if (resume == null) {
            if (s.rewardWarnings++ == 0) AgasLog.w("Avertissement « récompense perdue » affiché, bouton Reprendre introuvable")
            return
        }
        if (dryRun) {
            if (s.dryRunLogged.add(resume.key)) AgasLog.action("[test] Cliquerais sur ${resume.reason} pour garder la récompense")
            return
        }
        s.rewardWarnings++
        click(resume, preferAccessibilityClick = true)
        // On laisse la vidéo se terminer avant de retenter une croix, de plus en plus longtemps.
        s.nextActionAt = now() + REWARD_BACKOFF_MS * s.rewardWarnings
        AgasLog.action("Récompense protégée : clic sur ${resume.reason}")
    }

    private fun onNothingFound(s: AdSession, values: AgasSettings.Values, status: String) {
        logStatus(s, status)
        if (values.backFallback && !values.dryRun && !s.backTried && now() - s.startedAt > BACK_AFTER_MS) {
            s.backTried = true
            s.nextActionAt = now() + AFTER_CLICK_DELAY_MS
            performGlobalAction(GLOBAL_ACTION_BACK)
            AgasLog.action("Aucun bouton trouvé : essai de la touche Retour")
        }
    }

    /** Enregistre la structure de l'écran et une capture d'écran (voir [TreeDumper]). */
    private fun dumpNow(gamePackage: String, activity: String, scanWindows: List<ScanWindow>) {
        val baseName = TreeDumper.newBaseName(gamePackage)
        val file = TreeDumper.dump(this, baseName, gamePackage, activity, scanWindows) ?: return
        AgasLog.w("Aucun bouton trouvé. Structure enregistrée : dumps/${file.name}")
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                result.hardwareBuffer.use { buffer ->
                    Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                        ?.let { TreeDumper.saveScreenshot(this@AdSkipperService, baseName, it) }
                }
            }

            override fun onFailure(errorCode: Int) = AgasLog.d("Capture d'écran impossible (code $errorCode)")
        })
    }

    /**
     * Version debug uniquement : `adb shell am broadcast -a fr.nzosifou.agas.DUMP -p fr.nzosifou.agas`
     * enregistre l'écran actuel, quel qu'il soit (sans passer par uiautomator, qui suspend les
     * services d'accessibilité).
     */
    private val debugDumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.layer }
            val scanWindows = appWindows.mapNotNull { w ->
                w.root?.also { it.refresh() }?.let { ScanWindow(it, Rect().also(w::getBoundsInScreen)) }
            }
            val pkg = scanWindows.firstOrNull()?.root?.packageName?.toString() ?: "inconnu"
            AgasLog.d("Dump manuel ; ${describeWindows()}")
            dumpNow(pkg, foregroundActivity ?: "?", scanWindows)
        }
    }

    private val isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Résumé des fenêtres à l'écran, pour le diagnostic : « #id type paquet ». */
    private fun describeWindows(): String = windows.joinToString(prefix = "fenêtres : ") { w ->
        val pkg = w.root?.packageName?.toString()?.substringAfterLast('.') ?: "?"
        "#${w.id} t${w.type} $pkg « ${w.title} »"
    }

    /** Message de diagnostic, au plus une fois toutes les [STATUS_LOG_INTERVAL_MS]. */
    private fun logStatus(s: AdSession, message: String) {
        val now = now()
        if (now - s.lastStatusLogAt < STATUS_LOG_INTERVAL_MS) return
        s.lastStatusLogAt = now
        AgasLog.d("${(now - s.startedAt) / 1000} s : $message")
    }

    private fun endSession() {
        val s = session ?: return
        session = null
        handler.removeCallbacks(tick)
        tickAt = NO_TICK
        val now = now()
        val seconds = (now - s.startedAt) / 1000
        // On ne s'attribue la fermeture que si la pub a disparu peu après notre dernier clic.
        val closedByUs = s.lastClickAt > 0 && now - s.lastClickAt < CLOSED_BY_US_WINDOW_MS
        when {
            // Une fausse croix a ouvert une autre appli : la pub a pu être écourtée (récompense perdue ?).
            s.hijacked -> AgasLog.w("Pub interrompue après $seconds s : une fausse croix a ouvert une autre appli")
            closedByUs -> {
                settings.recordAdSkipped()
                AgasLog.i("Pub fermée par AGAS après $seconds s (${s.clicks} clic(s))")
                AgasEvents.toast("Pub ${s.network} passée")
            }
            s.clicks > 0 -> AgasLog.w("Pub fermée sans AGAS après $seconds s (${s.clicks} clic(s) sans effet)")
            else -> AgasLog.i("Pub terminée après $seconds s sans action d'AGAS")
        }
        // « Retour au jeu. » s'affiche un instant sur l'écran d'accueil.
        AgasEvents.setAdStatus(AgasEvents.AdStatus(s.network, AgasEvents.Phase.RETURNING))
        handler.removeCallbacks(clearAdStatus)
        handler.postDelayed(clearAdStatus, RETURNING_PHASE_MS)
    }

    // ---------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------

    /**
     * Clic d'accessibilité (ACTION_CLICK) au premier essai, appui simulé ensuite : certaines régies
     * n'écoutent que les vrais évènements tactiles.
     */
    private fun click(c: Candidate, preferAccessibilityClick: Boolean): String {
        if ((preferAccessibilityClick || !c.tapAllowed) &&
            c.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ) {
            return "Clic"
        }
        if (!c.tapAllowed) return "Clic refusé (appui simulé risqué)"
        tap(c.center)
        return "Appui simulé"
    }

    private fun tap(p: Point) {
        if (p.x < 0 || p.y < 0) return
        val path = Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }
        runCatching {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                .build()
            dispatchGesture(gesture, null, null)
        }.onFailure { AgasLog.w("Appui impossible en (${p.x}, ${p.y}) : ${it.message}") }
    }

    /** Surveillance après un clic : quelle appli arrive au premier plan ? */
    private val hijackWatch = object : Runnable {
        override fun run() {
            val armed = guard.active() ?: return
            val top = topAppPackage()
            val onLandingPage = top == armed.gamePackage && foregroundPackage == top &&
                foregroundActivity?.let(rules::isLandingPage) == true
            val hijack = guard.check(top, onLandingPage)
            if (hijack != null) onHijack(hijack, top.orEmpty()) else handler.postDelayed(this, HIJACK_WATCH_INTERVAL_MS)
        }
    }

    /** Détournement confirmé : on note la position fautive et on revient au jeu (voir [ensureBackInGame]). */
    private fun onHijack(armed: HijackGuard.Armed, hijackerPkg: String) {
        if (recovering) return // déjà pris en charge (voir handleForeignApp)
        val what = if (hijackerPkg == armed.gamePackage) "une page de la pub" else appLabel(hijackerPkg)
        val s = session?.takeIf { it.gamePackage == armed.gamePackage }
        if (s != null && now() < s.expectStoreUntil) {
            // Ouverture voulue (bouton de sortie « boutique ») : ni piège, ni détournement.
            s.expectStoreUntil = 0L
            AgasLog.i("$what ouvert comme prévu par le bouton de sortie : fermeture, retour à la pub")
            ensureBackInGame(armed.gamePackage, attempt = 1)
            return
        }
        AgasLog.w("Le clic a ouvert $what : fausse croix, retour au jeu")
        settings.recordHijackBlocked()
        if (s != null) {
            s.blacklist += armed.clickPoint
            s.hijacked = true
            s.lastClickedTrapKey?.let { key ->
                traps.record(armed.adActivity, key)
                AgasLog.w("Piège mémorisé pour ${armed.adActivity.substringAfterLast('.')} : « $key »")
                AgasEvents.toast("Fausse croix évitée — piège mémorisé pour ${s.network}")
            }
        }
        ensureBackInGame(armed.gamePackage, attempt = 1)
    }

    /**
     * Paquet de l'appli réellement au premier plan, d'après les fenêtres à l'écran. Plus fiable que
     * le dernier événement : certains jeux (Unity) n'en envoient pas quand ils reviennent devant.
     * On prend la fenêtre active (celle qui reçoit les touches) plutôt que la plus haute : le Play
     * Store pose parfois une fenêtre invisible au-dessus de tout.
     */
    private fun topAppPackage(): String? {
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val window = appWindows.firstOrNull { it.isActive } ?: appWindows.maxByOrNull { it.layer }
        return window?.root?.packageName?.toString() ?: foregroundPackage
    }

    /**
     * Suite d'un détournement confirmé : tant que le jeu n'est pas revenu, on insiste. Les pubs
     * enchaînent souvent les redirections (page intermédiaire dans le jeu → AliExpress / Play Store) :
     * toute appli qui apparaît pendant ce rattrapage en fait partie. Seul l'écran d'accueil (action
     * volontaire de l'utilisateur) l'interrompt.
     */
    private fun ensureBackInGame(game: String, attempt: Int) {
        recovering = true
        val pkg = topAppPackage()
        // L'Activity n'est connue que par les événements : on ne s'en sert que si elle est cohérente.
        val cls = foregroundActivity.takeIf { foregroundPackage == pkg }
        when {
            pkg == game && (cls == null || !rules.isLandingPage(cls)) -> {
                AgasLog.i("Retour réussi")
                recovering = false
                return
            }
            pkg != null && pkg in guard.launcherPackages -> {
                recovering = false
                return
            }
            attempt > MAX_RECOVERY_ATTEMPTS -> {
                AgasLog.w("Impossible de revenir au jeu après $MAX_RECOVERY_ATTEMPTS tentatives")
                recovering = false
                return
            }
            // Page de la pub dans le jeu, ou panneau d'installation du Play Store posé par-dessus
            // le jeu : « Retour » le ferme.
            pkg == game || (pkg == PLAY_STORE && attempt == 1) -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                AgasLog.action("${if (pkg == game) "Page de la pub" else "Panneau du Play Store"} : Retour")
            }
            // Autre appli (ouverte dans sa propre tâche, où « Retour » ne ramène pas au jeu) :
            // on relance le jeu, ce qui ramène sa tâche existante sans la redémarrer.
            else -> relaunchGame(game, pkg)
        }
        handler.postDelayed({ ensureBackInGame(game, attempt + 1) }, HIJACK_VERIFY_DELAY_MS)
    }

    /**
     * Une autre appli est devant la pub. Deux cas où on la referme sans dépendre de la surveillance
     * après clic (qui peut avoir été désactivée par un écho tardif de notre propre clic) :
     * - elle était attendue : AGAS vient d'utiliser le bouton de sortie « boutique » ;
     * - c'est le Play Store, resté plus de [STORE_STUCK_MS] sans que l'utilisateur ne touche rien.
     * Retourne true si on a réagi.
     */
    private fun handleForeignApp(s: AdSession, now: Long): Boolean {
        if (recovering) return false
        val top = topAppPackage()
        if (top == null || top == s.gamePackage || top == packageName ||
            top in guard.launcherPackages || top in guard.ignoredPackages
        ) {
            s.foreignSince = 0L
            return false
        }
        if (s.foreignSince == 0L) s.foreignSince = now
        if (!settings.values.value.hijackGuard || settings.values.value.dryRun) return false
        val reason = when {
            now < s.expectStoreUntil -> {
                s.expectStoreUntil = 0L
                "${appLabel(top)} ouvert comme prévu par le bouton de sortie : fermeture, retour à la pub"
            }
            top == PLAY_STORE && s.storeStuckCloses < MAX_STORE_STUCK_CLOSES &&
                now - s.foreignSince > STORE_STUCK_MS && now - lastUserClickAt > STORE_STUCK_MS -> {
                s.storeStuckCloses++
                "Le Play Store reste ouvert par-dessus la pub : fermeture"
            }
            else -> return false
        }
        guard.disarm()
        AgasLog.i(reason)
        ensureBackInGame(s.gamePackage, attempt = 1)
        return true
    }

    /**
     * La pub a ouvert une autre appli toute seule (redirection automatique vers le Play Store en fin
     * de vidéo, par exemple), sans clic d'AGAS. On la referme si tout indique que ce n'est pas
     * l'utilisateur : la pub était encore à l'écran il y a un instant, aucun toucher n'a été
     * détecté, et on n'est pas passé par l'écran d'accueil. Retourne true si on a réagi.
     */
    private fun handleAutoRedirect(s: AdSession, now: Long): Boolean {
        if (recovering || guard.active() != null) return false
        if (!settings.values.value.hijackGuard || settings.values.value.dryRun) return false
        if (s.redirectsHandled >= MAX_AUTO_REDIRECTS || now - s.lastRedirectAt < AUTO_REDIRECT_COOLDOWN_MS) return false
        if (now - s.lastAdVisibleAt > AUTO_REDIRECT_WINDOW_MS) return false
        if (now - lastUserClickAt < USER_CLICK_QUIET_MS) return false
        val top = topAppPackage() ?: return false
        if (top == s.gamePackage || top == packageName || top in guard.launcherPackages || top in guard.ignoredPackages) {
            return false
        }
        s.redirectsHandled++
        s.lastRedirectAt = now
        AgasLog.w("La pub a ouvert ${appLabel(top)} toute seule : retour à la pub")
        settings.recordHijackBlocked()
        ensureBackInGame(s.gamePackage, attempt = 1)
        return true
    }

    private fun relaunchGame(game: String, from: String?) {
        val launch = packageManager.getLaunchIntentForPackage(game)
        if (launch == null) {
            AgasLog.w("Impossible de relancer ${appLabel(game)}")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launch) }
            .onSuccess { AgasLog.action("${from?.let(::appLabel) ?: "Autre appli"} au premier plan : jeu relancé") }
            .onFailure { AgasLog.w("Échec du retour au jeu : ${it.message}") }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun now() = SystemClock.uptimeMillis()

    companion object {
        private const val CHANNEL_ID = "agas_service"
        private const val NOTIFICATION_ID = 1
        private const val NO_TICK = -1L
        private const val FIRST_TICK_DELAY_MS = 300L
        private const val TICK_MS = 400L
        private const val CLICK_PHASE_MS = 1_500L
        private const val RETURNING_PHASE_MS = 1_500L
        private const val FAST_TICK_MS = 150L
        private const val WAKE_TAP_AFTER_MS = 6_000L
        private const val WAKE_TAP_INTERVAL_MS = 15_000L
        private const val WAKE_TAP_SETTLE_MS = 300L
        private const val MAX_WAKE_TAPS = 4
        private const val FAST_SCAN_WINDOW_MS = 5_000L
        private const val STORE_EXIT_AFTER_MS = 10_000L
        private const val WEAK_COOLDOWN_MS = 10_000L
        private const val MAX_WEAK_FAILURES = 3
        /** Popup du Play Store restée par-dessus la pub sans action de l'utilisateur : on la ferme. */
        private const val STORE_STUCK_MS = 6_000L
        private const val MAX_STORE_STUCK_CLOSES = 3
        private const val STORE_EXIT_INTERVAL_MS = 10_000L
        private const val STORE_EXIT_MIN_GAP_MS = 1_500L
        private const val MAX_STORE_EXITS = 3
        private const val EXPECT_STORE_MS = 8_000L
        private const val PAUSED_TICK_MS = 1000L
        private const val WINDOWS_CHANGED_DELAY_MS = 150L
        private const val AD_GONE_CONFIRM_MS = 1000L
        private const val LEARN_WINDOWS_MS = 2000L
        private const val AFTER_CLICK_DELAY_MS = 1200L
        private const val CLOSED_BY_US_WINDOW_MS = 6_000L
        private const val UNLABELED_DELAY_MS = 8_000L
        private const val COOLDOWN_MS = 5_000L
        private const val REWARD_BACKOFF_MS = 8_000L
        private const val DUMP_AFTER_MS = 5_000L
        private const val MAX_DUMPS_PER_AD = 4
        private const val ACTION_DEBUG_DUMP = "fr.nzosifou.agas.DUMP"
        private const val BACK_AFTER_MS = 45_000L
        private const val SESSION_TIMEOUT_MS = 5 * 60_000L
        private const val STATUS_LOG_INTERVAL_MS = 5_000L
        private const val MAX_ATTEMPTS = 3
        private const val TAP_DURATION_MS = 60L
        private const val HIJACK_WATCH_INTERVAL_MS = 300L
        private const val HIJACK_VERIFY_DELAY_MS = 1200L
        private const val MAX_RECOVERY_ATTEMPTS = 4
        private const val PLAY_STORE = "com.android.vending"
        private const val MAX_AUTO_REDIRECTS = 3
        private const val AUTO_REDIRECT_COOLDOWN_MS = 5_000L
        /** L'autre appli doit apparaître juste après la pub (sinon l'utilisateur est parti ailleurs). */
        private const val AUTO_REDIRECT_WINDOW_MS = 4_000L
        private const val USER_CLICK_QUIET_MS = 5_000L

        private val _running = MutableStateFlow(false)

        /** true tant que le service est lié par le système (activé dans l'accessibilité). */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
