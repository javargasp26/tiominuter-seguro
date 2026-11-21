package com.javargasp.tiominutermvi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guard test for neutral and inclusive language (user story 7).
 *
 * Reads the real string resources by relative path (Gradle runs unit tests from the app module
 * directory) and fails when a user-facing string contains a gendered form directed at the user,
 * an inclusive "@"/"x"/"e" ending, or a blaming / appearance-shaming term.
 *
 * Denylist (Spanish): gendered adjectives/participles that refer to the user. They must be
 * rewritten with verbs, second person or nouns ("Te damos la bienvenida", "¿Empezamos?").
 * The list matches whole words only, so compounds like "listón" or "preparada" inside a longer
 * word never trigger it.
 *
 * Allowlist: a string key and the one denylisted word it may contain, each with a comment
 * explaining why. The exception covers only that word in that key: every other denylisted word is
 * still checked there, and the word still fails in any other string.
 */
class NeutralLanguageTest {

    private val esDenylist = listOf(
        "bienvenido", "bienvenida", "atrasado", "atrasada",
        "preparado", "preparada", "cansado", "cansada",
        "motivado", "motivada", "comprometido", "comprometida",
        "listo", "lista",
    )

    private val enDenylist = listOf("guys", "bro", "man up", "beast mode")

    /** Blaming or appearance-shaming terms, same tone rules. */
    private val toneDenylist = listOf(
        "no cumpliste", "fallaste", "perezoso", "lazy", "sin excusas", "no excuses",
    )

    /**
     * Key → the denylisted words it may contain (its English and Spanish forms), with the reason.
     * Example entry: "program_level_advanced" to setOf("avanzado") describes the training level,
     * not the user.
     */
    private val allowlist: Map<String, Set<String>> = mapOf(
        // "Te damos la bienvenida": "bienvenida" is the noun (the neutral form the guidelines recommend)
        "welcome_title" to setOf("bienvenida"),
        // The brand tagline "No excuses — just 10 minutes a day." / "Sin excusas — solo 10 minutos al
        // día." (CLAUDE.md "Language guidelines"): the product owner's decision, the core motivation
        // of the daily routine. Don't change it without the product owner's approval
        "home_screen_tagline" to setOf("no excuses", "sin excusas"),
    )

    /**
     * Inclusive endings on the denylist stems: "list@", "listx", "liste", "bienvenide", etc.
     * Standard "o"/"a" forms are already covered by the denylist; this adds the "@"/"x"/"e"
     * variants. Whole words only.
     */
    private val esInclusiveEnding = Regex(
        """\b(bienvenid|atrasad|preparad|cansad|motivad|comprometid|list)[oa@xe]\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun readStrings(file: String): Map<String, String> {
        val path = File("src/main/res/$file")
        assertTrue("run from the app module: $path", path.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path)
        val resources = document.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (index in 0 until resources.length) {
            val node = resources.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            result[name] = node.textContent
        }
        return result
    }

    private fun offenders(
        strings: Map<String, String>,
        denylist: List<String>,
        extra: List<Regex> = emptyList(),
    ): List<String> = strings.mapNotNull { (name, text) ->
        val lower = text.lowercase()
        val allowed = allowlist[name].orEmpty()
        val hit = denylist.firstOrNull { it !in allowed && Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lower) }
            ?: extra.firstNotNullOfOrNull { regex -> regex.findAll(lower).map { it.value }.firstOrNull { it !in allowed } }
        if (hit != null) "$name -> \"$hit\"" else null
    }

    @Test
    fun `no gendered user-directed forms in Spanish strings`() {
        val offenders = offenders(readStrings("values-es/strings.xml"), esDenylist)
        assertTrue(
            "Gendered user-directed forms found (rewrite with verbs, second person or nouns):\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no inclusive endings or blaming terms in Spanish strings`() {
        val offenders = offenders(
            readStrings("values-es/strings.xml"),
            toneDenylist,
            extra = listOf(esInclusiveEnding),
        )
        assertTrue(
            "Inclusive endings or blaming terms found:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no gendered or shaming terms in English strings`() {
        val offenders = offenders(readStrings("values/strings.xml"), enDenylist + toneDenylist)
        assertTrue(
            "Gendered or shaming terms found:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the tagline is the only string allowed to say sin excusas or no excuses`() {
        val strings = mapOf(
            "home_screen_tagline" to "Sin excusas — solo 10 minutos al día.",
            "home_screen_tagline_en" to "No excuses — just 10 minutes a day.",
            "weekly_goal_met" to "¡Sin excusas! Meta cumplida.",
            "workout_complete" to "No excuses, great job.",
        )

        assertEquals(
            listOf(
                "home_screen_tagline_en -> \"no excuses\"",
                "weekly_goal_met -> \"sin excusas\"",
                "workout_complete -> \"no excuses\"",
            ),
            offenders(strings, toneDenylist),
        )
    }

    @Test
    fun `the tagline's exception covers only its phrase, not other denylisted words`() {
        val offenders = offenders(mapOf("home_screen_tagline" to "Sin excusas, perezoso."), toneDenylist)

        assertEquals(listOf("home_screen_tagline -> \"perezoso\""), offenders)
    }
}
