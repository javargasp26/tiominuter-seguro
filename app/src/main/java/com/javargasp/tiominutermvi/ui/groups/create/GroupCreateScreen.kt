package com.javargasp.tiominutermvi.ui.groups.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.GroupLimits
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.groups.common.InviteCodeCard
import com.javargasp.tiominutermvi.ui.groups.common.copyInviteCode
import com.javargasp.tiominutermvi.ui.groups.common.shareInvite
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun GroupCreateScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGroup: (String) -> Unit,
    viewModel: GroupCreateViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GroupCreateEffect.CopyCode -> copyInviteCode(context, effect.code)
                is GroupCreateEffect.ShareInvite -> shareInvite(context, effect.groupName, effect.code)
                is GroupCreateEffect.NavigateToGroup -> onNavigateToGroup(effect.groupId)
                GroupCreateEffect.NavigateBack -> onNavigateBack()
                is GroupCreateEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
            }
        }
    ) { state, onIntent ->
        GroupCreateContent(state, onIntent, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupCreateContent(
    state: GroupCreateState,
    onIntent: (GroupCreateIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_create_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(GroupCreateIntent.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.groups_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            val created = state.created
            if (created == null) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onIntent(GroupCreateIntent.OnNameChanged(it)) },
                    label = { Text(stringResource(R.string.groups_create_name_label)) },
                    singleLine = true,
                    isError = state.showNameError && !state.isNameValid,
                    supportingText = {
                        if (state.showNameError && !state.isNameValid) {
                            Text(stringResource(R.string.groups_create_name_error))
                        } else {
                            Text("${state.name.trim().length}/${GroupLimits.NAME_MAX_LENGTH}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onIntent(GroupCreateIntent.OnCreateClicked) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onIntent(GroupCreateIntent.OnCreateClicked) },
                    enabled = !state.isCreating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.groups_create_action))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.groups_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(stringResource(R.string.groups_created_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.groups_created_text, created.name))
                Spacer(modifier = Modifier.height(16.dp))
                InviteCodeCard(
                    code = created.code,
                    onCopy = { onIntent(GroupCreateIntent.OnCopyClicked) },
                    onShare = { onIntent(GroupCreateIntent.OnShareClicked) }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onIntent(GroupCreateIntent.OnOpenGroupClicked) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.groups_open_group)) }
            }
        }
    }
}
