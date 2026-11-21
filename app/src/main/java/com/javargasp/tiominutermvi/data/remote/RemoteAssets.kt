package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.content.ContentJsonFile
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.content.read
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Exercise catalog endpoint, shared by the gym and daily-routine remote sources. */
const val EXERCISES_FILE = "exercises.json"

/**
 * Json for every simulated remote endpoint; separate from the Koin `Json` used by
 * the daily-routine repository so the .jsonc files can contain comments and
 * trailing commas.
 */
@OptIn(ExperimentalSerializationApi::class)
val remoteJson = Json {
    allowComments = true
    allowTrailingComma = true
    ignoreUnknownKeys = true
}

/** [EXERCISES_FILE] with its decoder (also used by the content sync to check a download). */
val EXERCISES_CONTENT = ContentJsonFile(EXERCISES_FILE, ListSerializer(ExerciseDto.serializer()), remoteJson)

/**
 * Fetches one fake endpoint: waits [simulatedLatencyMs], reads [file] through the
 * [ContentSource] and decodes it, on [Dispatchers.IO].
 */
internal suspend fun <T> ContentSource.fetchEndpoint(
    file: ContentJsonFile<T>,
    simulatedLatencyMs: Long
): T = withContext(Dispatchers.IO) {
    delay(simulatedLatencyMs)
    read(file)
}

/** Fetches the exercise catalog endpoint ([EXERCISES_FILE]). */
internal suspend fun ContentSource.fetchExerciseCatalog(simulatedLatencyMs: Long): List<ExerciseDto> =
    fetchEndpoint(EXERCISES_CONTENT, simulatedLatencyMs)
