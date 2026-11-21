package com.javargasp.tiominutermvi.domain.sync

import com.javargasp.tiominutermvi.domain.auth.AuthRepository
import com.javargasp.tiominutermvi.domain.auth.AuthSession
import com.javargasp.tiominutermvi.domain.auth.UserDataEraser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Sync state for the Account screen. */
data class SyncStatus(
    val lastSuccessAt: Long? = null,
    val pendingChanges: Int = 0,
    val failing: Boolean = false,
    val running: Boolean = false
)

/** What the UI may ask of the sync. The UI never reads Firestore: it only sees Room. */
interface SyncRepository {
    val status: Flow<SyncStatus>

    /** Changes not uploaded yet (any account; 0 means nothing would be lost). */
    suspend fun pendingCount(): Int

    /** Syncs the signed-in account now; [SyncOutcome.SKIPPED] for a guest. */
    suspend fun syncNow(): SyncOutcome
}

/** Maps a program's device-local id to its Firestore uuid and back (the program feature implements it). */
interface ProgramRefMapper {
    suspend fun uuidOf(localId: Long): String?
    suspend fun localIdOf(uuid: String): Long?
}

/**
 * refIds of PROGRAM entries name the program by its local id on the device ("program:12:1:0")
 * and by its uuid in Firestore ("program:<uuid>:1:0"). A program unknown to the other side
 * (deleted) keeps the refId as it is.
 */
object ProgramRefIds {
    private const val PREFIX = "program:"

    suspend fun toRemote(refId: String?, mapper: ProgramRefMapper): String? {
        val parts = refId?.takeIf { it.startsWith(PREFIX) }?.split(':') ?: return refId
        if (parts.size != 4) return refId
        val localId = parts[1].toLongOrNull() ?: return refId
        val uuid = mapper.uuidOf(localId) ?: return refId
        return "$PREFIX$uuid:${parts[2]}:${parts[3]}"
    }

    suspend fun toLocal(refId: String?, mapper: ProgramRefMapper): String? {
        val parts = refId?.takeIf { it.startsWith(PREFIX) }?.split(':') ?: return refId
        if (parts.size != 4 || parts[1].toLongOrNull() != null) return refId
        val localId = mapper.localIdOf(parts[1]) ?: return refId
        return "$PREFIX$localId:${parts[2]}:${parts[3]}"
    }
}

/**
 * Decides when to sync, never blocking the UI or the app start ([scope] is the application's):
 * - when an account is signed in (sign-in, and app start with a session): the profile document,
 *   then a sync (whose account check merges or wipes first);
 * - [debounceMillis] after local writes, while signed in;
 * - offline or after a failure, the engine schedules a WorkManager retry.
 * Guests never sync. It is also the [UserDataEraser] of Delete account: once an account's server
 * data is erased, none of its syncs run (they would upload the local data again) until the
 * session becomes a guest (the account was deleted, or signed out).
 */
class SyncCoordinator(
    private val auth: AuthRepository,
    private val engine: SyncEngine,
    private val remote: RemoteUserData,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) : SyncRepository, UserDataEraser {

    override val status: Flow<SyncStatus> =
        combine(engine.status, engine.pendingCount) { run, pending ->
            SyncStatus(run.lastSuccessAt, pending, run.failing, run.running)
        }

    @OptIn(FlowPreview::class)
    fun start() {
        auth.session
            .distinctUntilChanged()
            .onEach { session ->
                when (session) {
                    is AuthSession.SignedIn -> {
                        engine.showAccount(session.uid)
                        scope.launch {
                            writeProfile(session)
                            syncIfAllowed(session.uid)
                        }
                    }
                    AuthSession.Guest -> {
                        engine.clearErasedAccount()
                        engine.showAccount(null)
                    }
                }
            }
            .launchIn(scope)

        engine.pendingCount
            .debounce(debounceMillis)
            .onEach { pending ->
                val session = auth.session.first()
                if (pending > 0 && session is AuthSession.SignedIn) syncIfAllowed(session.uid)
            }
            .launchIn(scope)
    }

    override suspend fun pendingCount(): Int = engine.pendingNow()

    override suspend fun syncNow(): SyncOutcome {
        val session = auth.session.first() as? AuthSession.SignedIn ?: return SyncOutcome.SKIPPED
        return syncIfAllowed(session.uid)
    }

    override suspend fun eraseServerData(uid: String) {
        engine.eraseServerData(uid)
    }

    /** The engine skips an account whose server data was erased. */
    private suspend fun syncIfAllowed(uid: String): SyncOutcome = engine.sync(uid)

    /** users/{uid}: the Google name (≤ 500 characters) and an https photo, or none. */
    private suspend fun writeProfile(session: AuthSession.SignedIn) {
        try {
            remote.writeProfile(
                session.uid,
                session.displayName?.take(MAX_DISPLAY_NAME),
                session.photoUrl?.takeIf { it.startsWith("https://") && it.length <= MAX_PHOTO_URL }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Sync: profile not written (${e.javaClass.simpleName}): ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 5_000L
        const val MAX_DISPLAY_NAME = 500
        const val MAX_PHOTO_URL = 2048
    }
}
