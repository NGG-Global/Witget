package com.softdread.widgets.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The window's width class, following Material's breakpoints: phones are
 * compact, small tablets and unfolded inners are medium, tablets in landscape
 * (and desktop windows) are expanded.
 *
 * Measured from the window, not the screen, so split-screen and freeform
 * windows get the layout their actual space deserves.
 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }.value
    return when {
        widthDp >= 840f -> WindowWidthClass.EXPANDED
        widthDp >= 600f -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.COMPACT
    }
}
