package com.example.mykeyboard

import android.app.Activity
import android.os.Bundle
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val p = KeyboardPrefs.prefs(this)

        val rg = findViewById<RadioGroup>(R.id.rgTheme)
        when (KeyboardPrefs.theme(this)) {
            KeyboardPrefs.THEME_DARK -> rg.check(R.id.rbDark)
            KeyboardPrefs.THEME_TEAL -> rg.check(R.id.rbTeal)
            else -> rg.check(R.id.rbLight)
        }
        rg.setOnCheckedChangeListener { _, id ->
            val v = when (id) {
                R.id.rbDark -> KeyboardPrefs.THEME_DARK
                R.id.rbTeal -> KeyboardPrefs.THEME_TEAL
                else -> KeyboardPrefs.THEME_LIGHT
            }
            p.edit().putInt(KeyboardPrefs.THEME, v).apply()
        }

        val sb = findViewById<SeekBar>(R.id.sbHeight)
        val tv = findViewById<TextView>(R.id.tvHeight)
        sb.progress = KeyboardPrefs.height(this)
        tv.text = "${getString(R.string.height_label)}: ${sb.progress}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tv.text = "${getString(R.string.height_label)}: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                p.edit().putInt(KeyboardPrefs.HEIGHT, seekBar?.progress ?: 260).apply()
            }
        })

        val cbV = findViewById<CheckBox>(R.id.cbVibrate)
        cbV.isChecked = KeyboardPrefs.vibrate(this)
        cbV.setOnCheckedChangeListener { _, b -> p.edit().putBoolean(KeyboardPrefs.VIBRATE, b).apply() }

        val cbS = findViewById<CheckBox>(R.id.cbSound)
        cbS.isChecked = KeyboardPrefs.sound(this)
        cbS.setOnCheckedChangeListener { _, b -> p.edit().putBoolean(KeyboardPrefs.SOUND, b).apply() }
    }
}
