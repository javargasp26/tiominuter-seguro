package com.javargasp.tiominutermvi.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ContentRulesTest {

    private val jsonPaths = setOf("exercises.json", "routines_for_gym.jsonc")
    private val sha = "a".repeat(64)

    private fun manifest(vararg entries: ContentFileEntry, schemaVersion: Int = 1, version: String = "20261020154542") =
        ContentManifest(schemaVersion, version, entries.toList())

    @Test
    fun `known JSON files and animations are allowed`() {
        assertTrue(ContentRules.isAllowedPath("exercises.json", jsonPaths))
        assertTrue(ContentRules.isAllowedPath("routines_for_gym.jsonc", jsonPaths))
        assertTrue(ContentRules.isAllowedPath("animations/100_outdoor_run.gif", jsonPaths))
        assertTrue(ContentRules.isAllowedPath("animations/118_cable_fly_v2.gif", jsonPaths))
    }

    @Test
    fun `other paths are not allowed`() {
        listOf(
            "../x", "/abs", "/exercises.json", "other/file.txt", "unknown.json", "animations/../exercises.json",
            "animations/a/b.gif", "animations/.gif", "animations/x.png", "animations\\x.gif", "manifest.json"
        ).forEach { assertFalse(it, ContentRules.isAllowedPath(it, jsonPaths)) }
    }

    @Test
    fun `a valid manifest has no rejection reason`() {
        assertNull(ContentRules.rejectionReason(manifest(ContentFileEntry("exercises.json", 10, sha)), jsonPaths))
    }

    @Test
    fun `schema, version, size, hash and duplicates are checked`() {
        val entry = ContentFileEntry("exercises.json", 10, sha)
        assertNotNull(ContentRules.rejectionReason(manifest(entry, schemaVersion = 2), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry, version = ""), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry, version = "../v"), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry.copy(size = ContentRules.MAX_FILE_BYTES + 1)), jsonPaths))
        assertNull(ContentRules.rejectionReason(manifest(entry.copy(size = ContentRules.MAX_FILE_BYTES)), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry.copy(size = -1)), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry.copy(sha256 = "ABC")), jsonPaths))
        assertNotNull(ContentRules.rejectionReason(manifest(entry, entry), jsonPaths))
    }

    @Test
    fun `readAtMost reads up to the limit and fails beyond it`() {
        assertEquals(5, readAtMost(ByteArrayInputStream(ByteArray(5)), 5).size)
        try {
            readAtMost(ByteArrayInputStream(ByteArray(6)), 5)
            throw AssertionError("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun `sha256 matches the known digest`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            sha256Hex("hello".toByteArray())
        )
    }
}
