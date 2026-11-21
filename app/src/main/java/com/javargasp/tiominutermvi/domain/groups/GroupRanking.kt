package com.javargasp.tiominutermvi.domain.groups

/**
 * A member's line in this week's ranking. [workouts] is 0 when their summary is from another week
 * ([isCurrentWeek] false); [compliance] = min(workouts / goal, 1).
 */
data class RankedMember(
    val position: Int,
    val member: GroupMember,
    val workouts: Int,
    val compliance: Double,
    val isCurrentWeek: Boolean,
    val isMe: Boolean
)

/** The ranking is computed by every reader, never stored (docs/firestore-model.md "Ranking"). */
object GroupRanking {

    /**
     * Order: compliance (desc), then streak (desc), then name ([nameOrder], case-insensitive by
     * default), then uid, so the order is stable. [currentWeekStart] is the reader's Monday.
     */
    fun rank(
        members: List<GroupMember>,
        currentWeekStart: String,
        myUid: String?,
        nameOrder: Comparator<String> = String.CASE_INSENSITIVE_ORDER
    ): List<RankedMember> {
        val lines = members.map { member ->
            val current = member.summary.weekStartLocal == currentWeekStart
            val workouts = if (current) member.summary.workoutsThisWeek else 0
            val goal = member.summary.weeklyGoal.coerceAtLeast(1)
            RankedMember(
                position = 0,
                member = member,
                workouts = workouts,
                compliance = minOf(workouts.toDouble() / goal, 1.0),
                isCurrentWeek = current,
                isMe = member.uid == myUid
            )
        }
        val order = compareByDescending<RankedMember> { it.compliance }
            .thenByDescending { it.member.summary.streakWeeks }
            .thenBy(nameOrder) { it.member.profile.displayName }
            .thenBy { it.member.uid }
        return lines.sortedWith(order).mapIndexed { index, line -> line.copy(position = index + 1) }
    }
}
