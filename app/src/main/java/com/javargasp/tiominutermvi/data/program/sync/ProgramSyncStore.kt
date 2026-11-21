package com.javargasp.tiominutermvi.data.program.sync

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import com.javargasp.tiominutermvi.data.sync.bool
import com.javargasp.tiominutermvi.data.sync.double
import com.javargasp.tiominutermvi.data.sync.drop
import com.javargasp.tiominutermvi.data.sync.enqueue
import com.javargasp.tiominutermvi.data.sync.int
import com.javargasp.tiominutermvi.data.sync.isPending
import com.javargasp.tiominutermvi.data.sync.long
import com.javargasp.tiominutermvi.data.sync.string
import com.javargasp.tiominutermvi.data.sync.toPendingChange
import com.javargasp.tiominutermvi.domain.program.repository.workoutDocId
import com.javargasp.tiominutermvi.domain.program.repository.workoutOfDocId
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.IncomingDoc
import com.javargasp.tiominutermvi.domain.sync.LocalSyncStore
import com.javargasp.tiominutermvi.domain.sync.OutgoingDoc
import com.javargasp.tiominutermvi.domain.sync.PendingChange
import com.javargasp.tiominutermvi.domain.sync.ProgramRefMapper
import com.javargasp.tiominutermvi.domain.sync.PullQuery
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.SyncConflicts
import com.javargasp.tiominutermvi.domain.time.WallClock
import kotlinx.coroutines.flow.Flow

/** `users/{uid}/programs/{programId}` ↔ `programs` (the uuid is the Firestore id, `id` stays local). */
object ProgramDocMapper {
    fun toFields(program: ProgramEntity): Map<String, Any?> = mapOf(
        "programId" to program.uuid,
        "goal" to program.goal,
        "level" to program.level,
        "location" to program.location,
        "focus" to program.focus,
        "weeks" to program.weeks,
        "daysPerWeek" to program.daysPerWeek,
        "age" to program.age,
        "weightKg" to program.weightKg,
        "heightCm" to program.heightCm,
        "createdAt" to program.createdAt
    )

    fun toEntity(uuid: String, fields: Map<String, Any?>, localId: Long): ProgramEntity? {
        return ProgramEntity(
            id = localId,
            goal = fields.string("goal") ?: return null,
            level = fields.string("level") ?: return null,
            location = fields.string("location") ?: return null,
            weeks = fields.int("weeks") ?: return null,
            daysPerWeek = fields.int("daysPerWeek") ?: return null,
            age = fields.int("age") ?: return null,
            weightKg = fields.double("weightKg") ?: return null,
            heightCm = fields.int("heightCm") ?: return null,
            createdAt = fields.long("createdAt") ?: return null,
            focus = fields.string("focus") ?: return null,
            uuid = uuid
        )
    }
}

/** `…/workouts/{week}-{dayIndex}` ↔ `program_workouts` (the generated payload, as stored). */
object ProgramWorkoutDocMapper {
    fun toFields(workout: ProgramWorkoutEntity): Map<String, Any?> = mapOf(
        "workoutId" to workoutDocId(workout.week, workout.dayIndex),
        "week" to workout.week,
        "dayIndex" to workout.dayIndex,
        "kind" to workout.kind,
        "isDeload" to workout.isDeload,
        "payload" to workout.payload
    )

    fun toEntity(programId: Long, fields: Map<String, Any?>): ProgramWorkoutEntity? {
        return ProgramWorkoutEntity(
            programId = programId,
            week = fields.int("week") ?: return null,
            dayIndex = fields.int("dayIndex") ?: return null,
            kind = fields.string("kind") ?: return null,
            isDeload = fields.bool("isDeload") ?: return null,
            payload = fields.string("payload") ?: return null
        )
    }
}

/** `…/completions/{week}-{dayIndex}` ↔ `program_workout_progress`. */
object ProgramCompletionDocMapper {
    fun toFields(completion: ProgramWorkoutProgressEntity): Map<String, Any?> = mapOf(
        "workoutId" to workoutDocId(completion.week, completion.dayIndex),
        "week" to completion.week,
        "dayIndex" to completion.dayIndex,
        "completedAt" to completion.completedAt
    )

    fun toEntity(programId: Long, fields: Map<String, Any?>): ProgramWorkoutProgressEntity? {
        return ProgramWorkoutProgressEntity(
            programId = programId,
            week = fields.int("week") ?: return null,
            dayIndex = fields.int("dayIndex") ?: return null,
            completedAt = fields.long("completedAt") ?: return null
        )
    }
}

/**
 * The program feature's sync-facing side: the [LocalSyncStore] of [ProgramDatabase] (programs,
 * their workouts and completions) and the [ProgramRefMapper] of PROGRAM refIds. The sync code
 * only sees these two interfaces. Pulled documents are written with the sync DAO, never enqueued.
 * A program deleted locally takes its pending workout and completion changes with it.
 */
class ProgramSyncStore(
    private val database: ProgramDatabase,
    private val clock: WallClock
) : LocalSyncStore, ProgramRefMapper {

    private val outbox get() = database.syncOutboxDao()
    private val dao get() = database.programSyncDao()

    override suspend fun pending(limit: Int): List<OutgoingDoc> = database.withTransaction {
        outbox.oldest(limit).mapNotNull { row ->
            val change = row.toPendingChange() ?: return@mapNotNull null
            val key = change.key
            if (key.collection.isProgramChild && dao.getProgramByUuid(key.parentId.orEmpty()) == null) {
                // The program is gone (its tombstone is pushed): its children are not uploaded
                outbox.drop(key)
                return@mapNotNull null
            }
            OutgoingDoc(change, if (change.delete) null else fieldsOf(key))
        }
    }

    /** The current row's document; a row that is gone becomes a tombstone. */
    private suspend fun fieldsOf(key: DocKey): Map<String, Any?>? = when (key.collection) {
        SyncCollection.PROGRAM -> dao.getProgramByUuid(key.id)?.let(ProgramDocMapper::toFields)
        SyncCollection.PROGRAM_WORKOUT -> childRow(key) { program, week, day ->
            database.programDao().getWorkout(program.id, week, day)?.let(ProgramWorkoutDocMapper::toFields)
        }
        SyncCollection.PROGRAM_COMPLETION -> childRow(key) { program, week, day ->
            dao.getCompletion(program.id, week, day)?.let(ProgramCompletionDocMapper::toFields)
        }
        else -> null
    }

    private suspend fun <T> childRow(key: DocKey, read: suspend (ProgramEntity, Int, Int) -> T?): T? {
        val program = dao.getProgramByUuid(key.parentId.orEmpty()) ?: return null
        val (week, day) = workoutOfDocId(key.id) ?: return null
        return read(program, week, day)
    }

    override suspend fun pendingCount(): Int = outbox.count()

    override fun observePendingCount(): Flow<Int> = outbox.observeCount()

    override suspend fun acknowledge(changes: List<PendingChange>) {
        database.withTransaction {
            changes.forEach {
                outbox.deleteIfVersion(it.key.collection.name, it.key.parentId.orEmpty(), it.key.id, it.version)
            }
        }
    }

    /** Programs first, then the workouts and completions of every local program. */
    override suspend fun pullPlan(): List<PullQuery> =
        listOf(PullQuery(SyncCollection.PROGRAM)) + dao.getPrograms().filter { it.uuid.isNotEmpty() }.flatMap {
            listOf(PullQuery(SyncCollection.PROGRAM_WORKOUT, it.uuid), PullQuery(SyncCollection.PROGRAM_COMPLETION, it.uuid))
        }

    override suspend fun apply(query: PullQuery, docs: List<IncomingDoc>): List<PullQuery> {
        val created = mutableListOf<String>()
        database.withTransaction { docs.forEach { applyOne(it, created) } }
        // A program new on this device: its workouts and completions are fetched in full
        return created.flatMap {
            listOf(
                PullQuery(SyncCollection.PROGRAM_WORKOUT, it, fullFetch = true),
                PullQuery(SyncCollection.PROGRAM_COMPLETION, it, fullFetch = true)
            )
        }
    }

    private suspend fun applyOne(doc: IncomingDoc, created: MutableList<String>) {
        val key = doc.key
        val pending = outbox.isPending(key)
        if (SyncConflicts.decide(doc, pending) == SyncConflicts.Decision.KEEP_LOCAL) return
        if (pending) outbox.drop(key)
        when (key.collection) {
            SyncCollection.PROGRAM -> applyProgram(doc, created)
            SyncCollection.PROGRAM_WORKOUT -> {
                val program = dao.getProgramByUuid(key.parentId.orEmpty()) ?: return
                val (week, day) = workoutOfDocId(key.id) ?: return
                if (doc.deleted) {
                    dao.deleteWorkout(program.id, week, day)
                } else {
                    ProgramWorkoutDocMapper.toEntity(program.id, doc.fields)?.let { dao.upsertWorkout(it) } ?: logUnreadable(key)
                }
            }
            SyncCollection.PROGRAM_COMPLETION -> {
                val program = dao.getProgramByUuid(key.parentId.orEmpty()) ?: return
                val (week, day) = workoutOfDocId(key.id) ?: return
                if (doc.deleted) {
                    dao.deleteCompletion(program.id, week, day)
                } else if (database.programDao().getWorkout(program.id, week, day) != null) {
                    ProgramCompletionDocMapper.toEntity(program.id, doc.fields)?.let { dao.upsertCompletion(it) } ?: logUnreadable(key)
                }
            }
            else -> Unit
        }
    }

    private suspend fun applyProgram(doc: IncomingDoc, created: MutableList<String>) {
        val uuid = doc.key.id
        if (doc.deleted) {
            // Deletion wins; the workouts and completions go with it (CASCADE), and so do their changes
            dao.deleteProgram(uuid)
            outbox.deleteChildrenOf(uuid)
            return
        }
        val existing = dao.getProgramByUuid(uuid)
        val entity = ProgramDocMapper.toEntity(uuid, doc.fields, existing?.id ?: 0) ?: return logUnreadable(doc.key)
        if (existing == null) {
            database.programDao().insertProgram(entity)
            created += uuid
        } else {
            dao.updateProgram(entity)
        }
    }

    override suspend fun enqueueAll() {
        database.withTransaction {
            val now = clock.nowMillis()
            val programs = dao.getPrograms().filter { it.uuid.isNotEmpty() }
            programs.forEach { program ->
                outbox.enqueue(DocKey(SyncCollection.PROGRAM, program.uuid), delete = false, now = now)
                database.programDao().getWorkouts(program.id).forEach {
                    outbox.enqueue(DocKey(SyncCollection.PROGRAM_WORKOUT, workoutDocId(it.week, it.dayIndex), program.uuid), false, now)
                }
            }
            val uuids = programs.associate { it.id to it.uuid }
            dao.getCompletions().forEach { completion ->
                val uuid = uuids[completion.programId] ?: return@forEach
                outbox.enqueue(
                    DocKey(SyncCollection.PROGRAM_COMPLETION, workoutDocId(completion.week, completion.dayIndex), uuid),
                    false,
                    now
                )
            }
        }
    }

    override suspend fun uuidOf(localId: Long): String? = dao.uuidOf(localId)?.takeIf { it.isNotEmpty() }

    override suspend fun localIdOf(uuid: String): Long? = dao.getProgramByUuid(uuid)?.id

    private fun logUnreadable(key: DocKey) {
        println("Sync: skipped an unreadable ${key.collection.path} document")
    }
}
