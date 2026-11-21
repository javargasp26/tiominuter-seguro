package com.javargasp.tiominutermvi.domain.traininglog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class LocalDateProviderTest {

    private fun provider(nowMillis: Long, zone: String) =
        LocalDateProvider(clock = { nowMillis }, timeZoneProvider = { TimeZone.getTimeZone(zone) })

    @Test
    fun `the same instant is a different local date in different zones`() {
        // 2026-10-19T23:30:00Z
        val instant = 1_792_452_600_000L

        assertEquals("2026-10-19", provider(instant, "UTC").today())
        assertEquals("2026-10-20", provider(instant, "Europe/Madrid").today()) // UTC+2 (summer time)
        assertEquals("2026-10-19", provider(instant, "America/Bogota").today()) // UTC-5
        assertEquals("2026-10-20", provider(instant, "Pacific/Kiritimati").today()) // UTC+14
    }

    @Test
    fun `local midnight starts the new date`() {
        // 2026-10-19T22:00:00Z is 00:00 on 20 October in Madrid
        val midnight = 1_792_447_200_000L
        val madrid = provider(midnight, "Europe/Madrid")

        assertEquals("2026-10-20", madrid.dateOf(midnight))
        assertEquals("2026-10-19", madrid.dateOf(midnight - 1))
    }

    @Test
    fun `today and yesterday come from the fixed clock`() {
        // 2026-10-19T10:00:00Z
        val provider = provider(1_792_404_000_000L, "UTC")

        assertEquals("2026-10-19", provider.today())
        assertEquals("2026-10-18", provider.todayPlusDays(-1))
        assertEquals("2026-10-20", provider.todayPlusDays(1))
    }

    @Test
    fun `yesterday is a calendar day across a daylight saving change`() {
        // 2026-10-25T23:30:00Z is 00:30 on 26 October in Madrid; 25 October had 25 hours
        val provider = provider(1_792_971_000_000L, "Europe/Madrid")

        assertEquals("2026-10-26", provider.today())
        assertEquals("2026-10-25", provider.todayPlusDays(-1))
    }

    @Test
    fun `yesterday crosses month and year boundaries`() {
        // 2027-01-01T10:00:00Z
        val provider = provider(1_798_797_600_000L, "UTC")

        assertEquals("2026-12-31", provider.todayPlusDays(-1))
    }

    @Test
    fun `the zone is read on every conversion`() {
        var zone = TimeZone.getTimeZone("UTC")
        val provider = LocalDateProvider(clock = { 1_792_452_600_000L }, timeZoneProvider = { zone })
        assertEquals("2026-10-19", provider.today())

        zone = TimeZone.getTimeZone("Asia/Tokyo")

        assertEquals("2026-10-20", provider.today())
    }

    @Test
    fun `week helpers start weeks on Monday`() {
        // 2026-10-22T10:00:00Z, a Thursday
        val provider = provider(1_792_663_200_000L, "UTC")

        assertEquals("2026-10-19", provider.currentWeekStart())
        assertEquals("2026-10-19", provider.mondayOf("2026-10-25"))
        assertEquals(7, provider.weekDates("2026-10-22").size)
        assertEquals("2026-10-25", provider.weekDates("2026-10-22").last())
        assertEquals("2026-10-12", provider.previousWeek("2026-10-22"))
        assertEquals("2026-10-26", provider.nextWeek("2026-10-19"))
    }

    @Test
    fun `the current week depends on the device zone around Sunday midnight`() {
        // 2026-10-25T23:30:00Z: still Sunday in UTC, already Monday 00:30 in Madrid
        val instant = 1_792_971_000_000L

        assertEquals("2026-10-19", provider(instant, "UTC").currentWeekStart())
        assertEquals("2026-10-26", provider(instant, "Europe/Madrid").currentWeekStart())
    }

    @Test
    fun `local times convert to instants in the device zone`() {
        val madrid = provider(1_792_663_200_000L, "Europe/Madrid")

        // 21 October 19:30 in Madrid (UTC+2) is 17:30Z
        assertEquals(1_792_603_800_000L, madrid.epochMillisAt("2026-10-21", 19, 30))
        assertEquals(1_792_533_600_000L, madrid.startOfDay("2026-10-21"))
        assertEquals(TimeOfDay(19, 30), madrid.timeOfDay(1_792_603_800_000L))
        assertEquals(TimeOfDay(17, 30), provider(0, "UTC").timeOfDay(1_792_603_800_000L))
    }

    @Test
    fun `start of day around a daylight saving change`() {
        val madrid = provider(1_792_663_200_000L, "Europe/Madrid")

        // 26 October starts at UTC+1 (23:00Z on the 25th), the 25th at UTC+2 (22:00Z on the 24th)
        assertEquals(25 * 60 * 60 * 1000L, madrid.startOfDay("2026-10-26") - madrid.startOfDay("2026-10-25"))
    }
}
