package com.javargasp.tiominutermvi.ui.components.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.javargasp.tiominutermvi.data.traininglog.photo.TrainingPhotoFileStore
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoFiles
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource

/** Authority of the FileProvider declared in the manifest (`${applicationId}.trainingphotos`). */
fun trainingPhotoAuthority(context: Context): String = "${context.packageName}.trainingphotos"

/**
 * The photo files for composables. Built from the context (not Koin) so screens and their
 * previews work alike; it has no state, so this is the same store Koin provides.
 */
@Composable
fun rememberTrainingPhotoFiles(): TrainingPhotoFiles {
    val context = LocalContext.current.applicationContext
    return remember(context) { TrainingPhotoFileStore(context.filesDir, context.cacheDir) }
}

/** Starts the gallery or the camera; results arrive in the callback given to [rememberTrainingPhotoPicker]. */
class TrainingPhotoPicker internal constructor(
    /** False when no camera app can take the picture: the camera option is hidden. */
    val cameraAvailable: Boolean,
    private val launchCamera: () -> Unit,
    private val launchGallery: () -> Unit
) {
    fun takePhoto() = launchCamera()
    fun chooseFromGallery() = launchGallery()
}

/**
 * Photo capture without dangerous permissions:
 * - Gallery: the Android Photo Picker (`PickVisualMedia`, images only).
 * - Camera: the camera app (`TakePicture`) writing into the FileProvider capture file in
 *   the cache. The app never requests CAMERA.
 *
 * [onPhotoSelected] receives the picked or taken picture; a cancelled capture deletes the
 * capture file. The capture uri survives configuration changes and process death.
 */
@Composable
fun rememberTrainingPhotoPicker(onPhotoSelected: (TrainingPhotoSource) -> Unit): TrainingPhotoPicker {
    val context = LocalContext.current
    val files = rememberTrainingPhotoFiles()
    val currentOnSelected by rememberUpdatedState(onPhotoSelected)
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraAvailable = remember(context) { hasCameraApp(context) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentOnSelected(TrainingPhotoSource(uri.toString(), fromCamera = false))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        captureUri = null
        if (saved && uri != null) {
            currentOnSelected(TrainingPhotoSource(uri, fromCamera = true))
        } else {
            files.deleteCapture()
        }
    }

    return remember(cameraAvailable, files) {
        TrainingPhotoPicker(
            cameraAvailable = cameraAvailable,
            launchCamera = {
                val uri = FileProvider.getUriForFile(context, trainingPhotoAuthority(context), files.captureFile())
                captureUri = uri.toString()
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    // The camera app went away since the check
                    captureUri = null
                    files.deleteCapture()
                }
            },
            launchGallery = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

private fun hasCameraApp(context: Context): Boolean =
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
