package dev.paddeck.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.paddeck.input.Keysym

@SuppressLint("ViewConstructor")
class PadKeyboardView(
    context: Context,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onLatin(key: Char, shifted: Boolean)
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onSpecial(keysym: Int)
        fun onToggleLanguage()
    }

    sealed interface Key {
        data class Latin(val key: Char) : Key
        data object Space : Key
        data object Backspace : Key
        data object Enter : Key
        data object Tab : Key
        data object Escape : Key
        data object Shift : Key
        data object Ctrl : Key
        data object Alt : Key
        data object Command : Key
        data object Language : Key
        data class Arrow(val keysym: Int) : Key
        data class Symbol(val label: String, val latin: Char) : Key
    }

    var korean: Boolean = true
        private set
    private var shifted: Boolean = false
    private val buttons = mutableListOf<Pair<TextView, Key>>()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatAction: Runnable? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#15181D"))
        setPadding(dp(6), dp(6), dp(6), dp(10))
        buildRows()
        refreshLabels()
    }

    private fun buildRows() {
        addRow(
            listOf(
                Key.Symbol("1", '1'), Key.Symbol("2", '2'), Key.Symbol("3", '3'), Key.Symbol("4", '4'),
                Key.Symbol("5", '5'), Key.Symbol("6", '6'), Key.Symbol("7", '7'), Key.Symbol("8", '8'),
                Key.Symbol("9", '9'), Key.Symbol("0", '0'), Key.Symbol("-", '-'), Key.Symbol("=", '='),
                Key.Backspace,
            ),
        )
        addRow(
            listOf(
                Key.Tab,
                Key.Latin('q'), Key.Latin('w'), Key.Latin('e'), Key.Latin('r'), Key.Latin('t'),
                Key.Latin('y'), Key.Latin('u'), Key.Latin('i'), Key.Latin('o'), Key.Latin('p'),
                Key.Symbol("[", '['), Key.Symbol("]", ']'),
            ),
        )
        addRow(
            listOf(
                Key.Shift,
                Key.Latin('a'), Key.Latin('s'), Key.Latin('d'), Key.Latin('f'), Key.Latin('g'),
                Key.Latin('h'), Key.Latin('j'), Key.Latin('k'), Key.Latin('l'),
                Key.Symbol(";", ';'), Key.Symbol("'", '\''),
                Key.Enter,
            ),
        )
        addRow(
            listOf(
                Key.Shift,
                Key.Latin('z'), Key.Latin('x'), Key.Latin('c'), Key.Latin('v'), Key.Latin('b'),
                Key.Latin('n'), Key.Latin('m'),
                Key.Symbol(",", ','), Key.Symbol(".", '.'), Key.Symbol("/", '/'),
                Key.Backspace,
            ),
        )
        addRow(
            listOf(
                Key.Ctrl, Key.Alt, Key.Command, Key.Language, Key.Space,
                Key.Arrow(Keysym.LEFT), Key.Arrow(Keysym.UP), Key.Arrow(Keysym.DOWN), Key.Arrow(Keysym.RIGHT),
                Key.Escape,
            ),
        )
    }

    private fun addRow(keys: List<Key>) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(46))
        }
        keys.forEach { key ->
            val weight = when (key) {
                Key.Space -> 3.4f
                Key.Shift, Key.Ctrl, Key.Alt, Key.Command -> 1.4f
                Key.Backspace, Key.Enter, Key.Tab -> 1.5f
                Key.Language -> 1.6f
                else -> 1f
            }
            val view = TextView(context).apply {
                gravity = Gravity.CENTER
                isClickable = true
                setTextColor(Color.parseColor("#E7EBEF"))
                textSize = 16f
                setPadding(0, 0, 0, 0)
            }
            val params = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            params.setMargins(dp(3), dp(3), dp(3), dp(3))
            view.layoutParams = params
            view.background = keyBackground()
            bindTouch(view, key)
            buttons += view to key
            row.addView(view)
        }
        addView(row)
    }

    private fun bindTouch(view: TextView, key: Key) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = keyBackground(pressed = true)
                    keyDown(key)
                    if (key is Key.Backspace || key is Key.Arrow) startRepeat(key)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.background = keyBackground()
                    stopRepeat()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.background = keyBackground()
                    stopRepeat()
                    true
                }

                else -> false
            }
        }
    }

    private fun startRepeat(key: Key) {
        stopRepeat()
        val action = object : Runnable {
            override fun run() {
                keyDown(key)
                repeatHandler.postDelayed(this, 55)
            }
        }
        repeatAction = action
        repeatHandler.postDelayed(action, 380)
    }

    private fun stopRepeat() {
        repeatAction?.let { repeatHandler.removeCallbacks(it) }
        repeatAction = null
    }

    private fun keyDown(key: Key) {
        when (key) {
            is Key.Latin -> listener.onLatin(key.key, shifted)
            is Key.Symbol -> listener.onLatin(key.latin, shifted)
            Key.Space -> listener.onSpace()
            Key.Backspace -> listener.onBackspace()
            Key.Enter -> listener.onEnter()
            Key.Tab -> listener.onSpecial(Keysym.TAB)
            Key.Escape -> listener.onSpecial(Keysym.ESCAPE)
            is Key.Arrow -> listener.onSpecial(key.keysym)
            Key.Shift -> {
                shifted = !shifted
                refreshLabels()
            }

            Key.Ctrl -> listener.onSpecial(Keysym.CONTROL_L)
            Key.Alt -> listener.onSpecial(Keysym.ALT_L)
            Key.Command -> listener.onSpecial(Keysym.SUPER_L)
            Key.Language -> {
                korean = !korean
                listener.onToggleLanguage()
                refreshLabels()
            }
        }
    }

    private fun refreshLabels() {
        for ((view, key) in buttons) {
            view.text = labelFor(key)
            view.alpha = if (key is Key.Shift && shifted) 0.65f else 1f
        }
    }

    private fun labelFor(key: Key): String {
        return when (key) {
            is Key.Latin -> if (korean) koreanLabel(key.key, shifted) else {
                if (shifted) key.key.uppercaseChar().toString() else key.key.toString()
            }

            is Key.Symbol -> if (shifted) shiftSymbol(key.latin) else key.label
            Key.Space -> "space"
            Key.Backspace -> "⌫"
            Key.Enter -> "⏎"
            Key.Tab -> "⇥"
            Key.Escape -> "esc"
            Key.Shift -> "⇧"
            Key.Ctrl -> "⌃"
            Key.Alt -> "⌥"
            Key.Command -> "⌘"
            Key.Language -> if (korean) "한/영" else "EN/한"
            is Key.Arrow -> when (key.keysym) {
                Keysym.LEFT -> "←"
                Keysym.RIGHT -> "→"
                Keysym.UP -> "↑"
                else -> "↓"
            }
        }
    }

    private fun koreanLabel(key: Char, shifted: Boolean): String {
        val base = KOREAN_MAP[key] ?: return key.toString()
        if (!shifted) return base.toString()
        return SHIFT_KOREAN_MAP[key]?.toString() ?: base.toString()
    }

    private fun shiftSymbol(ch: Char): String = when (ch) {
        '1' -> "!"
        '2' -> "@"
        '3' -> "#"
        '4' -> "$"
        '5' -> "%"
        '6' -> "^"
        '7' -> "&"
        '8' -> "*"
        '9' -> "("
        '0' -> ")"
        '-' -> "_"
        '=' -> "+"
        '[' -> "{"
        ']' -> "}"
        ';' -> ":"
        '\'' -> "\""
        ',' -> "<"
        '.' -> ">"
        '/' -> "?"
        else -> ch.toString()
    }

    private fun keyBackground(pressed: Boolean = false): GradientDrawable {
        val color = if (pressed) "#3A4652" else "#232A33"
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val KOREAN_MAP = mapOf(
            'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
            'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
            'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
            'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
            'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ',
            'n' to 'ㅜ', 'm' to 'ㅡ',
        )

        private val SHIFT_KOREAN_MAP = mapOf(
            'q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ',
            'o' to 'ㅒ', 'p' to 'ㅖ',
        )
    }
}
