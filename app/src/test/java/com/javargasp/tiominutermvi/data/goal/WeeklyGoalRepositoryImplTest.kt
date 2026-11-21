package com.javargasp.tiominutermvi.data.goal

import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

/** Goal storage with a fake DAO (Room itself is covered by WeeklyGoalDaoTest). */
class WeeklyGoalRepositoryImplTest {

    // Thursday 2026-10-22T10:00:00Z
    private var now = 1_792_663_200_000L
    private val dao = FakeWeeklyGoalDao()
    private val repository = WeeklyGoalRepositoryImpl(
        dao = dao,
        clock = { now },
        localDateProvider = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    )

    @Test
    fun `a goal applies from the Monday of the current week`() = runTest {
        repository.setGoal(4)

        assertEquals(listOf(WeeklyGoalEntity("2026-10-19", 4, now)), dao.rows.value)
        assertEquals(listOf(WeeklyGoal(4, "2026-10-19")), repository.observeGoals().first())
    }

    @Test
    fun `setting it again in the same week replaces it, past weeks keep theirs`() = runTest {
        repository.setGoal(3)
        now += 7 * DAY // next Thursday
        repository.setGoal(5)
        now += DAY
        repository.setGoal(2)

        assertEquals(
            listOf(WeeklyGoal(3, "2026-10-19"), WeeklyGoal(2, "2026-10-26")),
            repository.observeGoals().first()
        )
    }

    @Test
    fun `only 1 to 14 workouts are accepted`() = runTest {
        listOf(0, 15, -1).forEach { workouts ->
            try {
                repository.setGoal(workouts)
                fail("$workouts accepted")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        repository.setGoal(1)
        repository.setGoal(14)
        assertEquals(listOf(14), dao.rows.value.map { it.workoutsPerWeek })
    }

    private class FakeWeeklyGoalDao : WeeklyGoalDao {
        val rows = MutableStateFlow<List<WeeklyGoalEntity>>(emptyList())

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }

        override suspend fun upsert(goal: WeeklyGoalEntity) {
            rows.value = (rows.value.filterNot { it.effectiveFromWeek == goal.effectiveFromWeek } + goal)
                .sortedBy { it.effectiveFromWeek }
        }

        override fun observeAll(): Flow<List<WeeklyGoalEntity>> = rows.map { it.sortedBy { row -> row.effectiveFromWeek } }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
