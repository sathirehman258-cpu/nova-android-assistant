package com.nova.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.ai.AssistantBrain
import com.nova.assistant.ai.BrainEvent
import com.nova.assistant.voice.AndroidTtsVoiceEngine
import com.nova.assistant.voice.SpeechRecognizerManager
import com.nova.assistant.voice.SttEvent
import com.nova.assistant.voice.VoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val statusText: String = "Tap the mic or say \u201cHey Nova\u201d",
    val transcript: String = "",
    val lastReply: String = "",
    val pendingConfirmationPrompt: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val brain: AssistantBrain,
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val voiceEngine: AndroidTtsVoiceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            brain.events.collect { event ->
                when (event) {
                    is BrainEvent.Listening -> _uiState.value = _uiState.value.copy(isListening = event.active)
                    is BrainEvent.Thinking -> _uiState.value = _uiState.value.copy(
                        isThinking = event.active,
                        statusText = if (event.active) "Thinking\u2026" else _uiState.value.statusText
                    )
                    is BrainEvent.StatusUpdate -> _uiState.value = _uiState.value.copy(statusText = event.text)
                    is BrainEvent.PendingConfirmation -> _uiState.value =
                        _uiState.value.copy(pendingConfirmationPrompt = event.prompt)
                    is BrainEvent.Spoken -> {
                        _uiState.value = _uiState.value.copy(
                            lastReply = event.text,
                            statusText = "Done",
                            pendingConfirmationPrompt = null
                        )
                        voiceEngine.speak(event.text, VoiceSettings())
                    }
                }
            }
        }
    }

    fun onMicPressed() {
        _uiState.value = _uiState.value.copy(isListening = true, statusText = "Listening\u2026")
        viewModelScope.launch {
            speechRecognizerManager.listen().collect { event ->
                when (event) {
                    is SttEvent.Partial -> _uiState.value = _uiState.value.copy(transcript = event.text)
                    is SttEvent.Final -> {
                        _uiState.value = _uiState.value.copy(transcript = event.text, isListening = false)
                        brain.handleTranscript(event.text)
                    }
                    is SttEvent.Error -> _uiState.value = _uiState.value.copy(
                        isListening = false,
                        statusText = "Didn't catch that \u2014 try again"
                    )
                    SttEvent.SilenceTimeout -> _uiState.value = _uiState.value.copy(isListening = false)
                }
            }
        }
    }

    fun onConfirm() {
        viewModelScope.launch { brain.confirmPending() }
    }

    fun onCancelConfirmation() {
        brain.cancelPending()
    }

    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { brain.handleTranscript(text) }
    }
}
