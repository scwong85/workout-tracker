package com.workouttracker.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

enum class BackupSource { ANDROID, WEB }

data class ImportData(
    val source: BackupSource,
    val exercises: List<Exercise>,
    val plans: List<Plan>,
    val sessions: List<Session>,
)

class BackupFormatException(message: String) : Exception(message)

/** This app's own backup file. Plain domain objects, versioned for future migrations. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val exercises: List<Exercise>,
    val plans: List<Plan>,
    val sessions: List<Session>,
) {
    companion object {
        const val FORMAT = "workout-tracker-android"
    }
}

object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(exercises: List<Exercise>, plans: List<Plan>, sessions: List<Session>, now: Long): String =
        json.encodeToString(BackupFile.serializer(), BackupFile(exportedAt = now, exercises = exercises, plans = plans, sessions = sessions))

    /** Reads either this app's backup or the original web app's export. */
    fun decode(text: String, newId: () -> String = Ids::new): ImportData {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupFormatException("This file isn't a workout backup (not valid JSON).")
        }
        return when {
            (root["format"] as? JsonPrimitive)?.content == BackupFile.FORMAT -> {
                val file = try {
                    json.decodeFromJsonElement(BackupFile.serializer(), root)
                } catch (e: Exception) {
                    throw BackupFormatException("This backup is damaged or from a newer version of the app.")
                }
                ImportData(BackupSource.ANDROID, file.exercises, file.plans, file.sessions)
            }
            root.containsKey("workoutSessions") || root.containsKey("workoutPlans") -> WebImporter.import(root, newId)
            else -> throw BackupFormatException("This file isn't a workout backup.")
        }
    }
}

/**
 * Maps the web app's IndexedDB export (see docs/01-FUNCTIONALITY.md §2) onto the Android model.
 * Presets are matched by name so they aren't duplicated; references to exercises that no longer
 * exist become archived exercises so history stays intact. The web app's `skippedSets` count was
 * unreliable and is dropped.
 */
internal object WebImporter {
    fun import(root: JsonObject, newId: () -> String): ImportData {
        val presetsByName = Presets.exercises.associateBy { it.name.lowercase() }
        val exercises = linkedMapOf<String, Exercise>() // keyed by new id
        val idMap = mutableMapOf<String, String>() // web id -> new id

        root.array("exercises").forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val webId = o.str("id") ?: return@forEach
            val name = o.str("name")?.trim().orEmpty().ifBlank { return@forEach }
            val preset = presetsByName[name.lowercase()]
            val exercise = if (preset != null && o.bool("isPreset") != false) {
                preset
            } else {
                Exercise(
                    id = webId,
                    name = name,
                    type = type(o),
                    category = category(o.str("category")),
                    defaultWeightKg = o.num("defaultWeight") ?: 0.0,
                    isPreset = false,
                    createdAt = o.long("createdAt") ?: 0,
                    updatedAt = o.long("createdAt") ?: 0,
                )
            }
            idMap[webId] = exercise.id
            exercises[exercise.id] = exercise
        }

        fun resolve(webId: String?, name: String?, t: ExerciseType): Exercise {
            val mapped = webId?.let { idMap[it] }?.let { exercises[it] }
            if (mapped != null) return mapped
            val byName = name?.lowercase()?.let { n -> presetsByName[n] ?: exercises.values.firstOrNull { it.name.lowercase() == n } }
            if (byName != null) {
                exercises.putIfAbsent(byName.id, byName)
                webId?.let { idMap[it] = byName.id }
                return byName
            }
            val ghost = Exercise(
                id = webId ?: newId(),
                name = name?.ifBlank { null } ?: "Unknown exercise",
                type = t,
                category = Category.FULL_BODY,
                isArchived = true,
            )
            exercises[ghost.id] = ghost
            webId?.let { idMap[it] = ghost.id }
            return ghost
        }

        val plans = root.array("workoutPlans").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val groups = o.array("exercises").mapNotNull { g ->
                val go = g as? JsonObject ?: return@mapNotNull null
                val items = go.array("exercises").mapNotNull { i ->
                    val io = i as? JsonObject ?: return@mapNotNull null
                    val t = type(io)
                    val ex = resolve(io.str("exerciseId"), io.str("exerciseName"), t)
                    PlanItem(
                        id = newId(),
                        exerciseId = ex.id,
                        exerciseName = ex.name,
                        type = ex.type,
                        category = ex.category,
                        sets = (io.long("sets") ?: 1).toInt().coerceIn(1, 20),
                        reps = (io.long("reps") ?: PlanDefaults.REPS.toLong()).toInt().coerceAtLeast(1),
                        durationSec = (io.long("duration") ?: PlanDefaults.DURATION_SEC.toLong()).toInt().coerceAtLeast(1),
                        weightKg = io.num("weight") ?: 0.0,
                    )
                }
                if (items.isEmpty()) null
                else PlanGroup(id = newId(), items = items, restAfterSec = (go.long("restAfterSet") ?: PlanDefaults.REST_SEC.toLong()).toInt().coerceIn(0, 600))
            }
            Plan(
                id = o.str("id") ?: newId(),
                name = o.str("name")?.ifBlank { null } ?: "Imported plan",
                groups = groups,
                createdAt = o.long("createdAt") ?: 0,
                updatedAt = o.long("updatedAt") ?: o.long("createdAt") ?: 0,
            )
        }
        val planIds = plans.map { it.id }.toSet()

        val sessions = root.array("workoutSessions").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val start = o.long("startTime") ?: return@mapNotNull null
            val ordinals = mutableMapOf<String, Int>() // exercise -> group ordinal, by first appearance
            val counts = mutableMapOf<String, Int>()
            val sets = o.array("completedSets").mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                val t = type(so)
                val ex = resolve(so.str("exerciseId"), so.str("exerciseName"), t)
                val ordinal = ordinals.getOrPut(ex.id) { ordinals.size }
                val setNo = (counts[ex.id] ?: 0) + 1
                counts[ex.id] = setNo
                val reps = (so.long("reps") ?: 0).toInt()
                val duration = (so.long("duration") ?: 0).toInt()
                SessionSet(
                    id = newId(),
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    type = t,
                    groupOrdinal = ordinal,
                    setOrdinal = setNo,
                    targetReps = if (t == ExerciseType.REPS) reps else 0,
                    actualReps = if (t == ExerciseType.REPS) reps else 0,
                    targetDurationSec = if (t == ExerciseType.DURATION) duration else 0,
                    actualDurationSec = if (t == ExerciseType.DURATION) duration else 0,
                    weightKg = so.num("weight") ?: 0.0,
                    outcome = SetOutcome.DONE,
                    completedAt = so.long("completedAt") ?: start,
                )
            }
            Session(
                id = o.str("id") ?: newId(),
                planId = o.str("workoutPlanId")?.takeIf { it in planIds },
                planName = o.str("workoutPlanName")?.ifBlank { null } ?: "Workout",
                startedAt = start,
                endedAt = o.long("endTime") ?: start,
                status = SessionStatus.COMPLETED,
                sets = sets,
            )
        }

        return ImportData(BackupSource.WEB, exercises.values.toList(), plans, sessions)
    }

    private fun type(o: JsonObject): ExerciseType = if (o.long("type") == 2L) ExerciseType.DURATION else ExerciseType.REPS

    private fun category(raw: String?): Category = when (raw?.lowercase()) {
        "chest" -> Category.CHEST
        "back" -> Category.BACK
        "legs" -> Category.LEGS
        "core" -> Category.CORE
        "arms" -> Category.ARMS
        "shoulders" -> Category.SHOULDERS
        else -> Category.FULL_BODY
    }

    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.str(key: String): String? = prim(key)?.content
    private fun JsonObject.num(key: String): Double? = prim(key)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
    private fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.content.toLongOrNull() }
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
}
