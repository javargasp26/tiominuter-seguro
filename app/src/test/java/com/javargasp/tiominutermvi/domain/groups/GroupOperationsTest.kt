package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.testutil.GroupsTestKit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The product rules the app checks before each batch, over a fake Firestore that applies the rules. */
class GroupOperationsTest {

    private val kit = GroupsTestKit()
    private val repository = kit.repository
    private val operations = kit.operations

    private suspend fun assertFails(error: GroupError, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $error")
        } catch (e: GroupException) {
            assertEquals(error, e.error)
        }
    }

    // ---------- Create ----------

    @Test
    fun `create makes me owner and only member, with a code, my index and counter`() = runTest {
        val created = operations.create("  Morning crew ")

        assertEquals(CreatedGroup("group1", "Morning crew", "AAAAAAAA"), created)
        assertEquals(GroupInfo("group1", "Morning crew", "me", "AAAAAAAA", 1), repository.groups["group1"])
        assertEquals("Alex Rivera", repository.members.getValue("group1").getValue("me").profile.displayName)
        assertEquals(1, repository.counters["me"])
    }

    @Test
    fun `a taken code is retried with a new one, up to 3 times`() = runTest {
        repository.seedGroup("other", "Other", "AAAAAAAA", listOf("ana"))
        assertEquals("BBBBBBBB", operations.create("Crew").code)

        repository.seedGroup("o2", "O2", "CCCCCCCC", listOf("ana"))
        repository.failOn("createGroup", GroupError.PERMISSION_DENIED, times = 3)
        assertFails(GroupError.PERMISSION_DENIED) { operations.create("Again") }
    }

    @Test
    fun `create needs a valid name, a goal, the network, a session and room for one more group`() = runTest {
        assertFails(GroupError.INVALID_NAME) { operations.create("   ") }
        assertFails(GroupError.INVALID_NAME) { operations.create("x".repeat(41)) }

        kit.network.online = false
        assertFails(GroupError.NETWORK) { operations.create("Crew") }
        kit.network.online = true

        kit.goals.goals.value = emptyList()
        assertFails(GroupError.GOAL_REQUIRED) { operations.create("Crew") }
        assertFalse(operations.hasWeeklyGoal())
        kit.goals.goals.value = listOf(WeeklyGoalFixture.goal)

        (1..5).forEach { repository.seedGroup("g$it", "G$it", "CODE000$it", listOf("me")) }
        assertFails(GroupError.GROUP_LIMIT) { operations.create("Sixth") }

        kit.auth.signOut()
        assertFails(GroupError.NOT_SIGNED_IN) { operations.create("Crew") }
        assertTrue(repository.calls.none { it.startsWith("createGroup") })
    }

    // ---------- Join ----------

    @Test
    fun `join with a looked-up code adds me with my summary`() = runTest {
        repository.seedGroup("g1", "Crew", "K7MPX2QA", listOf("ana"))

        val invite = operations.findInvite(" k7mp x2qa ")
        assertEquals(InvitePreview("K7MPX2QA", "g1", "Crew"), invite)
        assertEquals("g1", operations.join(invite))

        assertEquals(2, repository.groups.getValue("g1").memberCount)
        assertEquals(3, repository.members.getValue("g1").getValue("me").summary.weeklyGoal)
    }

    @Test
    fun `join errors - invalid code, group full, already a member, 5 groups, a regenerated code`() = runTest {
        assertFails(GroupError.INVALID_CODE) { operations.findInvite("K7MPX2QO") }
        assertFails(GroupError.INVALID_CODE) { operations.findInvite("ZZZZZZZZ") }

        repository.seedGroup("full", "Full", "FZFZFZFZ", (1..20).map { "m$it" })
        assertFails(GroupError.GROUP_FULL) { operations.join(operations.findInvite("FZFZFZFZ")) }

        repository.seedGroup("mine", "Mine", "MYMYMYMY", listOf("ana", "me"))
        assertFails(GroupError.ALREADY_MEMBER) { operations.join(operations.findInvite("MYMYMYMY")) }

        repository.seedGroup("old", "Old", "KKKKKKKK", listOf("ana"))
        val stale = operations.findInvite("KKKKKKKK")
        repository.regenerateCode("old", "KKKKKKKK", "NNNNNNNN", "Old")
        assertFails(GroupError.INVALID_CODE) { operations.join(stale) }

        (1..4).forEach { repository.seedGroup("g$it", "G$it", "CODE000$it", listOf("me")) }
        repository.seedGroup("open", "Open", "PPPPPPPP", listOf("ana"))
        assertFails(GroupError.GROUP_LIMIT) { operations.join(operations.findInvite("PPPPPPPP")) }
    }

    @Test
    fun `joining again after being removed works, because the stale index entry is reconciled first`() = runTest {
        repository.seedGroup("g1", "Crew", "K7MPX2QA", listOf("ana", "me"))
        repository.removeMember("g1", "me")

        operations.join(operations.findInvite("K7MPX2QA"))

        assertTrue(repository.members.getValue("g1").containsKey("me"))
        assertEquals(1, repository.counters["me"])
    }

    // ---------- Leave ----------

    @Test
    fun `a member leaves, an owner alone deletes the group, an owner with members must name an heir`() = runTest {
        repository.seedGroup("theirs", "Theirs", "AAAA2222", listOf("ana", "me"))
        operations.leave(repository.groups.getValue("theirs"))
        assertFalse(repository.members.getValue("theirs").containsKey("me"))

        repository.seedGroup("solo", "Solo", "BBBB2222", listOf("me"))
        operations.leave(repository.groups.getValue("solo"))
        assertNull(repository.groups["solo"])
        assertNull(repository.invites["BBBB2222"])

        repository.seedGroup("mine", "Mine", "CCCC2222", listOf("me", "ana"))
        assertFails(GroupError.PERMISSION_DENIED) { operations.leave(repository.groups.getValue("mine")) }
        operations.leave(repository.groups.getValue("mine"), newOwnerUid = "ana")
        assertEquals("ana", repository.groups.getValue("mine").ownerUid)
        assertEquals(0, repository.counters["me"])
    }

    // ---------- Owner actions ----------

    @Test
    fun `owner actions - rename, regenerate, remove, transfer - and none for a member`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("me", "ana", "luis"))
        val group = { repository.groups.getValue("g") }

        operations.rename(group(), " New name ")
        assertEquals("New name", group().name)
        assertEquals("g" to "New name", repository.invites["CREWCREW"])

        assertEquals("AAAAAAAA", operations.regenerateCode(group()))
        assertNull(repository.invites["CREWCREW"])

        operations.removeMember(group(), "luis")
        assertEquals(2, group().memberCount)

        operations.transfer(group(), "ana")
        assertEquals("ana", group().ownerUid)
        assertFails(GroupError.PERMISSION_DENIED) { operations.rename(group(), "Mine again") }
        assertFails(GroupError.PERMISSION_DENIED) { operations.delete(group()) }
        assertTrue(kit.log.messages.any { it.contains("PERMISSION_DENIED") && !it.contains("ana") })
    }

    @Test
    fun `delete removes every other member one batch at a time, then deletes the group`() = runTest {
        val others = (1..19).map { "m$it" }
        repository.seedGroup("g", "Full", "FZFZFZFZ", listOf("me") + others)

        operations.delete(repository.groups.getValue("g"))

        val removals = repository.calls.filter { it.startsWith("removeMember") }
        assertEquals(others.map { "removeMember:g:$it" }, removals)
        assertEquals("deleteGroup:g", repository.calls.last())
        assertNull(repository.groups["g"])
        assertTrue(repository.members.getValue("g").isEmpty())
        assertEquals(0, repository.counters["me"])
    }

    @Test
    fun `a member who leaves during the deletion doesn't stop it, one still there does`() = runTest {
        repository.seedGroup("g", "Crew", "CREWCREW", listOf("me", "ana", "luis"))
        // Ana leaves just before her removal, so the removal is denied
        repository.beforeOp = { if (it == "removeMember:g:ana") repository.members.getValue("g").remove("ana") }
        repository.failOn("removeMember", GroupError.PERMISSION_DENIED)
        operations.delete(repository.groups.getValue("g"))
        assertNull(repository.groups["g"])

        repository.beforeOp = {}
        repository.seedGroup("h", "Other", "OTHEROTH", listOf("me", "ana"))
        repository.failOn("removeMember", GroupError.PERMISSION_DENIED)
        assertFails(GroupError.PERMISSION_DENIED) { operations.delete(repository.groups.getValue("h")) }
        assertTrue(repository.groups.containsKey("h"))
    }

    // ---------- List ----------

    @Test
    fun `my groups come with my position this week`() = runTest {
        repository.seedGroup(
            "g", "Crew", "CREWCREW", listOf("ana", "me"),
            summaries = mapOf(
                "ana" to com.javargasp.tiominutermvi.testutil.FakeGroupRepository.summary(workouts = 3, goal = 3),
                "me" to com.javargasp.tiominutermvi.testutil.FakeGroupRepository.summary(workouts = 1, goal = 3)
            )
        )

        val groups = operations.myGroups()

        assertEquals(listOf(MyGroup(repository.groups.getValue("g"), myPosition = 2, isOwner = false)), groups)
    }
}

private object WeeklyGoalFixture {
    val goal = com.javargasp.tiominutermvi.domain.goal.WeeklyGoal(3, "2026-10-12")
}
