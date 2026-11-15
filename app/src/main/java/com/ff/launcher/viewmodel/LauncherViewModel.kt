package com.ff.launcher.viewmodel

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.icu.text.AlphabeticIndex
import android.icu.text.RuleBasedCollator
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff.launcher.R
import com.ff.launcher.data.AppInfo
import com.ff.launcher.data.AppRepository
import com.ff.launcher.data.FavoritesRepository
import com.ff.launcher.data.OnboardingRepository
import com.ff.launcher.data.QuickActionConfig
import com.ff.launcher.data.QuickActionPreferencesRepository
import com.ff.launcher.data.QuickActionType
import com.ff.launcher.data.TriggerConfig
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import com.ff.launcher.data.TriggerPreferencesRepository
import com.ff.launcher.data.glance.CalendarEvent
import com.ff.launcher.data.glance.CalendarProvider
import com.ff.launcher.data.glance.CalendarState
import com.ff.launcher.data.glance.HeaderConfig
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.glance.HeaderPreferencesRepository
import com.ff.launcher.data.glance.TimeData
import com.ff.launcher.data.glance.TimeProvider
import com.ff.launcher.data.glance.WeatherData
import com.ff.launcher.data.glance.WeatherRepository
import com.ff.launcher.data.db.DEFAULT_WIDGET_SLOTS
import com.ff.launcher.data.db.HiddenAppEntity
import com.ff.launcher.data.db.LauncherDatabase
import com.ff.launcher.data.db.SLOT_WEIGHT_LARGE
import com.ff.launcher.data.db.SLOT_WEIGHT_MEDIUM
import com.ff.launcher.data.db.SLOT_WEIGHT_SMALL
import com.ff.launcher.data.db.WidgetSlotEntity
import com.ff.launcher.widget.LauncherAppWidgetHost
import com.ff.launcher.widget.LauncherAppWidgetHostView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Widget binding requests routed to the Activity's registered launchers. */
sealed interface WidgetRequest {
    /**
     * `ACTION_APPWIDGET_BIND` — the system confirmation dialog shown when
     * [AppWidgetManager.bindAppWidgetIdIfAllowed] returns false.
     */
    data class BindPermission(val intent: Intent) : WidgetRequest

    /** [appWidgetId] is bound to the host; the configure page launches through it. */
    data class Configure(val appWidgetId: Int) : WidgetRequest
}

/** A hidden app with its display label, for the settings sheet's management list. */
data class HiddenAppUi(val componentKey: String, val label: String)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application, viewModelScope)
    private val database = LauncherDatabase.get(application)
    private val favoritesRepository = FavoritesRepository(application, database)
    private val triggerRepository = TriggerPreferencesRepository(application)
    private val quickActionRepository = QuickActionPreferencesRepository(application)
    private val onboardingRepository = OnboardingRepository(application)

    private val timeProvider = TimeProvider(application, viewModelScope)
    private val calendarProvider = CalendarProvider(application)
    private val weatherRepository = WeatherRepository(application)
    private val headerPreferencesRepository = HeaderPreferencesRepository(application)

    val timeState: StateFlow<TimeData> = timeProvider.timeState

    private val _calendarState = MutableStateFlow(CalendarState())
    val calendarState: StateFlow<CalendarState> = _calendarState.asStateFlow()

    val weatherState: StateFlow<WeatherData> = weatherRepository.cachedWeather
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeatherData())

    val headerConfig: StateFlow<HeaderConfig> = headerPreferencesRepository.headerConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, HeaderConfig())

    val quickActionConfig: StateFlow<QuickActionConfig> = quickActionRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, QuickActionConfig())

    private val _quickActionPreview = MutableStateFlow<QuickActionConfig?>(null)
    val quickActionDisplay: StateFlow<QuickActionConfig> =
        combine(quickActionConfig, _quickActionPreview) { persisted, preview -> preview ?: persisted }
            .stateIn(viewModelScope, SharingStarted.Eagerly, QuickActionConfig())

    private val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            Log.d(TAG, "onTorchModeChanged: cameraId=$cameraId, enabled=$enabled")
            _isFlashlightOn.value = enabled
        }
    }

    private val _isQuickActionPickerOpen = MutableStateFlow(false)
    val isQuickActionPickerOpen: StateFlow<Boolean> = _isQuickActionPickerOpen.asStateFlow()

    private val appWidgetHost = LauncherAppWidgetHost(application)
    private val appWidgetManager = AppWidgetManager.getInstance(application)

    private val _isSecondScreenOpen = MutableStateFlow(false)
    val isSecondScreenOpen: StateFlow<Boolean> = _isSecondScreenOpen.asStateFlow()

    private val _isTuningSheetOpen = MutableStateFlow(false)
    val isTuningSheetOpen: StateFlow<Boolean> = _isTuningSheetOpen.asStateFlow()

    private val _isArrangeFavoritesOpen = MutableStateFlow(false)
    val isArrangeFavoritesOpen: StateFlow<Boolean> = _isArrangeFavoritesOpen.asStateFlow()

    private val _selectedAppForManage = MutableStateFlow<AppInfo?>(null)
    val selectedAppForManage: StateFlow<AppInfo?> = _selectedAppForManage.asStateFlow()

    val allInstalledApps: StateFlow<List<AppInfo>> = appRepository.apps

    private val _hostResumed = MutableStateFlow(false)

    /**
     * When non-null, the built-in [com.ff.launcher.ui.secondary.WidgetPickerSheet] is
     * shown for this slot. Replaces the deprecated `ACTION_APPWIDGET_PICK` system
     * activity that is silently broken on most modern ROMs.
     */
    private val _pickingWidgetSlotId = MutableStateFlow<Int?>(null)
    val pickingWidgetSlotId: StateFlow<Int?> = _pickingWidgetSlotId.asStateFlow()

    /** Override set when the user skips/completes onboarding before DataStore settles. */
    private val _onboardingDoneOverride = MutableStateFlow<Boolean?>(null)
    /** Reads persisted state first, then the manual override. */
    val onboardingDone: StateFlow<Boolean?> = combine(
        onboardingRepository.isOnboardingDone,
        _onboardingDoneOverride,
    ) { persisted, override -> override ?: persisted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Re-checked on every onResume and after the role dialog returns. */
    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()
    private val _isDeckActive: StateFlow<Boolean> =
        combine(_isSecondScreenOpen, _hostResumed) { open, resumed -> open && resumed }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** One-off, fire-and-forget messages surfaced to the user as toasts. */
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    /** Widget picker/configure intents for MainActivity's registered launchers. */
    private val _widgetRequests = Channel<WidgetRequest>(Channel.BUFFERED)
    val widgetRequests: Flow<WidgetRequest> = _widgetRequests.receiveAsFlow()

    val recentApps: StateFlow<List<AppInfo>> = combine(
        database.recentAppDao().observeRecent(),
        appRepository.apps,
    ) { recents, apps ->
        val appMap = apps.associateBy { it.componentKey }
        recents.mapNotNull { appMap[it.componentKey] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val newlyInstalledApps: StateFlow<List<AppInfo>> = appRepository.apps.map { apps ->
        apps.filter { it.firstInstallTime > 0L }
            .sortedByDescending { it.firstInstallTime }
            .take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAppSearchOpen = MutableStateFlow(false)
    val isAppSearchOpen: StateFlow<Boolean> = _isAppSearchOpen.asStateFlow()

    private val _homeResetTrigger = MutableStateFlow(0L)
    val homeResetTrigger: StateFlow<Long> = _homeResetTrigger.asStateFlow()

    fun openAppSearch() {
        _isAppSearchOpen.value = true
    }

    fun closeAppSearch() {
        _isAppSearchOpen.value = false
    }

    /** Slot being bound plus the ALREADY-ALLOCATED widget id — released on any cancel. */
    private var pendingPick: Pair<Int, Int>? = null
    private var pendingConfigure: Pair<Int, Int>? = null

    /** Bounded to one live waiter; replaced (never queued) by every new commit. */
    private var settleJob: Job? = null

    /** In-memory preview overriding the persisted config while the tuning sheet is open. */
    private val _triggerPreview = MutableStateFlow<TriggerConfig?>(null)

    val triggerConfig: StateFlow<TriggerConfig> = triggerRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, TriggerConfig())

    /** What the pill renders: live preview while tuning, persisted value otherwise. */
    val triggerDisplay: StateFlow<TriggerConfig> =
        combine(triggerConfig, _triggerPreview) { persisted, preview -> preview ?: persisted }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TriggerConfig())

    val widgetSlots: StateFlow<List<WidgetSlotEntity>> =
        database.widgetSlotDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Grouping/sorting runs on [Dispatchers.Default] — it rebuilds the ICU index and
     * re-sorts hundreds of localized labels, and re-runs on every favorite toggle.
     * Hidden apps (Phase 3 sets them from the row menu) are filtered out here.
     */
    private val hiddenKeysFlow: Flow<Set<String>> =
        database.hiddenAppDao().observeAll()
            .map { rows -> rows.mapTo(HashSet(rows.size)) { it.componentKey } }

    /** Hidden apps with display labels, for the settings sheet's management list. */
    val hiddenApps: StateFlow<List<HiddenAppUi>> =
        combine(appRepository.apps, hiddenKeysFlow) { apps, hidden ->
            apps.filter { it.componentKey in hidden }
                .map { HiddenAppUi(it.componentKey, it.label) }
                .sortedBy { it.label.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pinned favorite apps in user-arranged sequence for the arrange-favorites sheet. */
    val favoriteAppsList: StateFlow<List<AppInfo>> = combine(
        appRepository.apps,
        favoritesRepository.favoriteKeysOrdered,
        hiddenKeysFlow,
    ) { apps, orderedKeys, hiddenKeys ->
        val visibleByKey = apps.filter { it.componentKey !in hiddenKeys }.associateBy { it.componentKey }
        orderedKeys.mapNotNull { visibleByKey[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val homeState: StateFlow<HomeListState> =
        combine(
            appRepository.apps,
            favoritesRepository.favoriteKeysOrdered,
            hiddenKeysFlow,
            ::buildHomeState,
        )
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeListState())

    private var isListening = false

    /**
     * SPEC Module 4: listening requires the deck open AND the host RESUMED. Only a
     * SUCCESSFUL start sets the flag — a failed Binder call must stay retryable, and a
     * `setSecondScreenOpen(true)` from a paused host must not start listening early.
     */
    private fun ensureListening() {
        if (isListening || !_hostResumed.value) return
        runCatching { appWidgetHost.startListening() }
            .onSuccess { isListening = true }
            .onFailure { Log.w(TAG, "startListening failed", it) }
    }

    init {
        viewModelScope.launch {
            database.widgetSlotDao().ensureDefaults(DEFAULT_WIDGET_SLOTS)
            favoritesRepository.migrateFromLegacyIfNeeded()
        }
        // Smart default favorites seeding: waits for the first non-empty apps scan, then
        // auto-pins the device's dialer / browser / contacts / gallery / email / camera.
        viewModelScope.launch {
            val initialApps = appRepository.apps.first { it.isNotEmpty() }
            favoritesRepository.seedDefaultFavoritesIfNeeded(initialApps)
        }
        // Default-home state: re-checked on every onResume via [refreshDefaultLauncherState].
        refreshDefaultLauncherState()
        refreshCalendar()
        refreshWeather()
        runCatching {
            cameraManager?.registerTorchCallback(torchCallback, null)
            Log.i(TAG, "Registered torch callback")
        }.onFailure { Log.w(TAG, "Failed to register torch callback", it) }
        // SPEC Module 4: listen ONLY while the deck is open AND the activity is resumed.
        viewModelScope.launch {
            _isDeckActive.collect { active ->
                if (active) {
                    ensureListening()
                } else if (isListening) {
                    isListening = false
                    runCatching { appWidgetHost.stopListening() }
                }
            }
        }
        // Prune favorites/hidden rows of uninstalled packages (updates are excluded by
        // `replacing`), and drop widget bindings whose provider just disappeared — the
        // system removes its widgets on host-app uninstall, leaving stale ids behind.
        // Slot rows are read straight from the DAO: widgetSlots' upstream may be cold
        // (WhileSubscribed) after 5s in the background, so `.value` could be stale.
        viewModelScope.launch {
            appRepository.packageEvents.collect { event ->
                if (event is com.ff.launcher.data.PackageEvent.Removed && !event.replacing) {
                    favoritesRepository.removePackage(event.packageName, event.userSerial)
                    database.hiddenAppDao().deleteForPackage(event.packageName, event.userSerial.toString())
                    database.widgetSlotDao().getAll().forEach { slot ->
                        val id = slot.appWidgetId ?: return@forEach
                        if (appWidgetManager.getAppWidgetInfo(id) == null) {
                            database.widgetSlotDao().clearWidget(slot.slotId)
                            releaseAppWidgetIdAsync(id)
                        }
                    }
                }
            }
        }
    }

    fun setSecondScreenOpen(open: Boolean) {
        // Deck opening triggers recomposition → createView on the first frame; listening
        // must already be up or widgets miss their initial update broadcast.
        if (open) ensureListening()
        _isSecondScreenOpen.value = open
    }

    fun setTuningSheetOpen(open: Boolean) {
        _isTuningSheetOpen.value = open
    }

    fun setArrangeFavoritesOpen(open: Boolean) {
        _isArrangeFavoritesOpen.value = open
    }

    fun openManageFavorites(app: AppInfo? = null) {
        _selectedAppForManage.value = app
        _isArrangeFavoritesOpen.value = true
    }

    fun closeManageFavorites() {
        _selectedAppForManage.value = null
        _isArrangeFavoritesOpen.value = false
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            _calendarState.value = calendarProvider.loadUpcomingEvents()
        }
    }

    fun refreshWeather(force: Boolean = false) {
        viewModelScope.launch {
            weatherRepository.getOrFetchWeather(forceRefresh = force)
        }
    }

    fun setHeaderMode(mode: HeaderMode) {
        Log.i(TAG, "setHeaderMode: $mode")
        viewModelScope.launch { headerPreferencesRepository.setHeaderMode(mode) }
    }

    fun setHeaderUseFahrenheit(useFahrenheit: Boolean) {
        Log.i(TAG, "setHeaderUseFahrenheit: $useFahrenheit")
        viewModelScope.launch { headerPreferencesRepository.setUseFahrenheit(useFahrenheit) }
    }

    fun moveFavorite(componentKey: String, delta: Int) {
        Log.i(TAG, "moveFavorite: $componentKey, delta=$delta")
        viewModelScope.launch { favoritesRepository.moveFavorite(componentKey, delta) }
    }

    fun updateFavoriteOrder(orderedKeys: List<String>) {
        Log.i(TAG, "updateFavoriteOrder: count=${orderedKeys.size}")
        viewModelScope.launch { favoritesRepository.updateOrder(orderedKeys) }
    }

    fun setHostResumed(resumed: Boolean) {
        _hostResumed.value = resumed
    }

    /** Re-checks whether this launcher is the current default home (onResume etc.). */
    fun refreshDefaultLauncherState() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDefaultLauncher.value = onboardingRepository.isDefaultLauncher()
        }
    }

    /** Marks onboarding as completed (default achieved or explicitly skipped). */
    fun completeOnboarding() {
        _onboardingDoneOverride.value = true
        viewModelScope.launch { onboardingRepository.setOnboardingDone() }
    }

    /** The system request that makes the user confirm this launcher as default home. */
    fun buildSetDefaultIntent(): Intent = onboardingRepository.createSetDefaultIntent()

    /**
     * Closes the Secondary Screen together with any open sheets (tuning sheet, arrange favorites,
     * search sheet, widget picker) so the launcher returns cleanly to the primary home screen.
     */
    fun closeSecondary() {
        // A pending bind dies with the deck: release the reserved ids, otherwise the
        // onHostStopped exemption would short-circuit forever after a Home gesture out
        // of the picker/configure screen (singleTask + clearTaskOnLaunch can skip the
        // normal RESULT_CANCELED callback).
        pendingPick?.second?.let(::releaseAppWidgetIdAsync)
        pendingPick = null
        pendingConfigure?.first?.let(::releaseAppWidgetIdAsync)
        pendingConfigure = null
        _pickingWidgetSlotId.value = null
        _isAppSearchOpen.value = false
        _isSecondScreenOpen.value = false
        _isTuningSheetOpen.value = false
        _selectedAppForManage.value = null
        _isArrangeFavoritesOpen.value = false
    }

    /**
     * Handles returning to the primary home screen when the user taps the system Home button
     * or performs the home navigation gesture. Closes all overlay screens and sheets and
     * signals the home screen to collapse and scroll back to top.
     */
    fun onHomeIntent() {
        closeSecondary()
        _homeResetTrigger.value = System.currentTimeMillis()
    }

    /**
     * allocate/delete are synchronous Binder calls into AppWidgetService that also write
     * `/data/system/.../appwidgets.xml` — releasing on the main thread (Home gesture,
     * back press) risks jank/ANR under I/O pressure, so every release goes to IO.
     */
    private fun releaseAppWidgetIdAsync(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
                .onFailure { Log.w(TAG, "deleteAppWidgetId failed for $appWidgetId", it) }
        }
    }

    /**
     * Auto-close the Secondary Screen whenever the host activity stops — UNLESS the stop
     * is caused by our own widget picker or configure activity: the user is mid-binding
     * and expects to land back inside the deck, not on the primary screen.
     */
    fun onHostStopped() {
        if (pendingPick != null || pendingConfigure != null) return
        closeSecondary()
    }

    fun launchApp(app: AppInfo) {
        Log.i(TAG, "launchApp: ${app.label} (${app.componentKey})")
        viewModelScope.launch {
            runCatching {
                database.recentAppDao().recordLaunch(
                    com.ff.launcher.data.db.RecentAppEntity(
                        app.componentKey,
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
        val component = parseComponent(app.componentKey) ?: return
        val context = getApplication<Application>()

        // Spec-mandated launch path: LauncherApps routes through the system's launcher
        // permission checks and can start work-profile apps a bare Intent never could.
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val started = runCatching {
                launcherApps.startMainActivity(component, app.userHandle, null, null)
            }.onFailure { Log.w(TAG, "startMainActivity failed for ${app.label}", it) }
                .isSuccess
            if (started) return
            if (app.userHandle != Process.myUserHandle()) {
                // The fallback below can only start apps in the calling user — it would
                // silently open the personal-profile twin of the tapped work-profile app.
                _userMessages.tryEmit(context.getString(R.string.error_work_profile))
                return
            }
        }

        // Fallback for the calling user while the launcher isn't yet the default HOME
        // (LauncherApps restricts some paths to the active launcher during development).
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "startActivity failed for ${app.label}", it)
                _userMessages.tryEmit(context.getString(R.string.error_launch_failed))
            }
    }

    fun toggleFavorite(app: AppInfo) {
        Log.i(TAG, "toggleFavorite: ${app.label} (${app.componentKey})")
        viewModelScope.launch { favoritesRepository.toggle(app.componentKey) }
    }

    fun hideApp(app: AppInfo) {
        viewModelScope.launch {
            database.hiddenAppDao().upsert(
                HiddenAppEntity(
                    componentKey = app.componentKey,
                    hiddenAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun unhideApp(componentKey: String) {
        viewModelScope.launch { database.hiddenAppDao().delete(componentKey) }
    }

    fun setSlotHeightWeight(slotId: Int, heightWeight: Float) {
        val safeWeight = heightWeight.coerceIn(SLOT_WEIGHT_SMALL, SLOT_WEIGHT_LARGE)
        viewModelScope.launch {
            runCatching { database.widgetSlotDao().updateHeightWeight(slotId, safeWeight) }
                .onFailure { Log.w(TAG, "updateHeightWeight failed", it) }
        }
    }

    fun openAppInfo(app: AppInfo) {
        val component = parseComponent(app.componentKey) ?: return
        val context = getApplication<Application>()

        // The standard cross-profile entry point: opens the App Info page of the tapped
        // app inside its own profile (work profile included).
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            val opened = runCatching {
                launcherApps.startAppDetailsActivity(component, app.userHandle, null, null)
            }.onFailure { Log.w(TAG, "startAppDetailsActivity failed for ${app.label}", it) }
                .isSuccess
            if (opened) return
            if (app.userHandle != Process.myUserHandle()) {
                // Same defense as launchApp: the fallback below runs in the calling user
                // and would open the personal-profile twin's page (or "app not found").
                _userMessages.tryEmit(context.getString(R.string.error_work_profile))
                return
            }
        }

        // Calling-user fallback only.
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "openAppInfo failed for ${app.label}", it)
                _userMessages.tryEmit(context.getString(R.string.error_launch_failed))
            }
    }

    fun uninstallApp(app: AppInfo) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "uninstall failed for ${app.label}", it)
                _userMessages.tryEmit(context.getString(R.string.error_launch_failed))
            }
    }

    // --- Widget slots (Phase 3 built-in picker) ---

    /** Opens the built-in widget picker sheet for [slotId]. */
    fun requestWidgetForSlot(slotId: Int) {
        _pickingWidgetSlotId.value = slotId
    }

    fun dismissWidgetPicker() {
        _pickingWidgetSlotId.value = null
    }

    /**
     * User selected a widget in the built-in picker: allocates a host id, attempts
     * `bindAppWidgetIdIfAllowed` (true for the default launcher), and either proceeds
     * to configure/bind or fires the system `ACTION_APPWIDGET_BIND` permission dialog.
     */
    fun onWidgetProviderSelected(providerInfo: AppWidgetProviderInfo) {
        val slotId = _pickingWidgetSlotId.value ?: return
        _pickingWidgetSlotId.value = null

        viewModelScope.launch {
            val appWidgetId = withContext(Dispatchers.IO) {
                runCatching { appWidgetHost.allocateAppWidgetId() }.getOrNull()
            }
            if (appWidgetId == null) {
                Log.w(TAG, "Failed to allocate appWidgetId for slot $slotId")
                return@launch
            }

            val displayMetrics = getApplication<Application>().resources.displayMetrics
            val widthDp = (displayMetrics.widthPixels / displayMetrics.density).roundToInt()
            val heightDp = ((displayMetrics.heightPixels / displayMetrics.density) / 3).roundToInt()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp - 36)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp - 36)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp - 28)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp - 28)
            }
            val bound = withContext(Dispatchers.IO) {
                runCatching {
                    appWidgetManager.bindAppWidgetIdIfAllowed(
                        appWidgetId,
                        providerInfo.profile,
                        providerInfo.provider,
                        options,
                    )
                }.getOrDefault(false)
            }

            Log.d(TAG, "onWidgetProviderSelected: slotId=$slotId, appWidgetId=$appWidgetId, bound=$bound, provider=${providerInfo.provider}")

            if (bound) {
                proceedAfterWidgetBound(slotId, appWidgetId)
            } else {
                pendingPick = slotId to appWidgetId
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
                }
                _widgetRequests.trySend(WidgetRequest.BindPermission(bindIntent))
            }
        }
    }

    /** Called when the system ACTION_APPWIDGET_BIND activity returns. */
    fun onBindPermissionResult(resultCode: Int, data: Intent?) {
        val pending = pendingPick ?: return
        val (slotId, appWidgetId) = pending
        pendingPick = null
        Log.d(TAG, "onBindPermissionResult: resultCode=$resultCode, slotId=$slotId, appWidgetId=$appWidgetId")
        if (resultCode == android.app.Activity.RESULT_OK) {
            val actualId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) ?: appWidgetId
            proceedAfterWidgetBound(slotId, actualId)
        } else {
            releaseAppWidgetIdAsync(appWidgetId)
        }
    }

    /** After a successful bind: launch the configure page if the widget has one. */
    private fun proceedAfterWidgetBound(slotId: Int, appWidgetId: Int) {
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)
        Log.d(TAG, "proceedAfterWidgetBound: slotId=$slotId, appWidgetId=$appWidgetId, provider=$provider, configure=${provider?.configure}")
        if (provider?.configure != null) {
            pendingConfigure = appWidgetId to slotId
            _widgetRequests.trySend(WidgetRequest.Configure(appWidgetId))
        } else {
            bindSlot(slotId, appWidgetId)
        }
    }

    /**
     * Launches the widget's configure page through [AppWidgetHost.startAppWidgetConfigure
     * ActivityForResult] — the system grants the temporary start permission for
     * non-exported configure activities that a plain Intent launch would crash on
     * (SecurityException / ActivityNotFoundException, Android 11+).
     */
    fun startWidgetConfigure(activity: Activity, appWidgetId: Int, requestCode: Int) {
        val started = runCatching {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                appWidgetId,
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY,
                requestCode,
                null,
            )
        }.onFailure { Log.w(TAG, "startWidgetConfigure failed for widget $appWidgetId", it) }
            .isSuccess
        if (!started) onWidgetConfigureResult(Activity.RESULT_CANCELED, null)
    }

    fun onWidgetConfigureResult(resultCode: Int, data: Intent?) {
        val pending = pendingConfigure
        pendingConfigure = null
        val (appWidgetId, slotId) = pending ?: return
        val actualId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) ?: appWidgetId
        Log.d(TAG, "onWidgetConfigureResult: resultCode=$resultCode, slotId=$slotId, appWidgetId=$actualId")
        val info = appWidgetManager.getAppWidgetInfo(actualId)
        val isOptional = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info != null) {
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        } else false

        if (resultCode == android.app.Activity.RESULT_OK || isOptional) {
            bindSlot(slotId, actualId)
        } else {
            // Configure abandoned — release the id or it leaks in AppWidgetService.
            releaseAppWidgetIdAsync(actualId)
        }
    }

    fun removeWidgetFromSlot(slotId: Int) {
        viewModelScope.launch {
            val slot = runCatching { database.widgetSlotDao().getById(slotId) }.getOrNull()
            database.widgetSlotDao().clearWidget(slotId)
            slot?.appWidgetId?.let(::releaseAppWidgetIdAsync)
        }
    }

    /**
     * Creates a fresh host view with the CALLING (activity) context — never cache it
     * here: the ViewModel outlives Activities, and AppWidgetHostView/RemoteViews need
     * an Activity context for PendingIntent launches (BAL), themed inflation and
     * collection widgets. The activity-scoped cache lives in the UI layer instead.
     */
    fun createHostView(activityContext: Context, appWidgetId: Int): LauncherAppWidgetHostView? {
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching {
            appWidgetHost.createView(activityContext, appWidgetId, provider)
                as LauncherAppWidgetHostView
        }.onFailure { Log.w(TAG, "createView failed for widget $appWidgetId", it) }
            .getOrNull()
    }

    private fun bindSlot(slotId: Int, appWidgetId: Int) {
        viewModelScope.launch {
            val current = runCatching { database.widgetSlotDao().getById(slotId) }.getOrNull()
            val oldId = current?.appWidgetId
            val weight = current?.heightWeight ?: SLOT_WEIGHT_MEDIUM
            runCatching {
                database.widgetSlotDao().upsert(WidgetSlotEntity(slotId, appWidgetId, weight))
            }.onSuccess {
                Log.d(TAG, "Successfully bound widget $appWidgetId to slot $slotId")
                refreshWidgetListening()
            }.onFailure {
                Log.w(TAG, "bindSlot failed", it)
                releaseAppWidgetIdAsync(appWidgetId)
            }
            // Replacing a widget leaks the OLD id into AppWidgetService unless deleted.
            if (oldId != null && oldId != appWidgetId) {
                releaseAppWidgetIdAsync(oldId)
            }
        }
    }

    /**
     * Staggered startListening refresh: newly bound widgets or newly inflated host views
     * need AppWidgetService to push the latest RemoteViews. Running a staggered refresh
     * (immediate, 350ms, 800ms) guarantees the initial snapshot is received even if the
     * widget provider app took a brief moment to generate its views.
     */
    fun refreshWidgetListening() {
        if (!_hostResumed.value) return
        viewModelScope.launch {
            runCatching { appWidgetHost.startListening() }
            delay(350)
            runCatching { appWidgetHost.startListening() }
            delay(800)
            runCatching { appWidgetHost.startListening() }
        }
    }

    // --- Edge trigger tuning: previews are pure memory; only commits touch disk. ---

    fun previewTriggerVerticalBias(bias: Float) =
        previewTrigger {
            // Coerce identically to the persisted path — otherwise a float slipping past
            // the range boundary would never equal the config that flows back, and the
            // preview retirement below would always burn its full timeout.
            it.copy(verticalBias = bias.coerceIn(TriggerPreferencesRepository.VERTICAL_BIAS_RANGE))
        }

    fun previewTriggerHeightDp(height: Int) =
        previewTrigger { it.copy(heightDp = height.coerceIn(TriggerPreferencesRepository.HEIGHT_RANGE)) }

    fun previewTriggerWidthDp(width: Int) =
        previewTrigger { it.copy(widthDp = width.coerceIn(TriggerPreferencesRepository.WIDTH_RANGE)) }

    fun previewTriggerOpacity(opacity: Float) =
        previewTrigger { it.copy(opacity = opacity.coerceIn(TriggerPreferencesRepository.OPACITY_RANGE)) }

    fun setTriggerEdgeSide(side: TriggerEdgeSide) {
        previewTrigger { it.copy(edgeSide = side) }
        commitAndSettle { triggerRepository.setEdgeSide(side) }
    }

    fun setTriggerVerticalBias(bias: Float) {
        previewTrigger { it.copy(verticalBias = bias.coerceIn(TriggerPreferencesRepository.VERTICAL_BIAS_RANGE)) }
        commitAndSettle { triggerRepository.setVerticalBias(bias) }
    }

    fun setTriggerHeightDp(height: Int) {
        previewTrigger { it.copy(heightDp = height.coerceIn(TriggerPreferencesRepository.HEIGHT_RANGE)) }
        commitAndSettle { triggerRepository.setHeightDp(height) }
    }

    fun setTriggerWidthDp(width: Int) {
        previewTrigger { it.copy(widthDp = width.coerceIn(TriggerPreferencesRepository.WIDTH_RANGE)) }
        commitAndSettle { triggerRepository.setWidthDp(width) }
    }

    fun setTriggerOpacity(opacity: Float) {
        previewTrigger { it.copy(opacity = opacity.coerceIn(TriggerPreferencesRepository.OPACITY_RANGE)) }
        commitAndSettle { triggerRepository.setOpacity(opacity) }
    }

    fun setTriggerMode(mode: TriggerMode) {
        previewTrigger { it.copy(mode = mode) }
        commitAndSettle { triggerRepository.setMode(mode) }
    }

    private fun previewTrigger(transform: (TriggerConfig) -> TriggerConfig) {
        _triggerPreview.value = transform(_triggerPreview.value ?: triggerConfig.value)
    }

    /**
     * Persists the commit, then retires the preview only after the persisted flow has
     * caught up (bounded wait). Clearing the preview eagerly — e.g. when the sheet is
     * dismissed while the write is still in flight — would flash the pre-tuning value
     * back onto the pill for one frame before the new value lands.
     *
     * The persist itself always runs to completion (DataStore serializes concurrent
     * edits, so ordering holds even across replaced waiters); only the preview-retirement
     * wait is replaced when a newer commit arrives, bounding live waiters to one.
     */
    private fun commitAndSettle(persist: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { persist() }
                .onFailure { Log.w(TAG, "Trigger persist failed", it) }
        }
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            val preview = _triggerPreview.value ?: return@launch
            withTimeoutOrNull(1_000) { triggerConfig.first { it == preview } }
            if (_triggerPreview.value == preview) _triggerPreview.value = null
        }
    }

    fun setTriggerEnabled(enabled: Boolean) {
        Log.i(TAG, "setTriggerEnabled: enabled=$enabled")
        if (!enabled && _isSecondScreenOpen.value) {
            setSecondScreenOpen(false)
        }
        viewModelScope.launch {
            triggerRepository.setEnabled(enabled)
        }
    }

    // --- Quick Action Floating Button ---

    fun openQuickActionPicker() {
        Log.i(TAG, "openQuickActionPicker")
        _isQuickActionPickerOpen.value = true
    }

    fun closeQuickActionPicker() {
        Log.i(TAG, "closeQuickActionPicker")
        _isQuickActionPickerOpen.value = false
    }

    fun setQuickActionEnabled(enabled: Boolean) {
        Log.i(TAG, "setQuickActionEnabled: enabled=$enabled")
        viewModelScope.launch { quickActionRepository.setEnabled(enabled) }
    }

    fun setQuickAction(type: QuickActionType, componentKey: String = "", label: String = "") {
        Log.i(TAG, "setQuickAction: type=$type, componentKey=$componentKey, label=$label")
        viewModelScope.launch { quickActionRepository.setAction(type, componentKey, label) }
    }

    fun setQuickActionEdgeSide(side: TriggerEdgeSide) {
        Log.i(TAG, "setQuickActionEdgeSide: side=$side")
        viewModelScope.launch { quickActionRepository.setEdgeSide(side) }
    }

    fun previewQuickActionVerticalBias(bias: Float) {
        val base = _quickActionPreview.value ?: quickActionConfig.value
        _quickActionPreview.value = base.copy(
            verticalBias = bias.coerceIn(QuickActionPreferencesRepository.VERTICAL_BIAS_RANGE)
        )
    }

    fun previewQuickActionSizeDp(sizeDp: Int) {
        val base = _quickActionPreview.value ?: quickActionConfig.value
        _quickActionPreview.value = base.copy(
            sizeDp = sizeDp.coerceIn(QuickActionPreferencesRepository.SIZE_RANGE)
        )
    }

    fun previewQuickActionOpacity(opacity: Float) {
        val base = _quickActionPreview.value ?: quickActionConfig.value
        _quickActionPreview.value = base.copy(
            opacity = opacity.coerceIn(QuickActionPreferencesRepository.OPACITY_RANGE)
        )
    }

    fun setQuickActionVerticalBias(bias: Float) {
        val clamped = bias.coerceIn(QuickActionPreferencesRepository.VERTICAL_BIAS_RANGE)
        Log.i(TAG, "setQuickActionVerticalBias: bias=$clamped")
        _quickActionPreview.value = null
        viewModelScope.launch { quickActionRepository.setVerticalBias(clamped) }
    }

    fun setQuickActionSizeDp(size: Int) {
        val clamped = size.coerceIn(QuickActionPreferencesRepository.SIZE_RANGE)
        Log.i(TAG, "setQuickActionSizeDp: size=$clamped")
        _quickActionPreview.value = null
        viewModelScope.launch { quickActionRepository.setSizeDp(clamped) }
    }

    fun setQuickActionOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(QuickActionPreferencesRepository.OPACITY_RANGE)
        Log.i(TAG, "setQuickActionOpacity: opacity=$clamped")
        _quickActionPreview.value = null
        viewModelScope.launch { quickActionRepository.setOpacity(clamped) }
    }

    fun resetQuickActionToDefaults() {
        Log.i(TAG, "resetQuickActionToDefaults")
        _quickActionPreview.value = null
        viewModelScope.launch { quickActionRepository.resetToDefaults() }
    }

    fun toggleFlashlight() {
        val cm = cameraManager ?: return
        runCatching {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cm.cameraIdList.firstOrNull() ?: return
            val target = !_isFlashlightOn.value
            Log.i(TAG, "toggleFlashlight: target=$target, cameraId=$cameraId")
            cm.setTorchMode(cameraId, target)
            _isFlashlightOn.value = target
        }.onFailure { Log.w(TAG, "Failed to toggle flashlight", it) }
    }

    fun executeQuickAction() {
        val config = quickActionConfig.value
        val context = getApplication<Application>()
        Log.i(TAG, "executeQuickAction: type=${config.actionType}, key=${config.componentKey}")
        when (config.actionType) {
            QuickActionType.FLASHLIGHT -> toggleFlashlight()
            QuickActionType.CAMERA -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure { _userMessages.tryEmit(context.getString(R.string.error_launch_failed)) }
            }
            QuickActionType.ALARM -> {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .recoverCatching {
                        context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    .onFailure { _userMessages.tryEmit(context.getString(R.string.error_launch_failed)) }
            }
            QuickActionType.ASSISTANT -> {
                val intent = Intent(Intent.ACTION_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure { _userMessages.tryEmit(context.getString(R.string.error_launch_failed)) }
            }
            QuickActionType.APP -> {
                if (config.componentKey.isNotBlank()) {
                    val app = allInstalledApps.value.firstOrNull { it.componentKey == config.componentKey }
                    if (app != null) {
                        launchApp(app)
                    } else {
                        val component = parseComponent(config.componentKey)
                        if (component != null) {
                            val intent = Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LAUNCHER)
                                .setComponent(component)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            runCatching { context.startActivity(intent) }
                                .onFailure { _userMessages.tryEmit(context.getString(R.string.error_launch_failed)) }
                        } else {
                            openQuickActionPicker()
                        }
                    }
                } else {
                    openQuickActionPicker()
                }
            }
        }
    }

    override fun onCleared() {
        appRepository.dispose()
        timeProvider.dispose()
        runCatching {
            cameraManager?.unregisterTorchCallback(torchCallback)
            Log.i(TAG, "Unregistered torch callback")
        }
        runCatching { appWidgetHost.stopListening() }
        super.onCleared()
    }

    private fun parseComponent(componentKey: String): ComponentName? =
        ComponentName.unflattenFromString(
            componentKey.substringBeforeLast(AppInfo.PROFILE_KEY_SEPARATOR),
        )

    /**
     * Builds the primary home list. The ICU index locale IS the scroller's locale (a
     * confirmed product decision): ja devices get kana buckets, ko devices jamo, zh
     * devices pinyin letters — and only buckets that actually contain apps become
     * sections, so the strip renders no dead nodes.
     *
     * Pinyin rescue (round-8 synthesis): labels the system locale's collation can't
     * place (hanzi under en, digits, symbols) get a SECOND pass through the
     * `Locale.SIMPLIFIED_CHINESE` index so hanzi apps still land in the A–Z sections
     * under non-CJK systems — while ja/ko natives, already NORMAL in their own locale,
     * never touch the rescue and keep their native sections. Everything the two indexes
     * can't place ends up in one trailing `#` section. In-group sorting uses the zh
     * collation for A–Z sections (latin + rescued hanzi order correctly) and the primary
     * collation otherwise. Runs on [Dispatchers.Default] (see [homeState]).
     */
    private fun buildHomeState(
        apps: List<AppInfo>,
        orderedFavoriteKeys: List<String>,
        hiddenKeys: Set<String>,
    ): HomeListState {
        // Hidden apps leave the home entirely — both the A–Z sections and favorites.
        val visibleApps = apps.filter { it.componentKey !in hiddenKeys }
        val favoritesSet = orderedFavoriteKeys.toSet()
        val primaryBuilder = AlphabeticIndex<Void>(Locale.getDefault())
            .addLabels(Locale.ENGLISH)
        val primaryCollator = primaryBuilder.collator
        val primary = primaryBuilder.buildImmutableIndex()
        val pinyinBuilder = AlphabeticIndex<Void>(Locale.SIMPLIFIED_CHINESE)
            .addLabels(Locale.ENGLISH)
        val pinyinCollator = pinyinBuilder.collator
        val pinyin = pinyinBuilder.buildImmutableIndex()

        // Primary bucket display order for section sequencing.
        val primaryOrder = HashMap<String, Int>()
        for (bucketIndex in 0 until primary.getBucketCount()) {
            val label = sectionLabelOf(primary, bucketIndex)
            if (label != null) primaryOrder[label] = bucketIndex
        }

        data class Placement(val app: AppInfo, val section: String)
        val placements = visibleApps.map { app ->
            val direct = sectionLabelOf(app.label, primary)?.let { label ->
                // NORMAL bucket confirmed by label lookup — keep the direct placement.
                label
            }
            when {
                direct != null -> Placement(app, direct)
                else -> {
                    val rescued = sectionLabelOf(app.label, pinyin)
                    if (rescued != null) Placement(app, rescued) else Placement(app, "#")
                }
            }
        }

        val rows = mutableListOf<HomeRow>()
        val sections = mutableListOf<HomeListState.Section>()

        // Maintain the user's arranged sequence for favorites:
        val visibleByKey = visibleApps.associateBy { it.componentKey }
        val favoriteApps = orderedFavoriteKeys.mapNotNull { visibleByKey[it] }
        favoriteApps.forEach { rows += HomeRow.AppRow(app = it, isFavorite = true) }
        if (favoriteApps.isNotEmpty()) rows += HomeRow.Divider

        val bySection = placements.groupBy { it.section }
        bySection.keys
            .filter { it != "#" }
            .sortedWith(compareBy({ primaryOrder[it] ?: Int.MAX_VALUE }, { it }))
            .forEach { section ->
                // A–Z sections hold latin + pinyin-rescued hanzi → zh collation orders
                // both correctly; native-script sections use their own collation.
                val latinSection = section.length == 1 && section[0] in 'A'..'Z'
                val collator = if (latinSection) pinyinCollator else primaryCollator
                sections += HomeListState.Section(section, rows.size)
                rows += HomeRow.Header(section)
                bySection.getValue(section)
                    .sortedWith { a, b -> compareLabels(a.app.label, b.app.label, collator, a.app.componentKey, b.app.componentKey) }
                    .forEach { rows += HomeRow.AppRow(it.app, isFavorite = it.app.componentKey in favoritesSet) }
            }
        bySection["#"]?.let { members ->
            sections += HomeListState.Section("#", rows.size)
            rows += HomeRow.Header("#")
            members.sortedWith { a, b -> compareLabels(a.app.label, b.app.label, pinyinCollator, a.app.componentKey, b.app.componentKey) }
                .forEach { rows += HomeRow.AppRow(it.app, isFavorite = it.app.componentKey in favoritesSet) }
        }
        return HomeListState(
            rows = rows,
            sections = sections,
            favoriteCount = favoriteApps.size,
            favoriteKeys = orderedFavoriteKeys,
        )
    }

    /** The real (NORMAL) bucket label for [bucketIndex], or null for under/overflow. */
    private fun sectionLabelOf(
        index: AlphabeticIndex.ImmutableIndex<Void>,
        bucketIndex: Int,
    ): String? = runCatching {
        val bucket = index.getBucket(bucketIndex)
        if (bucket.labelType != AlphabeticIndex.Bucket.LabelType.NORMAL) return@runCatching null
        bucket.label.trim().takeUnless { it.isEmpty() }
    }.getOrNull()

    /** The section label [label] resolves to under [index], or null if it overflows. */
    private fun sectionLabelOf(
        label: String,
        index: AlphabeticIndex.ImmutableIndex<Void>,
    ): String? = runCatching {
        val bucket = index.getBucket(index.getBucketIndex(label))
        if (bucket.labelType != AlphabeticIndex.Bucket.LabelType.NORMAL) return@runCatching null
        bucket.label.trim().takeUnless { it.isEmpty() }
    }.getOrNull()

    private fun compareLabels(
        a: String,
        b: String,
        collator: RuleBasedCollator,
        aKey: String,
        bKey: String,
    ): Int = collator.compare(a, b).takeIf { it != 0 } ?: aKey.compareTo(bKey)

    private companion object {
        private const val TAG = "LauncherViewModel"
    }
}
