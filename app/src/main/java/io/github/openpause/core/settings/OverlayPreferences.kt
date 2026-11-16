package io.github.openpause.core.settings

import android.content.Context
import androidx.core.content.edit

/** Global overlay look settings, backed by SharedPreferences. */
object OverlayPreferences {
    private const val FILE = "open_pause_overlay"
    private const val KEY_COVER_BACKGROUND = "cover_background"

    /** When true the countdown hides the app behind it with an opaque backdrop. */
    fun coverBackground(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COVER_BACKGROUND, false)

    fun setCoverBackground(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_COVER_BACKGROUND, value) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
