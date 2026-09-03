package com.nova.assistant.domain.usecase

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.nova.assistant.domain.model.ActionResult
import com.nova.assistant.domain.model.ActionStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceControls @Inject constructor(
    private val context: Context
) {
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun setFlashlight(on: Boolean): ActionResult = try {
        val cameraId = cameraManager.cameraIdList.firstOrNull()
            ?: return ActionResult(ActionStatus.NOT_SUPPORTED, "No flashlight found on this device.")
        cameraManager.setTorchMode(cameraId, on)
        ActionResult(ActionStatus.SUCCESS, if (on) "Flashlight on." else "Flashlight off.")
    } catch (e: Exception) {
        ActionResult(ActionStatus.FAILED, "Couldn't control the flashlight: ${e.message}")
    }

    fun setVolume(stream: String, percent: Int): ActionResult {
        val streamType = when (stream.lowercase()) {
            "music", "media" -> AudioManager.STREAM_MUSIC
            "ring", "ringer" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val max = audioManager.getStreamMaxVolume(streamType)
        val target = (percent.coerceIn(0, 100) / 100f * max).toInt()
        return try {
            audioManager.setStreamVolume(streamType, target, 0)
            ActionResult(ActionStatus.SUCCESS, "Volume set to $percent percent.")
        } catch (e: SecurityException) {
            ActionResult(ActionStatus.REQUIRES_PERMISSION, "Nova needs Do Not Disturb access to change that stream.")
        }
    }

    /**
     * Screen brightness changes require WRITE_SETTINGS on many OEMs; where that's not granted,
     * Nova should route the user to the settings shortcut instead of failing silently.
     */
    fun setBrightness(percent: Int): ActionResult {
        return if (android.provider.Settings.System.canWrite(context)) {
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                (percent.coerceIn(0, 100) / 100f * 255).toInt()
            )
            ActionResult(ActionStatus.SUCCESS, "Brightness set to $percent percent.")
        } else {
            ActionResult(
                ActionStatus.REQUIRES_PERMISSION,
                "Nova needs the 'Modify system settings' permission to change brightness directly.",
                permission = android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
        }
    }
}
