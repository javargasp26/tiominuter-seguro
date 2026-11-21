package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.ProgramWorkoutTitleCodes
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogs
import com.javargasp.tiominutermvi.ui.components.WorkoutNameTexts
import com.javargasp.tiominutermvi.ui.components.programTitleRes
import com.javargasp.tiominutermvi.ui.components.sourceLabelRes
import com.javargasp.tiominutermvi.ui.logactivity.activityTypeLabel
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The app's real string resources in one language, read from `src/main/res/<folder>/strings.xml`
 * (unit tests run from the app module, like NeutralLanguageTest), by resource id.
 */
class StringResources(folder: String) {
    private val byName: Map<String, String> = run {
        val file = File("src/main/res/$folder/strings.xml")
        require(file.isFile) { "run from the app module: $file" }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val node = nodes.item(i) as Element
            node.getAttribute("name") to node.textContent.replace("\\'", "'").replace("\\\"", "\"")
        }
    }

    fun has(name: String): Boolean = name in byName

    fun raw(name: String): String = byName[name] ?: error("no string $name")

    fun get(id: Int, vararg args: Any): String = String.format(Locale.ROOT, raw(nameOf(id)), *args)

    companion object {
        val ENGLISH by lazy { StringResources("values") }
        val SPANISH by lazy { StringResources("values-es") }

        private val names: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        fun nameOf(id: Int): String = names[id] ?: error("no string resource $id")
    }
}

/** [WorkoutNameTexts] over the real strings of English ("en") or Spanish ("es"). */
class XmlWorkoutNameTexts(override val language: String) : WorkoutNameTexts {
    private val strings = if (language == "es") StringResources.SPANISH else StringResources.ENGLISH

    override fun day(dayNumber: Int) = strings.get(R.string.workout_name_day, dayNumber)
    override fun dayWithTitle(day: String, title: String) = strings.get(R.string.workout_name_day_with_title, day, title)
    override fun program(code: ProgramTitleCode) = strings.get(programTitleRes(code))
    override fun activity(type: ActivityType) = strings.get(activityTypeLabel(type))
    override fun source(source: TrainingSource) = strings.get(sourceLabelRes(source))

    companion object {
        val EN = XmlWorkoutNameTexts("en")
        val ES = XmlWorkoutNameTexts("es")
    }
}

/** The daily catalog only (the name catalog reads nothing else of it). */
class FakeDailyCatalog(var routines: List<Routine> = emptyList(), var fails: Boolean = false) : WorkoutRepository {
    override suspend fun getRoutines(): List<Routine> = if (fails) error("no catalog") else routines
    override suspend fun getRoutine(routineId: Int): Routine? = routines.firstOrNull { it.routineId == routineId }
    override suspend fun getExercises(): List<Exercise> = emptyList()
    override suspend fun getExercise(exerciseId: Int): Exercise? = null
    override suspend fun refresh(): Result<Unit> = Result.success(Unit)
}

/** The program workouts by refId, as the program feature would answer (a missing key: deleted). */
class FakeProgramTitleCodes(val codes: MutableMap<String, ProgramTitleCode> = mutableMapOf()) : ProgramWorkoutTitleCodes {
    val lookups = mutableListOf<String>()

    override suspend fun titleCodeOf(refId: String): ProgramTitleCode? {
        lookups += refId
        return codes[refId]
    }
}

fun dailyRoutine(day: Int, title: String? = null, titleEs: String? = null) =
    Routine(routineId = day, routineDay = "Day $day", title = title, exercises = emptyList(), titleEs = titleEs)

fun workoutNameCatalogs(
    gym: GymRoutineRepository,
    daily: WorkoutRepository = FakeDailyCatalog(),
    programs: ProgramWorkoutTitleCodes = ProgramWorkoutTitleCodes.None
) = WorkoutNameCatalogs(gym, daily, programs)
