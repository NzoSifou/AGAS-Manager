package fr.nzosifou.agas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowUpRight
import com.adamglin.phosphoricons.regular.BatteryWarning
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.Circle
import fr.nzosifou.agas.R
import fr.nzosifou.agas.agent.api.AdPhase
import fr.nzosifou.agas.data.AgasEvents
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.ui.components.LogLine
import fr.nzosifou.agas.ui.components.NButton
import fr.nzosifou.agas.ui.components.NButtonStyle
import fr.nzosifou.agas.ui.components.NDivider
import fr.nzosifou.agas.ui.components.NSwitch
import fr.nzosifou.agas.ui.components.cssLinearGradient
import fr.nzosifou.agas.ui.theme.Nocturne
import kotlinx.coroutines.delay

/** Une étape de la carte « Fiabilité ». */
class ReliabilityStep(val label: String, val action: String?, val done: Boolean, val onAction: () -> Unit, val onToggle: () -> Unit)

@Composable
fun HomeTab(
    values: AgasSettings.Values,
    stats: AgasSettings.Stats,
    adStatus: AgasEvents.AdStatus?,
    log: List<AgasLog.Entry>,
    reliability: List<ReliabilityStep>,
    notice: Pair<String, String>?,
    onOpenNotice: () -> Unit,
    onToggleEnabled: () -> Unit,
    onOpenLog: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroCard(values, adStatus, onToggleEnabled)
        notice?.let { (title, sub) -> NoticeCard(title, sub, onOpenNotice) }
        StatsRow(stats)
        if (reliability.any { !it.done }) ReliabilityCard(reliability)
        RecentActivity(log, onOpenLog)
    }
}

@Composable
private fun HeroCard(values: AgasSettings.Values, adStatus: AgasEvents.AdStatus?, onToggleEnabled: () -> Unit) {
    val on = values.enabled
    val inAd = on && adStatus != null
    val kicker = when {
        !on -> "EN PAUSE"
        inAd -> "PUB EN COURS · ${adStatus!!.network.uppercase()}"
        values.dryRun -> "MODE TEST"
        else -> "SERVICE ACTIF"
    }
    val title = when {
        !on -> "Les pubs restent à toi."
        inAd -> when (adStatus!!.phase) {
            AdPhase.SEARCHING -> "Recherche du bouton de fermeture…"
            AdPhase.CLICKING -> "Bouton trouvé, appui…"
            AdPhase.RETURNING -> "Retour au jeu."
        }
        else -> "Prêt à passer la prochaine pub."
    }
    val body = if (!on) "AGAS ne touche à rien tant que l'interrupteur est coupé."
    else "Analyse toutes les 400 ms et à chaque changement de l'écran."

    val glow by animateColorAsState(
        when {
            !on -> Color(0x2E595D6C) // rgba(89,93,108,.18)
            inAd -> Color(0x6B9184D9) // rgba(145,132,217,.42)
            else -> Color(0x339184D9) // rgba(145,132,217,.2)
        },
        tween(600), label = "glow",
    )
    val dotColor = if (on) Nocturne.accent else Nocturne.neutral600
    val dotRing = when {
        !on -> Color.Transparent
        inAd -> Color(0x599184D9) // rgba(145,132,217,.35)
        else -> Color(0x269184D9) // rgba(145,132,217,.15)
    }
    val symbolAlpha by animateFloatAsState(if (on) 1f else 0.35f, tween(400), label = "symbol")

    val shape = RoundedCornerShape(Nocturne.radiusLg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cssLinearGradient(170f, Nocturne.surface, Nocturne.bg)
            // Halo : cercle de 220 dp posé à right:-40, top:-50, radial jusqu'à 65 %.
            .drawBehind {
                val radius = 110.dp.toPx()
                val center = Offset(size.width + 40.dp.toPx() - radius, -50.dp.toPx() + radius)
                drawCircle(
                    Brush.radialGradient(0f to glow, 0.65f to Color.Transparent, center = center, radius = radius),
                    radius = radius, center = center,
                )
            }
            .border(1.dp, Nocturne.neutral800, shape)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    // box-shadow: 0 0 0 4px <ring>
                    .drawBehind { drawCircle(dotRing, radius = size.minDimension / 2 + 4.dp.toPx()) }
                    .background(dotColor, CircleShape),
            )
            Text(kicker, color = Nocturne.neutral300, fontFamily = Nocturne.font, fontSize = 11.sp, letterSpacing = 0.08.em)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title, color = Nocturne.text, fontFamily = Nocturne.font, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 1.15.em,
                )
                Text(body, style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp))
            }
            Box(Modifier.size(64.dp).alpha(symbolAlpha), contentAlignment = Alignment.Center) {
                // filter: drop-shadow(0 0 14px rgba(145,132,217,.55)) : lueur derrière le symbole.
                Box(
                    Modifier
                        .size(64.dp)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    0f to Color(0x4D9184D9), 1f to Color.Transparent,
                                    center = center, radius = size.minDimension / 2,
                                ),
                            )
                        },
                )
                Image(painterResource(R.drawable.ic_agas_symbol), contentDescription = null, modifier = Modifier.size(64.dp))
            }
        }
        Column {
            NDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleEnabled)
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Passer les pubs automatiquement", style = Nocturne.body, modifier = Modifier.weight(1f))
                NSwitch(values.enabled)
            }
        }
    }
}

@Composable
private fun StatsRow(stats: AgasSettings.Stats) {
    // « Pubs passées » s'allume un instant quand une pub vient d'être passée.
    var lastAds by remember { mutableIntStateOf(stats.adsSkipped) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(stats.adsSkipped) {
        if (stats.adsSkipped > lastAds) {
            flash = true
            delay(1200)
            flash = false
        }
        lastAds = stats.adsSkipped
    }
    val adsColor by animateColorAsState(if (flash) Nocturne.accent300 else Nocturne.text, tween(400), label = "ads")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCell(stats.adsSkipped, "Pubs passées", adsColor, Modifier.weight(1f))
        StatCell(stats.clicks, "Clics", Nocturne.text, Modifier.weight(1f))
        StatCell(stats.hijacksBlocked, "Détournements évités", Nocturne.text, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(Nocturne.surface, RoundedCornerShape(Nocturne.radiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value.toString(),
            style = Nocturne.body.copy(
                color = color, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 1.1.em,
                fontFeatureSettings = "tnum", // font-variant-numeric: tabular-nums
            ),
        )
        Text(label, color = Nocturne.neutral400, fontFamily = Nocturne.font, fontSize = 11.sp, lineHeight = 1.25.em)
    }
}

@Composable
private fun ReliabilityCard(steps: List<ReliabilityStep>) {
    val shape = RoundedCornerShape(Nocturne.radiusMd)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Nocturne.surface, shape)
            .border(1.dp, Nocturne.accent800, shape)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PhosphorIcons.Regular.BatteryWarning, null, tint = Nocturne.accent400, modifier = Modifier.size(18.dp))
            Text(
                "Le système peut mettre AGAS en veille", color = Nocturne.text, fontFamily = Nocturne.font,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            Text(
                "${steps.count { it.done }}/${steps.size}",
                style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 11.sp, fontFeatureSettings = "tnum"),
            )
        }
        Text(
            "Sans ces réglages, le téléphone gèle AGAS quelques secondes après que tu quittes l'appli.",
            style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
            modifier = Modifier.padding(start = 26.dp),
        )
        Column(Modifier.padding(top = 6.dp)) {
            steps.forEach { step ->
                NDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = step.onToggle)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (step.done) PhosphorIcons.Regular.CheckCircle else PhosphorIcons.Regular.Circle, null,
                        tint = if (step.done) Nocturne.accent else Nocturne.neutral600, modifier = Modifier.size(18.dp),
                    )
                    Text(
                        step.label,
                        style = Nocturne.body.copy(
                            color = if (step.done) Nocturne.neutral500 else Nocturne.text,
                            textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (!step.done && step.action != null) {
                        Row(
                            Modifier.clickable(onClick = step.onAction),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(step.action, color = Nocturne.accent, fontFamily = Nocturne.font, fontSize = 12.sp)
                            Icon(PhosphorIcons.Regular.ArrowUpRight, null, tint = Nocturne.accent, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivity(log: List<AgasLog.Entry>, onOpenLog: () -> Unit) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Activité récente", color = Nocturne.text, fontFamily = Nocturne.font, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            NButton(
                "Journal", onClick = onOpenLog, style = NButtonStyle.Ghost,
                fontSize = 12, trailingIcon = PhosphorIcons.Regular.CaretRight, iconSize = 12.dp,
            )
        }
        if (log.isEmpty()) {
            Text(
                "Rien pour l'instant. Lance un jeu : les pubs détectées apparaîtront ici.",
                style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
            )
        }
        log.take(4).forEach { LogLine(it, withIcon = false, modifier = Modifier.padding(vertical = 3.dp)) }
    }
}
