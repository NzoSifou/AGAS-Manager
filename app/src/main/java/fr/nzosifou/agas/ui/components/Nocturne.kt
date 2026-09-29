package fr.nzosifou.agas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.nzosifou.agas.R
import fr.nzosifou.agas.ui.theme.Nocturne

/** Interrupteur Nocturne : piste 36 × 20, pastille de 14. */
@Composable
fun NSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val spec = tween<Color>(200)
    val border by animateColorAsState(if (checked) Nocturne.accent else Nocturne.neutral700, spec, label = "border")
    val track by animateColorAsState(if (checked) Nocturne.accent800 else Color.Transparent, spec, label = "track")
    val knob by animateColorAsState(if (checked) Nocturne.accent200 else Nocturne.neutral600, spec, label = "knob")
    val left by animateDpAsState(if (checked) 19.dp else 3.dp, tween(200), label = "left")
    Box(
        modifier
            .size(36.dp, 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .border(1.dp, border, RoundedCornerShape(10.dp)),
    ) {
        Box(
            Modifier
                // En CSS, la pastille est positionnée à l'intérieur de la bordure de 1 px.
                .offset(x = left + 1.dp, y = 3.dp)
                .size(14.dp)
                .background(knob, CircleShape),
        )
    }
}

enum class NButtonStyle { Primary, Secondary, Ghost }

/**
 * Bouton Nocturne. Le bouton principal est un contour d'accent, jamais un aplat. Les états
 * « pressé » viennent de la rampe d'accent.
 */
@Composable
fun NButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NButtonStyle = NButtonStyle.Primary,
    icon: ImageVector? = null,
    iconSize: Dp = 15.dp,
    fontSize: Int = 14,
    trailingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Nocturne.radiusMd)
    val (content, border, pressedBg) = when (style) {
        NButtonStyle.Primary -> Triple(Nocturne.accent, Nocturne.accent, Nocturne.accent.copy(alpha = 0.22f))
        NButtonStyle.Secondary -> Triple(Nocturne.text, Nocturne.divider, Nocturne.text.copy(alpha = 0.14f))
        NButtonStyle.Ghost -> Triple(Nocturne.accent, Color.Transparent, Nocturne.accent.copy(alpha = 0.18f))
    }
    Row(
        modifier
            .clip(shape)
            .background(if (pressed) pressedBg else Color.Transparent)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                horizontal = if (style == NButtonStyle.Ghost) 2.8.dp else 10.08.dp,
                vertical = 5.6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        Text(
            text,
            color = content,
            fontFamily = Nocturne.font,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize.sp,
            lineHeight = 1.2.em,
        )
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
    }
}

/** Bouton icône secondaire 36 × 36 (Partager, Effacer…). */
@Composable
fun NIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Nocturne.radiusMd)
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (pressed) Nocturne.text.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, Nocturne.divider, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Nocturne.text, modifier = Modifier.size(16.dp))
    }
}

/** Étiquette à contour d'accent (« Mode test »). */
@Composable
fun NTagOutline(text: String) {
    Text(
        text,
        color = Nocturne.accent,
        fontFamily = Nocturne.font,
        fontSize = 11.sp,
        letterSpacing = 0.02.em,
        modifier = Modifier
            .border(1.dp, Nocturne.accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Tuile du logo : dégradé du fond de l'icône, liseré, symbole « petite taille ». */
@Composable
fun AgasLogoTile(size: Dp, radius: Dp, symbolSize: Dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .border(1.dp, Nocturne.neutral800, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_agas_symbol_small),
            contentDescription = null,
            modifier = Modifier.size(symbolSize),
        )
    }
}

/** Ligne de séparation de 1 dp (couleur « divider »). */
@Composable
fun NDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Nocturne.divider),
    )
}
