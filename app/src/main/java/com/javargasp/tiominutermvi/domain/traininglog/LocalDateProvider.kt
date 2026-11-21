package com.javargasp.tiominutermvi.domain.traininglog

import com.javargasp.tiominutermvi.domain.time.WallClock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Local calendar dates ("yyyy-MM-dd") in the device time zone, without java.time
 * (minSdk 24, no desugaring). Week helpers (weeks start on Monday) delegate to [CalendarDates]. The zone is read on every call, so a zone change applies
 * to the next conversion; tests inject a fixed [clock] and [timeZoneProvider].
 */
class LocalDateProvider(
    private val clock: WallClock,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() }
) {

    /** The local date of [epochMillis]. */
    fun dateOf(epochMillis: Long): String = formatter().format(epochMillis)

    /** Today's local date. */
    fun today(): String = dateOf(clock.nowMillis())

    /** The local date [days] calendar days after (or before, when negative) today. */
    fun todayPlusDays(days: Int): String {
        val calendar = Calendar.getInstance(timeZoneProvider(), Locale.ROOT)
        calendar.timeInMillis = clock.nowMillis()
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return dateOf(calendar.timeInMillis)
    }

    /** The Monday of the week that contains [date] (weeks start on Monday). */
    fun mondayOf(date: String): String = CalendarDates.mondayOf(date)

    /** The Monday of the current week. */
    fun currentWeekStart(): String = mondayOf(today())

    /** The 7 dates, Monday to Sunday, of the week that contains [date]. */
    fun weekDates(date: String): List<String> = CalendarDates.weekDates(date)

    /** The Monday of the week before the one that contains [date]. */
    fun previousWeek(date: String): String = CalendarDates.plusWeeks(date, -1)

    /** The Monday of the week after the one that contains [date]. */
    fun nextWeek(date: String): String = CalendarDates.plusWeeks(date, 1)

    /** The current wall-clock time in epoch milliseconds. */
    fun nowMillis(): Long = clock.nowMillis()

    /** 00:00 of local [date] in the device zone, in epoch milliseconds. */
    fun startOfDay(date: String): Long = epochMillisAt(date, 0, 0)

    /**
     * Local [date] at [hour]:[minute] in the device zone, in epoch milliseconds. A time that
     * doesn't exist (skipped by a daylight saving change) moves forward, as Calendar does.
     */
    fun epochMillisAt(date: String, hour: Int, minute: Int): Long {
        val parsed = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply { timeZone = timeZoneProvider() }
            .parse(date) ?: throw IllegalArgumentException("Not a yyyy-MM-dd date: $date")
        val calendar = Calendar.getInstance(timeZoneProvider(), Locale.ROOT)
        calendar.time = parsed
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** The local time of day of [epochMillis] in the device zone. */
    fun timeOfDay(epochMillis: Long): TimeOfDay {
        val calendar = Calendar.getInstance(timeZoneProvider(), Locale.ROOT)
        calendar.timeInMillis = epochMillis
        return TimeOfDay(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun formatter(): SimpleDateFormat {
        val zone = timeZoneProvider()
        return SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply { timeZone = zone }
    }

    companion object {
        const val DATE_PATTERN = "yyyy-MM-dd"
    }
}

/** A local time of day (hour 0..23, minute 0..59). */
data class TimeOfDay(val hour: Int, val minute: Int)
