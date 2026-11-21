package com.javargasp.tiominutermvi.domain.traininglog

import com.javargasp.tiominutermvi.domain.repository.GymRoutineRepository
import com.javargasp.tiominutermvi.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** The gym and daily names this device knows, for screens that name workouts (the feed). */
fun interface WorkoutNameCatalogSource {
    fun observeCatalog(): Flow<WorkoutNameCatalog>

    companion object {
        val Empty = WorkoutNameCatalogSource { flowOf(WorkoutNameCatalog.EMPTY) }
    }
}

/**
 * Builds the [WorkoutNameCatalog] from this device's catalogs (Room, through the repositories)
 * and the program feature's workouts ([programs]). A catalog that can't be read leaves its part
 * empty, so the names fall back as [WorkoutNameResolver] says.
 */
class WorkoutNameCatalogs(
    private val gymRoutines: GymRoutineRepository,
    private val dailyRoutines: WorkoutRepository,
    private val programs: ProgramWorkoutTitleCodes
) : WorkoutNameCatalogSource {

    override fun observeCatalog(): Flow<WorkoutNameCatalog> =
        combine(gymRoutines.observeGymRoutines(), flow { emit(readDailyRoutines()) }) { gym, daily ->
            WorkoutNameCatalog.of(gym, daily)
        }

    /** [catalog] plus the program workout of every PROGRAM entry of [entries] (one lookup each). */
    suspend fun withProgramsOf(catalog: WorkoutNameCatalog, entries: List<TrainingLogEntry>): WorkoutNameCatalog {
        val refIds = entries.filter { it.source == TrainingSource.PROGRAM }.mapNotNull { it.refId }.distinct()
        if (refIds.isEmpty()) return catalog
        val codes = refIds.mapNotNull { refId -> programs.safeTitleCodeOf(refId)?.let { refId to it } }.toMap()
        return catalog.copy(programWorkouts = codes)
    }

    private suspend fun readDailyRoutines() = try {
        dailyRoutines.getRoutines()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("WorkoutNames: daily catalog not available (${e.javaClass.simpleName})")
        emptyList()
    }
}

/** [ProgramWorkoutTitleCodes.titleCodeOf] that never throws: a failure is "not known". */
suspend fun ProgramWorkoutTitleCodes.safeTitleCodeOf(refId: String): ProgramTitleCode? = try {
    titleCodeOf(refId)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    println("WorkoutNames: no program workout for an entry (${e.javaClass.simpleName})")
    null
}

/** The feed post's title key of [entry] (PROGRAM looked up in [this]). */
suspend fun ProgramWorkoutTitleCodes.titleKeyOf(entry: TrainingLogEntry): String? {
    val code = if (entry.source == TrainingSource.PROGRAM) entry.refId?.let { safeTitleCodeOf(it) } else null
    return WorkoutNameResolver.titleKeyOf(entry, code)
}
