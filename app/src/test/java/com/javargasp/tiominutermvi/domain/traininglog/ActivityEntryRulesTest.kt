package com.javargasp.tiominutermvi.domain.traininglog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Now: Thursday 2026-10-22T10:00:00Z. */
class ActivityEntryRulesTest {

    private val now = 1_792_663_200_000L
    private val utc = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    private val windowStart = ActivityEntryRules.windowStart(utc)

    private fun input(
        type: ActivityType? = ActivityType.RUNNING,
        name: String = "",
        minutes: Int? = 30,
        intensity: Intensity? = Intensity.MODERATE,
        completedAt: Long = now - 60 * 60 * 1000L,
        note: String = ""
    ) = ActivityEntryInput(type, name, minutes, intensity, completedAt, note)

    private fun errors(input: ActivityEntryInput) = ActivityEntryRules.validate(input, now, windowStart)

    @Test
    fun `a complete entry is valid`() {
        assertTrue(errors(input()).isEmpty())
    }

    @Test
    fun `duration must be 1 to 600 minutes`() {
        assertEquals(setOf(ActivityEntryError.DURATION_OUT_OF_RANGE), errors(input(minutes = null)))
        assertEquals(setOf(ActivityEntryError.DURATION_OUT_OF_RANGE), errors(input(minutes = 0)))
        assertTrue(errors(input(minutes = 1)).isEmpty())
        assertTrue(errors(input(minutes = 600)).isEmpty())
        assertEquals(setOf(ActivityEntryError.DURATION_OUT_OF_RANGE), errors(input(minutes = 601)))
    }

    @Test
    fun `type and intensity are required`() {
        assertEquals(
            setOf(ActivityEntryError.TYPE_MISSING, ActivityEntryError.INTENSITY_MISSING),
            errors(input(type = null, intensity = null))
        )
    }

    @Test
    fun `OTHER needs a name of at most 40 characters`() {
        assertEquals(setOf(ActivityEntryError.NAME_MISSING), errors(input(type = ActivityType.OTHER, name = "")))
        assertEquals(setOf(ActivityEntryError.NAME_MISSING), errors(input(type = ActivityType.OTHER, name = "   ")))
        assertTrue(errors(input(type = ActivityType.OTHER, name = "a".repeat(40))).isEmpty())
        assertTrue(errors(input(type = ActivityType.OTHER, name = "  " + "a".repeat(40) + "  ")).isEmpty())
        assertEquals(setOf(ActivityEntryError.NAME_TOO_LONG), errors(input(type = ActivityType.OTHER, name = "a".repeat(41))))
        // Other types ignore the name
        assertTrue(errors(input(type = ActivityType.YOGA, name = "a".repeat(60))).isEmpty())
    }

    @Test
    fun `the note is optional and at most 200 characters`() {
        assertTrue(errors(input(note = "")).isEmpty())
        assertTrue(errors(input(note = "n".repeat(200))).isEmpty())
        assertEquals(setOf(ActivityEntryError.NOTE_TOO_LONG), errors(input(note = "n".repeat(201))))
    }

    @Test
    fun `the time can be now but never in the future`() {
        assertTrue(errors(input(completedAt = now)).isEmpty())
        assertEquals(setOf(ActivityEntryError.DATE_IN_FUTURE), errors(input(completedAt = now + 1)))
        assertEquals(setOf(ActivityEntryError.DATE_IN_FUTURE), errors(input(completedAt = now + 60 * 60 * 1000L)))
    }

    @Test
    fun `the window starts at midnight six days ago`() {
        // Friday 16 October 00:00 UTC
        assertEquals(1_792_108_800_000L, windowStart)
        assertTrue(errors(input(completedAt = windowStart)).isEmpty())
        assertEquals(setOf(ActivityEntryError.DATE_TOO_EARLY), errors(input(completedAt = windowStart - 1)))
    }

    @Test
    fun `the window follows the device zone`() {
        // Same instant; in Madrid (UTC+2) the window starts at 2026-10-15T22:00:00Z
        val madrid = LocalDateProvider({ now }, { TimeZone.getTimeZone("Europe/Madrid") })
        val madridStart = ActivityEntryRules.windowStart(madrid)
        assertEquals(1_792_101_600_000L, madridStart)

        val lateThursdayUtc = 1_792_105_200_000L // 2026-10-15T23:00Z = Friday 01:00 in Madrid
        assertEquals(setOf(ActivityEntryError.DATE_TOO_EARLY), errors(input(completedAt = lateThursdayUtc)))
        assertTrue(ActivityEntryRules.validate(input(completedAt = lateThursdayUtc), now, madridStart).isEmpty())
    }

    @Test
    fun `stored title and note`() {
        assertEquals("Climbing", ActivityEntryRules.titleFor(input(type = ActivityType.OTHER, name = " Climbing ")))
        assertEquals("", ActivityEntryRules.titleFor(input(type = ActivityType.RUNNING, name = "ignored")))
        assertEquals("Felt good", ActivityEntryRules.noteFor(input(note = "  Felt good \n")))
        assertNull(ActivityEntryRules.noteFor(input(note = " \n ")))
    }

    @Test
    fun `codes round trip and unknown codes are null`() {
        ActivityType.entries.forEach { assertEquals(it, ActivityType.fromCode(it.code)) }
        Intensity.entries.forEach { assertEquals(it, Intensity.fromCode(it.code)) }
        assertNull(ActivityType.fromCode("SURFING"))
        assertNull(Intensity.fromCode("EXTREME"))
        assertEquals(14, ActivityType.entries.size)
    }
}
