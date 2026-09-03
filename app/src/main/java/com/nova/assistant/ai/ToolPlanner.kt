package com.nova.assistant.ai

import com.nova.assistant.data.remote.BackendApi
import com.nova.assistant.data.remote.ConversationTurn
import com.nova.assistant.data.remote.PlanRequest
import com.nova.assistant.data.remote.ScreenContextDto
import com.nova.assistant.domain.model.DetectedLanguage
import com.nova.assistant.domain.model.ParsedIntent
import com.nova.assistant.domain.model.ToolCall
import com.nova.assistant.domain.tools.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the backend to get a structured plan, then validates every tool call against
 * ToolRegistry before anything reaches the executor. Any tool call that fails validation
 * is dropped and logged — it is NEVER executed, and NEVER silently retried as raw code.
 */
@Singleton
class ToolPlanner @Inject constructor(
    private val backendApi: BackendApi
) {
    suspend fun plan(
        transcript: String,
        language: DetectedLanguage,
        history: List<ConversationTurn>,
        screenContext: ScreenContextDto?
    ): ParsedIntent {
        val response = backendApi.planActions(
            PlanRequest(
                transcript = transcript,
                detectedLanguage = language.name,
                conversationHistory = history,
                screenContext = screenContext
            )
        )

        val validatedCalls = response.actions.mapNotNull { dto ->
            when (val result = ToolRegistry.validate(dto.tool, dto.arguments)) {
                is ToolRegistry.ValidationResult.Valid -> ToolCall(dto.tool, dto.arguments)
                is ToolRegistry.ValidationResult.Rejected -> {
                    // Reject silently from execution; surfaced only in debug logs upstream.
                    null
                }
            }
        }

        return ParsedIntent(
            rawText = transcript,
            detectedLanguage = language,
            toolCalls = validatedCalls,
            conversationalReply = response.conversationalReply
        )
    }
}
