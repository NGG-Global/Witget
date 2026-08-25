package com.softdread.widgets.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.softdread.widgets.R
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Calibration dump for the drawn wordmark: renders "witget" in Baloo with the
 * coral dot over the i and the amber bowl under the g at baseline-relative em
 * offsets, so the constants in WitgetWordmark are measured against the real
 * font rather than guessed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordmarkCalibration {

    @Test
    fun measureGlyphAnchors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = ResourcesCompat.getFont(context, R.font.baloo2) ?: Typeface.DEFAULT
        val typeface = Typeface.create(base, 700, false)
        val size = 400f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = 0xFF000000.toInt()
        }
        val baseline = 500f

        fun inkBounds(glyph: String, yFrom: Float, yTo: Float, interior: Boolean): FloatArray {
            val w = paint.measureText(glyph).toInt() + 40
            val bmp = Bitmap.createBitmap(w, 700, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFFFFFFFF.toInt())
            c.drawText(glyph, 20f, baseline, paint)
            var minX = Float.MAX_VALUE; var maxX = 0f; var minY = Float.MAX_VALUE; var maxY = 0f
            for (y in yFrom.toInt() until yTo.toInt()) {
                var leftInk = -1; var rightInk = -1
                for (x in 0 until w) {
                    val ink = (bmp.getPixel(x, y) and 0xFF) < 128
                    if (ink) { if (leftInk < 0) leftInk = x; rightInk = x }
                }
                for (x in 0 until w) {
                    val ink = (bmp.getPixel(x, y) and 0xFF) < 128
                    val hit = if (interior) (!ink && leftInk in 0 until x && x < rightInk) else ink
                    if (hit) {
                        if (x < minX) minX = x.toFloat()
                        if (x > maxX) maxX = x.toFloat()
                        if (y < minY) minY = y.toFloat()
                        if (y > maxY) maxY = y.toFloat()
                    }
                }
            }
            return floatArrayOf(minX - 20f, minY, maxX - 20f, maxY)
        }

        // The i's tittle: ink above the x-height zone.
        val tittle = inkBounds("i", baseline - size * 0.95f, baseline - size * 0.55f, interior = false)
        val iAdvance = paint.measureText("i")
        println("TITTLE cx=${((tittle[0] + tittle[2]) / 2f) / iAdvance} " +
            "cyEm=${(baseline - (tittle[1] + tittle[3]) / 2f) / size} " +
            "rEm=${(tittle[2] - tittle[0]) / 2f / size}")

        // The g's counter: enclosed background inside the bowl.
        val counter = inkBounds("g", baseline - size * 0.52f, baseline.toFloat(), interior = true)
        val gAdvance = paint.measureText("g")
        println("COUNTER cx=${((counter[0] + counter[2]) / 2f) / gAdvance} " +
            "cyEm=${(baseline - (counter[1] + counter[3]) / 2f) / size} " +
            "rEm=${minOf(counter[2] - counter[0], counter[3] - counter[1]) / 2f / size}")
    }

    @Test
    fun dump() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val base = ResourcesCompat.getFont(context, R.font.baloo2) ?: Typeface.DEFAULT
        val typeface = Typeface.create(base, 700, false)
        val size = 160f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = 0xFF3B2521.toInt()
        }
        val text = "witget"
        val width = paint.measureText(text).toInt() + 80
        val bitmap = Bitmap.createBitmap(width, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFCEEDC.toInt())
        val x = 40f
        val baseline = 200f

        // Amber bowl under the g's counter, drawn first so the glyph overprints.
        val gStart = x + paint.measureText(text, 0, 3)
        val gWidth = paint.measureText("g")
        val bowl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0B058.toInt() }
        canvas.drawCircle(gStart + gWidth * 0.507f, baseline - size * 0.2125f, size * 0.1265f, bowl)

        canvas.drawText(text, x, baseline, paint)

        // Coral dot over the i's tittle, drawn last so it replaces it.
        val iStart = x + paint.measureText("w")
        val iWidth = paint.measureText("i")
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCC6848.toInt() }
        canvas.drawCircle(iStart + iWidth * 0.546f, baseline - size * 0.624f, size * 0.104f, dot)

        val out = File("build/tile-snapshots").apply { mkdirs() }
        FileOutputStream(File(out, "wordmark.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
