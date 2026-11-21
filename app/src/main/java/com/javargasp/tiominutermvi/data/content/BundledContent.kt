package com.javargasp.tiominutermvi.data.content

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest

/** The content bundled in the APK (`content/v1` is the assets folder). */
interface BundledContent {
    /** Opens the bundled file at [path] ("exercises.json"), or null when the APK doesn't have it. */
    fun open(path: String): InputStream?

    /** File names in the bundled `animations/` folder. */
    fun animationNames(): Set<String>
}

/** [BundledContent] read from the APK assets. */
class AssetBundledContent(private val assets: AssetManager) : BundledContent {

    private val animations: Set<String> by lazy {
        assets.list(ContentRules.ANIMATIONS_DIR.trimEnd('/'))?.toSet().orEmpty()
    }

    override fun open(path: String): InputStream? =
        try {
            assets.open(path)
        } catch (e: FileNotFoundException) {
            null
        }

    override fun animationNames(): Set<String> = animations
}

/**
 * sha256 of the bundled JSON files, computed once per process (they are small).
 *
 * @param jsonPaths the content JSON files the app reads ([ContentJsonFile.path])
 */
class BundledContentHashes(
    private val bundled: BundledContent,
    jsonPaths: Collection<String>
) {
    private val paths = jsonPaths.distinct().sorted()

    private val hashes: Map<String, String> by lazy {
        paths.mapNotNull { path -> bundled.open(path)?.use { path to sha256Hex(it) } }.toMap()
    }

    /** sha256 of the bundled [path], or null when it is not bundled. */
    fun sha256(path: String): String? = hashes[path]

    /** One hash of every bundled JSON file: it changes when an app update changes any of them. */
    val fingerprint: String by lazy {
        sha256Hex(paths.joinToString("\n") { "$it:${hashes[it] ?: "-"}" }.toByteArray())
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
