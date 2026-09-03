package com.nova.assistant.permissions

import android.Manifest
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

enum class NovaCapability(val explanation: String) {
    MICROPHONE("Nova needs your microphone to hear voice commands."),
    NOTIFICATIONS_POST("Nova needs this to show its listening/status notification."),
    CONTACTS("Nova needs this to match names you say (like \"Rahim\") to real contacts."),
    CALL_PHONE("Nova needs this to place calls when you ask it to."),
    SMS("Nova needs this to send text messages on your behalf."),
    LOCATION("Nova needs this only for location-based commands like navigation."),
    CAMERA("Nova needs this to open the camera when you ask."),
    ACCESSIBILITY("Nova needs Accessibility access to see on-screen buttons and control visible apps for you. This is optional — Nova still works for basic commands without it."),
    NOTIFICATION_LISTENER("Nova needs Notification Access to read back your notifications when you ask. This is optional.")
}

/**
 * Requests permissions ONE AT A TIME, only when a feature that needs them is first used —
 * never all at first launch. Accessibility and Notification Access are special-case system
 * settings screens (not runtime permission dialogs), so PermissionCenter always explains why
 * BEFORE sending the user there.
 */
@Singleton
class PermissionCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isGranted(manifestPermission: String): Boolean =
        ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationListenerSettingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    fun manifestPermissionFor(capability: NovaCapability): String? = when (capability) {
        NovaCapability.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        NovaCapability.NOTIFICATIONS_POST -> Manifest.permission.POST_NOTIFICATIONS
        NovaCapability.CONTACTS -> Manifest.permission.READ_CONTACTS
        NovaCapability.CALL_PHONE -> Manifest.permission.CALL_PHONE
        NovaCapability.SMS -> Manifest.permission.SEND_SMS
        NovaCapability.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        NovaCapability.CAMERA -> Manifest.permission.CAMERA
        NovaCapability.ACCESSIBILITY, NovaCapability.NOTIFICATION_LISTENER -> null // special settings screens
    }
}
