package com.javargasp.tiominutermvi.data.content

import com.javargasp.tiominutermvi.data.program.remote.AssetProgramRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.EXERCISES_CONTENT
import java.io.File
import java.io.IOException
import java.io.InputStream

const val TEST_BASE_URL = "https://content.test/content/v1/"

/** Every content JSON file the app registers in Koin (core and program). */
val ALL_CONTENT_FILES: List<ContentJsonFile<*>> = listOf(
    EXERCISES_CONTENT,
    AssetDailyRemoteDataSource.DAILY_ROUTINES_CONTENT,
    AssetGymRemoteDataSource.GYM_ROUTINES_CONTENT,
    AssetProgramRemoteDataSource.EXERCISES_CONTENT,
    AssetProgramRemoteDataSource.TEMPLATES_CONTENT
)

/** [BundledContent] over a folder laid out like `content/v1` (the APK assets). */
class DirectoryBundledContent(private val dir: File) : BundledContent {
    override fun open(path: String): InputStream? = File(dir, path).takeIf { it.isFile }?.inputStream()

    override fun animationNames(): Set<String> = File(dir, "animations").list()?.toSet().orEmpty()
}

/** Fake Hosting: serves [files] by URL, records every request, and can go offline. */
class FakeContentHttpClient : ContentHttpClient {
    var online = true
    val files = mutableMapOf<String, ByteArray>()
    val requested = mutableListOf<String>()

    override suspend fun get(url: String, maxBytes: Long): ByteArray {
        requested += url
        if (!online) throw IOException("Unable to resolve host")
        val body = files[url] ?: throw IOException("HTTP 404 for $url")
        if (body.size > maxBytes) throw IOException("$url is larger than $maxBytes bytes")
        return body
    }

    /** Requests other than the manifest. */
    fun downloads(): List<String> = requested.filterNot { it.endsWith(ContentRules.MANIFEST_FILE) }

    /**
     * Publishes a version: the files at their URLs and a manifest listing them.
     * [entryOverrides] replaces the listed entry of a path (wrong hash, size...).
     */
    fun publish(
        contentVersion: String,
        content: Map<String, ByteArray>,
        entryOverrides: Map<String, ContentFileEntry> = emptyMap(),
        schemaVersion: Int = ContentRules.SCHEMA_VERSION
    ) {
        files.clear()
        val entries = content.map { (path, bytes) ->
            files[TEST_BASE_URL + path] = bytes
            entryOverrides[path] ?: ContentFileEntry(path, bytes.size.toLong(), sha256Hex(bytes))
        }
        val manifest = ContentManifest(schemaVersion, contentVersion, entries.sortedBy { it.path })
        files[TEST_BASE_URL + ContentRules.MANIFEST_FILE] =
            contentManifestJson.encodeToString(ContentManifest.serializer(), manifest).toByteArray()
    }
}

/** A [ContentStore] whose activation "crashes" right before the local manifest is written. */
class CrashingContentStore(root: File) : ContentStore(root) {
    override fun writeActive(manifest: LocalContentManifest) {
        throw IllegalStateException("Simulated crash before writing the local manifest")
    }
}

/** One app process: its own snapshot of the active version, as after a restart. */
class ContentProcess(
    bundleDir: File,
    val store: ContentStore,
    http: ContentHttpClient
) {
    private val bundled = DirectoryBundledContent(bundleDir)
    private val active = ActiveContent(store)
    private val hashes = BundledContentHashes(bundled, ALL_CONTENT_FILES.map { it.path })

    val source = CachedContentSource(store, active, bundled, hashes)
    val locator = ContentAnimationLocator(bundled, store, active)
    val syncer = ContentSyncer(TEST_BASE_URL, http, store, active, bundled, hashes, ALL_CONTENT_FILES)
}
