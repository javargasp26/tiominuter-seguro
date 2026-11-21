package com.javargasp.tiominutermvi.domain.sync

import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.testutil.FakeNetwork
import com.javargasp.tiominutermvi.testutil.FakeRemoteUserData
import com.javargasp.tiominutermvi.testutil.FakeSyncStateStore
import com.javargasp.tiominutermvi.testutil.FakeWallClock
import com.javargasp.tiominutermvi.testutil.InMemorySyncStore
import com.javargasp.tiominutermvi.testutil.RecordingScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the sync runs: guests never, an account at sign-in / start and after local writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    private val remote = FakeRemoteUserData()
    private val store = InMemorySyncStore(listOf(SyncCollection.TRAINING_LOG))
    private val state = FakeSyncStateStore()
    private val engine = SyncEngine(
        listOf(store), remote, state, LocalUserDataWiper { store.clear() }, FakeWallClock(1L), FakeNetwork(), RecordingScheduler()
    )
    private val entry = DocKey(SyncCollection.TRAINING_LOG, "e1")

    private fun TestScope.started(auth: FakeAuthRepository): SyncCoordinator =
        SyncCoordinator(auth, engine, remote, backgroundScope, debounceMillis = 5_000).also {
            it.start()
            runCurrent()
        }

    @Test
    fun `a guest never syncs, also after writes`() = runTest {
        val auth = FakeAuthRepository()
        val coordinator = started(auth)

        store.write(entry, mapOf("title" to "A"))
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(remote.batchSizes.isEmpty())
        assertTrue(remote.fetches.isEmpty())
        assertEquals(SyncOutcome.SKIPPED, coordinator.syncNow())
    }

    @Test
    fun `signing in writes the profile and syncs the local data to the account`() = runTest {
        store.write(entry, mapOf("title" to "Guest workout"))
        val auth = FakeAuthRepository()
        started(auth)

        auth.signInWithGoogle("token")
        runCurrent()

        val uid = FakeAuthRepository.SAMPLE_USER.uid
        assertEquals("Alex Rivera" to null, remote.profiles[uid])
        assertEquals(setOf(entry.path), remote.docsOf(uid).keys)
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun `local writes are synced about 5 seconds later, once`() = runTest {
        val auth = FakeAuthRepository(initialSession = FakeAuthRepository.SAMPLE_USER)
        started(auth)
        val batchesAfterStart = remote.batchSizes.size

        store.write(entry, mapOf("title" to "A"))
        advanceTimeBy(1_000)
        store.write(DocKey(SyncCollection.TRAINING_LOG, "e2"), mapOf("title" to "B"))
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(batchesAfterStart, remote.batchSizes.size)

        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(listOf(2), remote.batchSizes.drop(batchesAfterStart))
    }

    @Test
    fun `after its server data is erased, the account's writes are not uploaded automatically`() = runTest {
        val auth = FakeAuthRepository(initialSession = FakeAuthRepository.SAMPLE_USER)
        val coordinator = started(auth)
        val uid = FakeAuthRepository.SAMPLE_USER.uid

        coordinator.eraseServerData(uid)
        store.write(entry, mapOf("title" to "A"))
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(SyncOutcome.SKIPPED, coordinator.syncNow())
        assertTrue(remote.docsOf(uid).isEmpty())
        assertEquals(1, store.outbox.size)
    }

    @Test
    fun `signing out (or the account being deleted) lifts the block`() = runTest {
        val auth = FakeAuthRepository(initialSession = FakeAuthRepository.SAMPLE_USER)
        val coordinator = started(auth)
        coordinator.eraseServerData(FakeAuthRepository.SAMPLE_USER.uid)

        auth.signOut()
        runCurrent()

        assertEquals(null, state.erased)
    }

    @Test
    fun `the profile keeps at most 500 characters of the name and only an https photo`() = runTest {
        val long = AuthSession.SignedIn("uid-2", "x".repeat(600), "a@example.com", "http://photo")
        val auth = FakeAuthRepository(initialSession = long)
        started(auth)

        val (name, photo) = remote.profiles.getValue("uid-2")
        assertEquals(500, name?.length)
        assertEquals(null, photo)
    }
}
