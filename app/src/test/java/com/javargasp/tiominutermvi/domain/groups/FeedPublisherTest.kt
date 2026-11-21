package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.FeedTestKit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed's publish flow: the queue is filled by the real training history (record, delete, photo)
 * and flushed to a feed with the rules' semantics, one write per group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPublisherTest {

    private val kit = FeedTestKit()
    private val groups = kit.groups.repository
    private val feed = kit.feed

    /** I'm in g1 (owner) and g2 (with Ana); the explanation was already shown. */
    private fun inTwoGroups(introSeen: Boolean = true) {
        groups.seedGroup("g1", "One", "AAAAAAAA", listOf("me"))
        groups.seedGroup("g2", "Two", "BBBBBBBB", listOf("ana", "me"))
        if (introSeen) kit.preferences.setFeedIntroSeen("me")
    }

    private suspend fun flush() = kit.publisher.flush(kit.me)

    // ---------- What the queue holds ----------

    @Test
    fun `a counted workout recorded while signed in is shared by default`() = runTest {
        val id = kit.record(minutes = 30)
        val row = kit.store.get(kit.uuidOf(id))
        assertNotNull(row)
        assertTrue(row!!.share)
        assertTrue(row.dirty)
        assertEquals("me", row.ownerUid)
    }

    @Test
    fun `a workout under 10 minutes, or one of a guest, is never queued`() = runTest {
        val short = kit.record(minutes = 9)
        assertNull(kit.store.get(kit.uuidOf(short)))
        kit.signedInUid = null
        val asGuest = kit.record(minutes = 45)
        assertNull(kit.store.get(kit.uuidOf(asGuest)))
        // An unknown duration counts (as for the weekly goal)
        kit.signedInUid = "me"
        assertNotNull(kit.store.get(kit.uuidOf(kit.record(minutes = null))))
    }

    // ---------- Posting ----------

    @Test
    fun `nothing is posted until the explanation is acknowledged, then one write per group`() = runTest {
        inTwoGroups(introSeen = false)
        val id = kit.record(title = "Pull Day")
        val uuid = kit.uuidOf(id)

        flush()
        assertTrue("waits for the explanation", feed.posts.values.all { it.isEmpty() })
        assertTrue(kit.store.get(uuid)!!.dirty)

        kit.sharing.acknowledgeIntro()
        assertEquals(FlushOutcome.DONE, flush())
        val posted = listOf("g1", "g2").map { feed.post(it, uuid)!! }
        assertEquals(listOf("createPost:g1:$uuid", "createPost:g2:$uuid"), feed.calls.filter { it.startsWith("createPost") })
        posted.forEach { post ->
            assertEquals("me", post.authorUid)
            assertEquals("Alex Rivera", post.authorName)
            assertEquals(TrainingSource.GYM, post.source)
            assertEquals("Pull Day", post.title)
            assertEquals(1800, post.durationSeconds)
            assertEquals("2026-10-22", post.dateLocal)
            assertEquals("2026-10-19", post.weekStartLocal)
        }
        assertFalse(kit.store.get(uuid)!!.dirty)
    }

    @Test
    fun `posting twice is idempotent, also when the answer of a create was lost`() = runTest {
        inTwoGroups()
        val uuid = kit.uuidOf(kit.record())
        // The server applies the create but the answer is lost: the next set is refused (the post exists)
        feed.failOn("createPost", GroupError.NETWORK)
        assertEquals(FlushOutcome.PENDING, flush())
        assertNull(feed.post("g1", uuid))
        assertNotNull(feed.post("g2", uuid))
        feed.seed("g1", feed.post("g2", uuid)!!) // as if g1's create had reached the server

        assertEquals(FlushOutcome.DONE, flush())
        assertEquals(listOf("g1", "g2"), kit.store.get(uuid)!!.postedGroupIds.sorted())
        assertEquals("no group is marked refused", emptyList<String>(), kit.store.get(uuid)!!.refusedGroupIds)
        val creates = feed.calls.count { it.startsWith("createPost") }
        flush()
        assertEquals("nothing left to post", creates, feed.calls.count { it.startsWith("createPost") })
    }

    @Test
    fun `a group that refuses doesn't block the others and is never retried`() = runTest {
        inTwoGroups()
        groups.members.getValue("g2").remove("me") // removed by the owner; my index is stale
        val uuid = kit.uuidOf(kit.record())

        assertEquals(FlushOutcome.DONE, flush())
        assertNotNull(feed.post("g1", uuid))
        assertEquals(listOf("g2"), kit.store.get(uuid)!!.refusedGroupIds)
        val before = feed.calls.size
        kit.store.setShare(uuid, true) // any local change makes the row dirty again
        flush()
        assertTrue("g2 is not tried again", feed.calls.drop(before).none { it.startsWith("createPost:g2") })
    }

    @Test
    fun `offline the queue survives and is flushed later`() = runTest {
        inTwoGroups()
        val uuid = kit.uuidOf(kit.record())
        kit.groups.network.online = false
        assertEquals(FlushOutcome.PENDING, flush())
        assertTrue(feed.calls.isEmpty())

        kit.groups.network.online = true
        assertEquals(FlushOutcome.DONE, flush())
        assertNotNull(feed.post("g2", uuid))
    }

    @Test
    fun `a network failure keeps the row dirty and the next flush finishes it`() = runTest {
        inTwoGroups()
        val uuid = kit.uuidOf(kit.record())
        feed.failOn("createPost", GroupError.FAILED, times = 2)
        assertEquals(FlushOutcome.PENDING, flush())
        assertTrue(kit.store.get(uuid)!!.dirty)
        assertEquals(FlushOutcome.DONE, flush())
        assertNotNull(feed.post("g1", uuid))
        assertNotNull(feed.post("g2", uuid))
    }

    @Test
    fun `a workout goes to the groups of its first post, never to a group joined later`() = runTest {
        inTwoGroups()
        val uuid = kit.uuidOf(kit.record())
        flush()
        groups.seedGroup("g3", "Three", "CCCCCCCC", listOf("cara", "me"))
        kit.store.setShare(uuid, true)
        flush()
        assertNull(feed.post("g3", uuid))
    }

    @Test
    fun `a workout of a past week is not posted`() = runTest {
        inTwoGroups()
        val uuid = kit.uuidOf(kit.record())
        // Next Monday before the first flush: its week is over
        kit.groups.now += 4 * DAY
        assertEquals(FlushOutcome.DONE, flush())
        assertNull(feed.post("g1", uuid))
        assertFalse(kit.store.get(uuid)!!.dirty)
    }

    @Test
    fun `another account's queued workout is dropped, never posted as me`() = runTest {
        inTwoGroups()
        kit.signedInUid = "previous"
        val uuid = kit.uuidOf(kit.record())
        flush()
        assertNull(kit.store.get(uuid))
        assertTrue(feed.calls.none { it.startsWith("createPost") })
    }

    // ---------- The switch, deleting, photos ----------

    @Test
    fun `turning the switch off before the flush posts nothing, after it deletes the posts`() = runTest {
        inTwoGroups()
        val first = kit.record()
        kit.sharing.setShare(first, false)
        flush()
        assertNull(feed.post("g1", kit.uuidOf(first)))

        val second = kit.record()
        flush()
        assertNotNull(feed.post("g1", kit.uuidOf(second)))
        kit.sharing.setShare(second, false)
        flush()
        assertNull(feed.post("g1", kit.uuidOf(second)))
        assertNull(feed.post("g2", kit.uuidOf(second)))
        assertEquals(emptyList<String>(), kit.store.get(kit.uuidOf(second))!!.postedGroupIds)
    }

    @Test
    fun `deleting the entry deletes its post in every group of my index without reading first`() = runTest {
        inTwoGroups()
        val id = kit.record()
        val uuid = kit.uuidOf(id)
        flush()
        groups.seedGroup("g3", "Three", "CCCCCCCC", listOf("cara", "me")) // never posted there

        kit.trainingLog.deleteEntry(id)
        assertEquals(FlushOutcome.DONE, flush())
        assertNull(feed.post("g1", uuid))
        assertNull(feed.post("g2", uuid))
        assertTrue("a missing post is a no-op delete", "deletePost:g3:$uuid" in feed.calls)
        assertTrue(feed.calls.none { it.startsWith("isMyPost") || it.startsWith("postIds") })
        assertNull("the row is gone once its posts are", kit.store.get(uuid))
    }

    @Test
    fun `a photo added later updates the thumbnail of my posts, and removing it removes it`() = runTest {
        inTwoGroups()
        val id = kit.record()
        val uuid = kit.uuidOf(id)
        flush()
        assertNull(feed.post("g1", uuid)!!.thumb)

        kit.thumbnails["training_photos/$uuid-1.jpg"] = ByteArray(30_000)
        kit.trainingLog.setPhotoPath(id, "training_photos/$uuid-1.jpg")
        flush()
        assertEquals(30_000, feed.post("g1", uuid)!!.thumb!!.size)
        assertEquals(30_000, feed.post("g2", uuid)!!.thumb!!.size)

        kit.trainingLog.setPhotoPath(id, null)
        flush()
        assertNull(feed.post("g1", uuid)!!.thumb)
        assertTrue("setThumbnail:g2:$uuid:none" in feed.calls)
    }

    @Test
    fun `a new post carries the photo's thumbnail, and one over the cap is never sent`() = runTest {
        inTwoGroups()
        val id = kit.record()
        val uuid = kit.uuidOf(id)
        kit.thumbnails["training_photos/a-1.jpg"] = ByteArray(FeedLimits.THUMB_MAX_BYTES + 1)
        kit.trainingLog.setPhotoPath(id, "training_photos/a-1.jpg")
        flush()
        assertNull("over the cap: posted without it", feed.post("g1", uuid)!!.thumb)

        val other = kit.record()
        kit.thumbnails["training_photos/b-1.jpg"] = ByteArray(1_000)
        kit.trainingLog.setPhotoPath(other, "training_photos/b-1.jpg")
        flush()
        assertEquals(1_000, feed.post("g2", kit.uuidOf(other))!!.thumb!!.size)
    }

    @Test
    fun `an ACTIVITY post carries its type so readers can name it in their language`() = runTest {
        inTwoGroups()
        val id = kit.trainingLog.addActivityEntry(
            com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput(
                type = com.javargasp.tiominutermvi.domain.traininglog.ActivityType.RUNNING,
                customName = "",
                durationMinutes = 40,
                intensity = com.javargasp.tiominutermvi.domain.traininglog.Intensity.MODERATE,
                completedAt = kit.groups.now - 60_000,
                note = "private note"
            )
        )
        flush()
        val post = feed.post("g1", kit.uuidOf(id))!!
        assertEquals(com.javargasp.tiominutermvi.domain.traininglog.ActivityType.RUNNING, post.activityType)
        assertEquals("", post.title)
    }

    @Test
    fun `each post carries the locale-free key of its workout name`() = runTest {
        inTwoGroups()
        kit.programTitles.codes["program:7:1:0"] = ProgramTitleCode.UPPER_A
        val gym = kit.uuidOf(kit.record(source = TrainingSource.GYM, title = "Pull Day", refId = "gym:64"))
        val daily = kit.uuidOf(kit.record(source = TrainingSource.DAILY, title = "Day 5", refId = "daily:5"))
        // Recorded on a Spanish device: the title is Spanish, the key is not
        val program = kit.uuidOf(kit.record(source = TrainingSource.PROGRAM, title = "Tren superior A", refId = "program:7:1:0"))
        flush()

        for (group in listOf("g1", "g2")) {
            assertEquals("gym/64", feed.post(group, gym)!!.titleKey)
            assertEquals("daily/5", feed.post(group, daily)!!.titleKey)
            assertEquals("program/UPPER_A", feed.post(group, program)!!.titleKey)
            assertEquals("the title is kept as the fallback", "Tren superior A", feed.post(group, program)!!.title)
        }
    }

    @Test
    fun `a workout whose name can't be keyed is posted without a key`() = runTest {
        inTwoGroups()
        // The program was deleted before the flush; an old refId format
        val program = kit.uuidOf(kit.record(source = TrainingSource.PROGRAM, title = "Upper A", refId = "program:9:1:0"))
        val odd = kit.uuidOf(kit.record(source = TrainingSource.GYM, title = "Pull Day", refId = "gym:0"))
        flush()

        assertNull(feed.post("g1", program)!!.titleKey)
        assertEquals("Upper A", feed.post("g1", program)!!.title)
        assertNull(feed.post("g1", odd)!!.titleKey)
        assertNotNull("posted in every group, nothing refused", feed.post("g2", program))
    }

    // ---------- Triggers ----------

    @Test
    fun `start flushes after a debounce, on a queue change and after a successful sync`() = runTest {
        val scope = TestScope(testScheduler)
        val live = FeedTestKit(scope)
        live.groups.repository.seedGroup("g1", "One", "AAAAAAAA", listOf("me"))
        live.preferences.setFeedIntroSeen("me")
        live.publisher.start()
        runCurrent()

        val uuid = live.uuidOf(live.record())
        advanceTimeBy(FeedPublisher.DEFAULT_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertNull("debounced", live.feed.post("g1", uuid))
        advanceTimeBy(2)
        runCurrent()
        assertNotNull(live.feed.post("g1", uuid))

        // Offline: pending; a successful sync run (back online) flushes again
        live.groups.network.online = false
        val later = live.uuidOf(live.record())
        advanceTimeBy(FeedPublisher.DEFAULT_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertNull(live.feed.post("g1", later))
        live.groups.network.online = true
        live.groups.sync.status.value = SyncStatus(lastSuccessAt = 1L)
        advanceTimeBy(FeedPublisher.DEFAULT_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertNotNull(live.feed.post("g1", later))
        scope.cancel()
    }

    @Test
    fun `a guest never flushes`() = runTest {
        val scope = TestScope(testScheduler)
        val live = FeedTestKit(scope)
        live.groups.auth.signOut()
        live.publisher.start()
        live.record()
        advanceTimeBy(FeedPublisher.DEFAULT_DEBOUNCE_MILLIS * 2)
        runCurrent()
        assertTrue(live.feed.calls.isEmpty())
        scope.cancel()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
