package com.javargasp.tiominutermvi.ui.groups.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.MviComposable
import org.koin.androidx.compose.koinViewModel

@Composable
fun GroupJoinScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGroup: (String) -> Unit,
    viewModel: GroupJoinViewModel = koinViewModel()
) {
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GroupJoinEffect.NavigateToGroup -> onNavigateToGroup(effect.groupId)
                GroupJoinEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        GroupJoinContent(state, onIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupJoinContent(state: GroupJoinState, onIntent: (GroupJoinIntent) -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_join_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(GroupJoinIntent.OnBackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.groups_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            OutlinedTextField(
                value = state.code,
                onValueChange = { onIntent(GroupJoinIntent.OnCodeChanged(it)) },
                label = { Text(stringResource(R.string.groups_join_code_label)) },
                singleLine = true,
                isError = state.error != null,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                supportingText = { Text(state.error?.resolve(context) ?: stringResource(R.string.groups_join_code_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when {
                    state.isLookingUp -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.groups_join_looking))
                    }
                    state.preview != null -> Text(
                        stringResource(R.string.groups_join_preview, state.preview.groupName),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onIntent(GroupJoinIntent.OnJoinClicked) },
                enabled = state.canJoin,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (state.isJoining) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.groups_join_action))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.groups_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
