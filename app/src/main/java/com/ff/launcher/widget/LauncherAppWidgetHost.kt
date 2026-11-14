package com.ff.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The launcher's [AppWidgetHost]. The host id must be stable across sessions and reboots
 * so widget bindings made through [android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK]
 * keep resolving to this host.
 *
 * Overrides [onCreateView] to return [LauncherAppWidgetHostView], which intercepts
 * long-press gestures across the entire widget hierarchy without breaking child clicks.
 */
class LauncherAppWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo,
    ): AppWidgetHostView = LauncherAppWidgetHostView(context)

    private companion object {
        /** Arbitrary stable id; FF Launcher is the only host this app registers. */
        const val HOST_ID = 0x004646
    }
}

/**
 * Custom [AppWidgetHostView] that detects long-presses during touch dispatch.
 *
 * Standard Android widgets contain clickable children (PendingIntents) that swallow
 * touch events, starving any outer Compose `combinedClickable` wrapper. By monitoring
 * ACTION_DOWN inside [onInterceptTouchEvent] and posting a timeout runnable, we can:
 * 1. Let normal taps and scrolls pass straight through to the widget's RemoteViews.
 * 2. Intercept the gesture and fire [onLongPress] the instant the finger holds still
 *    for [ViewConfiguration.getLongPressTimeout], sending ACTION_CANCEL to children
 *    automatically so the widget's own click never fires on release.
 *
 * It also consumes unconsumed leftovers in [onTouchEvent] so taps on a widget's blank
 * padding don't fall through to the deck's dismiss-on-tap, and it reports the laid-out
 * size to the widget ([updateAppWidgetSizeFromLayout]) so Android 12+ size-responsive
 * widgets render at the slot's real dimensions.
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var lastReportedSize: Pair<Int, Int>? = null

    private val longPressRunnable = Runnable {
        val callback = onLongPress ?: return@Runnable
        longPressTriggered = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        callback.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressTriggered = false
                downX = ev.x
                downY = ev.y
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                if (hypot(ev.x - downX, ev.y - downY) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPressRunnable)
            }
        }
        // Once the long-press fires, returning true steals the touch stream from child
        // RemoteViews (Android automatically dispatches ACTION_CANCEL to them).
        return longPressTriggered || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!longPressTriggered && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true
                }
            }
        }
        // Consume the touch stream even if the widget background has no clickable
        // children, so the long-press timer stays alive until timeout or finger lift,
        // and taps on blank widget areas don't dismiss the deck.
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(longPressRunnable)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            // A scrollable child (collection widget: calendar agenda, mail list…) has
            // claimed the gesture — its scrolling must never be mistaken for a long-press.
            removeCallbacks(longPressRunnable)
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        longPressTriggered = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Fires for ANY native size change (layout, slot weight changes in Phase 3) —
        // no need for the Compose update block to poll via post().
        updateAppWidgetSizeFromLayout()
    }

    /** Reports the laid-out size (in dp) to the widget once per actual change. */
    @Suppress("DEPRECATION")
    fun updateAppWidgetSizeFromLayout() {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val widthDp = (width / density).roundToInt()
        val heightDp = (height / density).roundToInt()
        val size = widthDp to heightDp
        if (lastReportedSize == size) return
        lastReportedSize = size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        } else {
            updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
    }
}
