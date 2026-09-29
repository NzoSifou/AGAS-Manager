package fr.nzosifou.agas.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.FastForward
import com.adamglin.phosphoricons.regular.House
import com.adamglin.phosphoricons.regular.ListBullets
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import fr.nzosifou.agas.data.AgasEvents
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.service.AdSkipperService
import fr.nzosifou.agas.ui.components.AgasLogoTile
import fr.nzosifou.agas.ui.components.NDivider
import fr.nzosifou.agas.ui.components.NTagOutline
import fr.nzosifou.agas.ui.theme.Nocturne
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    HOME("Accueil", "AGAS", PhosphorIcons.Regular.House),
    SETTINGS("Réglages", "Réglages", PhosphorIcons.Regular.SlidersHorizontal),
    LOG("Journal", "Journal", PhosphorIcons.Regular.ListBullets),
}

/** Racine de l'interface : première configuration (1c) tant que le service est inactif, sinon l'appli (1b). */
@Composable
fun AgasApp() {
    val context = LocalContext.current
    val running by AdSkipperService.running.collectAsStateWithLifecycle()

    // Notification du service au premier plan (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (running) MainApp() else SetupScreen()
}

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val settings = AgasSettings.get(context)
    val values by settings.values.collectAsStateWithLifecycle()
    val stats by settings.stats.collectAsStateWithLifecycle()
    val log by AgasLog.entries.collectAsStateWithLifecycle()
    val adStatus by AgasEvents.adStatus.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    // Message éphémère (« Pub Unity passée »…), 2,4 s.
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { AgasEvents.toasts.collect { toast = it } }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            toast = null
        }
    }

    val reliability = rememberReliabilitySteps(context)

    Column(
        Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // En-tête
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgasLogoTile(size = 36.dp, radius = 10.dp, symbolSize = 24.dp)
            Text(
                tab.title, color = Nocturne.text, fontFamily = Nocturne.font, fontSize = 17.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.02.em, modifier = Modifier.weight(1f),
            )
            if (values.dryRun) NTagOutline("Mode test")
        }

        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            LaunchedEffect(tab) { scroll.scrollTo(0) }
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                when (tab) {
                    Tab.HOME -> HomeTab(
                        values = values,
                        stats = stats,
                        adStatus = adStatus,
                        log = log,
                        reliability = reliability,
                        onToggleEnabled = { settings.update { it.copy(enabled = !it.enabled) } },
                        onOpenLog = { tab = Tab.LOG },
                    )
                    Tab.SETTINGS -> SettingsTab(values, onChange = settings::update, onResetStats = settings::resetStats)
                    Tab.LOG -> LogTab(log, onShare = { shareLog(context) }, onClear = AgasLog::clear)
                }
            }
            toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter)) }
        }

        BottomNav(tab) { tab = it }
    }
}

@Composable
private fun Toast(message: String, modifier: Modifier) {
    val shape = RoundedCornerShape(Nocturne.radiusMd)
    Row(
        modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .fillMaxWidth()
            .background(Nocturne.neutral900, shape)
            .border(1.dp, Nocturne.neutral700, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PhosphorIcons.Fill.FastForward, null, tint = Nocturne.accent, modifier = Modifier.size(16.dp))
        Text(message, style = Nocturne.body.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Nocturne.bg)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        NDivider()
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) {
            Tab.entries.forEach { item ->
                val selected = item == current
                val color = if (selected) Nocturne.accent else Nocturne.neutral400
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(item) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val pill = RoundedCornerShape(13.dp)
                    Box(
                        Modifier
                            .size(52.dp, 26.dp)
                            .then(if (selected) Modifier.border(1.dp, Nocturne.accent700, pill) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    Text(item.label, color = color, fontFamily = Nocturne.font, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Étapes de la carte « Fiabilité » (cochées par l'utilisateur, mémorisées). Xiaomi : batterie,
 * démarrage automatique, cadenas. Autres : optimisation de batterie, détectée automatiquement.
 */
@Composable
private fun rememberReliabilitySteps(context: Context): List<ReliabilityStep> {
    val prefs = remember { context.getSharedPreferences("agas_ui", Context.MODE_PRIVATE) }
    // Ancienne carte « C'est fait » : les trois réglages Xiaomi étaient déjà faits.
    val legacyDone = remember { prefs.getBoolean("xiaomi_settings_done", false) }
    var done by remember { mutableStateOf(List(3) { prefs.getBoolean("rel_$it", legacyDone) }) }
    var batteryUnrestricted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }
    fun toggle(i: Int) {
        done = done.toMutableList().also { it[i] = !it[i] }
        prefs.edit().putBoolean("rel_$i", done[i]).apply()
    }
    return if (SystemSettings.isXiaomi) {
        listOf(
            ReliabilityStep("Batterie → « Pas de restrictions »", "Réglage", done[0], { SystemSettings.openBattery(context) }, { toggle(0) }),
            ReliabilityStep("Démarrage automatique activé", "Ouvrir", done[1], { SystemSettings.openXiaomiAutostart(context) }, { toggle(1) }),
            ReliabilityStep("Cadenas dans les applis récentes", null, done[2], {}, { toggle(2) }),
        )
    } else {
        listOf(
            ReliabilityStep(
                "Batterie : ne pas optimiser", "Réglage", batteryUnrestricted || done[0],
                { SystemSettings.openBattery(context) }, { toggle(0) },
            ),
        )
    }
}

private fun shareLog(context: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Journal AGAS")
        .putExtra(Intent.EXTRA_TEXT, AgasLog.exportText())
    context.startActivity(Intent.createChooser(send, "Partager le journal"))
}
