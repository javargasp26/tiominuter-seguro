package com.javargasp.tiominutermvi.ui.program.create

import com.javargasp.tiominutermvi.domain.program.generator.computeBmi
import com.javargasp.tiominutermvi.domain.program.generator.isLowImpact
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.ui.base.MviState

/** Why a form field is invalid. */
enum class ProgramFieldError { REQUIRED, OUT_OF_RANGE }

/**
 * Create form. Text fields keep what the user typed; the parsed values and errors
 * are derived. Errors show once a field has text, or for every field after
 * Preview/Create was tapped with an invalid form ([showAllErrors]).
 */
data class ProgramCreateState(
    val ageText: String = "",
    val weightText: String = "",
    val heightText: String = "",
    val level: ProgramLevel = ProgramLevel.BEGINNER,
    val goal: ProgramGoal = ProgramGoal.GAIN_MUSCLE,
    val focus: ProgramFocus = ProgramFocus.DEFAULT,
    val location: ProgramLocation = ProgramLocation.HOME,
    val weeks: Int = ProgramProfile.DEFAULT_WEEKS,
    val daysPerWeek: Int = ProgramProfile.DEFAULT_DAYS_PER_WEEK,
    val showAllErrors: Boolean = false,
    val impactRules: ImpactRules? = null,
    /** Week 1 of the previewed program; null when the preview is closed. */
    val preview: List<ProgramWorkout>? = null,
    val exercises: Map<Int, ProgramExercise> = emptyMap(),
    val isWorking: Boolean = false,
    val createFailed: Boolean = false
) : MviState {

    val age: Int? get() = ageText.toIntOrNull()?.takeIf { it in ProgramProfile.AGE_RANGE }
    val weightKg: Double? get() = parseDecimal(weightText)?.takeIf { it in ProgramProfile.WEIGHT_RANGE_KG }
    val heightCm: Int? get() = heightText.toIntOrNull()?.takeIf { it in ProgramProfile.HEIGHT_RANGE_CM }

    val ageError: ProgramFieldError? get() = fieldError(ageText, age != null)
    val weightError: ProgramFieldError? get() = fieldError(weightText, weightKg != null)
    val heightError: ProgramFieldError? get() = fieldError(heightText, heightCm != null)

    /** The profile when every field is valid. */
    val profile: ProgramProfile?
        get() {
            val age = age ?: return null
            val weight = weightKg ?: return null
            val height = heightCm ?: return null
            return ProgramProfile(age, weight, height, level, goal, location, weeks, daysPerWeek, focus)
        }

    val bmi: Double?
        get() {
            val weight = weightKg ?: return null
            val height = heightCm ?: return null
            return computeBmi(weight, height)
        }

    /** The plan will use low-impact exercises (BMI or age rule). */
    val isLowImpact: Boolean
        get() {
            val rules = impactRules ?: return false
            val profile = profile ?: return false
            return isLowImpact(profile, rules)
        }

    /** BMI under 18.5 with "Lose fat": suggest "Gain muscle". */
    val showUnderweightWarning: Boolean
        get() {
            val bmi = bmi ?: return false
            return goal == ProgramGoal.LOSE_FAT && bmi < UNDERWEIGHT_BMI
        }

    private fun fieldError(text: String, valid: Boolean): ProgramFieldError? = when {
        valid -> null
        text.isEmpty() -> if (showAllErrors) ProgramFieldError.REQUIRED else null
        else -> ProgramFieldError.OUT_OF_RANGE
    }

    companion object {
        const val UNDERWEIGHT_BMI = 18.5

        /** Accepts "72.5" and "72,5". */
        fun parseDecimal(text: String): Double? = text.replace(',', '.').toDoubleOrNull()
    }
}
