package com.javargasp.tiominutermvi.domain.sync

/**
 * Sync of each person's data with Firestore (Phase 2b, docs/firestore-model.md is the contract).
 * Room stays the only source of truth for the UI; Firestore is a backup and sync target.
 */

/** The synced collections under `users/{uid}`; [path] is the Firestore collection id. */
enum class SyncCollection(val path: String, val idField: String) {
    TRAINING_LOG("trainingLog", "entryId"),
    WEEKLY_GOAL("weeklyGoals", "effectiveFromWeek"),
    PROGRAM("programs", "programId"),
    PROGRAM_WORKOUT("workouts", "workoutId"),
    PROGRAM_COMPLETION("completions", "workoutId"),

    /**
     * The daily progress: one document ([DAILY_PROGRESS_DOC_ID]) without an id field, merged
     * instead of last-write-wins (`DailyProgressSync`) and never tombstoned ([canTombstone]).
     */
    DAILY_PROGRESS("dailyProgress", "");

    /** Random (UUID) ids: their tombstones are final. Natural ids (a week, "1-0") can come back. */
    val hasRandomIds: Boolean get() = this == TRAINING_LOG || this == PROGRAM

    /** Every collection but the daily progress can hold tombstones. */
    val canTombstone: Boolean get() = this != DAILY_PROGRESS

    /** Subcollections of a program: [DocKey.parentId] is the program's uuid. */
    val isProgramChild: Boolean get() = this == PROGRAM_WORKOUT || this == PROGRAM_COMPLETION

    companion object {
        /** The id of the daily progress' only document. */
        const val DAILY_PROGRESS_DOC_ID = "state"

        fun fromName(name: String): SyncCollection? = entries.firstOrNull { it.name == name }
    }
}

/** A document under `users/{uid}`; [parentId] is the program uuid of a workout or completion. */
data class DocKey(val collection: SyncCollection, val id: String, val parentId: String? = null) {
    /** Path relative to `users/{uid}`, e.g. "programs/<uuid>/completions/1-0". */
    val path: String
        get() = if (parentId == null) "${collection.path}/$id" else "${SyncCollection.PROGRAM.path}/$parentId/${collection.path}/$id"
}

/** One change waiting in an outbox; [version] grows with every write of the same document. */
data class PendingChange(val key: DocKey, val delete: Boolean, val version: Long)

/**
 * What to write for a pending change: the document's own fields (as docs/firestore-model.md
 * lists them, without updatedAt / deleted / schemaVersion), or null for a tombstone.
 */
data class OutgoingDoc(val change: PendingChange, val fields: Map<String, Any?>?) {
    val key: DocKey get() = change.key
}

/** A Firestore server timestamp (seconds + nanoseconds), exact enough to order and resume pulls. */
data class SyncTime(val seconds: Long, val nanos: Int) : Comparable<SyncTime> {
    override fun compareTo(other: SyncTime): Int =
        compareValuesBy(this, other, SyncTime::seconds, SyncTime::nanos)

    val millis: Long get() = seconds * 1000 + nanos / 1_000_000

    fun encode(): String = "$seconds:$nanos"

    companion object {
        fun decode(value: String?): SyncTime? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 2) return null
            val seconds = parts[0].toLongOrNull() ?: return null
            val nanos = parts[1].toIntOrNull() ?: return null
            return SyncTime(seconds, nanos)
        }
    }
}

/** A document read from Firestore: its fields (a tombstone has only its id), and its server time. */
data class IncomingDoc(
    val key: DocKey,
    val fields: Map<String, Any?>,
    val deleted: Boolean,
    val updatedAt: SyncTime
)

/** A collection (or a program's subcollection) to pull; [fullFetch] ignores the watermark. */
data class PullQuery(val collection: SyncCollection, val parentId: String? = null, val fullFetch: Boolean = false)

/** Where a paginated pull resumes: after this (updatedAt, document id). */
data class PageCursor(val updatedAt: SyncTime, val docId: String)

data class RemotePage(val docs: List<IncomingDoc>, val next: PageCursor?)
