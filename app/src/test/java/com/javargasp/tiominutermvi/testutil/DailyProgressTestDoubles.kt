package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.data.local.dao.DailyProgressDao
import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The `daily_progress` row in memory, plus the DAILY refIds of the history it starts from. */
class FakeDailyProgressDao(var historyRefIds: List<String?> = emptyList()) : DailyProgressDao {
    val row = MutableStateFlow<DailyProgressEntity?>(null)

    override suspend fun get(): DailyProgressEntity? = row.value

    override fun observe(): Flow<DailyProgressEntity?> = row

    override suspend fun upsert(progress: DailyProgressEntity) {
        row.value = progress
    }

    override suspend fun dailyRefIds(): List<String?> = historyRefIds

    override suspend fun deleteAll() {
        row.value = null
    }
}

/** A [DailyProgressRepository] with the real rules over an in-memory state (ViewModel tests). */
class FakeDailyProgressRepository(initial: DailyProgress = DailyProgress()) : DailyProgressRepository {
    val progress = MutableStateFlow(initial)
    val finishedDays = mutableListOf<Int>()
    var restarts = 0

    override fun observeProgress(): Flow<DailyProgress> = progress.map { it }

    override suspend fun onDayFinished(routineId: Int) {
        finishedDays += routineId
        progress.value = DailyUnlockRules.afterFinishing(progress.value, routineId)
    }

    override suspend fun restart() {
        restarts++
        progress.value = DailyUnlockRules.restart(progress.value)
    }
}
