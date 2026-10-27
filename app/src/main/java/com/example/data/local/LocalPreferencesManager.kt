package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lich_van_nien_prefs")

data class LocalAppSettings(
    val themeMode: String = "dark", // Defaulting to sleek dark mode for battery saving & comfort
    val accentPalette: String = "crimson_gold",
    val notifyHolidays: Boolean = true,
    val notifyMung1Ram: Boolean = true,
    val defaultReminderHour: Int = 8
)

class LocalPreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val NOTIFY_HOLIDAYS = booleanPreferencesKey("notify_holidays")
        val NOTIFY_MUNG1_RAM = booleanPreferencesKey("notify_mung1_ram")
        val DEFAULT_REMINDER_HOUR = intPreferencesKey("default_reminder_hour")
    }

    val settingsFlow: Flow<LocalAppSettings> = context.dataStore.data.map { prefs ->
        LocalAppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: "dark",
            accentPalette = prefs[Keys.ACCENT_PALETTE] ?: "crimson_gold",
            notifyHolidays = prefs[Keys.NOTIFY_HOLIDAYS] ?: true,
            notifyMung1Ram = prefs[Keys.NOTIFY_MUNG1_RAM] ?: true,
            defaultReminderHour = prefs[Keys.DEFAULT_REMINDER_HOUR] ?: 8
        )
    }

    suspend fun updateSettings(
        themeMode: String? = null,
        accentPalette: String? = null,
        notifyHolidays: Boolean? = null,
        notifyMung1Ram: Boolean? = null,
        defaultReminderHour: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            themeMode?.let { prefs[Keys.THEME_MODE] = it }
            accentPalette?.let { prefs[Keys.ACCENT_PALETTE] = it }
            notifyHolidays?.let { prefs[Keys.NOTIFY_HOLIDAYS] = it }
            notifyMung1Ram?.let { prefs[Keys.NOTIFY_MUNG1_RAM] = it }
            defaultReminderHour?.let { prefs[Keys.DEFAULT_REMINDER_HOUR] = it }
        }
    }
}
