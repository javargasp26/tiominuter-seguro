package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource

/**
 * Groups (Phase 3): friends compare their week. The Firestore model and its rules are in
 * docs/firestore-model.md "Groups"; this package holds the app's side of it, pure where it can be.
 */

/** A group as its members see it. */
data class GroupInfo(
    val id: String,
    val name: String,
    val ownerUid: String,
    val inviteCode: String,
    val memberCount: Int
)

/**
 * What a member publishes about their week (and nothing else: no durations, notes, titles,
 * measurements or photos). [daysThisWeek] maps 1 (Monday) … 7 (Sunday) to the distinct sources
 * of that day's counted workouts, in [TrainingSource] order; days without one are absent.
 */
data class MemberSummary(
    val weekStartLocal: String,
    val workoutsThisWeek: Int,
    val weeklyGoal: Int,
    val streakWeeks: Int,
    val daysThisWeek: Map<Int, List<TrainingSource>>,
    val lastTrainedLocal: String?
)

/** The name and photo a member shows (see [MemberProfiles]). */
data class MemberProfile(val displayName: String, val photoUrl: String?)

/** One member doc of a group. [joinedAtMillis] is null while the server time is pending. */
data class GroupMember(
    val uid: String,
    val profile: MemberProfile,
    val joinedAtMillis: Long?,
    val summary: MemberSummary
)

/** An entry of my membership index (`users/{uid}/groups/{groupId}`). */
data class Membership(val groupId: String, val joinedAtMillis: Long?)

/** What `inviteCodes/{code}` tells someone about to join. */
data class InvitePreview(val code: String, val groupId: String, val groupName: String)

/** A group with its members, as a listener or a one-shot read returns them. */
data class GroupSnapshot(val group: GroupInfo, val members: List<GroupMember>)

/** Product limits (the rules enforce the same ones). */
object GroupLimits {
    const val MAX_MEMBERS = 20
    const val MAX_GROUPS = 5
    const val NAME_MAX_LENGTH = 40
    const val DISPLAY_NAME_MAX_LENGTH = 100
    const val PHOTO_URL_MAX_LENGTH = 2048
    const val MAX_WORKOUTS = 100
    const val MAX_STREAK = 1000

    /** 1–40 characters after trimming, one line. */
    fun normalizeName(input: String): String? {
        val name = input.trim()
        return name.takeIf { it.isNotEmpty() && it.length <= NAME_MAX_LENGTH && '\n' !in it && '\r' !in it }
    }
}

/** Why a group operation failed; the UI maps each to a friendly message. */
enum class GroupError {
    NETWORK,
    NOT_SIGNED_IN,
    GOAL_REQUIRED,
    INVALID_CODE,
    GROUP_FULL,
    ALREADY_MEMBER,
    GROUP_LIMIT,
    INVALID_NAME,

    /** The group or my membership is gone (deleted, or I was removed). */
    NOT_FOUND,

    /** The rules refused the write or read (logged without personal data, never retried in a loop). */
    PERMISSION_DENIED,
    FAILED
}

/** A failed group operation; [causeName] is the short exception class name (no personal data). */
class GroupException(val error: GroupError, val causeName: String? = null) :
    Exception("Group operation failed: $error${causeName?.let { " ($it)" } ?: ""}")
