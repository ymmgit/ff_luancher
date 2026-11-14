package com.ff.launcher.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.ui.home.AppSearchSheet
import com.ff.launcher.ui.home.HomeScreen
import com.ff.launcher.ui.home.ManageFavoritesSheet
import com.ff.launcher.ui.secondary.SecondaryScreen
import com.ff.launcher.ui.secondary.TriggerTuningSheet
import com.ff.launcher.ui.secondary.WidgetPickerSheet
import com.ff.launcher.ui.onboarding.WelcomeOnboardingScreen
import com.ff.launcher.ui.trigger.EdgeTrigger
import com.ff.launcher.viewmodel.LauncherViewModel
import com.ff.launcher.widget.LauncherAppWidgetHostView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ff.launcher.R
import com.ff.launcher.ui.theme.AccentGreen

/** The wave scroller occupies 35%–85% of the screen height on the right edge. */
private const val SCROLLER_TOP_FRACTION = 0.35f
private const val SCROLLER_BOTTOM_FRACTION = 0.85f

/** SPEC Module 4: idle widgets' cached views are dropped after 5 minutes closed. */
private const val WIDGET_CACHE_IDLE_MS = 5 * 60_000L


/**
 * Root composition, layered bottom-to-top:
 * 1. Primary home screen (app list + A–Z wave scroller) over the wallpaper scrim.
 * 2. Secondary screen overlay, fading/sliding in from the trigger's edge.
 * 3. Edge trigger pill, always on top so it can open *and* close the overlay.
 *
 * All edge alignment uses [AbsoluteAlignment] (physical sides) so RTL locales can't flip
 * the trigger to the opposite edge of where its swipe direction and exclusion rect point.
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val isSecondScreenOpen by viewModel.isSecondScreenOpen.collectAsStateWithLifecycle()
    val triggerConfig by viewModel.triggerDisplay.collectAsStateWithLifecycle()
    val isTuningSheetOpen by viewModel.isTuningSheetOpen.collectAsStateWithLifecycle()
    val isArrangeFavoritesOpen by viewModel.isArrangeFavoritesOpen.collectAsStateWithLifecycle()
    val selectedAppForManage by viewModel.selectedAppForManage.collectAsStateWithLifecycle()
    val favoriteAppsList by viewModel.favoriteAppsList.collectAsStateWithLifecycle()
    val allInstalledApps by viewModel.allInstalledApps.collectAsStateWithLifecycle()
    val recentApps by viewModel.recentApps.collectAsStateWithLifecycle()
    val newlyInstalledApps by viewModel.newlyInstalledApps.collectAsStateWithLifecycle()
    val isAppSearchOpen by viewModel.isAppSearchOpen.collectAsStateWithLifecycle()
    val pickingWidgetSlotId by viewModel.pickingWidgetSlotId.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val widgetSlots by viewModel.widgetSlots.collectAsStateWithLifecycle()
    val hiddenApps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Default-launcher re-prompt: shows a top banner when the user returns after
    // switching to another launcher, session-dismissable but reset when the role toggles.
    val bannerDismissed = remember { mutableStateOf(false) }
    LaunchedEffect(isDefaultLauncher) {
        if (isDefaultLauncher) bannerDismissed.value = false
    }

    // Widget host-view cache lives HERE — activity-scoped composition — and NOT in the
    // ViewModel: AppWidgetHostView/RemoteViews need an Activity context for PendingIntent
    // launches (background-activity-launch rules), themed inflation and collection
    // widgets, so a ViewModel-owned cache would either pin the Activity across
    // recreation or break widget clicks on Android 12+. The cache dies with this
    // composition (config changes included); the 5-minute idle purge below implements
    // SPEC Module 4 for the stay-open case.
    val hostViewCache = remember { mutableMapOf<Int, LauncherAppWidgetHostView>() }
    val cachePurgeJob = remember { mutableStateOf<Job?>(null) }

    fun obtainHostView(context: Context, slotId: Int, appWidgetId: Int): LauncherAppWidgetHostView? {
        hostViewCache[slotId]?.let { cached ->
            if (cached.appWidgetId == appWidgetId) {
                // Strip a stale Compose holder to avoid "already has a parent".
                (cached.parent as? ViewGroup)?.removeView(cached)
                return cached
            }
            // The slot was re-bound to a different widget — evict the old cache.
            (cached.parent as? ViewGroup)?.removeView(cached)
            hostViewCache.remove(slotId)
        }
        return viewModel.createHostView(context, appWidgetId)?.also { hostViewCache[slotId] = it }
    }

    // Return mechanic #1: system edge-back gesture closes the Secondary Screen.
    BackHandler(enabled = isSecondScreenOpen) {
        viewModel.setSecondScreenOpen(false)
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Role-manager dialog result: re-check default-home state once the system dialog returns.
    val homeRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshDefaultLauncherState() }
    // Deck composition lifecycle drives the widget view-cache purge timer (SPEC Module 4):
    // on close, drop GPU layers off the cached host views and arm the 5-minute idle purge.
    LaunchedEffect(isSecondScreenOpen) {
        if (isSecondScreenOpen) {
            cachePurgeJob.value?.cancel()
            cachePurgeJob.value = null
        } else {
            hostViewCache.values.forEach { it.setLayerType(View.LAYER_TYPE_NONE, null) }
            cachePurgeJob.value?.cancel()
            cachePurgeJob.value = scope.launch {
                delay(WIDGET_CACHE_IDLE_MS)
                hostViewCache.clear()
            }
        }
    }

    // Evict cache entries whose slot lost its binding (widget removed) or was re-bound
    // to a different widget — otherwise a removed slot's host view (and its whole
    // RemoteViews hierarchy) lingers in the cache for up to 5 minutes.
    LaunchedEffect(widgetSlots) {
        val boundAppWidgetIds = widgetSlots
            .filter { it.appWidgetId != null }
            .associate { it.slotId to it.appWidgetId }
        hostViewCache.entries.removeAll { entry ->
            val stale = boundAppWidgetIds[entry.key] != entry.value.appWidgetId
            if (stale) {
                (entry.value.parent as? ViewGroup)?.removeView(entry.value)
                entry.value.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            stale
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f)), // readability scrim over wallpaper
    ) {
        AnimatedVisibility(
            visible = !isSecondScreenOpen,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeScreen(
                state = homeState,
                listState = listState,
                onAppClick = viewModel::launchApp,
                onAppLongClick = viewModel::openManageFavorites,
                onOpenDeck = { viewModel.setSecondScreenOpen(true) },
                onOpenSettings = { viewModel.setTuningSheetOpen(true) },
                onOpenSearch = viewModel::openAppSearch,
            )
        }

        // Re-engagement banner: prompts the user to restore FF Launcher as default
        // when they return after switching to another launcher (post-onboarding only).
        AnimatedVisibility(
            visible = onboardingDone == true &&
                !isDefaultLauncher &&
                !isSecondScreenOpen &&
                !bannerDismissed.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            DefaultLauncherBanner(
                onClick = {
                    runCatching { homeRoleLauncher.launch(viewModel.buildSetDefaultIntent()) }
                },
                onDismiss = { bannerDismissed.value = true },
            )
        }

        val slideFromEnd = triggerConfig.edgeSide == TriggerEdgeSide.RIGHT
        AnimatedVisibility(
            visible = isSecondScreenOpen,
            enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { full ->
                if (slideFromEnd) full / 4 else -full / 4
            },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { full ->
                if (slideFromEnd) full / 4 else -full / 4
            },
        ) {
            SecondaryScreen(
                slots = widgetSlots,
                onRequestWidget = viewModel::requestWidgetForSlot,
                onRemoveWidget = viewModel::removeWidgetFromSlot,
                onOpenTuning = { viewModel.setTuningSheetOpen(true) },
                onUpdateSlotWeight = viewModel::setSlotHeightWeight,
                obtainHostView = ::obtainHostView,
                onDismiss = { viewModel.setSecondScreenOpen(false) },
            )
        }

        // Pill center sits at verticalBias of the screen height, clamped on-screen.
        val triggerHeight = triggerConfig.heightDp.dp
        val triggerTop = (maxHeight * triggerConfig.verticalBias - triggerHeight / 2)
            .coerceIn(0.dp, (maxHeight - triggerHeight).coerceAtLeast(0.dp))

        // Hit-testing hand-over: with the trigger parked inside the wave scroller's band
        // on the same edge, the scroller must own the strip. Merely flagging the
        // trigger's detectors off is not enough — its top-layer 24dp target would still
        // swallow ACTION_DOWN (tap detectors consume the down), so the scroller could
        // never start and any "now scrolling" flag could never flip. Skipping the
        // detectors entirely keeps the hit path from consuming anything. The deck stays
        // reachable via the ⚙ node → tuning sheet → move the trigger.
        val scrollerTop = maxHeight * SCROLLER_TOP_FRACTION
        val scrollerBottom = maxHeight * SCROLLER_BOTTOM_FRACTION
        val overlapsScroller = slideFromEnd &&
            triggerTop < scrollerBottom &&
            triggerTop + triggerHeight > scrollerTop

        // First-run onboarding: hides the edge trigger and gates its gestures until the
        // user finishes or skips the 3-step tour (steps 1–2 must not be auto-skipped).
        val showOnboarding = onboardingDone == false

        EdgeTrigger(
            config = triggerConfig,
            isSecondScreenOpen = isSecondScreenOpen,
            // Tapping the trigger opens the deck; tapping it again closes it.
            onActivate = { viewModel.setSecondScreenOpen(!isSecondScreenOpen) },
            gesturesEnabled = !showOnboarding && (isSecondScreenOpen || !overlapsScroller),
            modifier = Modifier
                .align(if (slideFromEnd) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
                .offset(y = triggerTop),
        )

        // Composed inside BoxWithConstraints so the hand-over geometry (maxHeight) is
        // available; the sheet renders into its own window either way.
        if (isTuningSheetOpen) {
            TriggerTuningSheet(
                config = triggerConfig,
                overlapsScroller = overlapsScroller,
                hiddenApps = hiddenApps,
                isDefaultLauncher = isDefaultLauncher,
                onRequestDefaultLauncher = {
                    homeRoleLauncher.launch(viewModel.buildSetDefaultIntent())
                },
                onUnhide = viewModel::unhideApp,
                onDismiss = { viewModel.setTuningSheetOpen(false) },
                onEdgeSideChange = viewModel::setTriggerEdgeSide,
                onModeChange = viewModel::setTriggerMode,
                onVerticalBiasPreview = viewModel::previewTriggerVerticalBias,
                onVerticalBiasCommit = viewModel::setTriggerVerticalBias,
                onHeightPreview = viewModel::previewTriggerHeightDp,
                onHeightCommit = viewModel::setTriggerHeightDp,
                onWidthPreview = viewModel::previewTriggerWidthDp,
                onWidthCommit = viewModel::setTriggerWidthDp,
                onOpacityPreview = viewModel::previewTriggerOpacity,
                onOpacityCommit = viewModel::setTriggerOpacity,
            )
        }

        if (isArrangeFavoritesOpen) {
            ManageFavoritesSheet(
                targetApp = selectedAppForManage,
                favoriteApps = favoriteAppsList,
                allApps = allInstalledApps,
                onToggleFavorite = viewModel::toggleFavorite,
                onMoveFavorite = viewModel::moveFavorite,
                onHideApp = viewModel::hideApp,
                onOpenAppInfo = viewModel::openAppInfo,
                onUninstallApp = viewModel::uninstallApp,
                onDismiss = viewModel::closeManageFavorites,
            )
        }

        if (pickingWidgetSlotId != null) {
            WidgetPickerSheet(
                onDismiss = viewModel::dismissWidgetPicker,
                onSelectProvider = viewModel::onWidgetProviderSelected,
            )
        }

        if (isAppSearchOpen) {
            AppSearchSheet(
                allApps = allInstalledApps,
                recentApps = recentApps,
                newlyInstalledApps = newlyInstalledApps,
                onAppClick = viewModel::launchApp,
                onAppLongClick = viewModel::openManageFavorites,
                onDismiss = viewModel::closeAppSearch,
            )
        }

        // First-run onboarding overlays everything (topmost) until default home is set
        // or skipped. onboardingDone == null (DataStore still loading) shows nothing.
        if (showOnboarding) {
            WelcomeOnboardingScreen(
                isDefaultLauncher = isDefaultLauncher,
                onSetDefaultClick = {
                    runCatching { homeRoleLauncher.launch(viewModel.buildSetDefaultIntent()) }
                },
                onCompleteOnboarding = { viewModel.completeOnboarding() },
            )
        }
    }
}

/**
 * Material 3 banner prompting the user to reclaim FF Launcher as the default home.
 */
@Composable
private fun DefaultLauncherBanner(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Row(
        modifier = modifier
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = insets.calculateTopPadding() + 8.dp,
            )
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.banner_set_default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp)
                .clickable { onDismiss() },
        )
    }
}
