package com.javargasp.tiominutermvi.domain.program.repository

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.session.ClosedSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Programs of the Program feature. A program is generated once, at creation, and
 * stored; later template changes never alter it.
 */
interface ProgramRepository {

    /** Every program, newest first, with its overall progress. */
    fun observePrograms(): Flow<List<ProgramSummary>>

    /** One program with weeks, workouts and completion; null when it doesn't exist. */
    fun observeProgram(programId: Long): Flow<ProgramDetail?>

    /** One workout with its completion state; null when it doesn't exist. */
    fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?>

    /** Generates the program without storing it (preview). */
    suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram

    /** Generates and stores the program in one transaction; returns its id. */
    suspend fun createProgram(profile: ProgramProfile): Long

    suspend fun deleteProgram(programId: Long)

    /**
     * Marks one exercise of a strength workout as done. When every exercise of the
     * workout is done, the workout is marked completed in the same transaction; when
     * this call is the one that completes it, a PROGRAM entry is recorded in the
     * training history (duration from the first to the last exercise completion).
     */
    suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int)

    /** Clears the exercises done in a strength workout; the workout's completion is kept. */
    suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int)

    /**
     * Marks a workout completed (cardio workouts, or doing a workout again) and records
     * a PROGRAM entry in the training history with [durationSeconds] (the screen's time).
     */
    suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?)

    /** The program exercise catalog by exerciseId, for names and animations. */
    suspend fun getExercises(): Map<Int, ProgramExercise>

    /** When a program becomes low impact (for the create form's note). */
    suspend fun getImpactRules(): ImpactRules

    /**
     * The training-history entry recorded by the latest completion of this workout in the
     * current process (to attach a photo to it), or null: none yet, or recording failed.
     * It becomes null when a new completion starts and gets the new id once recorded.
     * The default (fakes) never knows an entry.
     */
    fun observeCompletionEntryId(programId: Long, week: Int, dayIndex: Int): Flow<Long?> = flowOf(null)

    // --- Sessions (see `domain/session`); the defaults (fakes) do nothing ---

    /**
     * A set of this strength workout was completed now: an inactive session is closed first
     * (recorded, the workout stays resumable), then this set starts or extends the session.
     */
    suspend fun onStrengthSetCompleted(programId: Long, week: Int, dayIndex: Int) = Unit

    /** Closes every program session (strength and cardio) inactive for more than 2 hours. */
    suspend fun closeInactiveSessions() = Unit

    /** Sessions closed for inactivity not announced yet; each one only once. */
    fun takeClosedSessions(): List<ClosedSession> = emptyList()
}
