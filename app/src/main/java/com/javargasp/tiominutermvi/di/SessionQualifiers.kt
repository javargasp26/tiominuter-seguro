package com.javargasp.tiominutermvi.di

/** Koin qualifiers of the session trackers (see `domain/session`). */
object SessionQualifiers {
    /** The daily workout's [com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker]. */
    const val DAILY = "dailySessions"

    /** The program cardio workouts' ActiveSessionTracker (programModule). */
    const val PROGRAM_CARDIO = "programCardioSessions"

    /** Every program session (strength and cardio) as an InactiveSessionCloser (programModule). */
    const val PROGRAM = "programSessions"
}
