package com.javargasp.tiominutermvi.data.program

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.mapper.toProfile
import com.javargasp.tiominutermvi.data.program.mapper.toProgramWorkoutOrNull
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Migration tests for [ProgramDatabase], run against the schemas exported to app/schemas.
 */
class ProgramMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProgramDatabase::class.java
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate1To2KeepsProgramsWorkoutsAndProgressAndReadsThemAsBalanced() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt) " +
                    "VALUES (7, 'LOSE_FAT', 'INTERMEDIATE', 'HOME', 6, 3, 34, 72.5, 170, 1000)"
            )
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 0, 'STRENGTH', 0, '$STRENGTH_PAYLOAD')"
            )
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 1, 'OUTDOOR_RUN', 0, '$RUN_PAYLOAD')"
            )
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 0, 2000)")
            execSQL("INSERT INTO program_workout_progress (programId, week, dayIndex, completedAt) VALUES (7, 1, 1, 3000)")
            close()
        }

        // Validates the migrated schema against 2.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        db.query("SELECT goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt, focus FROM programs WHERE id = 7")
            .use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("LOSE_FAT", cursor.getString(0))
                assertEquals("INTERMEDIATE", cursor.getString(1))
                assertEquals("HOME", cursor.getString(2))
                assertEquals(6, cursor.getInt(3))
                assertEquals(3, cursor.getInt(4))
                assertEquals(34, cursor.getInt(5))
                assertEquals(72.5, cursor.getDouble(6), 0.0)
                assertEquals(170, cursor.getInt(7))
                assertEquals(1000L, cursor.getLong(8))
                assertEquals("BALANCED", cursor.getString(9))
            }
        db.query("SELECT payload FROM program_workouts WHERE programId = 7 ORDER BY dayIndex").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(STRENGTH_PAYLOAD, cursor.getString(0))
            cursor.moveToNext()
            assertEquals(RUN_PAYLOAD, cursor.getString(0))
        }
        db.query("SELECT position, completedAt FROM program_slot_progress WHERE programId = 7").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals(2000L, cursor.getLong(1))
        }
        assertEquals(1, db.count("program_workout_progress"))
        db.close()
    }

    @Test
    fun migratedProgramReadsThroughRoomAsBalanced() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt) " +
                    "VALUES (7, 'GAIN_MUSCLE', 'BEGINNER', 'GYM', 4, 2, 30, 70.0, 175, 1000)"
            )
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 0, 'STRENGTH', 0, '$STRENGTH_PAYLOAD')"
            )
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 0, 2000)")
            close()
        }

        // The real database with its AutoMigration, as the app opens it after an update.
        val database = Room.databaseBuilder(context, ProgramDatabase::class.java, TEST_DB).build()
        try {
            runBlocking {
                val program = database.programDao().getProgram(7)!!
                assertEquals(
                    ProgramProfile(30, 70.0, 175, ProgramLevel.BEGINNER, ProgramGoal.GAIN_MUSCLE, ProgramLocation.GYM, 4, 2),
                    program.toProfile()
                )
                assertEquals(ProgramFocus.BALANCED, program.toProfile().focus)

                val workout = database.programDao().getWorkout(7, 1, 0)!!
                assertTrue(workout.payload.toProgramWorkoutOrNull() is ProgramWorkout.Strength)
                assertEquals(setOf(0), database.programProgressDao().getCompletedPositions(7, 1, 0).toSet())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate2To3AddsSessionTablesAndDerivesStrengthSessionsInFlight() {
        val day = 24 * 60 * 60 * 1000L
        val minute = 60 * 1000L
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt, focus) " +
                    "VALUES (7, 'GAIN_MUSCLE', 'BEGINNER', 'GYM', 6, 3, 30, 70.0, 175, 1000, 'BALANCED')"
            )
            // (1, 0): 4 slots, 3 done (one days earlier): in flight
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 0, 'STRENGTH', 0, '$STRENGTH_PAYLOAD_4')"
            )
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 0, 1000)")
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 1, ${3 * day})")
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 2, ${3 * day + 50 * minute})")
            // (1, 1): its only slot done (recorded when completed): no session
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 1, 'STRENGTH', 0, '$STRENGTH_PAYLOAD')"
            )
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 1, 0, 2000)")
            execSQL("INSERT INTO program_workout_progress (programId, week, dayIndex, completedAt) VALUES (7, 1, 1, 2000)")
            // (1, 2): a run, no progress
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 2, 'OUTDOOR_RUN', 0, '$RUN_PAYLOAD')"
            )
            close()
        }

        // Validates the migrated schema against 3.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT programId, week, dayIndex, sessionStartedAt, lastActivityAt FROM program_strength_session").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(7L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            // The latest run: the slot done days earlier doesn't stretch it
            assertEquals(3 * day, cursor.getLong(3))
            assertEquals(3 * day + 50 * minute, cursor.getLong(4))
        }
        assertEquals(0, db.count("program_cardio_session"))
        assertEquals(4, db.count("program_slot_progress"))
        assertEquals(1, db.count("program_workout_progress"))

        // Sessions go with their program
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM programs WHERE id = 7")
        assertEquals(0, db.count("program_strength_session"))
        db.close()
    }

    @Test
    fun migrate1To3RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt) " +
                    "VALUES (7, 'LOSE_FAT', 'INTERMEDIATE', 'HOME', 6, 3, 34, 72.5, 170, 1000)"
            )
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 0, 'STRENGTH', 0, '$STRENGTH_PAYLOAD_4')"
            )
            execSQL("INSERT INTO program_slot_progress (programId, week, dayIndex, position, completedAt) VALUES (7, 1, 0, 0, 2000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT focus FROM programs WHERE id = 7").use { cursor ->
            cursor.moveToFirst()
            assertEquals("BALANCED", cursor.getString(0))
        }
        db.query("SELECT sessionStartedAt, lastActivityAt FROM program_strength_session").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(2000L, cursor.getLong(0))
            assertEquals(2000L, cursor.getLong(1))
        }
        db.close()
    }

    @Test
    fun migrate3To4GivesEveryProgramARandomLowercaseUuidAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 3).apply {
            for (id in listOf(7, 8, 9)) {
                execSQL(
                    "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt, focus) " +
                        "VALUES ($id, 'GAIN_MUSCLE', 'BEGINNER', 'GYM', 6, 3, 30, 70.0, 175, 1000, 'BALANCED')"
                )
            }
            execSQL(
                "INSERT INTO program_workouts (programId, week, dayIndex, kind, isDeload, payload) " +
                    "VALUES (7, 1, 0, 'STRENGTH', 0, '$STRENGTH_PAYLOAD')"
            )
            execSQL("INSERT INTO program_workout_progress (programId, week, dayIndex, completedAt) VALUES (7, 1, 0, 2000)")
            close()
        }

        // Validates the migrated schema against 4.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        val uuids = db.query("SELECT uuid FROM programs ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(3, uuids.size)
        assertEquals(3, uuids.toSet().size)
        val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue(uuids.all { uuidPattern.matches(it) })
        assertEquals(1, db.count("program_workouts"))
        assertEquals(1, db.count("program_workout_progress"))
        assertEquals(0, db.count("sync_outbox"))

        // Idempotent: running the backfill again changes nothing
        ProgramDatabase.Migration3To4().onPostMigrate(db)
        val again = db.query("SELECT uuid FROM programs ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(uuids, again)
        db.close()
    }

    @Test
    fun migrate1To4RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO programs (id, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt) " +
                    "VALUES (7, 'LOSE_FAT', 'INTERMEDIATE', 'HOME', 6, 3, 34, 72.5, 170, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.query("SELECT focus, uuid FROM programs WHERE id = 7").use { cursor ->
            cursor.moveToFirst()
            assertEquals("BALANCED", cursor.getString(0))
            assertEquals(36, cursor.getString(1).length)
        }
        db.close()
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "program-migration-test"

        const val STRENGTH_PAYLOAD =
            """{"kind":"STRENGTH","title":"FULL_BODY_A","slots":[{"position":0,"exerciseId":2,"type":"REPS","reps":[12,12],"restSeconds":45}]}"""
        const val RUN_PAYLOAD = """{"kind":"OUTDOOR_RUN","distanceKm":3.0,"walk":false}"""
        const val STRENGTH_PAYLOAD_4 =
            """{"kind":"STRENGTH","title":"FULL_BODY_A","slots":[""" +
                """{"position":0,"exerciseId":2,"type":"REPS","reps":[12,12],"restSeconds":45},""" +
                """{"position":1,"exerciseId":2,"type":"REPS","reps":[12,12],"restSeconds":45},""" +
                """{"position":2,"exerciseId":2,"type":"REPS","reps":[12,12],"restSeconds":45},""" +
                """{"position":3,"exerciseId":2,"type":"REPS","reps":[12,12],"restSeconds":45}]}"""
    }
}
