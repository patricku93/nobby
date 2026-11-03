package com.nobby.ring.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class WidgetInfoTest {
    private fun attrs(): Map<String, String> {
        val parser = ApplicationProvider.getApplicationContext<Context>().resources.getXml(R.xml.ring_widget_info)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        return (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
    }

    @Test
    fun `the widget resizes freely to full width and height`() {
        val a = attrs()
        // resizeMode horizontal|vertical = 0x1 | 0x2
        assertEquals("0x3", a["resizeMode"])
        assertNull("no width cap", a["maxResizeWidth"])
        assertNull("no height cap", a["maxResizeHeight"])
        assertTrue(a.containsKey("minResizeWidth"))
        assertTrue(a.containsKey("minResizeHeight"))
        assertEquals("2", a["targetCellWidth"])
        assertEquals("1", a["targetCellHeight"])
    }
}
