package com.javargasp.tiominutermvi.data.program.repository

import androidx.room.withTransaction
import com.javargasp.tiominutermvi.data.program.local.ProgramDatabase
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramSlotProgressEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutEntity
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramWorkoutProgressEntity
import com.javargasp.tiominutermvi.data.program.mapper.toDomain
import com.javargasp.tiominutermvi.data.program.mapper.toEntity
import com.javargasp.tiominutermvi.data.program.mapper.toProgramCatalog
import com.javargasp.tiominutermvi.data.program.mapper.toProgramWorkoutOrNull
import com.javargasp.tiominutermvi.data.program.mapper.toSummary
import com.javargasp.tiominutermvi.data.program.mapper.toWorkoutEntities
import com.javargasp.tiominutermvi.data.program.remote.ProgramRemoteDataSource
import com.javargasp.tiominutermvi.domain.program.generator.ProgramGenerator
import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ImpactRules
import com.javargasp.tiominutermvi.domain.program.model.ProgramDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSummary
import com.javargasp.tiominutermvi.domain.program.model.ProgramTemplates
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeekDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.repository.ProgramDataCleaner
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.domain.program.repository.ProgramWorkoutTitles
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.recordSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.session.SessionRules
import com.javargasp.tiominutermvi.domain.session.InactiveSessionCloser
import com.javargasp.tiominutermvi.domain.session.ClosedSession
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import com.javargasp.tiominutermvi.domain.program.repository.programRefId
import com.javargasp.tiominutermvi.domain.program.repository.ProgramStrengthSessions
import java.util.UUID
import com.javargasp.tiominutermvi.domain.program.repository.workoutDocId
import com.javargasp.tiominutermvi.domain.sync.SyncCollection
import com.javargasp.tiominutermvi.domain.sync.DocKey
import com.javargasp.tiominutermvi.domain.sync.ChangeTracker

/**
 * Room-backed [ProgramRepository]. The catalog and templates come from the remote
 * source once per instance (a Koin single) and are cached; stored programs never
 * depend on them again.
 *
 * Completed workouts are recorded in the app's training history through
 * [trainingLogRecorder], after the progress is stored; a recording failure is logged and never
 * fails the call.
 *
 * Sessions (`domain/session`): a strength workout has the gym's session (first set → last set,
 * closed after 2 hours without a set, the workout left resumable); cardio workouts have an
 * active-time session ([cardioSessions]). Abandoned sessions are recorded through
 * [sessionRecorder], dated on their start. The training log is in the app database, so a close
 * removes the session in this database's transaction and writes the entry last, under one lock.
 */
class ProgramRepositoryImpl(
    private val database: ProgramDatabase,
    private val remoteDataSource: ProgramRemoteDataSource,
    private val wallClock: WallClock,
    private val trainingLogRecorder: TrainingLogRecorder,
    private val workoutTitles: ProgramWorkoutTitles,
    private val sessionRecorder: SessionEntryRecorder,
    private val changes: ChangeTracker = ChangeTracker.None,
    private val newUuid: () -> String = { UUID.randomUUID().toString() }
) : ProgramRepository, ProgramDataCleaner, InactiveSessionCloser {

    private val programDao = database.programDao()
    private val progressDao = database.programProgressDao()
    private val sessionDao = database.programSessionDao()

    /** Strength workout sessions (the gym's rule; the workout stays resumable when one closes). */
    private val strengthSessions = ProgramStrengthSessions(
        ProgramStrengthSessionStore(database, sessionRecorder, ::titleOf), wallClock
    )

    /** The active-time session of the cardio workouts (treadmill, circuit, outdoor run). */
    val cardioSessions = ActiveSessionTracker(
        ProgramCardioSessionStore(database, sessionRecorder, ::titleOf), wallClock, "ProgramCardioSession"
    )

    private val sourcesMutex = Mutex()

    /** Entry id of each workout's latest recorded completion, by (programId, week, dayIndex). */
    private val completionEntryIds = MutableStateFlow<Map<Triple<Long, Int, Int>, Long?>>(emptyMap())
    private var cachedSources: Sources? = null

    private class Sources(val catalog: List<ProgramExercise>, val templates: ProgramTemplates)

    override fun observePrograms(): Flow<List<ProgramSummary>> =
        programDao.observeProgramSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeProgram(programId: Long): Flow<ProgramDetail?> =
        combine(
            programDao.observeProgram(programId),
            programDao.observeWorkouts(programId),
            progressDao.observeWorkouts(programId),
            progressDao.observeSlots(programId)
        ) { program, workouts, completedWorkouts, slots ->
            if (program == null) return@combine null
            val completed = completedWorkouts.map { it.week to it.dayIndex }.toSet()
            val positions = slots.groupBy({ it.week to it.dayIndex }, { it.position })
            val details = workouts.mapNotNull { entity ->
                entity.toDetail(
                    isCompleted = (entity.week to entity.dayIndex) in completed,
                    completedPositions = positions[entity.week to entity.dayIndex].orEmpty().toSet()
                )
            }
            val deloadWeeks = workouts.filter { it.isDeload }.map { it.week }.toSet()
            ProgramDetail(
                summary = program.toSummary(completedWorkouts = completed.size, totalWorkouts = workouts.size),
                weeks = details.groupBy { it.week }.toSortedMap().map { (week, list) ->
                    ProgramWeekDetail(weekNumber = week, isDeload = week in deloadWeeks, workouts = list)
                }
            )
        }

    override fun observeWorkout(programId: Long, week: Int, dayIndex: Int): Flow<ProgramWorkoutDetail?> =
        combine(
            programDao.observeWorkout(programId, week, dayIndex),
            progressDao.observeWorkoutCompleted(programId, week, dayIndex),
            progressDao.observeCompletedPositions(programId, week, dayIndex)
        ) { entity, isCompleted, positions ->
            entity?.toDetail(isCompleted, positions.toSet())
        }

    override suspend fun previewProgram(profile: ProgramProfile): GeneratedProgram {
        val sources = sources()
        return ProgramGenerator.generate(profile, sources.catalog, sources.templates)
    }

    override suspend fun createProgram(profile: ProgramProfile): Long {
        val program = previewProgram(profile)
        val uuid = newUuid()
        // The program and its workouts are uploaded with it (the sync's outbox, same transaction)
        return changes.inTransaction {
            val id = programDao.insertProgram(profile.toEntity(createdAt = wallClock.nowMillis()).copy(uuid = uuid))
            val workouts = program.toWorkoutEntities(id)
            programDao.insertWorkouts(workouts)
            changes.enqueue(DocKey(SyncCollection.PROGRAM, uuid))
            workouts.forEach { changes.enqueue(DocKey(SyncCollection.PROGRAM_WORKOUT, workoutDocId(it.week, it.dayIndex), uuid)) }
            id
        }
    }

    /** A tombstone of the program is uploaded; its workouts and completions go with it. */
    override suspend fun deleteProgram(programId: Long) {
        changes.inTransaction {
            val uuid = programDao.getProgram(programId)?.uuid
            programDao.deleteProgram(programId)
            if (!uuid.isNullOrEmpty()) changes.enqueue(DocKey(SyncCollection.PROGRAM, uuid), delete = true)
        }
    }

    override suspend fun markSlotCompleted(programId: Long, week: Int, dayIndex: Int, position: Int) {
        // Non-null when this call completes the workout: what to record after the transaction
        val completed: Pair<ProgramWorkout.Strength, Int?>? = database.withTransaction {
            val now = wallClock.nowMillis()
            val doneBefore = progressDao.getCompletedPositions(programId, week, dayIndex).toSet()
            progressDao.upsertSlot(ProgramSlotProgressEntity(programId, week, dayIndex, position, now))
            val workout = programDao.getWorkout(programId, week, dayIndex)
                ?.payload?.toProgramWorkoutOrNull() as? ProgramWorkout.Strength
                ?: return@withTransaction null
            val done = progressDao.getCompletedPositions(programId, week, dayIndex).toSet()
            if (workout.slots.isNotEmpty() && workout.slots.all { it.position in done }) {
                progressDao.upsertWorkout(ProgramWorkoutProgressEntity(programId, week, dayIndex, now))
                enqueueCompletion(programId, week, dayIndex)
                val wasAlreadyComplete = workout.slots.all { it.position in doneBefore }
                if (!wasAlreadyComplete) {
                    // The session ends with the completion, in this transaction: never recorded twice
                    val session = sessionDao.getStrength(programId, week, dayIndex)
                    sessionDao.deleteStrength(programId, week, dayIndex)
                    val duration = session?.let { SessionRules.durationSeconds(it.sessionStartedAt, it.lastActivityAt) }
                    return@withTransaction workout to duration
                }
            }
            null
        }
        if (completed != null) {
            val (workout, durationSeconds) = completed
            recordWorkout(programId, week, dayIndex, workout, durationSeconds)
        }
    }

    /** Reset by hand: the slots and the session go (the work is not saved). */
    override suspend fun resetWorkout(programId: Long, week: Int, dayIndex: Int) {
        database.withTransaction {
            progressDao.clearSlots(programId, week, dayIndex)
            sessionDao.deleteStrength(programId, week, dayIndex)
        }
    }

    override suspend fun onStrengthSetCompleted(programId: Long, week: Int, dayIndex: Int) =
        strengthSessions.onSetCompleted(programId, week, dayIndex)

    override suspend fun closeInactiveSessions() {
        strengthSessions.closeInactive()
        cardioSessions.closeInactive()
    }

    override suspend fun closeInactive() = closeInactiveSessions()

    override fun takeClosedSessions(): List<ClosedSession> =
        strengthSessions.takeClosedSessions() + cardioSessions.takeClosedSessions()

    /** The entry title of a workout, as for a completion; null when it can't be read. */
    private suspend fun titleOf(programId: Long, week: Int, dayIndex: Int): String? = try {
        programDao.getWorkout(programId, week, dayIndex)?.payload?.toProgramWorkoutOrNull()?.let { workoutTitles.title(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("ProgramSession: no title for $programId/$week/$dayIndex: ${e.message}")
        null
    }

    override suspend fun markWorkoutCompleted(programId: Long, week: Int, dayIndex: Int, durationSeconds: Int?) {
        changes.inTransaction {
            progressDao.upsertWorkout(ProgramWorkoutProgressEntity(programId, week, dayIndex, wallClock.nowMillis()))
            enqueueCompletion(programId, week, dayIndex)
        }
        val workout = programDao.getWorkout(programId, week, dayIndex)?.payload?.toProgramWorkoutOrNull() ?: return
        recordWorkout(programId, week, dayIndex, workout, durationSeconds)
    }

    /** The completion of a workout is uploaded (call inside the write's transaction). */
    private suspend fun enqueueCompletion(programId: Long, week: Int, dayIndex: Int) {
        val uuid = programDao.getProgram(programId)?.uuid
        if (!uuid.isNullOrEmpty()) {
            changes.enqueue(DocKey(SyncCollection.PROGRAM_COMPLETION, workoutDocId(week, dayIndex), uuid))
        }
    }

    /** Each completion is a new training: doing a completed workout again records again. */
    private suspend fun recordWorkout(
        programId: Long,
        week: Int,
        dayIndex: Int,
        workout: ProgramWorkout,
        durationSeconds: Int?
    ) {
        val key = Triple(programId, week, dayIndex)
        // A previous completion's entry must not receive this completion's photo
        completionEntryIds.update { it + (key to null) }
        val title = try {
            workoutTitles.title(workout)
        } catch (e: Exception) {
            println("TrainingLog: no title for program workout $programId/$week/$dayIndex: ${e.message}")
            return
        }
        val entryId = trainingLogRecorder.recordSafely(
            source = TrainingSource.PROGRAM,
            refId = programRefId(programId, week, dayIndex),
            title = title,
            durationSeconds = durationSeconds
        )
        completionEntryIds.update { it + (key to entryId) }
    }

    override fun observeCompletionEntryId(programId: Long, week: Int, dayIndex: Int): Flow<Long?> =
        completionEntryIds.map { it[Triple(programId, week, dayIndex)] }.distinctUntilChanged()

    override suspend fun getExercises(): Map<Int, ProgramExercise> =
        sources().catalog.associateBy { it.exerciseId }

    override suspend fun getImpactRules(): ImpactRules = sources().templates.impact

    private suspend fun sources(): Sources = sourcesMutex.withLock {
        cachedSources ?: Sources(
            catalog = remoteDataSource.fetchExercises().toProgramCatalog(),
            templates = remoteDataSource.fetchTemplates().toDomain()
        ).also { cachedSources = it }
    }

    private fun ProgramWorkoutEntity.toDetail(isCompleted: Boolean, completedPositions: Set<Int>): ProgramWorkoutDetail? {
        val workout = payload.toProgramWorkoutOrNull() ?: return null
        return ProgramWorkoutDetail(
            programId = programId,
            week = week,
            dayIndex = dayIndex,
            workout = workout,
            isCompleted = isCompleted,
            completedPositions = completedPositions
        )
    }

    /** Empties every table of [ProgramDatabase] (the file and its schema stay). */
    override suspend fun deleteAllProgramData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        completionEntryIds.value = emptyMap()
    }
}
