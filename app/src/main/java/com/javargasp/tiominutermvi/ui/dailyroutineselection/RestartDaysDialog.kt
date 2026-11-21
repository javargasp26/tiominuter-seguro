package com.javargasp.tiominutermvi.ui.dailyroutineselection

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.components.text

/**
 * The confirmation of "Restart days" (the selection screen and the completion of the last day):
 * every day locks again from Day 1; the history, the weekly goal and the groups don't change.
 */
@Composable
fun RestartDaysDialog(firstDay: WorkoutName.Daily, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_restart_title)) },
        text = { Text(stringResource(R.string.daily_restart_message, firstDay.text())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.daily_restart_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
