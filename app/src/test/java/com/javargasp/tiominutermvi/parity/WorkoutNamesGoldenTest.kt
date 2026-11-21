package com.javargasp.tiominutermvi.parity

import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.DailyRoutineNames
import com.javargasp.tiominutermvi.domain.traininglog.GymRoutineNames
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutTitleKey
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.ui.components.text
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/**
 * shared/golden/workout_names.json: WorkoutNameResolver (history entries and feed posts), the
 * titleKey of an entry's post and WorkoutTitleKey parsing, with the names rendered in English and
 * Spanish from the app's real strings (`WorkoutName.text`). Every source × hit / miss / fallback,
 * blank and odd titles, and refIds / keys at the format's edges (leading zeros, signs, hex,
 * whitespace, overflow, length).
 */
class WorkoutNamesGoldenTest {

    private val catalog = WorkoutNameCatalog(
        gymRoutines = mapOf(
            64 to GymRoutineNames("Pull Day", "Día de tirón"),
            65 to GymRoutineNames("Push Day", null),
            66 to GymRoutineNames("Leg Day", "  ")
        ),
        dailyRoutines = mapOf(
            3 to DailyRoutineNames(null, null),
            5 to DailyRoutineNames("Core", "Abdomen"),
            7 to DailyRoutineNames("Mobility", null),
            8 to DailyRoutineNames(null, "Movilidad")
        ),
        programWorkouts = mapOf(
            "program:p1:1:0" to ProgramTitleCode.FULL_BODY_A,
            "program:p1:1:1" to ProgramTitleCode.BRISK_WALK,
            "program:p2:3:2" to ProgramTitleCode.LOWER_C
        )
    )

    private val titles = listOf("Stored title", "", "   ", "﻿", " ", "Día 9 · Viejo")

    private val gymRefIds = listOf("gym:64", "gym:65", "gym:66", "gym:99", "gym:0064", "gym:+64", "gym:-64", "gym: 64", "gym:0x40", "gym:", "gym64", null, "daily:3")
    private val dailyRefIds = listOf(
        "daily:3", "daily:5", "daily:7", "daily:8", "daily:12", "daily:0", "daily:-3", "daily:007", "daily:+5",
        "daily:1234567890", "daily:99999999999", "daily: 5", "daily:0x5", "daily:", null, "gym:64"
    )
    private val programRefIds = listOf("program:p1:1:0", "program:p1:1:1", "program:p2:3:2", "program:gone:1:0", "program:p1:1", null)

    private class EntryCase(val source: TrainingSource, val refId: String?, val title: String, val activityType: String?)

    private fun entryCases(): List<EntryCase> = buildList {
        for (refId in gymRefIds) for (title in titles.take(3)) add(EntryCase(TrainingSource.GYM, refId, title, null))
        for (refId in dailyRefIds) for (title in titles.take(3)) add(EntryCase(TrainingSource.DAILY, refId, title, null))
        for (refId in programRefIds) for (title in titles.take(3)) add(EntryCase(TrainingSource.PROGRAM, refId, title, null))
        for (type in ActivityType.entries.map { it.code } + listOf("UNKNOWN_TYPE", null)) {
            for (title in titles) add(EntryCase(TrainingSource.ACTIVITY, null, title, type))
        }
        for (source in TrainingSource.entries) for (title in titles) add(EntryCase(source, null, title, null))
    }

    private val titleKeys = listOf(
        "gym/64", "gym/65", "gym/99", "gym/064", "gym/0", "gym/1234567890", "gym/123456789", "gym/-1", "gym/+64", "gym/ 64",
        "daily/3", "daily/5", "daily/12", "daily/0", "daily/05", "daily/123456789", "daily/1234567890",
        "program/FULL_BODY_A", "program/BRISK_WALK", "program/NEW_CODE_2030", "program/full_body_a", "program/_X", "program/9A",
        "program/A", "program/" + "A".repeat(72), "program/" + "A".repeat(73), "gym/64/extra", "weird", "", null
    )

    private fun nameJson(name: WorkoutName): JsonElement = when (name) {
        is WorkoutName.Gym -> obj("kind" to str("gym"), "name" to str(name.name), "nameEs" to str(name.nameEs))
        is WorkoutName.Daily -> obj("kind" to str("daily"), "dayNumber" to num(name.dayNumber), "title" to str(name.title), "titleEs" to str(name.titleEs))
        is WorkoutName.Program -> obj("kind" to str("program"), "code" to str(name.code.name))
        is WorkoutName.Activity -> obj("kind" to str("activity"), "type" to str(name.type.code))
        is WorkoutName.Stored -> obj("kind" to str("stored"), "text" to str(name.text))
        is WorkoutName.SourceOnly -> obj("kind" to str("sourceOnly"), "source" to str(name.source.name))
    }

    private fun rendered(name: WorkoutName): List<Pair<String, JsonElement>> = listOf(
        "name" to nameJson(name),
        "en" to str(name.text(XmlWorkoutNameTexts.EN)),
        "es" to str(name.text(XmlWorkoutNameTexts.ES))
    )

    @Test
    fun `workout names golden`() {
        val entries = entryCases().mapIndexed { index, case ->
            val entry = TrainingLogEntry(
                id = index + 1L, uuid = "e$index", dateLocal = "2026-10-21", completedAt = 0L, source = case.source,
                refId = case.refId, title = case.title, durationSeconds = 900, activityType = case.activityType,
                createdAt = 0L, updatedAt = 0L
            )
            val name = WorkoutNameResolver.nameOf(entry, catalog)
            val programCode = case.refId?.let { catalog.programWorkouts[it] }
            obj(
                "id" to str("entry #$index"),
                "kind" to str("entry"),
                "input" to obj(
                    "source" to str(case.source.name), "refId" to str(case.refId), "title" to str(case.title),
                    "activityType" to str(case.activityType)
                ),
                "output" to obj(*rendered(name).toTypedArray(), "titleKey" to str(WorkoutNameResolver.titleKeyOf(entry, programCode)))
            )
        }
        val posts = buildList {
            for (source in TrainingSource.entries) for (key in titleKeys) {
                for (title in listOf("Author title", "")) add(Triple(source, key, title))
            }
        }.mapIndexed { index, (source, key, title) ->
            val activityType = if (source == TrainingSource.ACTIVITY) listOf(ActivityType.RUNNING, ActivityType.OTHER, null)[index % 3] else null
            val name = WorkoutNameResolver.nameOfPost(key, source, activityType, title, catalog)
            obj(
                "id" to str("post #$index"),
                "kind" to str("post"),
                "input" to obj(
                    "source" to str(source.name), "titleKey" to str(key), "title" to str(title),
                    "activityType" to str(activityType?.code)
                ),
                "output" to obj(
                    *rendered(name).toTypedArray(),
                    "parsedKey" to str(WorkoutTitleKey.parse(key, source)?.value)
                )
            )
        }
        Golden.verify(
            "workout_names",
            Golden.document(
                domain = "workout_names",
                generatedBy = "app/src/test/java/com/javargasp/tiominutermvi/parity/WorkoutNamesGoldenTest.kt",
                description = "WorkoutNameResolver.nameOf / nameOfPost / titleKeyOf, WorkoutTitleKey.parse, and " +
                    "WorkoutName.text in English and Spanish (the app's strings.xml)",
                extra = mapOf(
                    "catalog" to obj(
                        "gymRoutines" to obj(*catalog.gymRoutines.map { (id, n) -> "$id" to obj("name" to str(n.name), "nameEs" to str(n.nameEs)) }.toTypedArray()),
                        "dailyRoutines" to obj(*catalog.dailyRoutines.map { (id, n) -> "$id" to obj("title" to str(n.title), "titleEs" to str(n.titleEs)) }.toTypedArray()),
                        "programWorkouts" to obj(*catalog.programWorkouts.map { (refId, code) -> refId to str(code.name) }.toTypedArray())
                    )
                ),
                cases = entries + posts
            )
        )
    }
}
