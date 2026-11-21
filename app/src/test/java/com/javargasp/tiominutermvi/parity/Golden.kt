package com.javargasp.tiominutermvi.parity

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.fail
import java.io.File
import java.security.MessageDigest

/**
 * The Kotlin ↔ Dart parity files (shared/golden/, CLAUDE.md "Parity"): the real Kotlin code run over
 * a fixed input matrix, written as canonical JSON (keys sorted, one-space indentation, no run
 * timestamps), one file per domain. The web's Dart ports assert equality with every file
 * (web/test/parity/).
 *
 * Normal unit-test runs **check**: regenerating must not change a byte. With
 * `-Pgolden.update=true` (`scripts/update_golden.sh`) the files are rewritten instead.
 */
object Golden {
    /** Unit tests run from the app module. */
    val dir = File("../shared/golden")

    const val UPDATE_COMMAND = "scripts/update_golden.sh"

    private val updating: Boolean get() = System.getProperty("golden.update") == "true"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = " "
    }

    /** The canonical text of [element]: object keys sorted at every level, a final newline. */
    fun canonical(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), sorted(element)) + "\n"

    /** Writes (update mode) or checks [name].json against [content]. */
    fun verify(name: String, content: JsonElement) {
        val text = canonical(content)
        val file = File(dir, "$name.json")
        if (updating) {
            dir.mkdirs()
            file.writeText(text)
            println("Golden: wrote $file (${text.length} characters)")
            return
        }
        if (!file.isFile) fail("$file is missing: run $UPDATE_COMMAND")
        val current = file.readText()
        if (current == text) return
        val currentLines = current.lines()
        val expectedLines = text.lines()
        val line = currentLines.indices.firstOrNull { it >= expectedLines.size || currentLines[it] != expectedLines[it] }
            ?: currentLines.size
        fail(
            "$file is out of date: the Kotlin code now gives another result (first difference at line ${line + 1}: " +
                "file \"${currentLines.getOrNull(line)?.trim()}\", now \"${expectedLines.getOrNull(line)?.trim()}\"). " +
                "If the change is intended, run $UPDATE_COMMAND, then make the web's Dart port match (web/test/parity/)."
        )
    }

    /** The golden document: what it is, how to update it, its [cases] (inputs and outputs). */
    fun document(domain: String, generatedBy: String, description: String, extra: Map<String, JsonElement> = emptyMap(), cases: List<JsonElement>) =
        obj(
            "domain" to JsonPrimitive(domain),
            "description" to JsonPrimitive(description),
            "generatedBy" to JsonPrimitive(generatedBy),
            "update" to JsonPrimitive(UPDATE_COMMAND),
            "caseCount" to JsonPrimitive(cases.size),
            "cases" to JsonArray(cases),
            *extra.toList().toTypedArray()
        )

    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { sorted(it.value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }
}

// Small builders, so the golden tests read like the JSON they write

fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*pairs))

fun arr(items: Iterable<JsonElement>): JsonArray = JsonArray(items.toList())

fun str(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun num(value: Number?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

fun bool(value: Boolean?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
