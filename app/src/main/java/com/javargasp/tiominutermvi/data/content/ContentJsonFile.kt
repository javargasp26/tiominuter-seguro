package com.javargasp.tiominutermvi.data.content

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * A content JSON file the app reads, with the decoder it reads it with. The data sources decode
 * through it, and [ContentSyncer] activates a downloaded file only if the same decoder accepts it.
 * Every instance is registered in Koin (any qualifier); the set of paths is also the list of
 * top-level JSON files a manifest may contain.
 */
class ContentJsonFile<T>(
    val path: String,
    private val deserializer: DeserializationStrategy<T>,
    private val json: Json
) {
    fun decode(text: String): T = json.decodeFromString(deserializer, text)
}
