package fr.nzosifou.agas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Trash
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.ui.components.LogLine
import fr.nzosifou.agas.ui.components.NIconButton
import fr.nzosifou.agas.ui.theme.Nocturne

private enum class LogFilter(val label: String) { ALL("Tout"), ACTIONS("Actions"), ALERTS("Alertes") }

@Composable
fun LogTab(log: List<AgasLog.Entry>, onShare: () -> Unit, onClear: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val rows = when (filter) {
        LogFilter.ALL -> log
        LogFilter.ACTIONS -> log.filter { it.level == AgasLog.Level.ACTION }
        LogFilter.ALERTS -> log.filter { it.level == AgasLog.Level.WARN }
    }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Contrôle segmenté
            val shape = RoundedCornerShape(Nocturne.radiusMd)
            Row(
                Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min)
                    .clip(shape)
                    .border(1.dp, Nocturne.divider, shape),
            ) {
                LogFilter.entries.forEachIndexed { index, option ->
                    if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Nocturne.divider))
                    val selected = filter == option
                    Text(
                        option.label,
                        color = if (selected) Nocturne.accent else Nocturne.neutral300,
                        fontFamily = Nocturne.font,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            // box-shadow: inset 0 0 0 1px accent (le conteneur arrondit les coins)
                            .then(if (selected) Modifier.border(1.dp, Nocturne.accent) else Modifier)
                            .clickable { filter = option }
                            .padding(vertical = 7.dp),
                    )
                }
            }
            NIconButton(PhosphorIcons.Regular.ShareNetwork, "Partager", onShare)
            NIconButton(PhosphorIcons.Regular.Trash, "Effacer", onClear)
        }

        if (rows.isEmpty()) {
            Text(
                "Rien pour l'instant. Lance un jeu : les pubs détectées apparaîtront ici.",
                style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 32.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Nocturne.surface, RoundedCornerShape(Nocturne.radiusMd))
                    .padding(vertical = 6.dp),
            ) {
                rows.forEach { LogLine(it, withIcon = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) }
            }
        }
    }
}
