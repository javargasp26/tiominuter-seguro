package com.javargasp.tiominutermvi.ui.program.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.currentLocale
import com.javargasp.tiominutermvi.ui.program.common.durationLabel
import com.javargasp.tiominutermvi.ui.program.common.focusLabel
import com.javargasp.tiominutermvi.ui.program.common.formatPercent
import com.javargasp.tiominutermvi.ui.program.common.goalLabel
import com.javargasp.tiominutermvi.ui.program.common.levelLabel
import com.javargasp.tiominutermvi.ui.program.common.locationLabel
import org.koin.androidx.compose.koinViewModel
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext

/** "My programs": every stored program, a FAB to create one, long press to delete. */
@Composable
fun ProgramListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProgramListViewModel = koinViewModel()
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is ProgramListEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
                ProgramListEffect.NavigateToCreate -> onNavigateToCreate()
                is ProgramListEffect.NavigateToDetail -> onNavigateToDetail(effect.programId)
                ProgramListEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        // Every open and return to the foreground: lazy close of abandoned program workouts
        LifecycleResumeEffect(Unit) {
            onIntent(ProgramListIntent.OnScreenResumed)
            onPauseOrDispose { }
        }
        ProgramListContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramListContent(
    state: ProgramListState,
    onIntent: (ProgramListIntent) -> Unit
) {
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = stringResource(R.string.program_list_title),
                onBack = { onIntent(ProgramListIntent.OnBackPressed) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(ProgramListIntent.OnCreateClicked) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.program_create_content_description)
                )
            }
        }
    ) { padding ->
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isEmpty -> EmptyContent(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.programs, key = { it.id }) { program ->
                    ProgramCard(
                        program = program,
                        onClick = { onIntent(ProgramListIntent.OnProgramClicked(program.id)) },
                        onDelete = { onIntent(ProgramListIntent.OnDeleteRequested(program.id)) }
                    )
                }
            }
        }
    }

    if (state.pendingDeleteId != null) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_delete_title),
            text = stringResource(R.string.program_delete_text),
            confirmLabel = stringResource(R.string.program_delete_confirm),
            dismissLabel = stringResource(R.string.program_cancel),
            onConfirm = { onIntent(ProgramListIntent.OnConfirmDelete) },
            onDismiss = { onIntent(ProgramListIntent.OnDismissDelete) }
        )
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.program_list_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.program_list_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgramCard(
    program: ProgramSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteLabel = stringResource(R.string.program_delete_action)
    val percent = formatPercent(program.completedWorkouts, program.totalWorkouts, currentLocale())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(R.string.program_open_action),
                onLongClickLabel = deleteLabel,
                onLongClick = onDelete,
                onClick = onClick
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = goalLabel(program.profile.goal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.program_focus_value, focusLabel(program.profile.focus)),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(text = durationLabel(program.profile), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    R.string.program_card_meta,
                    locationLabel(program.profile.location),
                    levelLabel(program.profile.level)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = {
                    if (program.totalWorkouts > 0) program.completedWorkouts.toFloat() / program.totalWorkouts else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.program_progress, percent),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
