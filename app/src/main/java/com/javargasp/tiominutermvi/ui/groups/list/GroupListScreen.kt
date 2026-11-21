package com.javargasp.tiominutermvi.ui.groups.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.MyGroup
import com.javargasp.tiominutermvi.ui.base.MviComposable
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun GroupListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToJoin: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToWeeklyGoal: () -> Unit,
    viewModel: GroupListViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Reloads every time the list is shown again (back from a group, create, join or sign-in)
    LifecycleStartEffect(viewModel) {
        viewModel.handleIntent(GroupListIntent.OnStart)
        onStopOrDispose { }
    }

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                GroupListEffect.NavigateBack -> onNavigateBack()
                GroupListEffect.NavigateToCreate -> onNavigateToCreate()
                GroupListEffect.NavigateToJoin -> onNavigateToJoin()
                is GroupListEffect.NavigateToDetail -> onNavigateToDetail(effect.groupId)
                GroupListEffect.NavigateToAccount -> onNavigateToAccount()
                GroupListEffect.NavigateToWeeklyGoal -> onNavigateToWeeklyGoal()
                is GroupListEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(effect.text.resolve(context))
                }
            }
        }
    ) { state, onIntent ->
        GroupListContent(state = state, onIntent = onIntent, snackbarHostState = snackbarHostState)
    }
}

/** Stateless list layout: guest explanation, loading, error, empty state or my groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupListContent(
    state: GroupListState,
    onIntent: (GroupListIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(GroupListIntent.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.groups_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isGuest == true -> GuestExplanation(onSignIn = { onIntent(GroupListIntent.OnSignInClicked) }, modifier = modifier)
            state.isGuest == null || (state.isLoading && state.groups.isEmpty()) -> Column(
                modifier = modifier,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            state.loadError -> Column(
                modifier = modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.groups_load_error), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onIntent(GroupListIntent.OnRetry) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.groups_retry))
                }
            }
            else -> GroupList(state = state, onIntent = onIntent, modifier = modifier)
        }
    }

    state.goalRequiredFor?.let {
        AlertDialog(
            onDismissRequest = { onIntent(GroupListIntent.OnGoalDialogDismissed) },
            title = { Text(stringResource(R.string.groups_goal_required_title)) },
            text = { Text(stringResource(R.string.groups_goal_required_text)) },
            confirmButton = {
                TextButton(onClick = { onIntent(GroupListIntent.OnSetGoalClicked) }) {
                    Text(stringResource(R.string.groups_goal_required_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(GroupListIntent.OnGoalDialogDismissed) }) {
                    Text(stringResource(R.string.groups_cancel))
                }
            }
        )
    }
}

@Composable
private fun GuestExplanation(onSignIn: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(R.drawable.ic_groups), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.groups_guest_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.groups_guest_text), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.groups_guest_sign_in))
        }
    }
}

@Composable
private fun GroupList(state: GroupListState, onIntent: (GroupListIntent) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.groups.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.groups_empty_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.groups_empty_text), textAlign = TextAlign.Center)
                }
            }
        }
        items(state.groups, key = { it.group.id }) { group ->
            GroupCard(group = group, onClick = { onIntent(GroupListIntent.OnGroupClicked(group.group.id)) })
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onIntent(GroupListIntent.OnCreateClicked) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.groups_create_button), textAlign = TextAlign.Center) }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { onIntent(GroupListIntent.OnJoinClicked) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.groups_join_button), textAlign = TextAlign.Center) }
            }
        }
        item {
            Text(
                stringResource(R.string.groups_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupCard(group: MyGroup, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (group.isOwner) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.groups_owner_label),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.groups_member_count, group.group.memberCount, group.group.memberCount),
                style = MaterialTheme.typography.bodyMedium
            )
            group.myPosition?.let {
                Text(
                    stringResource(R.string.groups_my_position, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
