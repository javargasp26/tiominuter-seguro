package com.javargasp.tiominutermvi.ui.components.share

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutSharing
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The share switch of one completed workout: ON by default (the queue already holds it), turned
 * off "for this workout only". The first time a workout is about to be shared, the explanation
 * shows once (OK, or "Don't share this one"); until it is acknowledged nothing is posted.
 */
class ShareInGroupsViewModel(
    private val sharing: WorkoutSharing,
    private val clock: WallClock
) : BaseViewModel<ShareInGroupsIntent, ShareInGroupsState, ShareInGroupsEffect>(ShareInGroupsState()) {

    private val openedAt = clock.nowMillis()
    private var latestJob: Job? = null

    override suspend fun reduce(intent: ShareInGroupsIntent, currentState: ShareInGroupsState) {
        when (intent) {
            is ShareInGroupsIntent.OnEntry -> load(intent.entryId)
            is ShareInGroupsIntent.OnLatestOf -> waitForLatest(intent.source)
            is ShareInGroupsIntent.OnShareChanged -> {
                val id = currentState.entryId ?: return
                if (!currentState.visible) return
                sharing.setShare(id, intent.share)
                setState(state.value.copy(share = intent.share, showIntro = intent.share && state.value.needsIntro))
            }
            ShareInGroupsIntent.OnIntroOk -> {
                sharing.acknowledgeIntro()
                setState(state.value.copy(needsIntro = false, showIntro = false))
            }
            ShareInGroupsIntent.OnIntroDontShare -> {
                currentState.entryId?.let { sharing.setShare(it, false) }
                sharing.acknowledgeIntro()
                setState(state.value.copy(share = false, needsIntro = false, showIntro = false))
            }
            ShareInGroupsIntent.OnIntroDismissed -> setState(currentState.copy(showIntro = false))
        }
    }

    private suspend fun load(entryId: Long) {
        if (state.value.entryId == entryId) return
        setState(ShareInGroupsState(entryId = entryId))
        val options = sharing.options(entryId)
        if (state.value.entryId != entryId) return
        setState(
            if (options == null) {
                ShareInGroupsState(entryId = entryId)
            } else {
                ShareInGroupsState(
                    entryId = entryId,
                    visible = true,
                    share = options.share,
                    needsIntro = options.needsIntro,
                    showIntro = options.share && options.needsIntro
                )
            }
        )
    }

    /** The entry recorded for this completion: right before the screen opened, or right after. */
    private fun waitForLatest(source: TrainingSource) {
        if (latestJob != null) return
        latestJob = viewModelScope.launch {
            val recorded = sharing.latestRecorded(source)
                .filterNotNull()
                .first { it.recordedAt >= openedAt - RECENT_MILLIS }
            load(recorded.id)
        }
    }

    private companion object {
        const val RECENT_MILLIS = 2 * 60 * 1000L
    }
}
