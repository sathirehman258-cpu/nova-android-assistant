package com.nova.assistant.domain.tools

import com.nova.assistant.domain.model.RiskLevel

/**
 * Declarative definition of one callable tool. This is the single source of truth for:
 *  - what arguments a tool accepts (strict schema; unknown args are rejected)
 *  - what Android permission(s) it needs
 *  - whether it requires user confirmation before running
 *  - how long it's allowed to run before the executor times it out
 *
 * The LLM only ever sees tool NAMES and argument SCHEMAS (via PromptManager) — it never
 * receives a path to execute arbitrary code, and any tool name it emits that isn't in this
 * registry is rejected before it ever reaches the Android Action Executor.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val requiredArgs: List<String>,
    val optionalArgs: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val risk: RiskLevel,
    val timeoutMs: Long = 8_000L
)

object ToolRegistry {

    val tools: Map<String, ToolDefinition> = listOf(
        ToolDefinition("open_app", "Launch an installed app by name", listOf("app_name"), risk = RiskLevel.LOW),
        ToolDefinition("close_app", "Stop/back out of an app", listOf("app_name"), risk = RiskLevel.LOW),
        ToolDefinition("search_web", "Search the web for a query", listOf("query"), risk = RiskLevel.LOW),
        ToolDefinition(
            "make_phone_call", "Place a phone call to a resolved contact", listOf("contact"),
            requiredPermissions = listOf("android.permission.CALL_PHONE"), risk = RiskLevel.HIGH
        ),
        ToolDefinition(
            "send_sms", "Send an SMS to a contact", listOf("contact", "message"),
            requiredPermissions = listOf("android.permission.SEND_SMS"), risk = RiskLevel.MEDIUM
        ),
        ToolDefinition("open_whatsapp_chat", "Open a WhatsApp chat with a contact", listOf("contact"), risk = RiskLevel.LOW),
        ToolDefinition(
            "send_whatsapp_message", "Type and send a WhatsApp message via visible UI automation",
            listOf("contact", "message"), risk = RiskLevel.MEDIUM
        ),
        ToolDefinition(
            "read_notifications", "Read back recent notifications", emptyList(),
            requiredPermissions = listOf("notification_listener"), risk = RiskLevel.LOW
        ),
        ToolDefinition("open_settings", "Open a specific settings page", listOf("page"), risk = RiskLevel.LOW),
        ToolDefinition("toggle_wifi", "Open Wi-Fi settings shortcut / toggle where allowed", emptyList(), risk = RiskLevel.LOW),
        ToolDefinition("toggle_bluetooth", "Open Bluetooth settings shortcut / toggle where allowed", emptyList(), risk = RiskLevel.LOW),
        ToolDefinition("set_brightness", "Set screen brightness", listOf("value"), risk = RiskLevel.LOW),
        ToolDefinition("set_volume", "Set volume for a stream", listOf("stream", "value"), risk = RiskLevel.LOW),
        ToolDefinition("turn_flashlight", "Turn flashlight on/off", listOf("on"), risk = RiskLevel.LOW),
        ToolDefinition("open_camera", "Open the camera app", emptyList(), risk = RiskLevel.LOW),
        ToolDefinition("open_maps", "Open Maps to a destination", listOf("destination"), risk = RiskLevel.LOW),
        ToolDefinition("start_navigation", "Start turn-by-turn navigation", listOf("destination"), risk = RiskLevel.LOW),
        ToolDefinition(
            "tap", "Tap a screen coordinate (fallback only — prefer tapElement)", listOf("x", "y"),
            requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.MEDIUM
        ),
        ToolDefinition(
            "swipe", "Swipe gesture between two points", listOf("x1", "y1", "x2", "y2", "duration"),
            requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW
        ),
        ToolDefinition(
            "type_text", "Type text into the currently focused field", listOf("text"),
            requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.MEDIUM
        ),
        ToolDefinition("press_back", "Press system back", emptyList(), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW),
        ToolDefinition("press_home", "Press system home", emptyList(), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW),
        ToolDefinition("scroll", "Scroll in a direction", listOf("direction"), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW),
        ToolDefinition("read_screen", "Read back a structured summary of visible screen content", emptyList(), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW),
        ToolDefinition("take_screenshot", "Take a screenshot where supported", emptyList(), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.MEDIUM),
        ToolDefinition("get_current_app", "Return the foreground app's package/activity", emptyList(), requiredPermissions = listOf("accessibility_service"), risk = RiskLevel.LOW),
        ToolDefinition("create_reminder", "Create a reminder", listOf("title", "time"), risk = RiskLevel.MEDIUM),
        ToolDefinition("create_alarm", "Create an alarm", listOf("time"), requiredPermissions = listOf("android.permission.SCHEDULE_EXACT_ALARM"), risk = RiskLevel.MEDIUM),
        ToolDefinition("get_weather", "Get weather for a location", listOf("location"), risk = RiskLevel.LOW),
        ToolDefinition("answer_question", "Answer a general knowledge question conversationally", listOf("question"), risk = RiskLevel.LOW)
    ).associateBy { it.name }

    fun get(name: String): ToolDefinition? = tools[name]

    /** Validates a raw (tool name, args) pair from the LLM against the strict schema above. */
    fun validate(name: String, args: Map<String, String>): ValidationResult {
        val def = tools[name] ?: return ValidationResult.Rejected("Unknown tool '$name' — not in allowlist")
        val missing = def.requiredArgs.filter { it !in args.keys }
        if (missing.isNotEmpty()) {
            return ValidationResult.Rejected("Missing required args for '$name': $missing")
        }
        val allowedKeys = (def.requiredArgs + def.optionalArgs).toSet()
        val unknown = args.keys.filterNot { it in allowedKeys }
        if (unknown.isNotEmpty()) {
            return ValidationResult.Rejected("Unknown args for '$name': $unknown")
        }
        return ValidationResult.Valid(def)
    }

    sealed class ValidationResult {
        data class Valid(val definition: ToolDefinition) : ValidationResult()
        data class Rejected(val reason: String) : ValidationResult()
    }
}
