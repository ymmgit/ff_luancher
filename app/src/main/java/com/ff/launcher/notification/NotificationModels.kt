package com.ff.launcher.notification

import android.app.PendingIntent

/**
 * Composite key representing an installed package under a specific user profile.
 * Matches AppInfo user isolation.
 */
data class BadgeKey(
    val packageName: String,
    val userSerial: Long,
)

/**
 * Transient, in-memory representation of an eligible notification for the inline panel.
 * Never persisted to disk and never logged.
 */
data class NotificationCard(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long,
    val isDismissible: Boolean,
    val hasMarkReadAction: Boolean,
    val contentIntent: PendingIntent?,
)
