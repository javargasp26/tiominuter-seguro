package com.javargasp.tiominutermvi.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.javargasp.tiominutermvi.data.local.TioMinuterDatabase
import com.javargasp.tiominutermvi.data.sync.AndroidNetworkMonitor
import com.javargasp.tiominutermvi.data.sync.AppSyncStore
import com.javargasp.tiominutermvi.data.sync.FirestoreRemoteUserData
import com.javargasp.tiominutermvi.data.sync.RoomChangeTracker
import com.javargasp.tiominutermvi.data.sync.SharedPreferencesSyncStateStore
import com.javargasp.tiominutermvi.data.sync.WorkManagerSyncScheduler
import com.javargasp.tiominutermvi.domain.auth.UserDataEraser
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker
import com.javargasp.tiominutermvi.domain.sync.LocalSyncStore
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.sync.RemoteUserData
import com.javargasp.tiominutermvi.domain.sync.SyncCoordinator
import com.javargasp.tiominutermvi.domain.sync.SyncEngine
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncScheduler
import com.javargasp.tiominutermvi.domain.sync.SyncStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin qualifiers of the sync. */
object SyncQualifiers {
    /** The app database's ChangeTracker and LocalSyncStore (training log, weekly goals). */
    const val APP = "appSync"

    /** The program database's ChangeTracker and LocalSyncStore (programModule). */
    const val PROGRAM = "programSync"
}

/**
 * The Firestore sync (Phase 2b): Room stays the only source of truth; the coordinator, started by
 * TioMinuterApplication, syncs the signed-in account in the background.
 */
val syncModule = module {

    // Memory cache only: Room is the local copy, and every read asks the server
    single {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
    single<RemoteUserData> { FirestoreRemoteUserData(get()) }
    single<SyncStateStore> { SharedPreferencesSyncStateStore(androidContext()) }
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
    single<SyncScheduler> { WorkManagerSyncScheduler(androidContext()) }

    single<ChangeTracker>(named(SyncQualifiers.APP)) {
        val database = get<TioMinuterDatabase>()
        RoomChangeTracker(database, database.syncOutboxDao(), get())
    }
    single<LocalSyncStore>(named(SyncQualifiers.APP)) { AppSyncStore(get(), get(), get()) }

    // Programs first: PROGRAM refIds of the training log are mapped through their uuids
    single {
        SyncEngine(
            stores = listOf(get(named(SyncQualifiers.PROGRAM)), get(named(SyncQualifiers.APP))),
            remote = get(),
            state = get(),
            wiper = get(),
            clock = get(),
            network = get(),
            scheduler = get()
        )
    }
    single { SyncCoordinator(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<SyncRepository> { get<SyncCoordinator>() }
    single<UserDataEraser> { get<SyncCoordinator>() }
}
