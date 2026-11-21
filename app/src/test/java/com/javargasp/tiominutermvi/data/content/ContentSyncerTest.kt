package com.javargasp.tiominutermvi.data.content

import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource.Companion.DAILY_ROUTINES_FILE
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.EXERCISES_FILE
import com.javargasp.tiominutermvi.testutil.CONTENT_DIR
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The content sync and read path against the real bundled content (`content/v1`), a fake Hosting
 * and a temporary `filesDir/content/`. A new [ContentProcess] stands for an app restart.
 */
class ContentSyncerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val http = FakeContentHttpClient()
    private lateinit var storeDir: File

    /** The bundled JSON files and one bundled animation, as Hosting publishes them. */
    private val bundledVersion: Map<String, ByteArray> =
        (ALL_CONTENT_FILES.map { it.path } + "animations/$BUNDLED_GIF").associateWith { File(CONTENT_DIR, it).readBytes() }

    private val bundledRoutines = File(CONTENT_DIR, DAILY_ROUTINES_FILE).readText()

    /** routines.json with other bytes that still decode (a new routine at the end). */
    private val changedRoutines = bundledRoutines.trimEnd().removeSuffix("]").trimEnd() +
        """, { "routineId": 999, "routineDay": "Day 999", "exercises": [ { "exerciseId": 2, "type": "REPS", "amount": 10 } ] } ]"""

    private val otherRoutines = changedRoutines.replace("Day 999", "Day 998")

    @Before
    fun setUp() {
        storeDir = tmp.newFolder("content")
    }

    private fun process(store: ContentStore = ContentStore(storeDir), bundleDir: File = CONTENT_DIR) =
        ContentProcess(bundleDir, store, http)

    private fun version(vararg changes: Pair<String, ByteArray>) = bundledVersion + changes

    /** Activates [routines] as version [contentVersion] (one process), then returns a fresh process. */
    private suspend fun activateRoutines(contentVersion: String, routines: String): ContentProcess {
        http.publish(contentVersion, version(DAILY_ROUTINES_FILE to routines.toByteArray()))
        val result = process().syncer.sync()
        assertTrue("$result", result is ContentSyncResult.Updated)
        http.requested.clear()
        return process()
    }

    private fun storedObjects(): Set<String> = File(storeDir, "objects").list()?.toSet().orEmpty()

    @Test
    fun `no network - every file comes from the bundle`() = runTest {
        http.online = false
        val app = process()

        val result = app.syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        for (file in ALL_CONTENT_FILES) {
            assertEquals(File(CONTENT_DIR, file.path).readText(), app.source.readText(file.path))
        }
        assertEquals(AnimationLocator.ASSET_URL_PREFIX + BUNDLED_GIF, app.locator.locate(BUNDLED_GIF))
        assertNull(app.locator.locate("999_not_published.gif"))
        assertTrue(storedObjects().isEmpty())
    }

    @Test
    fun `the data sources decode the bundled content through the content source`() = runTest {
        http.online = false
        val app = process()

        val routines = AssetDailyRemoteDataSource(app.source).fetchDailyRoutines()
        val gym = AssetGymRemoteDataSource(app.source).fetchGymRoutines()

        assertTrue(routines.isNotEmpty())
        assertTrue(gym.isNotEmpty())
    }

    @Test
    fun `a published version equal to the bundle downloads nothing and becomes active`() = runTest {
        http.publish("v1", bundledVersion)

        val result = process().syncer.sync()

        assertEquals(ContentSyncResult.Updated("v1", downloadedFiles = 0, cachedFiles = 0), result)
        assertTrue(http.downloads().isEmpty())
        assertEquals("v1", ContentStore(storeDir).readActive()?.contentVersion)
    }

    @Test
    fun `same contentVersion as the active one - nothing is downloaded`() = runTest {
        val app = activateRoutines("v2", changedRoutines)

        val result = app.syncer.sync()

        assertEquals(ContentSyncResult.UpToDate("v2"), result)
        assertEquals(listOf(TEST_BASE_URL + ContentRules.MANIFEST_FILE), http.requested)
        assertEquals(changedRoutines, app.source.readText(DAILY_ROUTINES_FILE))
    }

    @Test
    fun `changed JSON is downloaded, verified and served from the next launch`() = runTest {
        http.publish("v2", version(DAILY_ROUTINES_FILE to changedRoutines.toByteArray()))
        val running = process()

        val result = running.syncer.sync()

        assertEquals(ContentSyncResult.Updated("v2", downloadedFiles = 1, cachedFiles = 1), result)
        assertEquals(listOf(TEST_BASE_URL + DAILY_ROUTINES_FILE), http.downloads())
        // The running process keeps the version it started with
        assertEquals(bundledRoutines, running.source.readText(DAILY_ROUTINES_FILE))

        val next = process()
        assertEquals(changedRoutines, next.source.readText(DAILY_ROUTINES_FILE))
        assertEquals(File(CONTENT_DIR, EXERCISES_FILE).readText(), next.source.readText(EXERCISES_FILE))
        assertTrue(AssetDailyRemoteDataSource(next.source).fetchDailyRoutines().any { it.routineId == 999 })
    }

    @Test
    fun `sha256 mismatch - nothing is activated and the previous version stays`() = runTest {
        activateRoutines("v2", changedRoutines)
        val bytes = otherRoutines.toByteArray()
        http.publish(
            "v3",
            version(DAILY_ROUTINES_FILE to bytes),
            entryOverrides = mapOf(DAILY_ROUTINES_FILE to ContentFileEntry(DAILY_ROUTINES_FILE, bytes.size.toLong(), "0".repeat(64)))
        )

        val result = process().syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        assertPreviousVersionStays()
    }

    @Test
    fun `a body longer than its manifest size - nothing is activated`() = runTest {
        activateRoutines("v2", changedRoutines)
        val bytes = otherRoutines.toByteArray()
        http.publish(
            "v3",
            version(DAILY_ROUTINES_FILE to bytes),
            entryOverrides = mapOf(DAILY_ROUTINES_FILE to ContentFileEntry(DAILY_ROUTINES_FILE, 10, sha256Hex(bytes)))
        )

        val result = process().syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        assertPreviousVersionStays()
    }

    @Test
    fun `a file over 5 MB rejects the manifest`() = runTest {
        activateRoutines("v2", changedRoutines)
        val bytes = otherRoutines.toByteArray()
        http.publish(
            "v3",
            version(DAILY_ROUTINES_FILE to bytes),
            entryOverrides = mapOf(
                DAILY_ROUTINES_FILE to ContentFileEntry(DAILY_ROUTINES_FILE, ContentRules.MAX_FILE_BYTES + 1, sha256Hex(bytes))
            )
        )

        val result = process().syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        assertTrue(http.downloads().isEmpty())
        assertPreviousVersionStays()
    }

    @Test
    fun `JSON the app can't decode - nothing is activated`() = runTest {
        activateRoutines("v2", changedRoutines)
        http.publish("v3", version(DAILY_ROUTINES_FILE to """[ { "routineId": "not a number" } ]""".toByteArray()))

        val result = process().syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        assertPreviousVersionStays()
    }

    @Test
    fun `invalid paths reject the whole manifest`() = runTest {
        for (path in listOf("../x", "/abs", "other/file.txt", "animations/../exercises.json", "unknown.json", "animations/a.png")) {
            http.requested.clear()
            http.publish("v9", version(path to "x".toByteArray()))

            val result = process().syncer.sync()

            assertTrue("$path: $result", result is ContentSyncResult.Failed)
            assertTrue(path, http.downloads().isEmpty())
            assertNull(path, ContentStore(storeDir).readActive())
        }
    }

    @Test
    fun `another schemaVersion or a malformed manifest is rejected`() = runTest {
        http.publish("v2", version(DAILY_ROUTINES_FILE to changedRoutines.toByteArray()), schemaVersion = 2)
        assertTrue(process().syncer.sync() is ContentSyncResult.Failed)

        http.files[TEST_BASE_URL + ContentRules.MANIFEST_FILE] = """{ "schemaVersion": 1, "files": [] }""".toByteArray()
        assertTrue(process().syncer.sync() is ContentSyncResult.Failed)

        http.files[TEST_BASE_URL + ContentRules.MANIFEST_FILE] = "<html>".toByteArray()
        assertTrue(process().syncer.sync() is ContentSyncResult.Failed)

        assertTrue(http.downloads().isEmpty())
        assertNull(ContentStore(storeDir).readActive())
    }

    @Test
    fun `a new animation is downloaded and located, a bundled one never is`() = runTest {
        val newGif = "GIF89a new animation".toByteArray()
        http.publish(
            "v2",
            version(
                "animations/$NEW_GIF" to newGif,
                // Different bytes under a bundled name: still never downloaded
                "animations/$BUNDLED_GIF" to "GIF89a changed".toByteArray()
            )
        )

        val result = process().syncer.sync()

        assertEquals(ContentSyncResult.Updated("v2", downloadedFiles = 1, cachedFiles = 1), result)
        assertEquals(listOf(TEST_BASE_URL + "animations/$NEW_GIF"), http.downloads())
        val next = process()
        val located = next.locator.locate(NEW_GIF)
        assertTrue("$located", located is File)
        assertTrue(newGif.contentEquals((located as File).readBytes()))
        assertEquals(AnimationLocator.ASSET_URL_PREFIX + BUNDLED_GIF, next.locator.locate(BUNDLED_GIF))
    }

    @Test
    fun `a file already cached with the same hash is not downloaded again`() = runTest {
        val newGif = "GIF89a new animation".toByteArray()
        http.publish("v2", version("animations/$NEW_GIF" to newGif))
        process().syncer.sync()
        http.requested.clear()
        http.publish("v3", version("animations/$NEW_GIF" to newGif, DAILY_ROUTINES_FILE to changedRoutines.toByteArray()))

        val result = process().syncer.sync()

        assertEquals(ContentSyncResult.Updated("v3", downloadedFiles = 1, cachedFiles = 2), result)
        assertEquals(listOf(TEST_BASE_URL + DAILY_ROUTINES_FILE), http.downloads())
    }

    @Test
    fun `crash between moving the files and writing the local manifest - the previous version stays`() = runTest {
        activateRoutines("v2", changedRoutines)
        val previousObjects = storedObjects()
        http.publish("v3", version(DAILY_ROUTINES_FILE to otherRoutines.toByteArray()))

        val result = process(store = CrashingContentStore(storeDir)).syncer.sync()

        assertTrue(result is ContentSyncResult.Failed)
        // The new file was moved in, but nothing marks it as active
        assertTrue(storedObjects().containsAll(previousObjects) && storedObjects().size > previousObjects.size)
        val restarted = process()
        assertEquals("v2", restarted.store.readActive()?.contentVersion)
        assertEquals(changedRoutines, restarted.source.readText(DAILY_ROUTINES_FILE))

        // The next start removes the orphan file
        restarted.store.cleanUp()
        assertEquals(previousObjects, storedObjects())
        assertTrue(File(storeDir, "staging").list().isNullOrEmpty())
    }

    @Test
    fun `cached files the new version no longer lists are removed at the next start`() = runTest {
        activateRoutines("v2", changedRoutines)
        http.publish("v3", bundledVersion)

        val result = process().syncer.sync()
        assertEquals(ContentSyncResult.Updated("v3", downloadedFiles = 0, cachedFiles = 0), result)
        // Still on disk for the process that synced, deleted at the next start
        assertEquals(1, storedObjects().size)
        process().store.cleanUp()

        assertTrue(storedObjects().isEmpty())
        assertEquals(bundledRoutines, process().source.readText(DAILY_ROUTINES_FILE))
    }

    @Test
    fun `an app update with other bundled JSON ignores the older cached JSON`() = runTest {
        activateRoutines("v2", changedRoutines)
        val newBundle = tmp.newFolder("bundle")
        for (file in ALL_CONTENT_FILES) File(CONTENT_DIR, file.path).copyTo(File(newBundle, file.path))
        File(newBundle, EXERCISES_FILE).appendText("\n")

        val updated = process(bundleDir = newBundle)

        assertEquals(bundledRoutines, updated.source.readText(DAILY_ROUTINES_FILE))
        // The same published version is synced again against the new bundle
        http.publish("v2", version(DAILY_ROUTINES_FILE to changedRoutines.toByteArray()))
        assertTrue(updated.syncer.sync() is ContentSyncResult.Updated)
        assertEquals(changedRoutines, process(bundleDir = newBundle).source.readText(DAILY_ROUTINES_FILE))
    }

    @Test
    fun `syncOnce runs a single sync per process`() = runTest {
        http.publish("v1", bundledVersion)
        val app = process()

        val first = app.syncer.syncOnce()
        val second = app.syncer.syncOnce()

        assertTrue(first is ContentSyncResult.Updated)
        assertNull(second)
        assertEquals(1, http.requested.size)
    }

    private suspend fun assertPreviousVersionStays() {
        val restarted = process()
        assertEquals("v2", restarted.store.readActive()?.contentVersion)
        assertEquals(changedRoutines, restarted.source.readText(DAILY_ROUTINES_FILE))
        assertTrue(File(storeDir, "staging").list().isNullOrEmpty())
    }

    private companion object {
        const val BUNDLED_GIF = "2_squat.gif"
        const val NEW_GIF = "999_new_move.gif"
    }
}
