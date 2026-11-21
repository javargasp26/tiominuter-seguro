package com.javargasp.tiominutermvi.data.goal

import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker

/** Room-backed goal history. A goal set now applies from the Monday of the current week. */
class WeeklyGoalRepositoryImpl(
    private val dao: WeeklyGoalDao,
    private val clock: WallClock,
    private val localDateProvider: LocalDateProvider,
    private val changes: ChangeTracker = ChangeTracker.None
) : WeeklyGoalRepository {

    override fun observeGoals(): Flow<List<WeeklyGoal>> =
        dao.observeAll().map { rows -> rows.map { WeeklyGoal(it.workoutsPerWeek, it.effectiveFromWeek) } }

    override suspend fun setGoal(workoutsPerWeek: Int) {
        require(workoutsPerWeek in WeeklyGoal.WORKOUTS_RANGE) { "workoutsPerWeek must be 1..14, was $workoutsPerWeek" }
        val week = localDateProvider.currentWeekStart()
        changes.inTransaction {
            dao.upsert(WeeklyGoalEntity(effectiveFromWeek = week, workoutsPerWeek = workoutsPerWeek, updatedAt = clock.nowMillis()))
            changes.enqueue(DocKey(SyncCollection.WEEKLY_GOAL, week))
        }
    }
}
