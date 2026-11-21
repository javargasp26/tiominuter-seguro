package com.javargasp.tiominutermvi.domain.traininglog.photo

import kotlinx.coroutines.CancellationException

/** Outcome of a photo action for ViewModels: the value, or the error to show. */
sealed interface PhotoResult<out T> {
    data class Success<T>(val value: T) : PhotoResult<T>
    data class Failure(val error: TrainingPhotoError) : PhotoResult<Nothing>
}

/**
 * Runs a photo action; a failure becomes its [TrainingPhotoError] (anything unexpected is
 * FAILED and logged). Cancellation still propagates.
 */
suspend fun <T> photoAction(block: suspend () -> T): PhotoResult<T> =
    try {
        PhotoResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: TrainingPhotoException) {
        PhotoResult.Failure(e.error)
    } catch (e: Exception) {
        println("TrainingPhoto: ${e.message}")
        PhotoResult.Failure(TrainingPhotoError.FAILED)
    }
