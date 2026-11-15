package com.ff.launcher.viewmodel

import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ff.launcher.data.AppInfo
import com.ff.launcher.data.AppRepository
import com.ff.launcher.data.QuickActionConfig
import com.ff.launcher.data.QuickActionPreferencesRepository
import com.ff.launcher.data.QuickActionType
import com.ff.launcher.data.TriggerConfig
import com.ff.launcher.data.TriggerEdgeSide
import com.ff.launcher.data.TriggerMode
import com.ff.launcher.data.TriggerPreferencesRepository
import com.ff.launcher.data.db.DEFAULT_WIDGET_SLOTS
import com.ff.launcher.data.db.HOME_WIDGET_SLOT_ID
import com.ff.launcher.data.db.LauncherDatabase
import com.ff.launcher.data.db.WidgetSlotEntity
import com.ff.launcher.data.glance.HeaderConfig
import com.ff.launcher.data.glance.HeaderMode
import com.ff.launcher.data.glance.HeaderPreferencesRepository
import com.ff.launcher.data.glance.HeaderSize
import com.ff.launcher.data.glance.WeatherData
import com.ff.launcher.data.glance.WeatherRepository
import com.ff.launcher.data.notification.BadgePreferences
import com.ff.launcher.data.notification.BadgePreferencesRepository
import com.ff.launcher.data.notification.BadgeScope
import com.ff.launcher.notification.NotificationAccessHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel for [com.ff.launcher.SettingsActivity].
 *
 * Exposes observable StateFlows from DataStore and Room so that settings adjustments
 * reflect live in real time and persist directly across all launcher surfaces.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application, viewModelScope)
    private val database = LauncherDatabase.get(application)
    private val triggerRepository = TriggerPreferencesRepository(application)
    private val quickActionRepository = QuickActionPreferencesRepository(application)
    private val headerPreferencesRepository = HeaderPreferencesRepository(application)
    private val weatherRepository = WeatherRepository(application)
    private val badgePreferencesRepository = BadgePreferencesRepository(application)

    val badgePreferences: StateFlow<BadgePreferences> = badgePreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, BadgePreferences())

    private val _hasNotificationAccess = MutableStateFlow(NotificationAccessHelper.hasAccess(application))
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    fun checkNotificationAccess() {
        _hasNotificationAccess.value = NotificationAccessHelper.hasAccess(getApplication())
    }

    fun openNotificationAccessSettings() {
        NotificationAccessHelper.openSettings(getApplication())
    }

    fun setBadgeEnabled(enabled: Boolean) {
        viewModelScope.launch { badgePreferencesRepository.setEnabled(enabled) }
    }

    fun setMarkAsReadOnDismiss(value: Boolean) {
        viewModelScope.launch { badgePreferencesRepository.setMarkAsReadOnDismiss(value) }
    }

    fun setHideMessageText(value: Boolean) {
        viewModelScope.launch { badgePreferencesRepository.setHideMessageText(value) }
    }

    fun setBadgeScope(scope: BadgeScope) {
        viewModelScope.launch { badgePreferencesRepository.setScope(scope) }
    }

    fun setIncludeSilent(value: Boolean) {
        viewModelScope.launch { badgePreferencesRepository.setIncludeSilent(value) }
    }

    fun unmutePackage(packageName: String) {
        viewModelScope.launch { badgePreferencesRepository.unmutePackage(packageName) }
    }

    fun resetBadgePreferences() {
        viewModelScope.launch { badgePreferencesRepository.resetToDefaults() }
    }

    val weatherData: StateFlow<WeatherData> = weatherRepository.cachedWeather
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeatherData())

    fun hasLocationPermission(): Boolean = weatherRepository.hasLocationPermission()

    fun refreshWeather(force: Boolean = true) {
        viewModelScope.launch {
            weatherRepository.getOrFetchWeather(forceRefresh = force)
        }
    }

    val triggerConfig: StateFlow<TriggerConfig> = triggerRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, TriggerConfig())

    val headerConfig: StateFlow<HeaderConfig> = headerPreferencesRepository.headerConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, HeaderConfig())

    val homeWidgetSlot: StateFlow<WidgetSlotEntity?> = database.widgetSlotDao()
        .observeAll()
        .map { slots -> slots.firstOrNull { it.slotId == HOME_WIDGET_SLOT_ID } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val quickActionConfig: StateFlow<QuickActionConfig> = quickActionRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, QuickActionConfig())

    val allInstalledApps: StateFlow<List<AppInfo>> = appRepository.apps

    val quickActionTargetApp: StateFlow<AppInfo?> =
        combine(quickActionConfig, allInstalledApps) { config, apps ->
            if (config.actionType == QuickActionType.APP && config.componentKey.isNotEmpty()) {
                apps.find { it.componentKey == config.componentKey }
            } else null
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val hiddenKeysFlow: Flow<Set<String>> =
        database.hiddenAppDao().observeAll()
            .map { rows -> rows.mapTo(HashSet(rows.size)) { it.componentKey } }

    val hiddenApps: StateFlow<List<HiddenAppUi>> =
        combine(appRepository.apps, hiddenKeysFlow) { apps, hidden ->
            apps.filter { it.componentKey in hidden }
                .map { HiddenAppUi(it.componentKey, it.label) }
                .sortedBy { it.label.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    private val _isQuickActionPickerOpen = MutableStateFlow(false)
    val isQuickActionPickerOpen: StateFlow<Boolean> = _isQuickActionPickerOpen.asStateFlow()

    init {
        refreshDefaultLauncherState()
        viewModelScope.launch {
            database.widgetSlotDao().ensureDefaults(DEFAULT_WIDGET_SLOTS)
        }
    }

    fun refreshDefaultLauncherState() {
        val app = getApplication<Application>()
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = app.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = app.packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == app.packageName
        }
        _isDefaultLauncher.value = isDefault
    }

    fun buildSetDefaultIntent(): Intent {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = app.getSystemService(RoleManager::class.java)
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                ?: Intent(Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }

    fun setHeaderMode(mode: HeaderMode) {
        viewModelScope.launch { headerPreferencesRepository.setHeaderMode(mode) }
    }

    fun setHeaderUseFahrenheit(useFahrenheit: Boolean) {
        viewModelScope.launch { headerPreferencesRepository.setUseFahrenheit(useFahrenheit) }
    }

    fun setHeaderSize(size: HeaderSize) {
        viewModelScope.launch { headerPreferencesRepository.setHeaderSize(size) }
    }

    fun restoreDefaultHomeWidget() {
        viewModelScope.launch {
            database.widgetSlotDao().clearWidget(HOME_WIDGET_SLOT_ID)
        }
    }

    fun setTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch { triggerRepository.setEnabled(enabled) }
    }

    fun setTriggerEdgeSide(edgeSide: TriggerEdgeSide) {
        viewModelScope.launch { triggerRepository.setEdgeSide(edgeSide) }
    }

    fun setTriggerMode(mode: TriggerMode) {
        viewModelScope.launch { triggerRepository.setMode(mode) }
    }

    fun setTriggerVerticalBias(bias: Float) {
        viewModelScope.launch { triggerRepository.setVerticalBias(bias) }
    }

    fun setTriggerHeightDp(heightDp: Int) {
        viewModelScope.launch { triggerRepository.setHeightDp(heightDp) }
    }

    fun setTriggerWidthDp(widthDp: Int) {
        viewModelScope.launch { triggerRepository.setWidthDp(widthDp) }
    }

    fun setTriggerOpacity(opacity: Float) {
        viewModelScope.launch { triggerRepository.setOpacity(opacity) }
    }

    fun setTriggerWidgetCount(count: Int) {
        viewModelScope.launch {
            database.widgetSlotDao().ensureDefaults(DEFAULT_WIDGET_SLOTS)
            triggerRepository.setWidgetCount(count)
        }
    }

    fun setQuickActionEnabled(enabled: Boolean) {
        viewModelScope.launch { quickActionRepository.setEnabled(enabled) }
    }

    fun openQuickActionPicker() {
        _isQuickActionPickerOpen.value = true
    }

    fun closeQuickActionPicker() {
        _isQuickActionPickerOpen.value = false
    }

    fun setQuickAction(type: QuickActionType, componentKey: String = "", label: String = "") {
        viewModelScope.launch {
            quickActionRepository.setAction(type, componentKey, label)
            _isQuickActionPickerOpen.value = false
        }
    }

    fun setQuickActionEdgeSide(edgeSide: TriggerEdgeSide) {
        viewModelScope.launch { quickActionRepository.setEdgeSide(edgeSide) }
    }

    fun setQuickActionVerticalBias(bias: Float) {
        viewModelScope.launch { quickActionRepository.setVerticalBias(bias) }
    }

    fun setQuickActionSizeDp(sizeDp: Int) {
        viewModelScope.launch { quickActionRepository.setSizeDp(sizeDp) }
    }

    fun setQuickActionOpacity(opacity: Float) {
        viewModelScope.launch { quickActionRepository.setOpacity(opacity) }
    }

    fun resetQuickActionToDefaults() {
        viewModelScope.launch { quickActionRepository.resetToDefaults() }
    }

    fun unhideApp(componentKey: String) {
        viewModelScope.launch { database.hiddenAppDao().delete(componentKey) }
    }
}
