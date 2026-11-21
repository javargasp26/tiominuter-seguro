package com.javargasp.tiominutermvi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    /* Success colors are provided separately via [LocalExtendedColors] below. */

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Extra semantic colors not part of Material 3's [ColorScheme].
 * Currently only the success (green) palette.
 */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    // Fallback (light values) so previews or code outside the theme still work.
    ExtendedColors(
        success = SuccessLight,
        onSuccess = OnSuccessLight,
        successContainer = SuccessContainerLight,
        onSuccessContainer = OnSuccessContainerLight
    )
}

/** Convenience accessor, e.g. `MaterialTheme.extendedColors.success`. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

@Composable
fun TioMinuterMVITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) {
        ExtendedColors(
            success = SuccessDark,
            onSuccess = OnSuccessDark,
            successContainer = SuccessContainerDark,
            onSuccessContainer = OnSuccessContainerDark
        )
    } else {
        ExtendedColors(
            success = SuccessLight,
            onSuccess = OnSuccessLight,
            successContainer = SuccessContainerLight,
            onSuccessContainer = OnSuccessContainerLight
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}