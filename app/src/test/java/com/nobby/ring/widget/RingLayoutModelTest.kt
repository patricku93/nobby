package com.nobby.ring.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ring-style geometry per widget size: shape, ring diameter, label, and the Done pill only when something is due. */
class RingLayoutModelTest {
    private fun model(w: Int, h: Int, due: Boolean = false) = ringLayoutModel(DpSize(w.dp, h.dp), due)

    @Test
    fun `sizes pick the 2x1, square and wide shapes`() {
        assertEquals(RingShape.SMALL, model(110, 50).shape)
        assertEquals(RingShape.SQUARE, model(110, 110).shape)
        assertEquals(RingShape.WIDE, model(250, 110).shape)
        assertEquals(RingShape.WIDE, model(400, 300).shape)
        assertFalse("2x1 shows only the number", model(110, 50).withLabel)
        assertTrue(model(110, 110).withLabel)
    }

    @Test
    fun `the ring fits the content box of every size`() {
        for ((w, h) in listOf(110 to 50, 110 to 110, 250 to 110, 400 to 300, 160 to 180)) for (due in listOf(false, true)) {
            val m = model(w, h, due)
            val cw = w - 16
            val ch = h - 16
            val ringBox = if (m.shape == RingShape.SMALL) m.ring.value else m.ring.value * 1.08f
            val below = if (m.donePill && m.shape == RingShape.SQUARE) DONE_ROOM.value else 0f
            assertTrue("$w x $h due=$due ring $ringBox", ringBox + below <= ch + 0.01f)
            assertTrue("$w x $h due=$due width", ringBox <= cw + 0.01f)
        }
        assertEquals(34f, model(110, 50).ring.value, 0.01f)
    }

    @Test
    fun `square shows the Done pill under the ring only when an event is due or overdue`() {
        assertTrue(model(160, 180, due = true).donePill)
        assertFalse(model(160, 180, due = false).donePill)
        assertTrue("the pill takes room from the ring", model(160, 180, due = true).ring < model(160, 180).ring)
        assertTrue(model(110, 110, due = true).donePill)
    }

    @Test
    fun `wide shows Done beside the ring when due and 2x1 never`() {
        assertTrue(model(300, 150, due = true).donePill)
        assertFalse(model(300, 150).donePill)
        assertFalse(model(110, 60, due = true).donePill)
        assertEquals("wide keeps the ring size", model(300, 150).ring, model(300, 150, due = true).ring)
    }
}
