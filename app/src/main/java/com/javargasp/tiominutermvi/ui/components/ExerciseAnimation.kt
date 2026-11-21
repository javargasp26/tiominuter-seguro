package com.javargasp.tiominutermvi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.javargasp.tiominutermvi.data.content.AnimationLocator

/**
 * Animation shown while resting between sets (file in `animations/`).
 */
const val REST_ANIMATION = "13_rest.gif"

/**
 * Finds the animation files. `MainActivity` provides the Koin [AnimationLocator] (bundled, then
 * downloaded); previews get the bundled-only default.
 */
val LocalAnimationLocator = staticCompositionLocalOf { AnimationLocator.BundledOnly }

/**
 * Displays an exercise animation GIF found by [LocalAnimationLocator]: the bundled asset in
 * `animations/`, else a downloaded file; nothing when neither exists.
 *
 * The size is determined by the [modifier], not hardcoded, so callers control
 * the layout. Uses Coil's [AsyncImage] with the app's GIF-enabled ImageLoader.
 */
@Composable
fun ExerciseAnimation(
    animation: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val locator = LocalAnimationLocator.current
    val model = remember(locator, animation) { locator.locate(animation) }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
