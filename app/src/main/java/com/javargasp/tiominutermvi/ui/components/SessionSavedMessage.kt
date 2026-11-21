package com.javargasp.tiominutermvi.ui.components

import android.content.Context
import android.widget.Toast
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.traininglog.durationMinutes

/**
 * A session duration as "1 h 05 min" or "40 min": whole minutes rounded down, at least 1, the
 * same rule as the training history ([durationMinutes]), so both show the same value. The
 * labels come from strings; the arithmetic is unit-tested.
 */
fun formatSessionDuration(
    seconds: Int,
    hoursAndMinutes: (hours: Int, minutes: Int) -> String,
    minutesOnly: (minutes: Int) -> String
): String {
    val totalMinutes = durationMinutes(seconds)
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) hoursAndMinutes(hours, minutes) else minutesOnly(minutes)
}

/**
 * "Your unfinished workout was saved (1 h 05 min)." A short Toast (non-blocking, still visible
 * after navigating), shown when a gym, daily or program screen opens after the app closed an
 * inactive session.
 */
fun showSessionSaved(context: Context, durationSeconds: Int) {
    val duration = formatSessionDuration(
        durationSeconds,
        hoursAndMinutes = { hours, minutes -> context.getString(R.string.gym_session_duration_hours_minutes, hours, minutes) },
        minutesOnly = { minutes -> context.getString(R.string.gym_session_duration_minutes, minutes) }
    )
    Toast.makeText(context, context.getString(R.string.gym_session_saved, duration), Toast.LENGTH_LONG).show()
}
private const val MINUTES_PER_HOUR = 60
