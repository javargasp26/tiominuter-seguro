package com.javargasp.tiominutermvi.domain.auth

import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Small local settings of the account feature. Reads are synchronous (the start destination is
 * chosen from them before the first frame, so there is no flicker).
 */
interface AuthPreferences {
    /** True once the person chose Google or guest on Welcome (or an existing install was migrated). */
    fun isStartChoiceMade(): Boolean

    fun setStartChoiceMade()

    /** True after the first launch of a version with accounts has looked for existing data. */
    fun isInstallChecked(): Boolean

    fun setInstallChecked()

    /** When the guest banner was last dismissed ([WallClock] millis), or null. */
    fun guestBannerDismissedAt(): Long?

    fun setGuestBannerDismissedAt(millis: Long)
}

/**
 * Existing installs (updated from a version without accounts, so they already have local data)
 * start as guest and skip Welcome. Runs once, at the first launch of this version, before
 * anything creates a database (a fresh install has none yet). Later launches never migrate, so a
 * fresh install that closes Welcome without choosing still sees it next time.
 *
 * @param hasLocalData true when the app's databases already exist on disk
 */
class ExistingInstallMigration(
    private val preferences: AuthPreferences,
    private val hasLocalData: () -> Boolean
) {
    fun run() {
        if (preferences.isInstallChecked()) return
        if (!preferences.isStartChoiceMade() && hasLocalData()) preferences.setStartChoiceMade()
        preferences.setInstallChecked()
    }
}

/** When the Home guest banner shows: guests only; after a dismissal, again after 7 days at most once. */
object GuestBannerRule {
    const val REAPPEAR_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun isVisible(session: AuthSession, dismissedAt: Long?, now: Long): Boolean =
        session is AuthSession.Guest && (dismissedAt == null || now - dismissedAt >= REAPPEAR_AFTER_MILLIS)
}

/** The guest banner's visibility (from the session and the last dismissal) and its dismissal. */
class GuestBanner(
    private val authRepository: AuthRepository,
    private val preferences: AuthPreferences,
    private val wallClock: WallClock
) {
    private val dismissedAt = MutableStateFlow(preferences.guestBannerDismissedAt())

    /** "Now" is read on every emission; Home re-subscribes each time it is shown. */
    fun visible(): Flow<Boolean> =
        combine(authRepository.session, dismissedAt) { session, dismissed ->
            GuestBannerRule.isVisible(session, dismissed, wallClock.nowMillis())
        }

    fun dismiss() {
        val now = wallClock.nowMillis()
        preferences.setGuestBannerDismissedAt(now)
        dismissedAt.value = now
    }
}
