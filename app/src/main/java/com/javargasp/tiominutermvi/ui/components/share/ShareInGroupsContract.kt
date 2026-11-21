package com.javargasp.tiominutermvi.ui.components.share

import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.base.MviIntent
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * The "Share in my groups" switch of a completion screen. [visible] only for a signed-in person in
 * at least one group, for a workout the feed queue holds; [showIntro] is the one-time explanation.
 */
data class ShareInGroupsState(
    val entryId: Long? = null,
    val visible: Boolean = false,
    val share: Boolean = true,
    val needsIntro: Boolean = false,
    val showIntro: Boolean = false
) : MviState

sealed interface ShareInGroupsIntent : MviIntent {
    /** The completed workout's entry (it may arrive after the screen opened). */
    data class OnEntry(val entryId: Long) : ShareInGroupsIntent

    /** The daily completion screen: the next (or just) recorded entry of [source]. */
    data class OnLatestOf(val source: TrainingSource) : ShareInGroupsIntent
    data class OnShareChanged(val share: Boolean) : ShareInGroupsIntent
    data object OnIntroOk : ShareInGroupsIntent
    data object OnIntroDontShare : ShareInGroupsIntent

    /** Closed without choosing: nothing is posted until the explanation is acknowledged. */
    data object OnIntroDismissed : ShareInGroupsIntent
}

/** No one-shot events: the switch only changes the queue. */
sealed interface ShareInGroupsEffect : MviEffect
