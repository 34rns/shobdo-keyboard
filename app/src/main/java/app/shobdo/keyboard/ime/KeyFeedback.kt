package app.shobdo.keyboard.ime

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptics and the optional key click.
 *
 * The vibrator is fetched once. Strength comes from the setting: a light tap, a normal tap or
 * a firm tap, all of them short enough to feel like a key and not like an alarm. The click is
 * off by default, as a keyboard that makes noise without being asked is a nuisance.
 */
class KeyFeedback(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    var haptic: Boolean = true
    var strength: Int = 1
    var sound: Boolean = false

    fun key(isDelete: Boolean = false) {
        if (haptic) buzz()
        if (sound) {
            audioManager?.playSoundEffect(
                if (isDelete) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD,
                0.6f
            )
        }
    }

    /** The longer confirmation used when a long-press popup opens. */
    fun longPress() {
        if (haptic) buzz(1.4f)
    }

    private fun buzz(multiplier: Float = 1f) {
        val service = vibrator ?: return
        if (!service.hasVibrator()) return
        val duration = when (strength) {
            0 -> 8L
            2 -> 22L
            else -> 14L
        }
        val amplitude = when (strength) {
            0 -> 70
            2 -> 200
            else -> 130
        }
        runCatching {
            service.vibrate(
                VibrationEffect.createOneShot(
                    (duration * multiplier).toLong(),
                    amplitude.coerceIn(1, 255)
                )
            )
        }
    }
}
