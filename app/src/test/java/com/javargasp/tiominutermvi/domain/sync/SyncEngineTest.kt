package com.javargasp.tiominutermvi.domain.sync

import com.javargasp.tiominutermvi.domain.auth.LocalUserDataWiper
import com.javargasp.tiominutermvi.testutil.FakeNetwork
import com.javargasp.tiominutermvi.testutil.FakeRemoteUserData
import com.javargasp.tiominutermvi.testutil.FakeSyncStateStore
import com.javargasp.tiominutermvi.testutil.FakeWallClock
import com.javargasp.tiominutermvi.testutil.InMemorySyncStore
import com.javargasp.tiominutermvi.testutil.RecordingScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The sync engine with an in-memory Firestore and local store. */
class SyncEngineTest {

    private val remote = FakeRemoteUserData()
    private val store = InMemorySyncStore(listOf(SyncCollection.WEEKLY_GOAL, SyncCollection.TRAINING_LOG))
    private val state = FakeSyncStateStore()
    private val network = FakeNetwork()
    private val scheduler = RecordingScheduler()
    private val clock = FakeWallClock(5_000L)
    private var wipes = 0
    private val wiper = LocalUserDataWiper {
        wipes++
        store.clear()
    }

    private fun engine(pageSize: Int = 200) =
        SyncEngine(listOf(store), remote, state, wiper, clock, network, scheduler, pageSize)

    private fun entry(id: String) = DocKey(SyncCollection.TRAINING_LOG, id)
    private fun goal(week: String) = DocKey(SyncCollection.WEEKLY_GOAL, week)

    /** The account already synced on this device (incremental sync). */
    private fun ownedBy(uid: String) {
        state.owner = uid
    }

    // --- Push ---

    @Test
    fun `pending changes are pushed in batches of at most 500 and leave the outbox only then`() = runTest {
        ownedBy(UID)
        repeat(1_200) { store.write(entry("e$it"), mapOf("title" to "Workout $it")) }

        assertEquals(SyncOutcome.SUCCESS, engine().sync(UID))

        assertEquals(listOf(500, 500, 200), remote.batchSizes)
        assertTrue(store.outbox.isEmpty())
        assertEquals(1_200, remote.docsOf(UID).size)
    }

    @Test
    fun `a failed batch keeps its changes and schedules a retry, the next sync uploads them`() = runTest {
        ownedBy(UID)
        store.write(entry("e1"), mapOf("title" to "A"))
        remote.commitError = IOException("offline")

        assertEquals(SyncOutcome.FAILED, engine().sync(UID))
        assertEquals(1, store.outbox.size)
        assertEquals(1, scheduler.retries)

        remote.commitError = null
        assertEquals(SyncOutcome.SUCCESS, engine().sync(UID))
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun `the engine reports a failure and clears it on the next success`() = runTest {
        ownedBy(UID)
        val engine = engine()
        store.write(entry("e1"), mapOf("title" to "A"))
        remote.commitError = IOException("offline")
        engine.sync(UID)
        assertTrue(engine.status.value.failing)

        remote.commitError = null
        engine.sync(UID)
        assertFalse(engine.status.value.failing)
        assertEquals(5_000L, engine.status.value.lastSuccessAt)
    }

    @Test
    fun `a change written again while its batch uploads stays pending`() = runTest {
        ownedBy(UID)
        store.write(entry("e1"), mapOf("title" to "A"))
        val first = store.pending(10).single().change
        store.write(entry("e1"), mapOf("title" to "B"))

        store.acknowledge(listOf(first))

        assertEquals(1, store.outbox.size)
    }

    @Test
    fun `offline - nothing is read or written, a retry is scheduled`() = runTest {
        ownedBy(UID)
        store.write(entry("e1"), mapOf("title" to "A"))
        network.online = false

        assertEquals(SyncOutcome.OFFLINE, engine().sync(UID))

        assertTrue(remote.fetches.isEmpty())
        assertTrue(remote.batchSizes.isEmpty())
        assertEquals(1, scheduler.retries)
    }

    // --- Pull ---

    @Test
    fun `the pull pages through a batch that shares one server time, and saves the watermark`() = runTest {
        ownedBy(UID)
        // Another device uploaded 450 entries in one batch: every document has the same updatedAt
        val other = InMemorySyncStore(listOf(SyncCollection.TRAINING_LOG))
        repeat(450) { other.write(entry("e%03d".format(it)), mapOf("title" to "T$it")) }
        remote.commit(UID, other.pending(450))

        engine(pageSize = 100).sync(UID)

        assertEquals(450, store.rows.size)
        assertEquals(5, remote.fetches.count { it.collection == SyncCollection.TRAINING_LOG })
        val watermark = state.watermarks[UID to SyncCollection.TRAINING_LOG.path]
        assertEquals(remote.docsOf(UID).values.first().updatedAt, watermark)
    }

    @Test
    fun `the next pull starts at the watermark (inclusive) and skips what is older`() = runTest {
        ownedBy(UID)
        remote.put(UID, entry("older"), mapOf("title" to "Older"))
        remote.put(UID, entry("newest"), mapOf("title" to "Newest"))
        val engine = engine()
        engine.sync(UID)
        // Forget them locally to see what the next pull reads again
        store.rows.clear()

        remote.put(UID, entry("later"), mapOf("title" to "Later"))
        engine.sync(UID)

        // The document at the watermark comes again (harmless), older ones don't
        assertEquals(setOf(entry("newest"), entry("later")), store.rows.keys)
    }

    @Test
    fun `applying pulled documents enqueues nothing (no ping-pong)`() = runTest {
        ownedBy(UID)
        repeat(3) { remote.put(UID, entry("e$it"), mapOf("title" to "T$it")) }
        remote.put(UID, goal("2026-10-19"), mapOf("workoutsPerWeek" to 4L))

        engine().sync(UID)

        assertEquals(4, store.rows.size)
        assertTrue(store.outbox.isEmpty())
        assertTrue(remote.batchSizes.isEmpty())
    }

    // --- Tombstones and conflicts ---

    @Test
    fun `a uuid tombstone wins over a pending local edit and removes the row`() = runTest {
        ownedBy(UID)
        store.write(entry("e1"), mapOf("title" to "Edited here"))
        remote.put(UID, entry("e1"), mapOf("entryId" to "e1"), deleted = true)

        engine().sync(UID)

        assertFalse(entry("e1") in store.rows)
        assertTrue(store.outbox.isEmpty())
        assertTrue(remote.docsOf(UID).getValue(entry("e1").path).deleted)
    }

    @Test
    fun `a natural-id document is revived after a tombstone`() = runTest {
        ownedBy(UID)
        remote.put(UID, goal("2026-10-19"), mapOf("effectiveFromWeek" to "2026-10-19"), deleted = true)
        val engine = engine()
        engine.sync(UID)
        assertFalse(goal("2026-10-19") in store.rows)

        remote.put(UID, goal("2026-10-19"), mapOf("workoutsPerWeek" to 5L))
        engine.sync(UID)

        assertEquals(5L, store.rows.getValue(goal("2026-10-19")).fields["workoutsPerWeek"])
    }

    @Test
    fun `a pending local change wins over the remote version and is then pushed`() = runTest {
        ownedBy(UID)
        remote.put(UID, entry("e1"), mapOf("title" to "Remote"))
        store.write(entry("e1"), mapOf("title" to "Local"))

        engine().sync(UID)

        assertEquals("Local", store.rows.getValue(entry("e1")).fields["title"])
        assertEquals("Local", remote.docsOf(UID).getValue(entry("e1").path).fields["title"])
    }

    @Test
    fun `weekly goal - a pending local change wins and is pushed`() = runTest {
        ownedBy(UID)
        remote.put(UID, goal("2026-10-19"), mapOf("workoutsPerWeek" to 6L))
        store.write(goal("2026-10-19"), mapOf("workoutsPerWeek" to 3L))

        engine().sync(UID)

        assertEquals(3L, store.rows.getValue(goal("2026-10-19")).fields["workoutsPerWeek"])
        assertEquals(3L, remote.docsOf(UID).getValue(goal("2026-10-19").path).fields["workoutsPerWeek"])
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun `weekly goal - with nothing pending the remote value is applied`() = runTest {
        ownedBy(UID)
        store.rows[goal("2026-10-19")] = InMemorySyncStore.Row(mapOf("workoutsPerWeek" to 3L), updatedAt = 1)
        remote.put(UID, goal("2026-10-19"), mapOf("workoutsPerWeek" to 6L))

        engine().sync(UID)

        assertEquals(6L, store.rows.getValue(goal("2026-10-19")).fields["workoutsPerWeek"])
    }

    @Test
    fun `weekly goal - a device clock far in the past or the future changes nothing`() = runTest {
        ownedBy(UID)
        // Local rows written with absurd device times: the decision never looks at them
        store.write(goal("2026-10-19"), mapOf("workoutsPerWeek" to 3L), updatedAt = Long.MIN_VALUE / 2)
        store.write(goal("2026-10-26"), mapOf("workoutsPerWeek" to 4L), updatedAt = Long.MAX_VALUE / 2)
        store.rows[goal("2026-11-02")] = InMemorySyncStore.Row(mapOf("workoutsPerWeek" to 5L), updatedAt = Long.MAX_VALUE / 2)
        remote.put(UID, goal("2026-10-19"), mapOf("workoutsPerWeek" to 9L))
        remote.put(UID, goal("2026-10-26"), mapOf("workoutsPerWeek" to 9L))
        remote.put(UID, goal("2026-11-02"), mapOf("workoutsPerWeek" to 9L))

        engine().sync(UID)

        // Pending: local wins, whatever its clock said; not pending: remote applied, whatever its clock said
        assertEquals(3L, store.rows.getValue(goal("2026-10-19")).fields["workoutsPerWeek"])
        assertEquals(4L, store.rows.getValue(goal("2026-10-26")).fields["workoutsPerWeek"])
        assertEquals(9L, store.rows.getValue(goal("2026-11-02")).fields["workoutsPerWeek"])
    }

    // --- Accounts ---

    @Test
    fun `guest data meets an account - everything is uploaded and merged with the cloud`() = runTest {
        // Guest data, never synced, with no outbox (e.g. created before the sync existed)
        store.rows[entry("local")] = InMemorySyncStore.Row(mapOf("title" to "Local"), 1)
        remote.put(UID, entry("cloud"), mapOf("title" to "Cloud"))

        engine().sync(UID)

        assertEquals(setOf(entry("local"), entry("cloud")), store.rows.keys)
        assertEquals(setOf(entry("local").path, entry("cloud").path), remote.docsOf(UID).keys)
        assertEquals(UID, state.owner)
        assertEquals(0, wipes)
    }

    @Test
    fun `the same account again merges incrementally, without a wipe`() = runTest {
        ownedBy(UID)
        store.write(entry("new-here"), mapOf("title" to "Here"))
        remote.put(UID, entry("new-there"), mapOf("title" to "There"))

        engine().sync(UID)

        assertEquals(setOf(entry("new-here"), entry("new-there")), store.rows.keys)
        assertEquals(0, wipes)
    }

    @Test
    fun `another account - the local data is wiped first, then the new account's data downloaded`() = runTest {
        ownedBy("first-account")
        store.write(entry("of-first"), mapOf("title" to "First"))
        remote.put("second-account", entry("of-second"), mapOf("title" to "Second"))

        engine().sync("second-account")

        assertEquals(1, wipes)
        assertEquals(setOf(entry("of-second")), store.rows.keys)
        // Nothing of the first account reaches the second one
        assertEquals(setOf(entry("of-second").path), remote.docsOf("second-account").keys)
        assertEquals("second-account", state.owner)
    }

    @Test
    fun `erasing the server data deletes everything and makes the local data guest data again`() = runTest {
        ownedBy(UID)
        remote.put(UID, entry("e1"), mapOf("title" to "A"))
        val engine = engine()
        engine.sync(UID)

        engine.eraseServerData(UID)

        assertTrue(remote.docsOf(UID).isEmpty())
        assertNull(state.owner)
        assertTrue(state.watermarks.isEmpty())
    }

    @Test
    fun `after the erase, the account's local data is never uploaded again, also after a restart`() = runTest {
        ownedBy(UID)
        store.write(entry("e1"), mapOf("title" to "A"))
        engine().sync(UID)
        engine().eraseServerData(UID)
        // The Firebase user couldn't be deleted: the account still exists, with local changes
        store.write(entry("e2"), mapOf("title" to "B"))

        assertEquals(SyncOutcome.SKIPPED, engine().sync(UID))
        // A new process (a new engine over the same state) doesn't upload either
        assertEquals(SyncOutcome.SKIPPED, engine().sync(UID))

        assertTrue(remote.docsOf(UID).isEmpty())
        assertEquals(1, store.outbox.size)
        assertEquals(UID, state.erased)
    }

    @Test
    fun `an erase that fails marks nothing, so the next sync restores what was deleted`() = runTest {
        ownedBy(UID)
        remote.deleteError = IOException("offline")

        runCatching { engine().eraseServerData(UID) }

        assertNull(state.erased)
        assertEquals(UID, state.owner)
    }

    @Test
    fun `once the erased account is gone or signed out, syncs run again`() = runTest {
        ownedBy(UID)
        val engine = engine()
        engine.eraseServerData(UID)
        store.write(entry("e1"), mapOf("title" to "A"))

        engine.clearErasedAccount()

        // A later, explicit sign-in to that account merges the local data (guest data now)
        assertEquals(SyncOutcome.SUCCESS, engine.sync(UID))
        assertEquals(setOf(entry("e1").path), remote.docsOf(UID).keys)
    }

    // --- PROGRAM refIds ---

    @Test
    fun `PROGRAM refIds use the local id on the device and the program uuid in the cloud`() = runTest {
        val mapper = object : ProgramRefMapper {
            override suspend fun uuidOf(localId: Long) = if (localId == 12L) "9a1b2c3d-uuid" else null
            override suspend fun localIdOf(uuid: String) = if (uuid == "9a1b2c3d-uuid") 12L else null
        }

        assertEquals("program:9a1b2c3d-uuid:1:0", ProgramRefIds.toRemote("program:12:1:0", mapper))
        assertEquals("program:12:1:0", ProgramRefIds.toLocal("program:9a1b2c3d-uuid:1:0", mapper))
        // Unknown on the other side (a deleted program) or not a program: unchanged
        assertEquals("program:99:1:0", ProgramRefIds.toRemote("program:99:1:0", mapper))
        assertEquals("program:other-uuid:1:0", ProgramRefIds.toLocal("program:other-uuid:1:0", mapper))
        assertEquals("gym:64", ProgramRefIds.toRemote("gym:64", mapper))
        assertNull(ProgramRefIds.toLocal(null, mapper))
    }

    private companion object {
        const val UID = "uid-alex"
    }
}
