package com.nova.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.isActive

data class VoiceSettings(
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.05f, // slightly higher for a warmer female timbre
    val preferFemale: Boolean = true,
    val autoSpeak: Boolean = true
)

/**
 * VoiceEngine
 *  ├── AndroidTTS        (always-available fallback, implemented here)
 *  ├── CloudTTS          (plug in a neural provider for a more natural voice; stub below)
 *  └── OptionalPremiumTTS (e.g. ElevenLabs/Azure Neural — configure via backend, never a
 *                          hardcoded key in the APK)
 *
 * Callers should always go through VoiceEngineRouter, which falls back to AndroidTTS
 * automatically if the preferred engine is unavailable or errors out.
 */
interface VoiceEngine {
    suspend fun speak(text: String, settings: VoiceSettings)
    fun stop()
    fun isSpeaking(): Boolean
}

@Singleton
class AndroidTtsVoiceEngine @Inject constructor(
    context: Context
) : VoiceEngine {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                selectBestFemaleVoice(preferBangla = false)
            }
        }
    }

    private fun selectBestFemaleVoice(preferBangla: Boolean) {
        val engine = tts ?: return
        val targetLocale = if (preferBangla) Locale("bn", "BD") else Locale.US
        val candidate: Voice? = engine.voices?.filter { voice ->
            voice.locale == targetLocale || voice.locale.language == targetLocale.language
        }?.firstOrNull { voice -> voice.name.contains("female", ignoreCase = true) }
            ?: engine.voices?.firstOrNull { it.locale.language == targetLocale.language }

        candidate?.let { engine.voice = it }
        engine.language = targetLocale
    }

    override suspend fun speak(text: String, settings: VoiceSettings) {
        val engine = tts ?: return
        if (!ready) return

        engine.setSpeechRate(settings.speakingRate)
        engine.setPitch(settings.pitch)

        val utteranceId = UUID.randomUUID().toString()
        suspendCoroutine<Unit> { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (cont.context.isActive) cont.resume(Unit)
                }
                override fun onError(utteranceId: String?) {
                    if (cont.context.isActive) cont.resume(Unit)
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun shutdown() {
        tts?.shutdown()
    }
}

/**
 * Stub for a cloud/premium neural voice provider. Wire this to your backend's TTS proxy
 * (never call a provider directly with an embedded key). Falls back to AndroidTTS on failure.
 */
class CloudVoiceEngine @Inject constructor(
    private val fallback: AndroidTtsVoiceEngine
) : VoiceEngine {
    override suspend fun speak(text: String, settings: VoiceSettings) {
        try {
            // TODO: stream audio from backend TTS proxy endpoint and play it.
            throw NotImplementedError("Cloud TTS not yet configured — falling back.")
        } catch (e: Exception) {
            fallback.speak(text, settings)
        }
    }

    override fun stop() = fallback.stop()
    override fun isSpeaking(): Boolean = fallback.isSpeaking()
}
