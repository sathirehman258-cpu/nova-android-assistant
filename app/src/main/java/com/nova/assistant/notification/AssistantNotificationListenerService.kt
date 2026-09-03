package com.nova.assistant.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class NovaNotification(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postedAt: Long
)

/**
 * Requires the user to explicitly grant Notification Access in Android Settings — Nova explains
 * why before sending them there (see PermissionCenter/first-run flow) and never assumes it's on.
 *
 * Notification contents stay local: they populate an in-memory/local history used to answer
 * things like "read my notifications" and are NEVER forwarded to the backend/LLM wholesale —
 * only a user-requested summary is ever included in a request, and only for that turn.
 */
class AssistantNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val MAX_HISTORY = 50
        private val _history = MutableStateFlow<List<NovaNotification>>(emptyList())
        val history = _history.asStateFlow()

        private val buffer = CopyOnWriteArrayList<NovaNotification>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val note = NovaNotification(
            packageName = sbn.packageName,
            appLabel = appLabelFor(sbn.packageName),
            title = extras.getCharSequence("android.title")?.toString(),
            text = extras.getCharSequence("android.text")?.toString(),
            postedAt = sbn.postTime
        )
        buffer.add(0, note)
        while (buffer.size > MAX_HISTORY) buffer.removeAt(buffer.size - 1)
        _history.value = buffer.toList()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op: we keep local history independent of live removal for "what did I get earlier" queries.
    }

    /** Only called on an explicit user request (e.g. "dismiss that notification"). */
    fun dismiss(key: String) {
        cancelNotification(key)
    }

    private fun appLabelFor(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
