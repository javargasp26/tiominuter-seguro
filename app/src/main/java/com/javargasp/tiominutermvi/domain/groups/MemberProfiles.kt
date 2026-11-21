package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.auth.AuthSession

/** The name and photo my member docs carry, from the Google account. */
class MemberProfiles(private val texts: GroupTexts) {

    /** The name truncated to 100 characters ("Member" when there is none), and only an https photo. */
    fun of(session: AuthSession.SignedIn): MemberProfile {
        val name = session.displayName?.trim()?.take(GroupLimits.DISPLAY_NAME_MAX_LENGTH)?.trim()
        return MemberProfile(
            displayName = if (name.isNullOrEmpty()) texts.memberFallbackName() else name,
            photoUrl = session.photoUrl?.takeIf { it.startsWith("https://") && it.length <= GroupLimits.PHOTO_URL_MAX_LENGTH }
        )
    }
}
