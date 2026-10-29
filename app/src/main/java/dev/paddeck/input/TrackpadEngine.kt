package dev.paddeck.input

/**
 * 트랙패드 느낌을 만드는 계산만 담당합니다.
 * 안드로이드에 의존하지 않아 기기 없이도 시험할 수 있습니다.
 */
class TrackpadEngine(
    private val emit: (RemoteInput) -> Unit,
) {
    var remoteWidth: Int = 1920
        set(value) {
            field = value.coerceAtLeast(1)
            cursorX = cursorX.coerceIn(0f, field - 1f)
        }

    var remoteHeight: Int = 1080
        set(value) {
            field = value.coerceAtLeast(1)
            cursorY = cursorY.coerceIn(0f, field - 1f)
        }

    var sensitivity: Float = 1.4f
    var acceleration: Boolean = true
    var naturalScroll: Boolean = true
    var tapToClick: Boolean = true

    var cursorX: Float = 960f
        private set
    var cursorY: Float = 540f
        private set

    var buttonMask: Int = 0
        private set

    private var wheelAccX = 0f
    private var wheelAccY = 0f
    private var dragLocked = false

    fun moveRelative(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val factor = if (acceleration) accelerate(dx, dy) else 1f
        cursorX = (cursorX + dx * sensitivity * factor).coerceIn(0f, remoteWidth - 1f)
        cursorY = (cursorY + dy * sensitivity * factor).coerceIn(0f, remoteHeight - 1f)
        emitPointer()
    }

    fun moveToFraction(fx: Float, fy: Float) {
        cursorX = (fx.coerceIn(0f, 1f) * (remoteWidth - 1))
        cursorY = (fy.coerceIn(0f, 1f) * (remoteHeight - 1))
        emitPointer()
    }

    fun scroll(dx: Float, dy: Float) {
        val sign = if (naturalScroll) -1f else 1f
        wheelAccY += dy * sign
        wheelAccX += dx * sign
        while (wheelAccY <= -1f) {
            wheelAccY += 1f
            wheel(Buttons.WHEEL_UP)
        }
        while (wheelAccY >= 1f) {
            wheelAccY -= 1f
            wheel(Buttons.WHEEL_DOWN)
        }
        while (wheelAccX <= -1f) {
            wheelAccX += 1f
            wheel(Buttons.WHEEL_LEFT)
        }
        while (wheelAccX >= 1f) {
            wheelAccX -= 1f
            wheel(Buttons.WHEEL_RIGHT)
        }
    }

    private fun wheel(button: Int) {
        emitPointer(maskOverride = buttonMask or button)
        emitPointer()
    }

    fun tapLeft() {
        if (dragLocked) {
            endDrag()
            return
        }
        if (!tapToClick) return
        click(Buttons.LEFT)
    }

    fun tapRight() {
        click(Buttons.RIGHT)
    }

    fun doubleTapDragStart() {
        if (buttonMask and Buttons.LEFT == 0) {
            buttonMask = buttonMask or Buttons.LEFT
            dragLocked = true
            emitPointer()
        }
    }

    fun endDrag() {
        if (dragLocked) {
            dragLocked = false
            buttonMask = buttonMask and Buttons.LEFT.inv()
            emitPointer()
        }
    }

    fun click(button: Int) {
        emitPointer(maskOverride = buttonMask or button)
        emitPointer()
    }

    fun press(button: Int) {
        buttonMask = buttonMask or button
        emitPointer()
    }

    fun release(button: Int) {
        buttonMask = buttonMask and button.inv()
        if (buttonMask and Buttons.LEFT == 0) dragLocked = false
        emitPointer()
    }

    fun releaseAll() {
        if (buttonMask != 0) {
            buttonMask = 0
            dragLocked = false
            emitPointer()
        }
    }

    fun keyChord(modifier: Int, keySym: Int) {
        emit(RemoteInput.Key(true, modifier))
        emit(RemoteInput.Key(true, keySym))
        emit(RemoteInput.Key(false, keySym))
        emit(RemoteInput.Key(false, modifier))
    }

    fun keyDown(keysym: Int) = emit(RemoteInput.Key(true, keysym))
    fun keyUp(keysym: Int) = emit(RemoteInput.Key(false, keysym))

    private fun emitPointer(maskOverride: Int? = null) {
        emit(
            RemoteInput.Pointer(
                x = cursorX.toInt().coerceIn(0, remoteWidth - 1),
                y = cursorY.toInt().coerceIn(0, remoteHeight - 1),
                buttonMask = maskOverride ?: buttonMask,
            ),
        )
    }

    private fun accelerate(dx: Float, dy: Float): Float {
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        return (1f + (distance / 18f)).coerceAtMost(3.2f)
    }
}
