package com.javargasp.tiominutermvi.ui.program.common

import java.util.Locale

/**
 * Pure text formatting for the Program screens. Numbers use one decimal and the
 * given [Locale], so Spanish shows a decimal comma ("33,3").
 */

/** completed / total × 100 with one decimal, e.g. 1 of 3 → "33.3". A total of 0 gives "0.0". */
fun formatPercent(completed: Int, total: Int, locale: Locale): String {
    val percent = if (total <= 0) 0.0 else completed * 100.0 / total
    return formatOneDecimal(percent, locale)
}

fun formatOneDecimal(value: Double, locale: Locale): String = String.format(locale, "%.1f", value)

/** Labels used by [programExerciseSummary], filled from string resources by the UI. */
data class ProgramSummaryLabels(
    val sets: String,
    val reps: String,
    val time: String,
    val perSide: String,
    val seconds: (Int) -> String
)

/**
 * "Sets: 4 · Reps: 10, 10, 10, 10" for REPS, "Sets: 2 · Time: 30s, 45s" for TIME.
 * [values] holds one value per set. With [perSide] the values are per side:
 * "Sets: 3 · Time: 40s, 40s, 40s per side".
 */
fun programExerciseSummary(
    isTime: Boolean,
    values: List<Int>,
    labels: ProgramSummaryLabels,
    perSide: Boolean = false
): String {
    val detail = if (isTime) {
        "${labels.time}: " + values.joinToString(", ") { labels.seconds(it) }
    } else {
        "${labels.reps}: " + values.joinToString(", ")
    }
    val side = if (perSide) " ${labels.perSide}" else ""
    return "${labels.sets}: ${values.size} · $detail$side"
}
