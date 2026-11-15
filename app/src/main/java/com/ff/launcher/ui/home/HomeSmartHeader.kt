package com.ff.launcher.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ff.launcher.R
import com.ff.launcher.data.glance.CalendarState
import com.ff.launcher.data.glance.HeaderConfig
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.glance.TimeData
import com.ff.launcher.data.glance.WeatherData

/**
 * Modern Google Material 3 Smart Header for FF Launcher Home Screen.
 * Placed at the top of the home screen, supporting Combined At-a-Glance,
 * Clock Focus, Calendar Focus, and Weather Focus modes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeSmartHeader(
    timeData: TimeData,
    calendarState: CalendarState,
    weatherData: WeatherData,
    config: HeaderConfig,
    onModeChange: (HeaderMode) -> Unit,
    onUnitChange: (Boolean) -> Unit,
    onRefreshWeather: () -> Unit,
    onReplaceWidget: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (config.mode == HeaderMode.HIDDEN) return

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showConfigSheet by remember { mutableStateOf(false) }

    val onHeaderLongClick = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        showConfigSheet = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    when (config.mode) {
                        HeaderMode.CLOCK_ONLY -> openClockApp(context)
                        HeaderMode.CALENDAR_ONLY -> openCalendarApp(context)
                        HeaderMode.WEATHER_ONLY -> openWeatherApp(context, weatherData)
                        else -> {}
                    }
                },
                onLongClick = onHeaderLongClick,
            ),
    ) {
        when (config.mode) {
            HeaderMode.COMBINED -> {
                CombinedGlanceView(
                    timeData = timeData,
                    calendarState = calendarState,
                    weatherData = weatherData,
                    useFahrenheit = config.useFahrenheit,
                    onClockClick = { openClockApp(context) },
                    onCalendarClick = { openCalendarApp(context) },
                    onWeatherClick = { openWeatherApp(context, weatherData) },
                    onLongClick = onHeaderLongClick,
                )
            }
            HeaderMode.CLOCK_ONLY -> {
                ClockFocusView(
                    timeData = timeData,
                    onClockClick = { openClockApp(context) },
                )
            }
            HeaderMode.CALENDAR_ONLY -> {
                CalendarFocusView(
                    timeData = timeData,
                    calendarState = calendarState,
                    onCalendarClick = { openCalendarApp(context) },
                )
            }
            HeaderMode.WEATHER_ONLY -> {
                WeatherFocusView(
                    weatherData = weatherData,
                    useFahrenheit = config.useFahrenheit,
                    onWeatherClick = { openWeatherApp(context, weatherData) },
                )
            }
            HeaderMode.HIDDEN -> {}
        }
    }

    if (showConfigSheet) {
        HeaderSettingsSheet(
            config = config,
            weatherData = weatherData,
            onModeChange = onModeChange,
            onUnitChange = onUnitChange,
            onRefreshWeather = onRefreshWeather,
            onReplaceWidget = {
                showConfigSheet = false
                onReplaceWidget()
            },
            onDismiss = { showConfigSheet = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CombinedGlanceView(
    timeData: TimeData,
    calendarState: CalendarState,
    weatherData: WeatherData,
    useFahrenheit: Boolean,
    onClockClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Line 1: Big clean clock + Weather Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClockClick,
                        onLongClick = onLongClick,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = timeData.timeText,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timeData.amPmText.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeData.amPmText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = onWeatherClick,
                        onLongClick = onLongClick,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = weatherData.conditionEmoji(),
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = weatherData.formatTemp(useFahrenheit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Line 2: Date + Next Event or Day of week
        val nextEvent = calendarState.nextEvent
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onCalendarClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(
                text = timeData.dateText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (nextEvent != null) {
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${nextEvent.formatEventTime()} ${nextEvent.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ClockFocusView(
    timeData: TimeData,
    onClockClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = timeData.timeText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (timeData.amPmText.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeData.amPmText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        Text(
            text = "${timeData.dayOfWeek}, ${timeData.dateText}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CalendarFocusView(
    timeData: TimeData,
    calendarState: CalendarState,
    onCalendarClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = timeData.dayOfWeek.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
            Text(
                text = timeData.dateText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val nextEvent = calendarState.nextEvent
            if (nextEvent != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${nextEvent.formatEventTime()} · ${nextEvent.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherFocusView(
    weatherData: WeatherData,
    useFahrenheit: Boolean,
    onWeatherClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = weatherData.locationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = weatherData.conditionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "High: ${weatherData.formatTemp(useFahrenheit)} · Low: ${weatherData.lowC.toInt()}°C",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weatherData.conditionEmoji(),
                    fontSize = 32.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = weatherData.formatTemp(useFahrenheit),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderSettingsSheet(
    config: HeaderConfig,
    weatherData: WeatherData,
    onModeChange: (HeaderMode) -> Unit,
    onUnitChange: (Boolean) -> Unit,
    onRefreshWeather: () -> Unit,
    onReplaceWidget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            onRefreshWeather()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.section_smart_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onRefreshWeather) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh weather",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Weather Location Status & Permission Card
            if (!hasLocationPermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.weather_location_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.weather_location_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.weather_location_allow),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.weather_location_granted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = weatherData.locationName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(onClick = onRefreshWeather) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.weather_location_refresh),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            FilledTonalButton(
                onClick = onReplaceWidget,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_widget_replace),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setting_header_mode).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(8.dp))

            val modes = listOf(
                HeaderMode.COMBINED to stringResource(R.string.header_mode_combined),
                HeaderMode.CLOCK_ONLY to stringResource(R.string.header_mode_clock),
                HeaderMode.CALENDAR_ONLY to stringResource(R.string.header_mode_calendar),
                HeaderMode.WEATHER_ONLY to stringResource(R.string.header_mode_weather),
                HeaderMode.HIDDEN to stringResource(R.string.header_mode_hidden),
            )

            modes.forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = config.mode == mode,
                        onClick = { onModeChange(mode) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.setting_temperature_unit).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !config.useFahrenheit,
                    onClick = { onUnitChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.unit_celsius))
                }
                SegmentedButton(
                    selected = config.useFahrenheit,
                    onClick = { onUnitChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.unit_fahrenheit))
                }
            }

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

private fun openClockApp(context: Context) {
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(intent) }.isSuccess) return

    val setAlarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(setAlarm) }.isSuccess) return

    val commonClockPackages = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.android.alarmclock",
        "com.sec.android.app.clockpackage",
        "com.coloros.alarmclock",
        "com.oppo.alarmclock",
        "com.oneplus.deskclock",
        "com.xiaomi.deskclock",
        "com.asus.deskclock",
    )
    for (pkg in commonClockPackages) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launchIntent) }.isSuccess) return
        }
    }
}

private fun openCalendarApp(context: Context) {
    val builder = CalendarContract.CONTENT_URI.buildUpon()
    builder.appendPath("time")
    ContentUris.appendId(builder, System.currentTimeMillis())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = builder.build()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            val fallback = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
}

private fun openWeatherApp(context: Context, weather: WeatherData) {
    val commonWeatherPackages = listOf(
        "com.miui.weather2",
        "com.google.android.apps.weather",
        "com.sec.android.daemonapp",
        "com.coloros.weather2",
        "com.oppo.weather",
        "com.oneplus.weather",
        "com.huawei.android.totemweather",
    )
    for (pkg in commonWeatherPackages) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launchIntent) }.isSuccess) return
        }
    }

    val categoryIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_WEATHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(categoryIntent) }.isSuccess) return

    val query = Uri.encode(weather.locationName)
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather+$query")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(webIntent) }
}
