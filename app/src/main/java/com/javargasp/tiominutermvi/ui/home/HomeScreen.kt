package com.javargasp.tiominutermvi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.ui.auth.AccountAvatar
import com.javargasp.tiominutermvi.ui.base.CollectEffects
import com.javargasp.tiominutermvi.ui.components.text
import org.koin.androidx.compose.koinViewModel

/**
 * Home screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDailyRoutineSelection: () -> Unit,
    onNavigateToGymRoutineSelection: () -> Unit,
    onNavigateToProgramList: () -> Unit,
    onNavigateToTrainingHistory: () -> Unit,
    onNavigateToWeeklyGoal: () -> Unit,
    onNavigateToLogActivity: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToGroupDetail: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Handle effects
    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToDailyRoutineSelection -> {
                onNavigateToDailyRoutineSelection()
            }
            is HomeEffect.NavigateToGymRoutineSelection -> {
                onNavigateToGymRoutineSelection()
            }
            is HomeEffect.NavigateToProgramList -> {
                onNavigateToProgramList()
            }
            is HomeEffect.NavigateToTrainingHistory -> {
                onNavigateToTrainingHistory()
            }
            is HomeEffect.NavigateToWeeklyGoal -> {
                onNavigateToWeeklyGoal()
            }
            is HomeEffect.NavigateToLogActivity -> {
                onNavigateToLogActivity()
            }
            is HomeEffect.NavigateToAccount -> {
                onNavigateToAccount()
            }
            is HomeEffect.NavigateToGroups -> {
                onNavigateToGroups()
            }
            is HomeEffect.NavigateToGroupDetail -> {
                onNavigateToGroupDetail(effect.groupId)
            }
        }
    }
    
    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.handleIntent(HomeIntent.OnStart)
    }
    
    HomeContent(state = state, onIntent = viewModel::handleIntent)
}

/**
 * The Home layout: a top bar with the Groups icon and the account icon (the Google photo when
 * signed in) → the guest banner when shown → the "already trained today" banner when shown → mascot and title → the compact weekly goal card (with "See history",
 * the way to the training history) → "Day X available" → Start, Gym routines and Programs.
 * The column scrolls when the content is taller than the screen and stays vertically
 * centered otherwise. Stateless, so previews render it from a [HomeState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    // Groups sits next to the account: quiet, always in the same place
                    IconButton(onClick = { onIntent(HomeIntent.OnGroupsClicked) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_groups),
                            contentDescription = stringResource(R.string.groups_home_button)
                        )
                    }
                    IconButton(onClick = { onIntent(HomeIntent.OnAccountClicked) }) {
                        AccountAvatar(
                            photoUrl = (state.session as? AuthSession.SignedIn)?.photoUrl,
                            size = 32.dp,
                            contentDescription = stringResource(R.string.home_account_button)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (state.showGuestBanner) {
                    GuestProfileBanner(
                        onSignIn = { onIntent(HomeIntent.OnGuestBannerSignInClicked) },
                        onDismiss = { onIntent(HomeIntent.OnGuestBannerDismissed) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                state.trainedTodayBanner?.let { banner ->
                    TrainedTodayBannerCard(
                        banner = banner,
                        onOpen = { onIntent(HomeIntent.OnTrainedTodayBannerClicked) },
                        onDismiss = { onIntent(HomeIntent.OnTrainedTodayBannerDismissed) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                HomeHeader()
                Spacer(modifier = Modifier.height(24.dp))

                // This week's goal progress; "See history" and "Log activity" live in the card
                state.week?.let { week ->
                    WeeklyGoalCompactCard(
                        week = week,
                        message = state.weeklyMessage,
                        onClick = { onIntent(HomeIntent.OnWeeklyGoalClicked) },
                        onSeeHistory = { onIntent(HomeIntent.OnHistoryClicked) },
                        onLogActivity = { onIntent(HomeIntent.OnLogActivityClicked) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                HomeDayAvailable(state)

                HomeButtons(state = state, onIntent = onIntent)
            }
        }
    }
}

/** Mascot, title and tagline. */
@Composable
private fun HomeHeader() {
    // Uncle image
    Image(
        painter = painterResource(id = R.drawable.uncle),
        contentDescription = "Uncle",
        modifier = Modifier
            .size(88.dp)
            .padding(bottom = 8.dp)
    )

    // Title
    Text(
        text = stringResource(R.string.home_screen_title),
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Tagline
    Text(
        text = stringResource(R.string.home_screen_tagline),
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

/** "Day X available" once loaded (the day's name through WorkoutNameResolver). */
@Composable
private fun HomeDayAvailable(state: HomeState) {
    val dayName = state.currentDayName
    if (!state.isLoading && dayName != null) {
        Text(
            text = stringResource(R.string.home_day_available, dayName.text()),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Start (daily), Gym routines and Programs. */
@Composable
private fun HomeButtons(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit
) {
    // Start button
    Button(
        onClick = { onIntent(HomeIntent.OnStartRoutineClicked) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = stringResource(R.string.start_today_routine),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Gym routines button (secondary)
    OutlinedButton(
        onClick = { onIntent(HomeIntent.OnGymRoutinesClicked) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = stringResource(R.string.gym_routines_button),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Programs button (secondary)
    OutlinedButton(
        onClick = { onIntent(HomeIntent.OnProgramsClicked) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = stringResource(R.string.program_home_button),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
