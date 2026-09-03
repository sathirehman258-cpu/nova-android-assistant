package com.nova.assistant.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WakeWordSettings(
    val enabled: Boolean = true,
    val phrase: String = "Hey Nova",
    val sensitivity: Float = 0.5f, // 0..1
    val batterySavingMode: Boolean = false
)

/**
 * Abstraction over a dedicated LOW-POWER wake-word engine (e.g. Porcupine, Vosk keyword
 * spotting, or an OEM hardware trigger). Deliberately does NOT use Android's SpeechRecognizer
 * for always-on detection — that API is not designed for continuous listening and may stream
 * audio off-device, which is wrong for a background always-listening use case.
 *
 * Wire a real engine's SDK into WakeWordEngineImpl.start()/stop(); this class only defines
 * the contract + settings the rest of the app depends on.
 */
interface WakeWordEngine {
    fun start(settings: WakeWordSettings)
    fun stop()
    val detections: Flow<Unit>
}

@Singleton
class WakeWordManager @Inject constructor() : WakeWordEngine {

    private val _detections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val detections: Flow<Unit> = _detections

    private var running = false
    private var currentSettings = WakeWordSettings()

    override fun start(settings: WakeWordSettings) {
        currentSettings = settings
        if (!settings.enabled) {
            stop()
            return
        }
        // TODO: initialize the chosen on-device wake-word SDK here, configured with
        // settings.phrase and settings.sensitivity, and call onWakeWordDetected() from its
        // callback. Left unimplemented deliberately — this requires bundling a specific
        // third-party model/SDK that the developer must choose and license.
        running = true
    }

    override fun stop() {
        running = false
        // TODO: release the underlying engine's resources.
    }

    fun isRunning(): Boolean = running

    /** Call this from the real engine's detection callback once integrated. */
    fun onWakeWordDetected() {
        if (running) _detections.tryEmit(Unit)
    }
}
