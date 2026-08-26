package com.softdread.widgets.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation

/**
 * The motif vocabulary: every pictogram the content pack can ask for.
 *
 * A motif is the tile's visual anchor for what the copy is about — the toaster
 * joke shows toast, "blue paint" turns the field slate and shows a roller. The
 * pack curates one per joke and per screen-time equivalency in the extractor;
 * nothing is keyword-matched at runtime. The names here and the names in
 * `tools/content/extract_content.py` are the same set, and `ContentPackTest`
 * fails if they drift.
 *
 * These are not icons in the icon-font sense the design sheet forbids. Each is
 * drawn in the pack's own geometry — flat shapes, round caps, two tones — by
 * [MotifArt], the single drawing implementation both the widget renderer and
 * the in-app previews use.
 */
enum class MotifGlyph {
    ALERT, ARROW, BALL, BATTERY, BELL, BIRD, BONE, BOOK, BOX, BRANCH, BRIEFCASE,
    BROOM, BUG, BULB, BUS, CALENDAR, CHAIR, CLOCK, CLOUD, CUP, DOC, DOOR, DROP,
    DUMBBELL, FILM, FRIDGE, GAMEPAD, GRID, HOUSE, KETTLE, LAPTOP, LAUNDRY, LOCK,
    MAGNIFIER, MAIL, MIC, MICRO, MIRROR, MOON, NOTE, PAINT, PENCIL, PHONE,
    PLANE, PLANT, PLATE, POTATO, SOCK, SPEECH, SPINNER, SPIRAL, SPOON, SUN,
    TOAST, TOMATO, TV, UMBRELLA, WALK, WIFI;

    companion object {
        /** Content-pack names arrive as strings; an unknown name means no motif. */
        fun fromName(name: String?): MotifGlyph? =
            name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}

/**
 * Draws motif pictograms. One implementation: [TileRenderer] calls [draw]
 * directly on the tile canvas, and Compose previews go through [plateBitmap]
 * so the app shows exactly what the widget draws.
 *
 * Drawing conventions, so the set reads as one family:
 * - the glyph lives in a box of half-extent `r` around (cx, cy);
 * - strokes are `0.16 * r`, round caps and joins, in `ink`;
 * - the body mass is a flat `accent` fill (the tile's own field colour, so the
 *   pictogram is visibly of its tile), details and outlines are `ink`;
 * - no gradients, no shadows, nothing smaller than a dot the eye can resolve
 *   at 40dp.
 */
object MotifArt {

    fun draw(canvas: Canvas, glyph: MotifGlyph, cx: Float, cy: Float, r: Float, ink: Int, accent: Int) {
        G(canvas, cx, cy, r, ink, accent).draw(glyph)
    }

    /** The plate as previews consume it: a disc plus the glyph, as a bitmap. */
    fun plateBitmap(sizePx: Int, glyph: MotifGlyph, plate: Int, ink: Int, accent: Int): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
        canvas.drawCircle(half, half, half, paint)
        draw(canvas, glyph, half, half, half * GLYPH_OF_PLATE, ink, accent)
        return bitmap
    }

    /** Glyph half-extent as a fraction of the plate radius. */
    const val GLYPH_OF_PLATE = 0.56f

    // ------------------------------------------------------------------ dsl

    private class G(val c: Canvas, val cx: Float, val cy: Float, val r: Float, ink: Int, accent: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.FILL }
        val fillAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; style = Paint.Style.STROKE; strokeWidth = r * 0.16f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val strokeAccent = Paint(stroke).apply { color = accent }

        fun x(f: Float) = cx + f * r
        fun y(f: Float) = cy + f * r
        fun oval(l: Float, t: Float, rt: Float, b: Float) = RectF(x(l), y(t), x(rt), y(b))
        fun circle(fx: Float, fy: Float, fr: Float, p: Paint) = c.drawCircle(x(fx), y(fy), fr * r, p)
        fun rr(l: Float, t: Float, rt: Float, b: Float, rad: Float, p: Paint) =
            c.drawRoundRect(oval(l, t, rt, b), rad * r, rad * r, p)
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) =
            c.drawLine(x(x0), y(y0), x(x1), y(y1), p)
        fun arc(l: Float, t: Float, rt: Float, b: Float, start: Float, sweep: Float, p: Paint) =
            c.drawArc(oval(l, t, rt, b), start, sweep, false, p)
        fun path(p: Paint, block: Path.(G) -> Unit) {
            val path = Path(); path.block(this); c.drawPath(path, p)
        }
        fun poly(p: Paint, vararg pts: Float) = path(p) { g ->
            moveTo(g.x(pts[0]), g.y(pts[1]))
            for (i in 2 until pts.size step 2) lineTo(g.x(pts[i]), g.y(pts[i + 1]))
            close()
        }
        fun rotated(degrees: Float, block: G.() -> Unit) = c.withRotation(degrees, cx, cy) { block() }

        fun draw(glyph: MotifGlyph) {
            when (glyph) {
                MotifGlyph.ALERT -> {
                    poly(stroke, 0f, -0.62f, 0.66f, 0.56f, -0.66f, 0.56f)
                    line(0f, -0.18f, 0f, 0.16f, strokeAccent)
                    circle(0f, 0.38f, 0.07f, fillAccent)
                }
                MotifGlyph.ARROW -> {
                    line(-0.34f, 0.5f, -0.34f, -0.42f, stroke)
                    poly(fill, -0.34f, -0.66f, -0.14f, -0.32f, -0.54f, -0.32f)
                    line(0.34f, -0.5f, 0.34f, 0.42f, strokeAccent)
                    poly(fillAccent, 0.34f, 0.66f, 0.14f, 0.32f, 0.54f, 0.32f)
                }
                MotifGlyph.BALL -> {
                    circle(0f, 0f, 0.62f, stroke)
                    line(-0.62f, 0f, 0.62f, 0f, strokeAccent)
                    arc(-0.31f, -0.62f, 0.31f, 0.62f, -90f, 180f, strokeAccent)
                }
                MotifGlyph.BATTERY -> {
                    rr(-0.66f, -0.3f, 0.48f, 0.3f, 0.1f, stroke)
                    rr(0.52f, -0.12f, 0.66f, 0.12f, 0.05f, fill)
                    rr(-0.52f, -0.16f, 0.02f, 0.16f, 0.07f, fillAccent)
                }
                MotifGlyph.BELL -> {
                    path(fillAccent) { g ->
                        moveTo(g.x(-0.5f), g.y(0.32f))
                        cubicTo(g.x(-0.56f), g.y(-0.56f), g.x(0.56f), g.y(-0.56f), g.x(0.5f), g.y(0.32f))
                        close()
                    }
                    circle(0f, -0.52f, 0.08f, fill)
                    line(-0.62f, 0.32f, 0.62f, 0.32f, stroke)
                    circle(0f, 0.52f, 0.1f, fill)
                }
                MotifGlyph.BIRD -> {
                    circle(0.02f, 0.12f, 0.4f, fillAccent)
                    circle(0.34f, -0.26f, 0.22f, fillAccent)
                    poly(fill, 0.52f, -0.34f, 0.72f, -0.24f, 0.52f, -0.14f)
                    circle(0.38f, -0.3f, 0.05f, fill)
                    line(-0.08f, 0.5f, -0.08f, 0.68f, stroke)
                    line(0.14f, 0.5f, 0.14f, 0.68f, stroke)
                }
                MotifGlyph.BONE -> rotated(-24f) {
                    rr(-0.4f, -0.12f, 0.4f, 0.12f, 0.1f, fillAccent)
                    circle(-0.42f, -0.14f, 0.16f, fillAccent); circle(-0.42f, 0.14f, 0.16f, fillAccent)
                    circle(0.42f, -0.14f, 0.16f, fillAccent); circle(0.42f, 0.14f, 0.16f, fillAccent)
                }
                MotifGlyph.BOOK -> {
                    rr(-0.52f, -0.62f, 0.52f, 0.62f, 0.12f, stroke)
                    line(-0.24f, -0.62f, -0.24f, 0.62f, stroke)
                    poly(fillAccent, 0.14f, -0.62f, 0.4f, -0.62f, 0.4f, -0.1f, 0.27f, -0.24f, 0.14f, -0.1f)
                }
                MotifGlyph.BOX -> {
                    poly(fillAccent, -0.56f, -0.16f, 0f, -0.46f, 0.56f, -0.16f, 0f, 0.14f)
                    poly(stroke, -0.56f, -0.16f, 0f, -0.46f, 0.56f, -0.16f, 0.56f, 0.34f, 0f, 0.64f, -0.56f, 0.34f)
                    line(0f, 0.14f, 0f, 0.64f, stroke)
                }
                MotifGlyph.BRANCH -> {
                    line(-0.3f, -0.36f, -0.3f, 0.36f, stroke)
                    path(stroke) { g ->
                        moveTo(g.x(-0.3f), g.y(0.1f))
                        cubicTo(g.x(-0.3f), g.y(-0.26f), g.x(0.36f), g.y(-0.02f), g.x(0.36f), g.y(-0.3f))
                    }
                    circle(-0.3f, -0.52f, 0.15f, fillAccent)
                    circle(-0.3f, 0.52f, 0.15f, fill)
                    circle(0.36f, -0.46f, 0.15f, fillAccent)
                }
                MotifGlyph.BRIEFCASE -> {
                    rr(-0.6f, -0.24f, 0.6f, 0.56f, 0.12f, stroke)
                    arc(-0.2f, -0.5f, 0.2f, -0.02f, 180f, 180f, stroke)
                    circle(0f, 0.14f, 0.09f, fillAccent)
                }
                MotifGlyph.BROOM -> {
                    line(0.5f, -0.7f, 0.02f, -0.02f, stroke)
                    poly(fillAccent, 0.1f, 0.02f, -0.06f, -0.1f, -0.56f, 0.52f, -0.18f, 0.7f)
                    line(-0.36f, 0.32f, -0.22f, 0.5f, stroke)
                }
                MotifGlyph.BUG -> {
                    c.drawOval(oval(-0.3f, -0.3f, 0.3f, 0.5f), fillAccent)
                    circle(0f, -0.42f, 0.16f, fill)
                    line(0f, -0.24f, 0f, 0.44f, stroke)
                    line(-0.3f, -0.1f, -0.52f, -0.22f, stroke); line(0.3f, -0.1f, 0.52f, -0.22f, stroke)
                    line(-0.3f, 0.2f, -0.54f, 0.28f, stroke); line(0.3f, 0.2f, 0.54f, 0.28f, stroke)
                }
                MotifGlyph.BULB -> {
                    circle(0f, -0.14f, 0.42f, stroke)
                    circle(0f, -0.14f, 0.13f, fillAccent)
                    rr(-0.18f, 0.34f, 0.18f, 0.6f, 0.08f, fillAccent)
                }
                MotifGlyph.BUS -> {
                    rr(-0.62f, -0.5f, 0.62f, 0.34f, 0.14f, stroke)
                    rr(-0.46f, -0.34f, 0.46f, -0.04f, 0.07f, fillAccent)
                    circle(-0.34f, 0.44f, 0.13f, fill); circle(0.34f, 0.44f, 0.13f, fill)
                }
                MotifGlyph.CALENDAR -> {
                    rr(-0.6f, -0.5f, 0.6f, 0.6f, 0.12f, stroke)
                    rr(-0.6f, -0.5f, 0.6f, -0.14f, 0.12f, fillAccent)
                    line(-0.3f, -0.68f, -0.3f, -0.36f, stroke); line(0.3f, -0.68f, 0.3f, -0.36f, stroke)
                    circle(0.24f, 0.28f, 0.09f, fill)
                }
                MotifGlyph.CHAIR -> {
                    line(-0.4f, -0.66f, -0.4f, 0.66f, stroke)
                    line(-0.4f, 0.12f, 0.44f, 0.12f, strokeAccent)
                    line(0.44f, 0.12f, 0.44f, 0.66f, stroke)
                    line(-0.4f, -0.6f, 0.22f, -0.6f, stroke)
                }
                MotifGlyph.CLOCK -> {
                    circle(0f, 0f, 0.64f, stroke)
                    line(0f, 0f, 0f, -0.38f, stroke)
                    line(0f, 0f, 0.3f, 0.12f, strokeAccent)
                    circle(0f, 0f, 0.07f, fillAccent)
                }
                MotifGlyph.CLOUD -> {
                    circle(-0.3f, 0.14f, 0.28f, fillAccent)
                    circle(0.08f, -0.1f, 0.36f, fillAccent)
                    circle(0.4f, 0.18f, 0.24f, fillAccent)
                    rr(-0.3f, 0.1f, 0.4f, 0.42f, 0.16f, fillAccent)
                }
                MotifGlyph.CUP -> {
                    rr(-0.44f, -0.26f, 0.3f, 0.54f, 0.14f, stroke)
                    arc(0.22f, -0.14f, 0.66f, 0.3f, -70f, 140f, stroke)
                    arc(-0.28f, -0.62f, -0.04f, -0.38f, 120f, 180f, strokeAccent)
                    arc(0.02f, -0.62f, 0.26f, -0.38f, 120f, 180f, strokeAccent)
                }
                MotifGlyph.DOC -> {
                    rr(-0.48f, -0.64f, 0.48f, 0.64f, 0.12f, stroke)
                    line(-0.26f, -0.28f, 0.26f, -0.28f, strokeAccent)
                    line(-0.26f, 0.02f, 0.26f, 0.02f, stroke)
                    line(-0.26f, 0.32f, 0.06f, 0.32f, stroke)
                }
                MotifGlyph.DOOR -> {
                    rr(-0.4f, -0.66f, 0.4f, 0.66f, 0.1f, stroke)
                    circle(0.2f, 0.04f, 0.08f, fillAccent)
                    line(-0.58f, 0.66f, 0.58f, 0.66f, stroke)
                }
                MotifGlyph.DROP -> {
                    circle(0f, 0.2f, 0.44f, fillAccent)
                    poly(fillAccent, -0.31f, -0.06f, 0.31f, -0.06f, 0f, -0.66f)
                    circle(0.14f, 0.26f, 0.08f, fill)
                }
                MotifGlyph.DUMBBELL -> {
                    line(-0.4f, 0f, 0.4f, 0f, stroke)
                    rr(-0.58f, -0.3f, -0.36f, 0.3f, 0.08f, fillAccent)
                    rr(0.36f, -0.3f, 0.58f, 0.3f, 0.08f, fillAccent)
                    rr(-0.72f, -0.18f, -0.6f, 0.18f, 0.05f, fill)
                    rr(0.6f, -0.18f, 0.72f, 0.18f, 0.05f, fill)
                }
                MotifGlyph.FILM -> {
                    rr(-0.58f, -0.18f, 0.58f, 0.56f, 0.1f, fillAccent)
                    rr(-0.58f, -0.5f, 0.58f, -0.18f, 0.08f, fill)
                    line(-0.34f, -0.5f, -0.2f, -0.18f, strokeAccent)
                    line(0.06f, -0.5f, 0.2f, -0.18f, strokeAccent)
                }
                MotifGlyph.FRIDGE -> {
                    rr(-0.42f, -0.68f, 0.42f, 0.68f, 0.12f, stroke)
                    line(-0.42f, -0.16f, 0.42f, -0.16f, stroke)
                    line(0.22f, -0.46f, 0.22f, -0.3f, strokeAccent)
                    line(0.22f, 0.02f, 0.22f, 0.28f, strokeAccent)
                }
                MotifGlyph.GAMEPAD -> {
                    rr(-0.64f, -0.3f, 0.64f, 0.3f, 0.3f, stroke)
                    line(-0.42f, 0f, -0.18f, 0f, stroke)
                    line(-0.3f, -0.12f, -0.3f, 0.12f, stroke)
                    circle(0.26f, -0.08f, 0.08f, fillAccent)
                    circle(0.44f, 0.08f, 0.08f, fillAccent)
                }
                MotifGlyph.GRID -> {
                    rr(-0.58f, -0.58f, 0.58f, 0.58f, 0.1f, stroke)
                    line(-0.19f, -0.58f, -0.19f, 0.58f, stroke); line(0.19f, -0.58f, 0.19f, 0.58f, stroke)
                    line(-0.58f, -0.19f, 0.58f, -0.19f, stroke); line(-0.58f, 0.19f, 0.58f, 0.19f, stroke)
                    circle(-0.38f, -0.38f, 0.08f, fillAccent)
                }
                MotifGlyph.HOUSE -> {
                    poly(stroke, -0.66f, 0f, 0f, -0.58f, 0.66f, 0f)
                    rr(-0.48f, 0f, 0.48f, 0.62f, 0.06f, stroke)
                    rr(-0.12f, 0.28f, 0.12f, 0.62f, 0.05f, fillAccent)
                }
                MotifGlyph.KETTLE -> {
                    path(fillAccent) { g ->
                        moveTo(g.x(-0.44f), g.y(0.56f))
                        cubicTo(g.x(-0.56f), g.y(-0.34f), g.x(0.56f), g.y(-0.34f), g.x(0.44f), g.y(0.56f))
                        close()
                    }
                    line(-0.44f, 0.56f, 0.44f, 0.56f, stroke)
                    arc(-0.26f, -0.66f, 0.26f, -0.14f, 180f, 180f, stroke)
                    poly(fill, 0.42f, -0.08f, 0.68f, -0.26f, 0.48f, 0.14f)
                }
                MotifGlyph.LAPTOP -> {
                    rr(-0.52f, -0.54f, 0.52f, 0.22f, 0.1f, stroke)
                    poly(fillAccent, -0.66f, 0.44f, 0.66f, 0.44f, 0.52f, 0.22f, -0.52f, 0.22f)
                }
                MotifGlyph.LAUNDRY -> {
                    rr(-0.52f, -0.6f, 0.52f, 0.6f, 0.14f, stroke)
                    line(-0.52f, -0.34f, 0.52f, -0.34f, stroke)
                    circle(0.3f, -0.47f, 0.06f, fillAccent)
                    circle(0f, 0.12f, 0.3f, strokeAccent)
                }
                MotifGlyph.LOCK -> {
                    arc(-0.3f, -0.66f, 0.3f, -0.02f, 180f, 180f, stroke)
                    rr(-0.48f, -0.18f, 0.48f, 0.6f, 0.13f, fillAccent)
                    circle(0f, 0.12f, 0.09f, fill)
                    line(0f, 0.16f, 0f, 0.36f, stroke)
                }
                MotifGlyph.MAGNIFIER -> {
                    circle(-0.14f, -0.14f, 0.4f, stroke)
                    line(0.18f, 0.18f, 0.56f, 0.56f, stroke)
                    arc(-0.42f, -0.42f, -0.02f, -0.02f, 150f, 110f, strokeAccent)
                }
                MotifGlyph.MAIL -> {
                    rr(-0.62f, -0.44f, 0.62f, 0.44f, 0.12f, stroke)
                    path(strokeAccent) { g ->
                        moveTo(g.x(-0.58f), g.y(-0.36f)); lineTo(g.x(0f), g.y(0.1f)); lineTo(g.x(0.58f), g.y(-0.36f))
                    }
                }
                MotifGlyph.MIC -> {
                    rr(-0.17f, -0.64f, 0.17f, 0.06f, 0.17f, fillAccent)
                    arc(-0.36f, -0.5f, 0.36f, 0.24f, 0f, 180f, stroke)
                    line(0f, 0.24f, 0f, 0.5f, stroke)
                    line(-0.22f, 0.5f, 0.22f, 0.5f, stroke)
                }
                MotifGlyph.MICRO -> {
                    rr(-0.66f, -0.42f, 0.66f, 0.42f, 0.12f, stroke)
                    rr(-0.48f, -0.24f, 0.16f, 0.24f, 0.07f, fillAccent)
                    circle(0.42f, -0.1f, 0.06f, fill)
                    circle(0.42f, 0.12f, 0.06f, fill)
                }
                MotifGlyph.MIRROR -> {
                    c.drawOval(oval(-0.4f, -0.68f, 0.4f, 0.4f), stroke)
                    line(0f, 0.4f, 0f, 0.62f, stroke)
                    line(-0.26f, 0.62f, 0.26f, 0.62f, stroke)
                    line(-0.14f, -0.4f, 0.08f, -0.14f, strokeAccent)
                }
                MotifGlyph.MOON -> {
                    val a = Path().apply { addCircle(x(-0.06f), y(0f), 0.58f * r, Path.Direction.CW) }
                    val b = Path().apply { addCircle(x(0.3f), y(-0.12f), 0.5f * r, Path.Direction.CW) }
                    a.op(b, Path.Op.DIFFERENCE)
                    c.drawPath(a, fillAccent)
                    circle(0.42f, 0.3f, 0.06f, fill)
                }
                MotifGlyph.NOTE -> {
                    c.drawOval(oval(-0.44f, 0.28f, -0.04f, 0.62f), fillAccent)
                    line(-0.06f, 0.44f, -0.06f, -0.54f, stroke)
                    path(stroke) { g ->
                        moveTo(g.x(-0.06f), g.y(-0.54f))
                        cubicTo(g.x(0.3f), g.y(-0.46f), g.x(0.34f), g.y(-0.2f), g.x(0.14f), g.y(-0.08f))
                    }
                }
                MotifGlyph.PAINT -> {
                    rr(-0.56f, -0.52f, 0.3f, -0.12f, 0.1f, fillAccent)
                    path(stroke) { g ->
                        moveTo(g.x(0.3f), g.y(-0.32f)); lineTo(g.x(0.52f), g.y(-0.32f))
                        lineTo(g.x(0.52f), g.y(0.1f)); lineTo(g.x(0.36f), g.y(0.1f)); lineTo(g.x(0.36f), g.y(0.42f))
                    }
                    circle(-0.14f, 0.22f, 0.1f, fillAccent)
                    poly(fillAccent, -0.21f, 0.17f, -0.07f, 0.17f, -0.14f, 0f)
                }
                MotifGlyph.PENCIL -> rotated(-45f) {
                    rr(-0.13f, -0.62f, 0.13f, 0.3f, 0.06f, fillAccent)
                    poly(fill, -0.13f, 0.3f, 0.13f, 0.3f, 0f, 0.6f)
                    rr(-0.13f, -0.74f, 0.13f, -0.62f, 0.04f, fill)
                }
                MotifGlyph.PHONE -> {
                    rr(-0.34f, -0.66f, 0.34f, 0.66f, 0.15f, stroke)
                    line(-0.1f, -0.5f, 0.1f, -0.5f, strokeAccent)
                    circle(0f, 0.48f, 0.06f, fillAccent)
                }
                MotifGlyph.PLANE -> {
                    poly(fillAccent, 0.7f, -0.4f, -0.7f, 0.02f, -0.18f, 0.16f)
                    poly(fill, 0.7f, -0.4f, -0.18f, 0.16f, -0.02f, 0.52f)
                }
                MotifGlyph.PLANT -> {
                    poly(fill, -0.28f, 0.24f, 0.28f, 0.24f, 0.2f, 0.66f, -0.2f, 0.66f)
                    line(0f, 0.24f, 0f, -0.14f, stroke)
                    circle(-0.2f, -0.28f, 0.17f, fillAccent)
                    circle(0.2f, -0.32f, 0.17f, fillAccent)
                    circle(0f, -0.48f, 0.16f, fillAccent)
                }
                MotifGlyph.PLATE -> {
                    circle(0f, 0f, 0.66f, stroke)
                    circle(0f, 0f, 0.4f, strokeAccent)
                    circle(0f, 0f, 0.12f, fillAccent)
                }
                MotifGlyph.POTATO -> rotated(18f) {
                    c.drawOval(oval(-0.56f, -0.36f, 0.56f, 0.36f), fillAccent)
                    circle(-0.22f, -0.08f, 0.05f, fill)
                    circle(0.14f, 0.1f, 0.05f, fill)
                    circle(0.3f, -0.12f, 0.05f, fill)
                }
                MotifGlyph.SOCK -> {
                    rr(-0.3f, -0.7f, 0.22f, 0.16f, 0.11f, fillAccent)
                    rr(-0.62f, 0.02f, 0.22f, 0.52f, 0.24f, fillAccent)
                    rr(-0.3f, -0.7f, 0.22f, -0.48f, 0.08f, fill)
                }
                MotifGlyph.SPEECH -> {
                    circle(0f, -0.08f, 0.52f, fillAccent)
                    poly(fillAccent, -0.08f, 0.36f, 0.14f, 0.68f, 0.28f, 0.3f)
                    circle(-0.22f, -0.08f, 0.06f, fill)
                    circle(0f, -0.08f, 0.06f, fill)
                    circle(0.22f, -0.08f, 0.06f, fill)
                }
                MotifGlyph.SPINNER -> {
                    arc(-0.58f, -0.58f, 0.58f, 0.58f, -60f, 300f, stroke)
                    circle(0.29f, -0.5f, 0.11f, fillAccent)
                }
                MotifGlyph.SPIRAL -> {
                    arc(-0.62f, -0.62f, 0.62f, 0.62f, -90f, 270f, stroke)
                    arc(-0.62f, -0.36f, 0.36f, 0.62f, 180f, 200f, stroke)
                    circle(0.04f, 0.1f, 0.08f, fillAccent)
                }
                MotifGlyph.SPOON -> {
                    c.drawOval(oval(-0.3f, -0.68f, 0.3f, 0.02f), fillAccent)
                    line(0f, 0.02f, 0f, 0.66f, stroke)
                }
                MotifGlyph.SUN -> {
                    circle(0f, 0f, 0.32f, fillAccent)
                    for (i in 0 until 8) rotated(i * 45f) { line(0f, -0.48f, 0f, -0.64f, stroke) }
                }
                MotifGlyph.TOAST -> {
                    path(fillAccent) { g ->
                        moveTo(g.x(-0.52f), g.y(0.6f)); lineTo(g.x(-0.52f), g.y(-0.08f))
                        cubicTo(g.x(-0.52f), g.y(-0.6f), g.x(0.52f), g.y(-0.6f), g.x(0.52f), g.y(-0.08f))
                        lineTo(g.x(0.52f), g.y(0.6f)); close()
                    }
                    circle(-0.14f, 0.14f, 0.06f, fill)
                    circle(0.18f, 0.3f, 0.06f, fill)
                }
                MotifGlyph.TOMATO -> {
                    circle(0f, 0.1f, 0.48f, fillAccent)
                    circle(-0.14f, -0.36f, 0.1f, fill)
                    circle(0.14f, -0.36f, 0.1f, fill)
                    circle(0f, -0.46f, 0.09f, fill)
                }
                MotifGlyph.TV -> {
                    rr(-0.58f, -0.5f, 0.58f, 0.34f, 0.13f, stroke)
                    rr(-0.42f, -0.36f, 0.42f, 0.2f, 0.07f, fillAccent)
                    line(-0.26f, 0.34f, -0.36f, 0.58f, stroke)
                    line(0.26f, 0.34f, 0.36f, 0.58f, stroke)
                }
                MotifGlyph.UMBRELLA -> {
                    arc(-0.64f, -0.6f, 0.64f, 0.68f, 180f, 180f, strokeAccent)
                    path(fillAccent) { g ->
                        moveTo(g.x(-0.64f), g.y(0.04f))
                        cubicTo(g.x(-0.5f), g.y(-0.66f), g.x(0.5f), g.y(-0.66f), g.x(0.64f), g.y(0.04f))
                        close()
                    }
                    line(0f, 0.04f, 0f, 0.5f, stroke)
                    arc(-0.24f, 0.38f, 0f, 0.62f, 0f, 180f, stroke)
                }
                MotifGlyph.WALK -> {
                    c.drawOval(oval(-0.5f, -0.52f, -0.1f, 0.02f), fillAccent)
                    c.drawOval(oval(-0.44f, 0.1f, -0.16f, 0.32f), fillAccent)
                    c.drawOval(oval(0.1f, 0.02f, 0.5f, 0.56f), fill)
                    c.drawOval(oval(0.16f, -0.32f, 0.44f, -0.1f), fill)
                }
                MotifGlyph.WIFI -> {
                    arc(-0.66f, -0.5f, 0.66f, 0.82f, -135f, 90f, stroke)
                    arc(-0.42f, -0.26f, 0.42f, 0.82f, -135f, 90f, stroke)
                    circle(0f, 0.4f, 0.1f, fillAccent)
                }
            }
        }
    }
}
