package com.javargasp.tiominutermvi.domain.program.model

/** User data a program is generated from. [focus] picks the split; it defaults to BALANCED. */
data class ProgramProfile(
    val age: Int,
    val weightKg: Double,
    val heightCm: Int,
    val level: ProgramLevel,
    val goal: ProgramGoal,
    val location: ProgramLocation,
    val weeks: Int,
    val daysPerWeek: Int,
    val focus: ProgramFocus = ProgramFocus.DEFAULT
) {
    companion object {
        val AGE_RANGE = 16..90
        val WEIGHT_RANGE_KG = 30.0..250.0
        val HEIGHT_RANGE_CM = 120..230
        val WEEKS_RANGE = 4..12
        val DAYS_PER_WEEK_RANGE = 2..5
        const val DEFAULT_WEEKS = 6
        const val DEFAULT_DAYS_PER_WEEK = 3
    }
}
