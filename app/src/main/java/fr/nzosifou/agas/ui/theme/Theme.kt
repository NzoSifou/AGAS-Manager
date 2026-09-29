package fr.nzosifou.agas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Design system « Nocturne » (maquette « AGAS mobile app design », variante 1b) : fond bleu-gris
 * presque neutre, un seul accent (le violet du logo, #9184d9) utilisé en trait et en lueur plutôt
 * qu'en aplat, rampes tonales 100–900. Thème sombre uniquement.
 */
object Nocturne {
    val bg = Color(0xFF161826)
    val surface = Color(0xFF232532)
    val text = Color(0xFFE9E9ED)
    val accent = Color(0xFF9184D9)

    /** color-mix(#e9e9ed 16 %, transparent). */
    val divider = Color(0x29E9E9ED)

    val neutral100 = Color(0xFFF3F5FE)
    val neutral200 = Color(0xFFE4E7F5)
    val neutral300 = Color(0xFFCFD3E5)
    val neutral400 = Color(0xFFB2B6CA)
    val neutral500 = Color(0xFF9397AB)
    val neutral600 = Color(0xFF75798C)
    val neutral700 = Color(0xFF595D6C)
    val neutral800 = Color(0xFF3F424D)
    val neutral900 = Color(0xFF292B31)

    val accent100 = Color(0xFFF5F4FF)
    val accent200 = Color(0xFFE7E5FE)
    val accent300 = Color(0xFFD2CEFD)
    val accent400 = Color(0xFFB5ABFC)
    val accent500 = Color(0xFF968AE0)
    val accent600 = Color(0xFF796CBF)
    val accent700 = Color(0xFF5D5294)
    val accent800 = Color(0xFF423A6A)
    val accent900 = Color(0xFF2B2741)

    val radiusSm = 4.dp
    val radiusMd = 8.dp
    val radiusLg = 14.dp

    /**
     * La maquette utilise Inter ; l'appli garde volontairement la police système d'Android
     * (choix validé), sans fichier de police à embarquer.
     */
    val font: FontFamily = FontFamily.SansSerif

    /** Texte de base : 13 sp, interligne 1,45. */
    val body = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 1.45.em, color = text)
}

private val NocturneColors = darkColorScheme(
    primary = Nocturne.accent,
    onPrimary = Nocturne.bg,
    background = Nocturne.bg,
    onBackground = Nocturne.text,
    surface = Nocturne.bg,
    onSurface = Nocturne.text,
    surfaceVariant = Nocturne.surface,
    onSurfaceVariant = Nocturne.neutral400,
    outline = Nocturne.divider,
)

@Composable
fun AgasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NocturneColors, content = content)
}
