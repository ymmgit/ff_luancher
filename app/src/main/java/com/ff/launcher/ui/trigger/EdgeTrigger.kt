package com.ff.launcher.ui.trigger

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ff.launcher.R
import com.ff.launcher.data.TriggerConfig
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Android caps the vertical size of a single gesture-exclusion rect at 200dp. */
private const val MAX_EXCLUSION_HEIGHT_DP = 200

/** How far (in dp) a finger must travel inward before a swipe is recognized. */
private const val SWIPE_THRESHOLD_DP = 14

/**
 * The subtle, user-tunable edge trigger pill.
 *
 * The 24dp-wide outer box is the touch target; the thin rounded pill inside is the visual.
 * Whenever [TriggerConfig.mode] includes swipe, the pill's pixel rect is published to
 * [android.view.View.setSystemGestureExclusionRects] (height clamped to Android's 200dp
 * limit) so inward swipes never collide with the system edge-back gesture. With
 * [TriggerMode.TAP_ONLY] the exclusion list is cleared, leaving back gestures unimpeded.
 *
 * @param gesturesEnabled When false the trigger detaches from hit-testing entirely: its
 *   pointerInput blocks exit without installing any detector (so ACTION_DOWN is never
 *   consumed — a merely "flagged off" tap detector would still swallow the down and
 *   starve anything underneath it), its a11y click action is removed, its gesture
 *   exclusion rect is cleared, and the visual pill dims to signal the hand-over. Used
 *   when the trigger is parked inside the wave scroller's band on the same edge.
 */
@Composable
fun EdgeTrigger(
    config: TriggerConfig,
    isSecondScreenOpen: Boolean = false,
    onActivate: () -> Unit,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnActivate by rememberUpdatedState(onActivate)
    val description = stringResource(R.string.a11y_edge_trigger)

    // Pill bounds in the host view's coordinate space, or null while cleared.
    var exclusionRect by remember { mutableStateOf<Rect?>(null) }
    val viewLocation = remember { IntArray(2) }

    // Publish (and — crucially — clear on dispose) the exclusion rect on the host view;
    // otherwise the edge-back gesture stays dead in this strip after the trigger leaves
    // the composition tree or the window changes.
    // When the secondary screen (widget deck) is open, NEVER exclude system back gestures!
    DisposableEffect(view, exclusionRect, config.mode, gesturesEnabled, isSecondScreenOpen) {
        val rect = exclusionRect
        view.systemGestureExclusionRects =
            if (gesturesEnabled && !isSecondScreenOpen && config.swipeEnabled && rect != null) {
                listOf(rect)
            } else {
                emptyList()
            }
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = description
                if (gesturesEnabled) {
                    onClick {
                        currentOnActivate()
                        true
                    }
                }
            }
            .width(24.dp) // 24dp touch target wrapper around the thin visual pill
            .height(config.heightDp.dp)
            .onGloballyPositioned { coords ->
                // Nothing to reserve when the trigger is handed over or tap-only — skip
                // the geometry work and keep the rect state honestly cleared.
                if (!gesturesEnabled || !config.swipeEnabled) {
                    if (exclusionRect != null) exclusionRect = null
                    return@onGloballyPositioned
                }
                val bounds = coords.boundsInWindow()
                view.getLocationInWindow(viewLocation) // reused array — no per-layout alloc
                val maxExclusionHeightPx = with(density) { MAX_EXCLUSION_HEIGHT_DP.dp.toPx() }
                val exclusionHeightPx = bounds.height.coerceAtMost(maxExclusionHeightPx)
                val shrink = (bounds.height - exclusionHeightPx) / 2f
                val rect = Rect(
                    (bounds.left - viewLocation[0]).roundToInt(),
                    (bounds.top - viewLocation[1] + shrink).roundToInt(),
                    (bounds.right - viewLocation[0]).roundToInt(),
                    (bounds.top - viewLocation[1] + shrink + exclusionHeightPx).roundToInt(),
                )
                if (rect != exclusionRect) exclusionRect = rect
            }
            .pointerInput(config.mode, config.edgeSide, gesturesEnabled) {
                if (config.tapEnabled && gesturesEnabled) {
                    detectTapGestures(onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnActivate()
                    })
                }
            }
            .pointerInput(config.mode, config.edgeSide, gesturesEnabled, isSecondScreenOpen) {
                if (config.swipeEnabled && gesturesEnabled) {
                    val swipeThreshold = SWIPE_THRESHOLD_DP.dp.toPx()
                    // Physical sides: RIGHT pill swipes left (negative x), LEFT pill right.
                    val inwardSign = if (config.edgeSide == TriggerEdgeSide.RIGHT) -1f else 1f
                    var accumulated = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulated = 0f
                            fired = false
                        },
                        onDragEnd = {
                            accumulated = 0f
                            fired = false
                        },
                        onDragCancel = {
                            accumulated = 0f
                            fired = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            accumulated += dragAmount
                            if (!fired && abs(accumulated) >= swipeThreshold) {
                                if (isSecondScreenOpen) {
                                    change.consume()
                                    fired = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnActivate()
                                } else if (accumulated * inwardSign > 0f) {
                                    change.consume()
                                    fired = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnActivate()
                                }
                            }
                        },
                    )
                }
            },
    ) {
        // Dim the pill while it has handed this strip over to the wave scroller. A plain
        // scale-down with no floor: the disabled pill must never be brighter than the
        // enabled one, and opacity 0 (invisible by design) stays invisible either way.
        val pillAlpha = if (gesturesEnabled) config.opacity else config.opacity * 0.35f
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(config.widthDp.dp)
                .height(config.heightDp.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = pillAlpha)),
        )
    }
}
