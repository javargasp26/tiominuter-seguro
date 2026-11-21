package com.javargasp.tiominutermvi.data.content

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * On-disk layout of the downloaded content, under [root] (`filesDir/content/`, excluded from
 * backup and device transfer):
 * - `objects/<sha256>`: verified files named by their hash, so an activation never overwrites a
 *   file the active version uses;
 * - `staging/`: the downloads of a sync in progress (deleted when it ends and at the next start);
 * - `active.json`: the [LocalContentManifest] of the active version, written last (temp file + rename).
 *
 * Plain java.io, JVM-tested.
 */
open class ContentStore(val root: File) {

    private val objectsDir = File(root, "objects")
    private val stagingDir = File(root, "staging")
    private val activeFile = File(root, ACTIVE_FILE)
    private val activeTempFile = File(root, "$ACTIVE_FILE.tmp")

    /** The active version, or null when there is none or it can't be read. */
    fun readActive(): LocalContentManifest? {
        if (!activeFile.isFile) return null
        return try {
            contentManifestJson.decodeFromString(LocalContentManifest.serializer(), activeFile.readText())
        } catch (e: Exception) {
            println("ContentStore: unreadable $ACTIVE_FILE, using the bundled content: $e")
            null
        }
    }

    fun objectFile(sha256: String): File = File(objectsDir, sha256)

    /** True when [entry] is stored with its size (its hash was checked before it got its name). */
    fun hasObject(entry: ContentFileEntry): Boolean =
        objectFile(entry.sha256).let { it.isFile && it.length() == entry.size }

    /** Empties the staging folder, ready for a sync. */
    fun startStaging() {
        clearStaging()
        if (!stagingDir.mkdirs()) throw IOException("Can't create $stagingDir")
    }

    /** Writes [bytes] (already verified to hash to [sha256]) into the staging folder. */
    fun writeStaged(sha256: String, bytes: ByteArray) {
        writeSynced(File(stagingDir, sha256), bytes)
    }

    fun clearStaging() {
        stagingDir.deleteRecursively()
    }

    /** Moves the staged files into `objects/`; a file already stored under its hash is kept. */
    fun promote(entries: Collection<ContentFileEntry>) {
        if (!objectsDir.isDirectory && !objectsDir.mkdirs()) throw IOException("Can't create $objectsDir")
        for (entry in entries) {
            val staged = File(stagingDir, entry.sha256)
            if (!staged.exists()) continue // same hash listed twice: moved already
            val target = objectFile(entry.sha256)
            if (hasObject(entry)) {
                staged.delete()
            } else if (!staged.renameTo(target)) {
                throw IOException("Can't move ${staged.name} into $objectsDir")
            }
        }
    }

    /** Marks [manifest] as the active version: temp file, then an atomic rename over `active.json`. */
    open fun writeActive(manifest: LocalContentManifest) {
        writeSynced(activeTempFile, contentManifestJson.encodeToString(LocalContentManifest.serializer(), manifest).toByteArray())
        if (!activeTempFile.renameTo(activeFile)) throw IOException("Can't write $activeFile")
    }

    /**
     * Deletes staging leftovers and every stored object the active manifest doesn't list.
     * Run at start only, before anything reads this process's version. Returns the number of
     * objects deleted.
     */
    fun cleanUp(): Int {
        clearStaging()
        activeTempFile.delete()
        val referenced = readActive()?.files.orEmpty().mapTo(HashSet()) { it.sha256 }
        return objectsDir.listFiles().orEmpty().count { it.name !in referenced && it.deleteRecursively() }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    companion object {
        /** Folder under `filesDir`; also excluded in `backup_rules.xml` and `data_extraction_rules.xml`. */
        const val DIRECTORY = "content"
        private const val ACTIVE_FILE = "active.json"
    }
}
