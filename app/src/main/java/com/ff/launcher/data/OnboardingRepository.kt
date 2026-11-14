package com.ff.launcher.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

/**
 * First-run onboarding state plus default-home detection/requests.
 *
 * A launcher can never silently make itself the default home — the system must show its
 * own confirmation. On Android 10+ that is [RoleManager.ROLE_HOME] via
 * [RoleManager.createRequestRoleIntent] (a proper system dialog); pre-Q falls back to
 * the system's default-apps settings page, because starting CATEGORY_HOME with a default
 * already set would just launch the OLD launcher instead of showing a chooser.
 */
class OnboardingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.onboardingDataStore

    /** Emits true once the onboarding flow has been completed or skipped. */
    val isOnboardingDone: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }

    /** Synchronous default-home check — call from a background dispatcher or onResume. */
    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = appContext.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        // Pre-Q fallback. (Only reached on <Q, so package-visibility filtering of other
        // home apps — an Android 11+ concern — does not apply here.)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = appContext.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == appContext.packageName
    }

    /**
     * The intent that asks the system to make this launcher the default home. On Q+ it
     * is the role-request dialog; pre-Q it opens the system default-apps settings page.
     */
    fun createSetDefaultIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = appContext.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
    }
}
