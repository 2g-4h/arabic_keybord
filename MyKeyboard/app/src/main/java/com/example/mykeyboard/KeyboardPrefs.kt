package com.example.mykeyboard

import android.content.Context
import android.graphics.Color

object KeyboardPrefs {
    const val PREF = "kb_prefs"
    const val THEME = "theme"
    const val HEIGHT = "height"
    const val VIBRATE = "vibrate"
    const val SOUND = "sound"

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1
    const val THEME_TEAL = 2

    fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun theme(c: Context) = prefs(c).getInt(THEME, THEME_LIGHT)
    fun height(c: Context) = prefs(c).getInt(HEIGHT, 260)
    fun vibrate(c: Context) = prefs(c).getBoolean(VIBRATE, true)
    fun sound(c: Context) = prefs(c).getBoolean(SOUND, false)

    class Colors(
        val bg: Int, val key: Int, val alt: Int,
        val text: Int, val accent: Int, val hint: Int, val stroke: Int
    )

    fun colors(c: Context): Colors = when (theme(c)) {
        THEME_DARK -> Colors(
            0xFF161618.toInt(), 0xFF2C2C2E.toInt(), 0xFF3A3A3C.toInt(),
            0xFFF2F2F7.toInt(), 0xFF6BB8AE.toInt(), 0xFF8E8E93.toInt(), 0xFF48484A.toInt()
        )
        THEME_TEAL -> Colors(
            0xFFE3EFEC.toInt(), Color.WHITE, 0xFFCBE2DD.toInt(),
            0xFF123B35.toInt(), 0xFF3E9B8F.toInt(), 0xFF7BA39C.toInt(), 0xFFAEC9C3.toInt()
        )
        else -> Colors(
            0xFFECECEC.toInt(), Color.WHITE, 0xFFD6D6D6.toInt(),
            0xFF1C1C1E.toInt(), 0xFF6BB8AE.toInt(), 0xFF9E9E9E.toInt(), 0xFFBDBDBD.toInt()
        )
    }
}

object BarcodeBus {
    @Volatile var commit: ((String) -> Unit)? = null
}
