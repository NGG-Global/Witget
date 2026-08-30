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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.softdread.widgets.R
import com.softdread.widgets.design.Contrast
import com.softdread.widgets.design.SoftDreadPalette
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

    /** Compact voice sizes step down through these before any copy is cut. */
    private val VOICE_STEPS = floatArrayOf(1f, 0.88f, 0.78f)

    /**
     * One step of the shrink-to-fit ladder every text block runs before it is
     * allowed to be cut. Copy outranks type size everywhere on a tile: the
     * sheet's scale is a starting point, not a licence to clip a number, a
     * micro-label or a punchline.
     */
    private const val FIT_STEP = 0.07f

    /** Per-role floors for that ladder, as a fraction of the sheet's size. */
    private const val HERO_MIN_SCALE = 0.5f
    private const val LABEL_MIN_SCALE = 0.72f
    private const val STATEMENT_MIN_SCALE = 0.45f
    private const val VOICE_MIN_SCALE = 0.7f
    private const val SUPPORT_MIN_SCALE = 0.7f
    private const val ROW_SECONDARY_MIN_SCALE = 0.55f
    private const val ROW_MIN_SCALE = 0.55f

    /** Dots ink only this fraction of the square they are drawn into. */
    private const val DOTS_HEIGHT_RATIO = 0.55f

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
        var bitmap = createBitmap(width, height, if (needsAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        val pass = Pass(context, Canvas(bitmap), role, breakpoint, colours, content, width, height, fontScale, densityPx)
        pass.draw()
        if (pass.copyLostToMotif && content.motif != null) {
            // Copy lost to the plate: re-render this tile without it. The
            // retinted field survives; only the pictogram is given up.
            bitmap = createBitmap(width, height, if (needsAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            Pass(
                context, Canvas(bitmap), role, breakpoint, colours,
                content.copy(motif = null), width, height, fontScale, densityPx,
            ).draw()
        }
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
        private val satelliteGeometry = TileGeometry.satellite(role, breakpoint, colours)

        /**
         * The motif plate: a disc carrying the content's pictogram — the tile's
         * visual anchor for what the copy is about. It occupies the satellite's
         * slot (the sheet's shape budget stays one cropped circle plus one
         * companion), so when a motif renders the decorative satellite does not.
         * Colours are measured against the actual backdrop, not assumed: the
         * plate must be perceptible on whatever sits under it — field or field
         * circle — and the glyph ink is the pack extreme that reads best on the
         * plate.
         */
        val motifPlate = buildMotifPlate()
        val satelliteSpec = if (motifPlate != null) null else satelliteGeometry
        val obstacles = buildObstacles()

        /**
         * Set when copy lost to the plate: a text block was cut short, or drew
         * across the plate's disc (possible when a field circle on the other
         * side leaves the guard no side to give). The renderer then throws the
         * pass away and re-renders without the plate — the punchline always
         * outranks the pictogram; a retinted field costs nothing and stays.
         */
        var copyLostToMotif = false

        fun markMotifCollision(x: Float, y: Float, w: Float, h: Float) {
            val plate = motifPlate ?: return
            val r = plate.radius + 2 * unit
            val overlapsX = x < plate.cx + r && x + w > plate.cx - r
            val overlapsY = y < plate.cy + r && y + h > plate.cy - r
            if (overlapsX && overlapsY) copyLostToMotif = true
        }

        fun draw() {
            TileArt.drawBackgroundInto(
                canvas, width, height,
                radius = breakpoint.cornerRadiusDp * unit,
                surface = colours.surface,
                circle = circleSpec,
                satellite = satelliteSpec,
                insetStroke = if (colours.needsInsetStroke) colours.hairline else null,
            )
            motifPlate?.let { plate ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color = plate.plate.toArgb()
                canvas.drawCircle(plate.cx, plate.cy, plate.radius, paint)
                MotifArt.draw(
                    canvas, plate.glyph, plate.cx, plate.cy,
                    plate.radius * MotifArt.GLYPH_OF_PLATE,
                    ink = plate.ink.toArgb(), accent = plate.accent.toArgb(),
                )
            }
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
            val width = spanAt(y, y + heroPaint(hero).textSize * 1.1f).width
            val block = fitted(hero, width, 1, HERO_MIN_SCALE) { heroPaint(hero, scale = it) }.second
            val free = height - padV - y
            block.drawAt(contentLeft, y + max(0f, (free - block.height) / 2f))
        }

        private fun compact() {
            var y = padV
            y += labelRow(y)

            // Bottom-anchored stack: call to action, then chip, then voice.
            // The voice decides what else survives, in a fixed order: full
            // size beside the chip; then the chip is given up for a fourth
            // line; then the voice steps its size down. Metadata garnish and
            // display size both rank below a complete punchline.
            var showChip = content.chips.isNotEmpty()
            var voiceShrink = 0
            content.voice?.let { voice ->
                val ladder = buildList {
                    if (showChip) add(true to 0)
                    add(false to 0)
                    add(false to 1)
                    add(false to 2)
                }
                val fit = ladder.firstOrNull { (chip, shrink) ->
                    !fitCompactVoice(voice, chip, shrink).ellipsized
                } ?: ladder.last()
                showChip = fit.first
                voiceShrink = fit.second
            }

            var bottom = height - padV
            content.callToAction?.let {
                val width = spanAt(bottom - lineOf(ctaPaint()), bottom).width
                val block = fitted(it.uppercase(Locale.getDefault()), width, 1, SUPPORT_MIN_SCALE) {
                    ctaPaint(it)
                }.second
                bottom -= block.height
                block.drawAt(contentLeft, bottom)
                bottom -= 6 * unit
            }
            if (showChip) {
                bottom -= chipRow(listOf(content.chips.first()), bottom, anchorBottom = true)
                bottom -= 7 * unit
            }
            content.voice?.let {
                val maxLines = if (content.heroValue != null || showChip) 3 else 4
                val estTop = bottom - lineOf(voicePaint(voiceShrink)) * maxLines
                val width = spanAt(estTop, bottom).width
                // The step ladder above trades the chip and then display size;
                // this is the last resort before a compact punchline is cut.
                val block = fitted(it, width, maxLines, VOICE_MIN_SCALE) {
                    voicePaint(voiceShrink, scale = it)
                }.second
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
                    val hero = content.heroValue
                    val textLeft = contentLeft + markSize + 10 * unit
                    val (paint, block) = fitted(hero, contentRight - textLeft, 1, HERO_MIN_SCALE) {
                        heroPaint(hero, besideLeading = true, scale = it)
                    }
                    val rowHeight = max(markSize, paint.textSize)
                    val rowTop = bandTop + max(0f, (bandBottom - bandTop - rowHeight) / 2f)
                    drawLeading(leading, contentLeft, rowTop + (rowHeight - markSize) / 2f, markSize)
                    block.drawAt(textLeft, rowTop + (rowHeight - block.height) / 2f)
                }
                content.heroValue != null -> {
                    val hero = content.heroValue
                    // The safe span for the row the hero actually occupies, not
                    // for the whole band: a field circle low in the tile used to
                    // narrow the column that "19 days" was measured against and
                    // cut it to "19 d...".
                    val estHeight = lineOf(heroPaint(hero))
                    val rowTop = bandTop + max(0f, (bandBottom - bandTop - estHeight) / 2f)
                    val width = spanAt(rowTop, rowTop + estHeight).width
                    val block = rowBlock(
                        hero to heroPaint(hero),
                        content.heroSuffix?.let { it to subheadPaint() },
                        null,
                        maxWidth = width,
                    )
                    block.drawAt(contentLeft, bandTop + max(0f, (bandBottom - bandTop - block.height) / 2f))
                }
                leading != null -> {
                    val rowTop = bandTop + max(0f, (bandBottom - bandTop - ballSize) / 2f)
                    drawLeading(leading, contentLeft, rowTop, ballSize)
                }
            }
        }

        /** Lays the compact voice out as it would render, to test for cuts. */
        private fun fitCompactVoice(voice: String, withChip: Boolean, shrink: Int = 0): Block {
            var anchor = height - padV
            content.callToAction?.let { anchor -= lineOf(ctaPaint()) + 6 * unit }
            if (withChip) anchor -= (lineOf(chipPaint(colours.pillText)) + 12 * unit) + 7 * unit
            val paint = voicePaint(shrink)
            val maxLines = if (content.heroValue != null || withChip) 3 else 4
            val estTop = anchor - lineOf(paint) * maxLines
            return layout(voice, paint, spanAt(estTop, anchor).width, maxLines)
        }

        private fun standard() {
            val leading = content.leading
            val markSize = 82 * unit
            var textLeft = contentLeft
            val leadingInset = if (leading != null && leading !is LeadingVisual.Numeral) markSize + 18 * unit else 0f

            // Measure the column first so the whole row can centre vertically —
            // and so a tile shorter than the design canvas (a 4x1 resize) gives
            // content up in a fixed order instead of running off the bottom.
            val room = height - padV * 2
            var voiceLines = if (content.heroValue == null) 4 else 2
            var showChips = content.chips.isNotEmpty()
            var showCta = content.callToAction != null
            var blocks = standardColumn(textLeft + leadingInset, voiceLines, showChips, showCta)
            val reductions = listOf<() -> Boolean>(
                { if (voiceLines > 2) { voiceLines = 2; true } else false },
                { if (showCta) { showCta = false; true } else false },
                { if (showChips) { showChips = false; true } else false },
                { if (voiceLines > 1) { voiceLines = 1; true } else false },
            )
            var index = 0
            while (columnHeight(blocks) > room && index < reductions.size) {
                if (reductions[index]()) {
                    blocks = standardColumn(textLeft + leadingInset, voiceLines, showChips, showCta)
                } else {
                    index++
                }
            }
            val measured = columnHeight(blocks)

            when (leading) {
                is LeadingVisual.Numeral -> {
                    val block = fitted(leading.text, contentRight - contentLeft, 1, HERO_MIN_SCALE) {
                        heroNumeralPaint(it)
                    }.second
                    block.drawAt(contentLeft, (height - block.height) / 2f)
                    textLeft = contentLeft + block.width + 18 * unit
                }
                null -> Unit
                else -> {
                    drawLeading(leading, contentLeft, (height - markSize) / 2f, markSize)
                    textLeft = contentLeft + markSize + 18 * unit
                }
            }

            val rebuilt = standardColumn(textLeft, voiceLines, showChips, showCta)
            var y = max(padV, (height - measured) / 2f)
            rebuilt.forEach { block ->
                block.drawAt(block.leftOverride ?: textLeft, y)
                y += block.height + 7 * unit
            }
        }

        private fun columnHeight(blocks: List<Block>): Float =
            blocks.sumOf { it.height.toDouble() }.toFloat() +
                blocks.size.let { if (it > 1) (it - 1) * 7 * unit else 0f }

        /** The standard layout's text column, measured against [left]. */
        private fun standardColumn(
            left: Float,
            voiceLines: Int,
            showChips: Boolean,
            showCta: Boolean,
        ): List<Block> {
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
                blocks += rowBlock(
                    hero to heroPaint(hero),
                    content.heroSuffix?.let { it to metricPaint() },
                    content.metric?.let { it to metricPaint() },
                    maxWidth = fullBand,
                )
            }
            content.bars.forEach { bar -> blocks += barBlock(bar, fullBand) }
            content.voice?.let {
                val statement = content.heroValue == null && content.bars.isEmpty()
                val minScale = if (statement) STATEMENT_MIN_SCALE else VOICE_MIN_SCALE
                blocks += fitted(it, fullBand, voiceLines, minScale) { scale ->
                    if (statement) statementPaint(scale) else voicePaint(scale = scale)
                }.second
            }
            if (showChips && content.chips.isNotEmpty()) blocks += chipsBlock(content.chips.take(2))
            if (showCta) {
                content.callToAction?.let {
                    blocks += fitted(it.uppercase(Locale.getDefault()), fullBand, 1, SUPPORT_MIN_SCALE) { scale ->
                        ctaPaint(scale)
                    }.second
                }
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
                    // Dots draw into 0.55 of the square they are given; only
                    // reserve what is actually inked, or the empty remainder
                    // pushes the subhead off a tile that had room for it.
                    y += if (leading is LeadingVisual.Dots) size * DOTS_HEIGHT_RATIO else size
                }
            }

            if (content.bars.isNotEmpty()) {
                y += 20 * unit
                content.bars.forEach { bar ->
                    y += largeBar(bar, y)
                    y += 16 * unit
                }
            }

            // The plate lives in the tile's upper zone; the stack must never
            // rise into it. Horizontal avoidance alone cannot save a tile that
            // also has a field circle on the other side — the guard rightly
            // refuses to leave a sliver — so the plate takes a vertical slice.
            drawBottomStack(topLimit = max(y + 10 * unit, motifPlate?.let { it.cy + it.radius + 8 * unit } ?: 0f))
        }

        /**
         * The standalone statement — the Daily Joke's punchline and the 8
         * ball's answer — laid out so it is never cut.
         *
         * Both dimensions bite here: the ceiling caps the line count, and a
         * field circle beside the copy narrows the column. Stepping the size
         * down fixes both at once, because a smaller line both fits the width
         * and buys another line under the ceiling, so the loop continues until
         * the whole statement renders or the 13sp floor is reached.
         */
        private fun fitStatement(
            text: String,
            bottom: Float,
            topLimit: Float,
            statementLines: Int,
        ): Block {
            var scale = 1f
            while (true) {
                val paint = statementPaint(scale)
                val fitLines = ((bottom - topLimit) / lineOf(paint)).toInt().coerceIn(1, statementLines)
                val estTop = bottom - lineOf(paint) * fitLines
                val block = layout(text, paint, spanAt(estTop, bottom).width, fitLines)
                if (!block.ellipsized || scale <= STATEMENT_MIN_SCALE) return block
                scale = (scale - FIT_STEP).coerceAtLeast(STATEMENT_MIN_SCALE)
            }
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

            // The ceiling below is what really bounds the statement; this is
            // only an upper limit, and it has to be generous enough for a long
            // joke in a column narrowed by a field circle at a 1.6x font scale.
            var statementLines = 9
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
                        // Measure the lines the space actually allows, exactly
                        // as fitStatement will lay them out; counting the raw
                        // maximum here made the stack look overfull and dropped
                        // chips a tile had room for.
                        val paint = statementPaint()
                        val lines = (available / lineOf(paint)).toInt().coerceIn(1, statementLines)
                        total += layout(it, paint, contentRight - contentLeft, lines).height
                    }
                }
                content.pill?.let { total += 16 * unit + measurePillHeight(it) }
                if (showChips) total += 14 * unit + (lineOf(chipPaint(colours.pillText)) + 12 * unit)
                if (showCta) total += 14 * unit + lineOf(ctaPaint())
                return total
            }

            // Give things up, cheapest first, until the stack fits.
            val reductions = listOf<() -> Boolean>(
                { if (statementLines > 4) { statementLines = 4; true } else false },
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

            // A cut punchline outranks its garnish. If the statement still
            // does not fit at its smallest size — which happens when a field
            // circle narrows the column at a large font scale — give up the
            // chips and then the call to action to buy it the lines it needs.
            if (content.heroValue == null && content.subhead == null) {
                content.voice?.let { voice ->
                    repeat(2) {
                        if (!statementCut(voice, topLimit, showCta, showChips, statementLines)) return@let
                        when {
                            showChips -> showChips = false
                            showCta -> showCta = false
                            else -> return@let
                        }
                    }
                }
            }

            var bottom = height - padV
            if (showCta) {
                content.callToAction?.let {
                    val block = fitted(
                        it.uppercase(Locale.getDefault()), contentRight - contentLeft, 1, SUPPORT_MIN_SCALE,
                    ) { scale -> ctaPaint(scale) }.second
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
                    val width = spanAt(bottom - 60 * unit, bottom).width
                    val block = fitted(it, width, subheadLines, SUPPORT_MIN_SCALE) { scale ->
                        subheadPaint(scale)
                    }.second
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
                content.voice?.let { voice ->
                    // The statement steps its size down before anything else is
                    // given up: a motif plate plus a field circle can shrink
                    // the safe area enough that the display size no longer
                    // holds the whole line, and the punchline must never lose
                    // its ending to decoration.
                    val block = fitStatement(voice, bottom, topLimit, statementLines)
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

        /**
         * The pill's laid-out copy. The pill carries the literal metric, so it
         * is never dropped and never cut: it steps its type size down instead.
         * [measurePillHeight] and [pill] share this so the measured stack and
         * the drawn stack cannot disagree.
         */
        /** Where the statement's baseline sits, given what rides below it. */
        private fun statementBottom(showCta: Boolean, showChips: Boolean): Float {
            var bottom = height - padV
            if (showCta && content.callToAction != null) bottom -= lineOf(ctaPaint()) + 14 * unit
            if (showChips) bottom -= (lineOf(chipPaint(colours.pillText)) + 12 * unit) + 14 * unit
            content.pill?.let { bottom -= measurePillHeight(it) + 16 * unit }
            return bottom
        }

        /** True when the statement would still be cut with this much room. */
        private fun statementCut(
            voice: String,
            topLimit: Float,
            showCta: Boolean,
            showChips: Boolean,
            statementLines: Int,
        ): Boolean =
            fitStatement(voice, statementBottom(showCta, showChips), topLimit, statementLines).ellipsized

        private fun pillFit(text: String): Block {
            val padding = 12 * unit
            return fitted(text, (contentRight - contentLeft) - padding * 2, 3, SUPPORT_MIN_SCALE) { scale ->
                pillPaint(scale)
            }.second
        }

        /** The pill's height as [pill] will draw it, without drawing it. */
        private fun measurePillHeight(text: String): Float = pillFit(text).height + 12 * unit * 2

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
            val label = content.label.uppercase(Locale.getDefault())
            val detail = content.labelDetail?.uppercase(Locale.getDefault())
            val span = spanAt(y, y + lineOf(labelPaint()))

            // The label is the tile's name and always wins; it steps down
            // rather than clipping, which matters most at large font scales
            // where "WEATHER, TRANSLATED" no longer fits at the sheet's size.
            // The detail is a bonus that renders only when both then fit.
            val (paint, block) = fitted(label, span.width, 1, LABEL_MIN_SCALE) { labelPaint(it) }
            val detailWidth = detail?.let { paint.measureText(it) } ?: 0f
            val bothFit = detail != null && block.width + 8 * unit + detailWidth <= span.width

            block.drawAt(span.left, y)
            if (bothFit) {
                canvas.drawText(detail!!, span.right - detailWidth, y - paint.fontMetrics.top, paint)
            }
            return max(block.height.toFloat(), lineOf(paint)) + 4 * unit
        }

        private fun labelBlock(maxWidth: Float): Block? =
            fitted(content.label.uppercase(Locale.getDefault()), maxWidth, 1, LABEL_MIN_SCALE) {
                labelPaint(it)
            }.second

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
                        sizePx, (size * DOTS_HEIGHT_RATIO).toInt(),
                        leading.filled, leading.total,
                        colours.onSurface, colours.trackTint,
                    )
                    canvas.drawBitmap(dots, null, rect(x, y, x + size, y + size * DOTS_HEIGHT_RATIO), null)
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
            val barHeight = 14 * unit
            // A bar is copy plus a measurement, so it obeys the same guard the
            // text does. Without this the year row on Time Progress, and the
            // countdown's elapsed bar, drew straight across the field circle.
            val span = spanAt(y, y + header + 7 * unit + barHeight)
            val left = span.left
            val right = span.right
            canvas.drawText(bar.label, left, y + header - labelPaint.fontMetrics.bottom - 2 * unit, labelPaint)
            canvas.drawText(
                bar.valueText,
                right - valuePaint.measureText(bar.valueText),
                y + header - valuePaint.fontMetrics.bottom,
                valuePaint,
            )
            val top = y + header + 7 * unit
            val bitmap = TileArt.bar(
                (right - left).toInt().coerceAtLeast(8), barHeight.toInt(),
                bar.fraction, colours.trackTint,
                com.softdread.widgets.design.SoftDreadTiles.colours(bar.colourRole).surface,
            )
            canvas.drawBitmap(bitmap, null, rect(left, top, right, top + barHeight), null)
            return header + 7 * unit + barHeight
        }

        private fun pill(text: String, bottom: Float): Float {
            val padding = 12 * unit
            val block = pillFit(text)
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
            motifPlate?.let { plate ->
                // The plate is always solid, so it is always an obstacle.
                val r = plate.radius + 4 * unit
                list += TileTextGuard.Obstacle(plate.cx - r, plate.cy - r, plate.cx + r, plate.cy + r)
            }
            return list
        }

        // ------------------------------------------------------------- motif

        class MotifPlate(
            val glyph: MotifGlyph,
            val cx: Float,
            val cy: Float,
            val radius: Float,
            val plate: Color,
            val ink: Color,
            val accent: Color,
        )

        private fun buildMotifPlate(): MotifPlate? {
            val glyph = content.motif ?: return null
            // A tiny tile has no room for a companion shape, and a setup state
            // should look like a request, not like content.
            if (breakpoint == WidgetBreakpoint.TINY || content.isSetupState) return null

            val short = min(width, height).toFloat()
            val radius = short * when (breakpoint) {
                WidgetBreakpoint.COMPACT -> 0.155f
                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 0.15f
                else -> 0.13f
            }
            // The satellite's measured position when the sheet defines one for
            // this tile; otherwise the top-end slot under the label row.
            var cx: Float
            var cy: Float
            // Only an upper-half satellite slot can host the plate: clay and
            // cream place their satellite low, where the plate would sit on
            // the pill. Those tiles use the top-end slot instead.
            val slot = satelliteGeometry?.takeIf { it.centreYRatio <= 0.5f }
            if (slot != null) {
                cx = width * slot.centreXRatio
                cy = height * slot.centreYRatio
            } else {
                cx = contentRight - radius
                cy = padV + lineOf(labelPaint()) + 6 * unit + radius
            }
            val margin = 4 * unit
            cx = cx.coerceIn(radius + margin, width - radius - margin)
            cy = cy.coerceIn(radius + margin, height - radius - margin)

            val backdrop = backdropAt(cx, cy)
            val plate = Contrast.perceptibleShape(
                preferred = colours.contrastCircle,
                surface = backdrop,
                fallback = colours.satellite,
            )
            val ink = Contrast.bestOn(plate, SoftDreadPalette.Ink, SoftDreadPalette.Cream)
            val accent = Contrast.perceptibleShape(
                preferred = colours.surface,
                surface = plate,
                fallback = ink,
            )
            return MotifPlate(glyph, cx, cy, radius, plate, ink, accent)
        }

        /** What actually sits under a point: the field, or the field circle. */
        private fun backdropAt(px: Float, py: Float): Color {
            val spec = circleSpec ?: return colours.surface
            val reference = min(width, height).toFloat()
            val rect = TileTextGuard.circleObstacle(
                spec.anchor, reference * spec.diameterRatio, reference * spec.overhangRatio,
                width.toFloat(), height.toFloat(),
            )
            val ccx = (rect.left + rect.right) / 2f
            val ccy = (rect.top + rect.bottom) / 2f
            val cr = (rect.right - rect.left) / 2f
            val dx = px - ccx
            val dy = py - ccy
            if (dx * dx + dy * dy > cr * cr) return colours.surface
            return if (spec.colour.alpha == 1f) spec.colour else spec.colour.compositeOver(colours.surface)
        }

        private interface Block {
            val width: Int
            val height: Int
            val leftOverride: Float?
            /** True when StaticLayout had to cut the text. */
            val ellipsized: Boolean get() = false
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
                override val ellipsized =
                    staticLayout.getEllipsisCount(staticLayout.lineCount - 1) > 0
                override fun drawAt(x: Float, y: Float) {
                    markMotifCollision(x, y, width.toFloat(), height.toFloat())
                    if (ellipsized) copyLostToMotif = motifPlate != null || copyLostToMotif
                    canvas.withSave {
                        translate(x, y)
                        staticLayout.draw(this)
                    }
                }
            }
        }

        /**
         * Lays [text] out and, if it would be cut, steps the type size down
         * until it fits or reaches [minScale]. Returns the paint that survived
         * alongside the block, because callers that right-align a companion
         * (the label's detail, a row's metric) need to measure with it.
         *
         * This is the single reason a hero numeral, a micro-label or a
         * punchline can no longer end in an ellipsis: every text block on a
         * tile goes through here, and giving up display size is always
         * preferred to giving up a word.
         */
        private fun fitted(
            text: String,
            maxWidth: Float,
            maxLines: Int,
            minScale: Float,
            paintAt: (Float) -> TextPaint,
        ): Pair<TextPaint, Block> {
            var scale = 1f
            var paint = paintAt(scale)
            var block = layout(text, paint, maxWidth, maxLines)
            while (block.ellipsized && scale > minScale) {
                scale = (scale - FIT_STEP).coerceAtLeast(minScale)
                paint = paintAt(scale)
                block = layout(text, paint, maxWidth, maxLines)
            }
            return paint to block
        }

        /** [TextPaint] at a fraction of another's size, keeping face and colour. */
        private fun scaled(source: TextPaint, factor: Float): TextPaint =
            TextPaint(source).apply { textSize = source.textSize * factor }

        /**
         * A baseline-aligned row: hero plus optional suffix and metric.
         *
         * When the row is wider than the space it has, the trailing parts are
         * scaled down first — the hero number is the point of the row — and
         * only then is the whole row scaled. Ellipsizing is the last resort,
         * not the first response.
         */
        private fun rowBlock(
            first: Pair<String, TextPaint>,
            second: Pair<String, TextPaint>?,
            third: Pair<String, TextPaint>?,
            maxWidth: Float,
        ): Block {
            val natural = listOfNotNull(first, second, third)
            val gap = 8 * unit
            val parts = fitRow(natural, maxWidth, gap)
            val heights = parts.map { lineOf(it.second) }
            val rowHeight = heights.max()
            return object : Block {
                override val width = maxWidth.toInt()
                override val height = rowHeight.toInt()
                override val leftOverride: Float? = null
                override fun drawAt(x: Float, y: Float) {
                    markMotifCollision(x, y, maxWidth, rowHeight)
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

        /** Scales a row's parts so the whole row fits [maxWidth]; see [rowBlock]. */
        private fun fitRow(
            parts: List<Pair<String, TextPaint>>,
            maxWidth: Float,
            gap: Float,
        ): List<Pair<String, TextPaint>> {
            if (parts.isEmpty() || maxWidth <= 0f) return parts
            fun measure(text: String, paint: TextPaint) = paint.measureText(text.replace('\n', ' '))
            val gaps = gap * (parts.size - 1)
            val heroWidth = measure(parts[0].first, parts[0].second)
            val restWidth = parts.drop(1).sumOf { measure(it.first, it.second).toDouble() }.toFloat()
            if (heroWidth + restWidth + gaps <= maxWidth) return parts

            // Give the secondary parts up first.
            val restBudget = maxWidth - heroWidth - gaps
            val secondary = if (restWidth > 0f) {
                (restBudget / restWidth).coerceIn(ROW_SECONDARY_MIN_SCALE, 1f)
            } else {
                1f
            }
            val stepped = parts.mapIndexed { index, part ->
                if (index == 0) part else part.first to scaled(part.second, secondary)
            }
            val steppedWidth = stepped.sumOf { measure(it.first, it.second).toDouble() }.toFloat() + gaps
            if (steppedWidth <= maxWidth) return stepped

            val whole = (maxWidth / steppedWidth).coerceIn(ROW_MIN_SCALE, 1f)
            return stepped.map { it.first to scaled(it.second, whole) }
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

        private fun heroPaint(text: String, besideLeading: Boolean = false, scale: Float = 1f): TextPaint {
            val sp = heroSizeSp(breakpoint, text.length, besideLeading)
            return paint(colours.onSurface, spToPx(sp * scale), 800, trackingEm = -0.03f)
        }

        private fun heroNumeralPaint(scale: Float = 1f): TextPaint {
            val sp = when (breakpoint) {
                WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 62f
                WidgetBreakpoint.EXPANDED -> 92f
                WidgetBreakpoint.HERO -> 100f
                else -> 38f
            }
            return paint(colours.onSurface, spToPx(sp * scale), 800, trackingEm = -0.04f)
        }

        private fun labelPaint(scale: Float = 1f): TextPaint =
            paint(colours.label, spToPx((if (breakpoint.isLarge) 12f else 11f) * scale), 600, trackingEm = 0.14f)

        private fun voicePaint(shrink: Int = 0, scale: Float = 1f): TextPaint = paint(
            colours.onSurfaceMuted,
            spToPx(
                when (breakpoint) {
                    WidgetBreakpoint.TINY, WidgetBreakpoint.COMPACT -> 13f
                    WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 16f
                    WidgetBreakpoint.EXPANDED -> 20f
                    WidgetBreakpoint.HERO -> 22f
                } * VOICE_STEPS[shrink.coerceIn(0, VOICE_STEPS.lastIndex)] * scale,
            ),
            if (breakpoint == WidgetBreakpoint.COMPACT || breakpoint == WidgetBreakpoint.TINY) 500 else 600,
        )

        private fun statementPaint(scale: Float = 1f): TextPaint = paint(
            colours.onSurface,
            spToPx(
                when (breakpoint) {
                    WidgetBreakpoint.TINY -> 13f
                    WidgetBreakpoint.COMPACT -> 15f
                    WidgetBreakpoint.STANDARD, WidgetBreakpoint.WIDE -> 19f
                    WidgetBreakpoint.EXPANDED -> 30f
                    WidgetBreakpoint.HERO -> 34f
                } * scale,
            ),
            600,
        )

        private fun subheadPaint(scale: Float = 1f): TextPaint =
            paint(colours.onSurface, spToPx((if (breakpoint.isLarge) 23f else 16f) * scale), 600)

        private fun metricPaint(scale: Float = 1f): TextPaint =
            paint(colours.onSurfaceMuted, spToPx((if (breakpoint.isLarge) 15f else 13f) * scale), 500)

        private fun chipPaint(colour: Color): TextPaint = paint(colour, spToPx(12f), 600)

        private fun pillPaint(scale: Float = 1f): TextPaint =
            paint(colours.pillText, spToPx((if (breakpoint.isLarge) 14f else 13f) * scale), 500)

        private fun ctaPaint(scale: Float = 1f): TextPaint =
            paint(colours.callToAction, spToPx(11f * scale), 600, trackingEm = 0.1f)

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

    // Concurrent: several widgets can compose at once, and a plain HashMap can
    // corrupt — or spin — when two of them resolve a weight simultaneously.
    private val typefaces = java.util.concurrent.ConcurrentHashMap<Int, Typeface>()

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
