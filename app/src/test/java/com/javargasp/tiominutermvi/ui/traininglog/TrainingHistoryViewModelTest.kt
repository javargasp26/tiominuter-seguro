package com.javargasp.tiominutermvi.ui.traininglog

import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.testutil.FakeDailyCatalog
import com.javargasp.tiominutermvi.testutil.FakeProgramTitleCodes
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.testutil.workoutNameCatalogs
import com.javargasp.tiominutermvi.ui.components.text
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingHistoryViewModelTest {

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // 2026-10-19T10:00:00Z, UTC: today is 2026-10-19
    private val localDateProvider = LocalDateProvider({ 1_792_404_000_000L }, { TimeZone.getTimeZone("UTC") })
    private val repository = FakeTrainingLogRepository()
    private val goalRepository = FakeWeeklyGoalRepository()
    private val gymRepository = FakeGymRoutineRepository()
    private val dailyCatalog = FakeDailyCatalog()
    private val programCodes = FakeProgramTitleCodes()
    private val names = workoutNameCatalogs(gymRepository, dailyCatalog, programCodes)

    @Test
    fun `loading until the first emission`() = runTest {
        val viewModel = TrainingHistoryViewModel(FakeTrainingLogRepository(emit = false), goalRepository, localDateProvider, names, photos)

        assertTrue(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `no entries shows the empty state`() = runTest {
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isEmpty)
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    @Test
    fun `entries are grouped by day newest first with Today and Yesterday labels`() = runTest {
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        repository.entries.value = listOf(
            entry(1, "2026-10-12", completedAt = 100),
            entry(2, "2026-10-18", completedAt = 200),
            entry(3, "2026-10-19", completedAt = 300),
            entry(4, "2026-10-19", completedAt = 400)
        )

        val days = viewModel.state.value.days
        assertEquals(listOf("2026-10-19", "2026-10-18", "2026-10-12"), days.map { it.dateLocal })
        assertEquals(
            listOf(DayLabel.Today, DayLabel.Yesterday, DayLabel.Date("2026-10-12")),
            days.map { it.label }
        )
        assertEquals(listOf(4L, 3L), days[0].entries.map { it.id })
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `labels follow the clock and the device zone`() = runTest {
        // Same instant, but 2026-10-20 in Tokyo: the 19th becomes Yesterday
        val tokyo = LocalDateProvider({ 1_792_452_600_000L }, { TimeZone.getTimeZone("Asia/Tokyo") })
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, tokyo, names, photos)

        repository.entries.value = listOf(entry(1, "2026-10-20"), entry(2, "2026-10-19"), entry(3, "2026-10-18"))

        assertEquals(
            listOf(DayLabel.Today, DayLabel.Yesterday, DayLabel.Date("2026-10-18")),
            viewModel.state.value.days.map { it.label }
        )
    }

    @Test
    fun `confirming a delete removes the entry`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"), entry(2, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnDeleteRequested(2))
        assertEquals(2L, viewModel.state.value.pendingDeleteId)
        assertEquals(2L, viewModel.state.value.pendingDeleteEntry?.id)

        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmDelete)

        assertEquals(listOf(2L), repository.deletedIds)
        assertNull(viewModel.state.value.pendingDeleteId)
        assertEquals(listOf(1L), viewModel.state.value.days.single().entries.map { it.id })
    }

    @Test
    fun `cancelling a delete keeps the entry`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnDeleteRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnDismissDelete)
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmDelete)

        assertTrue(repository.deletedIds.isEmpty())
        assertNull(viewModel.state.value.pendingDeleteId)
        assertEquals(1, viewModel.state.value.days.single().entries.size)
    }

    @Test
    fun `deleting the last entry shows the empty state`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnDeleteRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmDelete)

        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `a pending delete closes when its entry disappears`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"), entry(2, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        viewModel.handleIntent(TrainingHistoryIntent.OnDeleteRequested(2))

        repository.entries.value = listOf(entry(1, "2026-10-19"))

        assertNull(viewModel.state.value.pendingDeleteId)
        assertNull(viewModel.state.value.pendingDeleteEntry)
    }

    @Test
    fun `a delete failure keeps the screen working`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"))
        repository.deleteError = IllegalStateException("locked")
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnDeleteRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmDelete)

        assertNull(viewModel.state.value.pendingDeleteId)
        assertEquals(1, viewModel.state.value.days.single().entries.size)
    }

    @Test
    fun `back navigates back`() = runTest {
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(TrainingHistoryIntent.OnBackPressed)

        assertEquals(listOf(TrainingHistoryEffect.NavigateBack), effects)
    }

    // ------------------------------------------------------------------
    // Week headers
    // ------------------------------------------------------------------

    @Test
    fun `days are grouped into weeks starting on Monday, newest first`() = runTest {
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        repository.entries.value = listOf(
            entry(1, "2026-10-11", completedAt = 100), // Sunday of the week of 5 October
            entry(2, "2026-10-12", completedAt = 200), // Monday
            entry(3, "2026-10-18", completedAt = 300), // Sunday
            entry(4, "2026-10-19", completedAt = 400) // Monday (today)
        )

        val weeks = viewModel.state.value.weeks
        assertEquals(listOf("2026-10-19", "2026-10-12", "2026-10-05"), weeks.map { it.weekStart })
        assertEquals(listOf(listOf("2026-10-18", "2026-10-12")), weeks.filter { it.weekStart == "2026-10-12" }.map { w -> w.days.map { it.dateLocal } })
        assertEquals(listOf(1, 2, 1), weeks.map { it.progress.workoutCount })
    }

    @Test
    fun `each week shows the goal it had and counts every workout of a day`() = runTest {
        goalRepository.goals.value = listOf(WeeklyGoal(2, "2026-10-05"), WeeklyGoal(4, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        repository.entries.value = listOf(
            entry(1, "2026-09-30", completedAt = 50), // before the first goal
            entry(2, "2026-10-06", completedAt = 100),
            entry(3, "2026-10-06", completedAt = 110),
            entry(4, "2026-10-08", completedAt = 120),
            entry(5, "2026-10-13", completedAt = 200),
            entry(6, "2026-10-19", completedAt = 300)
        )

        val byWeek = viewModel.state.value.weeks.associateBy { it.weekStart }
        assertEquals(4, byWeek.getValue("2026-10-19").progress.goal)
        assertFalse(byWeek.getValue("2026-10-19").progress.isGoalMet)
        assertEquals(2, byWeek.getValue("2026-10-12").progress.goal)
        assertFalse(byWeek.getValue("2026-10-12").progress.isGoalMet)
        // Two workouts on 6 October and one on the 8th
        assertEquals(3, byWeek.getValue("2026-10-05").progress.workoutCount)
        assertEquals(2, byWeek.getValue("2026-10-05").progress.workoutsOn("2026-10-06"))
        assertTrue(byWeek.getValue("2026-10-05").progress.isGoalMet)
        assertNull(byWeek.getValue("2026-09-28").progress.goal)
    }

    @Test
    fun `short workouts stay in the history but are not counted`() = runTest {
        goalRepository.goals.value = listOf(WeeklyGoal(2, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        repository.entries.value = listOf(
            entry(1, "2026-10-19", completedAt = 100, durationSeconds = MIN_COUNTED_DURATION_SECONDS - 1),
            entry(2, "2026-10-19", completedAt = 200, durationSeconds = null),
            entry(3, "2026-10-20", completedAt = 300, durationSeconds = MIN_COUNTED_DURATION_SECONDS)
        )

        val week = viewModel.state.value.weeks.single()
        assertEquals(3, week.days.sumOf { it.entries.size })
        assertEquals(2, week.progress.workoutCount)
        assertTrue(week.progress.isGoalMet)
    }

    @Test
    fun `a goal change updates the week headers`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        assertNull(viewModel.state.value.weeks.single().progress.goal)

        goalRepository.goals.value = listOf(WeeklyGoal(1, "2026-10-19"))

        assertTrue(viewModel.state.value.weeks.single().progress.isGoalMet)
    }

    // ------------------------------------------------------------------
    // Logged activities
    // ------------------------------------------------------------------

    private fun activityEntry(id: Long, date: String, note: String? = null, title: String = "") =
        entry(id, date, completedAt = id * 100).copy(
            source = TrainingSource.ACTIVITY,
            refId = null,
            title = title,
            activityType = ActivityType.RUNNING.code,
            intensity = Intensity.MODERATE.code,
            note = note
        )

    @Test
    fun `tapping a logged activity opens it for editing`() = runTest {
        repository.entries.value = listOf(activityEntry(5, "2026-10-19"), entry(1, "2026-10-19"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(TrainingHistoryIntent.OnEntryClicked(5))

        assertEquals(listOf<TrainingHistoryEffect>(TrainingHistoryEffect.NavigateToEditActivity(5)), effects)
    }

    @Test
    fun `tapping an automatic entry does nothing`() = runTest {
        repository.entries.value = listOf(entry(1, "2026-10-19"), entry(2, "2026-10-19").copy(source = TrainingSource.GYM))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(TrainingHistoryIntent.OnEntryClicked(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnEntryClicked(2))
        viewModel.handleIntent(TrainingHistoryIntent.OnEntryClicked(99))

        assertTrue(effects.isEmpty())
    }

    @Test
    fun `the FAB logs a new activity`() = runTest {
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(TrainingHistoryIntent.OnLogActivityClicked)

        assertEquals(listOf<TrainingHistoryEffect>(TrainingHistoryEffect.NavigateToLogActivity), effects)
    }

    @Test
    fun `an activity row has its type, intensity and the first line of the note`() = runTest {
        repository.entries.value = listOf(activityEntry(5, "2026-10-19", note = "\n  Morning run  \nlegs tired"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        val row = viewModel.state.value.days.single().entries.single()
        assertTrue(row.isActivity)
        assertEquals(ActivityType.RUNNING, row.activity)
        assertEquals(Intensity.MODERATE, row.intensityLevel)
        assertEquals(10, durationMinutes(row.durationSeconds!!))
        assertEquals("Morning run", noteFirstLine(row))
        // Automatic entries never show a note line
        assertNull(noteFirstLine(entry(1, "2026-10-19").copy(note = "x")))
        assertNull(noteFirstLine(activityEntry(6, "2026-10-19", note = null)))
    }

    @Test
    fun `logged activities count in the week headers like any workout`() = runTest {
        goalRepository.goals.value = listOf(WeeklyGoal(2, "2026-10-19"))
        repository.entries.value = listOf(
            activityEntry(5, "2026-10-19"),
            activityEntry(6, "2026-10-19").copy(durationSeconds = MIN_COUNTED_DURATION_SECONDS - 60),
            entry(1, "2026-10-19")
        )
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        val week = viewModel.state.value.weeks.single()
        assertEquals(2, week.progress.workoutCount)
        assertTrue(week.progress.isGoalMet)
    }

    // ------------------------------------------------------------------
    // Workout names in the viewer's language (WorkoutNameResolver)
    // ------------------------------------------------------------------

    private fun gymEntry(id: Long, routineId: Int, storedTitle: String) =
        entry(id, "2026-10-19").copy(source = TrainingSource.GYM, refId = "gym:$routineId", title = storedTitle)

    private val pullDay = GymRoutine(64, "Pull Day", emptyList(), routineNameEs = "Día de tirón")

    private fun TrainingHistoryState.titleOf(id: Long, texts: XmlWorkoutNameTexts) = nameOf(entry(id)!!).text(texts)

    @Test
    fun `the gym routines are observed for the entry titles`() = runTest {
        gymRepository.routines.value = listOf(pullDay)
        repository.entries.value = listOf(gymEntry(1, 64, "Pull Day"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        assertEquals("Día de tirón", viewModel.state.value.titleOf(1, XmlWorkoutNameTexts.ES))

        gymRepository.routines.value = listOf(pullDay.copy(routineNameEs = "Tirón"))
        assertEquals("Tirón", viewModel.state.value.titleOf(1, XmlWorkoutNameTexts.ES))
        assertEquals("Pull Day", viewModel.state.value.titleOf(1, XmlWorkoutNameTexts.EN))
    }

    @Test
    fun `every source is named in the viewer's language, whatever language recorded it`() = runTest {
        gymRepository.routines.value = listOf(pullDay)
        programCodes.codes["program:7:1:0"] = ProgramTitleCode.UPPER_A
        repository.entries.value = listOf(
            gymEntry(1, 64, "Pull Day"),
            entry(2, "2026-10-19").copy(source = TrainingSource.DAILY, refId = "daily:5", title = "Day 5"),
            // Recorded on a Spanish device: the stored title is Spanish
            entry(3, "2026-10-19").copy(source = TrainingSource.PROGRAM, refId = "program:7:1:0", title = "Tren superior A")
        )
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val state = viewModel.state.value

        assertEquals(listOf("Pull Day", "Day 5", "Upper A"), (1L..3L).map { state.titleOf(it, XmlWorkoutNameTexts.EN) })
        assertEquals(listOf("Día de tirón", "Día 5", "Tren superior A"), (1L..3L).map { state.titleOf(it, XmlWorkoutNameTexts.ES) })
        assertEquals("one lookup per program entry", listOf("program:7:1:0"), programCodes.lookups.distinct())
    }

    @Test
    fun `unknown routines and deleted programs keep their stored title`() = runTest {
        repository.entries.value = listOf(
            gymEntry(1, 99, "Old routine"),
            gymEntry(2, 64, "Push").copy(refId = "gym:x"),
            entry(3, "2026-10-19").copy(source = TrainingSource.PROGRAM, refId = "program:8:1:0", title = "Full body A"),
            entry(4, "2026-10-19").copy(source = TrainingSource.DAILY, refId = null, title = "Day 1")
        )
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val state = viewModel.state.value

        assertEquals(
            listOf("Old routine", "Push", "Full body A", "Day 1"),
            (1L..4L).map { state.titleOf(it, XmlWorkoutNameTexts.ES) }
        )
    }

    @Test
    fun `the history still works when the daily catalog can't be read`() = runTest {
        dailyCatalog.fails = true
        repository.entries.value = listOf(entry(1, "2026-10-19").copy(source = TrainingSource.DAILY, refId = "daily:3", title = "Day 3"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Día 3", viewModel.state.value.titleOf(1, XmlWorkoutNameTexts.ES))
    }

    // ------------------------------------------------------------------
    // Photos
    // ------------------------------------------------------------------

    private val galleryPhoto = TrainingPhotoSource("content://picker/1", fromCamera = false)

    private fun gymEntryWithPhoto(id: Long, photoPath: String?) =
        entry(id, "2026-10-19").copy(source = TrainingSource.GYM, refId = "gym:64", photoPath = photoPath)

    @Test
    fun `a photo is attached to an automatic entry`() = runTest {
        repository.entries.value = listOf(gymEntryWithPhoto(1, null))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnPhotoSelected(1, galleryPhoto))

        assertEquals(listOf(1L to "content://picker/1"), photos.attached)
        assertTrue(viewModel.state.value.processingPhotoEntryIds.isEmpty())
    }

    @Test
    fun `logged activities change their photo in their form, not from the history`() = runTest {
        repository.entries.value = listOf(activityEntry(5, "2026-10-19").copy(photoPath = "training_photos/a-1.jpg"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnPhotoSelected(5, galleryPhoto))
        viewModel.handleIntent(TrainingHistoryIntent.OnRemovePhotoRequested(5))
        viewModel.handleIntent(TrainingHistoryIntent.OnPhotoSelected(99, galleryPhoto))

        assertTrue(photos.attached.isEmpty())
        assertNull(viewModel.state.value.pendingPhotoRemovalEntry)
    }

    @Test
    fun `removing a photo asks first and removes it on confirm`() = runTest {
        repository.entries.value = listOf(gymEntryWithPhoto(1, "training_photos/uuid-1-1.jpg"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnRemovePhotoRequested(1))
        assertEquals(1L, viewModel.state.value.pendingPhotoRemovalEntry?.id)
        viewModel.handleIntent(TrainingHistoryIntent.OnDismissRemovePhoto)
        assertTrue(photos.removed.isEmpty())

        viewModel.handleIntent(TrainingHistoryIntent.OnRemovePhotoRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmRemovePhoto)

        assertEquals(listOf(1L), photos.removed)
        assertNull(viewModel.state.value.pendingPhotoRemovalEntry)
    }

    @Test
    fun `an entry without photo has nothing to remove`() = runTest {
        repository.entries.value = listOf(gymEntryWithPhoto(1, null))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)

        viewModel.handleIntent(TrainingHistoryIntent.OnRemovePhotoRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmRemovePhoto)

        assertNull(viewModel.state.value.pendingPhotoRemovalEntry)
        assertTrue(photos.removed.isEmpty())
    }

    @Test
    fun `a photo failure shows a message and keeps the entry`() = runTest {
        repository.entries.value = listOf(gymEntryWithPhoto(1, "training_photos/uuid-1-1.jpg"))
        val viewModel = TrainingHistoryViewModel(repository, goalRepository, localDateProvider, names, photos)
        val effects = collectEffects(viewModel)
        photos.error = TrainingPhotoError.UNREADABLE_IMAGE

        viewModel.handleIntent(TrainingHistoryIntent.OnPhotoSelected(1, galleryPhoto))
        photos.error = TrainingPhotoError.FAILED
        viewModel.handleIntent(TrainingHistoryIntent.OnRemovePhotoRequested(1))
        viewModel.handleIntent(TrainingHistoryIntent.OnConfirmRemovePhoto)

        assertEquals(
            listOf<TrainingHistoryEffect>(
                TrainingHistoryEffect.ShowPhotoError(TrainingPhotoError.UNREADABLE_IMAGE),
                TrainingHistoryEffect.ShowPhotoError(TrainingPhotoError.FAILED)
            ),
            effects
        )
        assertEquals(1, viewModel.state.value.days.single().entries.size)
        assertTrue(viewModel.state.value.processingPhotoEntryIds.isEmpty())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun entry(id: Long, date: String, completedAt: Long = id, durationSeconds: Int? = 600) = TrainingLogEntry(
        id = id,
        uuid = "uuid-$id",
        dateLocal = date,
        completedAt = completedAt,
        source = TrainingSource.DAILY,
        refId = "daily:$id",
        title = "Day $id",
        durationSeconds = durationSeconds,
        createdAt = completedAt,
        updatedAt = completedAt
    )

    private fun TestScope.collectEffects(viewModel: TrainingHistoryViewModel): List<TrainingHistoryEffect> {
        val effects = mutableListOf<TrainingHistoryEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeGymRoutineRepository : GymRoutineRepository {
        val routines = MutableStateFlow<List<GymRoutine>>(emptyList())

        override fun observeGymRoutines(): Flow<List<GymRoutine>> = routines
        override fun observeGymRoutine(routineId: Int): Flow<GymRoutine?> =
            routines.map { list -> list.firstOrNull { it.routineId == routineId } }

        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private class FakeWeeklyGoalRepository : WeeklyGoalRepository {
        val goals = MutableStateFlow<List<WeeklyGoal>>(emptyList())

        override fun observeGoals(): Flow<List<WeeklyGoal>> = goals

        override suspend fun setGoal(workoutsPerWeek: Int) = Unit
    }

    /** Entries are newest first, like the real repository. */
    private class FakeTrainingLogRepository(private val emit: Boolean = true) : TrainingLogRepository {
        val entries = MutableStateFlow<List<TrainingLogEntry>>(emptyList())
        val deletedIds = mutableListOf<Long>()
        var deleteError: Exception? = null

        override fun observeEntries(): Flow<List<TrainingLogEntry>> =
            if (emit) entries.map { list -> list.sortedByDescending { it.completedAt } } else emptyFlow()

        override fun observeEntriesBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntry>> =
            observeEntries().map { list -> list.filter { it.dateLocal in fromDate..toDate } }

        override suspend fun getEntry(id: Long): TrainingLogEntry? = entries.value.firstOrNull { it.id == id }
        override suspend fun addActivityEntry(input: ActivityEntryInput): Long = throw UnsupportedOperationException()
        override suspend fun updateActivityEntry(id: Long, input: ActivityEntryInput) = throw UnsupportedOperationException()
        override suspend fun setPhotoPath(id: Long, photoPath: String?): String? = throw UnsupportedOperationException()
        override suspend fun getPhotoPaths(): List<String> = emptyList()

        override suspend fun deleteEntry(id: Long) {
            deleteError?.let { throw it }
            deletedIds.add(id)
            entries.value = entries.value.filterNot { it.id == id }
        }
    }

    /** Photo repository fake: records every call; [error] makes the next actions fail. */
    private class FakeTrainingPhotoRepository : TrainingPhotoRepository {
        var error: TrainingPhotoError? = null
        val attached = mutableListOf<Pair<Long, String>>()
        val committed = mutableListOf<Pair<Long, StagedPhoto>>()
        val discarded = mutableListOf<StagedPhoto>()
        val removed = mutableListOf<Long>()
        private var staged = 0

        override suspend fun stage(source: TrainingPhotoSource): StagedPhoto {
            error?.let { throw TrainingPhotoException(it) }
            staged++
            return StagedPhoto(File("staged-$staged.jpg"), File("staged-${staged}_thumb.jpg"))
        }

        override suspend fun commit(entryId: Long, staged: StagedPhoto): String {
            error?.let { throw TrainingPhotoException(it) }
            committed += entryId to staged
            return "training_photos/entry$entryId-${committed.size}.jpg"
        }

        override fun discard(staged: StagedPhoto) {
            discarded += staged
        }

        override suspend fun attach(entryId: Long, source: TrainingPhotoSource): String {
            error?.let { throw TrainingPhotoException(it) }
            attached += entryId to source.uri
            return "training_photos/entry$entryId-${attached.size}.jpg"
        }

        override suspend fun remove(entryId: Long) {
            error?.let { throw TrainingPhotoException(it) }
            removed += entryId
        }

        override suspend fun cleanUp(): Int = 0
    }
}
