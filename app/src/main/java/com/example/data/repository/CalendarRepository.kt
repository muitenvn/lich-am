package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.CalendarEvent
import com.example.data.model.SharedSchedule
import com.example.data.model.UserPreference
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CalendarRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Người dùng cần đăng nhập Google trước khi truy cập dữ liệu đám mây.")
    }

    fun observeEvents(userId: String): Flow<List<CalendarEvent>> {
        val path = "users/$userId/events"
        return db.collection("users").document(userId).collection("events")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CalendarEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getUserEvents(userId: String = requireUserId()): Result<List<CalendarEvent>> {
        val path = "users/$userId/events"
        return try {
            val snapshot = db.collection("users").document(userId).collection("events")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Result.success(
                snapshot.toObjects(
                    CalendarEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getEventById(targetUserId: String, eventId: String): Result<CalendarEvent?> {
        val path = "users/$targetUserId/events/$eventId"
        return try {
            val doc = db.collection("users").document(targetUserId).collection("events").document(eventId)
                .get()
                .await()
            Result.success(
                doc.toObject(
                    CalendarEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createEvent(event: CalendarEvent): Result<String> {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("events")
        val docRef = if (event.id.isNotBlank()) collectionRef.document(event.id) else collectionRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to event.title.trim().take(120).ifEmpty { "Sự kiện mới" },
            "notes" to event.notes.trim().take(500),
            "solarDay" to event.solarDay.coerceIn(1L, 31L),
            "solarMonth" to event.solarMonth.coerceIn(1L, 12L),
            "solarYear" to event.solarYear.coerceIn(1900L, 2100L),
            "lunarDay" to event.lunarDay.coerceIn(1L, 30L),
            "lunarMonth" to event.lunarMonth.coerceIn(1L, 12L),
            "lunarYear" to event.lunarYear.coerceIn(1900L, 2100L),
            "isLunarLeapMonth" to event.isLunarLeapMonth,
            "isLunarRecurring" to event.isLunarRecurring,
            "category" to event.category,
            "reminderMinutesBefore" to event.reminderMinutesBefore.coerceIn(0L, 43200L),
            "syncedToGoogle" to event.syncedToGoogle,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateEvent(event: CalendarEvent): Result<Unit> {
        val uid = requireUserId()
        require(event.id.isNotBlank()) { "Event ID must not be blank for update" }
        requireNotNull(event.createdAt) { "Event createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("events").document(event.id)
        return try {
            docRef.set(event.copy(userId = uid, updatedAt = null)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("events").document(eventId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserPreference(userId: String): Flow<UserPreference?> {
        val docRef = db.collection("users").document(userId).collection("settings").document("preferences")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserPreference::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun saveUserPreference(pref: UserPreference, existingCreatedAt: com.google.firebase.Timestamp?): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        return try {
            if (existingCreatedAt == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "themeMode" to pref.themeMode,
                    "accentPalette" to pref.accentPalette,
                    "notifyHolidays" to pref.notifyHolidays,
                    "notifyMung1Ram" to pref.notifyMung1Ram,
                    "defaultReminderHour" to pref.defaultReminderHour.coerceIn(0L, 23L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.set(
                    pref.copy(
                        id = "preferences",
                        userId = uid,
                        createdAt = existingCreatedAt,
                        updatedAt = null
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSharedByMe(ownerId: String): Flow<List<SharedSchedule>> {
        val path = "shared_schedules"
        return db.collection(path)
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SharedSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeSharedWithMe(recipientEmail: String): Flow<List<SharedSchedule>> {
        val path = "shared_schedules"
        return db.collection(path)
            .whereEqualTo("recipientEmail", recipientEmail.trim().lowercase())
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SharedSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createSharedSchedule(schedule: SharedSchedule): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("shared_schedules").document()
        val payload = mapOf(
            "ownerId" to uid,
            "ownerName" to schedule.ownerName.trim().take(100).ifEmpty { "Bạn bè" },
            "recipientEmail" to schedule.recipientEmail.trim().lowercase().take(150),
            "title" to schedule.title.trim().take(120),
            "notes" to schedule.notes.trim().take(500),
            "solarDateIso" to schedule.solarDateIso.trim().take(20),
            "lunarDateDisplay" to schedule.lunarDateDisplay.trim().take(60),
            "category" to schedule.category,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedSchedule(shareId: String): Result<Unit> {
        val docRef = db.collection("shared_schedules").document(shareId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
