package com.javargasp.tiominutermvi.ui.logactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoException
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.testutil.MainDispatcherRule
import com.javargasp.tiominutermvi.ui.traininglog.DayLabel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * The real [TrainingLogRepositoryImpl] over a private fake DAO. Now: Thursday
 * 2026-10-22T10:00:00Z, device zone UTC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogActivityViewModelTest {

    private val photoFiles = FakePhotoFiles()

    private val photos = FakeTrainingPhotoRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = 1_792_663_200_000L
    private val dao = FakeTrainingLogDao()
    private val dates = LocalDateProvider({ now }, { TimeZone.getTimeZone("UTC") })
    private val repository = TrainingLogRepositoryImpl(dao, { now }, dates, photoFiles, newUuid = { "uuid-${dao.rows.value.size + 1}" })

    private fun newViewModel() = LogActivityViewModel(null, repository, dates, photos)
    private fun editViewModel(id: Long) = LogActivityViewModel(id, repository, dates, photos)

    private fun LogActivityViewModel.fillValid() {
        handleIntent(LogActivityIntent.OnTypeSelected(ActivityType.TEAM_SPORT))
        handleIntent(LogActivityIntent.OnQuickDurationSelected(60))
        handleIntent(LogActivityIntent.OnIntensitySelected(Intensity.HARD))
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Test
    fun `a new activity starts today at the current time with the last 7 days`() = runTest {
        val state = newViewModel().state.value

        assertFalse(state.isLoading)
        assertFalse(state.isEditMode)
        assertEquals("2026-10-22", state.form!!.date)
        assertEquals(10 to 0, state.form!!.hour to state.form!!.minute)
        assertEquals(
            listOf("2026-10-22", "2026-10-21", "2026-10-20", "2026-10-19", "2026-10-18", "2026-10-17", "2026-10-16"),
            state.dayOptions.map { it.date }
        )
        assertEquals(DayLabel.Today, state.dayOptions[0].label)
        assertEquals(DayLabel.Yesterday, state.dayOptions[1].label)
        assertEquals(DayLabel.Date("2026-10-20"), state.dayOptions[2].label)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `a past day defaults to 18-00 unless a time was chosen`() = runTest {
        val viewModel = newViewModel()

        viewModel.handleIntent(LogActivityIntent.OnDateSelected("2026-10-20"))
        assertEquals(18 to 0, viewModel.state.value.form!!.let { it.hour to it.minute })

        viewModel.handleIntent(LogActivityIntent.OnTimeSelected(7, 15))
        viewModel.handleIntent(LogActivityIntent.OnDateSelected("2026-10-19"))
        assertEquals(7 to 15, viewModel.state.value.form!!.let { it.hour to it.minute })

        // Days outside the options are ignored
        viewModel.handleIntent(LogActivityIntent.OnDateSelected("2026-10-10"))
        assertEquals("2026-10-19", viewModel.state.value.form!!.date)
    }

    @Test
    fun `saving a valid activity stores it and goes back`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)
        viewModel.fillValid()
        viewModel.handleIntent(LogActivityIntent.OnDateSelected("2026-10-21"))
        viewModel.handleIntent(LogActivityIntent.OnNoteChanged("Basketball\nwith friends"))

        viewModel.handleIntent(LogActivityIntent.OnSave)

        val stored = dao.rows.value.single()
        assertEquals("ACTIVITY", stored.source)
        assertEquals("TEAM_SPORT", stored.activityType)
        assertEquals("HARD", stored.intensity)
        assertEquals(3_600, stored.durationSeconds)
        assertEquals("2026-10-21", stored.dateLocal)
        assertEquals(1_792_605_600_000L, stored.completedAt) // 21 October 18:00Z
        assertEquals("Basketball\nwith friends", stored.note)
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
    }

    @Test
    fun `saving with missing fields shows the errors, which then follow the edits`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(
            setOf(ActivityEntryError.TYPE_MISSING, ActivityEntryError.DURATION_OUT_OF_RANGE, ActivityEntryError.INTENSITY_MISSING),
            viewModel.state.value.errors
        )
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(LogActivityIntent.OnTypeSelected(ActivityType.OTHER))
        assertEquals(
            setOf(ActivityEntryError.NAME_MISSING, ActivityEntryError.DURATION_OUT_OF_RANGE, ActivityEntryError.INTENSITY_MISSING),
            viewModel.state.value.errors
        )
    }

    @Test
    fun `a future time is rejected`() = runTest {
        val viewModel = newViewModel()
        viewModel.fillValid()
        viewModel.handleIntent(LogActivityIntent.OnTimeSelected(11, 0))

        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(setOf(ActivityEntryError.DATE_IN_FUTURE), viewModel.state.value.errors)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `fields are filtered and capped`() = runTest {
        val viewModel = newViewModel()

        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("4a5"))
        assertEquals("45", viewModel.state.value.form!!.durationText)
        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("12345"))
        assertEquals("123", viewModel.state.value.form!!.durationText)
        viewModel.handleIntent(LogActivityIntent.OnCustomNameChanged("n".repeat(50)))
        assertEquals(40, viewModel.state.value.form!!.customName.length)
        viewModel.handleIntent(LogActivityIntent.OnNoteChanged("x".repeat(250)))
        assertEquals(200, viewModel.state.value.form!!.note.length)
    }

    @Test
    fun `under 10 minutes shows the hint that it won't count`() = runTest {
        val viewModel = newViewModel()

        listOf("" to false, "0" to false, "1" to true, "9" to true, "10" to false, "45" to false).forEach { (text, hint) ->
            viewModel.handleIntent(LogActivityIntent.OnDurationChanged(text))
            assertEquals("duration '$text'", hint, viewModel.state.value.showsShortDurationHint)
        }
    }

    // ------------------------------------------------------------------
    // Edit and delete
    // ------------------------------------------------------------------

    private suspend fun storedActivityId(): Long {
        val viewModel = newViewModel()
        viewModel.handleIntent(LogActivityIntent.OnTypeSelected(ActivityType.OTHER))
        viewModel.handleIntent(LogActivityIntent.OnCustomNameChanged("Climbing"))
        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("50"))
        viewModel.handleIntent(LogActivityIntent.OnIntensitySelected(Intensity.MODERATE))
        viewModel.handleIntent(LogActivityIntent.OnDateSelected("2026-10-20"))
        viewModel.handleIntent(LogActivityIntent.OnSave)
        return dao.rows.value.single().id
    }

    @Test
    fun `editing loads the entry, and saving keeps uuid and createdAt`() = runTest {
        val id = storedActivityId()
        val created = dao.rows.value.single()
        now += 60 * 60 * 1000L
        val viewModel = editViewModel(id)
        val effects = collectEffects(viewModel)

        val form = viewModel.state.value.form!!
        assertTrue(viewModel.state.value.isEditMode)
        assertEquals(ActivityType.OTHER, form.type)
        assertEquals("Climbing", form.customName)
        assertEquals("50", form.durationText)
        assertEquals(Intensity.MODERATE, form.intensity)
        assertEquals("2026-10-20", form.date)
        assertEquals(18 to 0, form.hour to form.minute)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("70"))
        viewModel.handleIntent(LogActivityIntent.OnSave)

        val updated = dao.rows.value.single()
        assertEquals(created.uuid, updated.uuid)
        assertEquals(created.createdAt, updated.createdAt)
        assertEquals(now, updated.updatedAt)
        assertEquals(70 * 60, updated.durationSeconds)
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
    }

    @Test
    fun `an entry older than the window can still be edited on its own day`() = runTest {
        val id = storedActivityId() // 20 October
        now += 5 * DAY // Tuesday 27: the window starts on the 21st
        val viewModel = editViewModel(id)

        assertEquals("2026-10-20", viewModel.state.value.dayOptions.last().date)
        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("55"))
        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(55 * 60, dao.rows.value.single().durationSeconds)
    }

    @Test
    fun `automatic entries and missing ids show not found`() = runTest {
        repository.record(TrainingSource.GYM, "gym:1", "Pull Day", 1_800)

        assertTrue(editViewModel(1).state.value.isNotFound)
        assertTrue(editViewModel(99).state.value.isNotFound)
    }

    @Test
    fun `delete asks for confirmation, then removes the entry and goes back`() = runTest {
        val id = storedActivityId()
        val viewModel = editViewModel(id)
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(LogActivityIntent.OnDeleteClicked)
        assertTrue(viewModel.state.value.showDeleteConfirmation)
        viewModel.handleIntent(LogActivityIntent.OnDismissDelete)
        assertFalse(viewModel.state.value.showDeleteConfirmation)
        assertEquals(1, dao.rows.value.size)

        viewModel.handleIntent(LogActivityIntent.OnDeleteClicked)
        viewModel.handleIntent(LogActivityIntent.OnConfirmDelete)

        assertTrue(dao.rows.value.isEmpty())
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
    }

    @Test
    fun `delete is not offered when creating`() = runTest {
        val viewModel = newViewModel()

        viewModel.handleIntent(LogActivityIntent.OnDeleteClicked)

        assertFalse(viewModel.state.value.showDeleteConfirmation)
    }

    // ------------------------------------------------------------------
    // Unsaved changes
    // ------------------------------------------------------------------

    @Test
    fun `back without changes leaves directly`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)

        viewModel.handleIntent(LogActivityIntent.OnBackPressed)

        assertFalse(viewModel.state.value.showDiscardConfirmation)
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
    }

    @Test
    fun `back with changes asks first`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)
        viewModel.handleIntent(LogActivityIntent.OnTypeSelected(ActivityType.YOGA))
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        viewModel.handleIntent(LogActivityIntent.OnBackPressed)
        assertTrue(viewModel.state.value.showDiscardConfirmation)
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(LogActivityIntent.OnDismissDiscard)
        assertFalse(viewModel.state.value.showDiscardConfirmation)
        assertTrue(effects.isEmpty())

        viewModel.handleIntent(LogActivityIntent.OnBackPressed)
        viewModel.handleIntent(LogActivityIntent.OnConfirmDiscard)
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `undoing a change means no unsaved changes`() = runTest {
        val id = storedActivityId()
        val viewModel = editViewModel(id)

        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("51"))
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        viewModel.handleIntent(LogActivityIntent.OnDurationChanged("50"))
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertNull(viewModel.state.value.form!!.note.takeIf { it.isNotEmpty() })
    }

    // ------------------------------------------------------------------
    // Helpers and fakes
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Photo
    // ------------------------------------------------------------------

    private val galleryPhoto = TrainingPhotoSource("content://picker/1", fromCamera = false)

    @Test
    fun `a picked photo is processed at once, shown in the form and attached on save`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)
        viewModel.fillValid()

        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        val photo = viewModel.state.value.form!!.photo as FormPhoto.Staged
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        assertTrue(photos.committed.isEmpty())

        viewModel.handleIntent(LogActivityIntent.OnSave)

        val id = dao.rows.value.single().id
        assertEquals(listOf(id to photo.staged), photos.committed)
        assertEquals(listOf(LogActivityEffect.NavigateBack), effects)
    }

    @Test
    fun `a photo that can't be read is reported and the form keeps its photo`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)
        photos.error = TrainingPhotoError.UNREADABLE_IMAGE

        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))

        assertEquals(FormPhoto.None, viewModel.state.value.form!!.photo)
        assertFalse(viewModel.state.value.isProcessingPhoto)
        assertEquals(listOf<LogActivityEffect>(LogActivityEffect.ShowPhotoError(TrainingPhotoError.UNREADABLE_IMAGE)), effects)
    }

    @Test
    fun `picking again or removing discards the unsaved photo`() = runTest {
        val viewModel = newViewModel()
        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        val first = (viewModel.state.value.form!!.photo as FormPhoto.Staged).staged

        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        val second = (viewModel.state.value.form!!.photo as FormPhoto.Staged).staged
        viewModel.handleIntent(LogActivityIntent.OnRemovePhoto)

        assertEquals(listOf(first, second), photos.discarded)
        assertEquals(FormPhoto.None, viewModel.state.value.form!!.photo)
    }

    @Test
    fun `leaving without saving discards the unsaved photo`() = runTest {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LogActivityViewModel(null, repository, dates, photos) as T
        }
        val viewModel = ViewModelProvider(store, factory)[LogActivityViewModel::class.java]
        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        val staged = (viewModel.state.value.form!!.photo as FormPhoto.Staged).staged

        store.clear() // calls onCleared()

        assertEquals(listOf(staged), photos.discarded)
        assertTrue(photos.committed.isEmpty())
    }

    @Test
    fun `editing shows the stored photo and replacing it commits the new one`() = runTest {
        val id = storedActivityId()
        repository.setPhotoPath(id, "training_photos/uuid-1-1.jpg")
        val viewModel = editViewModel(id)
        assertEquals(FormPhoto.Stored("training_photos/uuid-1-1.jpg"), viewModel.state.value.form!!.photo)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(listOf(id), photos.committed.map { it.first })
        assertTrue(photos.removed.isEmpty())
    }

    @Test
    fun `editing can remove the photo, on save`() = runTest {
        val id = storedActivityId()
        repository.setPhotoPath(id, "training_photos/uuid-1-1.jpg")
        val viewModel = editViewModel(id)

        viewModel.handleIntent(LogActivityIntent.OnRemovePhoto)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        assertTrue(photos.removed.isEmpty())
        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(listOf(id), photos.removed)
    }

    @Test
    fun `saving without touching the photo leaves it alone`() = runTest {
        val id = storedActivityId()
        repository.setPhotoPath(id, "training_photos/uuid-1-1.jpg")
        val viewModel = editViewModel(id)

        viewModel.handleIntent(LogActivityIntent.OnNoteChanged("New note"))
        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertTrue(photos.committed.isEmpty())
        assertTrue(photos.removed.isEmpty())
        assertEquals("training_photos/uuid-1-1.jpg", dao.rows.value.single().photoPath)
    }

    @Test
    fun `a photo that can't be saved never loses the activity`() = runTest {
        val viewModel = newViewModel()
        val effects = collectEffects(viewModel)
        viewModel.fillValid()
        viewModel.handleIntent(LogActivityIntent.OnPhotoSelected(galleryPhoto))
        photos.error = TrainingPhotoError.NO_SPACE

        viewModel.handleIntent(LogActivityIntent.OnSave)

        assertEquals(1, dao.rows.value.size)
        assertEquals(
            listOf(LogActivityEffect.ShowPhotoError(TrainingPhotoError.NO_SPACE), LogActivityEffect.NavigateBack),
            effects
        )
    }

    private fun TestScope.collectEffects(viewModel: LogActivityViewModel): List<LogActivityEffect> {
        val effects = mutableListOf<LogActivityEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }
        return effects
    }

    private class FakeTrainingLogDao : TrainingLogDao {
        val rows = MutableStateFlow<List<TrainingLogEntity>>(emptyList())

        override suspend fun insert(entry: TrainingLogEntity): Long {
            val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
            rows.value = rows.value + entry.copy(id = id)
            return id
        }

        override fun observeAll(): Flow<List<TrainingLogEntity>> = rows

        override fun observeBetween(fromDate: String, toDate: String): Flow<List<TrainingLogEntity>> = rows

        override suspend fun getById(id: Long): TrainingLogEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByUuid(uuid: String): TrainingLogEntity? = rows.value.firstOrNull { it.uuid == uuid }

        override suspend fun update(entry: TrainingLogEntity): Int {
            rows.value = rows.value.map { if (it.id == entry.id) entry else it }
            return 1
        }

        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }

        override suspend fun updatePhotoPath(id: Long, photoPath: String?, updatedAt: Long): Int {
            val before = rows.value
            rows.value = before.map { if (it.id == id) it.copy(photoPath = photoPath, updatedAt = updatedAt) else it }
            return before.count { it.id == id }
        }

        override suspend fun getPhotoPaths(): List<String> = rows.value.mapNotNull { it.photoPath }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
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

    /** Records the photos deleted through [TrainingPhotoFiles.deletePhoto]; nothing else is used. */
    private class FakePhotoFiles : TrainingPhotoFiles {
        val deletedPhotos = mutableListOf<String>()
        override fun fileOf(relativePath: String) = File(relativePath)
        override fun newStagedPhoto() = throw UnsupportedOperationException()
        override fun captureFile() = throw UnsupportedOperationException()
        override fun deleteCapture() = Unit
        override fun deletePhoto(photoPath: String) {
            deletedPhotos += photoPath
        }
        override fun storedFiles(): List<String> = emptyList()
        override fun deleteStoredFile(relativePath: String) = Unit
        override fun deleteStaleTemporaryFiles(olderThanMillis: Long) = Unit
    }
}
