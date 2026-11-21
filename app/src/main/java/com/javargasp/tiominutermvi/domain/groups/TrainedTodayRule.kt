package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates

/** Home's "already trained today" banner: who trained, and in which of my groups. */
data class TrainedTodayBanner(val names: List<String>, val groupIds: List<String>)

/** The members of one of my groups, as Home fetched them. */
data class GroupMembers(val groupId: String, val members: List<GroupMember>)

/** When Home shows the banner (pure). */
object TrainedTodayRule {

    /**
     * Shown only to a signed-in person ([myUid] not null) without a counted workout [today], not
     * dismissed [today], when at least one other member of their groups trained today by their own
     * summary (lastTrainedLocal == today, a summary of this week; stale docs are ignored). Names are
     * distinct people across all groups (by uid), excluding me, sorted by name.
     */
    fun banner(
        myUid: String?,
        today: String,
        iTrainedToday: Boolean,
        dismissedOn: String?,
        groups: List<GroupMembers>
    ): TrainedTodayBanner? {
        if (myUid == null || iTrainedToday || dismissedOn == today) return null
        val weekStart = CalendarDates.mondayOf(today)
        val trainedByUid = linkedMapOf<String, GroupMember>()
        val groupIds = mutableListOf<String>()
        groups.forEach { group ->
            val trainers = group.members.filter {
                it.uid != myUid && it.summary.lastTrainedLocal == today && it.summary.weekStartLocal == weekStart
            }
            if (trainers.isNotEmpty()) groupIds += group.groupId
            trainers.forEach { trainedByUid.putIfAbsent(it.uid, it) }
        }
        if (trainedByUid.isEmpty()) return null
        val names = trainedByUid.values.map { it.profile.displayName }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return TrainedTodayBanner(names, groupIds)
    }
}
