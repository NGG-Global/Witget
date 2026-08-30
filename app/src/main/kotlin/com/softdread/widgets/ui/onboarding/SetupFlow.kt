package com.softdread.widgets.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softdread.widgets.R
import com.softdread.widgets.data.content.ContentRepository
import com.softdread.widgets.data.content.pool
import com.softdread.widgets.data.device.ScreenTimeDataSource
import com.softdread.widgets.design.SoftDreadMotion
import com.softdread.widgets.design.SoftDreadSpacing
import com.softdread.widgets.design.SoftDreadTheme
import com.softdread.widgets.design.SoftDreadTiles
import com.softdread.widgets.design.SoftDreadType
import com.softdread.widgets.design.ThemePack
import com.softdread.widgets.design.entrance
import com.softdread.widgets.design.pressScale
import com.softdread.widgets.design.rememberReducedMotion
import com.softdread.widgets.design.slowPulse
import com.softdread.widgets.domain.model.ColourRole
import com.softdread.widgets.domain.model.Personality
import com.softdread.widgets.domain.model.WidgetBreakpoint
import com.softdread.widgets.domain.model.WidgetType
import com.softdread.widgets.domain.selection.Interpolation
import com.softdread.widgets.ui.PinWidget
import com.softdread.widgets.ui.components.ChoicePill
import com.softdread.widgets.ui.components.Dot
import com.softdread.widgets.ui.components.PillGroup
import com.softdread.widgets.ui.components.PreviewTile
import com.softdread.widgets.ui.components.SpecLine
import com.softdread.widgets.ui.components.WitgetMark
import com.softdread.widgets.ui.components.WitgetWordmark
import com.softdread.widgets.ui.labelRes
import com.softdread.widgets.ui.permissions.PermissionStatus
import com.softdread.widgets.widgets.common.LeadingVisual
import com.softdread.widgets.widgets.common.TileContent
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first-run flow, from the owner's motion concept: a splash brand moment,
 * then five steps — voice, look, the pack, access, home — over a circle field
 * that re-anchors on every step and re-skins with the chosen pack.
 *
 * Everything previewed is real: the voice step's battery tile speaks actual
 * Content Bible lines for each personality, the look step's tiles re-resolve
 * through the same legibility pipeline the widgets use, the access step asks
 * only for what was picked, and the home step hands the picked widgets to the
 * launcher one at a time through the real pin flow.
 */
@Composable
fun SetupFlow(
    selectedPersonality: Personality,
    selectedPack: ThemePack,
    onSelectPersonality: (Personality) -> Unit,
    onSelectPack: (ThemePack) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    skipSplash: Boolean = false,
) {
    val chrome = SoftDreadTheme.chrome
    val reducedMotion = rememberReducedMotion()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var splashDone by rememberSaveable { mutableStateOf(skipSplash || reducedMotion) }
    // Kept as a joined string so plain rememberSaveable handles it.
    var pickedRaw by rememberSaveable {
        mutableStateOf("${'$'}{WidgetType.SCREEN_TIME.id},${'$'}{WidgetType.BATTERY.id},${'$'}{WidgetType.WEATHER.id}")
    }
    val picked = remember(pickedRaw) { pickedRaw.split(',').filter { it.isNotBlank() } }
    val togglePick: (String) -> Unit = { id ->
        pickedRaw = if (id in picked) (picked - id).joinToString(",") else (picked + id).joinToString(",")
    }

    Box(modifier = modifier.fillMaxSize().background(chrome.wallpaper)) {
        SetupCircleField(step = step, pack = selectedPack, isDark = chrome.isDark)

        // The circle field stays full-bleed behind the bars; the content does
        // not. Nothing here sits in a Scaffold, so the insets are applied by
        // hand — without this the first screen a user ever sees put its header
        // under the status bar.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = SoftDreadSpacing.XLarge),
        ) {
            SetupHeader(
                step = step,
                visible = splashDone,
                onSkip = onFinish,
            )
            ProgressTrack(step = step)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (splashDone) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val forward = targetState > initialState
                            (slideInVertically(SoftDreadMotion.settle()) { if (forward) it / 8 else -it / 8 } +
                                fadeIn(SoftDreadMotion.settle()))
                                .togetherWith(fadeOut(tween(180)))
                        },
                        label = "setupStep",
                    ) { current ->
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (current) {
                                0 -> IntroStep()
                                1 -> VoiceStep(selectedPersonality, onSelectPersonality)
                                2 -> LookStep(selectedPack, onSelectPack)
                                3 -> WidgetsStep(picked, togglePick)
                                4 -> AccessStep(picked)
                                5 -> HomeStep(picked)
                                else -> DoneStep(selectedPersonality, selectedPack, picked.size)
                            }
                        }
                    }
                }
            }

            SetupFooter(
                step = step,
                pickedCount = picked.size,
                visible = splashDone,
                onStep = { step = it },
                onAdvance = { if (step < 6) step++ else onFinish() },
            )
        }

        if (!splashDone) {
            BrandSplash(onDone = { splashDone = true })
        }
    }
}

// ------------------------------------------------------------------ the field

/**
 * The four circles behind everything. Anchors are the concept's, converted to
 * fractions of its 392x872 canvas, one set per step; each transition glides
 * over 1200ms and the colours cross-fade when the pack changes. A slow drift
 * loop rides on top so the field never sits perfectly still.
 */
@Composable
private fun SetupCircleField(step: Int, pack: ThemePack, isDark: Boolean) {
    val reduced = rememberReducedMotion()
    // x, y, scale per circle per step, as fractions of the concept canvas.
    val anchors = remember {
        listOf(
            listOf(Triple(0.474f, -0.119f, 1.18f), Triple(-0.327f, 0.557f, 1.00f), Triple(0.546f, 0.695f, 0.92f), Triple(0.015f, 0.067f, 0.80f)),
            listOf(Triple(0.607f, -0.158f, 0.94f), Triple(-0.383f, 0.450f, 1.24f), Triple(0.643f, 0.771f, 0.80f), Triple(-0.071f, 0.151f, 0.72f)),
            listOf(Triple(-0.235f, -0.144f, 1.06f), Triple(0.592f, 0.502f, 0.90f), Triple(-0.163f, 0.748f, 1.14f), Triple(0.684f, 0.110f, 0.86f)),
            listOf(Triple(0.526f, -0.197f, 0.82f), Triple(-0.449f, 0.628f, 1.10f), Triple(0.500f, 0.803f, 1.02f), Triple(-0.087f, 0.257f, 0.90f)),
            listOf(Triple(-0.301f, -0.083f, 1.24f), Triple(0.607f, 0.672f, 0.96f), Triple(-0.148f, 0.365f, 0.80f), Triple(0.658f, 0.851f, 1.02f)),
            listOf(Triple(0.587f, -0.135f, 1.02f), Triple(-0.367f, 0.716f, 1.16f), Triple(0.582f, 0.397f, 0.76f), Triple(-0.102f, 0.073f, 0.84f)),
            listOf(Triple(0.286f, -0.183f, 1.34f), Triple(-0.235f, 0.539f, 1.28f), Triple(0.526f, 0.775f, 1.06f), Triple(0.097f, 0.172f, 0.96f)),
        )
    }
    val sizes = listOf(0.867f, 0.668f, 0.561f, 0.383f)
    val roles = listOf(ColourRole.CLAY, ColourRole.SAGE, ColourRole.SLATE, ColourRole.AMBER)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val stepAnchors = anchors[step.coerceIn(0, anchors.size - 1)]

        roles.forEachIndexed { index, role ->
            val (fx, fy, scale) = stepAnchors[index]
            val spec = if (reduced) snap<Float>() else tween(1200, easing = EaseOutQuart)
            val x by animateFloatAsState(fx * w, spec, label = "fieldX$index")
            val y by animateFloatAsState(fy * h, spec, label = "fieldY$index")
            val s by animateFloatAsState(scale, spec, label = "fieldS$index")
            val colour by animateColorAsState(
                SoftDreadTiles.colours(role, pack, isDark).surface,
                tween(700),
                label = "fieldC$index",
            )
            val diameter = maxWidth * sizes[index]
            Box(
                Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .size(diameter)
                    .graphicsLayer {
                        scaleX = s
                        scaleY = s
                    }
                    .driftLoop(index, reduced)
                    .clip(CircleShape)
                    .background(colour),
            )
        }
    }
}

/** The concept's dr1..dr4: slow alternate translation loops. */
private fun Modifier.driftLoop(index: Int, reduced: Boolean): Modifier = composed {
    if (reduced) return@composed this
    val transition = rememberInfiniteTransition(label = "setupDrift")
    val periods = listOf(26_000, 34_000, 30_000, 22_000)
    val targets = listOf(34f to 26f, 28f to -36f, -30f to 24f, 22f to 18f)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable<Float>(
            animation = tween(periods[index % 4], easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driftPhase$index",
    )
    val (dx, dy) = targets[index % 4]
    graphicsLayer {
        translationX = dx * density * phase
        translationY = dy * density * phase
    }
}

// ------------------------------------------------------------------- chrome

@Composable
private fun SetupHeader(step: Int, visible: Boolean, onSkip: () -> Unit) {
    val chrome = SoftDreadTheme.chrome
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "headerAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = SoftDreadSpacing.Large)
            .height(46.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WitgetMark(size = 34)
        Spacer(Modifier.width(9.dp))
        WitgetWordmark(fontSize = 21.sp)
        Spacer(Modifier.weight(1f))
        if (step in 1..5) {
            SpecLine("%02d / 05".format(step))
            Spacer(Modifier.width(SoftDreadSpacing.Large))
        }
        Text(
            text = stringResource(R.string.setup_skip),
            style = MaterialTheme.typography.labelLarge,
            color = chrome.secondaryType,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProgressTrack(step: Int) {
    val chrome = SoftDreadTheme.chrome
    val fraction by animateFloatAsState(
        targetValue = (step / 6f).coerceIn(0f, 1f),
        animationSpec = tween(800, easing = androidx.compose.animation.core.EaseOutQuart),
        label = "progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(chrome.onSurface.copy(alpha = 0.13f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(chrome.brandDot),
        )
    }
}

@Composable
private fun SetupFooter(
    step: Int,
    pickedCount: Int,
    visible: Boolean,
    onStep: (Int) -> Unit,
    onAdvance: () -> Unit,
) {
    val chrome = SoftDreadTheme.chrome
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "footerAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (step in 1..5) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { index ->
                    val width by animateDpAsState(
                        if (index == step) 26.dp else 8.dp,
                        SoftDreadMotion.pop(),
                        label = "setupDot",
                    )
                    Box(
                        Modifier
                            .width(width)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (index == step) chrome.brandDot else chrome.onSurface.copy(alpha = 0.2f))
                            // Dots jump back to any step already passed.
                            .clickable(enabled = index < step, role = Role.Button) { onStep(index) },
                    )
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        val label = when (step) {
            0 -> stringResource(R.string.setup_cta_begin)
            5 -> stringResource(R.string.setup_cta_next)
            6 -> stringResource(R.string.setup_cta_open)
            else -> stringResource(R.string.setup_cta_next)
        }
        val enabled = step != 3 || pickedCount > 0
        val interactionSource = remember { MutableInteractionSource() }
        val scale = pressScale(interactionSource)
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(26.dp))
                .background(if (enabled) chrome.accent else chrome.hairline)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onAdvance,
                )
                .padding(horizontal = 28.dp, vertical = 15.dp),
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "ctaLabel",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) chrome.onAccent else chrome.secondaryType,
                )
            }
        }
    }
}

// -------------------------------------------------------------------- steps

@Composable
private fun IntroStep() {
    val chrome = SoftDreadTheme.chrome
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.entrance(0)) {
            Text(
                text = stringResource(R.string.setup_intro_headline),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 33.sp),
                color = chrome.onSurface,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Box(Modifier.entrance(1)) { SpecLine(stringResource(R.string.setup_intro_spec)) }
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Box(Modifier.entrance(2)) {
            Text(
                text = stringResource(R.string.setup_intro_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.secondaryType,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

@Composable
private fun StepHeading(spec: String, title: String, sub: String) {
    val chrome = SoftDreadTheme.chrome
    Column {
        Box(Modifier.entrance(0)) { SpecLine(spec) }
        Spacer(Modifier.height(SoftDreadSpacing.Medium))
        Box(Modifier.entrance(1)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = chrome.onSurface)
        }
        Spacer(Modifier.height(SoftDreadSpacing.Small))
        Box(Modifier.entrance(2)) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.secondaryType,
                modifier = Modifier.widthIn(max = 380.dp),
            )
        }
    }
}

@Composable
private fun VoiceStep(selected: Personality, onSelect: (Personality) -> Unit) {
    val context = LocalContext.current
    val chrome = SoftDreadTheme.chrome

    // Real Content Bible lines: the 15-24% battery band, first response per
    // personality, with the percent resolved — the same string the widget
    // itself could show. No anti-repeat history is touched.
    val voiceLines by produceState(emptyMap<Personality, String>()) {
        val document = ContentRepository.get(context).document(WidgetType.BATTERY)
        value = Personality.entries.associateWith { personality ->
            document.pool("BAT_B6", personality).firstOrNull()
                ?.let { Interpolation.interpolate(it.text, mapOf("percent" to "23")) }
                ?: ""
        }
    }

    Column {
        StepHeading(
            stringResource(R.string.setup_step_voice),
            stringResource(R.string.setup_voice_title),
            stringResource(R.string.setup_voice_sub),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Large))

        Box(Modifier.entrance(3)) {
            AnimatedContent(
                targetState = voiceLines[selected].orEmpty(),
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 10 })
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "voiceTile",
            ) { line ->
                PreviewTile(
                    role = ColourRole.SAGE,
                    breakpoint = WidgetBreakpoint.STANDARD,
                    content = TileContent(
                        label = "battery",
                        heroValue = "23%",
                        voice = line,
                        leading = LeadingVisual.Ring(0.23f),
                        contentDescription = "Battery preview: 23 percent. $line",
                    ),
                    isDark = chrome.isDark,
                    modifier = Modifier.widthIn(max = 380.dp),
                )
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Box(Modifier.entrance(4)) {
            PillGroup {
                Personality.entries.forEach { personality ->
                    ChoicePill(
                        label = stringResource(personality.labelRes),
                        selected = personality == selected,
                        onClick = { onSelect(personality) },
                    )
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Row(Modifier.entrance(5), verticalAlignment = Alignment.Top) {
            Dot(colour = chrome.brandDot, size = 7.dp, modifier = Modifier.padding(top = 6.dp).slowPulse())
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(160)) },
                label = "voiceBlurb",
            ) { personality ->
                Text(
                    text = stringResource(personality.blurbResCompat()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

private fun Personality.blurbResCompat(): Int = when (this) {
    Personality.NEUTRAL -> R.string.personality_neutral_blurb
    Personality.FRIENDLY -> R.string.personality_friendly_blurb
    Personality.DRY -> R.string.personality_dry_blurb
    Personality.SARCASTIC -> R.string.personality_sarcastic_blurb
    Personality.CHAOTIC -> R.string.personality_chaotic_blurb
}

@Composable
private fun LookStep(selected: ThemePack, onSelect: (ThemePack) -> Unit) {
    val chrome = SoftDreadTheme.chrome
    Column {
        StepHeading(
            stringResource(R.string.setup_step_look),
            stringResource(R.string.setup_look_title),
            stringResource(R.string.setup_look_sub),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Large))

        // Three mini tiles that re-tint live through the real colour pipeline.
        Row(Modifier.entrance(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniTile(ColourRole.CLAY, selected, "screen", "0.7")
            MiniTile(ColourRole.SAGE, selected, "battery", "23%")
            MiniTile(ColourRole.SLATE, selected, "sleeps", "12")
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Column(Modifier.entrance(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePack.entries.forEach { pack ->
                PackRow(pack = pack, selected = pack == selected, onClick = { onSelect(pack) })
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

@Composable
private fun MiniTile(role: ColourRole, pack: ThemePack, label: String, hero: String) {
    val chrome = SoftDreadTheme.chrome
    val colours = SoftDreadTiles.colours(role, pack, chrome.isDark)
    val surface by animateColorAsState(colours.surface, tween(600), label = "miniSurface")
    val on by animateColorAsState(colours.onSurface, tween(600), label = "miniOn")
    Box(
        Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(surface),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 40.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(colours.circleTint),
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = SoftDreadType.MicroLabel.copy(fontSize = 9.sp),
                color = on.copy(alpha = 0.82f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = hero,
                style = SoftDreadType.Hero2x2.copy(fontSize = 34.sp),
                color = on,
            )
        }
    }
}

@Composable
private fun PackRow(pack: ThemePack, selected: Boolean, onClick: () -> Unit) {
    val chrome = SoftDreadTheme.chrome
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    val border by animateColorAsState(
        if (selected) chrome.accentText else chrome.hairline,
        SoftDreadMotion.settle(),
        label = "packBorder",
    )
    val dotScale by animateFloatAsState(if (selected) 1f else 0f, SoftDreadMotion.pop(), label = "packDot")
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(chrome.surface.copy(alpha = 0.72f))
            .border(
                androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, border),
                RoundedCornerShape(22.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(ColourRole.CLAY, ColourRole.EMBER, ColourRole.AMBER, ColourRole.SAGE, ColourRole.SLATE)
                .forEach { role ->
                    val swatch by animateColorAsState(
                        SoftDreadTiles.colours(role, pack, chrome.isDark).surface,
                        tween(600),
                        label = "swatch",
                    )
                    Box(
                        Modifier
                            .width(15.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(swatch),
                    )
                }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(pack.labelResCompat()),
            style = MaterialTheme.typography.titleMedium,
            color = chrome.onSurface,
            modifier = Modifier.weight(1f),
        )
        Dot(
            colour = chrome.brandDot,
            size = 9.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = dotScale
                scaleY = dotScale
                alpha = dotScale
            },
        )
    }
}

private fun ThemePack.labelResCompat(): Int = when (this) {
    ThemePack.CLAY_HOUSE -> R.string.theme_clay_house
    ThemePack.DUSK_HOUSE -> R.string.theme_dusk_house
    ThemePack.ORCHARD_HOUSE -> R.string.theme_orchard_house
    ThemePack.PAPER_HOUSE -> R.string.theme_paper_house
}

@Composable
private fun WidgetsStep(picked: List<String>, onToggle: (String) -> Unit) {
    val chrome = SoftDreadTheme.chrome
    Column {
        StepHeading(
            stringResource(R.string.setup_step_pack),
            stringResource(R.string.setup_pack_title),
            stringResource(R.string.setup_pack_sub),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Large))

        val entries = WidgetType.entries
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            entries.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.entrance(3 + rowIndex),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { type ->
                        WidgetToggleCard(
                            type = type,
                            selected = type.id in picked,
                            onToggle = { onToggle(type.id) },
                            modifier = Modifier.weight(1f).widthIn(max = 220.dp),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Row(Modifier.entrance(8), verticalAlignment = Alignment.CenterVertically) {
            Dot(colour = chrome.brandDot, size = 7.dp, modifier = Modifier.slowPulse())
            Spacer(Modifier.width(9.dp))
            SpecLine(
                if (picked.isEmpty()) stringResource(R.string.setup_picked_none)
                else stringResource(R.string.setup_picked_count, picked.size),
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

private fun needRes(type: WidgetType): Int? = when (type) {
    WidgetType.SCREEN_TIME -> R.string.setup_need_usage
    WidgetType.DAY_VIBE -> R.string.setup_need_calendar
    WidgetType.WEATHER -> R.string.setup_need_location
    WidgetType.COUNTDOWN -> R.string.setup_need_date
    else -> null
}

@Composable
private fun WidgetToggleCard(
    type: WidgetType,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = SoftDreadTheme.chrome
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    val border by animateColorAsState(
        if (selected) chrome.accentText else chrome.hairline,
        SoftDreadMotion.settle(),
        label = "cardBorder",
    )
    val tickScale by animateFloatAsState(if (selected) 1f else 0f, SoftDreadMotion.pop(), label = "tick")
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(chrome.surface.copy(alpha = 0.72f))
            .border(
                androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, border),
                RoundedCornerShape(20.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SoftDreadTiles.colours(type.colourRole, dark = chrome.isDark).surface),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(com.softdread.widgets.ui.WidgetCatalog.entry(type).nameRes),
                style = MaterialTheme.typography.titleSmall,
                color = chrome.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // The tick: the pack's own mark, drawn, no icon font.
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = tickScale
                        scaleY = tickScale
                        alpha = tickScale
                    }
                    .clip(CircleShape)
                    .background(chrome.accent),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(9.dp)) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    )
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.05f, size.height * 0.55f)
                        lineTo(size.width * 0.4f, size.height * 0.9f)
                        lineTo(size.width * 0.95f, size.height * 0.15f)
                    }
                    drawPath(path, chromeTickColour, style = stroke)
                }
            }
        }
        needRes(type)?.let {
            Spacer(Modifier.height(7.dp))
            Text(
                text = stringResource(it),
                style = SoftDreadType.Mono.copy(fontSize = 9.5.sp),
                color = chrome.secondaryType,
            )
        }
    }
}

private val chromeTickColour = androidx.compose.ui.graphics.Color(0xFFFFF6EC)

@Composable
private fun AccessStep(picked: List<String>) {
    val context = LocalContext.current
    val chrome = SoftDreadTheme.chrome
    var permissions by remember { mutableStateOf(PermissionStatus.read(context)) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissions = PermissionStatus.read(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = PermissionStatus.read(context)
    }

    data class Perm(val titleRes: Int, val bodyRes: Int, val actionRes: Int, val granted: Boolean, val grant: () -> Unit)

    val perms = buildList {
        if (WidgetType.SCREEN_TIME.id in picked) {
            add(
                Perm(
                    R.string.permission_usage_title, R.string.permission_usage_body,
                    R.string.permission_usage_action, permissions.hasUsageAccess,
                ) { settingsLauncher.launch(ScreenTimeDataSource(context).settingsIntent()) },
            )
        }
        if (WidgetType.DAY_VIBE.id in picked) {
            add(
                Perm(
                    R.string.permission_calendar_title, R.string.permission_calendar_body,
                    R.string.permission_calendar_action, permissions.hasCalendar,
                ) { permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR) },
            )
        }
        if (WidgetType.WEATHER.id in picked) {
            add(
                Perm(
                    R.string.permission_location_title, R.string.permission_location_body,
                    R.string.permission_location_action, permissions.hasCoarseLocation,
                ) { permissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
            )
        }
    }

    Column {
        StepHeading(
            stringResource(R.string.setup_step_access),
            stringResource(R.string.setup_access_title),
            stringResource(R.string.setup_access_sub),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Large))

        if (perms.isEmpty()) {
            Column(
                Modifier
                    .entrance(3)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(chrome.surface)
                    .padding(18.dp),
            ) {
                Text(
                    stringResource(R.string.setup_access_none_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = chrome.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.setup_access_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryType,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                perms.forEachIndexed { index, perm ->
                    Column(
                        Modifier
                            .entrance(3 + index)
                            .widthIn(max = 420.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(chrome.surface)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(perm.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = chrome.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            val grantedAlpha by animateFloatAsState(
                                if (perm.granted) 1f else 0f,
                                tween(320),
                                label = "granted",
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.graphicsLayer { alpha = grantedAlpha },
                            ) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(SoftDreadTiles.colours(ColourRole.SAGE, dark = chrome.isDark).surface),
                                )
                                Spacer(Modifier.width(7.dp))
                                SpecLine(stringResource(R.string.setup_granted))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(perm.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = chrome.secondaryType,
                        )
                        if (!perm.granted) {
                            Spacer(Modifier.height(13.dp))
                            com.softdread.widgets.ui.components.SoftDreadOutlinedButton(
                                label = stringResource(perm.actionRes),
                                onClick = perm.grant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Box(Modifier.entrance(6)) { SpecLine(stringResource(R.string.setup_access_footer)) }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

@Composable
private fun HomeStep(picked: List<String>) {
    val context = LocalContext.current
    val chrome = SoftDreadTheme.chrome
    val scope = rememberCoroutineScope()
    val supported = remember { PinWidget.isSupported(context) }
    var placedCount by rememberSaveable { mutableIntStateOf(0) }
    var placing by remember { mutableStateOf(false) }
    val types = picked.mapNotNull { WidgetType.fromId(it) }

    Column {
        StepHeading(
            stringResource(R.string.setup_step_home),
            stringResource(R.string.setup_home_title),
            stringResource(R.string.setup_home_sub),
        )
        Spacer(Modifier.height(SoftDreadSpacing.Large))

        // The launcher mock: the picked tiles plus one dashed ghost slot.
        Column(
            Modifier
                .entrance(3)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(chrome.surface.copy(alpha = 0.72f))
                .padding(16.dp),
        ) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                types.take(6).forEach { type ->
                    val colours = SoftDreadTiles.colours(type.colourRole, dark = chrome.isDark)
                    Column(
                        Modifier
                            .size(74.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colours.surface)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = miniLabel(type),
                            style = SoftDreadType.MicroLabel.copy(fontSize = 7.5.sp),
                            color = colours.onSurface.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = miniHero(type),
                            style = SoftDreadType.Hero2x2.copy(fontSize = 22.sp),
                            color = colours.onSurface,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            androidx.compose.foundation.BorderStroke(1.5.dp, chrome.hairline),
                            RoundedCornerShape(22.dp),
                        ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                repeat(4) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(chrome.onSurface.copy(alpha = 0.1f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Row(Modifier.entrance(4), verticalAlignment = Alignment.CenterVertically) {
            Dot(colour = chrome.brandDot, size = 7.dp, modifier = Modifier.slowPulse())
            Spacer(Modifier.width(9.dp))
            SpecLine(
                when {
                    !supported -> stringResource(R.string.setup_place_manual)
                    placedCount > 0 -> stringResource(R.string.setup_placing, placedCount, types.size)
                    else -> stringResource(R.string.setup_place_ready, types.size)
                },
            )
        }

        if (supported && types.isNotEmpty()) {
            Spacer(Modifier.height(SoftDreadSpacing.Large))
            Box(Modifier.entrance(5)) {
                com.softdread.widgets.ui.components.SoftDreadOutlinedButton(
                    label = stringResource(R.string.setup_cta_place),
                    enabled = !placing,
                    onClick = {
                        placing = true
                        scope.launch {
                            // One at a time: each request opens the launcher's
                            // own sheet, and stacking them reads as spam.
                            types.forEach { type ->
                                if (PinWidget.request(context, type)) placedCount++
                                delay(650)
                            }
                            placing = false
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
    }
}

private fun miniLabel(type: WidgetType): String = when (type) {
    WidgetType.SCREEN_TIME -> "SCREEN"
    WidgetType.DAILY_JOKE -> "JOKE"
    WidgetType.BATTERY -> "BATTERY"
    WidgetType.DAY_VIBE -> "VIBE"
    WidgetType.WEATHER -> "WEATHER"
    WidgetType.COUNTDOWN -> "SLEEPS"
    WidgetType.TIME_PROGRESS -> "TODAY"
    WidgetType.MAGIC_8_BALL -> "8 BALL"
}

private fun miniHero(type: WidgetType): String = when (type) {
    WidgetType.SCREEN_TIME -> "0.7"
    WidgetType.DAILY_JOKE -> "ha"
    WidgetType.BATTERY -> "23%"
    WidgetType.DAY_VIBE -> "4"
    WidgetType.WEATHER -> "34°"
    WidgetType.COUNTDOWN -> "12"
    WidgetType.TIME_PROGRESS -> "61%"
    WidgetType.MAGIC_8_BALL -> "?"
}

@Composable
private fun DoneStep(personality: Personality, pack: ThemePack, count: Int) {
    val chrome = SoftDreadTheme.chrome
    Column(Modifier.padding(top = 90.dp)) {
        Box(Modifier.entrance(0)) {
            Text(
                text = stringResource(R.string.setup_done_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 46.sp),
                color = chrome.onSurface,
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.Large))
        Box(Modifier.entrance(1)) {
            Text(
                text = stringResource(R.string.setup_done_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = chrome.secondaryType,
                modifier = Modifier.widthIn(max = 310.dp),
            )
        }
        Spacer(Modifier.height(SoftDreadSpacing.XLarge))
        Column(Modifier.entrance(2).widthIn(max = 420.dp)) {
            SummaryRow(stringResource(R.string.setup_done_voice), stringResource(personality.labelRes))
            SummaryRow(stringResource(R.string.setup_done_pack), stringResource(pack.labelResCompat()))
            SummaryRow(stringResource(R.string.setup_done_widgets), count.toString(), last = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, last: Boolean = false) {
    val chrome = SoftDreadTheme.chrome
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(chrome.hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                label.uppercase(Locale.getDefault()),
                style = SoftDreadType.MonoLabel,
                color = chrome.secondaryType,
                modifier = Modifier.weight(1f),
            )
            Text(value.uppercase(Locale.getDefault()), style = SoftDreadType.MonoLabel, color = chrome.onSurface)
        }
        if (last) Box(Modifier.fillMaxWidth().height(1.dp).background(chrome.hairline))
    }
}

// ------------------------------------------------------------------- splash

/**
 * The brand moment from the concept, condensed to ~4 seconds and skippable by
 * a tap anywhere: the four quadrant blocks fly in from the corners and punch
 * together, the bubble pops over them, the assembled square gives way to the
 * real mark as two rings burst outward, the wordmark wipes in beside it, and
 * the whole splash releases into the flow.
 */
@Composable
private fun BrandSplash(onDone: () -> Unit) {
    val chrome = SoftDreadTheme.chrome
    val blocks = remember { List(4) { androidx.compose.animation.core.Animatable(0f) } }
    val punch = remember { androidx.compose.animation.core.Animatable(1f) }
    val bubble = remember { androidx.compose.animation.core.Animatable(0f) }
    val squareOut = remember { androidx.compose.animation.core.Animatable(1f) }
    val markIn = remember { androidx.compose.animation.core.Animatable(0f) }
    val rings = remember { List(2) { androidx.compose.animation.core.Animatable(0f) } }
    val wordWipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val splashAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        // Blocks converge, staggered.
        blocks.forEachIndexed { index, block ->
            launch {
                delay(index * 90L)
                block.animateTo(1f, tween(820, easing = EaseOutQuart))
            }
        }
        delay(1080)
        punch.animateTo(1.1f, tween(180))
        punch.animateTo(1f, SoftDreadMotion.pop())
        bubble.animateTo(1f, SoftDreadMotion.pop())
        delay(240)
        // The assembled square hands over to the real mark.
        launch { squareOut.animateTo(0f, tween(380)) }
        launch { markIn.animateTo(1f, SoftDreadMotion.pop()) }
        rings.forEachIndexed { index, ring ->
            launch {
                delay(index * 160L)
                ring.animateTo(1f, tween(900, easing = EaseOutQuart))
            }
        }
        delay(420)
        wordWipe.animateTo(1f, tween(780, easing = EaseOutQuart))
        delay(650)
        splashAlpha.animateTo(0f, tween(500))
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = splashAlpha.value }
            .background(chrome.wallpaper)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                // The four quadrants, converging into the mark's silhouette.
                val corner = 22.dp
                val quadColours = listOf(
                    androidx.compose.ui.graphics.Color(0xFF94A082),
                    androidx.compose.ui.graphics.Color(0xFFCC6848),
                    androidx.compose.ui.graphics.Color(0xFF4C5C71),
                    androidx.compose.ui.graphics.Color(0xFFF0B058),
                )
                val offsets = listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = punch.value * squareOut.value.coerceAtLeast(0.86f)
                            scaleY = punch.value * squareOut.value.coerceAtLeast(0.86f)
                            alpha = squareOut.value
                        },
                ) {
                    offsets.forEachIndexed { index, (ox, oy) ->
                        val progress = blocks[index].value
                        Box(
                            Modifier
                                .align(if (ox < 0) { if (oy < 0) Alignment.TopStart else Alignment.BottomStart } else { if (oy < 0) Alignment.TopEnd else Alignment.BottomEnd })
                                .size(56.dp)
                                .graphicsLayer {
                                    translationX = ox * 320 * density * (1f - progress)
                                    translationY = oy * 280 * density * (1f - progress)
                                    rotationZ = ox * -38f * (1f - progress)
                                    scaleX = 0.55f + 0.45f * progress
                                    scaleY = 0.55f + 0.45f * progress
                                    alpha = progress.coerceIn(0f, 1f)
                                }
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (index == 0) corner * 2 else 0.dp,
                                        topEnd = if (index == 1) corner * 2 else 0.dp,
                                        bottomStart = if (index == 2) corner * 2 else 0.dp,
                                        bottomEnd = if (index == 3) corner * 2 else 0.dp,
                                    ),
                                )
                                .background(quadColours[index]),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(62.dp)
                            .graphicsLayer {
                                scaleX = bubble.value
                                scaleY = bubble.value
                            }
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(0xFFF8E3C8)),
                    )
                }

                // The bursting rings and the real mark taking over.
                rings.forEachIndexed { index, ring ->
                    val progress = ring.value
                    if (progress > 0f && progress < 1f) {
                        Box(
                            Modifier
                                .size(112.dp)
                                .graphicsLayer {
                                    scaleX = 0.42f + 3f * progress
                                    scaleY = 0.42f + 3f * progress
                                    alpha = (1f - progress) * 0.6f
                                }
                                .border(
                                    androidx.compose.foundation.BorderStroke(
                                        if (index == 0) 3.dp else 2.dp,
                                        if (index == 0) androidx.compose.ui.graphics.Color(0xFFCC6848)
                                        else androidx.compose.ui.graphics.Color(0xFFF0B058),
                                    ),
                                    CircleShape,
                                ),
                        )
                    }
                }
                Box(
                    Modifier.graphicsLayer {
                        scaleX = markIn.value
                        scaleY = markIn.value
                        alpha = markIn.value
                        rotationZ = -16f * (1f - markIn.value)
                    },
                ) {
                    WitgetMark(size = 88)
                }
            }

            // The wordmark wipes in beside the mark.
            Box(
                Modifier
                    .padding(start = 20.dp)
                    .graphicsLayer {
                        alpha = if (wordWipe.value > 0f) 1f else 0f
                        translationX = -14f * density * (1f - wordWipe.value)
                    }
                    .drawWithContentClip(wordWipe.value),
            ) {
                WitgetWordmark(fontSize = 44.sp)
            }
        }
    }
}

/** Clips content to a left-to-right reveal fraction: the wordmark's wipe. */
private fun Modifier.drawWithContentClip(fraction: Float): Modifier =
    this.then(
        Modifier.drawWithContent {
            clipRect(right = size.width * fraction.coerceIn(0f, 1f)) {
                this@drawWithContent.drawContent()
            }
        },
    )
