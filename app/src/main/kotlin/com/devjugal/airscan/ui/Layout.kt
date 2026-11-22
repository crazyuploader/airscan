package com.devjugal.airscan.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** A phone in landscape, or any window at least 600 dp wide, gets a side rail and several columns. */
fun isWide(
    widthDp: Float,
    heightDp: Float,
): Boolean = widthDp >= 600f || widthDp > heightDp

@Composable
fun rememberIsWide(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { isWide(size.width.toDp().value, size.height.toDp().value) }
}

/** Adds a side margin to the system insets, so lists scroll behind the bars and stay clear of cutouts. */
@Composable
fun PaddingValues.withMargin(margin: Dp = 16.dp): PaddingValues {
    val direction: LayoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + margin,
        top = calculateTopPadding(),
        end = calculateEndPadding(direction) + margin,
        bottom = calculateBottomPadding(),
    )
}

/** A grid item that spans every column, for titles and wide blocks. */
fun LazyGridScope.fullItem(content: @Composable LazyGridItemScope.() -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, content = content)
