package com.ff.launcher.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ff.launcher.R
import com.ff.launcher.data.QuickActionPreferencesRepository
import com.ff.launcher.data.QuickActionPreferencesRepository.Companion.SIZE_RANGE_F
import com.ff.launcher.data.QuickActionType
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import com.ff.launcher.data.TriggerPreferencesRepository
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.HEIGHT_RANGE_F
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.VERTICAL_BIAS_RANGE
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.WIDTH_RANGE_F
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.glance.HeaderSize
import com.ff.launcher.data.notification.BadgeScope
import com.ff.launcher.notification.NotificationAccessHelper
import com.ff.launcher.ui.quickaction.QuickActionIcons
import com.ff.launcher.ui.quickaction.QuickActionPickerSheet
import com.ff.launcher.ui.theme.FavoriteAmber
import com.ff.launcher.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Modern Material 3 standalone Settings screen for FF Launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRequestDefaultLauncher: () -> Unit,
    onReplaceHomeWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val triggerConfig by viewModel.triggerConfig.collectAsState()
    val headerConfig by viewModel.headerConfig.collectAsState()
    val homeWidgetSlot by viewModel.homeWidgetSlot.collectAsState()
    val quickActionConfig by viewModel.quickActionConfig.collectAsState()
    val quickActionTargetApp by viewModel.quickActionTargetApp.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsState()
    val isQuickActionPickerOpen by viewModel.isQuickActionPickerOpen.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val badgePreferences by viewModel.badgePreferences.collectAsState()
    val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsState()

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(viewModel.hasLocationPermission())
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            viewModel.refreshWeather(force = true)
        }
    }

    val overlapsScroller = triggerConfig.edgeSide == TriggerEdgeSide.RIGHT && triggerConfig.enabled

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (overlapsScroller) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FavoriteAmber.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.hint_trigger_overlaps_scroller),
                        style = MaterialTheme.typography.bodySmall,
                        color = FavoriteAmber,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (!isDefaultLauncher) {
                Button(
                    onClick = onRequestDefaultLauncher,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_set_default),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Home Screen Header & Widgets Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.section_smart_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.smart_header_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val hasCustomWidget = homeWidgetSlot?.appWidgetId != null

                    if (hasCustomWidget) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.home_widget_custom_active),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.home_widget_custom_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = onReplaceHomeWidget,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.home_widget_replace),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = viewModel::restoreDefaultHomeWidget,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_restore),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onReplaceHomeWidget,
                            shape = RoundedCornerShape(12.dp),
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        Text(
                            text = stringResource(R.string.setting_header_mode),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val modes = listOf(
                            HeaderMode.COMBINED to stringResource(R.string.header_mode_combined),
                            HeaderMode.CLOCK_ONLY to stringResource(R.string.header_mode_clock),
                            HeaderMode.CALENDAR_ONLY to stringResource(R.string.header_mode_calendar),
                            HeaderMode.WEATHER_ONLY to stringResource(R.string.header_mode_weather),
                            HeaderMode.HIDDEN to stringResource(R.string.header_mode_hidden),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modes.forEach { (mode, label) ->
                                val selected = headerConfig.mode == mode
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.setHeaderMode(mode) },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )
                                        RadioButton(
                                            selected = selected,
                                            onClick = { viewModel.setHeaderMode(mode) },
                                        )
                                    }
                                }
                            }
                        }

                        if (headerConfig.mode != HeaderMode.HIDDEN) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                text = stringResource(R.string.setting_temperature_unit),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = !headerConfig.useFahrenheit,
                                    onClick = { viewModel.setHeaderUseFahrenheit(false) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) { Text(stringResource(R.string.unit_celsius)) }
                                SegmentedButton(
                                    selected = headerConfig.useFahrenheit,
                                    onClick = { viewModel.setHeaderUseFahrenheit(true) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) { Text(stringResource(R.string.unit_fahrenheit)) }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                text = stringResource(R.string.setting_widget_size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = headerConfig.size == HeaderSize.SMALL,
                                    onClick = { viewModel.setHeaderSize(HeaderSize.SMALL) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                ) { Text(stringResource(R.string.size_small)) }
                                SegmentedButton(
                                    selected = headerConfig.size == HeaderSize.MEDIUM,
                                    onClick = { viewModel.setHeaderSize(HeaderSize.MEDIUM) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                ) { Text(stringResource(R.string.size_medium)) }
                                SegmentedButton(
                                    selected = headerConfig.size == HeaderSize.LARGE,
                                    onClick = { viewModel.setHeaderSize(HeaderSize.LARGE) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                ) { Text(stringResource(R.string.size_large)) }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Text(
                        text = stringResource(R.string.weather_location_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

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
                                IconButton(onClick = { viewModel.refreshWeather(force = true) }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.weather_location_refresh),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notifications Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.section_notifications),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.setting_notification_arrows_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = badgePreferences.enabled,
                            onCheckedChange = { viewModel.setBadgeEnabled(it) },
                        )
                    }

                    // Notification Access Status Banner
                    if (!hasNotificationAccess) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.setting_notification_access_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.setting_notification_access_needed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { NotificationAccessHelper.openSettings(context) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.setting_notification_access_grant_button),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
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
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.setting_notification_access_granted),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                TextButton(
                                    onClick = { NotificationAccessHelper.openSettings(context) },
                                ) {
                                    Text(
                                        text = stringResource(R.string.setting_notification_access_manage),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    if (badgePreferences.enabled) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // Notification Scope
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.setting_notification_scope),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = badgePreferences.scope == BadgeScope.ALL,
                                    onClick = { viewModel.setBadgeScope(BadgeScope.ALL) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) {
                                    Text(
                                        text = stringResource(R.string.scope_all_apps),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                SegmentedButton(
                                    selected = badgePreferences.scope == BadgeScope.PINNED_ONLY,
                                    onClick = { viewModel.setBadgeScope(BadgeScope.PINNED_ONLY) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) {
                                    Text(
                                        text = stringResource(R.string.scope_pinned_only),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }

                        // Mark as read on dismiss toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_mark_as_read_on_dismiss),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.setting_mark_as_read_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = badgePreferences.markAsReadOnDismiss,
                                onCheckedChange = { viewModel.setMarkAsReadOnDismiss(it) },
                            )
                        }

                        // Hide message text toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_hide_message_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.setting_hide_message_text_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = badgePreferences.hideMessageText,
                                onCheckedChange = { viewModel.setHideMessageText(it) },
                            )
                        }

                        // Include silent notifications toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_include_silent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.setting_include_silent_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = badgePreferences.includeSilent,
                                onCheckedChange = { viewModel.setIncludeSilent(it) },
                            )
                        }

                        // Muted Apps Section
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.setting_muted_apps),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (badgePreferences.mutedPackages.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.setting_no_muted_apps),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    badgePreferences.mutedPackages.forEach { pkg ->
                                        val appName = allInstalledApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = appName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            OutlinedButton(
                                                onClick = { viewModel.unmutePackage(pkg) },
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.setting_unmute),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Reset to defaults
                        OutlinedButton(
                            onClick = { viewModel.resetBadgePreferences() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.quick_action_reset),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            // Edge Trigger Card (Secondary Screen)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_show_second_screen),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.setting_show_second_screen_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = triggerConfig.enabled,
                            onCheckedChange = viewModel::setTriggerEnabled,
                        )
                    }

                    if (!triggerConfig.enabled) {
                        Text(
                            text = stringResource(R.string.setting_second_screen_hidden_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    if (triggerConfig.enabled) {
                        Text(
                            text = stringResource(R.string.setting_edge_side),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = triggerConfig.edgeSide == TriggerEdgeSide.LEFT,
                                onClick = { viewModel.setTriggerEdgeSide(TriggerEdgeSide.LEFT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.edge_left)) }
                            SegmentedButton(
                                selected = triggerConfig.edgeSide == TriggerEdgeSide.RIGHT,
                                onClick = { viewModel.setTriggerEdgeSide(TriggerEdgeSide.RIGHT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.edge_right)) }
                        }

                        TuningSlider(
                            label = stringResource(R.string.setting_vertical_position),
                            externalValue = triggerConfig.verticalBias,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onCommit = viewModel::setTriggerVerticalBias,
                            valueRange = VERTICAL_BIAS_RANGE,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_pill_height),
                            externalValue = triggerConfig.heightDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onCommit = { viewModel.setTriggerHeightDp(it.roundToInt()) },
                            valueRange = HEIGHT_RANGE_F,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_pill_width),
                            externalValue = triggerConfig.widthDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onCommit = { viewModel.setTriggerWidthDp(it.roundToInt()) },
                            valueRange = WIDTH_RANGE_F,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_opacity),
                            externalValue = triggerConfig.opacity,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onCommit = viewModel::setTriggerOpacity,
                            valueRange = TriggerPreferencesRepository.OPACITY_RANGE,
                        )

                        Text(
                            text = stringResource(R.string.setting_activation_mode),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = triggerConfig.mode == TriggerMode.TAP_ONLY,
                                onClick = { viewModel.setTriggerMode(TriggerMode.TAP_ONLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text(stringResource(R.string.mode_tap)) }
                            SegmentedButton(
                                selected = triggerConfig.mode == TriggerMode.SWIPE_ONLY,
                                onClick = { viewModel.setTriggerMode(TriggerMode.SWIPE_ONLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text(stringResource(R.string.mode_swipe)) }
                            SegmentedButton(
                                selected = triggerConfig.mode == TriggerMode.TAP_AND_SWIPE,
                                onClick = { viewModel.setTriggerMode(TriggerMode.TAP_AND_SWIPE) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text(stringResource(R.string.mode_tap_and_swipe)) }
                        }
                    }
                }
            }

            // Quick Action Floating Button Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.section_quick_action),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.quick_action_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = quickActionConfig.enabled,
                            onCheckedChange = viewModel::setQuickActionEnabled,
                        )
                    }

                    if (quickActionConfig.enabled) {
                        Text(
                            text = stringResource(R.string.quick_action_target_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = viewModel::openQuickActionPicker),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    when (quickActionConfig.actionType) {
                                        QuickActionType.FLASHLIGHT -> Icon(
                                            QuickActionIcons.FlashlightOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        QuickActionType.CAMERA -> Icon(
                                            QuickActionIcons.Camera,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        QuickActionType.ALARM -> Icon(
                                            QuickActionIcons.Alarm,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        QuickActionType.ASSISTANT -> Icon(
                                            QuickActionIcons.Assistant,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                        QuickActionType.APP -> {
                                            val targetIcon = quickActionTargetApp?.icon
                                            if (targetIcon != null) {
                                                Image(
                                                    bitmap = targetIcon.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            } else {
                                                Icon(
                                                    QuickActionIcons.Rocket,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }

                                    val label = when (quickActionConfig.actionType) {
                                        QuickActionType.FLASHLIGHT -> stringResource(R.string.quick_action_preset_flashlight)
                                        QuickActionType.CAMERA -> stringResource(R.string.quick_action_preset_camera)
                                        QuickActionType.ALARM -> stringResource(R.string.quick_action_preset_alarm)
                                        QuickActionType.ASSISTANT -> stringResource(R.string.quick_action_preset_assistant)
                                        QuickActionType.APP -> quickActionTargetApp?.label ?: quickActionConfig.customLabel.ifEmpty { "Select App" }
                                    }

                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                FilledTonalButton(
                                    onClick = viewModel::openQuickActionPicker,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = stringResource(R.string.quick_action_choose_app),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.setting_edge_side),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = quickActionConfig.edgeSide == TriggerEdgeSide.LEFT,
                                onClick = { viewModel.setQuickActionEdgeSide(TriggerEdgeSide.LEFT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.edge_left)) }
                            SegmentedButton(
                                selected = quickActionConfig.edgeSide == TriggerEdgeSide.RIGHT,
                                onClick = { viewModel.setQuickActionEdgeSide(TriggerEdgeSide.RIGHT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.edge_right)) }
                        }

                        TuningSlider(
                            label = stringResource(R.string.setting_vertical_position),
                            externalValue = quickActionConfig.verticalBias,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onCommit = viewModel::setQuickActionVerticalBias,
                            valueRange = 0.08f..0.92f,
                        )

                        TuningSlider(
                            label = stringResource(R.string.quick_action_setting_size),
                            externalValue = quickActionConfig.sizeDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onCommit = { viewModel.setQuickActionSizeDp(it.roundToInt()) },
                            valueRange = SIZE_RANGE_F,
                        )

                        TuningSlider(
                            label = stringResource(R.string.quick_action_setting_opacity),
                            externalValue = quickActionConfig.opacity,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onCommit = viewModel::setQuickActionOpacity,
                            valueRange = QuickActionPreferencesRepository.OPACITY_RANGE,
                        )

                        OutlinedButton(
                            onClick = viewModel::resetQuickActionToDefaults,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.quick_action_reset))
                        }
                    }
                }
            }

            // Hidden Apps Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.section_hidden_apps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hiddenApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_hidden_apps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        hiddenApps.forEach { hidden ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = hidden.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = { viewModel.unhideApp(hidden.componentKey) },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_unhide),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isQuickActionPickerOpen) {
        QuickActionPickerSheet(
            config = quickActionConfig,
            allApps = allInstalledApps,
            onSelectAction = viewModel::setQuickAction,
            onDismiss = viewModel::closeQuickActionPicker,
        )
    }
}

@Composable
private fun TuningSlider(
    label: String,
    externalValue: Float,
    valueText: @Composable (Float) -> String,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    var localValue by remember { mutableFloatStateOf(externalValue) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(externalValue) {
        if (!isDragging) localValue = externalValue
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText(localValue),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = localValue,
            onValueChange = {
                isDragging = true
                localValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                onCommit(localValue)
            },
            valueRange = valueRange,
        )
    }
}
