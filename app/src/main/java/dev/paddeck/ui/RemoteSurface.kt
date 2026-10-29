package dev.paddeck.ui

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import dev.paddeck.input.Buttons
import dev.paddeck.input.TrackpadEngine

private fun centroid(points: List<Offset>): Offset? {
    if (points.isEmpty()) return null
    var x = 0f
    var y = 0f
    for (p in points) {
        x += p.x
        y += p.y
    }
    return Offset(x / points.size, y / points.size)
}

/**
 * 화면 전체를 트랙패드처럼 쓰게 하는 제스처 처리입니다.
 * 한 손가락은 커서 이동, 두 손가락은 스크롤, 두 손가락 톡은 오른쪽 클릭입니다.
 */
fun Modifier.trackpadGestures(engine: TrackpadEngine, directTouch: Boolean): Modifier =
    pointerInput(engine, directTouch) {
        awaitPointerEventScope {
            var mode = 0
            var downAt = 0L
            var travel = 0f
            var lastTap = 0L
            var threeFingerFired = false
            var previous: Offset? = null

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                val current = centroid(pressed.map { it.position })

                when (event.type) {
                    PointerEventType.Press -> {
                        if (mode == 0) {
                            mode = 1
                            downAt = System.currentTimeMillis()
                            travel = 0f
                            if (directTouch) {
                                val p = event.changes.first().position
                                engine.moveToFraction(p.x / size.width, p.y / size.height)
                            }
                        }
                        previous = current
                    }

                    PointerEventType.Move -> {
                        val before = previous
                        if (current != null && before != null) {
                            val dx = current.x - before.x
                            val dy = current.y - before.y
                            if (pressed.size >= 3) {
                                mode = 3
                                if (!threeFingerFired) {
                                    threeFingerFired = handleThreeFinger(dx, dy, engine)
                                }
                            } else if (pressed.size == 2) {
                                mode = 2
                                engine.scroll(dx / 7f, dy / 7f)
                            } else if (directTouch) {
                                engine.moveToFraction(current.x / size.width, current.y / size.height)
                                travel += Offset(dx, dy).getDistance()
                            } else {
                                engine.moveRelative(dx, dy)
                                travel += Offset(dx, dy).getDistance()
                            }
                        }
                        previous = current
                    }

                    PointerEventType.Release -> {
                        val now = System.currentTimeMillis()
                        if (mode == 1 && travel < 16f && now - downAt < 260) {
                            if (now - lastTap < 320) {
                                engine.doubleTapDragStart()
                                lastTap = 0
                            } else {
                                engine.tapLeft()
                                lastTap = now
                            }
                        }
                        if (pressed.isEmpty()) {
                            mode = 0
                            threeFingerFired = false
                            previous = null
                            engine.endDrag()
                        } else {
                            previous = current
                        }
                    }
                }

                if (event.type != PointerEventType.Release) {
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

private fun handleThreeFinger(dx: Float, dy: Float, engine: TrackpadEngine): Boolean {
    when {
        kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy < -30f -> engine.keyChord(0xffe3, 0xff52)
        kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy > 30f -> engine.keyChord(0xffe3, 0xff54)
        dx < -30f -> engine.keyChord(0xffe3, 0xff51)
        dx > 30f -> engine.keyChord(0xffe3, 0xff53)
        else -> return false
    }
    return true
}

class CapturedPointerHandler(private val engine: TrackpadEngine) {
    private var lastX = 0f
    private var lastY = 0f

    fun onEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val axisX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val axisY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                val dx = if (axisX != 0f || axisY != 0f) axisX else event.x - lastX
                val dy = if (axisX != 0f || axisY != 0f) axisY else event.y - lastY
                engine.moveRelative(dx, dy)
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_SCROLL -> {
                val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                engine.scroll(horizontal * 3f, -vertical * 3f)
            }

            MotionEvent.ACTION_BUTTON_PRESS -> {
                val button = event.buttonState
                if (button and MotionEvent.BUTTON_SECONDARY != 0) engine.press(Buttons.RIGHT)
                else if (button and MotionEvent.BUTTON_TERTIARY != 0) engine.press(Buttons.MIDDLE)
                else engine.press(Buttons.LEFT)
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = event.buttonState
                if (button and MotionEvent.BUTTON_SECONDARY != 0) engine.release(Buttons.RIGHT)
                else if (button and MotionEvent.BUTTON_TERTIARY != 0) engine.release(Buttons.MIDDLE)
                else engine.release(Buttons.LEFT)
            }

            MotionEvent.ACTION_UP -> engine.releaseAll()
        }
    }
}
