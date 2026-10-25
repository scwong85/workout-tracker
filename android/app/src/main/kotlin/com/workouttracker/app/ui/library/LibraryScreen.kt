package com.workouttracker.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.data.repo.ExerciseRepository
import com.workouttracker.app.ui.appViewModel
import com.workouttracker.app.ui.components.CategoryAvatar
import com.workouttracker.app.ui.components.EmptyState
import com.workouttracker.domain.Category
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class LibraryFilter(val label: String) { REPS("Reps"), TIMED("Timed"), CUSTOM("Custom") }

data class LibraryState(
    val query: String = "",
    val category: Category? = null,
    val filters: Set<LibraryFilter> = emptySet(),
    val groups: List<Pair<Category, List<Exercise>>> = emptyList(),
    val loaded: Boolean = false,
)

class LibraryViewModel(exercises: ExerciseRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<Category?>(null)
    private val filters = MutableStateFlow<Set<LibraryFilter>>(emptySet())

    val state: StateFlow<LibraryState> = combine(exercises.observeActive(), query, category, filters) { all, q, c, f ->
        val visible = all
            .filter { c == null || it.category == c }
            .filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
            .filter { LibraryFilter.REPS !in f || it.type == ExerciseType.REPS }
            .filter { LibraryFilter.TIMED !in f || it.type == ExerciseType.DURATION }
            .filter { LibraryFilter.CUSTOM !in f || !it.isPreset }
        LibraryState(
            query = q, category = c, filters = f, loaded = true,
            groups = Category.entries.mapNotNull { cat ->
                visible.filter { it.category == cat }.takeIf { it.isNotEmpty() }?.let { cat to it }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun setQuery(q: String) { query.value = q }
    fun toggleCategory(c: Category) = category.update { if (it == c) null else c }
    fun toggleFilter(f: LibraryFilter) = filters.update { current ->
        val next = if (f in current) current - f else current + f
        // Reps and Timed are mutually exclusive.
        when (f) {
            LibraryFilter.REPS -> next - LibraryFilter.TIMED
            LibraryFilter.TIMED -> next - LibraryFilter.REPS
            LibraryFilter.CUSTOM -> next
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
) {
    val vm = appViewModel { LibraryViewModel(it.exercises) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateExercise,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New exercise") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text("Search exercises") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LibraryFilter.entries) { f ->
                            FilterChip(selected = f in state.filters, onClick = { vm.toggleFilter(f) }, label = { Text(f.label) })
                        }
                        items(Category.entries) { c ->
                            FilterChip(selected = state.category == c, onClick = { vm.toggleCategory(c) }, label = { Text(c.label) })
                        }
                    }
                }
            }
            if (state.loaded && state.groups.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.SearchOff, "No exercises found", "Try a different search or filter, or create your own exercise.")
                }
            }
            state.groups.forEach { (category, list) ->
                stickyHeader(key = category.name) {
                    Text(
                        category.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(list, key = { it.id }) { ex ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenExercise(ex.id) },
                        leadingContent = { CategoryAvatar(ex.category) },
                        headlineContent = { Text(ex.name) },
                        supportingContent = { Text(if (ex.type == ExerciseType.REPS) "Reps" else "Timed") },
                        trailingContent = if (!ex.isPreset) {
                            { Text("Custom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        } else null,
                    )
                }
            }
        }
    }
}
