package com.javargasp.tiominutermvi.ui.home

import com.javargasp.tiominutermvi.testutil.FakeDailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.data.auth.InMemoryAuthPreferences
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.GuestBanner
import com.javargasp.tiominutermvi.domain.auth.GuestBannerRule
import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.domain.goal.ObserveCurrentWeek
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.groups.GroupMember
import com.javargasp.tiominutermvi.domain.groups.GroupMembers
import com.javargasp.tiominutermvi.domain.groups.GroupsHome
import com.javargasp.tiominutermvi.domain.groups.HomeGroups
import com.javargasp.tiominutermvi.domain.groups.MemberProfile
import com.javargasp.tiominutermvi.domain.groups.MemberSummary
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.testutil.GymSessionTestKit
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.testutil.StringResources
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.ui.components.text
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

/** Dates: the week of Monday 19 October 2026; today is Thursday 22 unless moved. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // 2026-10-22T10:00:00Z
    private var now = 1_792_663_200_000L
    private val goals = MutableStateFlow<List<WeeklyGoal>>(emptyList())
    private val entries = MutableStateFlow<List<TrainingLogEntry>>(emptyList())
    private val auth = FakeAuthRepository()
    private val authPreferences = InMemoryAuthPreferences()
    private val gymSessions = GymSessionTestKit()
    private val groupsHome = FakeGroupsHome()
    private val dailyProgress = FakeDailyProgressRepository()
    private var dailyRoutines: List<Routine>? = listOf(Routine(1, "Day 1", exercises = emptyList()), Routine(2, "Day 2", exercises = emptyList()))

    private fun createViewModel() = HomeViewModel(
        workoutRepository = FakeWorkoutRepository(dailyRoutines),
        observeCurrentWeek = ObserveCurrentWeek(
            weeklyGoalRepository = FakeWeeklyGoalRepository(goals),
            trainingLogRepository = FakeTrainingLogRepository(entries),
            localDateProvider = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
        ),
        authRepository = auth,
        guestBanner = GuestBanner(auth, authPreferences) { now },
        sessionClosers = listOf(gymSessions.tracker),
        dailyProgress = dailyProgress,
        groupsHome = groupsHome
    )

    @Test
    fun `initial state is correct`() {
        val initialState = createViewModel().state.value

        assertFalse(initialState.isLoading)
        assertEquals(null, initialState.currentDay)
    }

    @Test
    fun `OnGymRoutinesClicked emits NavigateToGymRoutineSelection`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(HomeIntent.OnGymRoutinesClicked)

        assertEquals(listOf(HomeEffect.NavigateToGymRoutineSelection), effects)
    }

    @Test
    fun `tapping the weekly card opens the goal screen`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(HomeIntent.OnWeeklyGoalClicked)

        assertEquals(listOf(HomeEffect.NavigateToWeeklyGoal), effects)
    }

    // ------------------------------------------------------------------
    // "This week" card
    // ------------------------------------------------------------------

    @Test
    fun `without a goal the card invites to set one`() = runTest {
        entries.value = listOf(entry(1, "2026-10-19"))
        val state = createViewModel().state.value

        assertNull(state.week!!.progress.goal)
        assertEquals(1, state.week!!.progress.workoutCount)
        assertNull(state.weeklyMessage)
    }

    @Test
    fun `with workouts still needed it says how many are left`() = runTest {
        goals.value = listOf(WeeklyGoal(4, "2026-10-19"))
        entries.value = listOf(entry(1, "2026-10-19"), entry(2, "2026-10-21"))

        val state = createViewModel().state.value

        assertEquals(2, state.week!!.progress.workoutCount)
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(2), state.weeklyMessage)
    }

    @Test
    fun `two workouts on the same day count as two`() = runTest {
        goals.value = listOf(WeeklyGoal(3, "2026-10-19"))
        entries.value = listOf(entry(1, "2026-10-22", 3_600), entry(2, "2026-10-22", 2_400))

        val state = createViewModel().state.value

        assertEquals(2, state.week!!.progress.workoutsOn("2026-10-22"))
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(1), state.weeklyMessage)
    }

    @Test
    fun `workouts shorter than the minimum do not count, exactly the minimum and unknown do`() = runTest {
        goals.value = listOf(WeeklyGoal(3, "2026-10-19"))
        entries.value = listOf(
            entry(1, "2026-10-19", MIN_COUNTED_DURATION_SECONDS - 1),
            entry(2, "2026-10-20", MIN_COUNTED_DURATION_SECONDS),
            entry(3, "2026-10-21", null)
        )

        val state = createViewModel().state.value

        assertEquals(2, state.week!!.progress.workoutCount)
        assertEquals(0, state.week!!.progress.workoutsOn("2026-10-19"))
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(1), state.weeklyMessage)
    }

    @Test
    fun `when the goal is met it says so and the streak includes this week`() = runTest {
        goals.value = listOf(WeeklyGoal(2, "2026-10-12"))
        entries.value = listOf(entry(1, "2026-10-13"), entry(2, "2026-10-15"), entry(3, "2026-10-19"), entry(4, "2026-10-22"))

        val state = createViewModel().state.value

        assertEquals(WeeklyGoalMessage.GoalMet, state.weeklyMessage)
        assertEquals(2, state.week!!.streak)
    }

    @Test
    fun `exceeding the goal keeps the real count and stays met`() = runTest {
        goals.value = listOf(WeeklyGoal(4, "2026-10-19"))
        entries.value = listOf(
            entry(1, "2026-10-19"), entry(2, "2026-10-19"), entry(3, "2026-10-20"), entry(4, "2026-10-21"), entry(5, "2026-10-22")
        )

        val state = createViewModel().state.value

        assertEquals(5, state.week!!.progress.workoutCount)
        assertEquals(0, state.week!!.workoutsNeeded)
        assertEquals(WeeklyGoalMessage.GoalMet, state.weeklyMessage)
    }

    @Test
    fun `late in the week it still says how many workouts are left`() = runTest {
        now += 3 * DAY // Sunday 25
        goals.value = listOf(WeeklyGoal(5, "2026-10-19"))
        entries.value = listOf(entry(1, "2026-10-19"))

        // No "no longer reachable" note: several workouts a day are possible
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(4), createViewModel().state.value.weeklyMessage)
    }

    @Test
    fun `the streak of past weeks shows before this week is met`() = runTest {
        goals.value = listOf(WeeklyGoal(1, "2026-10-05"))
        entries.value = listOf(entry(1, "2026-10-06"), entry(2, "2026-10-13"))

        val state = createViewModel().state.value

        assertEquals(2, state.week!!.streak)
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(1), state.weeklyMessage)
    }

    @Test
    fun `a new workout updates the card`() = runTest {
        goals.value = listOf(WeeklyGoal(1, "2026-10-19"))
        val viewModel = createViewModel()
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(1), viewModel.state.value.weeklyMessage)

        entries.value = listOf(entry(1, "2026-10-22"))

        assertEquals(WeeklyGoalMessage.GoalMet, viewModel.state.value.weeklyMessage)
    }

    @Test
    fun `OnStart closes abandoned gym sessions (app start and every return to Home)`() = runTest {
        gymSessions.clock.now = 0L
        gymSessions.tracker.onSetCompleted(64)
        gymSessions.clock.now = 30 * 60_000L
        gymSessions.tracker.onSetCompleted(64)
        val viewModel = createViewModel()

        gymSessions.clock.now = 30 * 60_000L + 2 * 3_600_000L + 1
        viewModel.handleIntent(HomeIntent.OnStart)

        assertEquals(listOf(30 * 60), gymSessions.recorder.entries.map { it.durationSeconds })
        assertEquals(listOf(64), gymSessions.store.clearedProgress)
        // The gym screens announce it, not Home
        assertEquals(1, gymSessions.tracker.takeClosedSessions().size)

        viewModel.handleIntent(HomeIntent.OnStart)
        assertEquals(1, gymSessions.recorder.entries.size)
    }

    @Test
    fun `OnStart picks up a new week`() = runTest {
        goals.value = listOf(WeeklyGoal(1, "2026-10-19"))
        entries.value = listOf(entry(1, "2026-10-22"))
        val viewModel = createViewModel()
        assertEquals("2026-10-19", viewModel.state.value.week!!.progress.weekStart)

        now += 4 * DAY // Monday 26
        viewModel.handleIntent(HomeIntent.OnStart)

        val week = viewModel.state.value.week!!
        assertEquals("2026-10-26", week.progress.weekStart)
        assertEquals(WeeklyGoalMessage.WorkoutsLeft(1), viewModel.state.value.weeklyMessage)
        assertEquals(1, week.streak)
    }

    @Test
    fun `OnStart keeps the week and loads the current day`() = runTest {
        goals.value = listOf(WeeklyGoal(3, "2026-10-19"))
        val viewModel = createViewModel()

        viewModel.handleIntent(HomeIntent.OnStart)

        val state = viewModel.state.value
        assertEquals(1, state.currentDay)
        assertEquals(3, state.week!!.progress.goal)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the current day is named in the viewer's language`() = runTest {
        val viewModel = createViewModel()

        viewModel.handleIntent(HomeIntent.OnStart)

        dailyProgress.progress.value = DailyProgress(lastCompletedDay = 1)
        val name = viewModel.state.value.currentDayName!!
        assertEquals(WorkoutName.Daily(2), name)
        val en = StringResources.ENGLISH.get(R.string.home_day_available, name.text(XmlWorkoutNameTexts.EN))
        val es = StringResources.SPANISH.get(R.string.home_day_available, name.text(XmlWorkoutNameTexts.ES))
        assertEquals("Day 2 available", en)
        assertEquals("Día 2 disponible", es)
    }

    // ------------------------------------------------------------------
    // "Day X available": the next unlocked day (DailyUnlockRules)
    // ------------------------------------------------------------------

    @Test
    fun `the available day is the one after the last finished, not the highest routine`() = runTest {
        dailyRoutines = (1..30).map { Routine(it, "Day $it", exercises = emptyList()) }
        dailyProgress.progress.value = DailyProgress(lastCompletedDay = 6)
        val viewModel = createViewModel()

        viewModel.handleIntent(HomeIntent.OnStart)

        assertEquals(7, viewModel.state.value.currentDay)
    }

    @Test
    fun `a finished day or a restart shows on Home at once`() = runTest {
        dailyRoutines = (1..30).map { Routine(it, "Day $it", exercises = emptyList()) }
        val viewModel = createViewModel()
        viewModel.handleIntent(HomeIntent.OnStart)
        assertEquals(1, viewModel.state.value.currentDay)

        dailyProgress.onDayFinished(1)
        assertEquals(2, viewModel.state.value.currentDay)

        dailyProgress.restart()
        assertEquals(1, viewModel.state.value.currentDay)
    }

    @Test
    fun `after the last day Home stays on the last day`() = runTest {
        dailyRoutines = (1..30).map { Routine(it, "Day $it", exercises = emptyList()) }
        dailyProgress.progress.value = DailyProgress(lastCompletedDay = 30)
        val viewModel = createViewModel()

        viewModel.handleIntent(HomeIntent.OnStart)

        assertEquals(30, viewModel.state.value.currentDay)
    }

    @Test
    fun `without the routines Home falls back to Day 1, without routines it shows no day`() = runTest {
        dailyRoutines = null
        dailyProgress.progress.value = DailyProgress(lastCompletedDay = 6)
        val failing = createViewModel()
        failing.handleIntent(HomeIntent.OnStart)
        assertEquals(1, failing.state.value.currentDay)
        assertEquals(WorkoutName.Daily(1), failing.state.value.currentDayName)

        dailyRoutines = emptyList()
        val empty = createViewModel()
        empty.handleIntent(HomeIntent.OnStart)
        assertEquals(0, empty.state.value.currentDay)
        assertNull(empty.state.value.currentDayName)
    }

    // ------------------------------------------------------------------
    // Account icon and guest banner
    // ------------------------------------------------------------------

    @Test
    fun `the account icon and the banner's sign in open the account screen`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(HomeIntent.OnAccountClicked)
        viewModel.handleIntent(HomeIntent.OnGuestBannerSignInClicked)

        assertEquals(listOf(HomeEffect.NavigateToAccount, HomeEffect.NavigateToAccount), effects)
    }

    @Test
    fun `a guest sees the banner, a signed-in person doesn't`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(HomeIntent.OnStart)
        assertTrue(viewModel.state.value.showGuestBanner)
        assertEquals(AuthSession.Guest, viewModel.state.value.session)

        auth.signInWithGoogle("token")

        assertFalse(viewModel.state.value.showGuestBanner)
        assertEquals(FakeAuthRepository.SAMPLE_USER, viewModel.state.value.session)
    }

    @Test
    fun `a dismissed banner stays hidden for 7 days, then comes back once`() = runTest {
        val viewModel = createViewModel()
        viewModel.handleIntent(HomeIntent.OnStart)

        viewModel.handleIntent(HomeIntent.OnGuestBannerDismissed)
        assertFalse(viewModel.state.value.showGuestBanner)
        assertEquals(now, authPreferences.bannerDismissedAt)

        // Home shown again a minute before the 7 days: still hidden
        val dismissedAt = now
        now = dismissedAt + GuestBannerRule.REAPPEAR_AFTER_MILLIS - 60_000
        viewModel.handleIntent(HomeIntent.OnStart)
        assertFalse(viewModel.state.value.showGuestBanner)

        now = dismissedAt + GuestBannerRule.REAPPEAR_AFTER_MILLIS
        viewModel.handleIntent(HomeIntent.OnStart)
        assertTrue(viewModel.state.value.showGuestBanner)

        // Dismissed again: another 7 days from now
        viewModel.handleIntent(HomeIntent.OnGuestBannerDismissed)
        now += GuestBannerRule.REAPPEAR_AFTER_MILLIS - 1
        viewModel.handleIntent(HomeIntent.OnStart)
        assertFalse(viewModel.state.value.showGuestBanner)
    }

    @Test
    fun `a dismissal from an earlier launch is remembered`() = runTest {
        authPreferences.bannerDismissedAt = now - 60_000

        val viewModel = createViewModel()
        viewModel.handleIntent(HomeIntent.OnStart)

        assertFalse(viewModel.state.value.showGuestBanner)
    }

    // ------------------------------------------------------------------
    // Groups: entry point and the "already trained today" banner
    // ------------------------------------------------------------------

    private fun member(uid: String, lastTrained: String?) = GroupMember(
        uid, MemberProfile(uid.replaceFirstChar { it.uppercase() }, null), 0L,
        MemberSummary("2026-10-19", 1, 3, 0, emptyMap(), lastTrained)
    )

    private suspend fun signedInWithTrainers(vararg groups: GroupMembers): HomeViewModel {
        auth.signInWithGoogle("token")
        groupsHome.groups = HomeGroups(FakeAuthRepository.SAMPLE_USER.uid, groups.toList())
        return createViewModel()
    }

    @Test
    fun `the Groups icon opens the groups`() = runTest {
        val viewModel = createViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(HomeIntent.OnGroupsClicked)

        assertEquals(listOf(HomeEffect.NavigateToGroups), effects)
    }

    @Test
    fun `the banner names who trained today in my groups, and asks for a publish on every OnStart`() = runTest {
        val viewModel = signedInWithTrainers(
            GroupMembers("g1", listOf(member("ana", "2026-10-22"), member("luis", "2026-10-21"))),
            GroupMembers("g2", listOf(member("ana", "2026-10-22"), member("bea", "2026-10-22")))
        )
        viewModel.handleIntent(HomeIntent.OnStart)

        assertEquals(listOf("Ana", "Bea"), viewModel.state.value.trainedTodayBanner?.names)
        assertEquals(1, groupsHome.shownCalls)
    }

    @Test
    fun `the banner is hidden for a guest and once I log a counted workout today`() = runTest {
        groupsHome.groups = HomeGroups("me", listOf(GroupMembers("g1", listOf(member("ana", "2026-10-22")))))
        val guest = createViewModel()
        guest.handleIntent(HomeIntent.OnStart)
        assertNull(guest.state.value.trainedTodayBanner)

        val viewModel = signedInWithTrainers(GroupMembers("g1", listOf(member("ana", "2026-10-22"))))
        viewModel.handleIntent(HomeIntent.OnStart)
        assertTrue(viewModel.state.value.trainedTodayBanner != null)

        entries.value = listOf(entry(1, "2026-10-22", durationSeconds = 300)) // under 10 minutes: still shown
        assertTrue(viewModel.state.value.trainedTodayBanner != null)
        entries.value = listOf(entry(2, "2026-10-22"))
        assertNull(viewModel.state.value.trainedTodayBanner)
    }

    @Test
    fun `dismissing hides it for the rest of today, a new day shows it again`() = runTest {
        val viewModel = signedInWithTrainers(GroupMembers("g1", listOf(member("ana", "2026-10-22"), member("bob", "2026-10-23"))))
        viewModel.handleIntent(HomeIntent.OnStart)

        viewModel.handleIntent(HomeIntent.OnTrainedTodayBannerDismissed)
        assertNull(viewModel.state.value.trainedTodayBanner)
        assertEquals("2026-10-22", groupsHome.dismissedOn)

        now += DAY
        viewModel.handleIntent(HomeIntent.OnStart)
        assertEquals(listOf("Bob"), viewModel.state.value.trainedTodayBanner?.names)
    }

    @Test
    fun `tapping the banner opens the group, or the list when several groups have trainers`() = runTest {
        val one = signedInWithTrainers(GroupMembers("g1", listOf(member("ana", "2026-10-22"))))
        val oneEffects = collectEffects(one)
        one.handleIntent(HomeIntent.OnStart)
        one.handleIntent(HomeIntent.OnTrainedTodayBannerClicked)
        assertEquals(listOf(HomeEffect.NavigateToGroupDetail("g1")), oneEffects)

        groupsHome.groups = HomeGroups(
            FakeAuthRepository.SAMPLE_USER.uid,
            listOf(GroupMembers("g1", listOf(member("ana", "2026-10-22"))), GroupMembers("g2", listOf(member("bea", "2026-10-22"))))
        )
        val several = createViewModel()
        val severalEffects = collectEffects(several)
        several.handleIntent(HomeIntent.OnStart)
        several.handleIntent(HomeIntent.OnTrainedTodayBannerClicked)
        assertEquals(listOf(HomeEffect.NavigateToGroups), severalEffects)
    }

    // ------------------------------------------------------------------
    // Helpers and fakes
    // ------------------------------------------------------------------

    private fun TestScope.collectEffects(viewModel: HomeViewModel): List<HomeEffect> {
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    private class FakeGroupsHome : GroupsHome {
        var groups: HomeGroups? = null
        var dismissedOn: String? = null
        var shownCalls = 0

        override suspend fun onHomeShown(): HomeGroups? {
            shownCalls++
            return groups
        }

        override fun trainedTodayDismissedOn(): String? = dismissedOn

        override fun dismissTrainedToday(today: String) {
            dismissedOn = today
        }
    }

    /** [routines] null: reading them fails (an empty database offline). */
    private class FakeWorkoutRepository(private val routines: List<Routine>?) : WorkoutRepository {
        override suspend fun getExercises(): List<Exercise> = emptyList()
        override suspend fun getRoutines(): List<Routine> = routines ?: throw IllegalStateException("no routines")
        override suspend fun getExercise(exerciseId: Int): Exercise? = null
        override suspend fun getRoutine(routineId: Int): Routine? = routines?.firstOrNull { it.routineId == routineId }
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private class FakeWeeklyGoalRepository(private val goals: MutableStateFlow<List<WeeklyGoal>>) : WeeklyGoalRepository {
        override fun observeGoals(): Flow<List<WeeklyGoal>> = goals
        override suspend fun setGoal(workoutsPerWeek: Int) = Unit
    }

    /** A workout of 30 minutes unless another duration is given. */
    private fun entry(id: Long, date: String, durationSeconds: Int? = 1_800) = TrainingLogEntry(
        id = id, uuid = "uuid-$id", dateLocal = date, completedAt = id, source = TrainingSource.DAILY,
        refId = "daily:$id", title = "Day $id", durationSeconds = durationSeconds, createdAt = id, updatedAt = id
    )

    private class FakeTrainingLogRepository(private val entries: MutableStateFlow<List<TrainingLogEntry>>) : TrainingLogRepository {
        override fun observeEntries(): Flow<List<TrainingLogEntry>> = entries
        override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> = emptyFlow()
        override suspend fun deleteEntry(id: Long) = Unit
        override suspend fun getEntry(id: Long): TrainingLogEntry? = null
        override suspend fun addActivityEntry(input: ActivityEntryInput): Long = throw UnsupportedOperationException()
        override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = throw UnsupportedOperationException()
        override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = throw UnsupportedOperationException()
        override suspend fun getPhotoPaths(): List<String> = emptyList()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
