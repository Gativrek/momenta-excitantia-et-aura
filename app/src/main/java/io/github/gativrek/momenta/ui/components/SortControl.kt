package io.github.gativrek.momenta.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gativrek.momenta.R
import io.github.gativrek.momenta.data.sorting.SortField
import io.github.gativrek.momenta.data.sorting.SortOrder

/**
 * What a list is sorted by, and which way. The name opens the choices (picking the current
 * one again flips it); the arrow flips the direction: up is ascending, down descending.
 */
@Composable
fun SortControl(
    order: SortOrder,
    fields: List<SortField>,
    onChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { open = true }) {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(order.field.label), modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                fields.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(stringResource(field.label)) },
                        trailingIcon = {
                            if (field == order.field) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                            }
                        },
                        onClick = {
                            open = false
                            onChange(
                                if (field == order.field) {
                                    order.copy(descending = !order.descending)
                                } else {
                                    SortOrder(field, field.descendingFirst)
                                },
                            )
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onChange(order.copy(descending = !order.descending)) }) {
            Icon(
                painterResource(R.drawable.ic_arrow_up),
                contentDescription =
                    stringResource(
                        if (order.descending) R.string.sort_descending else R.string.sort_ascending,
                    ),
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (order.descending) 180f else 0f },
            )
        }
    }
}

@get:StringRes
private val SortField.label: Int
    get() =
        when (this) {
            SortField.MANUAL -> R.string.sort_manual
            SortField.NAME -> R.string.sort_name
            SortField.ARTIST -> R.string.sort_artist
            SortField.ALBUM -> R.string.sort_album
            SortField.DATE_ADDED -> R.string.sort_date_added
            SortField.LENGTH -> R.string.sort_length
            SortField.FILE_SIZE -> R.string.sort_file_size
            SortField.TIME_LISTENED -> R.string.sort_time_listened
            SortField.TIMES_PLAYED -> R.string.sort_times_played
            SortField.LAST_PLAYED -> R.string.sort_last_played
            SortField.MOMENTS -> R.string.sort_moments
            SortField.MOMENTS_HEARD -> R.string.sort_moments_heard
        }
