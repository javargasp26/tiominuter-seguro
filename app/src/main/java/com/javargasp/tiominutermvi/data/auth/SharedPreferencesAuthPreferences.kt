package com.javargasp.tiominutermvi.data.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import com.javargasp.tiominutermvi.domain.auth.AuthPreferences

/**
 * [AuthPreferences] in a private SharedPreferences file (`auth_preferences`). The app had no
 * settings storage before; SharedPreferences needs no new dependency and is read synchronously,
 * which lets the start destination be chosen before the first frame.
 */
class SharedPreferencesAuthPreferences(private val preferences: SharedPreferences) : AuthPreferences {

    override fun isStartChoiceMade(): Boolean = preferences.getBoolean(KEY_START_CHOICE_MADE, false)

    override fun setStartChoiceMade() = preferences.edit { putBoolean(KEY_START_CHOICE_MADE, true) }

    override fun isInstallChecked(): Boolean = preferences.getBoolean(KEY_INSTALL_CHECKED, false)

    override fun setInstallChecked() = preferences.edit { putBoolean(KEY_INSTALL_CHECKED, true) }

    override fun guestBannerDismissedAt(): Long? =
        if (preferences.contains(KEY_BANNER_DISMISSED_AT)) preferences.getLong(KEY_BANNER_DISMISSED_AT, 0) else null

    override fun setGuestBannerDismissedAt(millis: Long) =
        preferences.edit { putLong(KEY_BANNER_DISMISSED_AT, millis) }

    companion object {
        const val FILE_NAME = "auth_preferences"
        private const val KEY_START_CHOICE_MADE = "start_choice_made"
        private const val KEY_INSTALL_CHECKED = "install_checked"
        private const val KEY_BANNER_DISMISSED_AT = "guest_banner_dismissed_at"
    }
}

/** [AuthPreferences] in memory, for tests and previews. */
class InMemoryAuthPreferences(
    var startChoiceMade: Boolean = false,
    var installChecked: Boolean = false,
    var bannerDismissedAt: Long? = null
) : AuthPreferences {
    override fun isStartChoiceMade() = startChoiceMade
    override fun setStartChoiceMade() { startChoiceMade = true }
    override fun isInstallChecked() = installChecked
    override fun setInstallChecked() { installChecked = true }
    override fun guestBannerDismissedAt() = bannerDismissedAt
    override fun setGuestBannerDismissedAt(millis: Long) { bannerDismissedAt = millis }
}
