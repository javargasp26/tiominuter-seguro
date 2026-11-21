package com.javargasp.tiominutermvi.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The single display rule for catalog names: Spanish → nameEs ?: name; otherwise name.
 */
class LocalizedNameTest {

    @Test
    fun `spanish shows the Spanish name`() {
        assertEquals("Sentadilla con barra", localizedName("Squat", "Sentadilla con barra", "es"))
    }

    @Test
    fun `other languages show the English name`() {
        assertEquals("Squat", localizedName("Squat", "Sentadilla con barra", "en"))
        assertEquals("Squat", localizedName("Squat", "Sentadilla con barra", "fr"))
    }

    @Test
    fun `spanish falls back to the English name when the Spanish one is missing or blank`() {
        assertEquals("Squat", localizedName("Squat", null, "es"))
        assertEquals("Squat", localizedName("Squat", "", "es"))
        assertEquals("Squat", localizedName("Squat", "   ", "es"))
    }

    @Test
    fun `the language is the code of the locale, so every Spanish region matches`() {
        // es-MX and es-ES both have the language "es".
        val mexico = java.util.Locale.forLanguageTag("es-MX").language
        val spain = java.util.Locale.forLanguageTag("es-ES").language
        assertEquals("Plancha", localizedName("Plank", "Plancha", mexico))
        assertEquals("Plancha", localizedName("Plank", "Plancha", spain))
    }
}
