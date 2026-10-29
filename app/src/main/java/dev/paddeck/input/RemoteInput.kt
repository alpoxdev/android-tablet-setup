package dev.paddeck.input

/** 원격에 보낼 입력 한 가지. */
sealed interface RemoteInput {
    data class Pointer(val x: Int, val y: Int, val buttonMask: Int) : RemoteInput
    data class Key(val down: Boolean, val keysym: Int) : RemoteInput
}

object Buttons {
    const val LEFT = 1
    const val MIDDLE = 2
    const val RIGHT = 4
    const val WHEEL_UP = 8
    const val WHEEL_DOWN = 16
    const val WHEEL_LEFT = 32
    const val WHEEL_RIGHT = 64
}
