package com.javargasp.tiominutermvi.data.program

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.javargasp.tiominutermvi.data.content.AssetBundledContent
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.remote.ProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import com.javargasp.tiominutermvi.data.program.repository.ProgramRepositoryImpl
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.kind
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder

/**
 * [ProgramRepositoryImpl] with an in-memory [ProgramDatabase] and the real program
 * assets (read through a counting wrapper of [AssetProgramRemoteDataSource]).
 * Completed workouts go to a fake [TrainingLogRecorder]; titles are the workout kind.
 */
@RunWith(AndroidJUnit4::class)
class ProgramRepositoryTest {

    private lateinit var database: ProgramDatabase
    private lateinit var remote: CountingRemote
    private lateinit var repository: ProgramRepositoryImpl
    private lateinit var recorder: FakeRecorder
    private var now = 1_000L

    private data class Recorded(val source: TrainingSource, val refId: String?, val title: String, val durationSeconds: Int?)

    /** An entry of an abandoned session: its duration, end and the moment its date comes from. */
    private data class SessionEntry(val refId: String?, val title: String, val durationSeconds: Int, val completedAt: Long, val dateOf: Long)

    private class FakeRecorder : TrainingLogRecorder, SessionEntryRecorder {
        /** Id returned for the next recorded entry. */
        var nextEntryId = 1L
        val recorded = mutableListOf<Recorded>()
        val sessionEntries = mutableListOf<SessionEntry>()
        var error: Exception? = null

        override suspend fun recordSession(
            source: TrainingSource,
            refId: String?,
            title: String,
            durationSeconds: Int,
            completedAt: Long,
            dateOfMillis: Long
        ): Long {
            assertEquals(TrainingSource.PROGRAM, source)
            sessionEntries.add(SessionEntry(refId, title, durationSeconds, completedAt, dateOfMillis))
            return nextEntryId++
        }

        override suspend fun record(source: TrainingSource, refId: String?, title: String, durationSeconds: Int?): Long {
            error?.let { throw it }
            recorded.add(Recorded(source, refId, title, durationSeconds))
            return nextEntryId++
        }
    }

    private fun newRepository() = ProgramRepositoryImpl(database, remote, { now }, recorder, { it.kind.name }, recorder)

    private class CountingRemote(private val delegate: ProgramRemoteDataSource) : ProgramRemoteDataSource {
        var templateCalls = 0
        var templatesOverride: ProgramTemplatesDto? = null

        override suspend fun fetchExercises(): List<ProgramExerciseDto> = delegate.fetchExercises()

        override suspend fun fetchTemplates(): ProgramTemplatesDto {
            templateCalls++
            return templatesOverride ?: delegate.fetchTemplates()
        }
    }

    private val gymProfile = ProgramProfile(
        age = 30, weightKg = 70.0, heightCm = 175, level = ProgramLevel.BEGINNER,
        goal = ProgramGoal.GAIN_MUSCLE, location = ProgramLocation.GYM, weeks = 6, daysPerWeek = 3
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProgramDatabase::class.java).build()
        remote = CountingRemote(AssetProgramRemoteDataSource(bundledContent(context)))
        recorder = FakeRecorder()
        repository = newRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun create_stores_every_week_and_workout() = runTest {
        val id = repository.createProgram(gymProfile)

        val detail = repository.observeProgram(id).first()!!
        assertEquals(gymProfile, detail.summary.profile)
        assertEquals(1_000L, detail.summary.createdAt)
        assertEquals(18, detail.summary.totalWorkouts)
        assertEquals(0, detail.summary.completedWorkouts)
        assertEquals((1..6).toList(), detail.weeks.map { it.weekNumber })
        assertEquals(listOf(4), detail.weeks.filter { it.isDeload }.map { it.weekNumber })
        assertTrue(detail.weeks.all { week -> week.workouts.size == 3 })
        assertEquals(repository.previewProgram(gymProfile).weeks[0].workouts, detail.weeks[0].workouts.map { it.workout })
    }

    @Test
    fun create_stores_the_focus_and_its_split() = runTest {
        val profile = gymProfile.copy(focus = ProgramFocus.LOWER_BODY)

        val id = repository.createProgram(profile)

        val detail = repository.observeProgram(id).first()!!
        assertEquals(profile, detail.summary.profile)
        assertEquals(ProgramFocus.LOWER_BODY, repository.observePrograms().first().single().profile.focus)
        assertEquals(
            listOf(StrengthDayTitle.LOWER_A, StrengthDayTitle.UPPER, StrengthDayTitle.LOWER_B),
            detail.weeks[0].workouts.map { (it.workout as ProgramWorkout.Strength).title }
        )
        assertEquals(repository.previewProgram(profile).weeks[0].workouts, detail.weeks[0].workouts.map { it.workout })
    }

    @Test
    fun catalog_and_templates_are_loaded_once() = runTest {
        repository.createProgram(gymProfile)
        repository.createProgram(gymProfile.copy(location = ProgramLocation.HOME))
        repository.getExercises()

        assertEquals(1, remote.templateCalls)
    }

    @Test
    fun stored_program_does_not_change_when_templates_change() = runTest {
        val id = repository.createProgram(gymProfile)
        val before = repository.observeProgram(id).first()

        // A new repository instance (new process) with different templates.
        val original = AssetProgramRemoteDataSource(
            bundledContent(ApplicationProvider.getApplicationContext())
        ).fetchTemplates()
        remote.templatesOverride = original.copy(prescriptions = original.prescriptions.map { it.copy(setsCompound = 5) })
        val other = newRepository()

        assertEquals(before, other.observeProgram(id).first())
    }

    @Test
    fun several_programs_coexist_and_delete_cascades() = runTest {
        val first = repository.createProgram(gymProfile)
        now = 2_000L
        val second = repository.createProgram(gymProfile.copy(goal = ProgramGoal.LOSE_FAT, location = ProgramLocation.HOME))
        repository.markWorkoutCompleted(first, 1, 0, null)

        assertEquals(listOf(second, first), repository.observePrograms().first().map { it.id })

        repository.deleteProgram(first)

        assertNull(repository.observeProgram(first).first())
        assertEquals(listOf(second), repository.observePrograms().first().map { it.id })
        assertTrue(database.programProgressDao().observeWorkouts(first).first().isEmpty())
    }

    @Test
    fun completing_every_slot_completes_the_strength_workout() = runTest {
        val id = repository.createProgram(gymProfile)
        val workout = repository.observeWorkout(id, 1, 0).first()!!
        val slots = (workout.workout as ProgramWorkout.Strength).slots

        slots.dropLast(1).forEach { repository.markSlotCompleted(id, 1, 0, it.position) }
        assertFalse(repository.observeWorkout(id, 1, 0).first()!!.isCompleted)

        repository.markSlotCompleted(id, 1, 0, slots.last().position)

        val done = repository.observeWorkout(id, 1, 0).first()!!
        assertTrue(done.isCompleted)
        assertEquals(slots.map { it.position }.toSet(), done.completedPositions)
        assertEquals(1, repository.observePrograms().first().single().completedWorkouts)
    }

    @Test
    fun reset_clears_slots_and_keeps_the_completion() = runTest {
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        slots.forEach { repository.markSlotCompleted(id, 1, 0, it.position) }

        repository.resetWorkout(id, 1, 0)

        val workout = repository.observeWorkout(id, 1, 0).first()!!
        assertTrue(workout.completedPositions.isEmpty())
        assertTrue(workout.isCompleted)
    }

    @Test
    fun cardio_workout_is_completed_explicitly() = runTest {
        val id = repository.createProgram(gymProfile.copy(goal = ProgramGoal.LOSE_FAT))
        val cardio = repository.observeWorkout(id, 1, 1).first()!!
        assertTrue(cardio.workout is ProgramWorkout.Treadmill)

        repository.markWorkoutCompleted(id, 1, 1, 1_800)

        assertTrue(repository.observeWorkout(id, 1, 1).first()!!.isCompleted)
        val week1 = repository.observeProgram(id).first()!!.weeks.first()
        assertEquals(1, week1.completedWorkouts)
    }

    @Test
    fun strength_auto_completion_records_one_entry_with_the_session_duration() = runTest {
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        slots.forEachIndexed { index, slot ->
            now = 10_000L + index * 60_000L
            // As the exercise screen does: the last set is noted, then the slot is marked
            repository.onStrengthSetCompleted(id, 1, 0)
            repository.markSlotCompleted(id, 1, 0, slot.position)
            if (index < slots.lastIndex) assertTrue(recorder.recorded.isEmpty())
        }

        assertEquals(
            listOf(Recorded(TrainingSource.PROGRAM, "program:$id:1:0", "STRENGTH", slots.lastIndex * 60)),
            recorder.recorded
        )

        // Marking an exercise of the completed workout again is not a new training
        repository.markSlotCompleted(id, 1, 0, slots.first().position)
        assertEquals(1, recorder.recorded.size)
        // The completion took the session: nothing is left to close later
        now += 5 * HOUR
        repository.closeInactiveSessions()
        assertTrue(recorder.sessionEntries.isEmpty())
    }

    @Test
    fun an_abandoned_strength_workout_is_recorded_once_and_stays_resumable() = runTest {
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        now = 10 * MINUTE
        repository.onStrengthSetCompleted(id, 1, 0)
        now = 20 * MINUTE
        repository.onStrengthSetCompleted(id, 1, 0)
        repository.markSlotCompleted(id, 1, 0, slots.first().position)
        now = 50 * MINUTE
        repository.onStrengthSetCompleted(id, 1, 0)

        now = 50 * MINUTE + 2 * HOUR - MINUTE
        repository.closeInactiveSessions()
        assertTrue(recorder.sessionEntries.isEmpty())
        now = 50 * MINUTE + 2 * HOUR + MINUTE
        repository.closeInactiveSessions()
        repository.closeInactiveSessions()

        assertEquals(
            listOf(SessionEntry("program:$id:1:0", "STRENGTH", 40 * 60, completedAt = 50 * MINUTE, dateOf = 10 * MINUTE)),
            recorder.sessionEntries
        )
        assertEquals(listOf(40 * 60), repository.takeClosedSessions().map { it.durationSeconds })
        // Resumable: the slot stays done and the workout is not completed
        val workout = repository.observeWorkout(id, 1, 0).first()!!
        assertEquals(setOf(slots.first().position), workout.completedPositions)
        assertFalse(workout.isCompleted)
        assertTrue(recorder.recorded.isEmpty())

        // Finishing it later is a new session
        now += HOUR
        slots.drop(1).forEach {
            repository.onStrengthSetCompleted(id, 1, 0)
            repository.markSlotCompleted(id, 1, 0, it.position)
        }
        assertEquals(1, recorder.recorded.size)
        assertEquals(0, recorder.recorded.single().durationSeconds)
    }

    @Test
    fun an_abandoned_cardio_workout_is_recorded_with_its_active_time_and_not_completed() = runTest {
        val id = repository.createProgram(gymProfile.copy(goal = ProgramGoal.LOSE_FAT))
        now = 10 * MINUTE
        repository.cardioSessions.start("program:$id:1:1")
        now = 30 * MINUTE
        repository.cardioSessions.update("program:$id:1:1", 900)

        now = 30 * MINUTE + 2 * HOUR + MINUTE
        repository.closeInactiveSessions()

        assertEquals(
            listOf(SessionEntry("program:$id:1:1", "TREADMILL", 900, completedAt = 30 * MINUTE, dateOf = 10 * MINUTE)),
            recorder.sessionEntries
        )
        assertFalse(repository.observeWorkout(id, 1, 1).first()!!.isCompleted)
    }

    @Test
    fun reset_and_delete_discard_sessions() = runTest {
        val id = repository.createProgram(gymProfile.copy(goal = ProgramGoal.LOSE_FAT))
        repository.onStrengthSetCompleted(id, 1, 0)
        repository.resetWorkout(id, 1, 0)
        repository.onStrengthSetCompleted(id, 1, 2)
        repository.cardioSessions.start("program:$id:1:1")

        repository.deleteProgram(id)
        now += 5 * HOUR
        repository.closeInactiveSessions()

        assertTrue(recorder.sessionEntries.isEmpty())
    }

    @Test
    fun doing_a_completed_strength_workout_again_records_a_new_entry() = runTest {
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        slots.forEach { repository.markSlotCompleted(id, 1, 0, it.position) }

        repository.resetWorkout(id, 1, 0)
        slots.forEach {
            repository.onStrengthSetCompleted(id, 1, 0)
            repository.markSlotCompleted(id, 1, 0, it.position)
        }

        assertEquals(2, recorder.recorded.size)
        // Every slot at the same instant: duration 0
        assertEquals(0, recorder.recorded.last().durationSeconds)
    }

    @Test
    fun cardio_completion_records_each_time_with_the_screen_duration() = runTest {
        val id = repository.createProgram(gymProfile.copy(goal = ProgramGoal.LOSE_FAT))

        repository.markWorkoutCompleted(id, 1, 1, 1_800)
        repository.markWorkoutCompleted(id, 1, 1, null)

        assertEquals(
            listOf(
                Recorded(TrainingSource.PROGRAM, "program:$id:1:1", "TREADMILL", 1_800),
                Recorded(TrainingSource.PROGRAM, "program:$id:1:1", "TREADMILL", null)
            ),
            recorder.recorded
        )
    }

    @Test
    fun each_completion_exposes_its_training_history_entry_for_a_photo() = runTest {
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots
        assertNull(repository.observeCompletionEntryId(id, 1, 0).first())

        recorder.nextEntryId = 41
        slots.forEach { repository.markSlotCompleted(id, 1, 0, it.position) }
        assertEquals(41L, repository.observeCompletionEntryId(id, 1, 0).first())

        recorder.nextEntryId = 77
        repository.markWorkoutCompleted(id, 1, 1, 600)
        assertEquals(77L, repository.observeCompletionEntryId(id, 1, 1).first())
        // Other workouts keep their own entry
        assertEquals(41L, repository.observeCompletionEntryId(id, 1, 0).first())

        // A completion that fails to record has no entry: the previous one is not offered
        recorder.error = IllegalStateException("history unavailable")
        repository.markWorkoutCompleted(id, 1, 1, 600)
        assertNull(repository.observeCompletionEntryId(id, 1, 1).first())
    }

    @Test
    fun a_recording_failure_does_not_fail_the_completion() = runTest {
        recorder.error = IllegalStateException("history unavailable")
        val id = repository.createProgram(gymProfile)
        val slots = (repository.observeWorkout(id, 1, 0).first()!!.workout as ProgramWorkout.Strength).slots

        slots.forEach { repository.markSlotCompleted(id, 1, 0, it.position) }
        repository.markWorkoutCompleted(id, 1, 1, 600)

        assertTrue(repository.observeWorkout(id, 1, 0).first()!!.isCompleted)
        assertTrue(repository.observeWorkout(id, 1, 1).first()!!.isCompleted)
        assertTrue(recorder.recorded.isEmpty())
    }

    @Test
    fun missing_program_or_workout_is_null() = runTest {
        assertNull(repository.observeProgram(99L).first())
        assertNull(repository.observeWorkout(99L, 1, 0).first())
    }

    @Test
    fun catalog_lookup_has_spanish_names_and_animations() = runTest {
        val exercises = repository.getExercises()

        assertEquals("Sentadilla búlgara", exercises.getValue(112).displayName(spanish = true))
        assertEquals("Bulgarian Split Squat", exercises.getValue(112).displayName(spanish = false))
        assertEquals("2_squat.gif", exercises.getValue(2).animation)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}

/** The bundled content only (the APK assets), as the app reads it with no download. */
private fun bundledContent(context: Context): ContentSource = object : ContentSource {
    private val bundled = AssetBundledContent(context.assets)
    override suspend fun readText(path: String): String =
        checkNotNull(bundled.open(path)) { "No asset $path" }.use { it.bufferedReader().readText() }
}
