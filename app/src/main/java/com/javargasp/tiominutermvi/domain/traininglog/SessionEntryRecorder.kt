package com.javargasp.tiominutermvi.domain.traininglog

/**
 * Records an entry whose times are given instead of "now": [completedAt] is when it ended and
 * its date (`dateLocal`) is the local date of [dateOfMillis]. Used by the gym session, which can
 * close a session hours after its last set. Returns the new entry's id.
 */
fun interface SessionEntryRecorder {
    suspend fun recordSession(
        source: TrainingSource,
        refId: String?,
        title: String,
        durationSeconds: Int,
        completedAt: Long,
        dateOfMillis: Long
    ): Long
}
