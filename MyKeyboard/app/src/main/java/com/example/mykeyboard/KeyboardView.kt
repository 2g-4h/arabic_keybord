package com.example.mykeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class KeyData(val label: String, val code: Int, val weight: Float = 1f)

class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { LETTERS, SYMBOLS }
    enum class Lang { EN, AR }

    var mode = Mode.LETTERS
    var lang = Lang.EN
    var shift = false
    var action: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    var onKey: ((Int, String) -> Unit)? = null

    private var bgC = 0
    private var keyC = 0
    private var altC = 0
    private var textC = 0
    private var accentC = 0
    private var hintC = 0
    private var strokeC = 0
    private var prefHeightPx = 260

    private val handler = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var longPressRunnable: Runnable? = null
    private var downKey: KeyData? = null
    private var downRect = RectF()

    private val bgPaint = Paint()
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val altPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        loadPrefs()
    }

    fun loadPrefs() {
        val c = KeyboardPrefs.colors(context)
        bgC = c.bg; keyC = c.key; altC = c.alt; textC = c.text
        accentC = c.accent; hintC = c.hint; strokeC = c.stroke
        prefHeightPx = (KeyboardPrefs.height(context) * resources.displayMetrics.density).roundToInt()
        bgPaint.color = bgC
        keyPaint.color = keyC
        altPaint.color = altC
        accentPaint.color = accentC
        strokePaint.apply { color = strokeC; style = Paint.Style.STROKE; strokeWidth = 2f }
        textPaint.apply { color = textC; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        hintPaint.apply { color = hintC; textAlign = Paint.Align.CENTER }
    }

    private val enLetters = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m")
    )
    private val arLetters = listOf(
        listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج","د"),
        listOf("ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط"),
        listOf("ذ","ء","ؤ","ر","ى","ة","و","ز","ظ","د")
    )
    private val enSymbols = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","_","&","-","+","(",")","/"),
        listOf("~","`","|","•","√","π","÷","×","§","∆"),
        listOf("£","¢","€","¥","^","°","=","{","}","\\")
    )
    private val arSymbols = listOf(
        listOf("١","٢","٣","٤","٥","٦","٧","٨","٩","٠"),
        listOf("@","#","$","_","&","-","+","(",")","/"),
        listOf("~","`","|","•","√","π","÷","×","؟","،"),
        listOf("£","¢","€","¥","^","°","=","{","}","\\")
    )

    private val enDigitHints = listOf("1","2","3","4","5","6","7","8","9","0")
    private val arDigitHints = listOf("١","٢","٣","٤","٥","٦","٧","٨","٩","٠")
    private val enAltRow2 = mapOf(
        "a" to listOf("~","@","ª"), "s" to listOf("`","§"), "d" to listOf("|","•"),
        "f" to listOf("•","√"), "g" to listOf("√","π"), "h" to listOf("π","÷"),
        "j" to listOf("÷","×"), "k" to listOf("×","∆"), "l" to listOf("∆","£")
    )
    private val harakat = listOf("ً","ٌ","ٍ","َ","ُ","ِ","ّ","ْ","ٰ","ـ")

    private fun letterRows(): List<List<String>> = if (lang == Lang.EN) enLetters else arLetters
    private fun symbolRows(): List<List<String>> = if (lang == Lang.EN) enSymbols else arSymbols

    private fun bottomRow(): List<KeyData> {
        val modeLabel = when {
            mode == Mode.SYMBOLS && lang == Lang.AR -> "ABC"
            mode == Mode.SYMBOLS -> "أبجد"
            lang == Lang.AR -> "١٢٣"
            else -> "?123"
        }
        val langLabel = if (lang == Lang.EN) "ع" else "A"
        return listOf(
            KeyData(modeLabel, -2, 1.7f),
            KeyData("", -8, 1.1f),   // barcode
            KeyData(langLabel, -6, 1.1f),
            KeyData(" ", -3, 5.2f),
            KeyData(if (lang == Lang.AR) "،" else ".", if (lang == Lang.AR) '،'.code else '.'.code, 1.1f),
            KeyData("", -4, 1.7f)    // enter / action
        )
    }

    private fun rows(): List<List<KeyData>> = when (mode) {
        Mode.SYMBOLS -> symbolRows().map { row -> row.map { KeyData(it, it.codePointAt(0)) } } + listOf(bottomRow())
        Mode.LETTERS -> {
            val letters = letterRows().map { row -> row.map { KeyData(it, it.codePointAt(0)) } }
            val r2 = listOf(KeyData("⇧", -1, 1.5f)) + letters[1].map { KeyData(it, it.codePointAt(0)) }
            val r3 = listOf(KeyData("⇧", -1, 1.5f)) +
                    letters[2].map { KeyData(it, it.codePointAt(0)) } + listOf(KeyData("⌫", -5, 1.5f))
            listOf(letters[0], r2, r3, bottomRow())
        }
    }

    private val pad get() = 8f * resources.displayMetrics.density
    private val gap get() = 8f * resources.displayMetrics.density

    private fun rowHeight(): Float {
        val rs = rows()
        return (prefHeightPx - pad * 2 - gap * (rs.size - 1)) / rs.size
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec)
        else prefHeightPx
        setMeasuredDimension(w, h)
    }

    private fun displayLabel(k: KeyData): String = when (k.code) {
        -3 -> if (lang == Lang.AR) "العربية" else "English"
        -5 -> "⌫"
        -4 -> actionIcon()
        -1 -> "⇧"
        else -> {
            if (shift && lang == Lang.EN && k.label.length == 1 && k.label[0].isLetter())
                k.label.uppercase()
            else k.label
        }
    }

    private fun actionIcon(): String = when (action) {
        EditorInfo.IME_ACTION_SEARCH -> "⌕"
        EditorInfo.IME_ACTION_GO -> "➜"
        EditorInfo.IME_ACTION_SEND -> "➤"
        EditorInfo.IME_ACTION_NEXT -> "➡"
        EditorInfo.IME_ACTION_DONE -> "✓"
        else -> "↵"
    }

    private fun hintFor(k: KeyData): String? {
        if (mode != Mode.LETTERS || k.code < 0 || k.label.length != 1) return null
        val all = letterRows().flatten()
        val idx = all.indexOf(k.label)
        if (idx < 0 || idx > 9) return null
        return if (lang == Lang.EN) enDigitHints[idx] else arDigitHints[idx]
    }

    private fun alternatesFor(k: KeyData): List<String>? {
        if (mode != Mode.LETTERS) return null
        return when {
            k.code == '.'.code || k.label == "،" -> harakat
            lang == Lang.EN -> {
                val l = k.label.lowercase()
                when {
                    l in listOf("q","w","e","r","t","y","u","i","o","p") ->
                        listOf(enDigitHints[enLetters[0].indexOf(l)])
                    enAltRow2.containsKey(l) -> enAltRow2[l]!!
                    else -> null
                }
            }
            else -> when (k.label) {
                "ا" -> listOf("أ", "إ", "آ", "ٱ", "ء")
                "ى" -> listOf("ى", "ئ")
                "و" -> listOf("و", "ؤ")
                "ه" -> listOf("ه", "ة")
                "ة" -> listOf("ة", "ه")
                "ء" -> listOf("ء", "أ", "إ", "ؤ", "ئ")
                else -> {
                    val all = arLetters.flatten()
                    val idx = all.indexOf(k.label)
                    if (idx in 0..9) listOf(arDigitHints[idx]) else null
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val rs = rows()
        val rh = rowHeight()
        var y = pad
        for (row in rs) {
            var x = pad
            val totalW = width - pad * 2 - gap * (row.size - 1)
            val tw = row.sumOf { it.weight.toDouble() }.toFloat()
            for (k in row) {
                val kw = totalW * k.weight / tw
                val rect = RectF(x, y, x + kw, y + rh)
                if (k.code == -4) {
                    val r = min(kw, rh) / 2f - 6f
                    canvas.drawCircle(rect.centerX(), rect.centerY(), r, accentPaint)
                    textPaint.textSize = r * 1.1f
                    textPaint.color = Color.WHITE
                    val base = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                    canvas.drawText(actionIcon(), rect.centerX(), base, textPaint)
                    textPaint.color = textC
                } else {
                    val p = if (k.code < 0 || k.code == ' '.code) altPaint else keyPaint
                    canvas.drawRoundRect(rect, 14f, 14f, p)
                    canvas.drawRoundRect(rect, 14f, 14f, strokePaint)
                    if (k.code == -8) {
                        strokePaint.style = Paint.Style.FILL
                        strokePaint.color = textC
                        val cy = rect.centerY()
                        val bh = rh * 0.34f
                        for (i in 0..3) {
                            val bx = rect.centerX() + (i - 1.5f) * 14f * resources.displayMetrics.density
                            canvas.drawRect(bx - 3f, cy - bh / 2, bx + 3f, cy + bh / 2, strokePaint)
                        }
                        strokePaint.style = Paint.Style.STROKE
                        strokePaint.color = strokeC
                    } else {
                        val label = displayLabel(k)
                        if (k.code == -3) {
                            textPaint.textSize = rh * 0.30f
                            textPaint.color = hintC
                        } else {
                            textPaint.textSize = if (k.code < 0) rh * 0.40f else rh * 0.42f
                            textPaint.color = textC
                        }
                        val base = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                        canvas.drawText(label, rect.centerX(), base, textPaint)
                    }
                    hintFor(k)?.let { h ->
                        hintPaint.textSize = rh * 0.22f
                        canvas.drawText(h, rect.right - 14f, rect.top + hintPaint.textSize + 6f, hintPaint)
                    }
                }
                x += kw + gap
            }
            y += rh + gap
        }
    }

    private fun keyAt(x: Float, y: Float, outRect: RectF): KeyData? {
        val rs = rows()
        val rh = rowHeight()
        var yy = pad
        for (row in rs) {
            if (y >= yy && y <= yy + rh) {
                var xx = pad
                val totalW = width - pad * 2 - gap * (row.size - 1)
                val tw = row.sumOf { it.weight.toDouble() }.toFloat()
                for (k in row) {
                    val kw = totalW * k.weight / tw
                    if (x >= xx && x <= xx + kw) {
                        outRect.set(xx, yy, xx + kw, yy + rh)
                        return k
                    }
                    xx += kw + gap
                }
                return null
            }
            yy += rh + gap
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downKey = keyAt(event.x, event.y, downRect)
                val k = downKey
                if (k != null) {
                    val alts = alternatesFor(k)
                    if (!alts.isNullOrEmpty()) {
                        longPressRunnable = Runnable { showPopup(alts) }
                        handler.postDelayed(longPressRunnable!!, 360)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                if (popup?.isShowing == true) {
                    dismissPopup()
                } else {
                    downKey?.let { performKey(it) }
                }
                downKey = null
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                dismissPopup()
                downKey = null
            }
        }
        return true
    }

    private fun dismissPopup() {
        popup?.dismiss()
        popup = null
    }

    private fun showPopup(alts: List<String>) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(altC); gd.cornerRadius = 22f * resources.displayMetrics.density
            background = gd
            setPadding(18, 10, 18, 10)
        }
        for (a in alts) {
            val tv = TextView(context).apply {
                text = a; textSize = 21f; setTextColor(textC)
                setPadding(26, 18, 26, 18)
                setOnClickListener {
                    onKey?.invoke(a.codePointAt(0), a)
                    dismissPopup()
                }
            }
            content.addView(tv)
        }
        val pw = PopupWindow(
            content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
        )
        pw.isOutsideTouchable = true
        pw.elevation = 16f
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        popup = pw
        val x = (downRect.centerX() - content.measuredWidth / 2f)
            .roundToInt().coerceIn(0, (width - content.measuredWidth).coerceAtLeast(0))
        val yTop = (downRect.top - content.measuredHeight - 14f).roundToInt().coerceAtLeast(8)
        pw.showAtLocation(this, Gravity.NO_GRAVITY, x, yTop)
    }

    private fun performKey(k: KeyData) {
        if (KeyboardPrefs.vibrate(context)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (KeyboardPrefs.sound(context)) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)
                ?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        }
        when (k.code) {
            -1 -> { shift = !shift; invalidate() }
            -2 -> { mode = if (mode == Mode.SYMBOLS) Mode.LETTERS else Mode.SYMBOLS; invalidate() }
            -6 -> { lang = if (lang == Lang.EN) Lang.AR else Lang.EN; shift = false; invalidate() }
            else -> {
                var label = k.label
                if (shift && lang == Lang.EN && label.length == 1 && label[0].isLetter()) {
                    label = label.uppercase()
                }
                onKey?.invoke(k.code, label)
                if (shift && k.code >= 0 && label.length == 1 && label[0].isLetter()) {
                    shift = false; invalidate()
                }
            }
        }
    }
}
