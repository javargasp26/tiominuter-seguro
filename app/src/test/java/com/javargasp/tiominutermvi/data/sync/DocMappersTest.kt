package com.javargasp.tiominutermvi.data.sync

import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import com.javargasp.tiominutermvi.data.program.sync.ProgramCompletionDocMapper
import com.javargasp.tiominutermvi.data.program.sync.ProgramDocMapper
import com.javargasp.tiominutermvi.data.program.sync.ProgramWorkoutDocMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every document has exactly the fields of docs/firestore-model.md (the rules' required +
 * optional keys, without updatedAt / deleted / schemaVersion, which the remote adds), and reads
 * back into the same row. Numbers come back from Firestore as Long / Double.
 */
class DocMappersTest {

    private val entry = TrainingLogEntity(
        id = 7, uuid = "3f2c8a1e-5b7d-4c9a-8e21-0d6f4b3a9c10", dateLocal = "2026-10-20", completedAt = 1_000L,
        source = "ACTIVITY", refId = null, title = "", durationSeconds = 1_800, activityType = "RUNNING",
        intensity = "MODERATE", note = "Easy run", photoPath = "training_photos/x.jpg", createdAt = 900L, updatedAt = 950L
    )

    @Test
    fun `a training entry has the model's fields, never the photo or the local ids`() {
        val fields = TrainingLogDocMapper.toFields(entry, remoteRefId = null)

        assertEquals(
            setOf("entryId", "dateLocal", "completedAt", "source", "refId", "title", "durationSeconds", "activityType",
                "intensity", "note", "createdAt"),
            fields.keys
        )
        // Back from Firestore (Int → Long): the same row, with the local id and photo kept
        val back = TrainingLogDocMapper.toEntity(entry.uuid, fields.asFirestore(), entry, null, updatedAtMillis = 2_000L)
        assertEquals(entry.copy(updatedAt = 2_000L), back)
    }

    @Test
    fun `a pulled entry new on this device has no photo and a new id`() {
        val back = TrainingLogDocMapper.toEntity(entry.uuid, TrainingLogDocMapper.toFields(entry, null).asFirestore(), null, null, 2_000L)
        assertEquals(0L, back?.id)
        assertNull(back?.photoPath)
    }

    @Test
    fun `a weekly goal has its week and value`() {
        val goal = WeeklyGoalEntity("2026-10-19", 4, 1_000L)
        val fields = WeeklyGoalDocMapper.toFields(goal)

        assertEquals(setOf("effectiveFromWeek", "workoutsPerWeek"), fields.keys)
        assertEquals(goal.copy(updatedAt = 5L), WeeklyGoalDocMapper.toEntity("2026-10-19", fields.asFirestore(), 5L))
    }

    @Test
    fun `the daily progress has only its day and cycle (no id field, no local time)`() {
        val fields = DailyProgressDocMapper.toFields(DailyProgressEntity(lastCompletedDay = 12, cycle = 2, updatedAt = 1_000L))

        assertEquals(mapOf("lastCompletedDay" to 12, "cycle" to 2), fields)
        assertEquals(DailyProgress(12, 2), DailyProgressDocMapper.toDomain(fields.asFirestore()))
    }

    @Test
    fun `an out-of-range or incomplete daily progress is ignored`() {
        assertNull(DailyProgressDocMapper.toDomain(mapOf("lastCompletedDay" to 1000L, "cycle" to 0L)))
        assertNull(DailyProgressDocMapper.toDomain(mapOf("lastCompletedDay" to -1L, "cycle" to 0L)))
        assertNull(DailyProgressDocMapper.toDomain(mapOf("lastCompletedDay" to 3L, "cycle" to -1L)))
        assertNull(DailyProgressDocMapper.toDomain(mapOf("lastCompletedDay" to "3", "cycle" to 0L)))
        assertNull(DailyProgressDocMapper.toDomain(mapOf("cycle" to 0L)))
        assertEquals(DailyProgress(999, 0), DailyProgressDocMapper.toDomain(mapOf("lastCompletedDay" to 999L, "cycle" to 0L)))
    }

    @Test
    fun `a program uses its uuid, never its local id`() {
        val program = ProgramEntity(
            id = 12, goal = "GAIN_MUSCLE", level = "BEGINNER", location = "GYM", weeks = 6, daysPerWeek = 3, age = 30,
            weightKg = 70.5, heightCm = 175, createdAt = 1_000L, focus = "BALANCED", uuid = "9a1b2c3d-4e5f-4a6b-8c7d-112233445566"
        )
        val fields = ProgramDocMapper.toFields(program)

        assertEquals(
            setOf("programId", "goal", "level", "location", "focus", "weeks", "daysPerWeek", "age", "weightKg", "heightCm", "createdAt"),
            fields.keys
        )
        assertEquals(program.uuid, fields["programId"])
        assertEquals(program.copy(id = 3), ProgramDocMapper.toEntity(program.uuid, fields.asFirestore(), localId = 3))
    }

    @Test
    fun `workouts and completions are keyed by week and day`() {
        val workout = ProgramWorkoutEntity(12, 2, 1, "STRENGTH", false, "{\"kind\":\"STRENGTH\"}")
        val completion = ProgramWorkoutProgressEntity(12, 2, 1, 3_000L)

        val workoutFields = ProgramWorkoutDocMapper.toFields(workout)
        val completionFields = ProgramCompletionDocMapper.toFields(completion)

        assertEquals(setOf("workoutId", "week", "dayIndex", "kind", "isDeload", "payload"), workoutFields.keys)
        assertEquals(setOf("workoutId", "week", "dayIndex", "completedAt"), completionFields.keys)
        assertEquals("2-1", workoutFields["workoutId"])
        assertEquals(workout.copy(programId = 4), ProgramWorkoutDocMapper.toEntity(4, workoutFields.asFirestore()))
        assertEquals(completion.copy(programId = 4), ProgramCompletionDocMapper.toEntity(4, completionFields.asFirestore()))
    }

    @Test
    fun `an unreadable document is skipped, not guessed`() {
        assertNull(TrainingLogDocMapper.toEntity("u", mapOf("title" to "x"), null, null, 1L))
        assertNull(ProgramDocMapper.toEntity("u", mapOf("goal" to "GAIN_MUSCLE"), 0))
    }

    /** What Firestore gives back: every integer as Long, every other number as Double. */
    private fun Map<String, Any?>.asFirestore(): Map<String, Any?> = mapValues { (_, value) ->
        when (value) {
            is Int -> value.toLong()
            is Float -> value.toDouble()
            else -> value
        }
    }
}
