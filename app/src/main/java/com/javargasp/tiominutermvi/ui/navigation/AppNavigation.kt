package com.javargasp.tiominutermvi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.javargasp.tiominutermvi.ui.account.AccountScreen
import com.javargasp.tiominutermvi.ui.dailyroutineselection.DailyRoutineSelectionScreen
import com.javargasp.tiominutermvi.ui.groups.groupsNavGraph
import com.javargasp.tiominutermvi.ui.gymexercise.GymExerciseScreen
import com.javargasp.tiominutermvi.ui.gymroutine.GymRoutineScreen
import com.javargasp.tiominutermvi.ui.gymroutineselection.GymRoutineSelectionScreen
import com.javargasp.tiominutermvi.ui.home.HomeScreen
import com.javargasp.tiominutermvi.ui.logactivity.LogActivityScreen
import com.javargasp.tiominutermvi.ui.program.programNavGraph
import com.javargasp.tiominutermvi.ui.traininglog.TrainingHistoryScreen
import com.javargasp.tiominutermvi.ui.weeklygoal.WeeklyGoalScreen
import com.javargasp.tiominutermvi.ui.welcome.WelcomeScreen
import com.javargasp.tiominutermvi.ui.workout.WorkoutCompletionScreen
import com.javargasp.tiominutermvi.ui.workout.WorkoutScreen

/**
 * Main navigation setup for the app.
 */
@Composable
fun AppNavigation(
    startDestination: String = Routes.HOME,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // First launch only (start destination until a choice is made); leaves no back entry
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToDailyRoutineSelection = {
                    navController.navigate(Routes.DAILY_ROUTINE_SELECTION)
                },
                onNavigateToGymRoutineSelection = {
                    navController.navigate(Routes.GYM_ROUTINE_SELECTION)
                },
                onNavigateToProgramList = {
                    navController.navigate(Routes.PROGRAM_LIST)
                },
                onNavigateToTrainingHistory = {
                    navController.navigate(Routes.TRAINING_HISTORY)
                },
                onNavigateToWeeklyGoal = {
                    navController.navigate(Routes.WEEKLY_GOAL)
                },
                onNavigateToLogActivity = {
                    navController.navigate(Routes.LOG_ACTIVITY)
                },
                onNavigateToAccount = {
                    navController.navigate(Routes.ACCOUNT)
                },
                onNavigateToGroups = {
                    navController.navigate(Routes.GROUPS)
                },
                onNavigateToGroupDetail = { groupId ->
                    navController.navigate(Routes.groupDetail(groupId))
                }
            )
        }

        composable(Routes.LOG_ACTIVITY) {
            LogActivityScreen(
                entryId = null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.LOG_ACTIVITY_EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_ENTRY_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong(Routes.ARG_ENTRY_ID) ?: return@composable
            LogActivityScreen(
                entryId = entryId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.WEEKLY_GOAL) {
            WeeklyGoalScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TRAINING_HISTORY) {
            TrainingHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogActivity = {
                    navController.navigate(Routes.LOG_ACTIVITY)
                },
                onNavigateToEditActivity = { entryId ->
                    navController.navigate(Routes.logActivityEdit(entryId))
                }
            )
        }

        composable(Routes.DAILY_ROUTINE_SELECTION) {
            DailyRoutineSelectionScreen(
                onNavigateToWorkout = { routineId ->
                    navController.navigate(Routes.workout(routineId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.WORKOUT) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getString(Routes.ARG_ROUTINE_ID)?.toIntOrNull() ?: return@composable
            WorkoutScreen(
                routineId = routineId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToCompletion = {
                    navController.navigate(Routes.workoutCompletion(routineId))
                }
            )
        }

        composable(Routes.WORKOUT_COMPLETION) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getString(Routes.ARG_ROUTINE_ID)?.toIntOrNull() ?: return@composable
            WorkoutCompletionScreen(
                routineId = routineId,
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                // After "Start again from Day 1": the days, locked again, above Home
                onNavigateToDailySelection = {
                    navController.navigate(Routes.DAILY_ROUTINE_SELECTION) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.GYM_ROUTINE_SELECTION) {
            GymRoutineSelectionScreen(
                onNavigateToGymRoutine = { routineId ->
                    navController.navigate(Routes.gymRoutine(routineId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.GYM_ROUTINE,
            arguments = listOf(
                navArgument(Routes.ARG_ROUTINE_ID) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getInt(Routes.ARG_ROUTINE_ID) ?: return@composable
            GymRoutineScreen(
                routineId = routineId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToExercise = { routineId, position ->
                    navController.navigate(Routes.gymExercise(routineId, position))
                }
            )
        }

        composable(
            route = Routes.GYM_EXERCISE,
            arguments = listOf(
                navArgument(Routes.ARG_ROUTINE_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_POSITION) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getInt(Routes.ARG_ROUTINE_ID) ?: return@composable
            val position = backStackEntry.arguments?.getInt(Routes.ARG_POSITION) ?: return@composable
            GymExerciseScreen(
                routineId = routineId,
                position = position,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        programNavGraph(navController)

        groupsNavGraph(navController)
    }
}
