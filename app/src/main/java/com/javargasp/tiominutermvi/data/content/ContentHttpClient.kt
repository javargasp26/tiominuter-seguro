package com.javargasp.tiominutermvi.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads content files; faked in tests. */
interface ContentHttpClient {
    /**
     * GETs [url] (HTTPS only) and returns the body.
     * @throws IOException on any failure, a status other than 200, or a body longer than [maxBytes]
     */
    suspend fun get(url: String, maxBytes: Long): ByteArray
}

/** [ContentHttpClient] over [HttpURLConnection], on [Dispatchers.IO]. */
class HttpUrlConnectionContentHttpClient(
    private val timeoutMs: Int = TIMEOUT_MS
) : ContentHttpClient {

    override suspend fun get(url: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw IOException("Only HTTPS is allowed: $url")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status for $url")
            if (connection.contentLengthLong > maxBytes) throw IOException("$url is larger than $maxBytes bytes")
            connection.inputStream.use { readAtMost(it, maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Reads [input] to the end; throws when it has more than [maxBytes] bytes. */
internal fun readAtMost(input: InputStream, maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IOException("Body is larger than $maxBytes bytes")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
