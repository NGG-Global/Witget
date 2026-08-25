package com.softdread.widgets.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The pack's motion language.
 *
 * The design sheet's motion row reads "NONE, EXCEPT 8 BALL TAP SWAP" — but that
 * governs the home-screen tiles, where RemoteViews can't animate anyway. The
 * app around them moves the way the pack looks: circles drift, blocks spring,
 * nothing bounces hard, nothing fades in from nowhere without a direction.
 * Three rules keep it coherent:
 *
 *  - springs over easings for anything the user causes (press, select);
 *  - a single stagger rhythm (60 ms) for anything that enters as a group;
 *  - drift is slow enough to be felt rather than watched (20-40 s cycles).
 */
object SoftDreadMotion {

    /** For selections and colour changes the user triggers. */
    fun <T> settle(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** For playful state pops: the page dot, a newly selected pill. */
    fun <T> pop(): androidx.compose.animation.core.SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Entrance stagger rhythm, in milliseconds per index step. */
    const val STAGGER_MS = 60

    /** Entrance travel distance. */
    val Rise: Dp = 22.dp
}

/**
 * Press feedback for the pack's flat blocks: a slight physical squeeze instead
 * of a Material ripple. The scale reads the same [MutableInteractionSource]
 * the caller passes to its `clickable`, which is what actually connects the
 * press to the animation — a modifier that invents its own source animates
 * nothing.
 */
@Composable
fun pressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = SoftDreadMotion.pop(),
        label = "pressScale",
    )
    return scale
}

/**
 * Staggered entrance: fade in while rising, delayed by [index] steps. Runs once
 * per composition of the host screen.
 */
fun Modifier.entrance(index: Int): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.toLong() * SoftDreadMotion.STAGGER_MS)
        // Fade and rise run together; the rise is slightly longer so the
        // element settles a beat after it is fully visible.
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, tween(420, easing = EaseOutCubic)) }
            launch { rise.animateTo(0f, tween(480, easing = EaseOutCubic)) }
        }
    }
    val risePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        SoftDreadMotion.Rise.toPx()
    }
    graphicsLayer {
        this.alpha = alpha.value
        translationY = rise.value * risePx
    }
}

/**
 * True when the user has disabled animations system-wide (Settings >
 * Accessibility > Remove animations sets the animator scale to zero). Compose
 * does not honour it automatically, so the ambient field checks it and holds
 * still; one-shot entrances and press feedback remain, as they carry state.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** One slowly drifting circle in the ambient field. */
private data class DriftSpec(
    val colour: Color,
    val size: Dp,
    val startX: Float,
    val startY: Float,
    val driftX: Dp,
    val driftY: Dp,
    val periodMs: Int,
)

/**
 * The ambient background: the pack's circles, oversized and slowly drifting.
 * Anchors are fractions of the measured container, so the field composes the
 * same on any screen; motion is pure render-thread translation — no allocation
 * per frame, no blur — so it stays well under frame budget on modest hardware.
 */
@Composable
fun BoxScope.CircleField(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    dim: Float = 1f,
) {
    val specs = remember(isDark) {
        val alpha = if (isDark) 0.10f else 0.16f
        listOf(
            DriftSpec(SoftDreadTiles.colours(com.softdread.widgets.domain.model.ColourRole.CLAY, dark = isDark).surface.copy(alpha = alpha), 340.dp, 0.68f, -0.08f, 34.dp, 26.dp, 26_000),
            DriftSpec(SoftDreadTiles.colours(com.softdread.widgets.domain.model.ColourRole.SAGE, dark = isDark).surface.copy(alpha = alpha), 260.dp, -0.28f, 0.32f, 28.dp, -36.dp, 34_000),
            DriftSpec(SoftDreadTiles.colours(com.softdread.widgets.domain.model.ColourRole.SLATE, dark = isDark).surface.copy(alpha = alpha), 220.dp, 0.62f, 0.72f, -30.dp, 24.dp, 30_000),
            DriftSpec(SoftDreadTiles.colours(com.softdread.widgets.domain.model.ColourRole.AMBER, dark = isDark).surface.copy(alpha = alpha * 0.9f), 150.dp, 0.06f, 0.04f, 22.dp, 18.dp, 22_000),
        )
    }
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "circleField")
    BoxWithConstraints(modifier = modifier.matchParentSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }
        specs.forEachIndexed { index, spec ->
            val phase by if (reducedMotion) {
                remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
            } else {
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable<Float>(
                        animation = tween(spec.periodMs, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "drift$index",
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (parentW * spec.startX + with(density) { spec.driftX.toPx() } * phase).roundToInt(),
                            (parentH * spec.startY + with(density) { spec.driftY.toPx() } * phase).roundToInt(),
                        )
                    }
                    .size(spec.size)
                    .graphicsLayer { alpha = dim }
                    .clip(CircleShape)
                    .background(spec.colour),
            )
        }
    }
}

/** A gentle breathing scale for the app mark; holds still under reduced motion. */
fun Modifier.breathing(): Modifier = composed {
    if (rememberReducedMotion()) return@composed this
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** EaseOutBack for pop-in entrances of small marks. */
val PopInEasing = EaseOutBack
