package com.javargasp.tiominutermvi.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Migration tests for [TioMinuterDatabase], run against the schemas exported
 * to app/schemas.
 */
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TioMinuterDatabase::class.java
    )

    @Test
    fun migrate1To2KeepsGymDataAndAddsEmptyDailyTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            execSQL(
                "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                    "VALUES (64, 0, 18, 'REPS', '10,10,10,10', 45)"
            )
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            close()
        }

        // Validates the migrated schema against 2.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        db.query("SELECT routineName FROM gym_routines WHERE routineId = 64").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Pull Day", cursor.getString(0))
        }
        db.query(
            "SELECT exerciseId, type, reps, restSeconds FROM gym_routine_exercises " +
                "WHERE routineId = 64 AND position = 0"
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(18, cursor.getInt(0))
            assertEquals("REPS", cursor.getString(1))
            assertEquals("10,10,10,10", cursor.getString(2))
            assertEquals(45, cursor.getInt(3))
        }
        db.query("SELECT position, completedAt FROM gym_exercise_progress WHERE routineId = 64").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals(1000L, cursor.getLong(1))
        }
        assertEquals(1, db.count("exercises"))
        assertEquals(0, db.count("daily_routines"))
        assertEquals(0, db.count("daily_routine_exercises"))
        db.close()
    }

    @Test
    fun migrate2To3KeepsEverythingAndAddsAnEmptyTrainingLog() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (13, 'Rest', '13_rest.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            execSQL(
                "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                    "VALUES (64, 0, 18, 'REPS', '10,10,10,10', 45)"
            )
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            execSQL("INSERT INTO daily_routines (routineId, routineDay, title) VALUES (1, 'Day 1', NULL)")
            execSQL("INSERT INTO daily_routines (routineId, routineDay, title) VALUES (2, 'Day 2', 'Legs')")
            execSQL(
                "INSERT INTO daily_routine_exercises (routineId, position, exerciseId, type, amount) " +
                    "VALUES (1, 0, 18, 'REPS', 10)"
            )
            execSQL(
                "INSERT INTO daily_routine_exercises (routineId, position, exerciseId, type, amount) " +
                    "VALUES (1, 1, 13, 'TIME', 30)"
            )
            close()
        }

        // Validates the migrated schema against 3.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT routineName FROM gym_routines WHERE routineId = 64").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Pull Day", cursor.getString(0))
        }
        db.query("SELECT reps, restSeconds FROM gym_routine_exercises WHERE routineId = 64 AND position = 0").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("10,10,10,10", cursor.getString(0))
            assertEquals(45, cursor.getInt(1))
        }
        db.query("SELECT completedAt FROM gym_exercise_progress WHERE routineId = 64 AND position = 0").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1000L, cursor.getLong(0))
        }
        db.query("SELECT routineDay, title FROM daily_routines ORDER BY routineId").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("Day 1", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            cursor.moveToNext()
            assertEquals("Legs", cursor.getString(1))
        }
        db.query("SELECT exerciseId, type, amount FROM daily_routine_exercises WHERE routineId = 1 ORDER BY position").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToLast()
            assertEquals(13, cursor.getInt(0))
            assertEquals("TIME", cursor.getString(1))
            assertEquals(30, cursor.getInt(2))
        }
        assertEquals(2, db.count("exercises"))
        assertEquals(0, db.count("training_log"))
        db.close()
    }

    @Test
    fun migrate1To3RunsBothAutoMigrations() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("daily_routines"))
        assertEquals(0, db.count("training_log"))
        db.close()
    }

    @Test
    fun migrate3To4KeepsTheTrainingLogAndAddsAnEmptyWeeklyGoal() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            execSQL(
                "INSERT INTO training_log (id, uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "activityType, intensity, note, photoPath, createdAt, updatedAt) VALUES " +
                    "(1, 'uuid-1', '2026-10-19', 1792404000000, 'GYM', 'gym:64', 'Pull Day', 1800, " +
                    "NULL, NULL, NULL, NULL, 1792404000000, 1792404000000)"
            )
            execSQL(
                "INSERT INTO training_log (id, uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "activityType, intensity, note, photoPath, createdAt, updatedAt) VALUES " +
                    "(2, 'uuid-2', '2026-10-20', 1792490400000, 'DAILY', 'daily:1', 'Day 1', NULL, " +
                    "NULL, NULL, 'note', NULL, 1792490400000, 1792490400000)"
            )
            close()
        }

        // Validates the migrated schema against 4.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.query("SELECT uuid, dateLocal, source, refId, title, durationSeconds, note FROM training_log ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("uuid-1", cursor.getString(0))
            assertEquals("2026-10-19", cursor.getString(1))
            assertEquals("GYM", cursor.getString(2))
            assertEquals("gym:64", cursor.getString(3))
            assertEquals("Pull Day", cursor.getString(4))
            assertEquals(1800, cursor.getInt(5))
            cursor.moveToNext()
            assertTrue(cursor.isNull(5))
            assertEquals("note", cursor.getString(6))
        }
        assertEquals(1, db.count("gym_exercise_progress"))
        assertEquals(0, db.count("weekly_goal"))
        db.close()
    }

    @Test
    fun migrate1To4RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("training_log"))
        assertEquals(0, db.count("weekly_goal"))
        db.close()
    }

    @Test
    fun migrate4To5RenamesTheGoalColumnAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO weekly_goal (effectiveFromWeek, daysPerWeek, updatedAt) VALUES ('2026-10-12', 3, 1000)")
            execSQL("INSERT INTO weekly_goal (effectiveFromWeek, daysPerWeek, updatedAt) VALUES ('2026-10-19', 4, 2000)")
            execSQL(
                "INSERT INTO training_log (id, uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "activityType, intensity, note, photoPath, createdAt, updatedAt) VALUES " +
                    "(1, 'uuid-1', '2026-10-19', 1792404000000, 'GYM', 'gym:64', 'Pull Day', 1800, " +
                    "NULL, NULL, NULL, NULL, 1792404000000, 1792404000000)"
            )
            close()
        }

        // Validates the migrated schema against 5.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        // A 4-day goal becomes 4 workouts
        db.query("SELECT effectiveFromWeek, workoutsPerWeek, updatedAt FROM weekly_goal ORDER BY effectiveFromWeek").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("2026-10-12", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(1000L, cursor.getLong(2))
            cursor.moveToNext()
            assertEquals("2026-10-19", cursor.getString(0))
            assertEquals(4, cursor.getInt(1))
        }
        db.query("SELECT uuid, title, durationSeconds FROM training_log").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("uuid-1", cursor.getString(0))
            assertEquals("Pull Day", cursor.getString(1))
            assertEquals(1800, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    fun migrate1To5RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("training_log"))
        assertEquals(0, db.count("weekly_goal"))
        db.close()
    }

    @Test
    fun migrate5To6AddsNullSpanishNamesAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            execSQL(
                "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                    "VALUES (64, 0, 18, 'REPS', '10,10,10,10', 45)"
            )
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            execSQL("INSERT INTO daily_routines (routineId, routineDay, title) VALUES (1, 'Day 1', NULL)")
            execSQL("INSERT INTO weekly_goal (effectiveFromWeek, workoutsPerWeek, updatedAt) VALUES ('2026-10-12', 4, 2000)")
            close()
        }

        // Validates the migrated schema against 6.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true)

        db.query("SELECT name, animation, nameEs FROM exercises WHERE exerciseId = 18").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Pull-ups", cursor.getString(0))
            assertEquals("18_pull_ups.gif", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        db.query("SELECT routineName, routineNameEs FROM gym_routines WHERE routineId = 64").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Pull Day", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        assertEquals(1, db.count("gym_routine_exercises"))
        assertEquals(1, db.count("gym_exercise_progress"))
        assertEquals(1, db.count("daily_routines"))
        assertEquals(1, db.count("weekly_goal"))

        // The next refresh fills the new columns.
        db.execSQL("UPDATE exercises SET nameEs = 'Dominadas' WHERE exerciseId = 18")
        db.execSQL("UPDATE gym_routines SET routineNameEs = 'Día de tirón' WHERE routineId = 64")
        db.query("SELECT e.nameEs, r.routineNameEs FROM exercises e, gym_routines r").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Dominadas", cursor.getString(0))
            assertEquals("Día de tirón", cursor.getString(1))
        }
        db.close()
    }

    @Test
    fun migrate1To6RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true)

        assertEquals(1, db.count("exercises"))
        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("training_log"))
        assertEquals(0, db.count("weekly_goal"))
        db.query("SELECT nameEs FROM exercises").use { cursor ->
            cursor.moveToFirst()
            assertTrue(cursor.isNull(0))
        }
        db.close()
    }

    @Test
    fun migrate6To7AddsGymSessionsDerivedFromProgressInFlight() {
        val day = 24 * 60 * 60 * 1000L
        val minute = 60 * 1000L
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            for (routineId in listOf(64, 65, 66)) {
                execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES ($routineId, 'Routine $routineId')")
                for (position in 0..2) {
                    execSQL(
                        "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                            "VALUES ($routineId, $position, 18, 'REPS', '10,10', 60)"
                    )
                }
            }
            // 64: a fourth slot still to do; one exercise days ago, two more today 50 minutes apart (in flight)
            execSQL(
                "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                    "VALUES (64, 3, 18, 'REPS', '10,10', 60)"
            )
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 1, ${3 * day})")
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 2, ${3 * day + 50 * minute})")
            // 65: every slot completed (its entry was recorded; the dialog only resets it)
            for (position in 0..2) {
                execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (65, $position, ${position * minute})")
            }
            // 66: no progress
            execSQL(
                "INSERT INTO training_log (uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "createdAt, updatedAt) VALUES ('u1', '2026-10-19', 5, 'GYM', 'gym:64', 'Routine 64', 600, 5, 5)"
            )
            close()
        }

        // Validates the migrated schema against 7.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true)

        db.query("SELECT routineId, sessionStartedAt, lastSetAt FROM gym_session ORDER BY routineId").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(64, cursor.getInt(0))
            // The latest run of completions: the exercise of days ago doesn't stretch it
            assertEquals(3 * day, cursor.getLong(1))
            assertEquals(3 * day + 50 * minute, cursor.getLong(2))
        }
        assertEquals(6, db.count("gym_exercise_progress"))
        assertEquals(1, db.count("training_log"))
        assertEquals(3, db.count("gym_routines"))
        assertEquals(10, db.count("gym_routine_exercises"))

        // The session goes with its routine, like the progress
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM gym_routines WHERE routineId = 64")
        assertEquals(0, db.count("gym_session"))
        db.close()
    }

    @Test
    fun migrate1To7RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            for (position in 0..1) {
                execSQL(
                    "INSERT INTO gym_routine_exercises (routineId, position, exerciseId, type, reps, restSeconds) " +
                        "VALUES (64, $position, 18, 'REPS', '10,10', 60)"
                )
            }
            execSQL("INSERT INTO gym_exercise_progress (routineId, position, completedAt) VALUES (64, 0, 1000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(1, db.count("gym_exercise_progress"))
        // Half done: one session starting and ending at that completion
        db.query("SELECT routineId, sessionStartedAt, lastSetAt FROM gym_session").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(64, cursor.getInt(0))
            assertEquals(1000L, cursor.getLong(1))
            assertEquals(1000L, cursor.getLong(2))
        }
        db.close()
    }

    @Test
    fun migrate7To8AddsAnEmptyDailySessionAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            execSQL("INSERT INTO gym_session (routineId, sessionStartedAt, lastSetAt) VALUES (64, 1000, 2000)")
            execSQL("INSERT INTO daily_routines (routineId, routineDay, title) VALUES (1, 'Day 1', NULL)")
            execSQL(
                "INSERT INTO training_log (uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "createdAt, updatedAt) VALUES ('u1', '2026-10-19', 5, 'DAILY', 'daily:1', 'Day 1', 600, 5, 5)"
            )
            close()
        }

        // Validates the migrated schema against 8.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true)

        assertEquals(0, db.count("daily_session"))
        assertEquals(1, db.count("gym_session"))
        assertEquals(1, db.count("training_log"))
        assertEquals(1, db.count("daily_routines"))
        db.execSQL("INSERT INTO daily_session (routineId, startedAt, activeSeconds, lastActivityAt) VALUES (1, 1000, 600, 2000)")
        assertEquals(1, db.count("daily_session"))
        db.close()
    }

    @Test
    fun migrate1To8RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises (exerciseId, name, animation) VALUES (18, 'Pull-ups', '18_pull_ups.gif')")
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("gym_session"))
        assertEquals(0, db.count("daily_session"))
        db.close()
    }

    @Test
    fun migrate8To9AddsAnEmptyOutboxAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO training_log (uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "createdAt, updatedAt) VALUES ('u1', '2026-10-19', 5, 'DAILY', 'daily:1', 'Day 1', 600, 5, 5)"
            )
            execSQL("INSERT INTO weekly_goal (effectiveFromWeek, workoutsPerWeek, updatedAt) VALUES ('2026-10-19', 4, 1)")
            execSQL("INSERT INTO daily_session (routineId, startedAt, activeSeconds, lastActivityAt) VALUES (1, 1000, 600, 2000)")
            close()
        }

        // Validates the migrated schema against 9.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true)

        assertEquals(0, db.count("sync_outbox"))
        assertEquals(1, db.count("training_log"))
        assertEquals(1, db.count("weekly_goal"))
        assertEquals(1, db.count("daily_session"))
        db.execSQL(
            "INSERT INTO sync_outbox (collection, parentId, docId, isDelete, version, enqueuedAt) " +
                "VALUES ('TRAINING_LOG', '', 'u1', 0, 1, 10)"
        )
        assertEquals(1, db.count("sync_outbox"))
        db.close()
    }

    @Test
    fun migrate1To9RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("sync_outbox"))
        db.close()
    }

    @Test
    fun migrate9To10AddsAnEmptyFeedQueueAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO training_log (uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "createdAt, updatedAt) VALUES ('u1', '2026-10-19', 5, 'GYM', 'gym:64', 'Pull Day', 2700, 5, 5)"
            )
            execSQL(
                "INSERT INTO sync_outbox (collection, parentId, docId, isDelete, version, enqueuedAt) " +
                    "VALUES ('TRAINING_LOG', '', 'u1', 0, 1, 10)"
            )
            close()
        }

        // Validates the migrated schema against 10.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true)

        assertEquals(0, db.count("feed_share"))
        assertEquals(1, db.count("training_log"))
        assertEquals(1, db.count("sync_outbox"))
        db.execSQL(
            "INSERT INTO feed_share (entryUuid, ownerUid, share, entryDeleted, targetGroupIds, postedGroupIds, " +
                "refusedGroupIds, postedPhotoPath, createdAt, version, dirty) " +
                "VALUES ('u1', 'uid', 1, 0, NULL, '', '', NULL, 10, 1, 1)"
        )
        assertEquals(1, db.count("feed_share"))
        db.close()
    }

    @Test
    fun migrate1To10RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("feed_share"))
        db.close()
    }

    @Test
    fun migrate10To11AddsAnEmptyDailyProgressAndKeepsEverything() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO training_log (uuid, dateLocal, completedAt, source, refId, title, durationSeconds, " +
                    "createdAt, updatedAt) VALUES ('u1', '2026-10-19', 5, 'DAILY', 'daily:7', 'Day 7', 700, 5, 5)"
            )
            execSQL(
                "INSERT INTO sync_outbox (collection, parentId, docId, isDelete, version, enqueuedAt) " +
                    "VALUES ('TRAINING_LOG', '', 'u1', 0, 1, 10)"
            )
            execSQL(
                "INSERT INTO feed_share (entryUuid, ownerUid, share, entryDeleted, targetGroupIds, postedGroupIds, " +
                    "refusedGroupIds, postedPhotoPath, createdAt, version, dirty) " +
                    "VALUES ('u1', 'uid', 1, 0, NULL, '', '', NULL, 10, 1, 1)"
            )
            close()
        }

        // Validates the migrated schema against 11.json (droppedTables = true).
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true)

        // Empty: the first read creates the row from the history (DailyProgressRepositoryImpl)
        assertEquals(0, db.count("daily_progress"))
        assertEquals(1, db.count("training_log"))
        assertEquals(1, db.count("sync_outbox"))
        assertEquals(1, db.count("feed_share"))
        db.execSQL("INSERT INTO daily_progress (id, lastCompletedDay, cycle, updatedAt) VALUES (0, 7, 0, 10)")
        assertEquals(1, db.count("daily_progress"))
        db.close()
    }

    @Test
    fun migrate1To11RunsEveryAutoMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO gym_routines (routineId, routineName) VALUES (64, 'Pull Day')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true)

        assertEquals(1, db.count("gym_routines"))
        assertEquals(0, db.count("daily_progress"))
        db.close()
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
