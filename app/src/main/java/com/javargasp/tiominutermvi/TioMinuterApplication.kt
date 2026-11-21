package com.javargasp.tiominutermvi

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.javargasp.tiominutermvi.data.content.ContentSyncResult
import com.javargasp.tiominutermvi.data.content.ContentSyncer
import com.javargasp.tiominutermvi.di.authModule
import com.javargasp.tiominutermvi.di.contentModule
import com.javargasp.tiominutermvi.di.programModule
import com.javargasp.tiominutermvi.di.workoutModule
import com.javargasp.tiominutermvi.domain.auth.ExistingInstallMigration
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import com.javargasp.tiominutermvi.domain.sync.SyncCoordinator
import com.javargasp.tiominutermvi.di.syncModule
import com.javargasp.tiominutermvi.di.groupsModule
import com.javargasp.tiominutermvi.domain.groups.GroupsCoordinator

/**
 * Application class for initializing Koin dependency injection and Coil image loading.
 * At start it also deletes training-photo files no entry references and syncs the content with
 * Firebase Hosting (both in the background; a new content version is used from the next launch).
 */
class TioMinuterApplication : Application(), ImageLoaderFactory {

    /** Work that may outlive any screen; a failure in one job doesn't cancel the others. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TioMinuterApplication)
            modules(contentModule, workoutModule, programModule, authModule, syncModule, groupsModule)
        }
        // First, before the photo cleanup opens the database: updated installs start as guest
        GlobalContext.get().get<ExistingInstallMigration>().run()
        cleanUpTrainingPhotos()
        syncContent()
        // The Firestore sync of the signed-in account, in the background (never blocks the start)
        GlobalContext.get().get<SyncCoordinator>().start()
        // Groups: my weekly summary published to my groups, and a throttled index reconciliation
        GlobalContext.get().get<GroupsCoordinator>().start()
    }

    private fun syncContent() {
        val syncer = GlobalContext.get().get<ContentSyncer>()
        applicationScope.launch {
            when (val result = syncer.syncOnce()) {
                is ContentSyncResult.Updated -> Log.i(
                    TAG,
                    "Content ${result.contentVersion} activated (${result.downloadedFiles} files downloaded, " +
                        "${result.cachedFiles} served from the cache); used from the next launch"
                )
                is ContentSyncResult.UpToDate -> Log.i(TAG, "Content ${result.contentVersion} is up to date")
                is ContentSyncResult.Failed -> Log.w(TAG, "Content sync failed, keeping the active content: ${result.reason}", result.cause)
                null -> Unit
            }
        }
    }

    private fun cleanUpTrainingPhotos() {
        val photos = GlobalContext.get().get<TrainingPhotoRepository>()
        applicationScope.launch {
            try {
                val deleted = photos.cleanUp()
                if (deleted > 0) Log.i(TAG, "Deleted $deleted unreferenced training photo files")
            } catch (e: Exception) {
                Log.w(TAG, "Training photo cleanup failed", e)
            }
        }
    }
    
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
            .components {
                add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .respectCacheHeaders(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()

    private companion object {
        const val TAG = "TioMinuterApplication"
    }
}