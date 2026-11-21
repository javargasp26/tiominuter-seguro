package com.javargasp.tiominutermvi.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.groups.TrainedTodayBanner

/**
 * "Ana already trained today. Your turn 💪": a quiet outlined card like the guest banner. Opening
 * goes to the group (or the group list); the close button hides it for the rest of today.
 */
@Composable
internal fun TrainedTodayBannerCard(
    banner: TrainedTodayBanner,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 12.dp)
            ) {
                Text(
                    text = trainedTodayText(banner.names),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.trained_today_open))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.trained_today_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 1 name, 2 names, or 2 names and "N more". */
@Composable
internal fun trainedTodayText(names: List<String>): String {
    val resources = LocalResources.current
    return when (names.size) {
        0 -> ""
        1 -> stringResource(R.string.trained_today_one, names[0])
        2 -> stringResource(R.string.trained_today_two, names[0], names[1])
        else -> resources.getQuantityString(R.plurals.trained_today_more, names.size - 2, names[0], names[1], names.size - 2)
    }
}
