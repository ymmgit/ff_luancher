package com.ff.launcher.data.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TimeData(
    val timeText: String = "",
    val amPmText: String = "",
    val dateText: String = "",
    val dayOfWeek: String = "",
    val epochMs: Long = System.currentTimeMillis(),
)

/**
 * Provides battery-efficient system time updates.
 * Listens to [Intent.ACTION_TIME_TICK], [Intent.ACTION_TIME_CHANGED], and
 * [Intent.ACTION_TIMEZONE_CHANGED] without continuous polling.
 */
class TimeProvider(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _timeState = MutableStateFlow(computeCurrentTime())
    val timeState: StateFlow<TimeData> = _timeState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            _timeState.value = computeCurrentTime()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
    }

    fun computeCurrentTime(): TimeData {
        val now = Date()
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm"
        val timeSdf = SimpleDateFormat(timePattern, Locale.getDefault())
        val amPmSdf = SimpleDateFormat("a", Locale.getDefault())
        val dateSdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val daySdf = SimpleDateFormat("EEEE", Locale.getDefault())

        return TimeData(
            timeText = timeSdf.format(now),
            amPmText = if (is24Hour) "" else amPmSdf.format(now),
            dateText = dateSdf.format(now),
            dayOfWeek = daySdf.format(now),
            epochMs = now.time,
        )
    }

    fun dispose() {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
