package com.javargasp.tiominutermvi.data.sync

/** Typed reads of Firestore document fields (numbers come back as Long or Double). */
fun Map<String, Any?>.string(key: String): String? = this[key] as? String

fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
