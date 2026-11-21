package com.javargasp.tiominutermvi.data.groups

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * A Firestore call of the groups feature with a timeout, its failures mapped to [GroupException]
 * (the exception's short class name only: no personal data).
 */
internal suspend fun <T> firestoreCall(timeoutMillis: Long, block: suspend () -> T): T =
    try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw GroupException(GroupError.NETWORK, "Timeout")
    } catch (e: CancellationException) {
        throw e
    } catch (e: GroupException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw GroupException(FirestoreGroupRepository.errorOf(e), e.javaClass.simpleName)
    } catch (e: FirebaseNetworkException) {
        throw GroupException(GroupError.NETWORK, e.javaClass.simpleName)
    } catch (e: IOException) {
        throw GroupException(GroupError.NETWORK, e.javaClass.simpleName)
    } catch (e: Exception) {
        throw GroupException(GroupError.FAILED, e.javaClass.simpleName)
    }
