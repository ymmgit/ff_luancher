package com.ff.launcher.ui.home

import android.os.Process
import androidx.compose.animation.Crossfade
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ff.launcher.R
import com.ff.launcher.data.AppInfo
import com.ff.launcher.ui.theme.TextPrimary
import com.ff.launcher.ui.theme.TextSecondary
import com.ff.launcher.viewmodel.HomeListState
import com.ff.launcher.viewmodel.HomeRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How long the revealed app list stays on screen after the scroller is released. */
private const val LIST_LINGER_MS = 2_500L

/**
 * Primary home screen, Niagara-style (confirmed product decision): collapsed state shows
 * only pinned favorites; touching the wave scroller reveals the full locale-collated
 * list, which lingers briefly after release and folds back. With nothing pinned, the
 * full list IS the home. Long-pressing a row opens a context menu (favorite toggle,
 * App info, Uninstall) instead of mutating state directly.
 */
@Composable
fun HomeScreen(
    state: HomeListState,
    listState: LazyListState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenDeck: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(false) }
    val collapseJob = remember { mutableStateOf<Job?>(null) }
    val favListState = rememberLazyListState()

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

    fun scheduleCollapse() {
        collapseJob.value?.cancel()
        collapseJob.value = scope.launch {
            delay(LIST_LINGER_MS)
            revealed = false
        }
    }

    fun onScrollerReveal(active: Boolean) {
        if (active) {
            collapseJob.value?.cancel()
            collapseJob.value = null
            revealed = true
        } else {
            // Linger: keep the list readable at the landing spot, then fold back.
            scheduleCollapse()
        }
    }

    // Scrolling the revealed list directly also (re-)arms the collapse timer.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                collapseJob.value?.cancel()
            } else if (revealed) {
                scheduleCollapse()
            }
        }
    }

    // System back gesture folds the revealed list back to favorites-only.
    BackHandler(enabled = revealed) {
        collapseJob.value?.cancel()
        collapseJob.value = null
        revealed = false
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
                    items(
                        items = state.rows,
                        key = ::rowKey,
                        contentType = { it::class },
                    ) { row ->
                        when (row) {
                            is HomeRow.Header -> SectionHeader(row.label)
                            is HomeRow.AppRow -> AppListRow(
                                row = row,
                                onClick = onAppClick,
                                onLongClick = onAppLongClick,
                            )
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
                // No favorites pinned AND full apps have loaded → guide card in the centre of an empty home.
                EmptyFavoritesGuide(
                    onBrowseApps = {
                        revealed = true
                        scheduleCollapse()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(listContentPadding),
                )
            } else {
                LazyColumn(
                    state = favListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = listContentPadding,
                ) {
                    items(
                        items = state.rows
                            .takeWhile { it !is HomeRow.Header }
                            .filterIsInstance<HomeRow.AppRow>(),
                        key = { "fav_${it.app.componentKey}" },
                    ) { row ->
                        AppListRow(
                            row = row,
                            onClick = onAppClick,
                            onLongClick = onAppLongClick,
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
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    val app = row.app
    val haptics = LocalHapticFeedback.current

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
            modifier = Modifier.weight(1f),
        )
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
