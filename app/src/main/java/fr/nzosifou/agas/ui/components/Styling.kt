package fr.nzosifou.agas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CursorClick
import com.adamglin.phosphoricons.regular.DotOutline
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.Warning
import fr.nzosifou.agas.data.AgasLog
import fr.nzosifou.agas.ui.theme.Nocturne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Équivalent de `linear-gradient(<angle>deg, from, to)` en CSS : 0° vers le haut, 90° vers la
 * droite, et une ligne de dégradé assez longue pour que les coins reçoivent les couleurs extrêmes.
 */
fun Modifier.cssLinearGradient(angleDeg: Float, from: Color, to: Color): Modifier = drawBehind {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRect(Brush.linearGradient(listOf(from, to), start = c - Offset(dx, dy) * half, end = c + Offset(dx, dy) * half))
}

/** Couleur et icône d'une ligne de journal selon son niveau (maquette 1b). */
fun AgasLog.Level.color(): Color = when (this) {
    AgasLog.Level.DEBUG -> Nocturne.neutral500
    AgasLog.Level.INFO -> Nocturne.text
    AgasLog.Level.ACTION -> Nocturne.accent300
    AgasLog.Level.WARN -> Nocturne.accent200
}

fun AgasLog.Level.icon(): ImageVector = when (this) {
    AgasLog.Level.DEBUG -> PhosphorIcons.Regular.DotOutline
    AgasLog.Level.INFO -> PhosphorIcons.Regular.Eye
    AgasLog.Level.ACTION -> PhosphorIcons.Regular.CursorClick
    AgasLog.Level.WARN -> PhosphorIcons.Regular.Warning
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

/** Ligne de journal : heure en chasse fixe, icône de niveau (facultative), message coloré. */
@Composable
fun LogLine(entry: AgasLog.Entry, withIcon: Boolean, modifier: Modifier = Modifier) {
    val color = entry.level.color()
    // align-items: baseline : l'heure et le message partagent leur ligne de base.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            timeFormat.format(Date(entry.time)),
            color = Nocturne.neutral500,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.alignByBaseline(),
        )
        if (withIcon) {
            Icon(entry.level.icon(), null, tint = color, modifier = Modifier.padding(top = 3.dp).size(12.dp))
        }
        Text(
            entry.message,
            style = Nocturne.body.copy(color = color, fontSize = 12.sp),
            modifier = Modifier.alignByBaseline(),
        )
    }
}
