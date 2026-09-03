package com.nova.assistant.domain.model

/**
 * Structured status every tool execution must resolve to.
 * The assistant must never say "Done" unless SUCCESS is returned by verification.
 */
enum class ActionStatus {
    SUCCESS,
    FAILED,
    REQUIRES_PERMISSION,
    REQUIRES_CONFIRMATION,
    NOT_SUPPORTED,
    TIMEOUT
}

data class ActionResult(
    val status: ActionStatus,
    val message: String,
    val permission: String? = null,
    val data: Map<String, String> = emptyMap()
)

/**
 * Risk tiers drive the confirmation system (ActionRiskManager).
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * A single tool invocation as decided by the ToolPlanner (from the LLM's structured output).
 * The LLM NEVER executes anything directly — it only ever emits ToolCall objects which are
 * validated against ToolRegistry before the Android Action Executor runs them.
 */
data class ToolCall(
    val tool: String,
    val arguments: Map<String, String> = emptyMap()
)

data class ParsedIntent(
    val rawText: String,
    val detectedLanguage: DetectedLanguage,
    val toolCalls: List<ToolCall>,
    val conversationalReply: String? = null
)

enum class DetectedLanguage {
    BANGLA,
    ENGLISH,
    BANGLISH,
    MIXED,
    UNKNOWN
}
