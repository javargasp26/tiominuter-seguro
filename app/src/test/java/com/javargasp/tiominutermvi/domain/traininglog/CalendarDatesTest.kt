package com.javargasp.tiominutermvi.domain.traininglog

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDatesTest {

    @Test
    fun `mondayOf returns the Monday of the week for every day`() {
        // 19 October 2026 is a Monday, 25 October a Sunday
        (19..25).forEach { day -> assertEquals("2026-10-19", CalendarDates.mondayOf("2026-10-$day")) }
        assertEquals("2026-10-26", CalendarDates.mondayOf("2026-10-26"))
    }

    @Test
    fun `weeks cross month, year and leap-day boundaries`() {
        assertEquals("2026-12-28", CalendarDates.mondayOf("2027-01-01"))
        assertEquals(
            listOf("2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01"),
            CalendarDates.weekDates("2026-10-28")
        )
        assertEquals("2028-02-28", CalendarDates.mondayOf("2028-03-01"))
        assertEquals("2028-02-29", CalendarDates.plusDays("2028-02-28", 1))
    }

    @Test
    fun `a week with a daylight saving change still has 7 consecutive dates`() {
        // Europe changes clocks on Sunday 25 October 2026; dates have no zone
        assertEquals(
            listOf("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25"),
            CalendarDates.weekDates("2026-10-25")
        )
    }

    @Test
    fun `plusWeeks moves Monday to Monday from any day`() {
        assertEquals("2026-10-12", CalendarDates.plusWeeks("2026-10-22", -1))
        assertEquals("2026-10-26", CalendarDates.plusWeeks("2026-10-25", 1))
        assertEquals("2027-01-04", CalendarDates.plusWeeks("2026-12-28", 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an invalid date is rejected`() {
        CalendarDates.mondayOf("2026-02-30")
    }
}
