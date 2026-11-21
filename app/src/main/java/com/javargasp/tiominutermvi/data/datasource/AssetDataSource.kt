package com.javargasp.tiominutermvi.data.datasource

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Data source for loading JSON files from assets.
 */
class AssetDataSource(
    private val context: Context,
    private val json: Json
) {
    /**
     * Load and parse a JSON file from assets.
     * @param fileName The name of the file in the assets directory
     * @param deserializer The deserialization strategy for the type
     * @return The deserialized object
     * @throws IOException If the file cannot be read
     * @throws kotlinx.serialization.SerializationException If the JSON cannot be parsed
     */
    suspend fun <T> loadFromAssets(fileName: String, deserializer: DeserializationStrategy<T>): T {
        return withContext(Dispatchers.IO) {
            val jsonString = readText(fileName)
            json.decodeFromString(deserializer, jsonString)
        }
    }

    /**
     * Read a file from assets as raw text, without parsing it.
     * @param fileName The name of the file in the assets directory
     * @return The file contents
     * @throws IOException If the file cannot be read
     */
    suspend fun readText(fileName: String): String {
        return withContext(Dispatchers.IO) {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        }
    }
}