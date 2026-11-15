package com.ff.launcher.data.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.badgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "badge_preferences")

enum class BadgeScope {
    ALL,
    PINNED_ONLY,
}

data class BadgePreferences(
    val enabled: Boolean = false,
    val markAsReadOnDismiss: Boolean = true,
    val hideMessageText: Boolean = false,
    val scope: BadgeScope = BadgeScope.ALL,
    val includeSilent: Boolean = false,
    val mutedPackages: Set<String> = emptySet(),
)

class BadgePreferencesRepository(private val context: Context) {

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("badge_enabled")
        val KEY_MARK_AS_READ = booleanPreferencesKey("badge_mark_as_read")
        val KEY_HIDE_MESSAGE_TEXT = booleanPreferencesKey("badge_hide_message_text")
        val KEY_SCOPE = stringPreferencesKey("badge_scope")
        val KEY_INCLUDE_SILENT = booleanPreferencesKey("badge_include_silent")
        val KEY_MUTED_PACKAGES = stringSetPreferencesKey("badge_muted_packages")
    }

    val preferences: Flow<BadgePreferences> = context.badgeDataStore.data.map { prefs ->
        val enabled = prefs[KEY_ENABLED] ?: false
        val markAsRead = prefs[KEY_MARK_AS_READ] ?: true
        val hideText = prefs[KEY_HIDE_MESSAGE_TEXT] ?: false
        val scopeStr = prefs[KEY_SCOPE] ?: BadgeScope.ALL.name
        val scope = runCatching { BadgeScope.valueOf(scopeStr) }.getOrDefault(BadgeScope.ALL)
        val includeSilent = prefs[KEY_INCLUDE_SILENT] ?: false
        val muted = prefs[KEY_MUTED_PACKAGES] ?: emptySet()

        BadgePreferences(
            enabled = enabled,
            markAsReadOnDismiss = markAsRead,
            hideMessageText = hideText,
            scope = scope,
            includeSilent = includeSilent,
            mutedPackages = muted,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    suspend fun setMarkAsReadOnDismiss(value: Boolean) {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_MARK_AS_READ] = value
        }
    }

    suspend fun setHideMessageText(value: Boolean) {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_HIDE_MESSAGE_TEXT] = value
        }
    }

    suspend fun setScope(scope: BadgeScope) {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_SCOPE] = scope.name
        }
    }

    suspend fun setIncludeSilent(value: Boolean) {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_INCLUDE_SILENT] = value
        }
    }

    suspend fun mutePackage(packageName: String) {
        context.badgeDataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.add(packageName)
            prefs[KEY_MUTED_PACKAGES] = current
        }
    }

    suspend fun unmutePackage(packageName: String) {
        context.badgeDataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.remove(packageName)
            prefs[KEY_MUTED_PACKAGES] = current
        }
    }

    suspend fun toggleMutePackage(packageName: String) {
        context.badgeDataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[KEY_MUTED_PACKAGES] = current
        }
    }

    suspend fun resetToDefaults() {
        context.badgeDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            prefs[KEY_MARK_AS_READ] = true
            prefs[KEY_HIDE_MESSAGE_TEXT] = false
            prefs[KEY_SCOPE] = BadgeScope.ALL.name
            prefs[KEY_INCLUDE_SILENT] = false
            prefs[KEY_MUTED_PACKAGES] = emptySet()
        }
    }
}
