package com.ff.launcher.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * In-memory app-scoped store holding eligible notification keys mapped per [BadgeKey].
 * Counts and message bodies are never written to disk or logged.
 */
@OptIn(FlowPreview::class)
object NotificationBadgeStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _rawKeys = MutableStateFlow<Map<BadgeKey, Set<String>>>(emptyMap())
    private val _isConnected = MutableStateFlow(false)

    @Volatile
    var includeSilent: Boolean = false

    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val badgeKeys: StateFlow<Map<BadgeKey, Set<String>>> = _rawKeys
        .debounce(200L)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        if (!connected) {
            _rawKeys.value = emptyMap()
        }
    }

    fun updateKeys(newMap: Map<BadgeKey, Set<String>>) {
        _rawKeys.value = newMap
    }

    fun hasNotifications(key: BadgeKey): Boolean {
        return !badgeKeys.value[key].isNullOrEmpty()
    }

    fun getNotificationCount(key: BadgeKey): Int {
        return badgeKeys.value[key]?.size ?: 0
    }
}
