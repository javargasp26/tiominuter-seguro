package com.javargasp.tiominutermvi.ui.home

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.GuestBanner
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.goal.ObserveCurrentWeek
import com.javargasp.tiominutermvi.domain.groups.GroupsHome
import com.javargasp.tiominutermvi.domain.session.InactiveSessionCloser
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen. Observes the current week's goal progress; every
 * [HomeIntent.OnStart] (sent each time Home is shown) restarts that observation, so a
 * new day or week is picked up when the person comes back to Home. The same reload
 * re-evaluates the guest banner (it reappears 7 days after a dismissal) and reads my groups'
 * members once for the "already trained today" banner ([GroupsHome]: cached, no listeners).
 * "Day X available" is the next unlocked daily routine ([DailyUnlockRules.nextAvailableDay]),
 * observed, so a finished day or a pulled progress shows at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val workoutRepository: WorkoutRepository,
    private val observeCurrentWeek: ObserveCurrentWeek,
    authRepository: AuthRepository,
    private val guestBanner: GuestBanner,
    private val sessionClosers: List<InactiveSessionCloser>,
    dailyProgress: DailyProgressRepository,
    private val groupsHome: GroupsHome = GroupsHome.None
) : BaseViewModel<HomeIntent, HomeState, HomeEffect>(
    initialState = HomeState()
) {

    private val weekReloads = MutableStateFlow(0)

    /** The daily routines (null until read) and the progress. */
    private var dailyRoutines: List<Routine>? = null
    private var progress: DailyProgress? = null

    init {
        weekReloads
            .flatMapLatest { observeCurrentWeek() }
            .onEach { week -> setState(state.value.copy(week = week)) }
            .catch { e -> println("Home: weekly progress unavailable: ${e.message}") }
            .launchIn(viewModelScope)

        weekReloads
            .flatMapLatest { guestBanner.visible() }
            .onEach { visible -> setState(state.value.copy(showGuestBanner = visible)) }
            .launchIn(viewModelScope)

        authRepository.session
            .onEach { session -> setState(state.value.copy(session = session)) }
            .launchIn(viewModelScope)

        dailyProgress.observeProgress()
            .onEach { progress = it; publishCurrentDay() }
            .catch { e -> println("Home: daily progress unavailable: ${e.message}") }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: HomeIntent, currentState: HomeState) {
        when (intent) {
            is HomeIntent.OnStart -> {
                // Abandoned sessions (gym, daily, program) become entries before the week is read
                // again (app start and every return to Home); their screens announce them
                sessionClosers.forEach { it.closeInactive() }
                // Latest state, not the snapshot: the week and the day may arrive meanwhile
                setState(state.value.copy(isLoading = true))
                loadCurrentDay()
                setState(state.value.copy(isLoading = false))
                weekReloads.value++
                loadGroups()
            }
            
            is HomeIntent.OnStartRoutineClicked -> {
                sendEffect(HomeEffect.NavigateToDailyRoutineSelection)
            }

            is HomeIntent.OnGymRoutinesClicked -> {
                sendEffect(HomeEffect.NavigateToGymRoutineSelection)
            }

            is HomeIntent.OnProgramsClicked -> {
                sendEffect(HomeEffect.NavigateToProgramList)
            }

            is HomeIntent.OnHistoryClicked -> {
                sendEffect(HomeEffect.NavigateToTrainingHistory)
            }

            is HomeIntent.OnWeeklyGoalClicked -> {
                sendEffect(HomeEffect.NavigateToWeeklyGoal)
            }

            is HomeIntent.OnLogActivityClicked -> {
                sendEffect(HomeEffect.NavigateToLogActivity)
            }

            is HomeIntent.OnAccountClicked, is HomeIntent.OnGuestBannerSignInClicked -> {
                sendEffect(HomeEffect.NavigateToAccount)
            }

            is HomeIntent.OnGuestBannerDismissed -> {
                guestBanner.dismiss()
            }

            is HomeIntent.OnGroupsClicked -> {
                sendEffect(HomeEffect.NavigateToGroups)
            }

            is HomeIntent.OnTrainedTodayBannerClicked -> {
                val groupIds = state.value.trainedTodayBanner?.groupIds.orEmpty()
                sendEffect(groupIds.singleOrNull()?.let { HomeEffect.NavigateToGroupDetail(it) } ?: HomeEffect.NavigateToGroups)
            }

            is HomeIntent.OnTrainedTodayBannerDismissed -> {
                val today = state.value.week?.today ?: return
                groupsHome.dismissTrainedToday(today)
                setState(state.value.copy(trainedTodayDismissedOn = today))
            }
        }
    }
    
    /** In the background: Home never waits for the network. */
    private fun loadGroups() {
        viewModelScope.launch {
            val groups = groupsHome.onHomeShown()
            setState(state.value.copy(groups = groups, trainedTodayDismissedOn = groupsHome.trainedTodayDismissedOn()))
        }
    }

    private suspend fun loadCurrentDay() {
        viewModelScope.launch {
            try {
                dailyRoutines = workoutRepository.getRoutines()
                publishCurrentDay()
            } catch (e: Exception) {
                // No catalog to cap the day with: Day 1, as before the unlock
                setState(state.value.copy(currentDay = 1, currentDayName = WorkoutName.Daily(1)))
            }
        }
    }

    /** The next unlocked day, once both the routines and the progress are known; none (0) without routines. */
    private fun publishCurrentDay() {
        val routines = dailyRoutines ?: return
        val current = progress ?: return
        val day = DailyUnlockRules.nextAvailableDay(current, routines.maxOfOrNull { it.routineId })
        val routine = routines.firstOrNull { it.routineId == day }
        setState(
            state.value.copy(
                currentDay = day ?: 0,
                currentDayName = routine?.let(WorkoutNameResolver::dailyName) ?: day?.let { WorkoutName.Daily(it) }
            )
        )
    }
}