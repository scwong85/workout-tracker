package com.workouttracker.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workouttracker.app.ui.theme.ThemeMode
import com.workouttracker.domain.PlanDefaults
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSec: Int = PlanDefaults.REST_SEC,
    val autoAdvanceRest: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val weeklyGoal: Int = 3,
    val askedNotifications: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val unit = stringPreferencesKey("weight_unit")
        val rest = intPreferencesKey("default_rest_sec")
        val autoAdvance = booleanPreferencesKey("auto_advance_rest")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val askedNotifications = booleanPreferencesKey("asked_notifications")
    }

    /** Eagerly started so non-UI readers (timers, alerts) always see the current value. */
    val settings: StateFlow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            weightUnit = p[Keys.unit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: d.weightUnit,
            defaultRestSec = p[Keys.rest] ?: d.defaultRestSec,
            autoAdvanceRest = p[Keys.autoAdvance] ?: d.autoAdvanceRest,
            sound = p[Keys.sound] ?: d.sound,
            vibration = p[Keys.vibration] ?: d.vibration,
            keepScreenOn = p[Keys.keepScreenOn] ?: d.keepScreenOn,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            weeklyGoal = p[Keys.weeklyGoal] ?: d.weeklyGoal,
            askedNotifications = p[Keys.askedNotifications] ?: d.askedNotifications,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun setWeightUnit(v: WeightUnit) = store.edit { it[Keys.unit] = v.name }
    suspend fun setDefaultRest(v: Int) = store.edit { it[Keys.rest] = v.coerceIn(0, 600) }
    suspend fun setAutoAdvanceRest(v: Boolean) = store.edit { it[Keys.autoAdvance] = v }
    suspend fun setSound(v: Boolean) = store.edit { it[Keys.sound] = v }
    suspend fun setVibration(v: Boolean) = store.edit { it[Keys.vibration] = v }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[Keys.keepScreenOn] = v }
    suspend fun setTheme(v: ThemeMode) = store.edit { it[Keys.theme] = v.name }
    suspend fun setDynamicColor(v: Boolean) = store.edit { it[Keys.dynamicColor] = v }
    suspend fun setWeeklyGoal(v: Int) = store.edit { it[Keys.weeklyGoal] = v.coerceIn(1, 7) }
    suspend fun setAskedNotifications() = store.edit { it[Keys.askedNotifications] = true }
}
