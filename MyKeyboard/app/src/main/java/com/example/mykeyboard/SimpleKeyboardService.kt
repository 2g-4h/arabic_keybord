package com.example.mykeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class SimpleKeyboardService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionsRow: LinearLayout
    private var lastPrefix = ""

    override fun onCreate() {
        super.onCreate()
        BarcodeBus.commit = { text ->
            currentInputConnection?.commitText(text, 1)
            updateSuggestions()
        }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_main, null)
        suggestionsRow = view.findViewById(R.id.suggestionsRow)
        keyboardView = view.findViewById(R.id.keyboardView)
        keyboardView.onKey = { code, label -> handleKey(code, label) }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.loadPrefs()
        keyboardView.shift = false
        keyboardView.action = info?.let { it.imeOptions and EditorInfo.IME_MASK_ACTION }
            ?: EditorInfo.IME_ACTION_UNSPECIFIED
        keyboardView.invalidate()
        updateSuggestions()
    }

    private fun handleKey(code: Int, label: String) {
        val ic = currentInputConnection ?: return
        when (code) {
            -3 -> { ic.commitText(" ", 1); updateSuggestions() }
            -5 -> { ic.deleteSurroundingText(1, 0); updateSuggestions() }
            -4 -> {
                val ei = currentInputEditorInfo
                val a = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0
                val noAction = (ei?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) ?: 0) != 0
                if (a != 0 && a != EditorInfo.IME_ACTION_NONE && !noAction) ic.performEditorAction(a)
                else ic.commitText("\n", 1)
            }
            -6 -> updateSuggestions()
            -2 -> updateSuggestions()
            -8 -> {
                val i = Intent(this, BarcodeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(i) }
            }
            -1 -> Unit
            else -> {
                ic.commitText(label, 1)
                updateSuggestions()
            }
        }
    }

    private fun updateSuggestions() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val matches = Regex("[A-Za-z\u0621-\u064a]+").findAll(before).toList()
        val prefix = matches.lastOrNull()?.value ?: ""
        lastPrefix = prefix

        val dict = if (keyboardView.lang == KeyboardView.Lang.AR) Dictionaries.AR else Dictionaries.EN
        val lower = prefix.lowercase()
        val words = if (prefix.isEmpty()) emptyList()
        else dict.filter { it.startsWith(lower, ignoreCase = true) && it.length > prefix.length }
            .sortedBy { it.length }.take(3)

        val punct = if (keyboardView.lang == KeyboardView.Lang.AR)
            listOf("،", ".", "؟") else listOf(".", ",", "?")

        suggestionsRow.removeAllViews()
        for (c in (words + punct).take(6)) {
            val tv = TextView(this).apply {
                text = c; textSize = 18f; setPadding(28, 12, 28, 12)
                setOnClickListener { commitSuggestion(c) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            suggestionsRow.addView(tv, lp)
        }
    }

    private fun commitSuggestion(candidate: String) {
        val ic = currentInputConnection ?: return
        if (lastPrefix.isNotEmpty()) ic.deleteSurroundingText(lastPrefix.length, 0)
        ic.commitText(candidate + " ", 1)
        lastPrefix = ""
        updateSuggestions()
    }
}
