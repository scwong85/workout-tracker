package com.workouttracker.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.workouttracker.domain.ActiveWorkout

/** Upcoming cards: tap to do next, reorder, or remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    workout: ActiveWorkout,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.8f)) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val first = workout.cursor
            val last = workout.queue.lastIndex
            if (first > last) {
                Text("Nothing queued.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                itemsIndexed(workout.upcoming, key = { _, s -> s.id }) { offset, set ->
                    val index = first + offset
                    ListItem(
                        modifier = Modifier.clickable(enabled = offset > 0) { onJump(index); onDismiss() },
                        overlineContent = { Text(if (offset == 0) "NOW" else "SET ${set.setOrdinal} OF ${set.totalSetsInGroup}") },
                        headlineContent = { Text(set.summary()) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > first) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                                }
                                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < last) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                                }
                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
