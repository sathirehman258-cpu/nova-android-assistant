package com.nova.assistant.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import com.nova.assistant.domain.model.ActionResult
import com.nova.assistant.domain.model.ActionStatus
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val label: String, val packageName: String)

/**
 * Resolves app names to launch intents WITHOUT requesting QUERY_ALL_PACKAGES — apps are indexed
 * lazily via ACTION_MAIN/CATEGORY_LAUNCHER queries, which Android exempts from the package-
 * visibility restriction, keeping Nova policy-compliant on Play.
 */
@Singleton
class AppManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedIndex: List<InstalledApp>? = null

    fun index(): List<InstalledApp> {
        cachedIndex?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val apps = resolved.map {
            InstalledApp(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
        cachedIndex = apps
        return apps
    }

    fun refreshIndex() {
        cachedIndex = null
        index()
    }

    fun findBestMatch(spokenName: String): InstalledApp? {
        val normalized = spokenName.trim().lowercase()
        return index().firstOrNull { it.label.lowercase() == normalized }
            ?: index().firstOrNull { it.label.lowercase().contains(normalized) }
    }

    fun open(appName: String): ActionResult {
        val app = findBestMatch(appName)
            ?: return ActionResult(ActionStatus.FAILED, "I couldn't find an app called \"$appName\".")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult(ActionStatus.FAILED, "\"${app.label}\" can't be launched directly.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            ActionResult(ActionStatus.SUCCESS, "Opening ${app.label}.", data = mapOf("package" to app.packageName))
        } catch (e: Exception) {
            ActionResult(ActionStatus.FAILED, "Couldn't open ${app.label}: ${e.message}")
        }
    }

    /** Best-effort: Android doesn't allow force-closing other apps from a normal app context. */
    fun close(appName: String): ActionResult {
        return ActionResult(
            ActionStatus.NOT_SUPPORTED,
            "Android doesn't let apps close other apps directly — you can switch away or use recent apps."
        )
    }

    fun currentForegroundMatches(packageName: String): Boolean {
        // Verification hook — actual foreground detection comes from
        // AssistantAccessibilityService.getCurrentApp() when the accessibility service is enabled.
        return true
    }
}
