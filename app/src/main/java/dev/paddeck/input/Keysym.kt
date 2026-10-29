package dev.paddeck.input

import android.view.KeyEvent

/** X11 키심 값. RFB는 키를 키심 숫자로 주고받습니다. */
object Keysym {
    const val BACKSPACE = 0xff08
    const val TAB = 0xff09
    const val RETURN = 0xff0d
    const val ESCAPE = 0xff1b
    const val INSERT = 0xff63
    const val DELETE = 0xffff
    const val HOME = 0xff50
    const val END = 0xff57
    const val PAGE_UP = 0xff55
    const val PAGE_DOWN = 0xff56
    const val LEFT = 0xff51
    const val UP = 0xff52
    const val RIGHT = 0xff53
    const val DOWN = 0xff54
    const val SHIFT_L = 0xffe1
    const val SHIFT_R = 0xffe2
    const val CONTROL_L = 0xffe3
    const val CONTROL_R = 0xffe4
    const val CAPS_LOCK = 0xffe5
    const val ALT_L = 0xffe9
    const val ALT_R = 0xffea
    const val SUPER_L = 0xffeb
    const val SUPER_R = 0xffec
    const val SPACE = 0x20
    const val F1 = 0xffbe

    fun function(n: Int): Int = F1 + (n - 1)

    /** 하드웨어 키 이벤트를 키심으로 바꿉니다. 시프트는 맥이 직접 처리하게 두고 기본 글자를 씁니다. */
    fun fromKeyEvent(event: KeyEvent): Int? {
        when (val code = event.keyCode) {
            KeyEvent.KEYCODE_DEL -> return BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> return DELETE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return RETURN
            KeyEvent.KEYCODE_TAB -> return TAB
            KeyEvent.KEYCODE_ESCAPE -> return ESCAPE
            KeyEvent.KEYCODE_SPACE -> return SPACE
            KeyEvent.KEYCODE_DPAD_LEFT -> return LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> return RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> return UP
            KeyEvent.KEYCODE_DPAD_DOWN -> return DOWN
            KeyEvent.KEYCODE_MOVE_HOME -> return HOME
            KeyEvent.KEYCODE_MOVE_END -> return END
            KeyEvent.KEYCODE_PAGE_UP -> return PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> return PAGE_DOWN
            KeyEvent.KEYCODE_INSERT -> return INSERT
            KeyEvent.KEYCODE_SHIFT_LEFT -> return SHIFT_L
            KeyEvent.KEYCODE_SHIFT_RIGHT -> return SHIFT_R
            KeyEvent.KEYCODE_CTRL_LEFT -> return CONTROL_L
            KeyEvent.KEYCODE_CTRL_RIGHT -> return CONTROL_R
            KeyEvent.KEYCODE_ALT_LEFT -> return ALT_L
            KeyEvent.KEYCODE_ALT_RIGHT -> return ALT_R
            KeyEvent.KEYCODE_META_LEFT -> return SUPER_L
            KeyEvent.KEYCODE_META_RIGHT -> return SUPER_R
            KeyEvent.KEYCODE_CAPS_LOCK -> return CAPS_LOCK
            KeyEvent.KEYCODE_SYM -> return SUPER_L
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> return function(code - KeyEvent.KEYCODE_F1 + 1)
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> return 'a'.code + (code - KeyEvent.KEYCODE_A)
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> return '0'.code + (code - KeyEvent.KEYCODE_0)
            else -> {
                val unicode = event.getUnicodeChar(0)
                return if (unicode in 0x20..0x7e) unicode else null
            }
        }
    }

    /** 화면 키보드가 보내는 글자를 키심으로 바꿉니다. */
    fun fromChar(ch: Char): Int? = when {
        ch in 'a'..'z' || ch in 'A'..'Z' -> ch.lowercaseChar().code
        ch in '0'..'9' -> ch.code
        ch in ' '..'~' -> ch.code
        ch == '\n' -> RETURN
        ch == '\t' -> TAB
        else -> null
    }
}
