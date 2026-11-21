package com.javargasp.tiominutermvi.ui.program

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutKind
import com.javargasp.tiominutermvi.ui.navigation.Routes
import com.javargasp.tiominutermvi.ui.program.circuit.ProgramCircuitScreen
import com.javargasp.tiominutermvi.ui.program.create.ProgramCreateScreen
import com.javargasp.tiominutermvi.ui.program.detail.ProgramDetailScreen
import com.javargasp.tiominutermvi.ui.program.list.ProgramListScreen
import com.javargasp.tiominutermvi.ui.program.outdoorrun.ProgramOutdoorRunScreen
import com.javargasp.tiominutermvi.ui.program.strength.ProgramStrengthScreen
import com.javargasp.tiominutermvi.ui.program.strengthexercise.ProgramStrengthExerciseScreen
import com.javargasp.tiominutermvi.ui.program.treadmill.ProgramTreadmillScreen

private val programIdArg = navArgument(Routes.ARG_PROGRAM_ID) { type = NavType.LongType }
private val workoutArgs = listOf(
    programIdArg,
    navArgument(Routes.ARG_WEEK) { type = NavType.IntType },
    navArgument(Routes.ARG_DAY) { type = NavType.IntType }
)

/** Typed arguments of a program workout route. */
private class WorkoutArgs(val programId: Long, val week: Int, val day: Int)

private fun NavBackStackEntry.workoutArgs(): WorkoutArgs? {
    val args = arguments ?: return null
    return WorkoutArgs(
        programId = args.getLong(Routes.ARG_PROGRAM_ID),
        week = args.getInt(Routes.ARG_WEEK),
        day = args.getInt(Routes.ARG_DAY)
    )
}

/** Destinations of the Program feature, added to the app's NavHost by AppNavigation. */
fun NavGraphBuilder.programNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }

    composable(Routes.PROGRAM_LIST) {
        ProgramListScreen(
            onNavigateToCreate = { navController.navigate(Routes.PROGRAM_CREATE) },
            onNavigateToDetail = { navController.navigate(Routes.programDetail(it)) },
            onNavigateBack = back
        )
    }

    composable(Routes.PROGRAM_CREATE) {
        ProgramCreateScreen(
            onNavigateToDetail = { programId ->
                navController.navigate(Routes.programDetail(programId)) {
                    popUpTo(Routes.PROGRAM_CREATE) { inclusive = true }
                }
            },
            onNavigateBack = back
        )
    }

    composable(Routes.PROGRAM_DETAIL, arguments = listOf(programIdArg)) { entry ->
        val programId = entry.arguments?.getLong(Routes.ARG_PROGRAM_ID) ?: return@composable
        ProgramDetailScreen(
            programId = programId,
            onNavigateToWorkout = { kind, week, day ->
                val route = when (kind) {
                    ProgramWorkoutKind.STRENGTH -> Routes.programStrength(programId, week, day)
                    ProgramWorkoutKind.TREADMILL -> Routes.programTreadmill(programId, week, day)
                    ProgramWorkoutKind.OUTDOOR_RUN -> Routes.programOutdoorRun(programId, week, day)
                    ProgramWorkoutKind.HOME_CIRCUIT -> Routes.programCircuit(programId, week, day)
                }
                navController.navigate(route)
            },
            onNavigateBack = back
        )
    }

    composable(Routes.PROGRAM_STRENGTH, arguments = workoutArgs) { entry ->
        val args = entry.workoutArgs() ?: return@composable
        ProgramStrengthScreen(
            programId = args.programId,
            week = args.week,
            dayIndex = args.day,
            onNavigateToExercise = { position ->
                navController.navigate(Routes.programStrengthExercise(args.programId, args.week, args.day, position))
            },
            onNavigateBack = back
        )
    }

    composable(
        Routes.PROGRAM_STRENGTH_EXERCISE,
        arguments = workoutArgs + navArgument(Routes.ARG_POSITION) { type = NavType.IntType }
    ) { entry ->
        val args = entry.workoutArgs() ?: return@composable
        val position = entry.arguments?.getInt(Routes.ARG_POSITION) ?: return@composable
        ProgramStrengthExerciseScreen(
            programId = args.programId,
            week = args.week,
            dayIndex = args.day,
            position = position,
            onNavigateBack = back
        )
    }

    composable(Routes.PROGRAM_TREADMILL, arguments = workoutArgs) { entry ->
        val args = entry.workoutArgs() ?: return@composable
        ProgramTreadmillScreen(args.programId, args.week, args.day, onNavigateBack = back)
    }

    composable(Routes.PROGRAM_OUTDOOR_RUN, arguments = workoutArgs) { entry ->
        val args = entry.workoutArgs() ?: return@composable
        ProgramOutdoorRunScreen(args.programId, args.week, args.day, onNavigateBack = back)
    }

    composable(Routes.PROGRAM_CIRCUIT, arguments = workoutArgs) { entry ->
        val args = entry.workoutArgs() ?: return@composable
        ProgramCircuitScreen(args.programId, args.week, args.day, onNavigateBack = back)
    }
}
