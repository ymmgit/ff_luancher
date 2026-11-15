package com.ff.launcher.ui.secondary

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import com.ff.launcher.data.db.WidgetSlotEntity
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ff.launcher.R
import com.ff.launcher.data.AppInfo
import com.ff.launcher.data.QuickActionConfig
import com.ff.launcher.data.QuickActionPreferencesRepository
import com.ff.launcher.data.QuickActionPreferencesRepository.Companion.SIZE_RANGE_F
import com.ff.launcher.data.QuickActionType
import com.ff.launcher.ui.quickaction.QuickActionIcons
import com.ff.launcher.data.TriggerConfig
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import com.ff.launcher.data.TriggerPreferencesRepository
import com.ff.launcher.data.glance.HeaderConfig
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.HEIGHT_RANGE_F
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.VERTICAL_BIAS_RANGE
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.WIDTH_RANGE_F
import com.ff.launcher.ui.theme.FavoriteAmber
import com.ff.launcher.viewmodel.HiddenAppUi
import kotlin.math.roundToInt

/**
 * Modern Google Material 3 Edge Trigger tuning sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerTuningSheet(
    config: TriggerConfig,
    overlapsScroller: Boolean,
    headerConfig: HeaderConfig = HeaderConfig(),
    homeWidgetSlot: WidgetSlotEntity? = null,
    onHeaderModeChange: (HeaderMode) -> Unit = {},
    onHeaderUnitChange: (Boolean) -> Unit = {},
    onReplaceHomeWidget: () -> Unit = {},
    onRestoreDefaultHomeWidget: () -> Unit = {},
    onTriggerEnabledChange: (Boolean) -> Unit = {},
    quickActionConfig: QuickActionConfig = QuickActionConfig(),
    quickActionTargetApp: AppInfo? = null,
    onQuickActionEnabledChange: (Boolean) -> Unit = {},
    onOpenQuickActionPicker: () -> Unit = {},
    onQuickActionEdgeSideChange: (TriggerEdgeSide) -> Unit = {},
    onQuickActionVerticalBiasChange: (Float) -> Unit = {},
    onQuickActionVerticalBiasPreview: (Float) -> Unit = {},
    onQuickActionSizeChange: (Int) -> Unit = {},
    onQuickActionSizePreview: (Int) -> Unit = {},
    onQuickActionOpacityChange: (Float) -> Unit = {},
    onQuickActionOpacityPreview: (Float) -> Unit = {},
    onResetQuickAction: () -> Unit = {},
    hiddenApps: List<HiddenAppUi>,
    isDefaultLauncher: Boolean,
    onRequestDefaultLauncher: () -> Unit,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit,
    onEdgeSideChange: (TriggerEdgeSide) -> Unit,
    onModeChange: (TriggerMode) -> Unit,
    onVerticalBiasPreview: (Float) -> Unit,
    onVerticalBiasCommit: (Float) -> Unit,
    onHeightPreview: (Int) -> Unit,
    onHeightCommit: (Int) -> Unit,
    onWidthPreview: (Int) -> Unit,
    onWidthCommit: (Int) -> Unit,
    onOpacityPreview: (Float) -> Unit,
    onOpacityCommit: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
                                        onClick = {
                                            onDismiss()
                                            onReplaceHomeWidget()
                                        },
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
                                        onClick = onRestoreDefaultHomeWidget,
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
                            onClick = {
                                onDismiss()
                                onReplaceHomeWidget()
                            },
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
                                        .clickable { onHeaderModeChange(mode) },
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
                                            onClick = { onHeaderModeChange(mode) },
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
                                    onClick = { onHeaderUnitChange(false) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                ) { Text(stringResource(R.string.unit_celsius)) }
                                SegmentedButton(
                                    selected = headerConfig.useFahrenheit,
                                    onClick = { onHeaderUnitChange(true) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                ) { Text(stringResource(R.string.unit_fahrenheit)) }
                            }
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
                            checked = config.enabled,
                            onCheckedChange = onTriggerEnabledChange,
                        )
                    }

                    if (!config.enabled) {
                        Text(
                            text = stringResource(R.string.setting_second_screen_hidden_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    if (config.enabled) {
                        Text(
                            text = stringResource(R.string.setting_edge_side),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = config.edgeSide == TriggerEdgeSide.LEFT,
                                onClick = { onEdgeSideChange(TriggerEdgeSide.LEFT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.edge_left)) }
                            SegmentedButton(
                                selected = config.edgeSide == TriggerEdgeSide.RIGHT,
                                onClick = { onEdgeSideChange(TriggerEdgeSide.RIGHT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.edge_right)) }
                        }

                        TuningSlider(
                            label = stringResource(R.string.setting_vertical_position),
                            externalValue = config.verticalBias,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onPreview = onVerticalBiasPreview,
                            onCommit = onVerticalBiasCommit,
                            valueRange = VERTICAL_BIAS_RANGE,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_pill_height),
                            externalValue = config.heightDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onPreview = { onHeightPreview(it.roundToInt()) },
                            onCommit = { onHeightCommit(it.roundToInt()) },
                            valueRange = HEIGHT_RANGE_F,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_pill_width),
                            externalValue = config.widthDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onPreview = { onWidthPreview(it.roundToInt()) },
                            onCommit = { onWidthCommit(it.roundToInt()) },
                            valueRange = WIDTH_RANGE_F,
                        )
                        TuningSlider(
                            label = stringResource(R.string.setting_opacity),
                            externalValue = config.opacity,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onPreview = onOpacityPreview,
                            onCommit = onOpacityCommit,
                            valueRange = TriggerPreferencesRepository.OPACITY_RANGE,
                        )

                        Text(
                            text = stringResource(R.string.setting_activation_mode),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = config.mode == TriggerMode.TAP_ONLY,
                                onClick = { onModeChange(TriggerMode.TAP_ONLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text(stringResource(R.string.mode_tap)) }
                            SegmentedButton(
                                selected = config.mode == TriggerMode.SWIPE_ONLY,
                                onClick = { onModeChange(TriggerMode.SWIPE_ONLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text(stringResource(R.string.mode_swipe)) }
                            SegmentedButton(
                                selected = config.mode == TriggerMode.TAP_AND_SWIPE,
                                onClick = { onModeChange(TriggerMode.TAP_AND_SWIPE) },
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
                            onCheckedChange = onQuickActionEnabledChange,
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
                                .clickable(onClick = onOpenQuickActionPicker),
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
                                            if (quickActionTargetApp?.icon != null) {
                                                Image(
                                                    bitmap = quickActionTargetApp.icon.asImageBitmap(),
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
                                    onClick = onOpenQuickActionPicker,
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
                                onClick = { onQuickActionEdgeSideChange(TriggerEdgeSide.LEFT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text(stringResource(R.string.edge_left)) }
                            SegmentedButton(
                                selected = quickActionConfig.edgeSide == TriggerEdgeSide.RIGHT,
                                onClick = { onQuickActionEdgeSideChange(TriggerEdgeSide.RIGHT) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text(stringResource(R.string.edge_right)) }
                        }

                        TuningSlider(
                            label = stringResource(R.string.setting_vertical_position),
                            externalValue = quickActionConfig.verticalBias,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onPreview = onQuickActionVerticalBiasPreview,
                            onCommit = onQuickActionVerticalBiasChange,
                            valueRange = 0.08f..0.92f,
                        )

                        TuningSlider(
                            label = stringResource(R.string.quick_action_setting_size),
                            externalValue = quickActionConfig.sizeDp.toFloat(),
                            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
                            onPreview = { onQuickActionSizePreview(it.roundToInt()) },
                            onCommit = { onQuickActionSizeChange(it.roundToInt()) },
                            valueRange = SIZE_RANGE_F,
                        )

                        TuningSlider(
                            label = stringResource(R.string.quick_action_setting_opacity),
                            externalValue = quickActionConfig.opacity,
                            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
                            onPreview = onQuickActionOpacityPreview,
                            onCommit = onQuickActionOpacityChange,
                            valueRange = QuickActionPreferencesRepository.OPACITY_RANGE,
                        )

                        OutlinedButton(
                            onClick = onResetQuickAction,
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
                                    onClick = { onUnhide(hidden.componentKey) },
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
}

@Composable
private fun TuningSlider(
    label: String,
    externalValue: Float,
    valueText: @Composable (Float) -> String,
    onPreview: (Float) -> Unit,
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
                onPreview(it)
            },
            onValueChangeFinished = {
                isDragging = false
                onCommit(localValue)
            },
            valueRange = valueRange,
        )
    }
}
