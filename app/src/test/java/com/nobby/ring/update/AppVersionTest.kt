package com.nobby.ring.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `parses plain and v-prefixed versions`() {
        val expected = AppVersion(2, 1, 3)
        listOf("2.1.3", "v2.1.3", "V2.1.3", "  v2.1.3 ").forEach { assertEquals(it, expected, AppVersion.parse(it)) }
    }

    @Test
    fun `ignores pre-release and build suffixes`() {
        assertEquals(AppVersion(2, 1, 3), AppVersion.parse("v2.1.3-rc1"))
        assertEquals(AppVersion(2, 1, 3), AppVersion.parse("2.1.3+build.5"))
        assertEquals(AppVersion(2, 1, 3), AppVersion.parse("2.1.3-beta.2+x"))
    }

    @Test
    fun `missing minor and patch count as zero`() {
        assertEquals(AppVersion(2, 1, 0), AppVersion.parse("v2.1"))
        assertEquals(AppVersion(3, 0, 0), AppVersion.parse("3"))
    }

    @Test
    fun `garbage is rejected`() {
        listOf("", " ", "v", "garbage", "1.2.3.4", "1..2", "a.b.c", "-1.0.0", "1.2.x", "1.-2.3", "99999999999.0.0", "latest")
            .forEach { assertNull("'$it'", AppVersion.parse(it)) }
        assertNull(AppVersion.parse(null))
    }

    @Test
    fun `compares numerically not lexically`() {
        assertTrue(AppVersion(2, 10, 0) > AppVersion(2, 9, 0))
        assertTrue(AppVersion(2, 1, 0) > AppVersion(2, 0, 9))
        assertTrue(AppVersion(3, 0, 0) > AppVersion(2, 99, 99))
        assertEquals(0, AppVersion(2, 1, 3).compareTo(AppVersion.parse("v2.1.3-rc1")!!))
    }

    @Test
    fun `version code follows major times 10000 plus minor times 100 plus patch`() {
        assertEquals(20000, AppVersion(2, 0, 0).versionCode)
        assertEquals(20103, AppVersion(2, 1, 3).versionCode)
    }

    @Test
    fun `toString is the bare triple`() {
        assertEquals("2.1.3", AppVersion.parse("v2.1.3-rc1").toString())
    }
}
