package com.javargasp.tiominutermvi.data.local

import androidx.room.AutoMigration
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.javargasp.tiominutermvi.data.local.dao.DailyProgressDao
import com.javargasp.tiominutermvi.data.local.dao.DailyRoutineDao
import com.javargasp.tiominutermvi.data.local.dao.DailySessionDao
import com.javargasp.tiominutermvi.data.local.dao.ExerciseDao
import com.javargasp.tiominutermvi.data.local.dao.FeedShareDao
import com.javargasp.tiominutermvi.data.local.dao.GymProgressDao
import com.javargasp.tiominutermvi.data.local.dao.GymRoutineDao
import com.javargasp.tiominutermvi.data.local.dao.GymSessionDao
import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.DailyRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.DailySessionEntity
import com.javargasp.tiominutermvi.data.local.entity.ExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.FeedShareEntity
import com.javargasp.tiominutermvi.data.local.entity.GymExerciseProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineEntity
import com.javargasp.tiominutermvi.data.local.entity.GymRoutineExerciseEntity
import com.javargasp.tiominutermvi.data.local.entity.GymSessionEntity
import com.javargasp.tiominutermvi.data.local.entity.RepsListConverter
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionRules
import com.javargasp.tiominutermvi.data.sync.SyncOutboxEntity
import com.javargasp.tiominutermvi.data.sync.SyncOutboxDao
import com.javargasp.tiominutermvi.data.sync.AppSyncDao

/**
 * Room database (local source of truth) for the Gym Routines feature, the
 * daily-routine catalog, the training history and the weekly goals. Schema files are exported to app/schemas (see
 * room { schemaDirectory } in app/build.gradle.kts).
 *
 * Version history:
 * - 1: gym routines, their exercise slots, progress and the shared exercises table.
 * - 2: adds daily_routines and daily_routine_exercises (AutoMigration, new tables only).
 * - 3: adds training_log (AutoMigration, new table only).
 * - 4: adds weekly_goal (AutoMigration, new table only).
 * - 5: renames weekly_goal.daysPerWeek to workoutsPerWeek (AutoMigration with
 *   [Migration4To5]); stored values carry over.
 * - 6: adds the nullable Spanish names exercises.nameEs and gym_routines.routineNameEs
 *   (AutoMigration); they stay null until the next catalog refresh fills them.
 * - 7: adds gym_session (AutoMigration with [Migration6To7], new table only); routines with
 *   progress in flight get a session derived from their completion times.
 * - 8: adds daily_session (AutoMigration, new table only): the active time of the daily workout.
 * - 9: adds sync_outbox (AutoMigration, new table only): pending changes of the training log and
 *   the weekly goals for the Firestore sync.
 * - 10: adds feed_share (AutoMigration, new table only): the group feed's queue of workouts to post.
 * - 11: adds daily_progress (AutoMigration, new table only): the daily routines' unlock progress,
 *   created on its first read from the training history (DailyProgressRepositoryImpl).
 */
@Database(
    entities = [
        ExerciseEntity::class,
        GymRoutineEntity::class,
        GymRoutineExerciseEntity::class,
        GymExerciseProgressEntity::class,
        DailyRoutineEntity::class,
        DailyRoutineExerciseEntity::class,
        TrainingLogEntity::class,
        WeeklyGoalEntity::class,
        GymSessionEntity::class,
        DailySessionEntity::class,
        SyncOutboxEntity::class,
        FeedShareEntity::class,
        DailyProgressEntity::class
    ],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = TioMinuterDatabase.Migration4To5::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = TioMinuterDatabase.Migration6To7::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11)
    ]
)
@TypeConverters(RepsListConverter::class)
abstract class TioMinuterDatabase : RoomDatabase() {

    abstract fun gymRoutineDao(): GymRoutineDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun gymProgressDao(): GymProgressDao

    abstract fun dailyRoutineDao(): DailyRoutineDao

    abstract fun trainingLogDao(): TrainingLogDao

    abstract fun weeklyGoalDao(): WeeklyGoalDao

    abstract fun gymSessionDao(): GymSessionDao

    abstract fun dailySessionDao(): DailySessionDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    abstract fun appSyncDao(): AppSyncDao

    abstract fun feedShareDao(): FeedShareDao

    abstract fun dailyProgressDao(): DailyProgressDao

    /** The weekly goal counts workouts instead of days: a 4-day goal becomes 4 workouts. */
    @RenameColumn(tableName = "weekly_goal", fromColumnName = "daysPerWeek", toColumnName = "workoutsPerWeek")
    class Migration4To5 : AutoMigrationSpec

    /**
     * Routines with exercises completed before sessions existed get a session from their
     * completion times ([GymSessionRules.fromCompletionTimes]: the latest run without a gap over
     * the inactivity timeout). A routine whose every slot is completed is skipped: its entry was
     * recorded when it was completed, and its completion dialog only resets the progress.
     */
    class Migration6To7 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val timesByRoutine = mutableMapOf<Int, MutableList<Long>>()
            db.query(
                """
                SELECT p.routineId, p.completedAt FROM gym_exercise_progress p
                WHERE EXISTS (
                    SELECT 1 FROM gym_routine_exercises e
                    WHERE e.routineId = p.routineId AND e.position NOT IN (
                        SELECT q.position FROM gym_exercise_progress q WHERE q.routineId = p.routineId
                    )
                )
                """
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    timesByRoutine.getOrPut(cursor.getInt(0)) { mutableListOf() } += cursor.getLong(1)
                }
            }
            for ((routineId, times) in timesByRoutine) {
                val session = GymSessionRules.fromCompletionTimes(routineId, times) ?: continue
                db.execSQL(
                    "INSERT INTO gym_session (routineId, sessionStartedAt, lastSetAt) VALUES (?, ?, ?)",
                    arrayOf<Any>(session.routineId, session.sessionStartedAt, session.lastSetAt)
                )
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "tiominuter.db"
    }
}
