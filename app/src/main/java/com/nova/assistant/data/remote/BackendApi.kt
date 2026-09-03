package com.nova.assistant.data.remote

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * All LLM calls go through YOUR backend — never directly from the APK to an LLM provider,
 * and no provider API key ever ships inside the app. The backend owns provider selection
 * (OpenAI-compatible / Gemini-compatible / local model), auth, and rate limiting.
 */
data class PlanRequest(
    val transcript: String,
    val detectedLanguage: String,
    val conversationHistory: List<ConversationTurn>,
    val screenContext: ScreenContextDto? = null,
    val userProfile: Map<String, String> = emptyMap()
)

data class ConversationTurn(val role: String, val content: String)

data class ScreenContextDto(
    val packageName: String,
    val activityName: String?,
    val elements: List<ScreenElementDto>
)

data class ScreenElementDto(
    val text: String?,
    val contentDescription: String?,
    val clickable: Boolean,
    val editable: Boolean
)

/** Structured, tool-calls-only response. The app never executes free-form code from this. */
data class PlanResponse(
    val intent: String,
    val conversationalReply: String?,
    val actions: List<ToolCallDto>
)

data class ToolCallDto(
    val tool: String,
    val arguments: Map<String, String> = emptyMap()
)

interface BackendApi {
    @Headers("Content-Type: application/json")
    @POST("v1/plan")
    suspend fun planActions(@Body request: PlanRequest): PlanResponse
}
