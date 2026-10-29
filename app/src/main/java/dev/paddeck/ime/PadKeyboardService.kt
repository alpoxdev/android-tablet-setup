package dev.paddeck.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import dev.paddeck.SessionBus
import dev.paddeck.input.Keysym

class PadKeyboardService : InputMethodService(), PadKeyboardView.Listener {

    private val composer = HangulComposer()
    private val heldModifiers = mutableSetOf<Int>()
    private var keyView: PadKeyboardView? = null

    override fun onCreateInputView(): View {
        val view = PadKeyboardView(this, this)
        keyView = view
        return view
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        heldModifiers.clear()
        composer.reset()
    }

    override fun onFinishInput() {
        flushComposer()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onLatin(key: Char, shifted: Boolean) {
        if (SessionBus.isRemote) {
            val keysym = Keysym.fromChar(key) ?: return
            if (shifted) SessionBus.sendKey(true, Keysym.SHIFT_L)
            SessionBus.sendKey(true, keysym)
            SessionBus.sendKey(false, keysym)
            if (shifted) SessionBus.sendKey(false, Keysym.SHIFT_L)
            return
        }

        if (keyView?.korean == true) {
            val jamo = jamoFor(key, shifted) ?: return
            applyEdit(composer.type(jamo))
        } else {
            flushComposer()
            val text = if (shifted) key.uppercaseChar().toString() else key.toString()
            currentInputConnection?.commitText(text, 1)
        }
    }

    override fun onSpace() {
        if (SessionBus.isRemote) {
            SessionBus.sendKey(true, Keysym.SPACE)
            SessionBus.sendKey(false, Keysym.SPACE)
            return
        }
        flushComposer()
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onBackspace() {
        if (SessionBus.isRemote) {
            SessionBus.sendKey(true, Keysym.BACKSPACE)
            SessionBus.sendKey(false, Keysym.BACKSPACE)
            return
        }
        val edit = composer.backspace()
        if (edit != null) {
            applyEdit(edit)
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    override fun onEnter() {
        if (SessionBus.isRemote) {
            SessionBus.sendKey(true, Keysym.RETURN)
            SessionBus.sendKey(false, Keysym.RETURN)
            return
        }
        flushComposer()
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onSpecial(keysym: Int) {
        val modifier = keysym in MODIFIERS
        if (SessionBus.isRemote) {
            if (modifier) {
                if (heldModifiers.contains(keysym)) {
                    SessionBus.sendKey(false, keysym)
                    heldModifiers.remove(keysym)
                } else {
                    SessionBus.sendKey(true, keysym)
                    heldModifiers.add(keysym)
                }
            } else {
                SessionBus.sendKey(true, keysym)
                SessionBus.sendKey(false, keysym)
            }
            return
        }

        val keyCode = when (keysym) {
            Keysym.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            Keysym.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            Keysym.UP -> KeyEvent.KEYCODE_DPAD_UP
            Keysym.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            Keysym.TAB -> KeyEvent.KEYCODE_TAB
            Keysym.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
            else -> return
        }
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun onToggleLanguage() {
        flushComposer()
    }

    private fun applyEdit(edit: HangulComposer.Edit) {
        val ic = currentInputConnection ?: return
        if (edit.commit.isNotEmpty()) ic.commitText(edit.commit, 1)
        if (edit.composing.isNotEmpty()) {
            ic.setComposingText(edit.composing, 1)
        } else {
            ic.finishComposingText()
        }
    }

    private fun flushComposer() {
        val ic = currentInputConnection ?: return
        val commit = composer.reset()
        if (commit.commit.isNotEmpty()) ic.commitText(commit.commit, 1)
        ic.finishComposingText()
    }

    private fun jamoFor(key: Char, shifted: Boolean): Char? {
        if (!shifted) return KOREAN_KEYS[key]
        return SHIFT_KOREAN_KEYS[key] ?: KOREAN_KEYS[key]
    }

    companion object {
        private val MODIFIERS = setOf(Keysym.CONTROL_L, Keysym.ALT_L, Keysym.SUPER_L, Keysym.SHIFT_L)

        private val KOREAN_KEYS = mapOf(
            'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ',
            'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
            'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ',
            'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
            'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ',
            'n' to 'ㅜ', 'm' to 'ㅡ',
        )

        private val SHIFT_KOREAN_KEYS = mapOf(
            'q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ',
            'o' to 'ㅒ', 'p' to 'ㅖ',
        )
    }
}
