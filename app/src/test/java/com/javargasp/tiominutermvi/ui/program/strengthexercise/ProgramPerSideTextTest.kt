package com.javargasp.tiominutermvi.ui.program.strengthexercise

import com.javargasp.tiominutermvi.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Per-side texts of the strength exercise screen. Plain JVM: reads the real
 * `strings.xml` files by relative path (Gradle runs unit tests from the module directory).
 */
class ProgramPerSideTextTest {

    @Test
    fun `per-side REPS slots use the per-side plural`() {
        assertEquals(R.plurals.program_reps_per_side, repsPlural(perSide = true))
        assertEquals(R.plurals.program_reps, repsPlural(perSide = false))
    }

    @Test
    fun `reps per side in English`() {
        val items = plurals("values", "program_reps_per_side")

        assertEquals("10 reps per side", items.getValue("other").format(10))
        assertEquals("1 rep per side", items.getValue("one").format(1))
    }

    @Test
    fun `reps per side in Spanish, with many`() {
        val items = plurals("values-es", "program_reps_per_side")

        assertEquals("10 repeticiones por lado", items.getValue("other").format(10))
        assertEquals("1 repetición por lado", items.getValue("one").format(1))
        assertEquals(setOf("one", "many", "other"), items.keys)
    }

    @Test
    fun `side labels in English and Spanish`() {
        assertEquals("Left side", string("values", "program_left_side"))
        assertEquals("Right side", string("values", "program_right_side"))
        assertEquals("Switch sides", string("values", "program_switch_sides"))
        assertEquals("Lado izquierdo", string("values-es", "program_left_side"))
        assertEquals("Lado derecho", string("values-es", "program_right_side"))
        assertEquals("Cambia de lado", string("values-es", "program_switch_sides"))
    }

    private fun document(folder: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/$folder/strings.xml"))

    private fun string(folder: String, name: String): String {
        val nodes = document(folder).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .single { it.getAttribute("name") == name }
            .textContent
    }

    private fun plurals(folder: String, name: String): Map<String, String> {
        val nodes = document(folder).getElementsByTagName("plurals")
        val plural = (0 until nodes.length).map { nodes.item(it) as Element }
            .single { it.getAttribute("name") == name }
        val items = plural.getElementsByTagName("item")
        return (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("quantity") to it.textContent }
    }
}
