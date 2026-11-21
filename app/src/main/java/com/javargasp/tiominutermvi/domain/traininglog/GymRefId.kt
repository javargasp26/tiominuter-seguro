package com.javargasp.tiominutermvi.domain.traininglog

private const val GYM_REF_PREFIX = "gym:"

/** refId of a GYM entry: "gym:<routineId>". */
fun gymRefId(routineId: Int): String = "$GYM_REF_PREFIX$routineId"

/** The routineId of a "gym:<routineId>" refId; null for any other refId. */
fun gymRoutineIdOf(refId: String?): Int? =
    refId?.takeIf { it.startsWith(GYM_REF_PREFIX) }?.removePrefix(GYM_REF_PREFIX)?.toIntOrNull()
