package com.nova.assistant.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AutomationRule(
    val id: String,
    val description: String,
    val triggerType: TriggerType,
    val actionSummary: String
)

enum class TriggerType { TIME_DAILY, TIME_ONCE, EVENT_BASED }

/**
 * Minimal automation runner: time-based rules go through WorkManager (survives reboots via
 * its own persistence); event-based triggers (Bluetooth connect, geofence arrival) need a
 * platform-specific receiver wired in separately and are represented here only as metadata.
 * Deliberately caps rule count to avoid runaway/looping automations.
 */
@Singleton
class AutomationManager @Inject constructor(
    private val context: Context
) {
    private val maxRules = 20
    private val rules = mutableListOf<AutomationRule>()

    fun scheduleDailyReminder(id: String, hour: Int, minute: Int, description: String): Boolean {
        if (rules.size >= maxRules) return false

        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.Data.Builder().putString("description", description).build())
            .setConstraints(Constraints.NONE)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            id, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request
        )
        rules.add(AutomationRule(id, description, TriggerType.TIME_DAILY, "Daily reminder"))
        return true
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(id)
        rules.removeAll { it.id == id }
    }

    fun list(): List<AutomationRule> = rules.toList()
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // TODO: surface a local notification / trigger AssistantBrain to speak the reminder
        // when the app is foregrounded. Kept minimal here to avoid duplicating notification
        // channel setup already shown in VoiceForegroundService.
        return Result.success()
    }
}
