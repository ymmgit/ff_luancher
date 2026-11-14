package com.ff.launcher.ui.secondary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ff.launcher.R
import com.ff.launcher.data.TriggerConfig
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.HEIGHT_RANGE_F
import com.ff.launcher.data.TriggerPreferencesRepository.Companion.OPACITY_RANGE
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

            // Edge Trigger Card
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
                        text = stringResource(R.string.section_edge_trigger),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

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
                        valueRange = OPACITY_RANGE,
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
