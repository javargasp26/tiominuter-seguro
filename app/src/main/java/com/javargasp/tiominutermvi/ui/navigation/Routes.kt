package com.javargasp.tiominutermvi.ui.navigation

/**
 * Navigation route definitions for the app.
 */
object Routes {
    const val HOME = "home"

    /** First-launch choice (Google or guest); the start destination until a choice is made. */
    const val WELCOME = "welcome"
    const val ACCOUNT = "account"
    const val DAILY_ROUTINE_SELECTION = "daily_routine_selection"
    const val TRAINING_HISTORY = "training_history"
    const val WEEKLY_GOAL = "weekly_goal"

    // Log an activity (new) or edit a logged one (entryId is a Long)
    const val LOG_ACTIVITY = "log_activity"
    const val ARG_ENTRY_ID = "entryId"
    const val LOG_ACTIVITY_EDIT = "log_activity/{$ARG_ENTRY_ID}"

    fun logActivityEdit(entryId: Long): String = "log_activity/$entryId"

    const val ARG_ROUTINE_ID = "routineId"
    const val WORKOUT = "workout/{$ARG_ROUTINE_ID}"

    /** The daily workout's completion; [ARG_ROUTINE_ID] is the day just finished. */
    const val WORKOUT_COMPLETION = "workout_completion/{$ARG_ROUTINE_ID}"

    // Gym routines destinations
    const val GYM_ROUTINE_SELECTION = "gym_routine_selection"
    const val GYM_ROUTINE = "gym_routine/{$ARG_ROUTINE_ID}"
    const val ARG_POSITION = "position"
    const val GYM_EXERCISE = "gym_exercise/{$ARG_ROUTINE_ID}/{$ARG_POSITION}"

    fun workout(routineId: Int): String = "workout/$routineId"

    fun workoutCompletion(routineId: Int): String = "workout_completion/$routineId"

    fun gymRoutine(routineId: Int): String = "gym_routine/$routineId"

    fun gymExercise(routineId: Int, position: Int): String = "gym_exercise/$routineId/$position"

    // Program destinations (programId is a Long)
    const val PROGRAM_LIST = "program_list"
    const val PROGRAM_CREATE = "program_create"
    const val ARG_PROGRAM_ID = "programId"
    const val ARG_WEEK = "week"
    const val ARG_DAY = "day"
    const val PROGRAM_DETAIL = "program_detail/{$ARG_PROGRAM_ID}"
    const val PROGRAM_STRENGTH = "program_strength/{$ARG_PROGRAM_ID}/{$ARG_WEEK}/{$ARG_DAY}"
    const val PROGRAM_STRENGTH_EXERCISE =
        "program_strength_exercise/{$ARG_PROGRAM_ID}/{$ARG_WEEK}/{$ARG_DAY}/{$ARG_POSITION}"
    const val PROGRAM_TREADMILL = "program_treadmill/{$ARG_PROGRAM_ID}/{$ARG_WEEK}/{$ARG_DAY}"
    const val PROGRAM_OUTDOOR_RUN = "program_outdoor_run/{$ARG_PROGRAM_ID}/{$ARG_WEEK}/{$ARG_DAY}"
    const val PROGRAM_CIRCUIT = "program_circuit/{$ARG_PROGRAM_ID}/{$ARG_WEEK}/{$ARG_DAY}"

    fun programDetail(programId: Long): String = "program_detail/$programId"

    fun programStrength(programId: Long, week: Int, day: Int): String = "program_strength/$programId/$week/$day"

    fun programStrengthExercise(programId: Long, week: Int, day: Int, position: Int): String =
        "program_strength_exercise/$programId/$week/$day/$position"

    fun programTreadmill(programId: Long, week: Int, day: Int): String = "program_treadmill/$programId/$week/$day"

    fun programOutdoorRun(programId: Long, week: Int, day: Int): String = "program_outdoor_run/$programId/$week/$day"

    fun programCircuit(programId: Long, week: Int, day: Int): String = "program_circuit/$programId/$week/$day"

    // Groups (ui/groups/GroupsNavGraph.kt)
    const val GROUPS = "groups"
    const val GROUP_CREATE = "group_create"
    const val GROUP_JOIN = "group_join"
    const val ARG_GROUP_ID = "groupId"
    const val GROUP_DETAIL = "group_detail/{$ARG_GROUP_ID}"

    fun groupDetail(groupId: String): String = "group_detail/$groupId"
}
