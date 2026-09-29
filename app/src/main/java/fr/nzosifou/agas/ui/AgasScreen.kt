package fr.nzosifou.agas.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.nzosifou.agas.R
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.service.AdSkipperService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgasScreen() {
    val context = LocalContext.current
    val settings = AgasSettings.get(context)
    val running by AdSkipperService.running.collectAsStateWithLifecycle()
    val values by settings.values.collectAsStateWithLifecycle()
    val stats by settings.stats.collectAsStateWithLifecycle()
    val log by AgasLog.entries.collectAsStateWithLifecycle()

    val isXiaomi = remember { Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) }
    val uiPrefs = remember { context.getSharedPreferences("agas_ui", Context.MODE_PRIVATE) }
    var xiaomiDone by remember { mutableStateOf(uiPrefs.getBoolean(PREF_XIAOMI_DONE, false)) }

    // Réévalué à chaque retour sur l'écran (l'utilisateur revient des réglages système).
    var batteryUnrestricted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }

    // Notification du service au premier plan (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AgasLogoHeader() }
            item {
                ServiceCard(
                    running = running,
                    onOpenAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenAppInfo = { openAppInfo(context) },
                )
            }
            if (!batteryUnrestricted || (isXiaomi && !xiaomiDone)) {
                item {
                    ReliabilityCard(
                        isXiaomi = isXiaomi,
                        batteryUnrestricted = batteryUnrestricted,
                        onRequestBattery = { openBatterySettings(context, isXiaomi) },
                        onOpenAutostart = { openXiaomiAutostart(context) },
                        onDone = {
                            xiaomiDone = true
                            uiPrefs.edit().putBoolean(PREF_XIAOMI_DONE, true).apply()
                        },
                    )
                }
            }
            item { SettingsCard(values) { settings.update(it) } }
            item { StatsCard(stats, onReset = settings::resetStats) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Journal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Journal AGAS")
                            .putExtra(Intent.EXTRA_TEXT, AgasLog.exportText())
                        context.startActivity(Intent.createChooser(send, "Partager le journal"))
                    }) { Text("Partager") }
                    TextButton(onClick = AgasLog::clear) { Text("Effacer") }
                }
            }
            if (log.isEmpty()) {
                item {
                    Text(
                        "Rien pour l'instant. Lance un jeu : les pubs détectées apparaîtront ici.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(log, key = { it.id }) { LogRow(it) }
        }
    }
}

/**
 * Logo horizontal de la maquette (logo 1e) : tuile de 40 dp en dégradé avec le symbole « petite
 * taille », suivie du nom et du sous-titre.
 */
@Composable
private fun AgasLogoHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val tile = RoundedCornerShape(11.dp)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(tile)
                .border(1.dp, Color(0xFF3F424D), tile),
            contentAlignment = Alignment.Center,
        ) {
            // Même dégradé que le fond de l'icône de l'appli.
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.drawable.ic_agas_symbol_small),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("AGAS", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em)
            Text(
                "Android Games Ads Skipper",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServiceCard(running: Boolean, onOpenAccessibility: () -> Unit, onOpenAppInfo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).background(
                        if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        CircleShape,
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (running) "Service actif" else "Service désactivé",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (running) {
                Text(
                    "AGAS surveille les pubs plein écran et les ferme dès que possible.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Active « AGAS – Passe-pub » dans les paramètres d'accessibilité " +
                        "(souvent sous « Applications installées » ou « Services téléchargés »).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAccessibility) { Text("Ouvrir l'accessibilité") }
                Text(
                    "Option grisée ? Android bloque l'accessibilité pour les applis installées hors " +
                        "Play Store. Ouvre les infos de l'appli, menu ⋮ en haut à droite, puis " +
                        "« Autoriser les paramètres restreints », et réessaie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenAppInfo) { Text("Infos de l'appli") }
            }
        }
    }
}

private const val PREF_XIAOMI_DONE = "xiaomi_settings_done"

@Composable
private fun ReliabilityCard(
    isXiaomi: Boolean,
    batteryUnrestricted: Boolean,
    onRequestBattery: () -> Unit,
    onOpenAutostart: () -> Unit,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Le système peut mettre AGAS en veille", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sans ces réglages, le téléphone gèle AGAS quelques secondes après que tu quittes " +
                    "l'appli : il ne voit alors plus aucune pub.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isXiaomi) {
                Text("1. Économiseur de batterie → « Pas de restrictions »", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRequestBattery) { Text("Réglage batterie") }
                Text("2. Activer « Démarrage automatique » pour AGAS", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenAutostart) { Text("Démarrage automatique") }
                Text(
                    "3. Dans les applis récentes, appui long sur AGAS → cadenas, pour qu'il ne soit pas " +
                        "fermé quand tu balaies les applis.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("C'est fait") }
            } else if (!batteryUnrestricted) {
                Button(onClick = onRequestBattery) { Text("Ne pas optimiser la batterie") }
            }
        }
    }
}

/** Réglage batterie de l'appli : écran Xiaomi dédié si possible, sinon la demande Android standard. */
private fun openBatterySettings(context: Context, isXiaomi: Boolean) {
    if (isXiaomi) {
        val miui = Intent()
            .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.getString(R.string.app_name))
        if (runCatching { context.startActivity(miui) }.isSuccess) return
    }
    val standard = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", context.packageName, null))
    if (runCatching { context.startActivity(standard) }.isSuccess) return
    openAppInfo(context)
}

private fun openXiaomiAutostart(context: Context) {
    val autostart = Intent()
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    if (runCatching { context.startActivity(autostart) }.isSuccess) return
    openAppInfo(context)
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    )
}

@Composable
private fun SettingsCard(values: AgasSettings.Values, onChange: ((AgasSettings.Values) -> AgasSettings.Values) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow("Passer les pubs automatiquement", null, values.enabled) { v ->
                onChange { it.copy(enabled = v) }
            }
            SettingRow(
                "Mode test",
                "Détecte et note dans le journal ce qui serait cliqué, sans cliquer.",
                values.dryRun,
            ) { v -> onChange { it.copy(dryRun = v) } }
            SettingRow(
                "Anti-détournement",
                "Si la pub ou un clic ouvre le Play Store ou une autre appli, revient à la pub " +
                    "(et mémorise les fausses croix).",
                values.hijackGuard,
            ) { v -> onChange { it.copy(hijackGuard = v) } }
            SettingRow(
                "Boutons sans libellé",
                "Après 8 s sans vraie croix, tente les petites icônes en haut de l'écran.",
                values.unlabeledButtons,
            ) { v -> onChange { it.copy(unlabeledButtons = v) } }
            SettingRow(
                "Réveiller les mini-jeux",
                "Sans bouton après 6 s, un seul appui sur la pub pour faire apparaître « Next ».",
                values.wakePlayables,
            ) { v -> onChange { it.copy(wakePlayables = v) } }
            SettingRow(
                "Sortir par le bouton boutique",
                "Mini-jeux dont la seule sortie est « Google Play » ou « Ouvrir la boutique » : AGAS " +
                    "l'utilise après 10 s puis referme la boutique (compte comme un clic sur la pub).",
                values.storeExitButtons,
            ) { v -> onChange { it.copy(storeExitButtons = v) } }
            SettingRow(
                "Touche Retour en dernier recours",
                "Après 45 s sans bouton trouvé. Peut faire perdre une récompense.",
                values.backFallback,
            ) { v -> onChange { it.copy(backFallback = v) } }
            SettingRow(
                "Enregistrer les pubs non résolues",
                "Sauvegarde leur structure pour améliorer la détection.",
                values.saveDumps,
            ) { v -> onChange { it.copy(saveDumps = v) } }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatsCard(stats: AgasSettings.Stats, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Stat("Pubs passées", stats.adsSkipped, Modifier.weight(1f))
                Stat("Clics", stats.clicks, Modifier.weight(1f))
                Stat("Détournements évités", stats.hijacksBlocked, Modifier.weight(1f))
            }
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) { Text("Remettre à zéro") }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun LogRow(entry: AgasLog.Entry) {
    val color = when (entry.level) {
        AgasLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AgasLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
        AgasLog.Level.ACTION -> MaterialTheme.colorScheme.primary
        AgasLog.Level.WARN -> MaterialTheme.colorScheme.error
    }
    Row {
        Text(
            timeFormat.format(Date(entry.time)),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
