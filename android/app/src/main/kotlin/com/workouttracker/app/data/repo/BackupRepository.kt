package com.workouttracker.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.workouttracker.app.data.db.AppDatabase
import com.workouttracker.app.data.db.setEntities
import com.workouttracker.app.data.db.toEntity
import com.workouttracker.app.data.db.toRows
import com.workouttracker.domain.Backup
import com.workouttracker.domain.ImportData
import com.workouttracker.domain.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val exercises: ExerciseRepository,
    private val plans: PlanRepository,
    private val sessions: SessionRepository,
    private val activeWorkout: ActiveWorkoutRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun exportJson(): String {
        val ex = exercises.observeAll().first()
        val pl = plans.observeAll().first()
        val se = sessions.getAll()
        return Backup.encode(ex, pl, se, clock())
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = exportJson()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file for writing")
        out.use { it.write(text.toByteArray()) }
    }

    /** Parses a file without touching the database, so the user can preview it first. */
    suspend fun read(uri: Uri): ImportData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't open the file")
        Backup.decode(text)
    }

    /**
     * Applies an import in one transaction. Merge upserts by id; replace wipes first.
     * A safety copy of the current data is written to app storage beforehand.
     */
    suspend fun apply(data: ImportData, replace: Boolean): File {
        val safety = writeSafetyCopy()
        db.withTransaction {
            if (replace) clearAll()
            db.exerciseDao().upsertAll(data.exercises.map { it.toEntity() })
            data.plans.forEach { p ->
                val rows = p.toRows()
                db.planDao().replace(rows.plan, rows.groups, rows.items)
            }
            data.sessions.forEach { s -> db.sessionDao().replace(s.toEntity(), s.setEntities()) }
            db.exerciseDao().insertIgnore(Presets.exercises.map { it.toEntity() })
        }
        return safety
    }

    suspend fun deleteAll() {
        writeSafetyCopy()
        activeWorkout.discard()
        db.withTransaction {
            clearAll()
            db.exerciseDao().insertIgnore(Presets.exercises.map { it.toEntity() })
        }
    }

    private suspend fun clearAll() {
        db.sessionDao().clear()
        db.planDao().clear()
        db.exerciseDao().clear()
    }

    private suspend fun writeSafetyCopy(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        // Keep the five most recent safety copies.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        File(dir, "before-import-${clock()}.json").apply { writeText(exportJson()) }
    }
}
