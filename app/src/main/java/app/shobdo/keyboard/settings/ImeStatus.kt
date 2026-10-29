package app.shobdo.keyboard.settings

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.shobdo.keyboard.BuildConfig
import app.shobdo.keyboard.ime.ShobdoIME

/**
 * Reads the two facts the onboarding screen needs.
 *
 * Android 14 stopped apps targeting SDK 34+ from reading the `enabled_input_methods` and
 * `default_input_method` secure settings, so the enabled list comes from
 * [InputMethodManager] and "is it the current keyboard" falls back to asking the service
 * itself.
 */
object ImeStatus {

    /** `package/class`, with the application id so a debug build checks itself. */
    val componentId: String
        get() = "${BuildConfig.APPLICATION_ID}/app.shobdo.keyboard.ime.ShobdoIME"

    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return fromSecureSettings(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return manager.enabledInputMethodList.any { info ->
                "${info.packageName}/${info.serviceName}" == componentId
            }
        }
        return fromSecureSettings(context)
    }

    fun isSelected(context: Context): Boolean {
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()
        if (!current.isNullOrBlank()) return current.trim() == componentId
        // The setting is not readable on this version; the service knows whether the system
        // has bound it as the active keyboard.
        return ShobdoIME.isRunning
    }

    private fun fromSecureSettings(context: Context): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        enabled.split(':').any { it.trim() == componentId }
    }.getOrDefault(false)

    fun showPicker(context: Context) {
        val manager = context.getSystemService(InputMethodManager::class.java)
        manager?.showInputMethodPicker()
    }
}
