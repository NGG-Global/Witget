package com.softdread.widgets.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.softdread.widgets.data.prefs.GlobalPreferences
import com.softdread.widgets.data.prefs.SavedLocation
import com.softdread.widgets.data.prefs.WidgetInstanceConfig
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.ui.preview.WidgetPreviewer
import com.softdread.widgets.widgets.common.TileRenderer
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide companion to [TileSnapshotDump]: not an assertion, but a full sweep.
 *
 * Renders the *real* pipeline — each widget's own `buildPayload`, not sample
 * content — for every widget at every breakpoint, in both modes, at the largest
 * accessibility font scale, and at the awkward sizes a launcher will actually
 * hand a resized tile. Writes PNGs under `build/audit-tiles/` so a human can
 * look at what ships.
 *
 * This is what surfaced the cut heroes, the cut punchline and the bars drawn
 * across a field circle. Run it and look at the output before trusting any
 * change to [TileRenderer] or to a widget's per-breakpoint content.
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*TileMatrixDump'
 *     ls app/build/audit-tiles/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TileMatrixDump {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun configFor(type: WidgetType): WidgetInstanceConfig {
        val base = WidgetInstanceConfig.default(1000 + type.ordinal, type)
        return when (type) {
            WidgetType.COUNTDOWN -> base.copy(
                countdownTitle = "japan",
                countdownTargetEpochMillis = System.currentTimeMillis() + 19L * 86_400_000L,
                countdownCreatedAtEpochMillis = System.currentTimeMillis() - 40L * 86_400_000L,
            )
            WidgetType.WEATHER -> base.copy(
                useDeviceLocation = false,
                savedLocation = SavedLocation("tel aviv", 32.08, 34.78),
            )
            else -> base
        }
    }

    private fun render(
        out: File,
        name: String,
        type: WidgetType,
        breakpoint: WidgetBreakpoint,
        dark: Boolean,
        widthDp: Int,
        heightDp: Int,
        density: Float = 3f,
    ) = runBlocking {
        val preview = WidgetPreviewer.previewOrSample(
            context = context,
            type = type,
            config = configFor(type),
            preferences = GlobalPreferences(defaultPersonalityKey = Personality.DEFAULT.key),
            breakpoint = breakpoint,
            isDark = dark,
        )
        val role = preview.content.fieldRole ?: type.colourRole
        val colours = SoftDreadTiles.colours(role, ThemePack.CLAY_HOUSE, dark)
        val bitmap: Bitmap = TileRenderer.render(
            context, role, breakpoint, colours, preview.content,
            (widthDp * density).toInt(), (heightDp * density).toInt(), density,
        )
        FileOutputStream(File(out, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun everyWidgetEverySize() {
        val out = File("build/audit-tiles/matrix").apply { mkdirs() }
        WidgetType.entries.forEach { type ->
            WidgetBreakpoint.entries.forEach { bp ->
                listOf(false, true).forEach { dark ->
                    val suffix = if (dark) "dark" else "light"
                    render(out, "${type.id}__${bp.name.lowercase()}__$suffix", type, bp, dark, bp.widthDp, bp.heightDp)
                }
            }
        }
    }

    /** Sizes a launcher can actually hand us that are not the design canvas. */
    @Test
    fun awkwardLauncherSizes() {
        val out = File("build/audit-tiles/awkward").apply { mkdirs() }
        // width x height in dp, with the breakpoint forSize() would pick.
        val cases = listOf(
            Triple("4x1_short_wide", 336, 80),
            Triple("2x1_tiny", 158, 80),
            Triple("2x2_small_phone", 158, 158),
            Triple("4x2_pixel", 320, 150),
            Triple("5x2_wide", 440, 150),
            Triple("4x4_pixel", 320, 330),
            Triple("4x3_squat", 320, 240),
            Triple("hero_tablet", 600, 380),
            Triple("hero_short_wide", 620, 300),
        )
        cases.forEach { (name, w, h) ->
            val bp = WidgetBreakpoint.forSize(w.toFloat(), h.toFloat())
            WidgetType.entries.forEach { type ->
                render(out, "${name}__${bp.name.lowercase()}__${type.id}", type, bp, false, w, h)
            }
        }
    }

    /** Largest accessibility font scale, where copy is most likely to be cut. */
    @Test
    fun largestFontScale() {
        val out = File("build/audit-tiles/fontscale").apply { mkdirs() }
        context.resources.configuration.fontScale = 1.6f
        listOf(WidgetBreakpoint.COMPACT, WidgetBreakpoint.STANDARD, WidgetBreakpoint.EXPANDED).forEach { bp ->
            WidgetType.entries.forEach { type ->
                render(out, "fs16__${bp.name.lowercase()}__${type.id}", type, bp, false, bp.widthDp, bp.heightDp)
            }
        }
    }
}
