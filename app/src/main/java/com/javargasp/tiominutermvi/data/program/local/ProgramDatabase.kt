package com.javargasp.tiominutermvi.data.program.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.javargasp.tiominutermvi.data.program.local.dao.ProgramDao
import com.javargasp.tiominutermvi.data.program.local.dao.ProgramProgressDao
import com.javargasp.tiominutermvi.data.program.local.dao.ProgramSessionDao
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramCardioSessionEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramSlotProgressEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramStrengthSessionEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import com.javargasp.tiominutermvi.data.program.mapper.toProgramWorkoutOrNull
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.session.SessionRules
import java.util.UUID
import com.javargasp.tiominutermvi.data.sync.SyncOutboxEntity
import com.javargasp.tiominutermvi.data.sync.SyncOutboxDao
import com.javargasp.tiominutermvi.data.program.local.dao.ProgramSyncDao

/**
 * Database of the Program feature, separate from TioMinuterDatabase (file [DATABASE_NAME]).
 * Schemas are exported to app/schemas/ by the Room Gradle plugin; never edit an exported one.
 *
 * Versions:
 * - 1: programs, program_workouts, program_slot_progress, program_workout_progress.
 * - 2: adds programs.focus (TEXT NOT NULL DEFAULT 'BALANCED'; AutoMigration, existing
 *   programs read as BALANCED).
 * - 3: adds program_strength_session and program_cardio_session (AutoMigration with
 *   [Migration2To3], new tables only); strength workouts with progress in flight get a session
 *   derived from their slot times.
 * - 4: adds programs.uuid (TEXT NOT NULL DEFAULT '', indexed) and sync_outbox (AutoMigration with
 *   [Migration3To4]); every existing program gets a random lowercase uuid (its Firestore id).
 */
@Database(
    entities = [
        ProgramEntity::class,
        ProgramWorkoutEntity::class,
        ProgramSlotProgressEntity::class,
        ProgramWorkoutProgressEntity::class,
        ProgramStrengthSessionEntity::class,
        ProgramCardioSessionEntity::class,
        SyncOutboxEntity::class
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = ProgramDatabase.Migration2To3::class),
        AutoMigration(from = 3, to = 4, spec = ProgramDatabase.Migration3To4::class)
    ]
)
abstract class ProgramDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    abstract fun programProgressDao(): ProgramProgressDao
    abstract fun programSessionDao(): ProgramSessionDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun programSyncDao(): ProgramSyncDao

    /**
     * Every program without a uuid gets a random lowercase one (UUID.randomUUID()). Idempotent:
     * only rows still at '' are touched.
     */
    class Migration3To4 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val ids = mutableListOf<Long>()
            db.query("SELECT id FROM programs WHERE uuid = ''").use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
            ids.forEach { id ->
                db.execSQL("UPDATE programs SET uuid = ? WHERE id = ? AND uuid = ''", arrayOf<Any>(UUID.randomUUID().toString(), id))
            }
        }
    }

    /**
     * Strength workouts with slots completed before sessions existed, but not all of them, get a
     * session from the slot times: the latest run without a gap over the inactivity timeout
     * ([SessionRules.latestRun]), as the gym's v7 migration does. A workout with every slot done
     * is skipped (it was recorded when completed).
     */
    class Migration2To3 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val timesByWorkout = mutableMapOf<Triple<Long, Int, Int>, MutableList<Pair<Int, Long>>>()
            db.query("SELECT programId, week, dayIndex, position, completedAt FROM program_slot_progress").use { cursor ->
                while (cursor.moveToNext()) {
                    val key = Triple(cursor.getLong(0), cursor.getInt(1), cursor.getInt(2))
                    timesByWorkout.getOrPut(key) { mutableListOf() } += cursor.getInt(3) to cursor.getLong(4)
                }
            }
            for ((key, slots) in timesByWorkout) {
                val (programId, week, dayIndex) = key
                val payload = db.query(
                    "SELECT payload FROM program_workouts WHERE programId = ? AND week = ? AND dayIndex = ?",
                    arrayOf<Any>(programId, week, dayIndex)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                val workout = payload?.toProgramWorkoutOrNull() as? ProgramWorkout.Strength ?: continue
                val done = slots.map { it.first }.toSet()
                if (workout.slots.isNotEmpty() && workout.slots.all { it.position in done }) continue
                val (start, last) = SessionRules.latestRun(slots.map { it.second }) ?: continue
                db.execSQL(
                    "INSERT INTO program_strength_session (programId, week, dayIndex, sessionStartedAt, lastActivityAt) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>(programId, week, dayIndex, start, last)
                )
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "programs.db"
    }
}
