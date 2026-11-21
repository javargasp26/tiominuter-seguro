package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.content.ContentJsonFile
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import kotlinx.serialization.builtins.ListSerializer

/**
 * [DailyRemoteDataSource] that treats the content JSON files as fake API endpoints, read through
 * the [ContentSource] (the downloaded version, else the bundled one).
 * Will be replaced by a real backend.
 *
 * @param simulatedLatencyMs Delay applied to every fetch to mimic network latency
 */
class AssetDailyRemoteDataSource(
    private val contentSource: ContentSource,
    private val simulatedLatencyMs: Long = 0
) : DailyRemoteDataSource {

    override suspend fun fetchDailyRoutines(): List<DailyRoutineDto> =
        contentSource.fetchEndpoint(DAILY_ROUTINES_CONTENT, simulatedLatencyMs)

    override suspend fun fetchExercises(): List<ExerciseDto> =
        contentSource.fetchExerciseCatalog(simulatedLatencyMs)

    companion object {
        const val DAILY_ROUTINES_FILE = "routines.json"

        val DAILY_ROUTINES_CONTENT =
            ContentJsonFile(DAILY_ROUTINES_FILE, ListSerializer(DailyRoutineDto.serializer()), remoteJson)
    }
}
