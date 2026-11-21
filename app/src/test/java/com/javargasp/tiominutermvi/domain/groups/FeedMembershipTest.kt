package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.testutil.FeedTestKit
import com.javargasp.tiominutermvi.testutil.feedPost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The feed sequences of the membership changes (docs/firestore-model.md "Feed", "Operations"). */
class FeedMembershipTest {

    private val kit = FeedTestKit()
    private val groups = kit.groups.repository
    private val feed = kit.feed
    private val now = kit.groups.now

    private fun postsOf(groupId: String, vararg authors: String) {
        authors.forEachIndexed { i, author -> feed.seed(groupId, feedPost("$author-$i", author, now - i)) }
    }

    @Test
    fun `leaving deletes my posts first, then the leave batch, and the others' posts stay`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("ana", "me", "luis"))
        postsOf("g", "me", "ana", "me", "luis")
        groups.beforeOp = { call ->
            if (call.startsWith("leaveGroup")) {
                assertTrue("my posts are gone before I leave", feed.posts.getValue("g").values.none { it.authorUid == "me" })
            }
        }
        kit.operations.leave(groups.groups.getValue("g"))
        assertEquals(listOf("ana", "luis"), feed.posts.getValue("g").values.map { it.authorUid }.sorted())
        assertNull(groups.members.getValue("g")["me"])
    }

    @Test
    fun `an owner leaving with a transfer deletes only their own posts`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("me", "ana"))
        postsOf("g", "me", "ana")
        kit.operations.leave(groups.groups.getValue("g"), newOwnerUid = "ana")
        assertEquals(listOf("ana"), feed.posts.getValue("g").values.map { it.authorUid })
        assertEquals("ana", groups.groups.getValue("g").ownerUid)
    }

    @Test
    fun `a failed post deletion stops the leave, so I stay a member and can retry`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("ana", "me"))
        postsOf("g", "me")
        feed.failOn("deletePost", GroupError.NETWORK)
        try {
            kit.operations.leave(groups.groups.getValue("g"))
            fail("expected a failure")
        } catch (e: GroupException) {
            assertEquals(GroupError.NETWORK, e.error)
        }
        assertNotNull(groups.members.getValue("g")["me"])
    }

    @Test
    fun `the owner removes a member, then deletes that member's posts`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("me", "ana", "luis"))
        postsOf("g", "ana", "luis", "ana", "me")
        groups.beforeOp = { call ->
            if (call == "removeMember:g:ana") assertEquals(2, feed.posts.getValue("g").values.count { it.authorUid == "ana" })
        }
        kit.operations.removeMember(groups.groups.getValue("g"), "ana")
        assertEquals(listOf("luis", "me"), feed.posts.getValue("g").values.map { it.authorUid }.sorted())
    }

    @Test
    fun `a removal stays done when deleting the posts fails (logged, they expire)`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("me", "ana"))
        postsOf("g", "ana")
        feed.failOn("postIdsBy", GroupError.NETWORK)
        kit.operations.removeMember(groups.groups.getValue("g"), "ana")
        assertNull(groups.members.getValue("g")["ana"])
        assertTrue(kit.groups.log.messages.any { "posts of a removed member" in it })
    }

    @Test
    fun `delete group deletes every post, then the members one by one, then the group`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("me", "ana", "luis"))
        postsOf("g", "me", "ana", "luis", "ana")
        groups.beforeOp = { call ->
            if (call.startsWith("removeMember") || call.startsWith("deleteGroup")) {
                assertTrue("posts first, while the group exists", feed.posts.getValue("g").isEmpty())
            }
        }
        kit.operations.delete(groups.groups.getValue("g"))
        assertNull(groups.groups["g"])
        assertEquals(
            listOf("removeMember:g:ana", "removeMember:g:luis", "deleteGroup:g"),
            groups.calls.filter { it.startsWith("removeMember") || it.startsWith("deleteGroup") }
        )
    }

    @Test
    fun `an owner alone leaving deletes the group's posts with it`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("me"))
        postsOf("g", "me", "me")
        kit.operations.leave(groups.groups.getValue("g"))
        assertTrue(feed.posts.getValue("g").isEmpty())
        assertNull(groups.groups["g"])
    }

    @Test
    fun `delete account removes my posts in every group before any group is left`() = runTest {
        groups.seedGroup("member", "M", "AAAAAAAA", listOf("ana", "me"))
        groups.seedGroup("shared", "S", "BBBBBBBB", listOf("me", "ana"), joinedAt = mapOf("me" to 1, "ana" to 2))
        groups.seedGroup("solo", "Solo", "CCCCCCCC", listOf("me"))
        postsOf("member", "me", "ana")
        postsOf("shared", "ana", "me")
        postsOf("solo", "me")
        groups.beforeOp = { call ->
            if (call.startsWith("leaveGroup") || call.startsWith("deleteGroup") || call.startsWith("removeMember")) {
                feed.posts.forEach { (groupId, posts) ->
                    assertTrue("no post of mine left in $groupId when the group cleanup starts", posts.values.none { it.authorUid == "me" })
                }
            }
        }
        val cleaner = GroupAccountCleaner(groups, kit.groups.reconciler, kit.groups.cache, kit.moderation)
        cleaner.leaveAllGroups("me")

        assertEquals(listOf("ana"), feed.posts.getValue("member").values.map { it.authorUid })
        assertEquals(listOf("ana"), feed.posts.getValue("shared").values.map { it.authorUid })
        assertTrue(feed.posts.getValue("solo").isEmpty())
        assertEquals("ana", groups.groups.getValue("shared").ownerUid)
        assertNull(groups.groups["solo"])
    }

    @Test
    fun `delete account stops before leaving any group when a post can't be deleted`() = runTest {
        groups.seedGroup("g", "G", "AAAAAAAA", listOf("ana", "me"))
        postsOf("g", "me")
        feed.failOn("deletePost", GroupError.PERMISSION_DENIED)
        val cleaner = GroupAccountCleaner(groups, kit.groups.reconciler, kit.groups.cache, kit.moderation)
        try {
            cleaner.leaveAllGroups("me")
            fail("expected a failure")
        } catch (e: GroupException) {
            assertEquals(GroupError.PERMISSION_DENIED, e.error)
        }
        assertTrue(groups.calls.none { it.startsWith("leaveGroup") })
    }
}
