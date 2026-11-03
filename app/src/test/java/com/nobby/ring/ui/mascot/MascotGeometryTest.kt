package com.nobby.ring.ui.mascot

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotGeometryTest {
    @Test
    fun `every nobby_js path string parses`() {
        MascotGeometry.allPaths().forEach { d -> assertTrue(d, PathParser().parsePathString(d).toNodes().isNotEmpty()) }
    }

    @Test
    fun `prop extents match nobby_js`() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), MascotGeometry.extents(null), 0f)
        assertArrayEquals(floatArrayOf(4f, 0f, 14f, 0f), MascotGeometry.extents(MascotProp.WAVE), 0f)
        assertArrayEquals(floatArrayOf(4f, 4f, 14f, 4f), MascotGeometry.extents(MascotProp.CALENDAR), 0f)
        assertArrayEquals(floatArrayOf(4f, 6f, 16f, 0f), MascotGeometry.extents(MascotProp.BELL), 0f)
        assertArrayEquals(floatArrayOf(4f, 0f, 16f, 6f), MascotGeometry.extents(MascotProp.PALETTE), 0f)
        assertArrayEquals(floatArrayOf(4f, 4f, 14f, 6f), MascotGeometry.extents(MascotProp.NOTE), 0f)
    }

    @Test
    fun `line weight follows lineFor`() {
        assertEquals(2.5f, MascotGeometry.lineFor(120f))
        assertEquals(2f, MascotGeometry.lineFor(40f))
        assertEquals(1.5f, MascotGeometry.lineFor(24f))
    }
}
