package com.javargasp.tiominutermvi.ui.groups.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource

/** A friendly message for each [GroupError]. */
@StringRes
fun GroupError.messageRes(): Int = when (this) {
    GroupError.NETWORK -> R.string.groups_message_no_network
    GroupError.NOT_SIGNED_IN -> R.string.groups_message_sign_in
    GroupError.GOAL_REQUIRED -> R.string.groups_message_goal_required
    GroupError.INVALID_CODE -> R.string.groups_message_invalid_code
    GroupError.GROUP_FULL -> R.string.groups_message_group_full
    GroupError.ALREADY_MEMBER -> R.string.groups_message_already_member
    GroupError.GROUP_LIMIT -> R.string.groups_message_group_limit
    GroupError.INVALID_NAME -> R.string.groups_message_invalid_name
    GroupError.NOT_FOUND -> R.string.groups_message_not_found
    GroupError.PERMISSION_DENIED -> R.string.groups_message_not_allowed
    GroupError.FAILED -> R.string.groups_message_failed
}

/** A snackbar or toast text: a resource with optional arguments (resolved by the screen). */
data class GroupText(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}

fun GroupError.toText(): GroupText = GroupText(messageRes())

/** The icon of a workout type in the week strips (decorative; the row has its own description). */
fun sourceIcon(source: TrainingSource): ImageVector = when (source) {
    TrainingSource.DAILY -> Icons.Filled.DateRange
    TrainingSource.GYM -> Icons.Filled.Build
    TrainingSource.PROGRAM -> Icons.AutoMirrored.Filled.List
    TrainingSource.ACTIVITY -> Icons.Filled.Create
}

@StringRes
fun sourceLabel(source: TrainingSource): Int = when (source) {
    TrainingSource.DAILY -> R.string.groups_source_daily
    TrainingSource.GYM -> R.string.groups_source_gym
    TrainingSource.PROGRAM -> R.string.groups_source_program
    TrainingSource.ACTIVITY -> R.string.groups_source_activity
}

/** Copies the code and confirms with a short toast. */
fun copyInviteCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.groups_code_label), code))
    Toast.makeText(context, R.string.groups_code_copied, Toast.LENGTH_SHORT).show()
}

/** The Android share sheet with "Join my group <name> on Tio Minuter with code <CODE>". */
fun shareInvite(context: Context, groupName: String, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.groups_share_text, groupName, code, context.getString(R.string.app_name)))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.groups_share_chooser)))
}

fun showGroupToast(context: Context, text: GroupText) {
    Toast.makeText(context, text.resolve(context), Toast.LENGTH_LONG).show()
}

/** The invite code, large and spaced, with Copy and Share. */
@Composable
fun InviteCodeCard(code: String, onCopy: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.groups_code_label)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = code,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                // Read character by character
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = "$label: ${code.toList().joinToString(" ")}" }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.groups_copy_code))
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.groups_share_code))
                }
            }
        }
    }
}
