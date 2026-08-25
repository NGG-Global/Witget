package com.softdread.widgets.widgets.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where an oversized circle is anchored before it is cropped by the tile edge. */
enum class CircleAnchor { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END, CENTER }

/**
 * One oversized circle, expressed the way the design sheet specifies it: as a
 * fraction of tile width, anchored so at least one edge crops it.
 */
data class CircleSpec(
    val colour: Color,
    /** Diameter as a fraction of the tile's short side. The sheet allows 0.55-1.7. */
    val diameterRatio: Float,
    val anchor: CircleAnchor,
    /** How far past the anchored edge the circle sits, as a fraction of the short side. */
    val overhangRatio: Float = 0.25f,
)

/** The optional single satellite: diameter never above 0.4x the tile's short side. */
data class SatelliteSpec(
    val colour: Color,
    val diameterRatio: Float,
    /** Centre position as fractions of tile width and height. */
    val centreXRatio: Float,
    val centreYRatio: Float,
)

/**
 * Renders the pack's graphic language into bitmaps for Glance.
 *
 * Why bitmaps: the design sheet's tile is a flat colour field with one
 * oversized circle cropped by an edge, plus a ring for data. Glance/RemoteViews
 * has no shape primitive that can position an overflowing circle, and its
 * `cornerRadius` modifier is a no-op below API 31, so drawing the field art into
 * a bitmap is the supported route rather than a hack. Text is never drawn into
 * these bitmaps — it stays real [androidx.glance.text.Text] so font scaling and
 * TalkBack keep working.
 *
 * Two things keep the payload small enough for RemoteViews:
 *  - bitmaps are capped at [MAX_DIMENSION] on the longest side and stretched by
 *    Glance with `FillBounds`; because the bitmap carries the tile's aspect
 *    ratio, circles stay circular;
 *  - on API 31+ the tile's rounded corners are clipped natively by the host, so
 *    the field bitmap needs no alpha channel and is drawn as `RGB_565`, halving
 *    its size.
 */
object TileArt {

    /**
     * Longest-side cap. 512 px upscales to a 368 dp tile on a 3x display with a
     * circle edge softness of about two display pixels, which is invisible at
     * arm's length, while keeping a square field bitmap at ~0.5 MB.
     */
    private const val MAX_DIMENSION = 512

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** True when the widget host clips the tile's rounded corners for us. */
    private val hostClipsCorners: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * The tile field: flat colour, one cropped circle, an optional satellite and
     * the 1px inset stroke that cream tiles need to hold an edge on pale
     * wallpapers.
     */
    fun background(
        widthPx: Int,
        heightPx: Int,
        cornerRadiusPx: Float,
        surface: Color,
        circle: CircleSpec?,
        satellite: SatelliteSpec? = null,
        insetStroke: Color? = null,
    ): Bitmap {
        val (width, height) = scaled(widthPx, heightPx)
        val scale = width.toFloat() / max(1, widthPx)
        val radius = cornerRadiusPx * scale
        val key = "bg:$width:$height:$radius:${surface.value}:${circle?.key()}:${satellite?.key()}:" +
            "${insetStroke?.value}:$hostClipsCorners"
        cache.get(key)?.let { return it }

        val needsAlpha = !hostClipsCorners
        val bitmap = createBitmap(
            width,
            height,
            if (needsAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565,
        )
        drawBackgroundInto(Canvas(bitmap), width, height, radius, surface, circle, satellite, insetStroke)
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Draws the field directly into [canvas] at native resolution — the
     * full-tile renderer uses this so text drawn on top stays crisp instead of
     * inheriting an upscaled background's softness.
     */
    fun drawBackgroundInto(
        canvas: Canvas,
        width: Int,
        height: Int,
        radius: Float,
        surface: Color,
        circle: CircleSpec?,
        satellite: SatelliteSpec? = null,
        insetStroke: Color? = null,
    ) {
        val needsAlpha = !hostClipsCorners
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Field. Below API 31 the rounded corners are baked in; from API 31 the
        // host clips them and a plain fill is both crisper and cheaper.
        paint.color = surface.toArgb()
        if (needsAlpha) {
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        } else {
            canvas.drawColor(surface.toArgb())
        }

        // Clip subsequent art to the tile so the circle is cropped, never spilled.
        // Circle geometry is measured against the tile's short side, so a wide
        // 4x2 tile gets the same visual circle weight as a square 2x2 one.
        val reference = min(width, height).toFloat()
        canvas.withSave {
            if (needsAlpha) {
                val path = Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        radius,
                        radius,
                        Path.Direction.CW,
                    )
                }
                clipPath(path)
            }

            circle?.let { spec ->
                val diameter = reference * spec.diameterRatio
                val overhang = reference * spec.overhangRatio
                val (centreX, centreY) = anchorCentre(
                    spec.anchor,
                    width.toFloat(),
                    height.toFloat(),
                    diameter,
                    overhang,
                )
                paint.color = spec.colour.toArgb()
                drawCircle(centreX, centreY, diameter / 2f, paint)
            }

            satellite?.let { spec ->
                paint.color = spec.colour.toArgb()
                drawCircle(
                    width * spec.centreXRatio,
                    height * spec.centreYRatio,
                    reference * spec.diameterRatio / 2f,
                    paint,
                )
            }
        }

        insetStroke?.let { stroke ->
            paint.style = Paint.Style.STROKE
            // Roughly 1dp at the drawn resolution: the field is drawn either at
            // its capped cache size or at native pixels; a ~1/400th-of-width
            // stroke matches the sheet's hairline at both.
            paint.strokeWidth = max(1f, width / 400f)
            paint.color = stroke.toArgb()
            val inset = paint.strokeWidth / 2f
            canvas.drawRoundRect(
                RectF(inset, inset, width - inset, height - inset),
                radius,
                radius,
                paint,
            )
            paint.style = Paint.Style.FILL
        }
    }

    /**
     * The 8 ball: a near-black sphere with the classic white number disc. The
     * "8" itself is real text drawn by the renderer, so it uses the pack's face.
     */
    fun eightBall(sizePx: Int, ballColour: Color, discColour: Color): Bitmap {
        val size = min(sizePx, MAX_DIMENSION).coerceAtLeast(24)
        val key = "ball:$size:${ballColour.value}:${discColour.value}"
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ballColour.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = discColour.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size * 0.27f, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * The data circle. The sheet draws it as a conic gradient; Glance cannot
     * express one, so it is drawn here as a stroked arc, which produces the same
     * two-tone ring with a crisp boundary.
     */
    fun ring(
        sizePx: Int,
        fraction: Float,
        trackColour: Color,
        fillColour: Color,
        strokeRatio: Float = 0.26f,
        centreColour: Color? = null,
    ): Bitmap {
        val size = min(sizePx, MAX_DIMENSION).coerceAtLeast(16)
        val safeFraction = fraction.coerceIn(0f, 1f)
        val key = "ring:$size:${(safeFraction * 1000).roundToInt()}:${trackColour.value}:" +
            "${fillColour.value}:$strokeRatio:${centreColour?.value}"
        cache.get(key)?.let { return it }

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val stroke = size * strokeRatio
        val inset = stroke / 2f
        val bounds = RectF(inset, inset, size - inset, size - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }

        paint.color = trackColour.toArgb()
        canvas.drawArc(bounds, 0f, 360f, false, paint)

        if (safeFraction > 0f) {
            paint.color = fillColour.toArgb()
            // Start at twelve o'clock and run clockwise, matching the sheet.
            canvas.drawArc(bounds, -90f, 360f * safeFraction, false, paint)
        }

        centreColour?.let {
            paint.style = Paint.Style.FILL
            paint.color = it.toArgb()
            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - stroke, paint)
        }

        cache.put(key, bitmap)
        return bitmap
    }

    /** A plain filled disc: the weather sky satellite and the 8 ball's disc. */
    fun disc(sizePx: Int, colour: Color): Bitmap {
        val size = min(sizePx, MAX_DIMENSION).coerceAtLeast(8)
        val key = "disc:$size:${colour.value}"
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(size, size)
        Canvas(bitmap).drawCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toArgb() },
        )
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Day Vibe's meeting dots: filled for booked meetings, tinted for the rest.
     * The sheet caps the row at eight dots.
     */
    fun dots(
        widthPx: Int,
        heightPx: Int,
        filled: Int,
        total: Int,
        dotColour: Color,
        emptyColour: Color,
        perRow: Int = 4,
    ): Bitmap {
        val width = min(widthPx, MAX_DIMENSION).coerceAtLeast(16)
        val height = min(heightPx, MAX_DIMENSION).coerceAtLeast(8)
        val safeTotal = total.coerceIn(1, 8)
        val safeFilled = filled.coerceIn(0, safeTotal)
        val key = "dots:$width:$height:$safeFilled:$safeTotal:${dotColour.value}:${emptyColour.value}:$perRow"
        cache.get(key)?.let { return it }

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val columns = min(perRow, safeTotal)
        val rows = (safeTotal + columns - 1) / columns
        val cellWidth = width.toFloat() / columns
        val cellHeight = height.toFloat() / rows
        val radius = min(cellWidth, cellHeight) * 0.34f

        for (index in 0 until safeTotal) {
            val column = index % columns
            val row = index / columns
            paint.color = (if (index < safeFilled) dotColour else emptyColour).toArgb()
            canvas.drawCircle(
                cellWidth * column + cellWidth / 2f,
                cellHeight * row + cellHeight / 2f,
                radius,
                paint,
            )
        }
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * A rounded progress bar — the pack's one non-circular graphic, per the
     * Time Progress note in the sheet.
     */
    fun bar(
        widthPx: Int,
        heightPx: Int,
        fraction: Float,
        trackColour: Color,
        fillColour: Color,
    ): Bitmap {
        val width = min(widthPx, MAX_DIMENSION).coerceAtLeast(8)
        val height = min(heightPx, MAX_DIMENSION).coerceAtLeast(4)
        val safeFraction = fraction.coerceIn(0f, 1f)
        val key = "bar:$width:$height:${(safeFraction * 1000).roundToInt()}:${trackColour.value}:${fillColour.value}"
        cache.get(key)?.let { return it }

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = height / 2f

        paint.color = trackColour.toArgb()
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)

        if (safeFraction > 0f) {
            // Never render a fill narrower than its own cap radius, or the bar
            // reads as a dot at low percentages.
            val fillWidth = max(height.toFloat(), width * safeFraction)
            paint.color = fillColour.toArgb()
            canvas.drawRoundRect(RectF(0f, 0f, fillWidth, height.toFloat()), radius, radius, paint)
        }
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * The design sheet's segmented strip: a row of equal rounded bars whose
     * opacity carries a value. Weather uses it for rain probability over the
     * next few hours.
     */
    fun strip(
        widthPx: Int,
        heightPx: Int,
        values: List<Float>,
        colour: Color,
        trackColour: Color,
        gapPx: Float,
    ): Bitmap {
        val width = min(widthPx, MAX_DIMENSION).coerceAtLeast(8)
        val height = min(heightPx, MAX_DIMENSION).coerceAtLeast(3)
        val safeValues = values.map { it.coerceIn(0f, 1f) }
        val key = "strip:$width:$height:${colour.value}:${trackColour.value}:$gapPx:" +
            safeValues.joinToString(",") { (it * 100).roundToInt().toString() }
        cache.get(key)?.let { return it }

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = safeValues.size.coerceAtLeast(1)
        val segmentWidth = (width - gapPx * (count - 1)) / count
        val radius = height / 2f

        safeValues.forEachIndexed { index, value ->
            // The sheet varies each segment's opacity rather than its length, so
            // the strip reads as a shape first and a chart second. The lowest
            // segment sits at the track tint, so an empty hour stays visible.
            paint.color = lerp(trackColour, colour, value).toArgb()
            val left = index * (segmentWidth + gapPx)
            canvas.drawRoundRect(RectF(left, 0f, left + segmentWidth, height.toFloat()), radius, radius, paint)
        }
        cache.put(key, bitmap)
        return bitmap
    }

    /** A rounded rectangle used behind copy pills and metadata chips. */
    fun pill(widthPx: Int, heightPx: Int, cornerRadiusPx: Float, colour: Color): Bitmap {
        val width = min(widthPx, MAX_DIMENSION).coerceAtLeast(8)
        val height = min(heightPx, MAX_DIMENSION).coerceAtLeast(8)
        val key = "pill:$width:$height:$cornerRadiusPx:${colour.value}"
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadiusPx,
            cornerRadiusPx,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour.toArgb() },
        )
        cache.put(key, bitmap)
        return bitmap
    }

    private fun scaled(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val longest = max(width, height)
        if (longest <= MAX_DIMENSION) return width to height
        val factor = MAX_DIMENSION.toFloat() / longest
        return max(1, (width * factor).roundToInt()) to max(1, (height * factor).roundToInt())
    }

    private fun anchorCentre(
        anchor: CircleAnchor,
        width: Float,
        height: Float,
        diameter: Float,
        overhang: Float,
    ): Pair<Float, Float> {
        val radius = diameter / 2f
        return when (anchor) {
            CircleAnchor.TOP_START -> (radius - overhang) to (radius - overhang)
            CircleAnchor.TOP_END -> (width - radius + overhang) to (radius - overhang)
            CircleAnchor.BOTTOM_START -> (radius - overhang) to (height - radius + overhang)
            CircleAnchor.BOTTOM_END -> (width - radius + overhang) to (height - radius + overhang)
            CircleAnchor.CENTER -> (width / 2f) to (height / 2f)
        }
    }

    private fun CircleSpec.key() = "${colour.value}|$diameterRatio|$anchor|$overhangRatio"
    private fun SatelliteSpec.key() = "${colour.value}|$diameterRatio|$centreXRatio|$centreYRatio"
}
