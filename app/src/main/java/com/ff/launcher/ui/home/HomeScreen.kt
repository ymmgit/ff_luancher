package com.ff.launcher.ui.home

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Process
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.BackHandler
import com.ff.launcher.notification.BadgeKey
import com.ff.launcher.notification.NotificationCard
import com.ff.launcher.ui.notification.NotificationArrow
import com.ff.launcher.ui.notification.NotificationPanel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ff.launcher.R
import com.ff.launcher.data.AppInfo
import com.ff.launcher.data.glance.CalendarState
import com.ff.launcher.data.glance.HeaderConfig
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.glance.HeaderSize
import com.ff.launcher.data.glance.TimeData
import com.ff.launcher.data.glance.WeatherData
import com.ff.launcher.data.db.HOME_WIDGET_SLOT_ID
import com.ff.launcher.data.db.WidgetSlotEntity
import com.ff.launcher.ui.theme.TextPrimary
import com.ff.launcher.ui.theme.TextSecondary
import com.ff.launcher.viewmodel.HomeListState
import com.ff.launcher.widget.LauncherAppWidgetHostView
import com.ff.launcher.viewmodel.HomeRow
import kotlinx.coroutines.launch

/**
 * Primary home screen, Niagara-style: collapsed state shows only pinned favorites;
 * touching the wave scroller reveals the full locale-collated app drawer list.
 * Remains open while browsing, and smoothly returns to favorites on Back or Home gesture.
 * Long-pressing a row opens a context menu (favorite toggle, App info, Uninstall).
 */
@Composable
fun HomeScreen(
    state: HomeListState,
    listState: LazyListState,
    homeResetTrigger: Long = 0L,
    homeWidgetSlot: WidgetSlotEntity? = null,
    timeData: TimeData = TimeData(),
    calendarState: CalendarState = CalendarState(),
    weatherData: WeatherData = WeatherData(),
    headerConfig: HeaderConfig = HeaderConfig(),
    onHeaderModeChange: (HeaderMode) -> Unit = {},
    onHeaderUnitChange: (Boolean) -> Unit = {},
    onHeaderSizeChange: (HeaderSize) -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    onReplaceHomeWidget: () -> Unit = {},
    onRestoreDefaultClock: () -> Unit = {},
    obtainHostView: (Context, Int, Int) -> LauncherAppWidgetHostView? = { _, _, _ -> null },
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenDeck: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    hasNotification: (AppInfo, Boolean) -> Boolean = { _, _ -> false },
    expandedBadgeKey: BadgeKey? = null,
    expandedCards: List<NotificationCard> = emptyList(),
    markAsReadEnabled: Boolean = true,
    onToggleAppNotifications: (AppInfo) -> Unit = {},
    onDismissNotificationCard: (NotificationCard) -> Unit = {},
    onClearAllNotificationCards: (AppInfo) -> Unit = {},
    onOpenNotificationCard: (NotificationCard) -> Unit = {},
    onCollapseNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(false) }
    val favListState = rememberLazyListState()

    LaunchedEffect(homeResetTrigger) {
        if (homeResetTrigger > 0L) {
            Log.d("HomeScreen", "homeResetTrigger fired ($homeResetTrigger) -> folding full list back to favorites")
            onCollapseNotifications()
            revealed = false
            runCatching { listState.scrollToItem(0) }
            runCatching { favListState.scrollToItem(0) }
        }
    }

    val showFullList = revealed
    val activeListState = if (showFullList) listState else favListState

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val triggerDistancePx = with(density) { 64.dp.toPx() }
    var accumulatedPullUp by remember { mutableStateOf(0f) }

    // Consistent, deliberate overscroll-to-search:
    // Only activates when the bottom of the list is ALREADY visible and resting on screen,
    // and the user actively pulls up by a deliberate distance (64dp).
    // Never activates from momentum/fling.
    val nestedScrollConnection = remember(activeListState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && accumulatedPullUp > 0f) {
                    val consumed = available.y.coerceAtMost(accumulatedPullUp)
                    accumulatedPullUp -= consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Ignore momentum/fling entirely so normal scrolling stops at the bottom
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }

                val layoutInfo = activeListState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) return Offset.Zero

                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                val isBottomShown = lastVisibleItem != null &&
                    lastVisibleItem.index == totalItems - 1 &&
                    (lastVisibleItem.offset + lastVisibleItem.size) <= (layoutInfo.viewportEndOffset + 8)

                // Only accumulate when the bottom is already shown and user continues pulling up
                if (available.y < 0f && isBottomShown) {
                    accumulatedPullUp += -available.y
                    if (accumulatedPullUp >= triggerDistancePx) {
                        accumulatedPullUp = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSearch()
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                accumulatedPullUp = 0f
                return Velocity.Zero
            }
        }
    }

    fun onScrollerReveal(active: Boolean) {
        if (active) {
            Log.d("HomeScreen", "onScrollerReveal: active=true -> entering app drawer (revealed=true)")
            revealed = true
        }
    }

    // System back gesture closes open notification panel or folds the revealed list back to favorites-only.
    BackHandler(enabled = revealed || expandedBadgeKey != null) {
        if (expandedBadgeKey != null) {
            onCollapseNotifications()
        } else {
            Log.d("HomeScreen", "BackHandler: returning to favorites view")
            revealed = false
        }
    }

    // Niagara-style: home shows pinned favorites only; the full list reveals on scroller
    // touch and folds back. With nothing pinned, show a guide card instead of the list.
    val insets = WindowInsets.systemBars.asPaddingValues()
    val listContentPadding = PaddingValues(
        start = 16.dp,
        end = 42.dp, // 34dp WaveScroller + 8dp breathing room = ~88% of screen width clickable
        top = insets.calculateTopPadding() + 12.dp,
        bottom = insets.calculateBottomPadding() + 24.dp,
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Crossfade(targetState = showFullList, label = "homeListCrossfade") { full ->
            if (full) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = listContentPadding,
                ) {
                    item(key = "home_header_area") {
                        HomeHeaderArea(
                            homeWidgetSlot = homeWidgetSlot,
                            timeData = timeData,
                            calendarState = calendarState,
                            weatherData = weatherData,
                            headerConfig = headerConfig,
                            onHeaderModeChange = onHeaderModeChange,
                            onHeaderUnitChange = onHeaderUnitChange,
                            onHeaderSizeChange = onHeaderSizeChange,
                            onRefreshWeather = onRefreshWeather,
                            onReplaceHomeWidget = onReplaceHomeWidget,
                            onRestoreDefaultClock = onRestoreDefaultClock,
                            obtainHostView = obtainHostView,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    items(
                        items = state.rows,
                        key = ::rowKey,
                        contentType = { it::class },
                    ) { row ->
                        when (row) {
                            is HomeRow.Header -> SectionHeader(row.label)
                            is HomeRow.AppRow -> {
                                val app = row.app
                                val isAppExpanded = expandedBadgeKey?.packageName == app.packageName
                                AppListRow(
                                    row = row,
                                    isPinned = false,
                                    hasNotification = hasNotification(app, false),
                                    isExpanded = isAppExpanded,
                                    cards = if (isAppExpanded) expandedCards else emptyList(),
                                    markAsReadEnabled = markAsReadEnabled,
                                    onClick = onAppClick,
                                    onLongClick = onAppLongClick,
                                    onToggleNotifications = { onToggleAppNotifications(app) },
                                    onCardClick = onOpenNotificationCard,
                                    onDismissCard = onDismissNotificationCard,
                                    onClearAll = { onClearAllNotificationCards(app) },
                                )
                            }
                            HomeRow.Divider -> HomeDivider()
                        }
                    }
                    item(key = "search_pull_hint") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenSearch),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.search_pull_up_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            } else if (state.favoriteCount == 0 && state.favoriteKeys.isEmpty() && state.rows.isNotEmpty()) {
                // No favorites pinned AND full apps have loaded → show Smart Header + guide card.
                LazyColumn(
                    state = favListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = listContentPadding,
                ) {
                    item(key = "home_header_area") {
                        HomeHeaderArea(
                            homeWidgetSlot = homeWidgetSlot,
                            timeData = timeData,
                            calendarState = calendarState,
                            weatherData = weatherData,
                            headerConfig = headerConfig,
                            onHeaderModeChange = onHeaderModeChange,
                            onHeaderUnitChange = onHeaderUnitChange,
                            onHeaderSizeChange = onHeaderSizeChange,
                            onRefreshWeather = onRefreshWeather,
                            onReplaceHomeWidget = onReplaceHomeWidget,
                            onRestoreDefaultClock = onRestoreDefaultClock,
                            obtainHostView = obtainHostView,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    item(key = "empty_guide") {
                        EmptyFavoritesGuide(
                            onBrowseApps = {
                                revealed = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = favListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = listContentPadding,
                ) {
                    item(key = "home_header_area") {
                        HomeHeaderArea(
                            homeWidgetSlot = homeWidgetSlot,
                            timeData = timeData,
                            calendarState = calendarState,
                            weatherData = weatherData,
                            headerConfig = headerConfig,
                            onHeaderModeChange = onHeaderModeChange,
                            onHeaderUnitChange = onHeaderUnitChange,
                            onHeaderSizeChange = onHeaderSizeChange,
                            onRefreshWeather = onRefreshWeather,
                            onReplaceHomeWidget = onReplaceHomeWidget,
                            onRestoreDefaultClock = onRestoreDefaultClock,
                            obtainHostView = obtainHostView,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    items(
                        items = state.rows
                            .takeWhile { it !is HomeRow.Header }
                            .filterIsInstance<HomeRow.AppRow>(),
                        key = { "fav_${it.app.componentKey}" },
                    ) { row ->
                        val app = row.app
                        val isAppExpanded = expandedBadgeKey?.packageName == app.packageName
                        AppListRow(
                            row = row,
                            isPinned = true,
                            hasNotification = hasNotification(app, true),
                            isExpanded = isAppExpanded,
                            cards = if (isAppExpanded) expandedCards else emptyList(),
                            markAsReadEnabled = markAsReadEnabled,
                            onClick = onAppClick,
                            onLongClick = onAppLongClick,
                            onToggleNotifications = { onToggleAppNotifications(app) },
                            onCardClick = onOpenNotificationCard,
                            onDismissCard = onDismissNotificationCard,
                            onClearAll = { onClearAllNotificationCards(app) },
                        )
                    }
                    item(key = "fav_search_pull_hint") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenSearch),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.search_pull_up_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        val sectionLabels = remember(state.sections) { state.sections.map { it.label } }
        WaveScroller(
            sectionLabels = sectionLabels,
            onSectionSelected = { sectionIndex ->
                state.sections.getOrNull(sectionIndex)?.let { section ->
                    scope.launch { listState.scrollToItem(section.rowIndex) }
                }
            },
            onSettingsSelected = onOpenSettings,
            onRevealChanged = ::onScrollerReveal,
            modifier = Modifier
                .align(AbsoluteAlignment.TopRight) // physical right edge, RTL-independent
                .offset(y = maxHeight * 0.35f)
                .size(width = 34.dp, height = maxHeight * 0.50f),
        )
    }
}

private fun rowKey(row: HomeRow): String = when (row) {
    is HomeRow.Header -> "header_${row.label}"
    is HomeRow.AppRow ->
        if (row.isFavorite) "fav_${row.app.componentKey}" else "app_${row.app.componentKey}"
    HomeRow.Divider -> "divider"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(
    row: HomeRow.AppRow,
    isPinned: Boolean,
    hasNotification: Boolean,
    isExpanded: Boolean,
    cards: List<NotificationCard>,
    markAsReadEnabled: Boolean,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    onToggleNotifications: () -> Unit,
    onCardClick: (NotificationCard) -> Unit,
    onDismissCard: (NotificationCard) -> Unit,
    onClearAll: () -> Unit,
) {
    val app = row.app
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = { onClick(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(app)
                    },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app)
            Spacer(Modifier.width(14.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasNotification) {
                Spacer(Modifier.width(4.dp))
                NotificationArrow(
                    visible = true,
                    isExpanded = isExpanded,
                    onClick = onToggleNotifications,
                )
            }
        }

        if (isExpanded && cards.isNotEmpty()) {
            NotificationPanel(
                cards = cards,
                markAsReadEnabled = markAsReadEnabled,
                onCardClick = onCardClick,
                onDismissCard = onDismissCard,
                onClearAll = onClearAll,
            )
        }
    }
}

@Composable
private fun AppIcon(app: AppInfo) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = remember(icon) { icon.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 8.dp),
    )
}

@Composable
private fun EmptyFavoritesGuide(
    onBrowseApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.home_empty_favorites_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_empty_favorites_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onBrowseApps,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_empty_favorites_action),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun HomeHeaderArea(
    homeWidgetSlot: WidgetSlotEntity?,
    timeData: TimeData,
    calendarState: CalendarState,
    weatherData: WeatherData,
    headerConfig: HeaderConfig,
    onHeaderModeChange: (HeaderMode) -> Unit,
    onHeaderUnitChange: (Boolean) -> Unit,
    onHeaderSizeChange: (HeaderSize) -> Unit = {},
    onRefreshWeather: () -> Unit,
    onReplaceHomeWidget: () -> Unit,
    onRestoreDefaultClock: () -> Unit,
    obtainHostView: (Context, Int, Int) -> LauncherAppWidgetHostView?,
    modifier: Modifier = Modifier,
) {
    val appWidgetId = homeWidgetSlot?.appWidgetId
    if (appWidgetId != null) {
        HomeCustomWidgetView(
            appWidgetId = appWidgetId,
            obtainHostView = obtainHostView,
            onReplaceWidget = onReplaceHomeWidget,
            onRestoreDefaultClock = onRestoreDefaultClock,
            modifier = modifier,
        )
    } else {
        HomeSmartHeader(
            timeData = timeData,
            calendarState = calendarState,
            weatherData = weatherData,
            config = headerConfig,
            onModeChange = onHeaderModeChange,
            onUnitChange = onHeaderUnitChange,
            onSizeChange = onHeaderSizeChange,
            onRefreshWeather = onRefreshWeather,
            onReplaceWidget = onReplaceHomeWidget,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCustomWidgetView(
    appWidgetId: Int,
    obtainHostView: (Context, Int, Int) -> LauncherAppWidgetHostView?,
    onReplaceWidget: () -> Unit,
    onRestoreDefaultClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showSheet by remember { mutableStateOf(false) }

    val providerAlive = remember(appWidgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) != null
    }

    LaunchedEffect(appWidgetId, providerAlive) {
        if (!providerAlive) {
            onRestoreDefaultClock()
        }
    }

    if (!providerAlive) return

    val hostView = remember(appWidgetId) {
        obtainHostView(context, HOME_WIDGET_SLOT_ID, appWidgetId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSheet = true
                },
            ),
    ) {
        if (hostView != null) {
            AndroidView(
                factory = { _ ->
                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                    hostView.onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSheet = true
                    }
                    hostView.post {
                        hostView.updateAppWidgetSizeFromLayout()
                        hostView.requestLayout()
                        hostView.invalidate()
                    }
                    hostView
                },
                update = { view ->
                    view.onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSheet = true
                    }
                    view.post { view.updateAppWidgetSizeFromLayout() }
                },
                onRelease = { view -> view.onLongPress = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            )
        }
    }

    if (showSheet) {
        HomeWidgetOptionsSheet(
            onReplace = {
                showSheet = false
                onReplaceWidget()
            },
            onRestore = {
                showSheet = false
                onRestoreDefaultClock()
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWidgetOptionsSheet(
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_widget_options),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            FilledTonalButton(
                onClick = onReplace,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_widget_replace),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            OutlinedButton(
                onClick = onRestore,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_widget_restore),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
