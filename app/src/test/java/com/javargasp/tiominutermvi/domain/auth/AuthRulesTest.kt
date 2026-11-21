package com.javargasp.tiominutermvi.domain.auth

import com.javargasp.tiominutermvi.data.auth.FakeAuthRepository
import com.javargasp.tiominutermvi.data.auth.InMemoryAuthPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

    // --- Existing installs ---

    @Test
    fun `an updated install with local data starts as guest without Welcome`() {
        val preferences = InMemoryAuthPreferences()

        ExistingInstallMigration(preferences) { true }.run()

        assertTrue(preferences.isStartChoiceMade())
        assertTrue(preferences.isInstallChecked())
    }

    @Test
    fun `a fresh install shows Welcome`() {
        val preferences = InMemoryAuthPreferences()

        ExistingInstallMigration(preferences) { false }.run()

        assertFalse(preferences.isStartChoiceMade())
        assertTrue(preferences.isInstallChecked())
    }

    @Test
    fun `a fresh install closed on Welcome still shows it after the databases exist`() {
        val preferences = InMemoryAuthPreferences()
        ExistingInstallMigration(preferences) { false }.run()

        // Second launch: the databases were created meanwhile, but no choice was made
        ExistingInstallMigration(preferences) { true }.run()

        assertFalse(preferences.isStartChoiceMade())
    }

    @Test
    fun `the migration never runs the data check once the install was checked`() {
        val preferences = InMemoryAuthPreferences(installChecked = true)

        ExistingInstallMigration(preferences) { error("must not be called") }.run()

        assertFalse(preferences.isStartChoiceMade())
    }

    // --- Guest banner rule ---

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun `the banner shows to guests never dismissed`() {
        assertTrue(GuestBannerRule.isVisible(AuthSession.Guest, dismissedAt = null, now = now))
    }

    @Test
    fun `the banner never shows when signed in`() {
        assertFalse(GuestBannerRule.isVisible(FakeAuthRepository.SAMPLE_USER, dismissedAt = null, now = now))
    }

    @Test
    fun `after a dismissal the banner returns at 7 days, not before`() {
        assertFalse(GuestBannerRule.isVisible(AuthSession.Guest, dismissedAt = now - 7 * day + 1, now = now))
        assertTrue(GuestBannerRule.isVisible(AuthSession.Guest, dismissedAt = now - 7 * day, now = now))
        assertTrue(GuestBannerRule.isVisible(AuthSession.Guest, dismissedAt = now - 30 * day, now = now))
    }
}
