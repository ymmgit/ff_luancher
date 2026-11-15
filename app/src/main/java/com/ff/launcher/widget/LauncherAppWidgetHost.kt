package com.ff.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
        // Synthesize ACTION_CANCEL so child views abort pending clicks or pressed states
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
        callback.invoke()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
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
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    longPressTriggered = false
                    return true // Consume ACTION_UP after long press so child never fires click
                }
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                removeCallbacks(longPressRunnable)
            }
        }

        if (longPressTriggered) {
            return true
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Once long press is triggered, intercept everything so child views don't receive it
        return longPressTriggered || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (longPressTriggered) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressTriggered = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        // Do NOT cancel longPressRunnable here.
        // Android framework automatically calls cancelLongPress() on ViewGroup when a child
        // view handles ACTION_DOWN. Cancelling here would break long-press on clickable widgets!
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Child views (including clickable Buttons and FrameLayouts in RemoteViews) often
        // call requestDisallowInterceptTouchEvent(true) on touch down.
        // We only cancel the long-press if the finger actually moves beyond touchSlop.
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
