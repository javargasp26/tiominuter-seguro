package com.javargasp.tiominutermvi.di

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import com.javargasp.tiominutermvi.data.feedback.AndroidWorkoutFeedback
import com.javargasp.tiominutermvi.data.goal.WeeklyGoalRepositoryImpl
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.local.dao.DailyRoutineDao
import com.javargasp.tiominutermvi.data.local.dao.DailySessionDao
import com.javargasp.tiominutermvi.data.local.dao.ExerciseDao
import com.javargasp.tiominutermvi.data.local.dao.GymProgressDao
import com.javargasp.tiominutermvi.data.local.dao.GymRoutineDao
import com.javargasp.tiominutermvi.data.local.dao.GymSessionDao
import com.javargasp.tiominutermvi.data.local.dao.TrainingLogDao
import com.javargasp.tiominutermvi.data.local.dao.WeeklyGoalDao
import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.DailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.GymRemoteDataSource
import com.javargasp.tiominutermvi.data.repository.DailyActiveSessionStore
import com.javargasp.tiominutermvi.data.repository.GymProgressRepositoryImpl
import com.javargasp.tiominutermvi.data.repository.GymRoutineRepositoryImpl
import com.javargasp.tiominutermvi.data.repository.RoomGymSessionStore
import com.javargasp.tiominutermvi.data.repository.WorkoutRepositoryImpl
import com.javargasp.tiominutermvi.data.traininglog.RoomWorkoutShareStore
import com.javargasp.tiominutermvi.data.traininglog.TrainingLogRepositoryImpl
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutShareStore
import com.javargasp.tiominutermvi.ui.components.share.ShareInGroupsViewModel
import com.javargasp.tiominutermvi.data.traininglog.photo.AndroidTrainingPhotoProcessor
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoRepositoryImpl
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.domain.goal.ObserveCurrentWeek
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionRepository
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionTracker
import com.javargasp.tiominutermvi.domain.repository.GymProgressRepository
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import com.javargasp.tiominutermvi.domain.session.InactiveSessionCloser
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogs
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoProcessor
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.ui.dailyroutineselection.DailyRoutineSelectionViewModel
import com.javargasp.tiominutermvi.ui.gymexercise.GymExerciseViewModel
import com.javargasp.tiominutermvi.ui.gymroutine.GymRoutineViewModel
import com.javargasp.tiominutermvi.ui.gymroutineselection.GymRoutineSelectionViewModel
import com.javargasp.tiominutermvi.ui.home.HomeViewModel
import com.javargasp.tiominutermvi.ui.logactivity.LogActivityViewModel
import com.javargasp.tiominutermvi.ui.traininglog.TrainingHistoryViewModel
import com.javargasp.tiominutermvi.ui.weeklygoal.WeeklyGoalViewModel
import com.javargasp.tiominutermvi.ui.workout.WorkoutViewModel
import com.javargasp.tiominutermvi.ui.workout.WorkoutCompletionViewModel
import com.javargasp.tiominutermvi.data.daily.DailyProgressRepositoryImpl
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.qualifier.named

/**
 * Koin module for providing workout-related dependencies. The remote data sources read the
 * content through the ContentSource of [contentModule].
 */
val workoutModule = module {
    
    single<WorkoutRepository> {
        WorkoutRepositoryImpl(get(), get())
    }

    single<WorkoutFeedback> {
        AndroidWorkoutFeedback(androidContext())
    }
    
    single<GymRemoteDataSource> {
        AssetGymRemoteDataSource(get())
    }

    single<DailyRemoteDataSource> {
        AssetDailyRemoteDataSource(get())
    }

    single<TioMinuterDatabase> {
        Room.databaseBuilder(
            androidContext(),
            TioMinuterDatabase::class.java,
            TioMinuterDatabase.DATABASE_NAME
        ).build()
    }

    single<GymRoutineDao> { get<TioMinuterDatabase>().gymRoutineDao() }
    single<ExerciseDao> { get<TioMinuterDatabase>().exerciseDao() }
    single<GymProgressDao> { get<TioMinuterDatabase>().gymProgressDao() }
    single<DailyRoutineDao> { get<TioMinuterDatabase>().dailyRoutineDao() }
    single<TrainingLogDao> { get<TioMinuterDatabase>().trainingLogDao() }
    single<WeeklyGoalDao> { get<TioMinuterDatabase>().weeklyGoalDao() }
    single<GymSessionDao> { get<TioMinuterDatabase>().gymSessionDao() }
    single<DailySessionDao> { get<TioMinuterDatabase>().dailySessionDao() }

    single<WallClock> { WallClock { System.currentTimeMillis() } }

    single<MonotonicTimeSource> { MonotonicTimeSource { SystemClock.elapsedRealtime() } }

    single { LocalDateProvider(get()) }

    // Training photos (local only): files, processing and the repository (also used by programModule)
    single<TrainingPhotoFiles> { TrainingPhotoFileStore(androidContext().filesDir, androidContext().cacheDir) }
    single<TrainingPhotoProcessor> { AndroidTrainingPhotoProcessor(androidContext().contentResolver) }
    single<TrainingPhotoRepository> { TrainingPhotoRepositoryImpl(get(), get(), get(), get()) }

    // One instance behind both interfaces (also used by programModule)
    // The group feed's queue, written in each entry's transaction; only while signed in
    single {
        RoomWorkoutShareStore(get<TioMinuterDatabase>().feedShareDao(), get()) {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        }
    }
    single<WorkoutShareStore> { get<RoomWorkoutShareStore>() }
    single {
        TrainingLogRepositoryImpl(get(), get(), get(), get(), changes = get(named(SyncQualifiers.APP)), shares = get<RoomWorkoutShareStore>())
    }
    single<TrainingLogRepository> { get<TrainingLogRepositoryImpl>() }
    single<TrainingLogRecorder> { get<TrainingLogRepositoryImpl>() }
    single<SessionEntryRecorder> { get<TrainingLogRepositoryImpl>() }

    single<DailyProgressRepository> {
        DailyProgressRepositoryImpl(get<TioMinuterDatabase>().dailyProgressDao(), get(), get(named(SyncQualifiers.APP)))
    }

    single<WeeklyGoalRepository> { WeeklyGoalRepositoryImpl(get(), get(), get(), get(named(SyncQualifiers.APP))) }
    factory { ObserveCurrentWeek(get(), get(), get()) }

    single<GymRoutineRepository> {
        GymRoutineRepositoryImpl(get(), get())
    }

    single<GymProgressRepository> {
        GymProgressRepositoryImpl(get(), get())
    }

    // A single: its lock makes closing a session idempotent across screens
    single<GymSessionRepository> { GymSessionTracker(RoomGymSessionStore(get()), get(), get()) }
    single(named(SessionQualifiers.DAILY)) {
        ActiveSessionTracker(DailyActiveSessionStore(get(), get()), get(), "DailySession")
    }
    
    // Home closes every kind of inactive session (the program's closer comes from programModule)
    // and reads my groups for its banner (groupsModule)
    viewModel {
        HomeViewModel(
            get(), get(), get(), get(),
            sessionClosers = listOf(
                get<GymSessionRepository>(),
                get<ActiveSessionTracker>(named(SessionQualifiers.DAILY)),
                get<InactiveSessionCloser>(named(SessionQualifiers.PROGRAM))
            ),
            dailyProgress = get(),
            // From groupsModule: the "already trained today" banner and the summary triggers
            groupsHome = get()
        )
    }
    viewModel { DailyRoutineSelectionViewModel(get(), get(named(SessionQualifiers.DAILY)), get()) }
    viewModel { GymRoutineSelectionViewModel(get(), get()) }
    viewModel { (routineId: Int) -> WorkoutViewModel(get(), routineId, get(), get(), get(named(SessionQualifiers.DAILY)), get()) }
    viewModel { (routineId: Int) -> WorkoutCompletionViewModel(routineId, get(), get()) }
    viewModel { (routineId: Int) -> GymRoutineViewModel(routineId, get(), get(), get(), get()) }
    // Workout names (WorkoutNameResolver's catalog): the gym and daily catalogs plus the program workouts
    single { WorkoutNameCatalogs(get(), get(), get()) }
    single<WorkoutNameCatalogSource> { get<WorkoutNameCatalogs>() }
    viewModel { TrainingHistoryViewModel(get(), get(), get(), get(), get()) }
    viewModel { WeeklyGoalViewModel(get(), get()) }
    // No parameter: log a new activity; a Long: edit that entry
    viewModel { params -> LogActivityViewModel(params.getOrNull<Long>(), get(), get(), get(), get()) }
    viewModel { ShareInGroupsViewModel(get(), get()) }
    viewModel { (routineId: Int, position: Int) ->
        GymExerciseViewModel(routineId, position, get(), get(), get(), get(), get())
    }
}