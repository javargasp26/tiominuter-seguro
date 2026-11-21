package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One exercise slot inside a gym routine. [position] is the exercise's index
 * inside the routine, so the same exercise can appear twice.
 *
 * [reps] holds one entry per set: a duration in seconds for TIME types,
 * a rep count for REPS. The number of sets is [reps].size and is not stored
 * separately.
 */
@Entity(
    tableName = "gym_routine_exercises",
    primaryKeys = ["routineId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = GymRoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseId"),
        Index("routineId")
    ]
)
data class GymRoutineExerciseEntity(
    val routineId: Int,
    val position: Int,
    val exerciseId: Int,
    val type: String,
    val reps: List<Int>,
    val restSeconds: Int
)

/**
 * Converts the [reps] list to/from a comma-separated string column.
 */
class RepsListConverter {

    @TypeConverter
    fun fromRepsList(reps: List<Int>): String = reps.joinToString(separator = ",")

    @TypeConverter
    fun toRepsList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toInt() }
}
