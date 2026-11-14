package com.ff.launcher.ui.secondary

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ff.launcher.R
import com.ff.launcher.data.db.SLOT_WEIGHT_LARGE
import com.ff.launcher.data.db.SLOT_WEIGHT_MEDIUM
import com.ff.launcher.data.db.SLOT_WEIGHT_SMALL
import com.ff.launcher.data.db.WidgetSlotEntity
import com.ff.launcher.widget.LauncherAppWidgetHostView

/**
 * Secondary Screen — Modern Google Material 3 widget deck:
 * Three fixed slots at 2/3/4 weights fill the panel height.
 * Material 3 surface containers with smooth corner curves (24.dp).
 */
@Composable
fun SecondaryScreen(
    slots: List<WidgetSlotEntity>,
    onRequestWidget: (Int) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenTuning: () -> Unit,
    onUpdateSlotWeight: (Int, Float) -> Unit,
    obtainHostView: (Context, Int, Int) -> LauncherAppWidgetHostView?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnOpenTuning by rememberUpdatedState(onOpenTuning)
    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnDismiss() },
                    onLongPress = { currentOnOpenTuning() },
                )
            }
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = insets.calculateTopPadding() + 14.dp,
                bottom = insets.calculateBottomPadding() + 14.dp,
            ),
    ) {
        slots.forEachIndexed { index, slot ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            WidgetSlotHost(
                slot = slot,
                onRequestWidget = onRequestWidget,
                onRemoveWidget = onRemoveWidget,
                onOpenTuning = currentOnOpenTuning,
                onUpdateSlotWeight = onUpdateSlotWeight,
                obtainHostView = obtainHostView,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(slot.heightWeight.coerceAtLeast(1f)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetSlotHost(
    slot: WidgetSlotEntity,
    onRequestWidget: (Int) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenTuning: () -> Unit,
    onUpdateSlotWeight: (Int, Float) -> Unit,
    obtainHostView: (Context, Int, Int) -> LauncherAppWidgetHostView?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { menuOpen = false }

    val appWidgetId = slot.appWidgetId
    val providerAlive = remember(appWidgetId) {
        appWidgetId == null ||
            AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) != null
    }
    LaunchedEffect(appWidgetId, providerAlive) {
        if (appWidgetId != null && !providerAlive) onRemoveWidget(slot.slotId)
    }
    val isEmpty = appWidgetId == null || !providerAlive

    val shape = RoundedCornerShape(24.dp)

    Surface(
        shape = shape,
        color = if (isEmpty) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.25f)
        } else {
            Color.Transparent
        },
        border = if (isEmpty) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        } else {
            null
        },
        tonalElevation = 0.dp,
        modifier = if (isEmpty) {
            modifier
                .combinedClickable(
                    onClick = { onRequestWidget(slot.slotId) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
        } else {
            modifier
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            if (isEmpty) {
                EmptySlotHint(onAddClick = { onRequestWidget(slot.slotId) })
            } else {
                val hostView = remember(slot.slotId, appWidgetId) {
                    obtainHostView(context, slot.slotId, appWidgetId)
                }
                if (hostView != null) {
                    AndroidView(
                        factory = { _ ->
                            (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
                            hostView.onLongPress = { menuOpen = true }
                            hostView.post {
                                hostView.updateAppWidgetSizeFromLayout()
                                hostView.requestLayout()
                                hostView.invalidate()
                            }
                            hostView
                        },
                        update = { view ->
                            view.onLongPress = { menuOpen = true }
                            view.post { view.updateAppWidgetSizeFromLayout() }
                        },
                        onRelease = { view -> view.onLongPress = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    EmptySlotHint(onAddClick = { onRequestWidget(slot.slotId) })
                }
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = if (isEmpty) Icons.Default.Add else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                if (isEmpty) R.string.slot_action_add_widget
                                else R.string.slot_action_replace_widget,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRequestWidget(slot.slotId)
                    },
                )
                if (!isEmpty) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        text = {
                            Text(
                                stringResource(R.string.slot_action_remove_widget),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRemoveWidget(slot.slotId)
                        },
                    )
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.menu_slot_size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                SlotSizeItem(
                    label = stringResource(R.string.slot_size_small),
                    selected = slot.heightWeight == SLOT_WEIGHT_SMALL,
                    onClick = {
                        menuOpen = false
                        onUpdateSlotWeight(slot.slotId, SLOT_WEIGHT_SMALL)
                    },
                )
                SlotSizeItem(
                    label = stringResource(R.string.slot_size_medium),
                    selected = slot.heightWeight == SLOT_WEIGHT_MEDIUM,
                    onClick = {
                        menuOpen = false
                        onUpdateSlotWeight(slot.slotId, SLOT_WEIGHT_MEDIUM)
                    },
                )
                SlotSizeItem(
                    label = stringResource(R.string.slot_size_large),
                    selected = slot.heightWeight == SLOT_WEIGHT_LARGE,
                    onClick = {
                        menuOpen = false
                        onUpdateSlotWeight(slot.slotId, SLOT_WEIGHT_LARGE)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.settings_tune_trigger),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onOpenTuning()
                    },
                )
            }
        }
    }
}

@Composable
private fun SlotSizeItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun EmptySlotHint(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilledTonalIconButton(
            onClick = onAddClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add widget",
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.slot_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
