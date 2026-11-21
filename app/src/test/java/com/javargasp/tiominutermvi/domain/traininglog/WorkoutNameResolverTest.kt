package com.javargasp.tiominutermvi.domain.traininglog

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts.Companion.EN
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts.Companion.ES
import com.javargasp.tiominutermvi.testutil.dailyRoutine
import com.javargasp.tiominutermvi.ui.components.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WorkoutNameResolver + its rendering, with the app's real English and Spanish strings: every
 * source × language × known / unknown / removed / old data, history entries and feed posts.
 */
class WorkoutNameResolverTest {

    private val catalog = WorkoutNameCatalog.of(
        gymRoutines = listOf(
            GymRoutine(64, "Pull Day", emptyList(), routineNameEs = "Día de tirón"),
            GymRoutine(65, "Push Day", emptyList(), routineNameEs = null)
        ),
        dailyRoutines = listOf(dailyRoutine(5), dailyRoutine(6, title = "Core", titleEs = "Abdomen"), dailyRoutine(7, title = "Mobility"))
    ).copy(programWorkouts = mapOf("program:3:1:0" to ProgramTitleCode.UPPER_A, "program:3:1:1" to ProgramTitleCode.TREADMILL))

    private fun entry(source: TrainingSource, refId: String?, title: String, activity: ActivityType? = null) = TrainingLogEntry(
        id = 1,
        uuid = "u",
        dateLocal = "2026-10-19",
        completedAt = 0,
        source = source,
        refId = refId,
        title = title,
        durationSeconds = 1800,
        activityType = activity?.code,
        createdAt = 0,
        updatedAt = 0
    )

    private fun TrainingLogEntry.named(texts: XmlWorkoutNameTexts) = WorkoutNameResolver.nameOf(this, catalog).text(texts)

    private fun both(entry: TrainingLogEntry) = entry.named(EN) to entry.named(ES)

    // ------------------------------------------------------------------ GYM

    @Test
    fun `gym - the catalog routine in each language, recorded in English`() {
        assertEquals("Pull Day" to "Día de tirón", both(entry(TrainingSource.GYM, "gym:64", "Pull Day")))
    }

    @Test
    fun `gym - a routine without a Spanish name shows its English name in Spanish`() {
        assertEquals("Push Day" to "Push Day", both(entry(TrainingSource.GYM, "gym:65", "Push Day")))
    }

    @Test
    fun `gym - a removed routine, a malformed refId or old data keep the stored title`() {
        assertEquals("Old routine" to "Old routine", both(entry(TrainingSource.GYM, "gym:99", "Old routine")))
        assertEquals("Pull Day" to "Pull Day", both(entry(TrainingSource.GYM, "gym:x", "Pull Day")))
        assertEquals("Pull Day" to "Pull Day", both(entry(TrainingSource.GYM, null, "Pull Day")))
        assertEquals("Gym routine" to "Rutina de gimnasio", both(entry(TrainingSource.GYM, null, "")))
    }

    // ------------------------------------------------------------------ DAILY

    @Test
    fun `daily - the day number in each language, never the stored English text`() {
        assertEquals("Day 5" to "Día 5", both(entry(TrainingSource.DAILY, "daily:5", "Day 5")))
    }

    @Test
    fun `daily - a routine with a title shows it, in Spanish only its Spanish title`() {
        assertEquals("Day 6 · Core" to "Día 6 · Abdomen", both(entry(TrainingSource.DAILY, "daily:6", "Day 6 · Core")))
        assertEquals("Day 7 · Mobility" to "Día 7", both(entry(TrainingSource.DAILY, "daily:7", "Day 7 · Mobility")))
    }

    @Test
    fun `daily - a routine not in the catalog is still its day, a malformed refId or old data keep the stored title`() {
        assertEquals("Day 30" to "Día 30", both(entry(TrainingSource.DAILY, "daily:30", "Day 30")))
        assertEquals("Day 1" to "Day 1", both(entry(TrainingSource.DAILY, null, "Day 1")))
        assertEquals("Day 0" to "Day 0", both(entry(TrainingSource.DAILY, "daily:0", "Day 0")))
        assertEquals("Day 1" to "Day 1", both(entry(TrainingSource.DAILY, "gym:1", "Day 1")))
    }

    // ------------------------------------------------------------------ PROGRAM

    @Test
    fun `program - the workout of the refId, whatever language recorded it`() {
        assertEquals("Upper A" to "Tren superior A", both(entry(TrainingSource.PROGRAM, "program:3:1:0", "Tren superior A")))
        assertEquals("Upper A" to "Tren superior A", both(entry(TrainingSource.PROGRAM, "program:3:1:0", "Upper A")))
        assertEquals("Treadmill" to "Cinta de correr", both(entry(TrainingSource.PROGRAM, "program:3:1:1", "Treadmill")))
    }

    @Test
    fun `program - a deleted program or old data keep the stored title`() {
        assertEquals("Tren superior A" to "Tren superior A", both(entry(TrainingSource.PROGRAM, "program:4:1:0", "Tren superior A")))
        assertEquals("Full body A" to "Full body A", both(entry(TrainingSource.PROGRAM, null, "Full body A")))
    }

    // ------------------------------------------------------------------ ACTIVITY

    @Test
    fun `activity - its type in each language, OTHER shows the custom name`() {
        assertEquals("Running" to "Correr", both(entry(TrainingSource.ACTIVITY, null, "", ActivityType.RUNNING)))
        assertEquals("Padel" to "Padel", both(entry(TrainingSource.ACTIVITY, null, "Padel", ActivityType.OTHER)))
        assertEquals(EN.activity(ActivityType.OTHER) to ES.activity(ActivityType.OTHER), both(entry(TrainingSource.ACTIVITY, null, "", ActivityType.OTHER)))
    }

    @Test
    fun `activity - an unknown type code keeps the stored title, or the source`() {
        val unknown = entry(TrainingSource.ACTIVITY, null, "Climbing").copy(activityType = "FUTURE_TYPE")
        assertEquals("Climbing" to "Climbing", both(unknown))
        assertEquals("Activity" to ES.source(TrainingSource.ACTIVITY), both(unknown.copy(title = "")))
    }

    // ------------------------------------------------------------------ Feed posts

    private fun post(titleKey: String?, source: TrainingSource, title: String, activity: ActivityType? = null) =
        WorkoutNameResolver.nameOfPost(titleKey, source, activity, title, catalog).let { it.text(EN) to it.text(ES) }

    @Test
    fun `a post is named in the reader's language from its key, whoever wrote it`() {
        // An English author's posts
        assertEquals("Pull Day" to "Día de tirón", post("gym/64", TrainingSource.GYM, "Pull Day"))
        assertEquals("Day 5" to "Día 5", post("daily/5", TrainingSource.DAILY, "Day 5"))
        assertEquals("Upper A" to "Tren superior A", post("program/UPPER_A", TrainingSource.PROGRAM, "Upper A"))
        // A Spanish author's posts
        assertEquals("Pull Day" to "Día de tirón", post("gym/64", TrainingSource.GYM, "Día de tirón"))
        assertEquals("Lower B" to "Tren inferior B", post("program/LOWER_B", TrainingSource.PROGRAM, "Tren inferior B"))
        assertEquals("Day 6 · Core" to "Día 6 · Abdomen", post("daily/6", TrainingSource.DAILY, "Día 6"))
    }

    @Test
    fun `a post without a usable key shows its title`() {
        assertEquals("Pull Day" to "Pull Day", post(null, TrainingSource.GYM, "Pull Day"))
        assertEquals("Old routine" to "Old routine", post("gym/99", TrainingSource.GYM, "Old routine"))
        assertEquals("Hyrox" to "Hyrox", post("program/HYROX", TrainingSource.PROGRAM, "Hyrox"))
        assertEquals("Pull Day" to "Pull Day", post("daily/5", TrainingSource.GYM, "Pull Day"))
        assertEquals("Pull Day" to "Pull Day", post("gym/064", TrainingSource.GYM, "Pull Day"))
        assertEquals("Program" to "Programa", post("program/??", TrainingSource.PROGRAM, ""))
    }

    @Test
    fun `an activity post is named by its type and ignores any key`() {
        assertEquals("Running" to "Correr", post(null, TrainingSource.ACTIVITY, "", ActivityType.RUNNING))
        assertEquals("Running" to "Correr", post("gym/64", TrainingSource.ACTIVITY, "", ActivityType.RUNNING))
        assertEquals("Padel" to "Padel", post(null, TrainingSource.ACTIVITY, "Padel", ActivityType.OTHER))
    }

    // ------------------------------------------------------------------ Title keys

    @Test
    fun `the post key of each source`() {
        assertEquals("gym/64", WorkoutNameResolver.titleKeyOf(entry(TrainingSource.GYM, "gym:64", "Pull Day"), null))
        assertEquals("daily/5", WorkoutNameResolver.titleKeyOf(entry(TrainingSource.DAILY, "daily:5", "Day 5"), null))
        assertEquals(
            "program/UPPER_A",
            WorkoutNameResolver.titleKeyOf(entry(TrainingSource.PROGRAM, "program:3:1:0", "Upper A"), ProgramTitleCode.UPPER_A)
        )
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.ACTIVITY, null, "", ActivityType.RUNNING), null))
    }

    @Test
    fun `no key when nothing is known, never one the rules would refuse`() {
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.PROGRAM, "program:4:1:0", "Upper A"), null))
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.GYM, null, "Pull Day"), null))
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.GYM, "gym:0", "Pull Day"), null))
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.DAILY, "daily:0", "Day 0"), null))
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.DAILY, "daily:x", "Day"), null))
        assertNull(WorkoutNameResolver.titleKeyOf(entry(TrainingSource.GYM, "gym:${Int.MAX_VALUE}", "Big"), null))
    }

    @Test
    fun `the key format is as strict as the rules`() {
        val valid = listOf(
            "gym/1" to TrainingSource.GYM, "gym/999999999" to TrainingSource.GYM, "daily/30" to TrainingSource.DAILY,
            "program/UPPER_A" to TrainingSource.PROGRAM, "program/${"A".repeat(72)}" to TrainingSource.PROGRAM
        )
        valid.forEach { (key, source) -> assertEquals(key, WorkoutTitleKey.parse(key, source)?.value) }

        val invalid = listOf(
            "", "gym/", "gym/0", "gym/064", "gym/x", "gym/1234567890", "GYM/64", "gym:64", " gym/64", "gym/64 ",
            "gym/64/extra", "daily/5", "program/UPPER_A"
        )
        invalid.forEach { assertNull(it, WorkoutTitleKey.parse(it, TrainingSource.GYM)) }
        listOf("program/upper_a", "program/", "program/1A", "program/_A", "program/A-B", "program/${"A".repeat(73)}", "gym/1")
            .forEach { assertNull(it, WorkoutTitleKey.parse(it, TrainingSource.PROGRAM)) }
        assertNull(WorkoutTitleKey.parse("gym/64", TrainingSource.ACTIVITY))
        assertNull(WorkoutTitleKey.parse(null, TrainingSource.GYM))
    }

    // ------------------------------------------------------------------ Program codes

    @Test
    fun `every program code is a valid key with a title in both languages`() {
        ProgramTitleCode.entries.forEach { code ->
            val key = WorkoutTitleKey.Program(code.name)
            assertEquals(key.value, WorkoutTitleKey.valueOrNull(key))
            assertTrue(code.name, EN.program(code).isNotBlank() && ES.program(code).isNotBlank())
            assertEquals(code, ProgramTitleCode.fromCode(code.name))
        }
        assertNull(ProgramTitleCode.fromCode("upper_a"))
    }

    @Test
    fun `program codes never change (posts in the feed use them)`() {
        assertEquals(
            listOf(
                "FULL_BODY_A", "FULL_BODY_B", "FULL_BODY_C", "UPPER_A", "UPPER_B", "LOWER_A", "LOWER_B", "PUSH", "PULL",
                "LEGS", "FULL_BODY", "UPPER", "LOWER", "UPPER_C", "LOWER_C", "OUTDOOR_RUN", "BRISK_WALK", "TREADMILL",
                "HOME_CIRCUIT"
            ),
            ProgramTitleCode.entries.map { it.name }
        )
    }
}
