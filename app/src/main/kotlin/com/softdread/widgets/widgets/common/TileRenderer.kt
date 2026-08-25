package com.softdread.widgets.widgets.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.softdread.widgets.R
import com.softdread.widgets.design.TileColours
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.WidgetBreakpoint
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a complete tile — field, art and text — into one bitmap.
 *
 * Why the text moved into the bitmap: Glance resolves fonts through
 * RemoteViews, which can only name *system* families, so widgets rendered in
 * the platform sans while every preview showed the sheet's Bricolage Grotesque.
 * Drawing the whole tile ourselves puts the real face on the home screen and,
 * just as importantly, gives the renderer the circle geometry — every text
 * block is laid out inside [TileTextGuard]'s safe span, so copy can no longer
 * run across a solid cream circle and vanish.
 *
 * What accessibility keeps: the tile is a single tap target whose full
 * [TileContent.contentDescription] is set by [SoftDreadTile], so TalkBack reads
 * the real values; and every text size here is multiplied by the system font
 * scale, so large-type users get large type — the fitted hero steps absorb the
 * growth instead of clipping.
 */
object TileRenderer {

    /**
     * Long-side cap. Sized for the hero tile on a 3x tablet (560dp -> 1680px,
     * downscaled 5%, invisible); at RGB_565 a 1600x972 tile is ~3 MB, well under
     * RemoteViews' bitmap budget of ~1.5x the screen's pixel bytes, which on any
     * tablet running 3x is upwards of 20 MB.
     */
    private const val MAX_DIMENSION = 1600

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val hostClipsCorners: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun render(
        context: Context,
        role: ColourRole,
        breakpoint: WidgetBreakpoint,
        colours: TileColours,
        content: TileContent,
        widthPx: Int,
        heightPx: Int,
        densityPx: Float,
    ): Bitmap {
        val fontScale = context.resources.configuration.fontScale.coerceIn(0.8f, 1.6f)
        val (width, height) = capped(widthPx, heightPx)
        val key = listOf(
            "tile", role, breakpoint, width, height, fontScale, densityPx,
            colours.hashCode(), content.hashCode(), hostClipsCorners,
        ).joinToString(":")
        cache.get(key)?.let { return it }

        val needsAlpha = !hostClipsCorners
        val bitmap = createBitmap(width, height, if (needsAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        Pass(context, canvas, role, breakpoint, colours, content, width, height, fontScale, densityPx).draw()
        cache.put(key, bitmap)
        return bitmap
    }

    private fun capped(w: Int, h: Int): Pair<Int, Int> {
        val longest = max(w, h).coerceAtLeast(1)
        if (longest <= MAX_DIMENSION) return max(1, w) to max(1, h)
        val f = MAX_DIMENSION.toFloat() / longest
        return max(1, (w * f).roundToInt()) to max(1, (h * f).roundToInt())
    }

    // ------------------------------------------------------------------ pass

    private class Pass(
        val context: Context,
        val canvas: Canvas,
        val role: ColourRole,
        val breakpoint: WidgetBreakpoint,
        val colours: TileColours,
        val content: TileContent,
        val width: Int,
        val height: Int,
        val fontScale: Float,
        val densityPx: Float,
    ) {
        /**
         * Pixels per design-dp. Fit-based — the smaller of the width and height
         * ratios against the breakpoint's design canvas — and capped at 1.25x
         * the device density. That cap is the tablet rule: a tile larger than
         * its design size gains breathing room and longer lines, it does not
         * simply magnify its glyphs.
         */
        val unit: Float = minOf(
            width / breakpoint.widthDp.toFloat(),
            height / breakpoint.heightDp.toFloat(),
            densityPx * 1.25f,
        )

        val padH = breakpoint.paddingHorizontalDp * unit
        val padV = breakpoint.paddingVerticalDp * unit
        val contentLeft = padH
        val contentRight = width - padH

        val circleSpec = TileGeometry.circle(role, breakpoint, colours)
        val satelliteSpec = TileGeometry.satellite(role, breakpoint, colours)
        val obstacles = buildObstacles()

        fun draw() {
            TileArt.drawBackgroundInto(
                canvas, width, height,
                radius = breakpoint.cornerRadiusDp * unit,
                surface = colours.surface,
                circle = circleSpec,
                satellite = satelliteSpec,
                insetStroke = if (colours.needsInsetStroke) colours.hairline else null,
            )
            when (breakpoint) {
                WidgetBreakpoint.TINY -> tiny()
                WidgetBreakpoint.COMPACT -> compact()
                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> standard()
                WidgetBreakpoint.EXPANDED, WidgetBreakpoint.HERO -> expanded()
            }
        }

        // ------------------------------------------------------------ layouts

        private fun tiny() {
            var y = padV
            y += labelRow(y)
            val hero = content.heroValue ?: content.voice.orEmpty()
            val paint = heroPaint(hero)
            val block = layout(hero, paint, spanAt(y, y + paint.textSize * 1.1f).width, 1)
            val free = height - padV - y
            block.drawAt(contentLeft, y + max(0f, (free - block.height) / 2f))
        }

        private fun compact() {
            var y = padV
            y += labelRow(y)

            // Bottom-anchored stack: call to action, then chip, then voice.
            var bottom = height - padV
            content.callToAction?.let {
                val paint = ctaPaint()
                val block = layout(it.uppercase(Locale.getDefault()), paint, spanAt(bottom - lineOf(paint), bottom).width, 1)
                bottom -= block.height
                block.drawAt(contentLeft, bottom)
                bottom -= 6 * unit
            }
            if (content.chips.isNotEmpty()) {
                bottom -= chipRow(listOf(content.chips.first()), bottom, anchorBottom = true)
                bottom -= 7 * unit
            }
            content.voice?.let {
                val paint = voicePaint()
                val maxLines = if (content.heroValue != null || content.chips.isNotEmpty()) 3 else 4
                val estTop = bottom - lineOf(paint) * maxLines
                val block = layout(it, paint, spanAt(estTop, bottom).width, maxLines)
                bottom -= block.height
                block.drawAt(spanAt(bottom, bottom + block.height).left, bottom)
                bottom -= 8 * unit
            }

            // The middle band carries the hero row (with an optional leading mark).
            val leading = content.leading
            val ballSize = 52 * unit
            val bandTop = y
            val bandBottom = bottom
            when {
                leading != null && content.heroValue != null -> {
                    val markSize = 46 * unit
                    val paint = heroPaint(content.heroValue, besideLeading = true)
                    val rowHeight = max(markSize, paint.textSize)
                    val rowTop = bandTop + max(0f, (bandBottom - bandTop - rowHeight) / 2f)
                    drawLeading(leading, contentLeft, rowTop + (rowHeight - markSize) / 2f, markSize)
                    val textLeft = contentLeft + markSize + 10 * unit
                    val block = layout(content.heroValue, paint, contentRight - textLeft, 1)
                    block.drawAt(textLeft, rowTop + (rowHeight - block.height) / 2f)
                }
                content.heroValue != null -> {
                    val paint = heroPaint(content.heroValue)
                    val block = layout(content.heroValue, paint, spanAt(bandTop, bandBottom).width, 1)
                    block.drawAt(contentLeft, bandTop + max(0f, (bandBottom - bandTop - block.height) / 2f))
                }
                leading != null -> {
                    val rowTop = bandTop + max(0f, (bandBottom - bandTop - ballSize) / 2f)
                    drawLeading(leading, contentLeft, rowTop, ballSize)
                }
            }
        }

        private fun standard() {
            val leading = content.leading
            val markSize = 82 * unit
            var textLeft = contentLeft

            // Measure the column first so the whole row can centre vertically.
            val blocks = standardColumn(textLeft + if (leading != null && leading !is LeadingVisual.Numeral) markSize + 18 * unit else 0f)
            val columnHeight = blocks.sumOf { it.height.toDouble() }.toFloat() +
                blocks.size.let { if (it > 1) (it - 1) * 7 * unit else 0f }
            var y = max(padV, (height - columnHeight) / 2f)

            when (leading) {
                is LeadingVisual.Numeral -> {
                    val paint = heroNumeralPaint()
                    val block = layout(leading.text, paint, contentRight - contentLeft, 1)
                    block.drawAt(contentLeft, (height - block.height) / 2f)
                    textLeft = contentLeft + block.width + 18 * unit
                }
                null -> Unit
                else -> {
                    drawLeading(leading, contentLeft, (height - markSize) / 2f, markSize)
                    textLeft = contentLeft + markSize + 18 * unit
                }
            }

            val rebuilt = standardColumn(textLeft)
            y = max(padV, (height - columnHeight) / 2f)
            rebuilt.forEach { block ->
                block.drawAt(block.leftOverride ?: textLeft, y)
                y += block.height + 7 * unit
            }
        }

        /** The standard layout's text column, measured against [left]. */
        private fun standardColumn(left: Float): List<Block> {
            val blocks = mutableListOf<Block>()
            val widthAt = { top: Float, bottom: Float ->
                TileTextGuard.safeSpan(left, contentRight, top, bottom, obstacles, minTextWidth(), gapPx()).width
            }
            // Vertical positions are unknown until drawn; the guard is applied
            // with generous bands (whole tile) here, which for the standard
            // tile's side circles reduces to the same clamp.
            val fullBand = widthAt(0f, height.toFloat())

            labelBlock(fullBand)?.let { blocks += it }
            content.heroValue?.let { hero ->
                val paint = heroPaint(hero)
                blocks += rowBlock(
                    hero to paint,
                    content.heroSuffix?.let { it to metricPaint() },
                    content.metric?.let { it to metricPaint() },
                    maxWidth = fullBand,
                )
            }
            content.bars.forEach { bar -> blocks += barBlock(bar, fullBand) }
            content.voice?.let {
                val paint = if (content.heroValue == null && content.bars.isEmpty()) statementPaint() else voicePaint()
                blocks += layout(it, paint, fullBand, if (content.heroValue == null) 4 else 2)
            }
            if (content.chips.isNotEmpty()) blocks += chipsBlock(content.chips.take(2))
            content.callToAction?.let {
                blocks += layout(it.uppercase(Locale.getDefault()), ctaPaint(), fullBand, 1)
            }
            return blocks
        }

        private fun expanded() {
            var y = padV
            y += labelRow(y)

            content.leading?.let { leading ->
                if (leading !is LeadingVisual.Numeral) {
                    val hero = breakpoint == WidgetBreakpoint.HERO
                    val size = when (leading) {
                        is LeadingVisual.Ring -> (if (hero) 176 else 150) * unit
                        is LeadingVisual.EightBall -> (if (hero) 156 else 132) * unit
                        else -> (if (hero) 120 else 104) * unit
                    }
                    y += 18 * unit
                    val x = if (leading is LeadingVisual.Ring || leading is LeadingVisual.EightBall) {
                        (width - size) / 2f
                    } else {
                        contentLeft
                    }
                    drawLeading(leading, x, y, size)
                    y += size
                }
            }

            if (content.bars.isNotEmpty()) {
                y += 20 * unit
                content.bars.forEach { bar ->
                    y += largeBar(bar, y)
                    y += 16 * unit
                }
            }

            drawBottomStack(topLimit = y + 10 * unit)
        }

        /**
         * The expanded tile's bottom-anchored group: strip, hero row, subhead
         * (or the standalone statement), pill, chips, call to action.
         *
         * Measured before it is placed. The first tablet build drew this stack
         * bottom-up with no ceiling, and on short-wide tiles the hero rose
         * straight through the label ("4.1" over SCREEN TIME, "28°" over
         * WEATHER, an answer across the 8 ball). Now, if the stack cannot fit
         * between [topLimit] and the bottom padding, it gives things up in a
         * fixed order — statement lines, a hero size step, the second subhead
         * line, the call to action, the chips, the strip — and the pill is
         * never dropped, because it is where the literal metric lives.
         */
        private fun drawBottomStack(topLimit: Float) {
            val available = (height - padV) - topLimit
            if (available <= 0f) return

            var statementLines = 6
            var heroShrink = 0
            var subheadLines = 2
            var showCta = content.callToAction != null
            var showChips = content.chips.isNotEmpty()
            var showStrip = content.strip.isNotEmpty()
            var showSubhead = content.subhead != null

            fun measure(): Float {
                var total = 0f
                if (showStrip) total += 5 * unit + 14 * unit
                content.heroValue?.let { total += lineOf(heroPaintStepped(it, heroShrink)) }
                if (showSubhead) {
                    total += 6 * unit +
                        layout(content.subhead!!, subheadPaint(), contentRight - contentLeft, subheadLines).height
                }
                if (content.heroValue == null && content.subhead == null) {
                    content.voice?.let {
                        total += layout(it, statementPaint(), contentRight - contentLeft, statementLines).height
                    }
                }
                content.pill?.let { total += 16 * unit + measurePillHeight(it) }
                if (showChips) total += 14 * unit + (lineOf(chipPaint(colours.pillText)) + 12 * unit)
                if (showCta) total += 14 * unit + lineOf(ctaPaint())
                return total
            }

            // Give things up, cheapest first, until the stack fits.
            val reductions = listOf<() -> Boolean>(
                { if (statementLines > 3) { statementLines = 3; true } else false },
                { if (heroShrink < 1) { heroShrink = 1; true } else false },
                { if (subheadLines > 1) { subheadLines = 1; true } else false },
                { if (statementLines > 2) { statementLines = 2; true } else false },
                { if (heroShrink < 2) { heroShrink = 2; true } else false },
                { if (showCta) { showCta = false; true } else false },
                { if (showChips) { showChips = false; true } else false },
                { if (showStrip) { showStrip = false; true } else false },
                { if (showSubhead) { showSubhead = false; true } else false },
            )
            var index = 0
            while (measure() > available && index < reductions.size) {
                if (!reductions[index]()) index++
            }

            var bottom = height - padV
            if (showCta) {
                content.callToAction?.let {
                    val block = layout(it.uppercase(Locale.getDefault()), ctaPaint(), contentRight - contentLeft, 1)
                    bottom -= block.height
                    block.drawAt(contentLeft, bottom.coerceAtLeast(topLimit))
                    bottom -= 14 * unit
                }
            }
            if (showChips) {
                bottom -= chipRow(content.chips.take(2), bottom, anchorBottom = true)
                bottom -= 14 * unit
            }
            content.pill?.let {
                bottom -= pill(it, bottom)
                bottom -= 16 * unit
            }
            if (showSubhead) {
                content.subhead?.let {
                    val block = layout(it, subheadPaint(), spanAt(bottom - 60 * unit, bottom).width, subheadLines)
                    bottom -= block.height
                    block.drawAt(spanAt(bottom, bottom + block.height).left, bottom.coerceAtLeast(topLimit))
                    bottom -= 6 * unit
                }
            }
            content.heroValue?.let { hero ->
                val block = rowBlock(
                    hero to heroPaintStepped(hero, heroShrink),
                    content.heroSuffix?.let { it to subheadPaint() },
                    content.metric?.let { it to metricPaint() },
                    maxWidth = spanAt(bottom - 90 * unit, bottom).width,
                )
                bottom -= block.height
                block.drawAt(spanAt(bottom, bottom + block.height).left, bottom.coerceAtLeast(topLimit))
            }
            if (content.heroValue == null && content.subhead == null) {
                content.voice?.let {
                    val paint = statementPaint()
                    // Never rise past the ceiling: fit the line count to what
                    // the space between the art and the chips actually allows.
                    val fitLines = ((bottom - topLimit) / lineOf(paint)).toInt().coerceIn(1, statementLines)
                    val estTop = bottom - lineOf(paint) * fitLines
                    val block = layout(it, paint, spanAt(estTop, bottom).width, fitLines)
                    bottom -= block.height
                    block.drawAt(spanAt(bottom, bottom + block.height).left, bottom.coerceAtLeast(topLimit))
                }
            }

            if (showStrip) {
                val stripHeight = 5 * unit
                val stripBitmap = TileArt.strip(
                    (contentRight - contentLeft).toInt(), stripHeight.toInt(),
                    content.strip, colours.onSurface, colours.trackTint, 10 * unit,
                )
                val stripBottom = (bottom - 14 * unit).coerceAtLeast(topLimit + stripHeight)
                canvas.drawBitmap(
                    stripBitmap, null,
                    rect(contentLeft, stripBottom - stripHeight, contentRight, stripBottom), null,
                )
            }
        }

        /** The pill's height as [pill] will draw it, without drawing it. */
        private fun measurePillHeight(text: String): Float {
            val paint = pillPaint()
            val padding = 12 * unit
            val block = layout(text, paint, (contentRight - contentLeft) - padding * 2, 3)
            return block.height + padding * 2
        }

        /** The fitted hero paint, stepped down [shrink] extra sizes when space demands it. */
        private fun heroPaintStepped(text: String, shrink: Int): TextPaint {
            if (shrink <= 0) return heroPaint(text)
            val steps = when (breakpoint) {
                WidgetBreakpoint.HERO -> listOf(88f, 72f, 50f)
                WidgetBreakpoint.EXPANDED -> listOf(76f, 64f, 44f)
                else -> return heroPaint(text)
            }
            val base = heroSizeSp(breakpoint, text.length, besideLeading = false)
            val start = steps.indexOfFirst { it <= base }.coerceAtLeast(0)
            val sp = steps[(start + shrink).coerceAtMost(steps.size - 1)]
            return paint(colours.onSurface, spToPx(sp), 800, trackingEm = -0.03f)
        }

        // -------------------------------------------------------- primitives

        private fun labelRow(y: Float): Float {
            val paint = labelPaint()
            val label = content.label.uppercase(Locale.getDefault())
            val detail = content.labelDetail?.uppercase(Locale.getDefault())
            val span = spanAt(y, y + lineOf(paint))

            // The label is the tile's name and always wins; the detail is a
            // bonus that renders only when both fit the safe span untruncated.
            val labelWidth = paint.measureText(label)
            val detailWidth = detail?.let { paint.measureText(it) } ?: 0f
            val bothFit = detail != null && labelWidth + 8 * unit + detailWidth <= span.width

            val block = layout(label, paint, span.width, 1)
            block.drawAt(span.left, y)
            if (bothFit) {
                canvas.drawText(detail!!, span.right - detailWidth, y - paint.fontMetrics.top, paint)
            }
            return max(block.height.toFloat(), lineOf(paint)) + 4 * unit
        }

        private fun labelBlock(maxWidth: Float): Block? {
            val paint = labelPaint()
            return layout(content.label.uppercase(Locale.getDefault()), paint, maxWidth, 1)
        }

        private fun drawLeading(leading: LeadingVisual, x: Float, y: Float, size: Float) {
            val sizePx = size.toInt()
            when (leading) {
                is LeadingVisual.Ring -> {
                    val ring = TileArt.ring(
                        sizePx, leading.fraction,
                        trackColour = colours.trackTint,
                        fillColour = leading.fillColour ?: colours.circle,
                        centreColour = if (leading.centreLabel != null) colours.surface else null,
                    )
                    canvas.drawBitmap(ring, null, rect(x, y, x + size, y + size), null)
                    leading.centreLabel?.let { label ->
                        // Centre text is sized by the ring's own inner disc
                        // (diameter 0.48x the ring), never by the breakpoint —
                        // a hero-sized paint spills across the arcs.
                        val paint = paint(colours.onSurface, size * 0.21f, 800, trackingEm = -0.03f)
                        val block = layout(label, paint, size, 1)
                        val detail = leading.centreDetail
                        val detailBlock = detail?.let {
                            layout(it, paint(colours.onSurfaceMuted, size * 0.10f, 500), size, 1)
                        }
                        val total = block.height + (detailBlock?.height ?: 0)
                        var ty = y + (size - total) / 2f
                        block.drawAt(x + (size - block.width) / 2f, ty)
                        ty += block.height
                        detailBlock?.drawAt(x + (size - detailBlock.width) / 2f, ty)
                    }
                }
                is LeadingVisual.EightBall -> {
                    val ball = TileArt.eightBall(sizePx, BallBlack, BallDisc)
                    canvas.drawBitmap(ball, null, rect(x, y, x + size, y + size), null)
                    val paint = paint(BallInk, size / 3.4f, 800)
                    val block = layout("8", paint, size, 1)
                    block.drawAt(x + (size - block.width) / 2f, y + (size - block.height) / 2f)
                }
                is LeadingVisual.Disc -> {
                    val disc = TileArt.disc(sizePx, leading.colour)
                    canvas.drawBitmap(disc, null, rect(x, y, x + size, y + size), null)
                    leading.label?.let {
                        val paint = paint(colours.surface, size / 2.4f, 800)
                        val block = layout(it, paint, size, 1)
                        block.drawAt(x + (size - block.width) / 2f, y + (size - block.height) / 2f)
                    }
                }
                is LeadingVisual.Dots -> {
                    val dots = TileArt.dots(
                        sizePx, (size * 0.55f).toInt(),
                        leading.filled, leading.total,
                        colours.onSurface, colours.trackTint,
                    )
                    canvas.drawBitmap(dots, null, rect(x, y, x + size, y + size * 0.55f), null)
                }
                is LeadingVisual.Numeral -> Unit // handled by the caller
            }
        }

        private fun barBlock(bar: TileBar, maxWidth: Float): Block {
            val labelPaint = chipPaint(colours.onSurfaceMuted)
            val valuePaint = chipPaint(colours.onSurface)
            val heightPx = (9 * unit).toInt()
            return object : Block {
                override val width = maxWidth.toInt()
                override val height = max(heightPx, lineOf(labelPaint).toInt())
                override val leftOverride: Float? = null
                override fun drawAt(x: Float, y: Float) {
                    val labelWidth = 52 * unit
                    val valueWidth = 42 * unit
                    canvas.drawText(bar.label, x, y - labelPaint.fontMetrics.top, labelPaint)
                    val barLeft = x + labelWidth
                    val barRight = x + maxWidth - valueWidth
                    val bitmap = TileArt.bar(
                        (barRight - barLeft).toInt().coerceAtLeast(8), heightPx,
                        bar.fraction, colours.trackTint,
                        com.softdread.widgets.design.SoftDreadTiles.colours(bar.colourRole).surface,
                    )
                    val barTop = y + (height - heightPx) / 2f
                    canvas.drawBitmap(bitmap, null, rect(barLeft, barTop, barRight, barTop + heightPx), null)
                    val valueX = x + maxWidth - valuePaint.measureText(bar.valueText)
                    canvas.drawText(bar.valueText, valueX, y - valuePaint.fontMetrics.top, valuePaint)
                }
            }
        }

        private fun largeBar(bar: TileBar, y: Float): Float {
            val labelPaint = metricPaint()
            val valuePaint = paint(colours.onSurface, spToPx(26f), 800)
            val header = max(lineOf(labelPaint), lineOf(valuePaint))
            canvas.drawText(bar.label, contentLeft, y + header - labelPaint.fontMetrics.bottom - 2 * unit, labelPaint)
            canvas.drawText(
                bar.valueText,
                contentRight - valuePaint.measureText(bar.valueText),
                y + header - valuePaint.fontMetrics.bottom,
                valuePaint,
            )
            val barHeight = 14 * unit
            val top = y + header + 7 * unit
            val bitmap = TileArt.bar(
                (contentRight - contentLeft).toInt(), barHeight.toInt(),
                bar.fraction, colours.trackTint,
                com.softdread.widgets.design.SoftDreadTiles.colours(bar.colourRole).surface,
            )
            canvas.drawBitmap(bitmap, null, rect(contentLeft, top, contentRight, top + barHeight), null)
            return header + 7 * unit + barHeight
        }

        private fun pill(text: String, bottom: Float): Float {
            val paint = pillPaint()
            val padding = 12 * unit
            val maxWidth = (contentRight - contentLeft) - padding * 2
            val block = layout(text, paint, maxWidth, 3)
            val pillHeight = block.height + padding * 2
            val top = bottom - pillHeight
            val radius = 16 * unit
            val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            bg.color = colours.pillBackground.toArgb()
            canvas.drawRoundRect(
                android.graphics.RectF(contentLeft, top, contentRight, bottom),
                radius, radius, bg,
            )
            block.drawAt(contentLeft + padding, top + padding)
            return pillHeight
        }

        private fun chipRow(chips: List<TileChip>, bottom: Float, anchorBottom: Boolean): Float {
            var x = contentLeft
            val paint = chipPaint(colours.pillText)
            val chipHeight = lineOf(paint) + 12 * unit
            val top = if (anchorBottom) bottom - chipHeight else bottom
            chips.forEach { chip ->
                val textPaint = chipPaint(if (chip.emphasised) colours.chipEmphasisOn else colours.pillText)
                val textWidth = textPaint.measureText(chip.text)
                val chipWidth = textWidth + 22 * unit
                if (x + chipWidth > contentRight) return@forEach
                val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                bg.color = (if (chip.emphasised) colours.chipEmphasisBg else colours.pillBackground).toArgb()
                canvas.drawRoundRect(
                    android.graphics.RectF(x, top, x + chipWidth, top + chipHeight),
                    chipHeight / 2f, chipHeight / 2f, bg,
                )
                canvas.drawText(chip.text, x + 11 * unit, top + 6 * unit - textPaint.fontMetrics.top, textPaint)
                x += chipWidth + 7 * unit
            }
            return chipHeight
        }

        // ------------------------------------------------------------- text

        private fun spanAt(top: Float, bottom: Float): TileTextGuard.SafeSpan =
            TileTextGuard.safeSpan(contentLeft, contentRight, top, bottom, obstacles, minTextWidth(), gapPx())

        private fun minTextWidth(): Float = (contentRight - contentLeft) * 0.45f
        private fun gapPx(): Float = 8 * unit

        private fun buildObstacles(): List<TileTextGuard.Obstacle> {
            val list = mutableListOf<TileTextGuard.Obstacle>()
            val reference = min(width, height).toFloat()
            circleSpec?.let { spec ->
                // Only solid shapes destroy contrast; tints shift it slightly and
                // the muted solver's headroom absorbs that.
                if (spec.colour.alpha == 1f) {
                    list += TileTextGuard.circleObstacle(
                        spec.anchor, reference * spec.diameterRatio, reference * spec.overhangRatio,
                        width.toFloat(), height.toFloat(),
                    )
                }
            }
            satelliteSpec?.let { spec ->
                val r = reference * spec.diameterRatio / 2f
                val cx = width * spec.centreXRatio
                val cy = height * spec.centreYRatio
                list += TileTextGuard.Obstacle(cx - r, cy - r, cx + r, cy + r)
            }
            return list
        }

        private interface Block {
            val width: Int
            val height: Int
            val leftOverride: Float?
            fun drawAt(x: Float, y: Float)
        }

        private fun layout(text: String, paint: TextPaint, maxWidth: Float, maxLines: Int): Block {
            val w = maxWidth.toInt().coerceAtLeast(1)
            val staticLayout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            val measured = (0 until staticLayout.lineCount).maxOf { staticLayout.getLineWidth(it) }
            return object : Block {
                override val width = measured.roundToInt()
                override val height = staticLayout.height
                override val leftOverride: Float? = null
                override fun drawAt(x: Float, y: Float) {
                    canvas.withSave {
                        translate(x, y)
                        staticLayout.draw(this)
                    }
                }
            }
        }

        /** A baseline-aligned row: hero plus optional suffix and metric. */
        private fun rowBlock(
            first: Pair<String, TextPaint>,
            second: Pair<String, TextPaint>?,
            third: Pair<String, TextPaint>?,
            maxWidth: Float,
        ): Block {
            val parts = listOfNotNull(first, second, third)
            val gap = 8 * unit
            val heights = parts.map { lineOf(it.second) }
            val rowHeight = heights.max()
            return object : Block {
                override val width = maxWidth.toInt()
                override val height = rowHeight.toInt()
                override val leftOverride: Float? = null
                override fun drawAt(x: Float, y: Float) {
                    var cx = x
                    val baseline = y + rowHeight - parts[0].second.fontMetrics.bottom
                    parts.forEachIndexed { index, (text, paint) ->
                        val available = maxWidth - (cx - x)
                        if (available <= 0) return@forEachIndexed
                        // A row draws one line; a newline that reaches it would
                        // render as nothing and fuse the words together.
                        val singleLine = text.replace('\n', ' ').replace("  ", " · ")
                        val clipped = TextUtils.ellipsize(singleLine, paint, available, TextUtils.TruncateAt.END).toString()
                        canvas.drawText(clipped, cx, baseline, paint)
                        cx += paint.measureText(clipped) + if (index < parts.size - 1) gap else 0f
                    }
                }
            }
        }

        private fun chipsBlock(chips: List<TileChip>): Block {
            val paint = chipPaint(colours.pillText)
            val chipHeight = (lineOf(paint) + 12 * unit).toInt()
            return object : Block {
                override val width = (contentRight - contentLeft).toInt()
                override val height = chipHeight
                override val leftOverride: Float? = null
                override fun drawAt(x: Float, y: Float) {
                    chipRow(chips, y, anchorBottom = false)
                }
            }
        }

        // ------------------------------------------------------------ paints

        /**
         * Type sizes are dp-anchored to the tile's own grid (1sp == 1dp at the
         * tile's design size) and then multiplied by the user's font scale.
         */
        private fun spToPx(sp: Float): Float = sp * unit * fontScale

        private fun lineOf(paint: TextPaint): Float =
            paint.fontMetrics.let { it.bottom - it.top }

        private fun paint(colour: Color, sizePx: Float, weight: Int, trackingEm: Float = 0f): TextPaint =
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                color = colour.toArgb()
                textSize = sizePx
                letterSpacing = trackingEm
                typeface = typefaceFor(context, weight)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && weight >= 600) isFakeBoldText = true
            }

        private fun heroPaint(text: String, besideLeading: Boolean = false): TextPaint {
            val sp = heroSizeSp(breakpoint, text.length, besideLeading)
            return paint(colours.onSurface, spToPx(sp), 800, trackingEm = -0.03f)
        }

        private fun heroNumeralPaint(): TextPaint {
            val sp = when (breakpoint) {
                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 62f
                WidgetBreakpoint.EXPANDED -> 92f
                WidgetBreakpoint.HERO -> 100f
                else -> 38f
            }
            return paint(colours.onSurface, spToPx(sp), 800, trackingEm = -0.04f)
        }

        private fun labelPaint(): TextPaint =
            paint(colours.label, spToPx(if (breakpoint.isLarge) 12f else 11f), 600, trackingEm = 0.14f)

        private fun voicePaint(): TextPaint = paint(
            colours.onSurfaceMuted,
            spToPx(
                when (breakpoint) {
                    WidgetBreakpoint.TINY, WidgetBreakpoint.COMPACT -> 13f
                    WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 16f
                    WidgetBreakpoint.EXPANDED -> 20f
                    WidgetBreakpoint.HERO -> 22f
                },
            ),
            if (breakpoint == WidgetBreakpoint.COMPACT || breakpoint == WidgetBreakpoint.TINY) 500 else 600,
        )

        private fun statementPaint(): TextPaint = paint(
            colours.onSurface,
            spToPx(
                when (breakpoint) {
                    WidgetBreakpoint.TINY -> 13f
                    WidgetBreakpoint.COMPACT -> 15f
                    WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 19f
                    WidgetBreakpoint.EXPANDED -> 30f
                    WidgetBreakpoint.HERO -> 34f
                },
            ),
            600,
        )

        private fun subheadPaint(): TextPaint =
            paint(colours.onSurface, spToPx(if (breakpoint.isLarge) 23f else 16f), 600)

        private fun metricPaint(): TextPaint =
            paint(colours.onSurfaceMuted, spToPx(if (breakpoint.isLarge) 15f else 13f), 500)

        private fun chipPaint(colour: Color): TextPaint = paint(colour, spToPx(12f), 600)

        private fun pillPaint(): TextPaint =
            paint(colours.pillText, spToPx(if (breakpoint.isLarge) 14f else 13f), 500)

        private fun ctaPaint(): TextPaint =
            paint(colours.callToAction, spToPx(11f), 600, trackingEm = 0.1f)

        private fun rect(l: Float, t: Float, r: Float, b: Float) =
            Rect(l.roundToInt(), t.roundToInt(), r.roundToInt(), b.roundToInt())
    }

    /** The sheet's fitted hero scale; shared with the in-app preview. */
    fun heroSizeSp(breakpoint: WidgetBreakpoint, length: Int, besideLeading: Boolean): Float =
        when (breakpoint) {
            WidgetBreakpoint.TINY -> if (length <= 6) 26f else 22f
            WidgetBreakpoint.COMPACT -> when {
                besideLeading -> if (length <= 4) 32f else 26f
                length <= 4 -> 38f
                length <= 7 -> 32f
                else -> 26f
            }
            WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> when {
                length <= 4 -> 46f
                length <= 8 -> 40f
                else -> 32f
            }
            WidgetBreakpoint.EXPANDED -> when {
                length <= 4 -> 76f
                length <= 7 -> 64f
                else -> 44f
            }
            WidgetBreakpoint.HERO -> when {
                length <= 4 -> 88f
                length <= 7 -> 72f
                else -> 50f
            }
        }

    // ---------------------------------------------------------------- fonts

    private val typefaces = mutableMapOf<Int, Typeface>()

    private fun typefaceFor(context: Context, weight: Int): Typeface =
        typefaces.getOrPut(weight) {
            val base = ResourcesCompat.getFont(context, R.font.bricolage_grotesque)
                ?: Typeface.SANS_SERIF
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(base, weight, false)
            } else {
                base
            }
        }

    /** The 8 ball's own colours: the classic object, not a theme surface. */
    private val BallBlack = Color(0xFF13161D)
    private val BallDisc = Color(0xFFFFFBF2)
    private val BallInk = Color(0xFF13161D)
}
