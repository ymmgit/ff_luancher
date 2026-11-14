package com.ff.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ff.launcher.R
import com.ff.launcher.ui.theme.BubbleBackground
import com.ff.launcher.ui.theme.TextPrimary
import com.ff.launcher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Locale-aware section scroller docked to the right edge. The strip renders exactly one
 * node per populated section (labels come from ICU in the device language — pinyin
 * letters on zh, kana buckets on ja, jamo on ko…) plus a settings node at the bottom,
 * so there are no dead nodes to fall back from. A single [awaitEachGesture] state
 * machine drives everything: the instant the finger lands (Down, zero touch-slop
 * latency) the list jumps and the bubble pops; moves track the **absolute** finger
 * position of the initially pressed pointerId only — stray second fingers can't yank
 * it — with a [HapticFeedbackType.TextHandleMove] tick on every node crossing, and
 * release on ⚙ opens settings. [onRevealChanged] toggles the collapsed favorites-only
 * home while the strip is in use.
 */
@Composable
fun WaveScroller(
    sectionLabels: List<String>,
    onSectionSelected: (Int) -> Unit,
    onSettingsSelected: () -> Unit,
    onRevealChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSectionSelected by rememberUpdatedState(onSectionSelected)
    val currentOnSettingsSelected by rememberUpdatedState(onSettingsSelected)
    val currentOnRevealChanged by rememberUpdatedState(onRevealChanged)
    val description = stringResource(R.string.a11y_wave_scroller)

    var activeIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    var thumbY by remember { mutableFloatStateOf(0f) }
    var scrollerHeightPx by remember { mutableFloatStateOf(1f) }

    // Let the last selection linger briefly after release, then fade out.
    LaunchedEffect(isDragging, activeIndex) {
        if (!isDragging) {
            delay(600)
            activeIndex = -1
        }
    }

    val settingsIndex = sectionLabels.size

    fun activate(index: Int) {
        if (index == activeIndex) return
        activeIndex = index
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (index < settingsIndex) currentOnSectionSelected(index)
    }

    Box(
        modifier = modifier
            .onSizeChanged { scrollerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .semantics { contentDescription = description }
            .pointerInput(sectionLabels) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Lock onto the initial pointer: a stray second finger elsewhere on
                    // the screen must not yank thumbY (and the bubble) between heights.
                    val pointerId = down.id
                    isDragging = true
                    currentOnRevealChanged(true)
                    try {
                        thumbY = down.position.y.coerceIn(0f, size.height.toFloat())
                        activate(indexAt(thumbY, size.height, settingsIndex + 1))

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: break
                            if (change.pressed) {
                                change.consume()
                                thumbY = change.position.y.coerceIn(0f, size.height.toFloat())
                                activate(indexAt(thumbY, size.height, settingsIndex + 1))
                            } else {
                                break // the tracked finger lifted — end the gesture
                            }
                        } while (true)

                        // The settings node activates on release so a drag passing
                        // through it never accidentally opens settings. Kept inside the
                        // try: a cancelled gesture must not open settings.
                        if (activeIndex == settingsIndex) currentOnSettingsSelected()
                    } finally {
                        // awaitPointerEvent can throw CancellationException when the
                        // system steals the gesture or this node leaves composition —
                        // the reset below MUST run on that path too, or the bubble and
                        // dragging state stay latched forever.
                        isDragging = false
                        currentOnRevealChanged(false)
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sectionLabels.forEachIndexed { index, label ->
                val isActive = index == activeIndex
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.4f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "waveSectionScale",
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.a11y_settings),
                tint = if (activeIndex == settingsIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }

        // Wave bubble: floats just left of the thumb so the finger never hides it.
        AnimatedVisibility(
            visible = isDragging && activeIndex in sectionLabels.indices,
            enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(90)),
            exit = scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val bubbleHeightPx = 52.dp.toPx()
                    val maxY = (scrollerHeightPx - bubbleHeightPx).coerceAtLeast(0f)
                    IntOffset(
                        (-64.dp).roundToPx(),
                        (thumbY - bubbleHeightPx / 2f).coerceIn(0f, maxY).roundToInt(),
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sectionLabels.getOrElse(activeIndex) { "" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun indexAt(y: Float, heightPx: Int, nodeCount: Int): Int =
    ((y / heightPx) * nodeCount).toInt().coerceIn(0, nodeCount - 1)
