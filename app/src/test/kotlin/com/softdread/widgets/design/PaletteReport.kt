package com.softdread.widgets.design

import androidx.compose.ui.graphics.Color
import com.softdread.widgets.domain.model.ColourRole
import org.junit.Test

/**
 * Not an assertion — a readable dump of what the palette actually resolves to,
 * so a colour change can be reviewed rather than guessed at. Run with:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*PaletteReport' -i
 */
class PaletteReport {

    private fun hex(c: Color) = "#%02X%02X%02X".format(
        (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
    )

    @Test
    fun printResolvedPalette() {
        listOf(false, true).forEach { dark ->
            println("\n--- ${if (dark) "dark" else "light"} ---")
            println("role     sheet     rendered  type      ratio  muted     label")
            ColourRole.entries.forEach { role ->
                val sheet = SoftDreadTiles.sheetField(role, ThemePack.CLAY_HOUSE, dark)
                val c = SoftDreadTiles.colours(role, ThemePack.CLAY_HOUSE, dark)
                println(
                    "%-8s %-9s %-9s %-9s %5.2f  %-9s %-9s".format(
                        role.name, hex(sheet), hex(c.surface), hex(c.onSurface),
                        Contrast.ratio(c.onSurface, c.surface), hex(c.onSurfaceMuted), hex(c.label),
                    ),
                )
            }
        }
    }
}
