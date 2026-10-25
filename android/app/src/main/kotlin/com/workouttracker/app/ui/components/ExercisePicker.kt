package com.workouttracker.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workouttracker.domain.Category
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType

/**
 * Searchable multi-select exercise picker shared by the plan editor and the workout player.
 * Selection order is preserved and survives filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<Exercise>, asSuperset: Boolean) -> Unit,
    title: String = "Add exercises",
    subtitleFor: (Exercise) -> String? = { null },
    footer: (@Composable () -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var asSuperset by rememberSaveable { mutableStateOf(false) }

    val visible = exercises
        .filter { !it.isArchived }
        .filter { category == null || it.category == category }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
    val byId = exercises.associateBy { it.id }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                }
                items(Category.entries) { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.id }) { ex ->
                    val checked = ex.id in selected
                    ListItem(
                        modifier = Modifier.clickable { if (checked) selected.remove(ex.id) else selected.add(ex.id) },
                        leadingContent = { CategoryAvatar(ex.category) },
                        headlineContent = { Text(ex.name) },
                        supportingContent = {
                            val type = if (ex.type == ExerciseType.REPS) "Reps" else "Timed"
                            Text(listOfNotNull(type, subtitleFor(ex)).joinToString(" · "))
                        },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
                if (visible.isEmpty()) {
                    item { Text("No exercises match", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            HorizontalDivider()
            if (footer != null && selected.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { footer() }
            }
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected.size >= 2) {
                        Switch(checked = asSuperset, onCheckedChange = { asSuperset = it })
                        Text("Superset", modifier = Modifier.padding(start = 8.dp).weight(1f))
                    } else {
                        Text(
                            if (selected.isEmpty()) "Select one or more" else "1 selected",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = { onConfirm(selected.mapNotNull { byId[it] }, asSuperset && selected.size >= 2) },
                    ) { Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}") }
                }
            }
        }
    }
}
