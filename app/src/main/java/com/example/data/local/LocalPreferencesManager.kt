package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CalendarEvent
import com.example.domain.LunarCalendarEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "lich_van_nien_prefs")

data class LocalAppSettings(
    val themeMode: String = "dark",
    val accentPalette: String = "crimson_gold",
    val notifyHolidays: Boolean = true,
    val notifyMung1Ram: Boolean = true,
    val defaultReminderHour: Int = 8
)

data class LocalAccountSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
) {
    val isSignedIn: Boolean get() = uid.isNotBlank() || email.isNotBlank()
}

class LocalPreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val NOTIFY_HOLIDAYS = booleanPreferencesKey("notify_holidays")
        val NOTIFY_MUNG1_RAM = booleanPreferencesKey("notify_mung1_ram")
        val DEFAULT_REMINDER_HOUR = intPreferencesKey("default_reminder_hour")

        val ACCOUNT_UID = stringPreferencesKey("account_uid")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ACCOUNT_NAME = stringPreferencesKey("account_name")

        val LOCAL_EVENTS_JSON = stringPreferencesKey("local_events_json")
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

    val accountSessionFlow: Flow<LocalAccountSession> = context.dataStore.data.map { prefs ->
        LocalAccountSession(
            uid = prefs[Keys.ACCOUNT_UID] ?: "",
            email = prefs[Keys.ACCOUNT_EMAIL] ?: "",
            displayName = prefs[Keys.ACCOUNT_NAME] ?: ""
        )
    }

    val localEventsFlow: Flow<List<CalendarEvent>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LOCAL_EVENTS_JSON]
        if (raw.isNullOrBlank()) {
            defaultSeedEvents()
        } else {
            parseEventsJson(raw)
        }
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

    suspend fun saveAccountSession(uid: String, email: String, displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCOUNT_UID] = uid
            prefs[Keys.ACCOUNT_EMAIL] = email
            prefs[Keys.ACCOUNT_NAME] = displayName
        }
    }

    suspend fun clearAccountSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCOUNT_UID)
            prefs.remove(Keys.ACCOUNT_EMAIL)
            prefs.remove(Keys.ACCOUNT_NAME)
        }
    }

    suspend fun addLocalEvent(event: CalendarEvent): CalendarEvent {
        val withId = if (event.id.isBlank()) {
            event.copy(id = "local_${UUID.randomUUID()}")
        } else {
            event
        }
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_EVENTS_JSON]?.let { parseEventsJson(it) } ?: defaultSeedEvents()
            val updated = (current.filterNot { it.id == withId.id } + withId)
            prefs[Keys.LOCAL_EVENTS_JSON] = encodeEventsJson(updated)
        }
        return withId
    }

    suspend fun deleteLocalEvent(eventId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_EVENTS_JSON]?.let { parseEventsJson(it) } ?: defaultSeedEvents()
            val updated = current.filterNot { it.id == eventId }
            prefs[Keys.LOCAL_EVENTS_JSON] = encodeEventsJson(updated)
        }
    }

    suspend fun markLocalEventSyncedToGoogle(eventId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_EVENTS_JSON]?.let { parseEventsJson(it) } ?: defaultSeedEvents()
            val updated = current.map { if (it.id == eventId) it.copy(syncedToGoogle = true) else it }
            prefs[Keys.LOCAL_EVENTS_JSON] = encodeEventsJson(updated)
        }
    }

    private fun defaultSeedEvents(): List<CalendarEvent> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val lunarNow = LunarCalendarEngine.solarToLunar(
            cal.get(Calendar.DAY_OF_MONTH),
            month,
            year
        )
        val ramSolar = LunarCalendarEngine.lunarToSolar(15, lunarNow.month, lunarNow.year, lunarNow.isLeapMonth)
        val nextLunarMonth = if (lunarNow.month == 12) 1 else lunarNow.month + 1
        val nextLunarYear = if (lunarNow.month == 12) lunarNow.year + 1 else lunarNow.year
        val mung1Solar = LunarCalendarEngine.lunarToSolar(1, nextLunarMonth, nextLunarYear, false)

        return listOf(
            CalendarEvent(
                id = "seed_ram_hang_thang",
                userId = "local_user",
                title = "Lễ Cúng Rằm Giữa Tháng (15 Âm lịch)",
                notes = "Chuẩn bị hương hoa, trái cây tươi dâng cúng gia tiên ngày Vọng.",
                solarDay = ramSolar.first.toLong(),
                solarMonth = ramSolar.second.toLong(),
                solarYear = ramSolar.third.toLong(),
                lunarDay = 15L,
                lunarMonth = lunarNow.month.toLong(),
                lunarYear = lunarNow.year.toLong(),
                isLunarLeapMonth = lunarNow.isLeapMonth,
                isLunarRecurring = true,
                category = "ancestral",
                reminderMinutesBefore = 60L,
                syncedToGoogle = false
            ),
            CalendarEvent(
                id = "seed_mung1_dau_thang",
                userId = "local_user",
                title = "Lễ Mùng 1 Đầu Tháng Âm Lịch",
                notes = "Ngày Sóc khởi đầu tháng mới bình an, may mắn.",
                solarDay = mung1Solar.first.toLong(),
                solarMonth = mung1Solar.second.toLong(),
                solarYear = mung1Solar.third.toLong(),
                lunarDay = 1L,
                lunarMonth = nextLunarMonth.toLong(),
                lunarYear = nextLunarYear.toLong(),
                isLunarLeapMonth = false,
                isLunarRecurring = true,
                category = "holiday",
                reminderMinutesBefore = 60L,
                syncedToGoogle = false
            )
        )
    }

    private fun parseEventsJson(raw: String): List<CalendarEvent> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CalendarEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CalendarEvent(
                        id = obj.optString("id"),
                        userId = obj.optString("userId", "local_user"),
                        title = obj.optString("title"),
                        notes = obj.optString("notes"),
                        solarDay = obj.optLong("solarDay", 1L),
                        solarMonth = obj.optLong("solarMonth", 1L),
                        solarYear = obj.optLong("solarYear", 2026L),
                        lunarDay = obj.optLong("lunarDay", 1L),
                        lunarMonth = obj.optLong("lunarMonth", 1L),
                        lunarYear = obj.optLong("lunarYear", 2026L),
                        isLunarLeapMonth = obj.optBoolean("isLunarLeapMonth", false),
                        isLunarRecurring = obj.optBoolean("isLunarRecurring", true),
                        category = obj.optString("category", "personal"),
                        reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 60L),
                        syncedToGoogle = obj.optBoolean("syncedToGoogle", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeEventsJson(events: List<CalendarEvent>): String {
        val arr = JSONArray()
        events.forEach { evt ->
            val obj = JSONObject().apply {
                put("id", evt.id)
                put("userId", evt.userId)
                put("title", evt.title)
                put("notes", evt.notes)
                put("solarDay", evt.solarDay)
                put("solarMonth", evt.solarMonth)
                put("solarYear", evt.solarYear)
                put("lunarDay", evt.lunarDay)
                put("lunarMonth", evt.lunarMonth)
                put("lunarYear", evt.lunarYear)
                put("isLunarLeapMonth", evt.isLunarLeapMonth)
                put("isLunarRecurring", evt.isLunarRecurring)
                put("category", evt.category)
                put("reminderMinutesBefore", evt.reminderMinutesBefore)
                put("syncedToGoogle", evt.syncedToGoogle)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
