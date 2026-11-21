package com.javargasp.tiominutermvi.domain.traininglog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TrainingLogRecorderTest {

    @Test
    fun `duration between completions needs at least two`() {
        assertNull(durationBetweenCompletions(emptyList()))
        assertNull(durationBetweenCompletions(listOf(10_000L)))
        assertEquals(90, durationBetweenCompletions(listOf(100_000L, 10_000L, 50_000L)))
        assertEquals(0, durationBetweenCompletions(listOf(5_000L, 5_000L)))
    }

    @Test
    fun `recordSafely passes the values through`() = runTest {
        val recorder = RecordingRecorder()

        val id = recorder.recordSafely(TrainingSource.GYM, "gym:1", "Pull Day", 600)

        assertEquals(listOf("GYM gym:1 Pull Day 600"), recorder.calls)
        assertEquals(1L, id)
    }

    @Test
    fun `recordSafely swallows failures`() = runTest {
        val recorder = RecordingRecorder(error = IllegalStateException("disk full"))

        assertNull(recorder.recordSafely(TrainingSource.DAILY, "daily:1", "Day 1", null))
    }

    @Test
    fun `recordSafely still propagates cancellation`() = runTest {
        val recorder = RecordingRecorder(error = CancellationException("left"))

        try {
            recorder.recordSafely(TrainingSource.DAILY, "daily:1", "Day 1", null)
            fail("cancellation was swallowed")
        } catch (e: CancellationException) {
            assertEquals("left", e.message)
        }
    }

    private class RecordingRecorder(private val error: Exception? = null) : TrainingLogRecorder {
        /** Id returned for the next recorded entry. */
        var nextEntryId = 1L
        val calls = mutableListOf<String>()

        override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long {
            error?.let { throw it }
            calls.add("$source $refId $title $durationSeconds")
            return nextEntryId++
        }
    }
}
