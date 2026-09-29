package fr.nzosifou.agas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowSquareOut
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretUp
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.WifiSlash
import fr.nzosifou.agas.ui.components.AgasLogoTile
import fr.nzosifou.agas.ui.components.NButton
import fr.nzosifou.agas.ui.components.NButtonStyle
import fr.nzosifou.agas.ui.theme.Nocturne

/** Première configuration (maquette 1c) : affichée tant que le service d'accessibilité n'est pas actif. */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    var helpOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            // radial-gradient(120% 60% at 0% 0%, accent-900, transparent 60%)
            .drawBehind {
                val r = 100f
                val sx = size.width * 1.2f / r
                val sy = size.height * 0.6f / r
                scale(sx, sy, pivot = Offset.Zero) {
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Nocturne.accent900,
                            0.6f to Color.Transparent,
                            center = Offset.Zero,
                            radius = r,
                        ),
                        size = Size(size.width / sx, size.height / sy),
                    )
                }
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Logo horizontal
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgasLogoTile(size = 40.dp, radius = 11.dp, symbolSize = 28.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            "AGAS", color = Nocturne.text, fontFamily = Nocturne.font, fontSize = 20.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = 0.06.em, lineHeight = 1.1.em,
                        )
                        Text("Android Games Ads Skipper", color = Nocturne.neutral400, fontFamily = Nocturne.font, fontSize = 11.sp)
                    }
                }

                // Accroche
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "PREMIÈRE CONFIGURATION", color = Nocturne.accent, fontFamily = Nocturne.font,
                        fontSize = 10.sp, letterSpacing = 0.1.em,
                    )
                    Text(
                        "Laisse AGAS appuyer sur la croix à ta place.",
                        color = Nocturne.text, fontFamily = Nocturne.font, fontSize = 28.sp,
                        fontWeight = FontWeight.Medium, lineHeight = 1.15.em,
                    )
                    Text(
                        "La pub s'affiche normalement et le développeur du jeu est payé. AGAS attend le bouton " +
                            "de fermeture, puis le touche dès qu'il devient cliquable.",
                        style = Nocturne.body.copy(color = Nocturne.neutral300),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Étape 1
                    val stepShape = RoundedCornerShape(Nocturne.radiusMd)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Nocturne.surface, stepShape)
                            .border(1.dp, Nocturne.accent700, stepShape)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(22.dp).border(1.dp, Nocturne.accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("1", color = Nocturne.accent, fontFamily = Nocturne.font, fontSize = 11.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Activer « AGAS – Passe-pub »", color = Nocturne.text, fontFamily = Nocturne.font,
                                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Paramètres d'accessibilité, souvent sous « Applications installées » ou « Services téléchargés ».",
                                style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp),
                            )
                            NButton(
                                "Ouvrir l'accessibilité",
                                onClick = { SystemSettings.openAccessibility(context) },
                                icon = PhosphorIcons.Regular.ArrowSquareOut,
                            )
                        }
                    }

                    // Aide « Option grisée ? »
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Nocturne.surface, RoundedCornerShape(Nocturne.radiusMd)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { helpOpen = !helpOpen }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(PhosphorIcons.Regular.LockSimple, null, tint = Nocturne.neutral400, modifier = Modifier.size(18.dp))
                            Text("Option grisée ?", style = Nocturne.body, modifier = Modifier.weight(1f))
                            Icon(
                                if (helpOpen) PhosphorIcons.Regular.CaretUp else PhosphorIcons.Regular.CaretDown,
                                null, tint = Nocturne.neutral400, modifier = Modifier.size(14.dp),
                            )
                        }
                        if (helpOpen) {
                            Column(
                                Modifier.padding(start = 44.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "Android bloque l'accessibilité des applis installées hors Play Store. Ouvre les infos " +
                                        "de l'appli, menu ⋮ en haut à droite, puis « Autoriser les paramètres restreints », et réessaie.",
                                    style = Nocturne.body.copy(color = Nocturne.neutral300, fontSize = 12.sp),
                                )
                                NButton(
                                    "Infos de l'appli",
                                    onClick = { SystemSettings.openAppInfo(context) },
                                    style = NButtonStyle.Secondary,
                                )
                            }
                        }
                    }
                }
            }

            // Engagements, en bas de page
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Promise(PhosphorIcons.Regular.WifiSlash, "Aucune IA, aucun serveur : rien ne quitte le téléphone.")
                Promise(PhosphorIcons.Regular.Eye, "L'écran n'est lu que pendant qu'une pub est affichée.")
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Nocturne.neutral400, modifier = Modifier.size(15.dp))
        Text(text, style = Nocturne.body.copy(color = Nocturne.neutral400, fontSize = 12.sp))
    }
}
