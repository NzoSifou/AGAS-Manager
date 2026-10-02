package fr.nzosifou.agas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.nzosifou.agas.agent.api.AgentSetting
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.ui.components.NButton
import fr.nzosifou.agas.ui.components.NButtonStyle
import fr.nzosifou.agas.ui.components.NDivider
import fr.nzosifou.agas.ui.components.NSwitch
import fr.nzosifou.agas.ui.theme.Nocturne

/**
 * Réglages regroupés par usage (maquette 1b) : ceux du Manager, puis ceux déclarés par l'Agent
 * chargé (dans ses groupes), puis la section de l'Agent lui-même (version, mises à jour).
 */
@Composable
fun SettingsTab(
    values: AgasSettings.Values,
    onChange: ((AgasSettings.Values) -> AgasSettings.Values) -> Unit,
    agentSettings: List<AgentSetting>,
    agentValues: Map<String, Boolean>,
    onAgentChange: (key: String, value: Boolean) -> Unit,
    agentSection: @Composable ColumnScope.() -> Unit,
    onResetStats: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsGroup("GÉNÉRAL") {
            SwitchRow(
                "Passer les pubs automatiquement", "Interrupteur général.", values.enabled,
            ) { onChange { it.copy(enabled = !it.enabled) } }
            NDivider()
            SwitchRow(
                "Mode test", "Détecte et note dans le journal ce qui serait cliqué, sans cliquer.", values.dryRun,
            ) { onChange { it.copy(dryRun = !it.dryRun) } }
        }
        // Groupes dans l'ordre où l'Agent les déclare.
        agentSettings.groupBy { it.group }.forEach { (group, items) ->
            SettingsGroup(group) {
                items.forEachIndexed { index, item ->
                    if (index > 0) NDivider()
                    val checked = agentValues[item.key] ?: item.default
                    SwitchRow(item.title, item.summary, checked) { onAgentChange(item.key, !checked) }
                }
            }
        }
        SettingsGroup("AGENT", content = agentSection)
        NButton("Remettre les statistiques à zéro", onClick = onResetStats, style = NButtonStyle.Secondary)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title, color = Nocturne.accent, fontFamily = Nocturne.font, fontSize = 10.sp,
            letterSpacing = 0.1.em, modifier = Modifier.padding(start = 2.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Nocturne.radiusMd))
                .background(Nocturne.surface),
            content = content,
        )
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Nocturne.body.copy(fontSize = 14.sp))
            Text(sub, style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp))
        }
        NSwitch(checked)
    }
}
