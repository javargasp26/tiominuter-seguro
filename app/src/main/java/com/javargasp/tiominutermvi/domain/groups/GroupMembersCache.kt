package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.time.WallClock

/**
 * The member docs of my groups for Home's banner, kept in memory for [ttlMillis] (≤ 5 groups × 20
 * members). Cleared on every account change and after my own group operations.
 */
class GroupMembersCache(private val clock: WallClock, private val ttlMillis: Long = DEFAULT_TTL_MILLIS) {

    private data class Entry(val uid: String, val fetchedAt: Long, val groups: List<GroupMembers>)

    @Volatile
    private var entry: Entry? = null

    fun get(uid: String): List<GroupMembers>? {
        val cached = entry ?: return null
        val age = clock.nowMillis() - cached.fetchedAt
        return cached.groups.takeIf { cached.uid == uid && age in 0 until ttlMillis }
    }

    fun put(uid: String, groups: List<GroupMembers>) {
        entry = Entry(uid, clock.nowMillis(), groups)
    }

    fun clear() {
        entry = null
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L
    }
}
