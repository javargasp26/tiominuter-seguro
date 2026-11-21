package com.javargasp.tiominutermvi.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSecondsTest {

    @Test
    fun `zero seconds formats as 00_00`() {
        assertEquals("00:00", formatSeconds(0))
    }

    @Test
    fun `five seconds formats as 00_05`() {
        assertEquals("00:05", formatSeconds(5))
    }

    @Test
    fun `fifty nine seconds formats without minute rollover`() {
        assertEquals("00:59", formatSeconds(59))
    }

    @Test
    fun `sixty seconds formats as one minute`() {
        assertEquals("01:00", formatSeconds(60))
    }

    @Test
    fun `sixty five seconds formats as one minute five seconds`() {
        assertEquals("01:05", formatSeconds(65))
    }

    @Test
    fun `ten minutes formats correctly`() {
        assertEquals("10:00", formatSeconds(600))
    }

    @Test
    fun `one second before an hour formats as 59_59`() {
        assertEquals("59:59", formatSeconds(3599))
    }
}
