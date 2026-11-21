package com.javargasp.tiominutermvi.domain.groups

import kotlin.random.Random

/** Invite codes: 8 characters, uppercase, without the ambiguous 0 O 1 I L (the rules check the same). */
object InviteCodes {
    const val LENGTH = 8
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** What the person typed, uppercased, without spaces or dashes (still possibly invalid). */
    fun normalize(input: String): String =
        input.uppercase().filterNot { it.isWhitespace() || it == '-' }

    /** Characters a code never uses (0, O, 1, I, L, anything else). */
    fun hasInvalidCharacters(normalized: String): Boolean = normalized.any { it !in ALPHABET }

    fun isValid(normalized: String): Boolean = normalized.length == LENGTH && !hasInvalidCharacters(normalized)
}
