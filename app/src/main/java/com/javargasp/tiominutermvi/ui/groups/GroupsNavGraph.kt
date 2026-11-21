package com.javargasp.tiominutermvi.ui.groups

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.javargasp.tiominutermvi.ui.groups.create.GroupCreateScreen
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailScreen
import com.javargasp.tiominutermvi.ui.groups.join.GroupJoinScreen
import com.javargasp.tiominutermvi.ui.groups.list.GroupListScreen
import com.javargasp.tiominutermvi.ui.navigation.Routes

/** Destinations of the Groups feature, added to the app's NavHost by AppNavigation. */
fun NavGraphBuilder.groupsNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    // Create and join are replaced by the group they lead to
    val openGroupFrom: (String) -> (String) -> Unit = { route ->
        { groupId ->
            navController.navigate(Routes.groupDetail(groupId)) {
                popUpTo(route) { inclusive = true }
            }
        }
    }

    composable(Routes.GROUPS) {
        GroupListScreen(
            onNavigateBack = back,
            onNavigateToCreate = { navController.navigate(Routes.GROUP_CREATE) },
            onNavigateToJoin = { navController.navigate(Routes.GROUP_JOIN) },
            onNavigateToDetail = { navController.navigate(Routes.groupDetail(it)) },
            onNavigateToAccount = { navController.navigate(Routes.ACCOUNT) },
            onNavigateToWeeklyGoal = { navController.navigate(Routes.WEEKLY_GOAL) }
        )
    }

    composable(Routes.GROUP_CREATE) {
        GroupCreateScreen(onNavigateBack = back, onNavigateToGroup = openGroupFrom(Routes.GROUP_CREATE))
    }

    composable(Routes.GROUP_JOIN) {
        GroupJoinScreen(onNavigateBack = back, onNavigateToGroup = openGroupFrom(Routes.GROUP_JOIN))
    }

    composable(
        route = Routes.GROUP_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val groupId = backStackEntry.arguments?.getString(Routes.ARG_GROUP_ID) ?: return@composable
        GroupDetailScreen(groupId = groupId, onNavigateBack = back)
    }
}
