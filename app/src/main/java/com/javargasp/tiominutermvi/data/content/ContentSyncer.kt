package com.javargasp.tiominutermvi.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Outcome of a [ContentSyncer.sync], logged by the application. */
sealed interface ContentSyncResult {
    /** The published version is already active. */
    data class UpToDate(val contentVersion: String) : ContentSyncResult

    /** [contentVersion] was activated (used from the next launch). */
    data class Updated(val contentVersion: String, val downloadedFiles: Int, val cachedFiles: Int) : ContentSyncResult

    /** Nothing changed: the previous version stays active. */
    data class Failed(val reason: String, val cause: Throwable? = null) : ContentSyncResult
}

/**
 * Brings the published content into the cache:
 * 1. GET `manifest.json`; reject it when malformed, of another schemaVersion or with an invalid entry
 *    ([ContentRules.rejectionReason]); nothing to do when its contentVersion is already active.
 * 2. Pick the files the bundle can't serve: JSON files whose sha256 differs from the bundled one,
 *    and animations the APK doesn't bundle (a bundled animation is never downloaded). A file already
 *    stored with the same hash is reused.
 * 3. Download them into staging, checking size and sha256; decode every JSON file the version will
 *    serve from the cache with the app's own decoder ([ContentJsonFile]).
 * 4. Move them into `objects/`, then write the local manifest LAST.
 * Any failure discards the staging folder and leaves the previous version active.
 *
 * Runs once per process ([syncOnce]) in the background; repositories that already loaded keep
 * their content, so a new version is used from the next launch.
 */
class ContentSyncer(
    private val baseUrl: String,
    private val http: ContentHttpClient,
    private val store: ContentStore,
    private val active: ActiveContent,
    private val bundled: BundledContent,
    private val bundledHashes: BundledContentHashes,
    jsonFiles: List<ContentJsonFile<*>>
) {
    private val decoders = jsonFiles.associateBy { it.path }
    private val started = AtomicBoolean(false)

    /** [sync] the first time it is called in this process; null afterwards. */
    suspend fun syncOnce(): ContentSyncResult? = if (started.compareAndSet(false, true)) sync() else null

    suspend fun sync(): ContentSyncResult = withContext(Dispatchers.IO) {
        // Fix this process's version before anything can change it, then drop leftovers
        active.manifest
        bundled.animationNames()
        try {
            store.cleanUp()
            update()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RejectedContentException) {
            ContentSyncResult.Failed(e.message.orEmpty(), e.cause)
        } catch (e: Exception) {
            ContentSyncResult.Failed("${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            store.clearStaging()
        }
    }

    private suspend fun update(): ContentSyncResult {
        val manifest = fetchManifest()
        ContentRules.rejectionReason(manifest, decoders.keys)?.let { throw RejectedContentException("Manifest rejected: $it") }

        val current = store.readActive()?.takeIf { it.bundleFingerprint == bundledHashes.fingerprint }
        if (current?.contentVersion == manifest.contentVersion) {
            return ContentSyncResult.UpToDate(manifest.contentVersion)
        }

        val cached = manifest.files.filter(::needsCache)
        val toDownload = cached.filterNot(store::hasObject)

        store.startStaging()
        for (entry in toDownload) {
            val bytes = http.get(baseUrl + entry.path, entry.size)
            if (bytes.size.toLong() != entry.size) {
                throw RejectedContentException("${entry.path}: ${bytes.size} bytes, manifest says ${entry.size}")
            }
            if (sha256Hex(bytes) != entry.sha256) throw RejectedContentException("${entry.path}: sha256 mismatch")
            if (!ContentRules.isAnimation(entry.path)) checkDecodes(entry, bytes.decodeToString())
            store.writeStaged(entry.sha256, bytes)
        }
        // JSON reused from an earlier version must also suit this app's decoders
        for (entry in cached - toDownload.toSet()) {
            if (!ContentRules.isAnimation(entry.path)) checkDecodes(entry, store.objectFile(entry.sha256).readText())
        }

        store.promote(toDownload)
        store.writeActive(LocalContentManifest(manifest.contentVersion, bundledHashes.fingerprint, cached))
        return ContentSyncResult.Updated(manifest.contentVersion, toDownload.size, cached.size)
    }

    private fun checkDecodes(entry: ContentFileEntry, text: String) {
        try {
            decoders.getValue(entry.path).decode(text)
        } catch (e: Exception) {
            throw RejectedContentException("${entry.path} can't be decoded: ${e.message}", e)
        }
    }

    private suspend fun fetchManifest(): ContentManifest {
        val bytes = http.get(baseUrl + ContentRules.MANIFEST_FILE, ContentRules.MAX_MANIFEST_BYTES)
        return try {
            contentManifestJson.decodeFromString(ContentManifest.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            throw RejectedContentException("Malformed manifest: ${e.message}", e)
        }
    }

    /** True when the bundle can't serve [entry], so the version needs it in the cache. */
    private fun needsCache(entry: ContentFileEntry): Boolean =
        if (ContentRules.isAnimation(entry.path)) {
            ContentRules.animationName(entry.path) !in bundled.animationNames()
        } else {
            entry.sha256 != bundledHashes.sha256(entry.path)
        }

    private class RejectedContentException(message: String, cause: Throwable? = null) : IOException(message, cause)
}
