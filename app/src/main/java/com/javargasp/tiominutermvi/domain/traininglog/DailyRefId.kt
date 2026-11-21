package com.javargasp.tiominutermvi.domain.traininglog

private const val DAILY_REF_PREFIX = "daily:"

/** refId of a DAILY entry: "daily:<routineId>". */
fun dailyRefId(routineId: Int): String = "$DAILY_REF_PREFIX$routineId"

/** The routineId of a "daily:<routineId>" refId; null for any other refId. */
fun dailyRoutineIdOf(refId: String?): Int? =
    refId?.takeIf { it.startsWith(DAILY_REF_PREFIX) }?.removePrefix(DAILY_REF_PREFIX)?.toIntOrNull()

/** Title of a DAILY entry: the routine's day, plus " · title" when it has one. */
fun dailyWorkoutTitle(routineDay: String, title: String?): String =
    listOfNotNull(routineDay, title?.takeIf { it.isNotBlank() }).joinToString(" · ")
