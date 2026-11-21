package com.javargasp.tiominutermvi.domain.traininglog

import kotlinx.coroutines.CancellationException

/**
 * The only thing other features need to add an entry to the training history.
 * The entry is completed now (date, uuid and timestamps are set by the implementation).
 * Returns the new entry's id (e.g. to attach a photo to it).
 */
interface TrainingLogRecorder {
    suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long
}

/**
 * [TrainingLogRecorder.record] for workout flows: a failure is logged and swallowed,
 * so recording never breaks the workout. Cancellation is still propagated.
 * Returns the new entry's id, or null when recording failed.
 */
suspend fun TrainingLogRecorder.recordSafely(
    source: TrainingSource,
    refId: String?,
    title: String,
    durationSeconds: Int?
): Long? =
    try {
        record(source, refId, title, durationSeconds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // println like the mappers: android.util.Log is not available in JVM tests
        println("TrainingLog: could not record $source $refId: ${e.message}")
        null
    }

/** Duration between the first and the last completion, or null with fewer than two. */
fun durationBetweenCompletions(completedAtMillis: List<Long>): Int? {
    if (completedAtMillis.size < 2) return null
    return ((completedAtMillis.max() - completedAtMillis.min()) / MILLIS_PER_SECOND).toInt()
}

internal const val MILLIS_PER_SECOND = 1000L
