package com.nova.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SttEvent {
    data class Partial(val text: String) : SttEvent()
    data class Final(val text: String) : SttEvent()
    data class Error(val code: Int, val message: String) : SttEvent()
    object SilenceTimeout : SttEvent()
}

/**
 * Wraps Android's SpeechRecognizer for a SINGLE listening session triggered by a wake-word hit
 * or a manual mic-button press. This is intentionally NOT used for always-on background
 * listening — see WakeWordManager for that, which uses a dedicated low-power engine instead.
 *
 * Each call to listen() creates a fresh SpeechRecognizer and destroys it when the session ends,
 * per Android's guidance against holding one open indefinitely.
 */
@Singleton
class SpeechRecognizerManager @Inject constructor(
    private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null
    private var retryCount = 0
    private val maxRetries = 1

    fun listen(languageTag: String = "bn-BD,en-US"): Flow<SttEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SttEvent.Error(-1, "Speech recognition not available on this device"))
            close()
            return@callbackFlow
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        retryCount < maxRetries
                    ) {
                        retryCount++
                        restart(languageTag)
                        return
                    }
                    trySend(SttEvent.Error(error, "STT error code $error"))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) trySend(SttEvent.Final(text))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) trySend(SttEvent.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        startListening(languageTag)

        awaitClose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun restart(languageTag: String) {
        recognizer?.cancel()
        startListening(languageTag)
    }

    private fun startListening(languageTag: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
        }
        recognizer?.startListening(intent)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
