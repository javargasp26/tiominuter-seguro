package com.javargasp.tiominutermvi.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Displays a duration formatted by [formatSeconds] (`mm:ss`) with the given
 * style and color.
 */
@Composable
fun TimerText(
    seconds: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null
) {
    Text(
        text = formatSeconds(seconds),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign
    )
}
