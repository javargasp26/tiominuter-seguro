package com.javargasp.tiominutermvi.ui.components.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.base.MviComposable
import org.koin.androidx.compose.koinViewModel

/**
 * "Share in my groups" for the workout [entryId] (null while it is being recorded). Shows nothing
 * for a guest, without groups, or for a workout that isn't shared (not counted).
 */
@Composable
fun ShareInGroupsSection(entryId: Long?, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return
    val viewModel: ShareInGroupsViewModel = koinViewModel(key = VIEW_MODEL_KEY)
    LaunchedEffect(entryId) {
        entryId?.let { viewModel.handleIntent(ShareInGroupsIntent.OnEntry(it)) }
    }
    MviComposable(viewModel = viewModel, onEffect = {}) { state, onIntent ->
        ShareInGroupsContent(state, onIntent, modifier)
    }
}

/** The same for a screen that doesn't know its entry: the one of [source] recorded for this completion. */
@Composable
fun LatestShareInGroupsSection(source: TrainingSource, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return
    val viewModel: ShareInGroupsViewModel = koinViewModel(key = VIEW_MODEL_KEY)
    LaunchedEffect(source) { viewModel.handleIntent(ShareInGroupsIntent.OnLatestOf(source)) }
    MviComposable(viewModel = viewModel, onEffect = {}) { state, onIntent ->
        ShareInGroupsContent(state, onIntent, modifier)
    }
}

@Composable
internal fun ShareInGroupsContent(
    state: ShareInGroupsState,
    onIntent: (ShareInGroupsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return
    ShareSwitchRow(
        checked = state.share,
        onCheckedChange = { onIntent(ShareInGroupsIntent.OnShareChanged(it)) },
        modifier = modifier
    )
    if (state.showIntro) {
        ShareIntroDialog(
            onOk = { onIntent(ShareInGroupsIntent.OnIntroOk) },
            onDontShare = { onIntent(ShareInGroupsIntent.OnIntroDontShare) },
            onDismiss = { onIntent(ShareInGroupsIntent.OnIntroDismissed) }
        )
    }
}

/** The switch row (also used by Log activity): 48 dp, one toggleable element for TalkBack. */
@Composable
fun ShareSwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.share_in_groups_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (checked) R.string.share_in_groups_on else R.string.share_in_groups_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** What group members will see, once: "OK" or "Don't share this one". */
@Composable
fun ShareIntroDialog(onOk: () -> Unit, onDontShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_intro_title)) },
        text = { Text(stringResource(R.string.share_intro_text)) },
        confirmButton = { TextButton(onClick = onOk) { Text(stringResource(R.string.share_intro_ok)) } },
        dismissButton = { TextButton(onClick = onDontShare) { Text(stringResource(R.string.share_intro_dont_share)) } }
    )
}

private const val VIEW_MODEL_KEY = "shareInGroups"
