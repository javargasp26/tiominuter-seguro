package com.javargasp.tiominutermvi.data.groups

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.FeedLimits
import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupTexts

/** [GroupPreferences] and [FeedPreferences] in SharedPreferences (file `group_preferences`). */
class SharedPreferencesGroupPreferences(private val preferences: SharedPreferences) : GroupPreferences, FeedPreferences {

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun publishedSummaryHash(uid: String): String? = preferences.getString(HASH + uid, null)

    override fun setPublishedSummaryHash(uid: String, hash: String?) {
        preferences.edit { putString(HASH + uid, hash) }
    }

    override fun isSummaryDirty(uid: String): Boolean = preferences.getBoolean(DIRTY + uid, false)

    override fun setSummaryDirty(uid: String, dirty: Boolean) {
        preferences.edit { putBoolean(DIRTY + uid, dirty) }
    }

    override fun lastReconciledAt(uid: String): Long? =
        if (preferences.contains(RECONCILED + uid)) preferences.getLong(RECONCILED + uid, 0L) else null

    override fun setLastReconciledAt(uid: String, millis: Long) {
        preferences.edit { putLong(RECONCILED + uid, millis) }
    }

    override fun trainedTodayDismissedOn(): String? = preferences.getString(BANNER_DISMISSED_ON, null)

    override fun setTrainedTodayDismissedOn(date: String?) {
        preferences.edit { putString(BANNER_DISMISSED_ON, date) }
    }

    override fun isFeedIntroSeen(uid: String): Boolean = preferences.getBoolean(FEED_INTRO + uid, false)

    override fun setFeedIntroSeen(uid: String) {
        preferences.edit { putBoolean(FEED_INTRO + uid, true) }
    }

    override fun lastFeedCleanupAt(uid: String): Long? =
        if (preferences.contains(FEED_CLEANUP + uid)) preferences.getLong(FEED_CLEANUP + uid, 0L) else null

    override fun setLastFeedCleanupAt(uid: String, millis: Long) {
        preferences.edit { putLong(FEED_CLEANUP + uid, millis) }
    }

    override fun hiddenPosts(uid: String): Set<String> =
        preferences.getStringSet(HIDDEN + uid, emptySet()).orEmpty().mapTo(mutableSetOf()) { it.substringBefore(HIDDEN_AT) }

    // Stored as "groupId/postId@millis"; a hidden post older than a post's life is gone anyway
    override fun hidePost(uid: String, key: String, nowMillis: Long) {
        val kept = preferences.getStringSet(HIDDEN + uid, emptySet()).orEmpty().filter {
            val at = it.substringAfter(HIDDEN_AT, "").toLongOrNull() ?: return@filter false
            nowMillis - at in 0..FeedLimits.SHARE_RETENTION_MILLIS
        }
        preferences.edit { putStringSet(HIDDEN + uid, (kept + "$key$HIDDEN_AT$nowMillis").toSet()) }
    }

    override fun knownGroupCount(uid: String): Int? =
        if (preferences.contains(GROUP_COUNT + uid)) preferences.getInt(GROUP_COUNT + uid, 0) else null

    override fun setKnownGroupCount(uid: String, count: Int) {
        preferences.edit { putInt(GROUP_COUNT + uid, count) }
    }

    override fun detailTab(groupId: String): Int? =
        if (preferences.contains(TAB + groupId)) preferences.getInt(TAB + groupId, 0) else null

    override fun setDetailTab(groupId: String, tab: Int) {
        preferences.edit { putInt(TAB + groupId, tab) }
    }

    companion object {
        const val FILE_NAME = "group_preferences"
        private const val HASH = "summaryHash_"
        private const val DIRTY = "summaryDirty_"
        private const val RECONCILED = "reconciledAt_"
        private const val BANNER_DISMISSED_ON = "trainedTodayDismissedOn"
        private const val FEED_INTRO = "feedIntroSeen_"
        private const val FEED_CLEANUP = "feedCleanupAt_"
        private const val HIDDEN = "hiddenPosts_"
        private const val HIDDEN_AT = "@"
        private const val GROUP_COUNT = "groupCount_"
        private const val TAB = "detailTab_"
    }
}

/** Group failures in logcat (tag `Groups`); callers never pass personal data. */
class LogcatGroupLog : GroupLog {
    override fun warn(message: String) {
        Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "Groups"
    }
}

/** [GroupTexts] from string resources (current language). */
class ResourceGroupTexts(private val context: Context) : GroupTexts {
    override fun memberFallbackName(): String = context.getString(R.string.groups_member_fallback_name)
}
