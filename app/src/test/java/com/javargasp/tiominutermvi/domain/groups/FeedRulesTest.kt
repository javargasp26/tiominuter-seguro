package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.testutil.FeedTestKit
import com.javargasp.tiominutermvi.testutil.feedPost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The feed's pure rules, the Spark cleanup and the share switch's controller. */
class FeedRulesTest {

    // ---------- Thumbnail ----------

    /** A fake JPEG whose size grows with the pixels and the quality, like a real one. */
    private fun encoder(bytesPerPixelAt60: Double, calls: MutableList<Pair<Int, Int>> = mutableListOf()) =
        { side: Int, quality: Int ->
            calls += side to quality
            ByteArray((side * side * bytesPerPixelAt60 * quality / 60).toInt())
        }

    @Test
    fun `the thumbnail is always within the target and the rules' cap, even from a huge image`() {
        for (density in listOf(0.01, 0.2, 0.5, 1.0, 3.0, 20.0)) {
            for (longest in listOf(80, 400, 4000, 12_000)) {
                val bytes = ThumbnailFitter.fit(longest, encoder(density))
                if (bytes != null) {
                    assertTrue(bytes.size <= FeedLimits.THUMB_TARGET_BYTES)
                    assertTrue(bytes.size <= FeedLimits.THUMB_MAX_BYTES)
                }
            }
        }
    }

    @Test
    fun `quality steps down first, then the size, and a small image is never upscaled`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        // 400 px at 60: 400*400*0.5 = 80 000 bytes (too big); at 40: 53 333 (fits)
        val bytes = ThumbnailFitter.fit(400, encoder(0.5, calls))
        assertNotNull(bytes)
        assertEquals(listOf(400 to 60, 400 to 50, 400 to 40), calls)

        val big = mutableListOf<Pair<Int, Int>>()
        ThumbnailFitter.fit(400, encoder(1.0, big))
        assertEquals("every quality at 400 first", ThumbnailFitter.QUALITIES.map { 400 to it }, big.take(4))
        assertEquals(300, big[4].first)

        val small = mutableListOf<Pair<Int, Int>>()
        ThumbnailFitter.fit(120, encoder(0.5, small))
        assertEquals(120, small.first().first)
    }

    @Test
    fun `no thumbnail when nothing fits or the image can't be encoded`() {
        assertNull(ThumbnailFitter.fit(400) { _, _ -> ByteArray(FeedLimits.THUMB_TARGET_BYTES + 1) })
        assertNull(ThumbnailFitter.fit(400) { _, _ -> null })
    }

    // ---------- Reactions ----------

    @Test
    fun `tapping sets a reaction, the same one removes it, another one changes it`() {
        assertEquals(Reaction.FIRE, ReactionRules.toggle(null, Reaction.FIRE))
        assertNull(ReactionRules.toggle(Reaction.FIRE, Reaction.FIRE))
        assertEquals(Reaction.CLAP, ReactionRules.toggle(Reaction.FIRE, Reaction.CLAP))
    }

    @Test
    fun `my reaction changes only my key, and counts group by reaction`() {
        val reactions = mapOf("ana" to Reaction.FIRE, "me" to Reaction.STRONG)
        assertEquals(mapOf("ana" to Reaction.FIRE, "me" to Reaction.FIRE), ReactionRules.withMine(reactions, "me", Reaction.FIRE))
        assertEquals(mapOf("ana" to Reaction.FIRE), ReactionRules.withMine(reactions, "me", null))
        val post = feedPost("p", "ana", 0L, reactions = mapOf("a" to Reaction.FIRE, "b" to Reaction.FIRE, "c" to Reaction.CLAP))
        assertEquals(mapOf(Reaction.FIRE to 2, Reaction.CLAP to 1), post.reactionCounts)
        assertEquals(listOf("strong", "fire", "clap", "raised"), Reaction.entries.map { it.code })
        assertNull(Reaction.fromCode("heart"))
    }

    // ---------- The current week ----------

    @Test
    fun `a post's week is the Monday of the workout's own date, Monday and Sunday included`() {
        val dates = FeedTestKit().groups.dates
        assertEquals("2026-10-19", dates.mondayOf("2026-10-19")) // Monday
        assertEquals("2026-10-19", dates.mondayOf("2026-10-25")) // Sunday
        assertEquals("2026-10-12", dates.mondayOf("2026-10-18")) // the Sunday before: last week
        assertEquals("2026-10-26", dates.mondayOf("2026-10-26"))
        assertEquals("2026-12-28", dates.mondayOf("2027-01-03")) // a week across the new year
    }

    // ---------- Cleanup (Spark) ----------

    @Test
    fun `the cleanup deletes posts older than 8 days one by one and skips the ones the server keeps`() = runTest {
        val kit = FeedTestKit()
        kit.groups.repository.seedGroup("g", "G", "AAAAAAAA", listOf("ana", "me"))
        val now = kit.groups.now
        kit.feed.uid = "me"
        listOf("old1", "old2").forEach { kit.feed.seed("g", feedPost(it, "ana", now - 9 * DAY)) }
        kit.feed.seed("g", feedPost("fresh", "ana", now - DAY))
        kit.feed.seed("g", feedPost("serverKeeps", "ana", now - 8 * DAY - 1_000))
        // The server's clock is 2 seconds behind mine: "serverKeeps" isn't expired there yet
        kit.feed.now = { now - 2_000 }

        assertEquals(2, kit.cleaner.cleanUpExpired("g"))
        assertNull(kit.feed.post("g", "old1"))
        assertNotNull(kit.feed.post("g", "fresh"))
        assertNotNull("refused silently", kit.feed.post("g", "serverKeeps"))
    }

    @Test
    fun `the cleanup at app start runs at most once every 6 hours, for every group`() = runTest {
        val kit = FeedTestKit()
        kit.groups.repository.seedGroup("g1", "G1", "AAAAAAAA", listOf("me"))
        kit.groups.repository.seedGroup("g2", "G2", "BBBBBBBB", listOf("ana", "me"))
        kit.cleaner.cleanUpIfDue("me")
        assertEquals(listOf("expiredPostIds:g1", "expiredPostIds:g2"), kit.feed.calls)

        kit.groups.now += FeedCleaner.DEFAULT_THROTTLE_MILLIS - 1
        kit.cleaner.cleanUpIfDue("me")
        assertEquals(2, kit.feed.calls.size)
        kit.groups.now += 1
        kit.cleaner.cleanUpIfDue("me")
        assertEquals(4, kit.feed.calls.size)
    }

    @Test
    fun `a failed cleanup at start is retried at the next start`() = runTest {
        val kit = FeedTestKit()
        kit.groups.repository.seedGroup("g1", "G1", "AAAAAAAA", listOf("me"))
        kit.groups.repository.failOn("memberships", GroupError.NETWORK)
        kit.cleaner.cleanUpIfDue("me")
        assertNull(kit.preferences.lastFeedCleanupAt("me"))
        kit.cleaner.cleanUpIfDue("me")
        assertNotNull(kit.preferences.lastFeedCleanupAt("me"))
    }

    // ---------- The share switch's controller ----------

    @Test
    fun `the switch shows for a shared workout of a signed-in member of a group, ON by default`() = runTest {
        val kit = FeedTestKit()
        val id = kit.record()
        assertNull("in no group", kit.sharing.options(id))
        kit.groups.repository.seedGroup("g1", "G1", "AAAAAAAA", listOf("me"))
        assertEquals(com.javargasp.tiominutermvi.domain.traininglog.ShareOptions(share = true, needsIntro = true), kit.sharing.options(id))
        assertNull("not counted", kit.sharing.options(kit.record(minutes = 5)))

        kit.sharing.acknowledgeIntro()
        assertFalse(kit.sharing.options(id)!!.needsIntro)
        kit.sharing.setShare(id, false)
        assertFalse(kit.sharing.options(id)!!.share)
        assertTrue(kit.sharing.isAvailable())

        kit.groups.auth.signOut()
        assertNull(kit.sharing.options(id))
        assertFalse(kit.sharing.isAvailable())
    }

    @Test
    fun `offline the switch uses the last known number of groups`() = runTest {
        val kit = FeedTestKit()
        kit.groups.repository.seedGroup("g1", "G1", "AAAAAAAA", listOf("me"))
        val id = kit.record()
        assertTrue(kit.sharing.isAvailable()) // reads the index: 1 group
        kit.groups.network.online = false
        val before = kit.groups.repository.calls.size
        assertNotNull(kit.sharing.options(id))
        assertEquals("no server read offline", before, kit.groups.repository.calls.size)
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
