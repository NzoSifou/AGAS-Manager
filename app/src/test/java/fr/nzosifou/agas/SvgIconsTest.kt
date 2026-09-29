package fr.nzosifou.agas

import fr.nzosifou.agas.detection.SvgIcons
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SvgIconsTest {

    private fun dataUri(svg: String) = "svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())

    @Test
    fun `croix réelle d'une pub Fyber`() {
        // Libellé exposé tel quel par la WebView de la pub (Mob Control, 28/09/2026).
        val label = "svg+xml;base64,PHN2ZyB3aWR0aD0iMTQiIGhlaWdodD0iMTQiIHZpZXdCb3g9IjAgMCAxNCAxNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTE0IDEuNDFMMTIuNTkgMEw3IDUuNTlMMS40MSAwTDAgMS40MUw1LjU5IDdMMCAxMi41OUwxLjQxIDE0TDcgOC40MUwxMi41OSAxNEwxNCAxMi41OUw4LjQxIDdMMTQgMS40MVoiIGZpbGw9IiM5NDk0OTQiLz4KPC9zdmc+Cg=="
        assertTrue(SvgIcons.isSvgDataUri(label))
        assertTrue(SvgIcons.looksLikeCross(label))
    }

    @Test
    fun `croix Material 24 px`() {
        val svg = """<svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>"""
        assertTrue(SvgIcons.looksLikeCross(dataUri(svg)))
    }

    @Test
    fun `croix en deux traits`() {
        val svg = """<svg viewBox="0 0 10 10"><line x1="0" y1="0" x2="10" y2="10"/><line x1="10" y1="0" x2="0" y2="10"/></svg>"""
        assertTrue(SvgIcons.looksLikeCross(dataUri(svg)))
    }

    @Test
    fun `un plus n'est pas une croix`() {
        val svg = """<svg viewBox="0 0 24 24"><path d="M19 13L13 13L13 19L11 19L11 13L5 13L5 11L11 11L11 5L13 5L13 11L19 11L19 13Z"/></svg>"""
        assertFalse(SvgIcons.looksLikeCross(dataUri(svg)))
    }

    @Test
    fun `une étoile de notation n'est pas une croix`() {
        val svg = """<svg viewBox="0 0 16 16"><path d="M12.2362 2L13 4.92746L9.73214 5.74964L11.9114 14.0652L8.21209 15L6.084 6.91781C6.03715 6.82164 5.98158 6.81929 5.89113 6.82398C5.42041 6.84744 2.98831 7.77986 2.77365 7.61332L2 4.69524L12.2362 2Z"/></svg>"""
        assertFalse(SvgIcons.looksLikeCross(dataUri(svg)))
    }
}
