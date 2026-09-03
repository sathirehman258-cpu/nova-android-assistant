package com.nova.assistant.ai

import com.nova.assistant.data.remote.ConversationTurn
import com.nova.assistant.domain.model.ActionResult
import com.nova.assistant.domain.model.ActionStatus
import com.nova.assistant.domain.model.DetectedLanguage
import com.nova.assistant.domain.model.ToolCall
import com.nova.assistant.domain.tools.ActionExecutor
import com.nova.assistant.voice.LanguageDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class BrainEvent {
    data class Listening(val active: Boolean) : BrainEvent()
    data class Thinking(val active: Boolean) : BrainEvent()
    data class StatusUpdate(val text: String) : BrainEvent()
    data class PendingConfirmation(val call: ToolCall, val prompt: String) : BrainEvent()
    data class Spoken(val text: String) : BrainEvent()
}

/**
 * Pipeline: Voice Input -> STT -> Language Detection -> Intent/Task Parser (ToolPlanner) ->
 * Safety + Permission Check (implicit in ActionExecutor) -> Tool/Action Planner ->
 * Android Action Executor -> Result Verification -> LLM Response Generator -> TTS
 *
 * This class holds no direct Android UI references; it's driven by the ViewModel layer.
 */
@Singleton
class AssistantBrain @Inject constructor(
    private val toolPlanner: ToolPlanner,
    private val languageDetector: LanguageDetector,
    private val actionExecutor: ActionExecutor
) {
    private val _events = MutableStateFlow<BrainEvent>(BrainEvent.Listening(false))
    val events: StateFlow<BrainEvent> = _events.asStateFlow()

    private val conversationHistory = mutableListOf<ConversationTurn>()
    private var pendingCall: ToolCall? = null

    suspend fun handleTranscript(transcript: String) {
        _events.value = BrainEvent.Thinking(true)

        val language = languageDetector.detect(transcript)
        val parsed = toolPlanner.plan(transcript, language, conversationHistory.toList(), screenContext = null)

        conversationHistory.add(ConversationTurn("user", transcript))
        parsed.conversationalReply?.let { conversationHistory.add(ConversationTurn("assistant", it)) }
        trimHistory()

        _events.value = BrainEvent.Thinking(false)

        if (parsed.toolCalls.isEmpty()) {
            val reply = parsed.conversationalReply ?: "I'm not sure how to help with that yet."
            speak(reply)
            return
        }

        for (call in parsed.toolCalls) {
            runCall(call, confirmed = false)
        }
    }

    /** Called when the user answers "yes" to a REQUIRES_CONFIRMATION prompt. */
    suspend fun confirmPending() {
        val call = pendingCall ?: return
        pendingCall = null
        runCall(call, confirmed = true)
    }

    fun cancelPending() {
        pendingCall = null
        speakSync("Okay, cancelled.")
    }

    private suspend fun runCall(call: ToolCall, confirmed: Boolean) {
        _events.value = BrainEvent.StatusUpdate(statusTextFor(call.tool))
        val result: ActionResult = actionExecutor.execute(call, confirmed)

        when (result.status) {
            ActionStatus.REQUIRES_CONFIRMATION -> {
                pendingCall = call
                _events.value = BrainEvent.PendingConfirmation(call, result.message)
                speak(result.message)
            }
            ActionStatus.SUCCESS -> speak(result.message.ifBlank { "Done." })
            ActionStatus.REQUIRES_PERMISSION -> speak(
                "I need permission to do that" + (result.permission?.let { " ($it)" } ?: "") + ". " + result.message
            )
            ActionStatus.NOT_SUPPORTED -> speak(result.message)
            ActionStatus.TIMEOUT -> speak(result.message)
            ActionStatus.FAILED -> speak(result.message)
        }
    }

    private fun statusTextFor(tool: String): String = when (tool) {
        "open_app" -> "Opening…"
        "send_whatsapp_message", "send_sms" -> "Preparing message…"
        "make_phone_call" -> "Calling…"
        "search_web" -> "Searching…"
        else -> "Working on it…"
    }

    private fun trimHistory() {
        val maxTurns = 20
        while (conversationHistory.size > maxTurns) conversationHistory.removeAt(0)
    }

    fun clearConversation() {
        conversationHistory.clear()
    }

    private suspend fun speak(text: String) {
        _events.value = BrainEvent.Spoken(text)
    }

    private fun speakSync(text: String) {
        _events.value = BrainEvent.Spoken(text)
    }
}
