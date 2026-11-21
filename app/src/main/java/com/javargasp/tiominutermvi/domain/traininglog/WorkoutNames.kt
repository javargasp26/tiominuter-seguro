package com.javargasp.tiominutermvi.domain.traininglog

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.Routine

/**
 * The locale-free name of a program workout, the same for every language: the program's
 * strength day titles and its cardio kinds. Its [name] is the code of a feed post's
 * `program/<CODE>` title key, so **never rename or remove a value** (older posts and other
 * apps' posts use it); add new ones.
 */
enum class ProgramTitleCode {
    FULL_BODY_A, FULL_BODY_B, FULL_BODY_C, UPPER_A, UPPER_B, LOWER_A, LOWER_B, PUSH, PULL, LEGS,
    FULL_BODY, UPPER, LOWER, UPPER_C, LOWER_C,
    OUTDOOR_RUN, BRISK_WALK, TREADMILL, HOME_CIRCUIT;

    companion object {
        /** The code of [code]; null for one this app doesn't know (a newer app's). */
        fun fromCode(code: String): ProgramTitleCode? = entries.firstOrNull { it.name == code }
    }
}

/**
 * What a workout is called, without the language: the UI turns it into text in the viewer's
 * language at display time (`WorkoutName.text()` in `ui/components`). Built only by
 * [WorkoutNameResolver].
 */
sealed interface WorkoutName {
    /** A gym routine of the catalog: its English and Spanish names. */
    data class Gym(val name: String, val nameEs: String?) : WorkoutName

    /** A daily routine: "Day N", plus its content title (and Spanish title) when it has one. */
    data class Daily(val dayNumber: Int, val title: String? = null, val titleEs: String? = null) : WorkoutName

    /** A program workout, named by its code. */
    data class Program(val code: ProgramTitleCode) : WorkoutName

    /** A logged activity of a named type (OTHER with a custom name is [Stored]). */
    data class Activity(val type: ActivityType) : WorkoutName

    /** A text shown as it is: an activity's custom name, or the stored title as the fallback. */
    data class Stored(val text: String) : WorkoutName

    /** Nothing to name it by: the source label ("Gym routine"). */
    data class SourceOnly(val source: TrainingSource) : WorkoutName
}

data class GymRoutineNames(val name: String, val nameEs: String?)

data class DailyRoutineNames(val title: String?, val titleEs: String?)

/**
 * The names a viewer's device knows: the gym and daily catalogs (by routineId) and the
 * program workouts of this device's history (by the entry's Room refId, "program:<id>:<w>:<d>").
 */
data class WorkoutNameCatalog(
    val gymRoutines: Map<Int, GymRoutineNames> = emptyMap(),
    val dailyRoutines: Map<Int, DailyRoutineNames> = emptyMap(),
    val programWorkouts: Map<String, ProgramTitleCode> = emptyMap()
) {
    companion object {
        val EMPTY = WorkoutNameCatalog()

        fun of(gymRoutines: List<GymRoutine>, dailyRoutines: List<Routine>): WorkoutNameCatalog = WorkoutNameCatalog(
            gymRoutines = gymRoutines.associate { it.routineId to GymRoutineNames(it.routineName, it.routineNameEs) },
            dailyRoutines = dailyRoutines.associate { it.routineId to DailyRoutineNames(it.title, it.titleEs) }
        )
    }
}

/**
 * The locale-free name of a feed post (`titleKey`), so each reader names the workout in their
 * own language without the author's data. Strict format (the rules check the same, see
 * firebase/firestore.rules `isPostTitleKey`), at most [MAX_LENGTH] characters, only on a post of
 * its own source:
 * - `gym/<routineId>` (GYM) and `daily/<dayNumber>` (DAILY): 1 to 9 digits, no leading zero;
 * - `program/<CODE>` (PROGRAM): an uppercase letter, then uppercase letters, digits or `_`
 *   (a [ProgramTitleCode] name; readers that don't know the code show the post's title).
 */
sealed interface WorkoutTitleKey {
    val source: TrainingSource
    val value: String

    data class Gym(val routineId: Int) : WorkoutTitleKey {
        override val source get() = TrainingSource.GYM
        override val value get() = "$GYM_PREFIX$routineId"
    }

    data class Daily(val dayNumber: Int) : WorkoutTitleKey {
        override val source get() = TrainingSource.DAILY
        override val value get() = "$DAILY_PREFIX$dayNumber"
    }

    data class Program(val code: String) : WorkoutTitleKey {
        override val source get() = TrainingSource.PROGRAM
        override val value get() = "$PROGRAM_PREFIX$code"
    }

    companion object {
        const val MAX_LENGTH = 80
        private const val GYM_PREFIX = "gym/"
        private const val DAILY_PREFIX = "daily/"
        private const val PROGRAM_PREFIX = "program/"
        private val NUMBER = Regex("[1-9][0-9]{0,8}")
        private val CODE = Regex("[A-Z][A-Z0-9_]*")

        /** The key of [value] when it is valid for a post of [source]; null otherwise. */
        fun parse(value: String?, source: TrainingSource): WorkoutTitleKey? {
            if (value == null || value.length > MAX_LENGTH) return null
            val key = when {
                value.startsWith(GYM_PREFIX) ->
                    value.removePrefix(GYM_PREFIX).takeIf { NUMBER.matches(it) }?.let { Gym(it.toInt()) }
                value.startsWith(DAILY_PREFIX) ->
                    value.removePrefix(DAILY_PREFIX).takeIf { NUMBER.matches(it) }?.let { Daily(it.toInt()) }
                value.startsWith(PROGRAM_PREFIX) ->
                    value.removePrefix(PROGRAM_PREFIX).takeIf { CODE.matches(it) }?.let { Program(it) }
                else -> null
            }
            return key?.takeIf { it.source == source }
        }

        /** [key]'s value when it is valid (a key the rules would refuse is never sent). */
        fun valueOrNull(key: WorkoutTitleKey): String? = key.value.takeIf { parse(it, key.source) == key }
    }
}

/**
 * The program workout a PROGRAM refId points to, read from the program feature's own data
 * (bound in `programModule`). Null when the program or the workout no longer exists.
 */
fun interface ProgramWorkoutTitleCodes {
    suspend fun titleCodeOf(refId: String): ProgramTitleCode?

    companion object {
        val None = ProgramWorkoutTitleCodes { null }
    }
}

/**
 * The single rule for what a workout is called. Every screen that shows a workout's name
 * (history, Home, the daily selection, the completion top bars, the group feed) goes through
 * it, and the UI renders the result in the viewer's language; **no screen shows a stored
 * title directly**. The stored `title` (in the recording device's language) is only the
 * fallback when nothing better is known.
 */
object WorkoutNameResolver {

    /**
     * A history entry. GYM: the routine of `gym:<id>` in the catalog, else the stored title.
     * DAILY: "Day N" from `daily:<N>` (with the routine's title if the catalog has one), else the
     * stored title. PROGRAM: the workout of its refId, else the stored title (the program was
     * deleted). ACTIVITY: OTHER's custom name, else its type. A blank stored title shows the source.
     */
    fun nameOf(entry: TrainingLogEntry, catalog: WorkoutNameCatalog): WorkoutName = when (entry.source) {
        TrainingSource.GYM -> gymRoutineIdOf(entry.refId)?.let { gymName(it, catalog) }
        TrainingSource.DAILY -> dailyRoutineIdOf(entry.refId)?.takeIf { it >= 1 }?.let { dailyName(it, catalog) }
        TrainingSource.PROGRAM -> entry.refId?.let { catalog.programWorkouts[it] }?.let { WorkoutName.Program(it) }
        TrainingSource.ACTIVITY -> activityName(entry.activity, entry.title)
    } ?: stored(entry.title, entry.source)

    /**
     * A feed post, on the reader's device: its [titleKey] when it is valid for its [source] and
     * the reader knows what it names (a gym routine in the catalog, a known program code; a day
     * number always), else the author's [title] (older posts, unknown codes, a removed routine).
     * An activity is named by its [activityType], as before.
     */
    fun nameOfPost(
        titleKey: String?,
        source: TrainingSource,
        activityType: ActivityType?,
        title: String,
        catalog: WorkoutNameCatalog
    ): WorkoutName {
        if (source == TrainingSource.ACTIVITY) return activityName(activityType, title) ?: stored(title, source)
        val resolved = when (val key = WorkoutTitleKey.parse(titleKey, source)) {
            is WorkoutTitleKey.Gym -> gymName(key.routineId, catalog)
            is WorkoutTitleKey.Daily -> dailyName(key.dayNumber, catalog)
            is WorkoutTitleKey.Program -> ProgramTitleCode.fromCode(key.code)?.let { WorkoutName.Program(it) }
            null -> null
        }
        return resolved ?: stored(title, source)
    }

    /**
     * The `titleKey` of [entry]'s post: GYM and DAILY from their refId; PROGRAM from its workout
     * ([programCode], looked up by the caller); none for an activity or when nothing is known.
     */
    fun titleKeyOf(entry: TrainingLogEntry, programCode: ProgramTitleCode?): String? {
        val key = when (entry.source) {
            TrainingSource.GYM -> gymRoutineIdOf(entry.refId)?.let { WorkoutTitleKey.Gym(it) }
            TrainingSource.DAILY -> dailyRoutineIdOf(entry.refId)?.let { WorkoutTitleKey.Daily(it) }
            TrainingSource.PROGRAM -> programCode?.let { WorkoutTitleKey.Program(it.name) }
            TrainingSource.ACTIVITY -> null
        }
        return key?.let { WorkoutTitleKey.valueOrNull(it) }
    }

    /** A daily routine of the catalog (Home, the daily selection): its routineId is its day number. */
    fun dailyName(routine: Routine): WorkoutName.Daily =
        WorkoutName.Daily(routine.routineId, routine.title, routine.titleEs)

    /** A gym routine (its screens' top bars, the gym selection). */
    fun gymName(routine: GymRoutine): WorkoutName.Gym = gymName(routine.routineName, routine.routineNameEs)

    /** A gym routine known by its names only (the exercise screen keeps them in its state). */
    fun gymName(routineName: String, routineNameEs: String?): WorkoutName.Gym = WorkoutName.Gym(routineName, routineNameEs)

    private fun gymName(routineId: Int, catalog: WorkoutNameCatalog): WorkoutName? =
        catalog.gymRoutines[routineId]?.let { WorkoutName.Gym(it.name, it.nameEs) }

    private fun dailyName(dayNumber: Int, catalog: WorkoutNameCatalog): WorkoutName {
        val names = catalog.dailyRoutines[dayNumber]
        return WorkoutName.Daily(dayNumber, names?.title, names?.titleEs)
    }

    private fun activityName(type: ActivityType?, title: String): WorkoutName? = when {
        type == null -> null
        type == ActivityType.OTHER && title.isNotBlank() -> WorkoutName.Stored(title)
        else -> WorkoutName.Activity(type)
    }

    private fun stored(title: String, source: TrainingSource): WorkoutName =
        if (title.isBlank()) WorkoutName.SourceOnly(source) else WorkoutName.Stored(title)
}
