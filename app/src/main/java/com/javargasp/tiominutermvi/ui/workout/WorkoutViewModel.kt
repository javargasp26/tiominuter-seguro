package com.javargasp.tiominutermvi.ui.workout

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.daily.DailyProgressRepository
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import com.javargasp.tiominutermvi.domain.session.ActiveTimeMeter
import com.javargasp.tiominutermvi.domain.session.ActiveWorkoutSession
import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.dailyRefId
import com.javargasp.tiominutermvi.domain.traininglog.dailyWorkoutTitle
import com.javargasp.tiominutermvi.domain.traininglog.recordSafely
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * ViewModel for the Workout screen.
 * Manages workout execution and exercise progression. When the workout finishes
 * (the single completion navigation) it records a DAILY entry in the training history with
 * its **active time**, measured by [timeSource]: while a timer runs (Get Ready, a TIME exercise,
 * a rest), in the foreground or the background, as the timers keep running there; and while a
 * REPS exercise is on screen (no timer: the person does the reps, then taps "Done"). A TIME
 * exercise waiting for "Get Ready" is not counted. The active time is kept in a persisted session
 * ([dailySessions]) from the first action, so a workout left unfinished (or a killed process) is
 * recorded once it has been inactive for 2 hours. Finishing also unlocks the next day
 * ([DailyProgressRepository.onDayFinished]); a session closed for inactivity never does.
 */
class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val routineId: Int,
    private val trainingLogRecorder: TrainingLogRecorder,
    timeSource: MonotonicTimeSource,
    dailySessions: ActiveSessionTracker,
    private val dailyProgress: DailyProgressRepository
) : BaseViewModel<WorkoutIntent, WorkoutState, WorkoutEffect>(
    initialState = WorkoutState()
) {
    
    private var timerJob: Job? = null
    private var readyCountdownJob: Job? = null
    private val session = ActiveWorkoutSession(
        viewModelScope, dailySessions, ActiveTimeMeter(timeSource), dailyRefId(routineId)
    )

    init {
        state.map { it.activity }
            .distinctUntilChanged()
            .onEach { (timerRunning, repsOnScreen) ->
                session.setTimerRunning(timerRunning)
                session.setActiveOnScreen(repsOnScreen)
            }
            .launchIn(viewModelScope)
    }
    
    override fun onCleared() {
        cancelTimer()
        cancelReadyCountdown()
        session.close()
    }

    override suspend fun reduce(intent: WorkoutIntent, currentState: WorkoutState) {
        if (intent.isUserAction) onUserAction()
        when (intent) {
            is WorkoutIntent.OnLoad -> {
                loadWorkout(currentState)
            }
            is WorkoutIntent.OnNextExercise -> {
                moveToNextExercise(currentState)
            }
            is WorkoutIntent.OnCompleteReps -> {
                completeRepsExercise(currentState)
            }
            is WorkoutIntent.OnSkipExercise -> {
                moveToNextExercise(currentState)
            }
            is WorkoutIntent.OnFinishWorkout -> {
                finishWorkout(currentState)
            }
            is WorkoutIntent.OnBackPressed -> {
                cancelTimer()
                cancelReadyCountdown()
                // Left unfinished: the timers stop and the screen is gone, so nothing counts any
                // more; the session is recorded after 2 hours of inactivity
                session.setVisible(false)
                sendEffect(WorkoutEffect.NavigateBack)
            }
            is WorkoutIntent.OnScreenVisible -> session.setVisible(true)
            is WorkoutIntent.OnScreenHidden -> session.setVisible(false)
            is WorkoutIntent.OnTimerFinished -> {
                // Check if this was a TIME exercise completion
                if (currentState.currentRoutineExercise?.type == ExerciseType.TIME) {
                    sendEffect(WorkoutEffect.PlayCompletionSound)
                    sendEffect(WorkoutEffect.TriggerHapticFeedback)
                }
                moveToNextExercise(currentState)
            }
            is WorkoutIntent.OnStartTimerWithReady -> {
                startReadyCountdown(currentState)
            }
        }
    }

    /** The first action stores the session (an older unfinished one is saved and announced). */
    private suspend fun onUserAction() {
        if (!session.isStarted) {
            session.start().forEach { sendEffect(WorkoutEffect.ShowSessionSaved(it.durationSeconds)) }
        }
        session.onUserAction()
    }

    private suspend fun loadWorkout(currentState: WorkoutState) {
        // OnLoad is re-sent after a rotation; a running rest keeps its single timer
        if (currentState.currentRoutineExercise?.isRest == true && timerJob?.isActive == true) {
            return
        }

        setState(currentState.copy(isLoading = true, error = null))
        
        try {
            val routine = repository.getRoutine(routineId)
            if (routine != null) {
                loadCurrentExercise(routine, currentState)
            } else {
                setState(
                    currentState.copy(
                        isLoading = false,
                        error = "Routine not found"
                    )
                )
            }
        } catch (e: Exception) {
            setState(
                currentState.copy(
                    isLoading = false,
                    error = e.message
                )
            )
        }
    }

    private suspend fun loadCurrentExercise(
        routine: com.javargasp.tiominutermvi.domain.model.Routine,
        currentState: WorkoutState
    ) {
        val currentIndex = currentState.currentExerciseIndex
        
        if (currentIndex >= routine.exercises.size) {
            // Workout complete
            cancelTimer()
            setState(
                currentState.copy(
                    isLoading = false,
                    routine = routine,
                    isWorkoutComplete = true,
                    isTimerRunning = false,
                    timerRemainingSeconds = 0,
                    timerDurationSeconds = 0,
                    isReadyCountdown = false,
                    readyCountdownSeconds = 0
                )
            )
            return
        }

        // Cancel any existing timer before loading new exercise
        cancelTimer()

        val routineExercise = routine.exercises[currentIndex]
        val exercise = repository.getExercise(routineExercise.exerciseId)
        
        val newState = currentState.copy(
            isLoading = false,
            routine = routine,
            currentExercise = exercise,
            currentRoutineExercise = routineExercise,
            completedReps = 0,
            isTimerRunning = false,
            timerRemainingSeconds = if (routineExercise.type == ExerciseType.TIME) routineExercise.amount else 0,
            timerDurationSeconds = if (routineExercise.type == ExerciseType.TIME) routineExercise.amount else 0,
            isReadyCountdown = false,
            readyCountdownSeconds = 0
        )
        
        setState(newState)

        // Rest slots have no "Get Ready" button: their countdown starts on load
        if (routineExercise.isRest && routineExercise.type == ExerciseType.TIME) {
            startTimer(routineExercise.amount)
        }
    }

    private suspend fun moveToNextExercise(currentState: WorkoutState) {
        val nextIndex = currentState.currentExerciseIndex + 1
        
        if (nextIndex >= currentState.totalExercises) {
            finishWorkout(currentState)
        } else {
            cancelTimer()
            cancelReadyCountdown()
            val newState = currentState.copy(currentExerciseIndex = nextIndex)
            setState(newState)
            currentState.routine?.let { loadCurrentExercise(it, newState) }
        }
    }

    private suspend fun completeRepsExercise(currentState: WorkoutState) {
        if (currentState.isRepsExercise && currentState.currentRoutineExercise != null) {
            val completedState =
                currentState.copy(completedReps = currentState.currentRoutineExercise.amount)
            setState(completedState)
            // Auto-advance after completing reps (from the updated state, so finishing keeps it)
            moveToNextExercise(completedState)
        }
    }

    private suspend fun finishWorkout(currentState: WorkoutState) {
        // Prevent duplicate navigation
        if (currentState.hasNavigatedToCompletion) {
            return
        }
        
        cancelTimer()
        cancelReadyCountdown()
        setState(
            currentState.copy(
                isWorkoutComplete = true,
                hasNavigatedToCompletion = true,
                currentExercise = null,
                currentRoutineExercise = null,
                isTimerRunning = false,
                timerRemainingSeconds = 0,
                isReadyCountdown = false,
                readyCountdownSeconds = 0
            )
        )
        sendEffect(WorkoutEffect.NavigateToCompletion)
        recordCompletion(currentState)
    }

    /**
     * Records the finished workout once, with its active time, without delaying the navigation;
     * the session ends first so it is never recorded again. Failures are only logged.
     */
    private fun recordCompletion(currentState: WorkoutState) {
        val routine = currentState.routine ?: return
        val title = dailyWorkoutTitle(routine.routineDay, routine.title)
        // Not cancelled by the navigation to the completion screen clearing this ViewModel
        viewModelScope.launch(NonCancellable) {
            val durationSeconds = session.finish()
            trainingLogRecorder.recordSafely(TrainingSource.DAILY, dailyRefId(routineId), title, durationSeconds)
            try {
                dailyProgress.onDayFinished(routineId)
            } catch (e: Exception) {
                // Never breaks the workout: the day stays as it was
                println("WorkoutViewModel: daily progress not saved: ${e.message}")
            }
        }
    }
    
    /**
     * Starts a countdown timer for the specified duration in seconds.
     */
    private fun startTimer(durationSeconds: Int) {
        // Never two timers at once
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var currentState = state.value
            setState(
                currentState.copy(
                    timerDurationSeconds = durationSeconds,
                    timerRemainingSeconds = durationSeconds,
                    isTimerRunning = true
                )
            )
            
            var remaining = durationSeconds
            while (remaining > 0 && isActive) {
                delay(1000) // Wait 1 second
                remaining--
                
                currentState = state.value
                setState(
                    currentState.copy(timerRemainingSeconds = remaining)
                )
            }
            
            // Timer finished
            if (isActive && remaining == 0) {
                currentState = state.value
                setState(
                    currentState.copy(isTimerRunning = false)
                )
                handleIntent(WorkoutIntent.OnTimerFinished)
            }
        }
    }
    
    /**
     * Cancels the current timer if it's running.
     */
    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        val currentState = state.value
        setState(
            currentState.copy(
                isTimerRunning = false,
                timerRemainingSeconds = 0
            )
        )
    }
    
    /**
     * Starts a 5-second ready countdown before the main timer.
     * Skips the countdown for rest slots ([RoutineExercise.isRest]); those normally
     * start on load (see [loadCurrentExercise]), so this branch is only a fallback.
     */
    private fun startReadyCountdown(currentState: WorkoutState) {
        val routineExercise = currentState.currentRoutineExercise ?: return
        val duration = routineExercise.amount
        // Nothing starts until the exercise itself has loaded
        if (currentState.currentExercise == null) return
        
        // Skip ready countdown for rest exercises
        if (routineExercise.isRest) {
            if (timerJob?.isActive != true) startTimer(duration)
            return
        }
        
        readyCountdownJob = viewModelScope.launch {
            setState(
                currentState.copy(
                    isReadyCountdown = true,
                    readyCountdownSeconds = 5
                )
            )
            
            var countdown = 5
            while (countdown > 0 && isActive) {
                delay(1000)
                countdown--
                
                val current = state.value
                setState(
                    current.copy(readyCountdownSeconds = countdown)
                )
            }
            
            // Ready countdown finished, start the main timer
            if (isActive) {
                val current = state.value
                setState(
                    current.copy(
                        isReadyCountdown = false,
                        readyCountdownSeconds = 0
                    )
                )
                startTimer(duration)
            }
        }
    }
    
    /**
     * Cancels the ready countdown if it's running.
     */
    private fun cancelReadyCountdown() {
        readyCountdownJob?.cancel()
        readyCountdownJob = null
        val currentState = state.value
        setState(
            currentState.copy(
                isReadyCountdown = false,
                readyCountdownSeconds = 0
            )
        )
    }
}

/** Actions of the person in the workout (not loading, leaving or the screen's visibility). */
private val WorkoutIntent.isUserAction: Boolean
    get() = when (this) {
        WorkoutIntent.OnNextExercise, WorkoutIntent.OnCompleteReps, WorkoutIntent.OnSkipExercise,
        WorkoutIntent.OnFinishWorkout, WorkoutIntent.OnStartTimerWithReady -> true
        WorkoutIntent.OnLoad, WorkoutIntent.OnBackPressed, WorkoutIntent.OnTimerFinished,
        WorkoutIntent.OnScreenVisible, WorkoutIntent.OnScreenHidden -> false
    }

/**
 * What counts as active time now: (a timer runs — Get Ready, a TIME exercise or a rest —, a REPS
 * exercise is shown). A TIME exercise waiting for "Get Ready" and the finished workout are neither.
 */
private val WorkoutState.activity: Pair<Boolean, Boolean>
    get() {
        val inProgress = !isWorkoutComplete && !hasNavigatedToCompletion && !isLoading
        val timerRunning = inProgress && (isTimerRunning || isReadyCountdown)
        val repsOnScreen = inProgress && currentRoutineExercise?.type == ExerciseType.REPS
        return timerRunning to repsOnScreen
    }
