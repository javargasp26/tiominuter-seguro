package com.javargasp.tiominutermvi.di

import androidx.room.Room
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.remote.ProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.program.repository.ProgramRepositoryImpl
import com.javargasp.tiominutermvi.data.program.repository.RoomProgramWorkoutTitleCodes
import com.javargasp.tiominutermvi.domain.program.repository.ProgramDataCleaner
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.program.repository.ProgramWorkoutTitles
import com.javargasp.tiominutermvi.ui.program.common.ResourceProgramWorkoutTitles
import com.javargasp.tiominutermvi.ui.program.circuit.ProgramCircuitViewModel
import com.javargasp.tiominutermvi.ui.program.create.ProgramCreateViewModel
import com.javargasp.tiominutermvi.ui.program.detail.ProgramDetailViewModel
import com.javargasp.tiominutermvi.ui.program.list.ProgramListViewModel
import com.javargasp.tiominutermvi.ui.program.outdoorrun.ProgramOutdoorRunViewModel
import com.javargasp.tiominutermvi.ui.program.strength.ProgramStrengthViewModel
import com.javargasp.tiominutermvi.ui.program.strengthexercise.ProgramStrengthExerciseViewModel
import com.javargasp.tiominutermvi.ui.program.treadmill.ProgramTreadmillViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.javargasp.tiominutermvi.domain.session.InactiveSessionCloser
import com.javargasp.tiominutermvi.domain.sync.ProgramRefMapper
import com.javargasp.tiominutermvi.domain.traininglog.ProgramWorkoutTitleCodes
import com.javargasp.tiominutermvi.domain.sync.LocalSyncStore
import com.javargasp.tiominutermvi.data.sync.RoomChangeTracker
import com.javargasp.tiominutermvi.data.program.sync.ProgramSyncStore

/**
 * Koin module of the Program feature. Uses ContentSource from [contentModule] and WorkoutFeedback,
 * WallClock, MonotonicTimeSource, TrainingLogRecorder and TrainingPhotoRepository from
 * [workoutModule] without redefining them.
 */
val programModule = module {

    // The program content files, so the content sync checks downloads with the program decoders
    single(named(AssetProgramRemoteDataSource.EXERCISES_FILE)) { AssetProgramRemoteDataSource.EXERCISES_CONTENT }
    single(named(AssetProgramRemoteDataSource.TEMPLATES_FILE)) { AssetProgramRemoteDataSource.TEMPLATES_CONTENT }

    single<ProgramRemoteDataSource> { AssetProgramRemoteDataSource(get()) }

    single<ProgramDatabase> {
        Room.databaseBuilder(
            androidContext(),
            ProgramDatabase::class.java,
            ProgramDatabase.DATABASE_NAME
        ).build()
    }

    single<ProgramWorkoutTitles> { ResourceProgramWorkoutTitles(androidContext()) }
    // The core's workout names: what a PROGRAM entry's refId points to (history, feed titleKey)
    single<ProgramWorkoutTitleCodes> { RoomProgramWorkoutTitleCodes(get<ProgramDatabase>().programDao()) }

    // One instance behind both interfaces; ProgramDataCleaner is used only by the app's local wipe
    single {
        val database = get<ProgramDatabase>()
        ProgramRepositoryImpl(
            get(), get(), get(), get(), get(), get(),
            changes = RoomChangeTracker(database, database.syncOutboxDao(), get())
        )
    }
    // The feature's sync-facing side: its LocalSyncStore and the PROGRAM refId mapping
    single { ProgramSyncStore(get(), get()) }
    single<LocalSyncStore>(named(SyncQualifiers.PROGRAM)) { get<ProgramSyncStore>() }
    single<ProgramRefMapper> { get<ProgramSyncStore>() }
    single<ProgramRepository> { get<ProgramRepositoryImpl>() }
    single<ProgramDataCleaner> { get<ProgramRepositoryImpl>() }
    // Sessions: the app-start check (Home) closes inactive program sessions; cardio screens use the tracker
    single<InactiveSessionCloser>(named(SessionQualifiers.PROGRAM)) { get<ProgramRepositoryImpl>() }
    single(named(SessionQualifiers.PROGRAM_CARDIO)) { get<ProgramRepositoryImpl>().cardioSessions }

    viewModel { ProgramListViewModel(get()) }
    viewModel { ProgramCreateViewModel(get()) }
    viewModel { (programId: Long) -> ProgramDetailViewModel(programId, get()) }
    viewModel { (programId: Long, week: Int, day: Int) -> ProgramStrengthViewModel(programId, week, day, get(), get()) }
    viewModel { (programId: Long, week: Int, day: Int, position: Int) ->
        ProgramStrengthExerciseViewModel(programId, week, day, position, get(), get(), get())
    }
    viewModel { (programId: Long, week: Int, day: Int) ->
        ProgramTreadmillViewModel(programId, week, day, get(), get(), get(), get(), get(named(SessionQualifiers.PROGRAM_CARDIO)))
    }
    viewModel { (programId: Long, week: Int, day: Int) ->
        ProgramOutdoorRunViewModel(programId, week, day, get(), get(), get(), get(named(SessionQualifiers.PROGRAM_CARDIO)))
    }
    viewModel { (programId: Long, week: Int, day: Int) ->
        ProgramCircuitViewModel(programId, week, day, get(), get(), get(), get(), get(named(SessionQualifiers.PROGRAM_CARDIO)))
    }
}
