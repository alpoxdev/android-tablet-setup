package dev.paddeck.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadEngineTest {

    private val events = mutableListOf<RemoteInput>()
    private val engine = TrackpadEngine { events += it }.apply {
        remoteWidth = 1000
        remoteHeight = 1000
        sensitivity = 1f
        acceleration = false
        naturalScroll = true
    }

    @Test
    fun movesCursorAndClamps() {
        engine.moveToFraction(0.5f, 0.5f)
        events.clear()
        engine.moveRelative(10f, 10f)
        val pointer = events.last() as RemoteInput.Pointer
        assertEquals(509, pointer.x)
        assertEquals(509, pointer.y)

        engine.moveRelative(100000f, 100000f)
        val clamped = events.last() as RemoteInput.Pointer
        assertEquals(999, clamped.x)
    }

    @Test
    fun naturalScrollSendsWheelUpWhenFingerMovesDown() {
        events.clear()
        engine.scroll(0f, 8f)
        val masks = events.filterIsInstance<RemoteInput.Pointer>().map { it.buttonMask }
        assertTrue(masks.contains(Buttons.WHEEL_UP))
        val last = events.last() as RemoteInput.Pointer
        assertEquals(0, last.buttonMask)
    }

    @Test
    fun clickSendsPressThenRelease() {
        events.clear()
        engine.tapLeft()
        assertEquals(2, events.size)
        assertEquals(Buttons.LEFT, (events[0] as RemoteInput.Pointer).buttonMask)
        assertEquals(0, (events[1] as RemoteInput.Pointer).buttonMask)
    }

    @Test
    fun tapToClickCanBeDisabled() {
        engine.tapToClick = false
        events.clear()
        engine.tapLeft()
        assertTrue(events.isEmpty())
    }

    @Test
    fun dragLockReleasesOnTap() {
        engine.doubleTapDragStart()
        assertEquals(Buttons.LEFT, (events.last() as RemoteInput.Pointer).buttonMask)
        events.clear()
        engine.tapLeft()
        assertEquals(0, (events.last() as RemoteInput.Pointer).buttonMask)
    }

    @Test
    fun keyChordSendsModifierAroundKey() {
        events.clear()
        engine.keyChord(Keysym.CONTROL_L, Keysym.UP)
        val keys = events.filterIsInstance<RemoteInput.Key>()
        assertEquals(listOf(true, true, false, false), keys.map { it.down })
        assertEquals(listOf(Keysym.CONTROL_L, Keysym.UP, Keysym.UP, Keysym.CONTROL_L), keys.map { it.keysym })
    }
}
