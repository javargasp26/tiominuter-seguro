package com.javargasp.tiominutermvi.domain.traininglog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GymRefIdTest {

    @Test
    fun `round trip of a gym refId`() {
        assertEquals("gym:64", gymRefId(64))
        assertEquals(64, gymRoutineIdOf(gymRefId(64)))
    }

    @Test
    fun `other refIds have no gym routine`() {
        assertNull(gymRoutineIdOf(null))
        assertNull(gymRoutineIdOf("daily:1"))
        assertNull(gymRoutineIdOf("program:1:1:0"))
        assertNull(gymRoutineIdOf("gym:"))
        assertNull(gymRoutineIdOf("gym:abc"))
    }
}
