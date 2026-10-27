package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CalendarEvent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CalendarRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveEvent_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CalendarRepository(firestore)
        val uniqueTitle = "Sự kiện Tết ${UUID.randomUUID()}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createEvent(
                CalendarEvent(
                    title = uniqueTitle,
                    notes = "Chuẩn bị mâm ngũ quả",
                    solarDay = 17L,
                    solarMonth = 2L,
                    solarYear = 2026L,
                    lunarDay = 1L,
                    lunarMonth = 1L,
                    lunarYear = 2026L,
                    category = "holiday"
                )
            )
        }
        assertTrue("Create event should succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val eventId = createResult.getOrThrow()

        val emittedEvents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEvents(aliceUid).first { list -> list.any { it.id == eventId } }
        }
        assertTrue(emittedEvents.any { it.id == eventId && it.title == uniqueTitle })
    }

    @Test
    fun getEventById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CalendarRepository(firestore)
        val eventId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createEvent(
                CalendarEvent(
                    title = "Lịch riêng của Alice",
                    notes = "Bí mật",
                    solarDay = 10L,
                    solarMonth = 5L,
                    solarYear = 2026L,
                    lunarDay = 24L,
                    lunarMonth = 3L,
                    lunarYear = 2026L,
                    category = "personal"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CalendarRepository(firestore)
        val bobResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getEventById(aliceUid, eventId)
        }
        assertTrue("Bob should not read Alice's event", bobResult.isFailure)
        val firestoreEx = bobResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeEvents_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CalendarRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeEvents("unauth_user_123").first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception when unauthenticated", caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caughtException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_lunar@test.com"
        const val BOB_EMAIL = "bob_lunar@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
