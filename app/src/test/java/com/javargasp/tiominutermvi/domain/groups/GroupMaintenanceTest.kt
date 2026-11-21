package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.sync.SyncStatus
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.testutil.GroupsTestKit
import com.javargasp.tiominutermvi.testutil.trainingEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Reconciliation, summary publishing, the delete-account cleanup and Home's cached read. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupMaintenanceTest {

    // ---------- Reconciliation ----------

    @Test
    fun `entries of groups I was removed from or that were deleted are dropped, one batch each`() = runTest {
        val kit = GroupsTestKit()
        val repository = kit.repository
        repository.seedGroup("kept", "Kept", "KEPTKEPT", listOf("ana", "me"))
        repository.seedGroup("removed", "Removed", "REMOVEDD", listOf("ana", "me"))
        repository.seedGroup("deleted", "Deleted", "DELETEDD", listOf("ana", "me"))
        repository.removeMember("removed", "me")
        // Deleted by its owner without my member doc: it was left behind
        repository.groups.remove("deleted")

        val groups = kit.reconciler.reconcile("me")

        assertEquals(listOf("kept"), groups.map { it.id })
        assertEquals(listOf("kept"), repository.index.getValue("me").keys.toList())
        assertEquals(1, repository.counters["me"])
        assertFalse(repository.members.getValue("deleted").containsKey("me"))
    }

    @Test
    fun `the reconciliation at start runs at most every 6 hours, and only logs failures`() = runTest {
        val kit = GroupsTestKit()
        kit.reconciler.reconcileIfDue("me")
        kit.reconciler.reconcileIfDue("me")
        assertEquals(1, kit.repository.calls.count { it == "memberships" })

        kit.now += GroupReconciler.DEFAULT_THROTTLE_MILLIS
        kit.repository.failOn("memberships", GroupError.NETWORK)
        kit.reconciler.reconcileIfDue("me")
        assertEquals(listOf("Groups: reconciliation skipped (NETWORK)"), kit.log.messages)
    }

    // ---------- Publishing ----------

    private fun TestScope.publishingKit(): GroupsTestKit {
        val kit = GroupsTestKit(backgroundScope)
        kit.repository.seedGroup("g1", "One", "ONEONEON", listOf("ana", "me"))
        kit.repository.seedGroup("g2", "Two", "TWOTWOTW", listOf("luis", "me"))
        kit.publisher!!.start()
        return kit
    }

    private fun GroupsTestKit.mySummary(group: String) = repository.members.getValue(group).getValue("me").summary

    @Test
    fun `changes are debounced into one publish to every group, about 5 seconds later`() = runTest {
        val kit = publishingKit()
        advanceTimeBy(6_000)
        runCurrent()
        val first = kit.repository.calls.count { it.startsWith("updateSummary") }
        assertEquals(2, first)

        kit.trainingLog.entries.value = listOf(trainingEntry("2026-10-21"))
        advanceTimeBy(2_000)
        kit.trainingLog.entries.value = listOf(trainingEntry("2026-10-21"), trainingEntry("2026-10-22", TrainingSource.DAILY))
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(first, kit.repository.calls.count { it.startsWith("updateSummary") })

        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(first + 2, kit.repository.calls.count { it.startsWith("updateSummary") })
        assertEquals(2, kit.mySummary("g2").workoutsThisWeek)
        assertEquals("2026-10-22", kit.mySummary("g1").lastTrainedLocal)
    }

    @Test
    fun `an unchanged summary is not written again, a short workout changes nothing`() = runTest {
        val kit = publishingKit()
        advanceTimeBy(6_000)
        runCurrent()
        val writes = kit.repository.calls.count { it.startsWith("updateSummary") }

        kit.trainingLog.entries.value = listOf(trainingEntry("2026-10-22", minutes = 5))
        kit.publisher!!.request()
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(writes, kit.repository.calls.count { it.startsWith("updateSummary") })
    }

    @Test
    fun `offline marks the summary dirty, and the next successful sync publishes it`() = runTest {
        val kit = publishingKit()
        kit.network.online = false
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(kit.preferences.isSummaryDirty("me"))
        assertTrue(kit.repository.calls.none { it.startsWith("updateSummary") })

        kit.network.online = true
        kit.sync.status.value = SyncStatus(lastSuccessAt = 1L)
        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(2, kit.repository.calls.count { it.startsWith("updateSummary") })
        assertFalse(kit.preferences.isSummaryDirty("me"))
    }

    @Test
    fun `a group that rejects the write doesn't block the others, a failing one keeps it dirty`() = runTest {
        val kit = publishingKit()
        kit.repository.removeMember("g1", "me") // removed: NOT_FOUND, dropped by the reconciliation later
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(3, kit.mySummary("g2").weeklyGoal)
        assertFalse(kit.preferences.isSummaryDirty("me"))

        kit.trainingLog.entries.value = listOf(trainingEntry("2026-10-22"))
        kit.repository.failOn("updateSummary", GroupError.NETWORK)
        kit.repository.seedGroup("g3", "Three", "THREETHR", listOf("sam", "me"))
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(kit.preferences.isSummaryDirty("me"))
        assertEquals(1, kit.mySummary("g3").workoutsThisWeek)
    }

    @Test
    fun `a new week is published when Home asks, guests and people without a goal publish nothing`() = runTest {
        val kit = publishingKit()
        advanceTimeBy(6_000)
        runCurrent()

        kit.now += 4 * 24 * 60 * 60 * 1000L // Monday 26
        kit.publisher!!.request()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals("2026-10-26", kit.mySummary("g1").weekStartLocal)

        val session = kit.auth.session.value as AuthSession.SignedIn
        kit.goals.goals.value = emptyList()
        assertEquals(PublishOutcome.SKIPPED, kit.publisher.publish(session))
        kit.auth.signOut()
        val writes = kit.repository.calls.size
        kit.publisher.request()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(writes, kit.repository.calls.size)
    }

    // ---------- Delete account ----------

    @Test
    fun `the account cleanup leaves, hands owned groups to the oldest member, deletes solo groups`() = runTest {
        val kit = GroupsTestKit()
        val repository = kit.repository
        repository.seedGroup("member", "Member", "MEMBERME", listOf("ana", "me"))
        repository.seedGroup("shared", "Shared", "SHAREDSH", listOf("me", "luis", "bea"), joinedAt = mapOf("me" to 1L, "luis" to 30L, "bea" to 20L))
        repository.seedGroup("solo", "Solo", "SOLOSOLO", listOf("me"))
        repository.seedGroup("gone", "Gone", "GONEGONE", listOf("ana", "me"))
        repository.removeMember("gone", "me")

        GroupAccountCleaner(repository, kit.reconciler, kit.cache).leaveAllGroups("me")

        assertEquals("bea", repository.groups.getValue("shared").ownerUid)
        assertFalse(repository.members.getValue("member").containsKey("me"))
        assertNull(repository.groups["solo"])
        assertTrue(repository.index["me"].isNullOrEmpty())
        assertNull(repository.counters["me"])
        assertEquals("deleteGroupCounter", repository.calls.last())
    }

    @Test
    fun `a failure stops the cleanup with an error`() = runTest {
        val kit = GroupsTestKit()
        kit.repository.seedGroup("member", "Member", "MEMBERME", listOf("ana", "me"))
        kit.repository.failOn("leaveGroup", GroupError.NETWORK)
        try {
            GroupAccountCleaner(kit.repository, kit.reconciler, kit.cache).leaveAllGroups("me")
            fail("Expected a failure")
        } catch (e: GroupException) {
            assertEquals(GroupError.NETWORK, e.error)
        }
        assertTrue(kit.repository.calls.none { it == "deleteGroupCounter" })
    }

    @Test
    fun `the heir is the oldest other member, a pending time last`() {
        fun m(uid: String, joined: Long?) = com.javargasp.tiominutermvi.domain.groups.GroupMember(
            uid, MemberProfile(uid, null), joined, com.javargasp.tiominutermvi.testutil.FakeGroupRepository.summary()
        )
        assertEquals("b", GroupAccountCleaner.oldestOtherMember("me", listOf(m("me", 1), m("c", null), m("b", 5), m("a", 9)))?.uid)
        assertNull(GroupAccountCleaner.oldestOtherMember("me", listOf(m("me", 1))))
    }

    // ---------- Home ----------

    @Test
    fun `Home reads my groups' members once, cached for 10 minutes, nothing for a guest`() = runTest {
        val kit = GroupsTestKit(backgroundScope)
        kit.repository.seedGroup("g1", "One", "ONEONEON", listOf("ana", "me"))
        val home = DefaultGroupsHome(kit.auth, kit.repository, kit.reconciler, kit.publisher!!, kit.cache, kit.preferences, kit.log, backgroundScope)

        assertEquals(listOf("ana", "me"), home.onHomeShown()!!.groups.single().members.map { it.uid })
        home.onHomeShown()
        assertEquals(1, kit.repository.calls.count { it == "members:g1" })

        kit.now += GroupMembersCache.DEFAULT_TTL_MILLIS
        home.onHomeShown()
        assertEquals(2, kit.repository.calls.count { it == "members:g1" })

        home.dismissTrainedToday("2026-10-22")
        assertEquals("2026-10-22", home.trainedTodayDismissedOn())
        kit.auth.signOut()
        assertNull(home.onHomeShown())
    }

    @Test
    fun `an account change clears the cache and the banner dismissal`() = runTest {
        val kit = GroupsTestKit(backgroundScope)
        kit.preferences.dismissedOn = "2026-10-22"
        kit.cache.put("me", emptyList())
        GroupsCoordinator(kit.auth, kit.publisher!!, kit.reconciler, kit.cache, kit.preferences, backgroundScope).start()
        runCurrent()
        assertEquals("2026-10-22", kit.preferences.dismissedOn)

        kit.auth.signOut()
        runCurrent()

        assertNull(kit.preferences.dismissedOn)
        assertNull(kit.cache.get("me"))
    }
}
