package com.ff.launcher.data

import android.graphics.Bitmap
import android.os.UserHandle

/**
 * A single launchable app on the device.
 *
 * @param componentKey Composite identity: "<ComponentName.flattenToString>#<user serial>".
 *   The serial (via [android.os.UserManager.getSerialNumberForUser]) is stable across
 *   reboots and makes the key unique when the same app is installed in both the personal
 *   and a work profile — LazyColumn keys and favorites persistence both depend on this.
 * @param icon Pre-decoded bitmap (size-capped, rasterized on [kotlinx.coroutines.Dispatchers.IO]),
 *   or null when the icon could not be loaded (a letter placeholder is rendered instead).
 * @param userHandle Owning user — work-profile apps are launched through
 *   [android.content.pm.LauncherApps.startMainActivity], never a bare [android.content.Intent].
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val componentKey: String,
    val icon: Bitmap?,
    val userHandle: UserHandle,
    val firstInstallTime: Long = 0L,
) {
    companion object {
        const val PROFILE_KEY_SEPARATOR = "#"
    }
}
