package com.workouttracker.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workouttracker.app.AppContainer
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.AdjustField
import com.workouttracker.domain.Exercise
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanUpdates
import com.workouttracker.domain.Records
import com.workouttracker.domain.Session
import com.workouttracker.domain.SessionSet
import com.workouttracker.domain.WorkoutEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class PlayerViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.activeWorkout

    val workout: StateFlow<ActiveWorkout?> = repo.state

    val exercises: StateFlow<List<Exercise>> = container.exercises.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<Session>> = container.sessions.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Best set from the most recent session per exercise, for the "Last time" cue. */
    val lastTimes: StateFlow<Map<String, SessionSet>> = history
        .map { sessions ->
            val ids = sessions.flatMap { s -> s.sets.map { it.exerciseId } }.toSet()
            ids.mapNotNull { id -> Records.lastPerformance(sessions, id)?.let { id to it } }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun complete() = repo.launchUpdate { w, now -> WorkoutEngine.complete(w, now) }
    fun skip() = repo.launchUpdate { w, now -> WorkoutEngine.skip(w, now) }
    fun undo() = repo.launchUpdate { w, _ -> WorkoutEngine.undo(w) }
    fun adjust(index: Int, field: AdjustField, delta: Double) =
        repo.launchUpdate { w, _ -> WorkoutEngine.adjust(w, index, field, delta) }
    fun startHold() = repo.launchUpdate { w, now -> WorkoutEngine.startHold(w, now) }
    fun cancelHold() = repo.launchUpdate { w, _ -> WorkoutEngine.cancelHold(w) }
    fun adjustRest(deltaSec: Int) = repo.launchUpdate { w, now -> WorkoutEngine.adjustRest(w, deltaSec, now) }
    fun skipRest() = repo.launchUpdate { w, _ -> WorkoutEngine.skipRest(w) }
    fun removeQueued(index: Int) = repo.launchUpdate { w, _ -> WorkoutEngine.removeQueued(w, index) }
    fun moveQueued(from: Int, to: Int) = repo.launchUpdate { w, _ -> WorkoutEngine.moveQueued(w, from, to) }
    fun jumpTo(index: Int) = repo.launchUpdate { w, _ -> WorkoutEngine.jumpTo(w, index) }

    fun inject(selected: List<Exercise>, asSuperset: Boolean, sets: Int, restSec: Int) =
        repo.launchUpdate { w, _ -> WorkoutEngine.inject(w, selected, asSuperset, sets, restSec) }

    val isLoaded: StateFlow<Boolean> = repo.isLoaded

    /** App scope: the screen closes as soon as the workout is gone, which must not cancel the write. */
    fun discard() {
        container.appScope.launch { repo.discard() }
    }

    /**
     * Optionally writes a plan from today's workout, then saves the session.
     * Returns the saved session, or null if nothing was logged.
     */
    suspend fun finish(rating: Int?, note: String, saveAsPlanName: String?, updatePlan: Boolean): Session? =
        container.appScope.async { finishNow(rating, note, saveAsPlanName, updatePlan) }.await()

    private suspend fun finishNow(rating: Int?, note: String, saveAsPlanName: String?, updatePlan: Boolean): Session? {
        val w = workout.value ?: return null
        if (saveAsPlanName != null && saveAsPlanName.isNotBlank()) {
            val groups = WorkoutEngine.toPlanGroups(w)
            if (groups.isNotEmpty()) container.plans.save(Plan(id = "", name = saveAsPlanName, groups = groups))
        }
        if (updatePlan && w.planId != null) {
            container.plans.get(w.planId)?.let { container.plans.save(PlanUpdates.applyActuals(it, w.log)) }
        }
        return repo.finish(rating, note)
    }
}
