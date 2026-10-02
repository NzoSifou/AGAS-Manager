package fr.nzosifou.agas.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.PuzzlePiece
import com.adamglin.phosphoricons.regular.WarningCircle
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.runtime.AgentPackage
import fr.nzosifou.agas.runtime.AgentRuntime
import fr.nzosifou.agas.runtime.AgentUpdater
import fr.nzosifou.agas.ui.components.NButton
import fr.nzosifou.agas.ui.components.NButtonStyle
import fr.nzosifou.agas.ui.components.NDivider
import fr.nzosifou.agas.ui.theme.Nocturne

/** Section « AGENT » des réglages : version chargée, mises à jour, version du Manager. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.AgentSection(
    runtime: AgentRuntime.State,
    update: AgentUpdater.Status,
    managerRelease: AgentUpdater.Release?,
    values: AgasSettings.Values,
    onChange: ((AgasSettings.Values) -> AgasSettings.Values) -> Unit,
) {
    val context = LocalContext.current
    val agent = runtime.agent
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (agent != null) PhosphorIcons.Regular.PuzzlePiece else PhosphorIcons.Regular.WarningCircle, null,
                tint = Nocturne.accent, modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (agent != null) "AGAS Agent ${agent.versionName}" else "Aucun Agent chargé",
                    style = Nocturne.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    when {
                        agent == null -> runtime.error ?: "AGAS ne peut pas passer les pubs."
                        agent.source == AgentPackage.Source.BUNDLED -> "Version intégrée au Manager. C'est elle qui sait passer les pubs."
                        else -> "Version téléchargée depuis GitHub. C'est elle qui sait passer les pubs."
                    },
                    style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
                )
            }
        }
        Text(updateText(update), style = Nocturne.body.copy(color = Nocturne.neutral300, fontSize = 12.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (update) {
                is AgentUpdater.Status.Available -> NButton(
                    "Installer la version ${update.release.version}",
                    onClick = { AgentUpdater.install(context, update.release) },
                    icon = PhosphorIcons.Regular.DownloadSimple, fontSize = 13,
                )
                is AgentUpdater.Status.Checking, is AgentUpdater.Status.Downloading -> Unit
                else -> NButton(
                    "Rechercher une mise à jour",
                    onClick = { AgentUpdater.check(context, install = false) },
                    style = NButtonStyle.Secondary, icon = PhosphorIcons.Regular.ArrowsClockwise, fontSize = 13,
                )
            }
            if (agent?.source == AgentPackage.Source.DOWNLOADED) {
                NButton(
                    "Revenir à la version intégrée", onClick = AgentRuntime::useBundled,
                    style = NButtonStyle.Secondary, icon = PhosphorIcons.Regular.ArrowCounterClockwise, fontSize = 13,
                )
            }
            if (runtime.hasRejected) {
                NButton(
                    "Réessayer les versions écartées", onClick = AgentRuntime::retryRejected,
                    style = NButtonStyle.Secondary, fontSize = 13,
                )
            }
        }
    }
    NDivider()
    SwitchRow(
        "Mettre à jour l'Agent automatiquement",
        "Installe les nouvelles versions dès leur publication (vérification toutes les 12 h). Sans réinstaller l'appli.",
        values.autoUpdateAgent,
    ) { onChange { it.copy(autoUpdateAgent = !it.autoUpdateAgent) } }
    if (managerRelease != null) {
        NDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { openPage(context, managerRelease.pageUrl) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AGAS Manager ${managerRelease.version} disponible", style = Nocturne.body.copy(fontSize = 14.sp))
                Text(
                    "Nouvel APK à installer par-dessus celui-ci (réglages conservés).",
                    style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
                )
            }
            Icon(PhosphorIcons.Regular.CaretRight, null, tint = Nocturne.accent, modifier = Modifier.size(14.dp))
        }
    }
}

private fun updateText(status: AgentUpdater.Status): String = when (status) {
    AgentUpdater.Status.Idle -> "Les nouvelles versions de l'Agent sont publiées sur GitHub."
    AgentUpdater.Status.Checking -> "Recherche d'une nouvelle version…"
    AgentUpdater.Status.UpToDate -> "L'Agent est à jour."
    is AgentUpdater.Status.Available -> "Version ${status.release.version} disponible."
    is AgentUpdater.Status.Downloading -> "Téléchargement de la version ${status.release.version}…"
    is AgentUpdater.Status.Installed -> "Version ${status.versionName} installée et active."
    is AgentUpdater.Status.NeedsNewerManager -> "La version ${status.version} de l'Agent demande d'abord de mettre à jour AGAS Manager."
    is AgentUpdater.Status.Failed -> status.message
}

/**
 * Bandeau de l'accueil quand une action est utile : aucun Agent chargé, ou mise à jour à
 * installer. Renvoie vers les réglages. Rien à afficher : null.
 */
fun homeNotice(
    runtime: AgentRuntime.State,
    update: AgentUpdater.Status,
    managerRelease: AgentUpdater.Release?,
): Pair<String, String>? = when {
    runtime.agent == null -> "Aucun Agent chargé" to (runtime.error ?: "AGAS ne peut pas passer les pubs.")
    update is AgentUpdater.Status.NeedsNewerManager || managerRelease != null ->
        "Mise à jour d'AGAS Manager disponible" to "Une nouvelle version de l'appli est publiée."
    update is AgentUpdater.Status.Available ->
        "AGAS Agent ${update.release.version} disponible" to "Nouvelles règles pour passer les pubs."
    else -> null
}

@Composable
fun NoticeCard(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Nocturne.radiusMd)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Nocturne.surface)
            .border(1.dp, Nocturne.accent800, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PhosphorIcons.Regular.DownloadSimple, null, tint = Nocturne.accent400, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Nocturne.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text(sub, style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp))
        }
        Icon(PhosphorIcons.Regular.CaretRight, null, tint = Nocturne.accent, modifier = Modifier.size(14.dp))
    }
}

private fun openPage(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
