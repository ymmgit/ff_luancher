package com.ff.launcher.ui.secondary

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ff.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A resolved widget provider for the picker. */
data class WidgetProviderItem(
    val providerInfo: AppWidgetProviderInfo,
    val widgetLabel: String,
    val description: String?,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val previewBitmap: Bitmap?,
)

/** App-level container grouping all its installable widgets. */
data class AppWidgetGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val widgets: List<WidgetProviderItem>,
)

/**
 * Modern Google Material 3 widget picker:
 * - Groups widgets by their host application.
 * - Shows the main application icon alongside the app label.
 * - Displays a clean indicator for widget count (e.g., "1 widget", "2 widgets", "4 widgets").
 * - Expandable cards with widget previews, labels, and dimensions.
 * - Real-time search filtering across applications and widget titles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    onDismiss: () -> Unit,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val expandedApps = remember { mutableStateMapOf<String, Boolean>() }

    val appGroups by produceState<List<AppWidgetGroup>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadGroupedInstalledWidgets(context) }
    }

    val filteredGroups = remember(appGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            appGroups
        } else {
            val query = searchQuery.trim().lowercase()
            appGroups.mapNotNull { group ->
                val appMatches = group.appLabel.lowercase().contains(query)
                val matchingWidgets = group.widgets.filter {
                    appMatches || it.widgetLabel.lowercase().contains(query) ||
                        (it.description?.lowercase()?.contains(query) == true)
                }
                if (matchingWidgets.isNotEmpty()) {
                    group.copy(widgets = matchingWidgets)
                } else null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            // M3 Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.widget_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )

            Spacer(Modifier.height(16.dp))

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.widget_picker_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val isSearching = searchQuery.isNotBlank()

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = filteredGroups,
                        key = { it.packageName },
                    ) { group ->
                        val isExpanded = isSearching || (expandedApps[group.packageName] ?: false)

                        AppWidgetGroupCard(
                            group = group,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedApps[group.packageName] = !isExpanded
                            },
                            onSelectWidget = { widget ->
                                onSelectProvider(widget.providerInfo)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppWidgetGroupCard(
    group: AppWidgetGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectWidget: (WidgetProviderItem) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // App Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Main application icon
                val icon = group.appIcon
                if (icon != null) {
                    Image(
                        bitmap = remember(icon) { icon.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = group.appLabel.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                // App label & widget count text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val count = group.widgets.size
                    val countText = if (count == 1) {
                        stringResource(R.string.widget_count_single, count)
                    } else {
                        stringResource(R.string.widget_count_multiple, count)
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Expand/collapse chevron
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }

            // Expanded Widget List
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    group.widgets.forEach { widget ->
                        WidgetCardItem(
                            widget = widget,
                            onClick = { onSelectWidget(widget) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCardItem(
    widget: WidgetProviderItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Preview Image if available
            val preview = widget.previewBitmap
            if (preview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = remember(preview) { preview.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = widget.widgetLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!widget.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = widget.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Dimensions badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(
                            R.string.widget_size_label,
                            widget.minWidthDp,
                            widget.minHeightDp,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Loads and groups all installable widget providers for the calling user. */
private fun loadGroupedInstalledWidgets(context: Context): List<AppWidgetGroup> {
    val manager = AppWidgetManager.getInstance(context)
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val rawProviders = runCatching {
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
    }.getOrDefault(manager.installedProviders)

    val groupedByPackage = mutableMapOf<String, MutableList<WidgetProviderItem>>()
    val appLabels = mutableMapOf<String, String>()
    val appIcons = mutableMapOf<String, Bitmap?>()

    for (info in rawProviders) {
        val pkg = info.provider.packageName
        runCatching {
            if (!appLabels.containsKey(pkg)) {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                appLabels[pkg] = label
                val iconDrawable = pm.getApplicationIcon(appInfo)
                appIcons[pkg] = drawableToBitmap(iconDrawable, maxWidth = 120, maxHeight = 120)
            }

            val widgetLabel = info.loadLabel(pm)?.takeIf { it.isNotBlank() }
                ?: info.provider.shortClassName

            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { info.loadDescription(context)?.toString() }.getOrNull()
            } else null

            val preview = runCatching {
                val previewDrawable = info.loadPreviewImage(context, 0)
                if (previewDrawable != null) {
                    drawableToBitmap(previewDrawable, maxWidth = 480, maxHeight = 300)
                } else {
                    val iconDrawable = info.loadIcon(context, 0)
                    if (iconDrawable != null) {
                        drawableToBitmap(iconDrawable, maxWidth = 120, maxHeight = 120)
                    } else null
                }
            }.getOrNull()

            val item = WidgetProviderItem(
                providerInfo = info,
                widgetLabel = widgetLabel,
                description = description,
                minWidthDp = (info.minWidth / density).roundToInt(),
                minHeightDp = (info.minHeight / density).roundToInt(),
                previewBitmap = preview,
            )

            groupedByPackage.getOrPut(pkg) { mutableListOf() }.add(item)
        }
    }

    return groupedByPackage.map { (pkg, widgets) ->
        widgets.sortBy { it.widgetLabel.lowercase() }
        AppWidgetGroup(
            packageName = pkg,
            appLabel = appLabels[pkg] ?: pkg,
            appIcon = appIcons[pkg],
            widgets = widgets,
        )
    }.sortedBy { it.appLabel.lowercase() }
}

/** Safe, bounded conversion from Drawable to Bitmap. */
private fun drawableToBitmap(
    drawable: Drawable,
    maxWidth: Int = 256,
    maxHeight: Int = 256,
): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val bmp = drawable.bitmap
        if (bmp.width <= maxWidth && bmp.height <= maxHeight) return bmp
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(maxWidth) ?: maxWidth
    val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(maxHeight) ?: maxHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
