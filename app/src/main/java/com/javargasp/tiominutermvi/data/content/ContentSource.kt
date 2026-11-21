package com.javargasp.tiominutermvi.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The single read path of the content JSON files: every data source reads them through it,
 * never from the assets directly.
 */
interface ContentSource {
    /** Text of [path] ("exercises.json") in the active version: the verified cached file, else the bundled one. */
    suspend fun readText(path: String): String
}

/** Reads [file] from this source and decodes it with the app's decoder. */
suspend fun <T> ContentSource.read(file: ContentJsonFile<T>): T = file.decode(readText(file.path))

/**
 * The active version as this process sees it, read once (at the first use): a version that a
 * sync activates later is used from the next launch, so screens never mix two versions.
 * Shared by [CachedContentSource] and [ContentAnimationLocator].
 */
class ActiveContent(private val store: ContentStore) {

    val manifest: LocalContentManifest? by lazy { store.readActive() }

    private val byPath: Map<String, ContentFileEntry> by lazy {
        manifest?.files.orEmpty().associateBy { it.path }
    }

    /** The cached entry of [path] in this process's version, if any. */
    fun entry(path: String): ContentFileEntry? = byPath[path]
}

/**
 * [ContentSource] over the cache and the APK assets. A cached JSON file is used only when the
 * active version was activated against this APK's bundled JSON ([BundledContentHashes.fingerprint]),
 * so an app update that ships newer content is never shadowed by an older download.
 */
class CachedContentSource(
    private val store: ContentStore,
    private val active: ActiveContent,
    private val bundled: BundledContent,
    private val bundledHashes: BundledContentHashes
) : ContentSource {

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        cachedText(path)
            ?: bundled.open(path)?.use { it.bufferedReader().readText() }
            ?: throw FileNotFoundException("No content file $path")
    }

    private fun cachedText(path: String): String? {
        val manifest = active.manifest ?: return null
        if (manifest.bundleFingerprint != bundledHashes.fingerprint) return null
        val entry = active.entry(path) ?: return null
        if (!store.hasObject(entry)) {
            println("ContentSource: cached $path is missing, using the bundled file")
            return null
        }
        return try {
            store.objectFile(entry.sha256).readText()
        } catch (e: IOException) {
            println("ContentSource: can't read cached $path, using the bundled file: $e")
            null
        }
    }
}
