package com.javargasp.tiominutermvi.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The published manifest (`manifest.json` next to the content on Hosting, written by
 * `scripts/build_manifest.py`): every file of the version with its size and sha256.
 */
@Serializable
data class ContentManifest(
    val schemaVersion: Int,
    val contentVersion: String,
    val files: List<ContentFileEntry>
)

/** One content file: [path] relative to `content/v1` ("exercises.json", "animations/2_squat.gif"). */
@Serializable
data class ContentFileEntry(
    val path: String,
    val size: Long,
    val sha256: String
)

/**
 * The version this device has activated (`filesDir/content/active.json`), written last by an
 * activation so a half-applied version is never marked as active.
 *
 * @param bundleFingerprint [BundledContentHashes.fingerprint] of the APK the version was activated
 *   against; after an app update with other bundled JSON the cached JSON is ignored until the next sync.
 * @param files only the files served from the cache (stored as `objects/<sha256>`); every other file
 *   of the version is the bundled one.
 */
@Serializable
data class LocalContentManifest(
    val contentVersion: String,
    val bundleFingerprint: String,
    val files: List<ContentFileEntry>
)

/** Json of both manifests: unknown keys allowed, so the publisher can add fields. */
internal val contentManifestJson = Json { ignoreUnknownKeys = true }

/** Limits and path rules of the downloaded content. */
object ContentRules {
    const val SCHEMA_VERSION = 1
    const val MANIFEST_FILE = "manifest.json"
    const val ANIMATIONS_DIR = "animations/"

    /** Largest content file a sync accepts. */
    const val MAX_FILE_BYTES = 5L * 1024 * 1024

    /** Largest manifest a sync accepts. */
    const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024

    private val ANIMATION_NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.gif")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val CONTENT_VERSION = Regex("[A-Za-z0-9._-]{1,64}")

    fun isAnimation(path: String): Boolean = path.startsWith(ANIMATIONS_DIR)

    fun animationName(path: String): String = path.removePrefix(ANIMATIONS_DIR)

    /**
     * Why [manifest] can't be used, or null when it can. One invalid entry rejects it all.
     * Allowed files: the top-level JSON files the app reads ([jsonPaths]) and `animations/<name>.gif`;
     * paths are relative, without `..`, each listed once; sizes up to [MAX_FILE_BYTES].
     */
    fun rejectionReason(manifest: ContentManifest, jsonPaths: Set<String>): String? {
        if (manifest.schemaVersion != SCHEMA_VERSION) return "unsupported schemaVersion ${manifest.schemaVersion}"
        if (!CONTENT_VERSION.matches(manifest.contentVersion)) return "invalid contentVersion"
        val seen = HashSet<String>()
        for (entry in manifest.files) {
            val path = entry.path
            when {
                !seen.add(path) -> return "duplicate path $path"
                !isAllowedPath(path, jsonPaths) -> return "path not allowed: $path"
                entry.size !in 0..MAX_FILE_BYTES -> return "invalid size ${entry.size} for $path"
                !SHA256.matches(entry.sha256) -> return "invalid sha256 for $path"
            }
        }
        return null
    }

    internal fun isAllowedPath(path: String, jsonPaths: Set<String>): Boolean {
        if (path.startsWith("/") || path.contains("..") || path.contains('\\')) return false
        return path in jsonPaths || (isAnimation(path) && ANIMATION_NAME.matches(animationName(path)))
    }
}
