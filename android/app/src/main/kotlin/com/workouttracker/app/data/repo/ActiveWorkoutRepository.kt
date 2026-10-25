package com.workouttracker.app.data.repo

import com.workouttracker.app.data.db.ActiveWorkoutEntity
import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.domain.ActiveWorkout
import com.workouttracker.domain.Phase
import com.workouttracker.domain.Plan
import com.workouttracker.domain.PlanGroup
import com.workouttracker.domain.Session
import com.workouttracker.domain.SetOutcome
import com.workouttracker.domain.WorkoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

sealed interface WorkoutEvent {
    /** A rest countdown reached zero (fires once per rest, whether or not it auto-advances). */
    data object RestEnded : WorkoutEvent

    /** A timed hold reached zero and the set was logged. */
    data object HoldEnded : WorkoutEvent
}

/**
 * Owns the in-progress workout. Every change is written to Room so the workout survives the
 * process being killed. All transitions are delegated to the pure [WorkoutEngine].
 */
class ActiveWorkoutRepository(
    db: AppDatabase,
    private val sessions: SessionRepository,
    private val plans: PlanRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.activeWorkoutDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val loaded = MutableStateFlow(false)
    /** False until the saved workout (if any) has been read from disk. */
    val isLoaded: StateFlow<Boolean> = loaded.asStateFlow()
    private val _state = MutableStateFlow<ActiveWorkout?>(null)
    val state: StateFlow<ActiveWorkout?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    /** Read on every tick so a settings change applies to the rest already running. */
    var autoAdvanceRest: () -> Boolean = { true }

    private var alertedRestEndsAt: Long? = null

    init {
        scope.launch {
            _state.value = dao.get()?.let { entity ->
                runCatching { json.decodeFromString(ActiveWorkout.serializer(), entity.json) }.getOrNull()
            }
            loaded.value = true
            tickLoop()
        }
    }

    fun now(): Long = clock()

    private suspend fun awaitLoaded() {
        loaded.first { it }
    }

    /** Writes to disk before publishing, so observers never see a state that isn't persisted. */
    private suspend fun persist(workout: ActiveWorkout?) {
        if (workout == null) dao.clear()
        else dao.put(ActiveWorkoutEntity(json = json.encodeToString(ActiveWorkout.serializer(), workout)))
        _state.value = workout
    }

    /** Applies [transform] to the active workout, if there is one. */
    suspend fun update(transform: (ActiveWorkout, Long) -> ActiveWorkout): ActiveWorkout? {
        awaitLoaded()
        return mutex.withLock {
            val current = _state.value ?: return@withLock null
            val next = transform(current, clock())
            if (next != current) persist(next)
            next
        }
    }

    fun launchUpdate(transform: (ActiveWorkout, Long) -> ActiveWorkout) {
        scope.launch { update(transform) }
    }

    suspend fun start(plan: Plan) {
        awaitLoaded()
        mutex.withLock { persist(WorkoutEngine.start(plan, clock())) }
    }

    /** Starts from groups that aren't a saved plan (e.g. repeating a past session). */
    suspend fun startAdHoc(name: String, groups: List<PlanGroup>) {
        awaitLoaded()
        mutex.withLock {
            persist(WorkoutEngine.start(Plan(id = "", name = name, groups = groups), clock()).copy(planId = null))
        }
    }

    suspend fun startEmpty() {
        awaitLoaded()
        mutex.withLock { persist(WorkoutEngine.startEmpty(clock())) }
    }

    suspend fun discard() {
        awaitLoaded()
        mutex.withLock { persist(null) }
    }

    /**
     * Saves the workout as a session (once) and clears it. Returns null when nothing was done,
     * in which case nothing is saved.
     */
    suspend fun finish(rating: Int? = null, note: String = ""): Session? {
        awaitLoaded()
        return mutex.withLock {
            val current = _state.value ?: return@withLock null
            val session = WorkoutEngine.finish(current, clock(), rating, note)
            val saved = if (session.sets.any { it.outcome == SetOutcome.DONE }) {
                sessions.save(session)
                current.planId?.let { plans.markPerformed(it, session.endedAt) }
                session
            } else null
            persist(null)
            saved
        }
    }

    /** Advances timers. Also called from the alarm receiver when the app is in the background. */
    suspend fun tick() {
        val current = _state.value ?: return
        val now = clock()
        when (val phase = current.phase) {
            is Phase.Resting -> if (now >= phase.endsAt && alertedRestEndsAt != phase.endsAt) {
                alertedRestEndsAt = phase.endsAt
                _events.tryEmit(WorkoutEvent.RestEnded)
            }
            is Phase.Performing -> {
                // Local copy: smart casts don't apply to properties declared in another module.
                val holdEndsAt = phase.holdEndsAt
                if (holdEndsAt != null && now >= holdEndsAt) _events.tryEmit(WorkoutEvent.HoldEnded)
            }
            Phase.Complete -> Unit
        }
        val autoAdvance = autoAdvanceRest()
        update { w, t -> WorkoutEngine.tick(w, t, autoAdvance) }
    }

    private suspend fun tickLoop() {
        while (true) {
            delay(TICK_MS)
            val phase = _state.value?.phase
            val timed = phase is Phase.Resting || (phase is Phase.Performing && phase.holdEndsAt != null)
            if (timed) tick()
        }
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
