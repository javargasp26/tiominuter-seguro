package com.javargasp.tiominutermvi.ui.components.photo

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import java.io.File
import java.util.Date

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

@StringRes
fun photoErrorMessage(error: TrainingPhotoError): Int = when (error) {
    TrainingPhotoError.UNREADABLE_IMAGE -> R.string.photo_error_unreadable
    TrainingPhotoError.NO_SPACE -> R.string.photo_error_no_space
    TrainingPhotoError.ENTRY_NOT_FOUND -> R.string.photo_error_entry_missing
    TrainingPhotoError.FAILED -> R.string.photo_error_failed
}

/**
 * Photo errors are a short Toast: it stays visible over dialogs and after the screen
 * navigates back (e.g. the log-activity form saved and closed).
 */
fun showTrainingPhotoError(context: Context, error: TrainingPhotoError) {
    Toast.makeText(context, photoErrorMessage(error), Toast.LENGTH_SHORT).show()
}

/** "Workout photo, 19 Oct 2026" for [millis]; "Workout photo" without a date. */
@Composable
fun photoContentDescription(millis: Long?): String {
    if (millis == null) return stringResource(R.string.photo_content_description_undated)
    val locale = LocalConfiguration.current.locales[0]
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "dMMMyyyy") }
    val date = remember(pattern, millis) { java.text.SimpleDateFormat(pattern, locale).format(Date(millis)) }
    return stringResource(R.string.photo_content_description, date)
}

// ---------------------------------------------------------------------------
// Choosing a source
// ---------------------------------------------------------------------------

/**
 * Bottom sheet to choose the camera or the gallery, with the privacy note. The camera row
 * is hidden when no camera app is available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPhotoSourceSheet(
    cameraAvailable: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.photo_source_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() }
            )
            if (cameraAvailable) {
                SheetRow(text = stringResource(R.string.photo_source_camera), onClick = onTakePhoto)
            }
            SheetRow(text = stringResource(R.string.photo_source_gallery), onClick = onChooseFromGallery)
            PhotoPrivacyNote(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

/** "Photos stay on this device for now. Location data is removed." */
@Composable
fun PhotoPrivacyNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.photo_privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * The source sheet plus the camera / gallery launchers. The launchers are registered even
 * while the sheet is hidden, so a result that arrives after a configuration change or
 * process death still reaches [onPhotoSelected].
 */
@Composable
fun TrainingPhotoChooser(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPhotoSelected: (TrainingPhotoSource) -> Unit
) {
    val picker = rememberTrainingPhotoPicker(onPhotoSelected)
    if (visible) {
        TrainingPhotoSourceSheet(
            cameraAvailable = picker.cameraAvailable,
            onTakePhoto = {
                onDismiss()
                picker.takePhoto()
            },
            onChooseFromGallery = {
                onDismiss()
                picker.chooseFromGallery()
            },
            onDismiss = onDismiss
        )
    }
}

// ---------------------------------------------------------------------------
// Showing a photo
// ---------------------------------------------------------------------------

/** The stored thumbnail file of [photoPath]. */
@Composable
fun rememberThumbnailFile(photoPath: String): File {
    val files = rememberTrainingPhotoFiles()
    return remember(files, photoPath) { files.fileOf(TrainingPhotoPaths.thumbnailPath(photoPath)) }
}

/** The stored photo file of [photoPath]. */
@Composable
fun rememberPhotoFile(photoPath: String): File {
    val files = rememberTrainingPhotoFiles()
    return remember(files, photoPath) { files.fileOf(photoPath) }
}

/**
 * A small square thumbnail ([size], at least 48dp) that opens the photo when tapped.
 * [contentDescription] is "Workout photo, <date>".
 */
@Composable
fun TrainingPhotoThumbnail(
    file: File,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val openLabel = stringResource(R.string.photo_open)
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = openLabel, role = Role.Image, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    )
}

/** The photo full screen on black, with a close button. */
@Composable
fun TrainingPhotoViewer(file: File, contentDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.photo_close),
                    tint = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// After a completed workout
// ---------------------------------------------------------------------------

/**
 * Photo of a workout just completed: [entryId] is the training-log entry recorded for that
 * completion (null until it is recorded, or when recording failed: then nothing is shown).
 */
data class CompletionPhotoState(
    val entryId: Long? = null,
    val photoPath: String? = null,
    val isProcessing: Boolean = false
) {
    val canAddPhoto: Boolean get() = entryId != null && !isProcessing
}

/**
 * "Add photo" (or "Change photo") for a completed workout, with the privacy note, a
 * progress line while the photo is saved and the thumbnail once added.
 */
@Composable
fun CompletionPhotoSection(
    photo: CompletionPhotoState,
    onPhotoSelected: (TrainingPhotoSource) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photo.entryId == null) return
    var showChooser by rememberSaveable { mutableStateOf(false) }
    var showViewer by rememberSaveable { mutableStateOf(false) }
    val description = photoContentDescription(remember { System.currentTimeMillis() })

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            photo.photoPath?.let { path ->
                TrainingPhotoThumbnail(
                    file = rememberThumbnailFile(path),
                    contentDescription = description,
                    onClick = { showViewer = true }
                )
            }
            OutlinedButton(
                onClick = { showChooser = true },
                enabled = photo.canAddPhoto,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (photo.photoPath == null) R.string.photo_add else R.string.photo_change))
            }
        }
        when {
            photo.isProcessing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.photo_processing), style = MaterialTheme.typography.bodySmall)
            }
            photo.photoPath != null -> Text(
                text = stringResource(R.string.photo_added),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        PhotoPrivacyNote()
    }

    TrainingPhotoChooser(
        visible = showChooser,
        onDismiss = { showChooser = false },
        onPhotoSelected = onPhotoSelected
    )
    if (showViewer && photo.photoPath != null) {
        TrainingPhotoViewer(
            file = rememberPhotoFile(photo.photoPath),
            contentDescription = description,
            onDismiss = { showViewer = false }
        )
    }
}
