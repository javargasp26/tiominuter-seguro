package com.javargasp.tiominutermvi.di

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.javargasp.tiominutermvi.data.auth.DefaultLocalUserDataWiper
import com.javargasp.tiominutermvi.data.auth.FirebaseAuthRepository
import com.javargasp.tiominutermvi.data.auth.SharedPreferencesAuthPreferences
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.domain.auth.AccountGroupsCleaner
import com.javargasp.tiominutermvi.domain.auth.AuthPreferences
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.ExistingInstallMigration
import com.javargasp.tiominutermvi.domain.auth.GuestBanner
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.domain.auth.UserDataEraser
import com.javargasp.tiominutermvi.ui.account.AccountViewModel
import com.javargasp.tiominutermvi.ui.welcome.WelcomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module of the account feature (identity only: Firebase Auth with Google, guest mode,
 * sign-out, account deletion with an optional local wipe). Uses the databases, DAOs, the training
 * photo repository and WallClock from [workoutModule] and ProgramDataCleaner from [programModule].
 */
val authModule = module {

    single<AuthPreferences> {
        SharedPreferencesAuthPreferences(
            androidContext().getSharedPreferences(SharedPreferencesAuthPreferences.FILE_NAME, Context.MODE_PRIVATE)
        )
    }

    // Before anything opens a database: an existing database means an update from a version without accounts
    factory {
        val context = androidContext()
        ExistingInstallMigration(get()) {
            context.getDatabasePath(TioMinuterDatabase.DATABASE_NAME).exists() ||
                context.getDatabasePath(ProgramDatabase.DATABASE_NAME).exists()
        }
    }

    single<LocalUserDataWiper> {
        val database = get<TioMinuterDatabase>()
        DefaultLocalUserDataWiper(
            inTransaction = { block -> database.withTransaction { block() } },
            trainingLogDao = get(),
            weeklyGoalDao = get(),
            gymProgressDao = get(),
            gymSessionDao = get(),
            dailySessionDao = get(),
            syncOutboxDao = database.syncOutboxDao(),
            feedShareDao = database.feedShareDao(),
            dailyProgressDao = database.dailyProgressDao(),
            programs = get(),
            photos = get()
        )
    }

    // The eraser (the sync coordinator) and the groups cleaner need the AuthRepository too: both are
    // resolved when an account is deleted
    single<AuthRepository> {
        val koin = getKoin()
        FirebaseAuthRepository(
            auth = FirebaseAuth.getInstance(),
            wiper = get(),
            eraser = UserDataEraser { uid -> koin.get<UserDataEraser>().eraseServerData(uid) },
            groupsCleaner = AccountGroupsCleaner { uid -> koin.get<AccountGroupsCleaner>().leaveAllGroups(uid) }
        )
    }

    factory { GuestBanner(get(), get(), get()) }

    viewModel { WelcomeViewModel(get(), get()) }
    viewModel { AccountViewModel(get(), get()) }
}
