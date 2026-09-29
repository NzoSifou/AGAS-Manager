package fr.nzosifou.agas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import fr.nzosifou.agas.data.AgasSettings
import fr.nzosifou.agas.ui.components.NButton
import fr.nzosifou.agas.ui.components.NButtonStyle
import fr.nzosifou.agas.ui.components.NDivider
import fr.nzosifou.agas.ui.components.NSwitch
import fr.nzosifou.agas.ui.theme.Nocturne

private class SettingItem(
    val title: String,
    val sub: String,
    val get: (AgasSettings.Values) -> Boolean,
    val set: (AgasSettings.Values, Boolean) -> AgasSettings.Values,
)

/** Réglages regroupés par usage (maquette 1b). */
private val GROUPS: List<Pair<String, List<SettingItem>>> = listOf(
    "GÉNÉRAL" to listOf(
        SettingItem("Passer les pubs automatiquement", "Interrupteur général.", { it.enabled }, { v, b -> v.copy(enabled = b) }),
        SettingItem(
            "Mode test", "Détecte et note dans le journal ce qui serait cliqué, sans cliquer.",
            { it.dryRun }, { v, b -> v.copy(dryRun = b) },
        ),
    ),
    "PROTECTION" to listOf(
        SettingItem(
            "Anti-détournement",
            "Si la pub ou un clic ouvre le Play Store ou une autre appli, revient à la pub (et mémorise les fausses croix).",
            { it.hijackGuard }, { v, b -> v.copy(hijackGuard = b) },
        ),
    ),
    "SANS VRAIE CROIX" to listOf(
        SettingItem(
            "Boutons sans libellé", "Après 8 s sans vraie croix, tente les petites icônes en haut de l'écran.",
            { it.unlabeledButtons }, { v, b -> v.copy(unlabeledButtons = b) },
        ),
        SettingItem(
            "Réveiller les mini-jeux", "Sans bouton après 6 s, un seul appui sur la pub pour faire apparaître « Next ».",
            { it.wakePlayables }, { v, b -> v.copy(wakePlayables = b) },
        ),
        SettingItem(
            "Sortir par le bouton boutique",
            "Seule sortie « Google Play » : AGAS l'utilise après 10 s puis referme la boutique (compte comme un clic sur la pub).",
            { it.storeExitButtons }, { v, b -> v.copy(storeExitButtons = b) },
        ),
        SettingItem(
            "Touche Retour en dernier recours", "Après 45 s sans bouton trouvé. Peut faire perdre une récompense.",
            { it.backFallback }, { v, b -> v.copy(backFallback = b) },
        ),
    ),
    "DIAGNOSTIC" to listOf(
        SettingItem(
            "Enregistrer les pubs non résolues", "Sauvegarde leur structure pour améliorer la détection.",
            { it.saveDumps }, { v, b -> v.copy(saveDumps = b) },
        ),
    ),
)

@Composable
fun SettingsTab(
    values: AgasSettings.Values,
    onChange: ((AgasSettings.Values) -> AgasSettings.Values) -> Unit,
    onResetStats: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GROUPS.forEach { (title, items) ->
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
                ) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) NDivider()
                        val checked = item.get(values)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChange { item.set(it, !checked) } }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.title, style = Nocturne.body.copy(fontSize = 14.sp))
                                Text(item.sub, style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp))
                            }
                            NSwitch(checked)
                        }
                    }
                }
            }
        }
        NButton("Remettre les statistiques à zéro", onClick = onResetStats, style = NButtonStyle.Secondary)
    }
}
