package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.content.ContentJsonFile
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.GymRoutineDto
import kotlinx.serialization.builtins.ListSerializer

/**
 * [GymRemoteDataSource] that treats the content JSON files as fake API endpoints, read through
 * the [ContentSource] (the downloaded version, else the bundled one).
 * Will be replaced by a real backend.
 *
 * @param simulatedLatencyMs Delay applied to every fetch to mimic network latency
 */
class AssetGymRemoteDataSource(
    private val contentSource: ContentSource,
    private val simulatedLatencyMs: Long = 0
) : GymRemoteDataSource {

    override suspend fun fetchGymRoutines(): List<GymRoutineDto> =
        contentSource.fetchEndpoint(GYM_ROUTINES_CONTENT, simulatedLatencyMs)

    override suspend fun fetchExercises(): List<ExerciseDto> =
        contentSource.fetchExerciseCatalog(simulatedLatencyMs)

    companion object {
        const val GYM_ROUTINES_FILE = "routines_for_gym.jsonc"

        val GYM_ROUTINES_CONTENT =
            ContentJsonFile(GYM_ROUTINES_FILE, ListSerializer(GymRoutineDto.serializer()), remoteJson)
    }
}
