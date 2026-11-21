package com.javargasp.tiominutermvi.domain.program.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Output of the generator: every week of the program, each with its workouts in order. */
@Serializable
data class GeneratedProgram(val weeks: List<ProgramWeek>)

/** One week. [weekNumber] starts at 1; deload weeks reduce volume. */
@Serializable
data class ProgramWeek(
    val weekNumber: Int,
    val isDeload: Boolean,
    val workouts: List<ProgramWorkout>
)

/**
 * One workout of a week. Stored in Room as JSON (the `payload` column), so a stored
 * program never changes when the templates change.
 */
@Serializable
sealed interface ProgramWorkout {

    /** Strength session: slots in order; [StrengthSlot.reps] holds one value per set. */
    @Serializable
    @SerialName("STRENGTH")
    data class Strength(
        val title: StrengthDayTitle,
        val slots: List<StrengthSlot>
    ) : ProgramWorkout

    @Serializable
    sealed interface Cardio : ProgramWorkout

    /** Outdoor run (or brisk walk when [walk]) of [distanceKm]. */
    @Serializable
    @SerialName("OUTDOOR_RUN")
    data class OutdoorRun(
        val distanceKm: Double,
        val walk: Boolean
    ) : Cardio

    @Serializable
    @SerialName("TREADMILL")
    data class Treadmill(val segments: List<TreadmillSegment>) : Cardio

    /** [rounds] of [exerciseIds], each worked for [workSeconds] then rested for [restSeconds]. */
    @Serializable
    @SerialName("HOME_CIRCUIT")
    data class HomeCircuit(
        val rounds: Int,
        val workSeconds: Int,
        val restSeconds: Int,
        val restBetweenRoundsSeconds: Int,
        val exerciseIds: List<Int>
    ) : Cardio
}

/**
 * One exercise of a strength workout. [position] is its index in the workout.
 * For [ProgramSlotType.REPS] each value of [reps] is a rep count; for TIME it is seconds.
 * With [perSide] every value is per side: each set is done on the left, then the right side.
 * Defaults to false, so payloads stored before the field existed still decode.
 */
@Serializable
data class StrengthSlot(
    val position: Int,
    val exerciseId: Int,
    val type: ProgramSlotType,
    val reps: List<Int>,
    val restSeconds: Int,
    val perSide: Boolean = false
) {
    val sets: Int get() = reps.size
}

@Serializable
data class TreadmillSegment(
    val durationSeconds: Int,
    val speedKmh: Double,
    val inclinePercent: Double,
    val label: TreadmillSegmentLabel
)

/** Stored kind of a workout; matches the serial names of [ProgramWorkout]. */
enum class ProgramWorkoutKind { STRENGTH, OUTDOOR_RUN, TREADMILL, HOME_CIRCUIT }

val ProgramWorkout.kind: ProgramWorkoutKind
    get() = when (this) {
        is ProgramWorkout.Strength -> ProgramWorkoutKind.STRENGTH
        is ProgramWorkout.OutdoorRun -> ProgramWorkoutKind.OUTDOOR_RUN
        is ProgramWorkout.Treadmill -> ProgramWorkoutKind.TREADMILL
        is ProgramWorkout.HomeCircuit -> ProgramWorkoutKind.HOME_CIRCUIT
    }
