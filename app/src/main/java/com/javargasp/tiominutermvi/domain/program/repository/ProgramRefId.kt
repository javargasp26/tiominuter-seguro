package com.javargasp.tiominutermvi.domain.program.repository

/** refId of a PROGRAM entry, also the key of its sessions: "program:<programId>:<week>:<dayIndex>". */
fun programRefId(programId: Long, week: Int, dayIndex: Int): String = "program:$programId:$week:$dayIndex"

/** (programId, week, dayIndex) of a "program:…" refId; null for any other refId. */
fun programWorkoutOf(refId: String): Triple<Long, Int, Int>? {
    val parts = refId.split(':')
    if (parts.size != 4 || parts[0] != "program") return null
    val programId = parts[1].toLongOrNull() ?: return null
    val week = parts[2].toIntOrNull() ?: return null
    val dayIndex = parts[3].toIntOrNull() ?: return null
    return Triple(programId, week, dayIndex)
}

/** Firestore id of a program's workout or completion: "{week}-{dayIndex}". */
fun workoutDocId(week: Int, dayIndex: Int): String = "$week-$dayIndex"

/** (week, dayIndex) of a "{week}-{dayIndex}" id; null otherwise. */
fun workoutOfDocId(id: String): Pair<Int, Int>? {
    val parts = id.split('-')
    if (parts.size != 2) return null
    val week = parts[0].toIntOrNull() ?: return null
    val dayIndex = parts[1].toIntOrNull() ?: return null
    return week to dayIndex
}
