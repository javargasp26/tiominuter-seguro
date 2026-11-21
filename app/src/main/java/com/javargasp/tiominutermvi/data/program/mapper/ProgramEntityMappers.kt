package com.javargasp.tiominutermvi.data.program.mapper

import com.javargasp.tiominutermvi.data.program.local.ProgramSummaryRow
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.kind
import kotlinx.serialization.json.Json

/** Json of the stored workout payloads. The discriminator is "kind" (e.g. "STRENGTH"). */
val programPayloadJson = Json {
    classDiscriminator = "kind"
    ignoreUnknownKeys = true
}

fun ProgramWorkout.toPayload(): String = programPayloadJson.encodeToString(ProgramWorkout.serializer(), this)

/** Decodes a stored payload; null (and logged) when it can't be read, so bad rows never crash the app. */
fun String.toProgramWorkoutOrNull(): ProgramWorkout? =
    try {
        programPayloadJson.decodeFromString(ProgramWorkout.serializer(), this)
    } catch (e: IllegalArgumentException) {
        println("ProgramEntityMappers: unreadable workout payload (${e.message})")
        null
    }

fun ProgramProfile.toEntity(createdAt: Long): ProgramEntity = ProgramEntity(
    goal = goal.name,
    level = level.name,
    location = location.name,
    weeks = weeks,
    daysPerWeek = daysPerWeek,
    age = age,
    weightKg = weightKg,
    heightCm = heightCm,
    createdAt = createdAt,
    focus = focus.code
)

fun ProgramEntity.toProfile(): ProgramProfile = ProgramProfile(
    age = age,
    weightKg = weightKg,
    heightCm = heightCm,
    level = ProgramLevel.valueOf(level),
    goal = ProgramGoal.valueOf(goal),
    location = ProgramLocation.valueOf(location),
    weeks = weeks,
    daysPerWeek = daysPerWeek,
    focus = focus.toProgramFocus()
)

/** An unknown stored focus reads as the default (logged), so a bad row never crashes the app. */
private fun String.toProgramFocus(): ProgramFocus =
    ProgramFocus.fromCode(this) ?: run {
        println("ProgramEntityMappers: unknown focus '$this', using ${ProgramFocus.DEFAULT}")
        ProgramFocus.DEFAULT
    }

fun ProgramSummaryRow.toDomain(): ProgramSummary = program.toSummary(completedWorkouts, totalWorkouts)

fun ProgramEntity.toSummary(completedWorkouts: Int, totalWorkouts: Int): ProgramSummary = ProgramSummary(
    id = id,
    profile = toProfile(),
    createdAt = createdAt,
    completedWorkouts = completedWorkouts,
    totalWorkouts = totalWorkouts
)

/** One row per workout: week numbers start at 1, day indices at 0. */
fun GeneratedProgram.toWorkoutEntities(programId: Long): List<ProgramWorkoutEntity> =
    weeks.flatMap { week ->
        week.workouts.mapIndexed { dayIndex, workout ->
            ProgramWorkoutEntity(
                programId = programId,
                week = week.weekNumber,
                dayIndex = dayIndex,
                kind = workout.kind.name,
                isDeload = week.isDeload,
                payload = workout.toPayload()
            )
        }
    }
