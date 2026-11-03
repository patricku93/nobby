package com.nobby.ring.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {
    private data class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(path: String): Res {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        fun elements(tag: String) = doc.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        val strings = elements("string").associate { it.getAttribute("name") to it.textContent }
        val plurals = elements("plurals").associate { p ->
            val items = p.getElementsByTagName("item")
            p.getAttribute("name") to (0 until items.length).map { items.item(it) as Element }
                .associate { it.getAttribute("quantity") to it.textContent }
        }
        return Res(strings, plurals)
    }

    private val en = load("src/main/res/values/strings.xml")
    private val de = load("src/main/res/values-de/strings.xml")
    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private fun args(texts: Collection<String>) = texts.flatMap { t -> placeholder.findAll(t).map { it.value } }.toSet()

    @Test
    fun `string keys are identical in EN and DE`() = assertEquals(en.strings.keys.sorted(), de.strings.keys.sorted())

    @Test
    fun `plural keys are identical in EN and DE`() = assertEquals(en.plurals.keys.sorted(), de.plurals.keys.sorted())

    @Test
    fun `every plural has one and other in both languages`() {
        (en.plurals + de.plurals.mapKeys { "de:${it.key}" }).forEach { (name, items) ->
            assertTrue("$name needs one+other, has ${items.keys}", items.keys.containsAll(listOf("one", "other")))
        }
    }

    @Test
    fun `placeholders match between EN and DE`() {
        en.strings.forEach { (k, v) -> assertEquals("string $k", args(listOf(v)), args(listOf(de.strings.getValue(k)))) }
        en.plurals.forEach { (k, v) -> assertEquals("plural $k", args(v.values), args(de.plurals.getValue(k).values)) }
    }
}
