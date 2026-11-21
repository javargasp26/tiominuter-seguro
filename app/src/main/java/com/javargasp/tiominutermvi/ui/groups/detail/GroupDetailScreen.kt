package com.javargasp.tiominutermvi.ui.groups.detail

import com.javargasp.tiominutermvi.ui.groups.feed.GroupFeedTab
import com.javargasp.tiominutermvi.domain.groups.GroupInfo
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.domain.groups.RankedMember
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.auth.AccountAvatar
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.groups.common.InviteCodeCard
import com.javargasp.tiominutermvi.ui.groups.common.copyInviteCode
import com.javargasp.tiominutermvi.ui.groups.common.shareInvite
import com.javargasp.tiominutermvi.ui.groups.common.showGroupToast
import com.javargasp.tiominutermvi.ui.groups.common.sourceIcon
import com.javargasp.tiominutermvi.ui.groups.common.sourceLabel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupDetailViewModel = koinViewModel { parametersOf(groupId) }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The real-time listener runs only while the screen is visible
    LifecycleStartEffect(viewModel) {
        viewModel.handleIntent(GroupDetailIntent.OnVisible)
        onStopOrDispose { viewModel.handleIntent(GroupDetailIntent.OnHidden) }
    }

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GroupDetailEffect.CopyCode -> copyInviteCode(context, effect.code)
                is GroupDetailEffect.ShareInvite -> shareInvite(context, effect.groupName, effect.code)
                is GroupDetailEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                is GroupDetailEffect.Close -> {
                    showGroupToast(context, effect.text)
                    onNavigateBack()
                }
                GroupDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        GroupDetailContent(state, onIntent, snackbarHostState) { modifier ->
            GroupFeedTab(groupId = groupId, ownerUid = state.group?.ownerUid, snackbarHostState = snackbarHostState, modifier = modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetailContent(
    state: GroupDetailState,
    onIntent: (GroupDetailIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    feed: @Composable (Modifier) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(GroupDetailIntent.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.groups_back))
                    }
                },
                actions = { if (state.isOwner) OwnerMenu(state, onIntent) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val group = state.group
        if (state.isLoading || group == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab) {
                Tab(
                    selected = state.tab == GroupDetailState.TAB_RANKING,
                    onClick = { onIntent(GroupDetailIntent.OnTabSelected(GroupDetailState.TAB_RANKING)) },
                    text = { Text(stringResource(R.string.groups_tab_ranking)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                Tab(
                    selected = state.tab == GroupDetailState.TAB_FEED,
                    onClick = { onIntent(GroupDetailIntent.OnTabSelected(GroupDetailState.TAB_FEED)) },
                    text = { Text(stringResource(R.string.groups_tab_feed)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
            // Only the selected tab is composed: the feed's listener runs only while it is visible
            if (state.tab == GroupDetailState.TAB_FEED) {
                feed(Modifier.fillMaxSize())
            } else {
                RankingTab(state, group, onIntent)
            }
        }
    }

    state.dialog?.let { GroupDetailDialogs(it, state, onIntent) }
}

@Composable
private fun RankingTab(state: GroupDetailState, group: GroupInfo, onIntent: (GroupDetailIntent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.groups_detail_this_week), style = MaterialTheme.typography.titleMedium)
        }
        items(state.ranking, key = { it.member.uid }) { line ->
            MemberRow(
                line = line,
                isOwner = line.member.uid == group.ownerUid,
                weekDates = state.weekDates,
                canManage = state.isOwner && !line.isMe,
                onIntent = onIntent
            )
        }
        item {
            InviteCodeCard(
                code = group.inviteCode,
                onCopy = { onIntent(GroupDetailIntent.OnCopyCode) },
                onShare = { onIntent(GroupDetailIntent.OnShareCode) }
            )
        }
        item {
            OutlinedButton(
                onClick = { onIntent(GroupDetailIntent.OnLeaveClicked) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.groups_detail_leave)) }
        }
    }
}

@Composable
private fun OwnerMenu(state: GroupDetailState, onIntent: (GroupDetailIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.groups_detail_owner_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_rename)) }, onClick = {
                expanded = false
                onIntent(GroupDetailIntent.OnRenameClicked)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_regenerate)) }, onClick = {
                expanded = false
                onIntent(GroupDetailIntent.OnRegenerateClicked)
            })
            if (state.otherMembers.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_transfer)) }, onClick = {
                    expanded = false
                    onIntent(GroupDetailIntent.OnTransferClicked)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_delete)) }, onClick = {
                expanded = false
                onIntent(GroupDetailIntent.OnDeleteClicked)
            })
        }
    }
}

/** Position, avatar, name, done/goal with a bar, streak, and the Mon–Sun strip; "me" highlighted. */
@Composable
private fun MemberRow(
    line: RankedMember,
    isOwner: Boolean,
    weekDates: List<String>,
    canManage: Boolean,
    onIntent: (GroupDetailIntent) -> Unit
) {
    val member = line.member
    val name = if (line.isMe) "${member.profile.displayName} (${stringResource(R.string.groups_detail_me)})" else member.profile.displayName
    val colors = if (line.isMe) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.groups_detail_position, line.position),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
                AccountAvatar(photoUrl = member.profile.photoUrl, size = 40.dp, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOwner) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.groups_owner_label),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        if (line.isCurrentWeek) {
                            stringResource(R.string.groups_detail_progress, line.workouts, member.summary.weeklyGoal) + " · " +
                                pluralStringResource(R.plurals.groups_detail_streak, member.summary.streakWeeks, member.summary.streakWeeks)
                        } else {
                            stringResource(R.string.groups_detail_progress, 0, member.summary.weeklyGoal) + " · " +
                                stringResource(R.string.groups_detail_not_updated)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (canManage) MemberMenu(member.uid, member.profile.displayName, onIntent)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { line.compliance.toFloat() }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            WeekStrip(
                days = if (line.isCurrentWeek) member.summary.daysThisWeek else emptyMap(),
                weekDates = weekDates
            )
        }
    }
}

@Composable
private fun MemberMenu(uid: String, name: String, onIntent: (GroupDetailIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.groups_detail_member_menu, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_make_owner)) }, onClick = {
                expanded = false
                onIntent(GroupDetailIntent.OnMakeOwnerClicked(uid))
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.groups_detail_remove)) }, onClick = {
                expanded = false
                onIntent(GroupDetailIntent.OnRemoveClicked(uid))
            })
        }
    }
}

private val DAY_LETTERS = listOf(
    R.string.weekly_day_short_mon, R.string.weekly_day_short_tue, R.string.weekly_day_short_wed,
    R.string.weekly_day_short_thu, R.string.weekly_day_short_fri, R.string.weekly_day_short_sat,
    R.string.weekly_day_short_sun
)

/** Mon–Sun with a small icon per workout type; one TalkBack sentence for the whole strip. */
@Composable
private fun WeekStrip(days: Map<Int, List<TrainingSource>>, weekDates: List<String>) {
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val dayNames = weekDates.map { dayName(it, locale) }
    val sentence = (1..7).joinToString(". ") { day ->
        val dayName = dayNames.getOrNull(day - 1).orEmpty()
        val sources = days[day].orEmpty()
        if (sources.isEmpty()) {
            resources.getString(R.string.groups_detail_day_none, dayName)
        } else {
            resources.getString(
                R.string.groups_detail_day_sources,
                dayName,
                sources.joinToString(", ") { resources.getString(sourceLabel(it)) }
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = sentence }) {
        (1..7).forEach { day ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(DAY_LETTERS[day - 1]), style = MaterialTheme.typography.labelSmall)
                val sources = days[day].orEmpty()
                sources.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { source ->
                            Icon(
                                sourceIcon(source),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (sources.isEmpty()) Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

private fun dayName(date: String, locale: Locale): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val parsed = runCatching { parser.parse(date) }.getOrNull() ?: return date
    return SimpleDateFormat("EEEE", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
}

@Composable
private fun GroupDetailDialogs(dialog: GroupDetailDialog, state: GroupDetailState, onIntent: (GroupDetailIntent) -> Unit) {
    val groupName = state.group?.name.orEmpty()
    val dismiss = { onIntent(GroupDetailIntent.OnDialogDismissed) }
    val cancel: @Composable () -> Unit = {
        TextButton(onClick = dismiss, enabled = !state.isWorking) { Text(stringResource(R.string.groups_cancel)) }
    }
    fun confirm(label: Int, enabled: Boolean = true): @Composable () -> Unit = {
        TextButton(onClick = { onIntent(GroupDetailIntent.OnConfirm) }, enabled = enabled && !state.isWorking) {
            if (state.isWorking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(label))
        }
    }
    when (dialog) {
        GroupDetailDialog.Rename -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_rename_title)) },
            text = {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = { onIntent(GroupDetailIntent.OnRenameTextChanged(it)) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.groups_create_name_label)) },
                    supportingText = { Text("${state.renameText.trim().length}/${GroupLimits.NAME_MAX_LENGTH}") }
                )
            },
            confirmButton = confirm(R.string.groups_dialog_save, GroupLimits.normalizeName(state.renameText) != null),
            dismissButton = cancel
        )
        GroupDetailDialog.RegenerateCode -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_regenerate_title)) },
            text = { Text(stringResource(R.string.groups_dialog_regenerate_text)) },
            confirmButton = confirm(R.string.groups_dialog_regenerate_confirm),
            dismissButton = cancel
        )
        is GroupDetailDialog.RemoveMember -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_remove_title, dialog.member.profile.displayName)) },
            text = { Text(stringResource(R.string.groups_dialog_remove_text)) },
            confirmButton = confirm(R.string.groups_dialog_remove_confirm),
            dismissButton = cancel
        )
        is GroupDetailDialog.PickNewOwner -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_transfer_title)) },
            text = {
                Column {
                    if (dialog.beforeLeaving) {
                        Text(stringResource(R.string.groups_dialog_transfer_before_leaving))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    state.otherMembers.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = false,
                                    role = Role.RadioButton,
                                    onClick = { onIntent(GroupDetailIntent.OnNewOwnerPicked(member.uid)) }
                                )
                        ) {
                            RadioButton(selected = false, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(member.profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = cancel
        )
        is GroupDetailDialog.ConfirmNewOwner -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_transfer_confirm_title, dialog.member.profile.displayName)) },
            text = {
                Text(
                    stringResource(
                        if (dialog.andLeave) R.string.groups_dialog_transfer_and_leave_text else R.string.groups_dialog_transfer_confirm_text
                    )
                )
            },
            confirmButton = confirm(
                if (dialog.andLeave) R.string.groups_dialog_transfer_and_leave_confirm else R.string.groups_dialog_transfer_confirm
            ),
            dismissButton = cancel
        )
        GroupDetailDialog.DeleteExplain -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_delete_title, groupName)) },
            text = { Text(stringResource(R.string.groups_dialog_delete_text)) },
            confirmButton = confirm(R.string.groups_dialog_delete_next),
            dismissButton = cancel
        )
        GroupDetailDialog.DeleteConfirm -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_delete_confirm_title)) },
            text = { Text(stringResource(R.string.groups_dialog_delete_confirm_text, groupName)) },
            confirmButton = confirm(R.string.groups_dialog_delete_confirm),
            dismissButton = cancel
        )
        GroupDetailDialog.Leave -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_leave_title, groupName)) },
            text = { Text(stringResource(R.string.groups_dialog_leave_text)) },
            confirmButton = confirm(R.string.groups_dialog_leave_confirm),
            dismissButton = cancel
        )
        GroupDetailDialog.LeaveAlone -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.groups_dialog_leave_title, groupName)) },
            text = { Text(stringResource(R.string.groups_dialog_leave_alone_text)) },
            confirmButton = confirm(R.string.groups_dialog_leave_confirm),
            dismissButton = cancel
        )
    }
}
