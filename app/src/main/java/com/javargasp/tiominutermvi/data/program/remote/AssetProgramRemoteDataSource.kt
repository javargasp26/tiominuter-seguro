package com.javargasp.tiominutermvi.data.program.remote

import com.javargasp.tiominutermvi.data.content.ContentJsonFile
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.content.read
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramExerciseDto
import com.javargasp.tiominutermvi.data.program.remote.dto.ProgramTemplatesDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [ProgramRemoteDataSource] that treats two content files as fake endpoints.
 * Reads them through the core [ContentSource] (the downloaded version, else the bundled one)
 * and decodes them with a lenient [Json] on [Dispatchers.IO].
 */
class AssetProgramRemoteDataSource(
    private val contentSource: ContentSource
) : ProgramRemoteDataSource {

    override suspend fun fetchExercises(): List<ProgramExerciseDto> =
        withContext(Dispatchers.IO) { contentSource.read(EXERCISES_CONTENT) }

    override suspend fun fetchTemplates(): ProgramTemplatesDto =
        withContext(Dispatchers.IO) { contentSource.read(TEMPLATES_CONTENT) }

    companion object {
        const val EXERCISES_FILE = "program_exercises.json"
        const val TEMPLATES_FILE = "program_templates.json"

        /** Lenient Json for the program assets: comments, trailing commas and unknown keys allowed. */
        @OptIn(ExperimentalSerializationApi::class)
        val programJson = Json {
            allowComments = true
            allowTrailingComma = true
            ignoreUnknownKeys = true
        }

        /** The program files with their decoders (also used by the content sync to check a download). */
        val EXERCISES_CONTENT =
            ContentJsonFile(EXERCISES_FILE, ListSerializer(ProgramExerciseDto.serializer()), programJson)
        val TEMPLATES_CONTENT = ContentJsonFile(TEMPLATES_FILE, ProgramTemplatesDto.serializer(), programJson)
    }
}
