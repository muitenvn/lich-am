package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CalendarEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val solarDay: Long = 1L,
    val solarMonth: Long = 1L,
    val solarYear: Long = 2026L,
    val lunarDay: Long = 1L,
    val lunarMonth: Long = 1L,
    val lunarYear: Long = 2026L,
    @get:PropertyName("isLunarLeapMonth")
    @set:PropertyName("isLunarLeapMonth")
    var isLunarLeapMonth: Boolean = false,
    @get:PropertyName("isLunarRecurring")
    @set:PropertyName("isLunarRecurring")
    var isLunarRecurring: Boolean = true,
    val category: String = "personal",
    val reminderMinutesBefore: Long = 60L,
    val syncedToGoogle: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPreference(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = "system",
    val accentPalette: String = "crimson_gold",
    val notifyHolidays: Boolean = true,
    val notifyMung1Ram: Boolean = true,
    val defaultReminderHour: Long = 8L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SharedSchedule(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val recipientEmail: String = "",
    val title: String = "",
    val notes: String = "",
    val solarDateIso: String = "",
    val lunarDateDisplay: String = "",
    val category: String = "personal",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
