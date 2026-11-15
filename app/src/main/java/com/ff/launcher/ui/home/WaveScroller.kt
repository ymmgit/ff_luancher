package com.ff.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Locale-aware section scroller docked to the right edge.
 *
 * Scrolling/dragging through the alphabetical scroller strictly operates on the
 * app section labels (A–Z, #). No matter how far down the user drags, the selection
 * and list position clamp to the last alphabetical apps, never jumping into the settings page.
 *
 * The bottom ⚙ settings icon is an independent tap target that only opens settings when
 * tapped directly, preventing accidental activation during scroll flings or downward drags.
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
    var alphabetHeightPx by remember { mutableFloatStateOf(1f) }

    // Let the last selection linger briefly after release, then fade out.
    LaunchedEffect(isDragging, activeIndex) {
        if (!isDragging) {
            delay(500)
            activeIndex = -1
        }
    }

    fun activate(index: Int) {
        if (sectionLabels.isEmpty()) return
        val clamped = index.coerceIn(0, sectionLabels.size - 1)
        if (clamped == activeIndex) return
        activeIndex = clamped
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSectionSelected(clamped)
    }

    Box(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Alphabetical Strip (strictly maps to sectionLabels)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .onSizeChanged { alphabetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(sectionLabels) {
                        if (sectionLabels.isEmpty()) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val pointerId = down.id
                            isDragging = true
                            currentOnRevealChanged(true)
                            try {
                                thumbY = down.position.y.coerceIn(0f, size.height.toFloat())
                                activate(indexAt(thumbY, size.height, sectionLabels.size))

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (change.pressed) {
                                        change.consume()
                                        thumbY = change.position.y.coerceIn(0f, size.height.toFloat())
                                        activate(indexAt(thumbY, size.height, sectionLabels.size))
                                    } else {
                                        break
                                    }
                                } while (true)
                            } finally {
                                isDragging = false
                                currentOnRevealChanged(false)
                            }
                        }
                    },
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
            }

            Spacer(Modifier.height(4.dp))

            // Independent Gear Settings Button (Only activates on direct tap)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSettingsSelected()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.a11y_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // Wave bubble: floats left of the thumb showing the active letter
        androidx.compose.animation.AnimatedVisibility(
            visible = isDragging && activeIndex in sectionLabels.indices,
            enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(90)),
            exit = scaleOut(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val bubbleHeightPx = 52.dp.toPx()
                    val maxY = (alphabetHeightPx - bubbleHeightPx).coerceAtLeast(0f)
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

private fun indexAt(y: Float, heightPx: Int, nodeCount: Int): Int {
    if (nodeCount <= 0 || heightPx <= 0) return 0
    return ((y / heightPx) * nodeCount).toInt().coerceIn(0, nodeCount - 1)
}
