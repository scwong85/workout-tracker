package com.workouttracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One swipe on a list row. [dismiss] = true removes the row (delete); false runs the action and
 * lets the row spring back (start, repeat).
 */
data class RowSwipe(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val dismiss: Boolean,
    val onCommit: () -> Unit,
)

/** A list row that slides to reveal a coloured action: [start] on a right swipe, [end] on a left swipe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(
    start: RowSwipe?,
    end: RowSwipe?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // The state is remembered once, so it must read the latest actions rather than capture them.
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentStart?.let { it.onCommit(); it.dismiss } ?: false
                SwipeToDismissBoxValue.EndToStart -> currentEnd?.let { it.onCommit(); it.dismiss } ?: false
                // Settling back to rest must always be allowed.
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        backgroundContent = {
            val direction = state.dismissDirection
            val swipe = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> start
                SwipeToDismissBoxValue.EndToStart -> end
                SwipeToDismissBoxValue.Settled -> null
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(swipe?.color ?: Color.Transparent)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (swipe != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(swipe.icon, contentDescription = null, tint = Color.White)
                        Text(swipe.label, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        content = content,
    )
}
