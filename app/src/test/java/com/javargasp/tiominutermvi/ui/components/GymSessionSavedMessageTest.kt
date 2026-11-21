package com.javargasp.tiominutermvi.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GymSessionSavedMessageTest {

    private fun format(seconds: Int) = formatSessionDuration(
        seconds,
        hoursAndMinutes = { hours, minutes -> "%d h %02d min".format(hours, minutes) },
        minutesOnly = { minutes -> "$minutes min" }
    )

    @Test
    fun `hours with two-digit minutes, or minutes alone`() {
        assertEquals("1 h 05 min", format(3_900))
        assertEquals("2 h 30 min", format(9_000))
        assertEquals("1 h 00 min", format(3_600))
        assertEquals("40 min", format(2_400))
        assertEquals("59 min", format(3_599))
    }

    @Test
    fun `under a minute shows 1 min, like the training history`() {
        assertEquals("1 min", format(0))
        assertEquals("1 min", format(59))
        assertEquals("1 min", format(-30))
    }
}
