package com.javargasp.tiominutermvi.domain.daily

/**
 * What a sync does with the daily progress (`users/{uid}/dailyProgress/state`, one document,
 * never tombstoned). Unlike the other synced documents it is merged, not "last write wins": the
 * more advanced state is kept ([DailyUnlockRules.merge]: a higher cycle, then a further day), so
 * a phone and the web never lock each other out of a day. No device clock is compared.
 */
object DailyProgressSync {

    sealed interface Outcome {
        /** The server is ahead: the local row takes its state (nothing to upload). */
        data class ApplyRemote(val progress: DailyProgress) : Outcome

        /** The device is ahead, or the server has no document yet: upload [progress]. */
        data class PushLocal(val progress: DailyProgress) : Outcome

        /** Both say the same: nothing to write either way. */
        data object InSync : Outcome
    }

    /**
     * [remote] null: the account has no document yet (its first sync from a version with the
     * unlock). The device's state is uploaded, and in cycle 0 it is first raised to the [history]
     * ([DailyUnlockRules.fromHistory]): a device whose progress was created before the account's
     * entries were downloaded (an account switch, a fresh install) still gets the days they earned.
     */
    fun reconcile(local: DailyProgress, remote: DailyProgress?, history: DailyProgress): Outcome {
        if (remote == null) {
            return Outcome.PushLocal(if (local.cycle == 0) DailyUnlockRules.merge(local, history) else local)
        }
        val order = DailyUnlockRules.compare(remote, local)
        return when {
            order > 0 -> Outcome.ApplyRemote(remote)
            order < 0 -> Outcome.PushLocal(local)
            else -> Outcome.InSync
        }
    }
}
