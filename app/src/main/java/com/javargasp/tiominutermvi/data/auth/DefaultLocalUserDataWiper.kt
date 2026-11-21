package com.javargasp.tiominutermvi.data.auth

import com.javargasp.tiominutermvi.data.local.dao.DailyProgressDao
import com.javargasp.tiominutermvi.data.local.dao.DailySessionDao
import com.javargasp.tiominutermvi.data.local.dao.FeedShareDao
import com.javargasp.tiominutermvi.data.local.dao.GymProgressDao
import com.javargasp.tiominutermvi.data.local.dao.GymSessionDao
import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.program.repository.ProgramDataCleaner
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.data.sync.SyncOutboxDao

/**
 * [LocalUserDataWiper] over DAO deletes (never `clearAllTables` on the app database, which also
 * holds the content catalog, and never the database files):
 * - in one transaction of the app database: `training_log`, `weekly_goal`, `gym_exercise_progress`, `gym_session`, `daily_session`, `sync_outbox`, `feed_share`, `daily_progress`;
 * - every program and its progress ([ProgramDataCleaner], the Program feature's own database);
 * - the training photo files, which no entry references any more ([TrainingPhotoRepository.cleanUp]).
 * The catalog tables (`exercises`, gym and daily routines), `filesDir/content/` and the assets stay.
 *
 * @param inTransaction runs the block in one transaction of the app database
 */
class DefaultLocalUserDataWiper(
    private val inTransaction: suspend (suspend () -> Unit) -> Unit,
    private val trainingLogDao: TrainingLogDao,
    private val weeklyGoalDao: WeeklyGoalDao,
    private val gymProgressDao: GymProgressDao,
    private val gymSessionDao: GymSessionDao,
    private val dailySessionDao: DailySessionDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val feedShareDao: FeedShareDao,
    private val dailyProgressDao: DailyProgressDao,
    private val programs: ProgramDataCleaner,
    private val photos: TrainingPhotoRepository
) : LocalUserDataWiper {

    override suspend fun wipe() {
        inTransaction {
            trainingLogDao.deleteAll()
            weeklyGoalDao.deleteAll()
            gymProgressDao.deleteAll()
            gymSessionDao.deleteAll()
            dailySessionDao.deleteAll()
            // Pending changes of the data just removed (an account switch, an account deletion)
            syncOutboxDao.deleteAll()
            // Workouts waiting to be posted to the groups of that account
            feedShareDao.deleteAll()
            // The daily unlock progress: the next read starts it again from the (empty) history
            dailyProgressDao.deleteAll()
        }
        programs.deleteAllProgramData()
        // After the rows: every photo file is now unreferenced
        photos.cleanUp()
    }
}
