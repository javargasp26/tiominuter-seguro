package com.javargasp.tiominutermvi.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.javargasp.tiominutermvi.domain.sync.NetworkMonitor
import com.javargasp.tiominutermvi.domain.sync.SyncOutcome
import com.javargasp.tiominutermvi.domain.sync.SyncRepository
import com.javargasp.tiominutermvi.domain.sync.SyncScheduler
import com.javargasp.tiominutermvi.domain.sync.SyncStateStore
import com.javargasp.tiominutermvi.domain.sync.SyncTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * [SyncStateStore] in SharedPreferences (file `sync_state`, like the auth preferences): small,
 * synchronous, no dependency. Keys per account: `wm|<uid>|<collection>` (the watermark as
 * "seconds:nanos") and `ok|<uid>` (the last success, WallClock millis).
 */
class SharedPreferencesSyncStateStore(context: Context) : SyncStateStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun lastSyncedUid(): String? = prefs.getString(LAST_UID, null)

    override fun setLastSyncedUid(uid: String?) = prefs.edit { if (uid == null) remove(LAST_UID) else putString(LAST_UID, uid) }

    override fun watermark(uid: String, key: String): SyncTime? = SyncTime.decode(prefs.getString("wm|$uid|$key", null))

    override fun setWatermark(uid: String, key: String, time: SyncTime) = prefs.edit { putString("wm|$uid|$key", time.encode()) }

    override fun lastSuccessAt(uid: String): Long? = if (prefs.contains("ok|$uid")) prefs.getLong("ok|$uid", 0) else null

    override fun setLastSuccessAt(uid: String, millis: Long) = prefs.edit { putLong("ok|$uid", millis) }

    override fun forget(uid: String) = prefs.edit {
        prefs.all.keys.filter { it.startsWith("wm|$uid|") || it == "ok|$uid" }.forEach { remove(it) }
    }

    override fun erasedAccount(): String? = prefs.getString(ERASED_UID, null)

    override fun setErasedAccount(uid: String?) = prefs.edit { if (uid == null) remove(ERASED_UID) else putString(ERASED_UID, uid) }

    private companion object {
        const val FILE = "sync_state"
        const val ERASED_UID = "erasedUid"
        const val LAST_UID = "lastSyncedUid"
    }
}

/** [NetworkMonitor] on ConnectivityManager (the ACCESS_NETWORK_STATE permission is already declared). */
class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    override fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * [SyncScheduler] on WorkManager: one unique one-time work ([WORK_NAME], KEEP) that runs when
 * there is a network, retried with exponential backoff while it fails.
 */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    override fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "firestore-sync"
        private const val BACKOFF_SECONDS = 30L
    }
}

/** The network-constrained sync: success when the sync worked (or there is no account), else retry. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: SyncRepository by inject()

    override suspend fun doWork(): Result = when (sync.syncNow()) {
        SyncOutcome.SUCCESS, SyncOutcome.SKIPPED -> Result.success()
        SyncOutcome.OFFLINE, SyncOutcome.FAILED -> Result.retry()
    }
}
