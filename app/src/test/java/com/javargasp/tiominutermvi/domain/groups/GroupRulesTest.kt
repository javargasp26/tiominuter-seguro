package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.testutil.FakeGroupRepository.Companion.summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Ranking order, the "already trained today" rule, invite codes, names and profiles. */
class GroupRulesTest {

    private val week = "2026-10-19"

    private fun member(uid: String, name: String, workouts: Int, goal: Int, streak: Int = 0, memberWeek: String = week, lastTrained: String? = null) =
        GroupMember(uid, MemberProfile(name, null), 0L, summary(memberWeek, workouts, goal, streak, lastTrained = lastTrained))

    // ---------- Ranking ----------

    @Test
    fun `ranking orders by compliance, capped at 1, then streak, then name, then uid`() {
        val ranking = GroupRanking.rank(
            listOf(
                member("a", "Zoe", 1, 4), // 0.25
                member("b", "ana", 6, 3, streak = 1), // 1 (capped)
                member("c", "Bea", 3, 3, streak = 2), // 1, longer streak
                member("d", "Ana", 3, 3, streak = 1), // 1, ties with b: name, case-insensitive, then uid
                member("e", "Luis", 2, 4) // 0.5
            ),
            week,
            myUid = "e"
        )

        assertEquals(listOf("c", "b", "d", "e", "a"), ranking.map { it.member.uid })
        assertEquals(listOf(1, 2, 3, 4, 5), ranking.map { it.position })
        assertEquals(1.0, ranking[1].compliance, 0.0)
        assertTrue(ranking[3].isMe)
    }

    @Test
    fun `a summary from a previous week counts as 0`() {
        val ranking = GroupRanking.rank(
            listOf(member("old", "Old", 5, 3, streak = 9, memberWeek = "2026-10-12"), member("new", "New", 1, 3)),
            week,
            myUid = null
        )

        assertEquals(listOf("new", "old"), ranking.map { it.member.uid })
        assertEquals(0, ranking[1].workouts)
        assertEquals(0.0, ranking[1].compliance, 0.0)
        assertFalse(ranking[1].isCurrentWeek)
    }

    // ---------- Already trained today ----------

    private val today = "2026-10-22"

    private fun group(id: String, vararg members: GroupMember) = GroupMembers(id, members.toList())

    @Test
    fun `names 1, 2 and more, distinct across groups, me excluded, sorted`() {
        val ana = member("ana", "Ana", 1, 3, lastTrained = today)
        val luis = member("luis", "Luis", 1, 3, lastTrained = today)
        val me = member("me", "Me", 1, 3, lastTrained = today)
        val banner = TrainedTodayRule.banner(
            "me", today, false, null,
            listOf(group("g1", luis, me), group("g2", ana, luis), group("g3", member("sam", "Sam", 0, 3)))
        )!!

        assertEquals(listOf("Ana", "Luis"), banner.names)
        assertEquals(listOf("g1", "g2"), banner.groupIds)
        assertEquals(listOf("Ana"), TrainedTodayRule.banner("me", today, false, null, listOf(group("g", ana)))!!.names)
    }

    @Test
    fun `hidden for a guest, once I trained, when dismissed today, or when nobody trained today`() {
        val groups = listOf(group("g", member("ana", "Ana", 1, 3, lastTrained = today)))

        assertNull(TrainedTodayRule.banner(null, today, false, null, groups))
        assertNull(TrainedTodayRule.banner("me", today, true, null, groups))
        assertNull(TrainedTodayRule.banner("me", today, false, today, groups))
        assertNull(TrainedTodayRule.banner("me", today, false, null, listOf(group("g", member("ana", "Ana", 1, 3, lastTrained = "2026-10-21")))))
    }

    @Test
    fun `a dismissal from yesterday shows it again on a new day, and stale docs are ignored`() {
        val groups = listOf(group("g", member("ana", "Ana", 1, 3, lastTrained = today)))
        assertEquals(listOf("Ana"), TrainedTodayRule.banner("me", today, false, "2026-10-21", groups)!!.names)

        val stale = listOf(group("g", member("ana", "Ana", 1, 3, memberWeek = "2026-10-12", lastTrained = today)))
        assertNull(TrainedTodayRule.banner("me", today, false, null, stale))
    }

    // ---------- Invite codes, names, profiles ----------

    @Test
    fun `codes are uppercased without spaces or dashes, and never use 0 O 1 I L`() {
        assertEquals("K7MPX2QA", InviteCodes.normalize(" k7mp-x2 qa "))
        assertTrue(InviteCodes.isValid("K7MPX2QA"))
        listOf("K7MPX2QO", "K7MPX2Q0", "K7MPX2QI", "K7MPX2Q1", "K7MPX2QL").forEach {
            assertTrue(it, InviteCodes.hasInvalidCharacters(it))
            assertFalse(InviteCodes.isValid(it))
        }
        assertFalse(InviteCodes.isValid("K7MPX2Q"))
        val random = Random(42)
        repeat(200) { assertTrue(InviteCodes.isValid(InviteCodes.generate(random))) }
    }

    @Test
    fun `group names are 1 to 40 characters once trimmed, one line`() {
        assertEquals("Crew", GroupLimits.normalizeName("  Crew "))
        assertNull(GroupLimits.normalizeName("   "))
        assertEquals("x".repeat(40), GroupLimits.normalizeName("x".repeat(40)))
        assertNull(GroupLimits.normalizeName("x".repeat(41)))
        assertNull(GroupLimits.normalizeName("a\nb"))
    }

    @Test
    fun `the profile truncates the name to 100, falls back to Member, keeps only an https photo`() {
        val profiles = MemberProfiles { "Member" }
        assertEquals(100, profiles.of(AuthSession.SignedIn("u", "x".repeat(150), null, null)).displayName.length)
        assertEquals("Member", profiles.of(AuthSession.SignedIn("u", "  ", null, null)).displayName)
        assertEquals("Member", profiles.of(AuthSession.SignedIn("u", null, null, "http://photo")).displayName)
        assertNull(profiles.of(AuthSession.SignedIn("u", "A", null, "http://photo")).photoUrl)
        assertEquals("https://photo", profiles.of(AuthSession.SignedIn("u", "A", null, "https://photo")).photoUrl)
    }
}
