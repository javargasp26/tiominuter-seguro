package com.javargasp.tiominutermvi.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.logactivity.activityTypeLabel

/** The texts a [WorkoutName] is made of, in one language. */
interface WorkoutNameTexts {
    /** Language code of these texts ("es"), for the catalog names (see [localizedName]). */
    val language: String

    /** "Day 3" / "Día 3". */
    fun day(dayNumber: Int): String

    /** "Day 3 · Core" / "Día 3 · Core". */
    fun dayWithTitle(day: String, title: String): String

    fun program(code: ProgramTitleCode): String

    fun activity(type: ActivityType): String

    fun source(source: TrainingSource): String
}

/**
 * The text of a workout name in [texts]' language. Gym: [localizedName]. Daily: "Day N", plus
 * the routine's title in that language when there is one (Spanish: only its Spanish title, so
 * a Spanish screen never shows an English title). Program: the workout's title. Activity: its
 * type. Stored: as it is. Nothing known: the source label.
 */
fun WorkoutName.text(texts: WorkoutNameTexts): String = when (this) {
    is WorkoutName.Gym -> localizedName(name, nameEs, texts.language)
    is WorkoutName.Daily -> {
        val day = texts.day(dayNumber)
        val title = if (texts.language == SPANISH_LANGUAGE) titleEs else title
        title?.takeIf { it.isNotBlank() }?.let { texts.dayWithTitle(day, it) } ?: day
    }
    is WorkoutName.Program -> texts.program(code)
    is WorkoutName.Activity -> texts.activity(type)
    is WorkoutName.Stored -> text
    is WorkoutName.SourceOnly -> texts.source(source)
}

/** The text of this workout name in the app's current language (follows language changes). */
@Composable
fun WorkoutName.text(): String {
    val resources = LocalResources.current
    val language = currentLanguage()
    val texts = remember(resources, language) { ResourceWorkoutNameTexts(resources, language) }
    return text(texts)
}

/** [WorkoutNameTexts] from the app's string resources. */
class ResourceWorkoutNameTexts(private val resources: Resources, override val language: String) : WorkoutNameTexts {
    override fun day(dayNumber: Int): String = resources.getString(R.string.workout_name_day, dayNumber)
    override fun dayWithTitle(day: String, title: String): String =
        resources.getString(R.string.workout_name_day_with_title, day, title)
    override fun program(code: ProgramTitleCode): String = resources.getString(programTitleRes(code))
    override fun activity(type: ActivityType): String = resources.getString(activityTypeLabel(type))
    override fun source(source: TrainingSource): String = resources.getString(sourceLabelRes(source))
}

/** The title of a program workout; the program feature's `workoutTitleRes` uses it too. */
@StringRes
fun programTitleRes(code: ProgramTitleCode): Int = when (code) {
    ProgramTitleCode.FULL_BODY_A -> R.string.program_title_full_body_a
    ProgramTitleCode.FULL_BODY_B -> R.string.program_title_full_body_b
    ProgramTitleCode.FULL_BODY_C -> R.string.program_title_full_body_c
    ProgramTitleCode.UPPER_A -> R.string.program_title_upper_a
    ProgramTitleCode.UPPER_B -> R.string.program_title_upper_b
    ProgramTitleCode.LOWER_A -> R.string.program_title_lower_a
    ProgramTitleCode.LOWER_B -> R.string.program_title_lower_b
    ProgramTitleCode.PUSH -> R.string.program_title_push
    ProgramTitleCode.PULL -> R.string.program_title_pull
    ProgramTitleCode.LEGS -> R.string.program_title_legs
    ProgramTitleCode.FULL_BODY -> R.string.program_title_full_body
    ProgramTitleCode.UPPER -> R.string.program_title_upper
    ProgramTitleCode.LOWER -> R.string.program_title_lower
    ProgramTitleCode.UPPER_C -> R.string.program_title_upper_c
    ProgramTitleCode.LOWER_C -> R.string.program_title_lower_c
    ProgramTitleCode.OUTDOOR_RUN -> R.string.program_title_outdoor_run
    ProgramTitleCode.BRISK_WALK -> R.string.program_title_brisk_walk
    ProgramTitleCode.TREADMILL -> R.string.program_title_treadmill
    ProgramTitleCode.HOME_CIRCUIT -> R.string.program_title_home_circuit
}

@StringRes
fun sourceLabelRes(source: TrainingSource): Int = when (source) {
    TrainingSource.DAILY -> R.string.training_source_daily
    TrainingSource.GYM -> R.string.training_source_gym
    TrainingSource.PROGRAM -> R.string.training_source_program
    TrainingSource.ACTIVITY -> R.string.training_source_activity
}
