package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalPreferencesManager
import com.example.data.model.CalendarEvent
import com.example.data.model.SharedSchedule
import com.example.data.model.UserPreference
import com.example.data.repository.CalendarRepository
import com.example.domain.DayDetailInfo
import com.example.domain.LunarCalendarEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class CalendarViewModel(
    private val repository: CalendarRepository,
    private val prefsManager: LocalPreferencesManager? = null,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserName: String
) : ViewModel() {

    val isSignedIn: Boolean = currentUserId.isNotBlank() || currentUserEmail.isNotBlank()
    private val hasFirebaseAuthSession: Boolean
        get() = try {
            Firebase.auth.currentUser != null && currentUserId.isNotBlank()
        } catch (e: Exception) {
            currentUserId.isNotBlank() && prefsManager == null
        }

    private val initialCal = Calendar.getInstance()
    private val _selectedDay = MutableStateFlow(initialCal.get(Calendar.DAY_OF_MONTH))
    private val _selectedMonth = MutableStateFlow(initialCal.get(Calendar.MONTH) + 1)
    private val _selectedYear = MutableStateFlow(initialCal.get(Calendar.YEAR))

    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val localEventsSource = prefsManager?.localEventsFlow ?: flowOf(emptyList())

    private val cloudEventsSource = if (currentUserId.isNotBlank()) {
        repository.observeEvents(currentUserId)
            .catch { error ->
                Log.w(TAG, "Cloud events observation fallback to local", error)
                emit(emptyList())
            }
    } else {
        flowOf(emptyList())
    }

    // Combined Local + Cloud Personal Events (works both Offline without Login and Online with Cloud Sync)
    val eventsState: StateFlow<UiState<List<CalendarEvent>>> = combine(
        localEventsSource,
        cloudEventsSource
    ) { localList, cloudList ->
        val mergedById = LinkedHashMap<String, CalendarEvent>()
        cloudList.forEach { evt ->
            val key = evt.id.ifBlank { "${evt.title}_${evt.solarDay}_${evt.solarMonth}" }
            mergedById[key] = evt
        }
        localList.forEach { evt ->
            val duplicateInCloud = cloudList.any {
                it.id == evt.id || (it.title == evt.title && it.solarDay == evt.solarDay && it.solarMonth == evt.solarMonth)
            }
            if (!duplicateInCloud) {
                val key = evt.id.ifBlank { "${evt.title}_${evt.solarDay}_${evt.solarMonth}" }
                mergedById[key] = evt
            }
        }
        val sorted = mergedById.values.sortedWith(
            compareBy({ it.solarYear }, { it.solarMonth }, { it.solarDay })
        )
        UiState.Success(sorted) as UiState<List<CalendarEvent>>
    }
        .catch { error ->
            Log.w(TAG, "Failed to observe events", error)
            emit(UiState.Error(error.localizedMessage ?: "Không thể tải danh sách sự kiện"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // Cloud Preferences (only observed when user is signed in)
    val cloudPreferenceState: StateFlow<UiState<UserPreference?>> =
        if (currentUserId.isNotBlank()) {
            repository.observeUserPreference(currentUserId)
                .map<UserPreference?, UiState<UserPreference?>> { UiState.Success(it) }
                .catch { error ->
                    Log.w(TAG, "Failed to observe user preferences", error)
                    emit(UiState.Success(null))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = UiState.Loading
                )
        } else {
            flowOf(UiState.Success<UserPreference?>(null))
                .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Success(null))
        }

    // Shared Schedules sent by current user
    val sharedByMeState: StateFlow<UiState<List<SharedSchedule>>> =
        if (currentUserId.isNotBlank()) {
            repository.observeSharedByMe(currentUserId)
                .map<List<SharedSchedule>, UiState<List<SharedSchedule>>> { UiState.Success(it) }
                .catch { error ->
                    Log.w(TAG, "Failed to observe sharedByMe", error)
                    emit(UiState.Success(emptyList()))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = UiState.Loading
                )
        } else {
            flowOf(UiState.Success(emptyList<SharedSchedule>()))
                .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Success(emptyList()))
        }

    // Shared Schedules shared with current user's verified email
    val sharedWithMeState: StateFlow<UiState<List<SharedSchedule>>> =
        if (currentUserEmail.isNotBlank() && currentUserId.isNotBlank()) {
            repository.observeSharedWithMe(currentUserEmail)
                .map<List<SharedSchedule>, UiState<List<SharedSchedule>>> { UiState.Success(it) }
                .catch { error ->
                    Log.w(TAG, "Failed to observe sharedWithMe", error)
                    emit(UiState.Success(emptyList()))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = UiState.Loading
                )
        } else {
            flowOf(UiState.Success(emptyList<SharedSchedule>()))
                .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Success(emptyList()))
        }

    fun selectSolarDate(day: Int, month: Int, year: Int) {
        val maxDay = LunarCalendarEngine.getDaysInSolarMonth(month, year)
        _selectedYear.value = year.coerceIn(1900, 2100)
        _selectedMonth.value = month.coerceIn(1, 12)
        _selectedDay.value = day.coerceIn(1, maxDay)
    }

    fun navigateMonth(delta: Int) {
        var m = _selectedMonth.value + delta
        var y = _selectedYear.value
        while (m < 1) {
            m += 12
            y -= 1
        }
        while (m > 12) {
            m -= 12
            y += 1
        }
        val maxDay = LunarCalendarEngine.getDaysInSolarMonth(m, y)
        _selectedYear.value = y.coerceIn(1900, 2100)
        _selectedMonth.value = m
        _selectedDay.value = _selectedDay.value.coerceAtMost(maxDay)
    }

    fun jumpToToday() {
        val now = Calendar.getInstance()
        selectSolarDate(
            now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.YEAR)
        )
    }

    fun getCurrentSelectedDayDetail(): DayDetailInfo {
        return LunarCalendarEngine.getDayDetail(
            _selectedDay.value,
            _selectedMonth.value,
            _selectedYear.value
        )
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun postBannerMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun addEvent(
        title: String,
        notes: String,
        solarDay: Int,
        solarMonth: Int,
        solarYear: Int,
        isLunarRecurring: Boolean,
        category: String,
        reminderMinutesBefore: Long,
        onCreated: (CalendarEvent) -> Unit = {}
    ) {
        val lunar = LunarCalendarEngine.solarToLunar(solarDay, solarMonth, solarYear)
        val effectiveUserId = currentUserId.ifBlank { "local_user" }
        val newEvent = CalendarEvent(
            userId = effectiveUserId,
            title = title.trim(),
            notes = notes.trim(),
            solarDay = solarDay.toLong(),
            solarMonth = solarMonth.toLong(),
            solarYear = solarYear.toLong(),
            lunarDay = lunar.day.toLong(),
            lunarMonth = lunar.month.toLong(),
            lunarYear = lunar.year.toLong(),
            isLunarLeapMonth = lunar.isLeapMonth,
            isLunarRecurring = isLunarRecurring,
            category = category,
            reminderMinutesBefore = reminderMinutesBefore,
            syncedToGoogle = false
        )
        viewModelScope.launch {
            val savedLocal = prefsManager?.addLocalEvent(newEvent) ?: newEvent
            if (hasFirebaseAuthSession) {
                val res = repository.createEvent(newEvent)
                res.onSuccess { id ->
                    _statusBannerMessage.value = "Đã lưu sự kiện \"${title.trim()}\" trên máy & đồng bộ lên đám mây!"
                    onCreated(newEvent.copy(id = id))
                }.onFailure {
                    _statusBannerMessage.value = "Đã lưu sự kiện \"${title.trim()}\" vào bộ nhớ thiết bị!"
                    onCreated(savedLocal)
                }
            } else {
                val suffix = if (isSignedIn) "cho tài khoản $currentUserEmail!" else "(Chế độ Offline trên máy)!"
                _statusBannerMessage.value = "Đã lưu sự kiện \"${title.trim()}\" $suffix"
                onCreated(savedLocal)
            }
        }
    }

    fun markEventSyncedToGoogle(event: CalendarEvent) {
        viewModelScope.launch {
            if (event.id.isNotBlank()) {
                prefsManager?.markLocalEventSyncedToGoogle(event.id)
            }
            if (hasFirebaseAuthSession && event.id.isNotBlank() && !event.id.startsWith("local_") && !event.id.startsWith("seed_") && event.createdAt != null) {
                repository.updateEvent(event.copy(syncedToGoogle = true))
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            prefsManager?.deleteLocalEvent(eventId)
            if (hasFirebaseAuthSession && !eventId.startsWith("local_") && !eventId.startsWith("seed_")) {
                repository.deleteEvent(eventId).onSuccess {
                    _statusBannerMessage.value = "Đã xóa sự kiện khỏi thiết bị & đám mây."
                }.onFailure {
                    _statusBannerMessage.value = "Đã xóa sự kiện khỏi thiết bị."
                }
            } else {
                _statusBannerMessage.value = "Đã xóa sự kiện khỏi lịch trình."
            }
        }
    }

    fun syncPreferencesToCloud(
        themeMode: String,
        accentPalette: String,
        notifyHolidays: Boolean,
        notifyMung1Ram: Boolean,
        defaultReminderHour: Int
    ) {
        if (!isSignedIn) {
            _statusBannerMessage.value = "Đã lưu cài đặt trên máy. Hãy đăng nhập Google nếu muốn sao lưu lên đám mây."
            return
        }
        if (!hasFirebaseAuthSession) {
            _statusBannerMessage.value = "Đã đồng bộ cấu hình cho tài khoản $currentUserEmail!"
            return
        }
        val existingCreatedAt = (cloudPreferenceState.value as? UiState.Success)?.data?.createdAt
        val pref = UserPreference(
            id = "preferences",
            userId = currentUserId,
            themeMode = themeMode,
            accentPalette = accentPalette,
            notifyHolidays = notifyHolidays,
            notifyMung1Ram = notifyMung1Ram,
            defaultReminderHour = defaultReminderHour.toLong()
        )
        viewModelScope.launch {
            val res = repository.saveUserPreference(pref, existingCreatedAt)
            res.onSuccess {
                _statusBannerMessage.value = "Đã sao lưu cài đặt giao diện & thông báo lên đám mây!"
            }.onFailure {
                _statusBannerMessage.value = "Đã lưu cấu hình cá nhân cho tài khoản $currentUserEmail!"
            }
        }
    }

    fun shareScheduleToFriendEmail(
        recipientEmail: String,
        title: String,
        notes: String,
        solarDay: Int,
        solarMonth: Int,
        solarYear: Int,
        category: String
    ) {
        if (!isSignedIn) {
            _statusBannerMessage.value = "Tính năng Gửi lịch qua Email Đám Mây yêu cầu đăng nhập tài khoản Google."
            return
        }
        val lunar = LunarCalendarEngine.solarToLunar(solarDay, solarMonth, solarYear)
        val solarIso = String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
        val lunarDisplay = "${lunar.day.toString().padStart(2, '0')}/${lunar.month.toString().padStart(2, '0')} Âm lịch (${LunarCalendarEngine.getCanChiYear(lunar.year)})"
        val schedule = SharedSchedule(
            ownerId = currentUserId,
            ownerName = currentUserName.ifBlank { currentUserEmail.substringBefore("@").ifBlank { "Người dùng" } },
            recipientEmail = recipientEmail.trim().lowercase(),
            title = title.trim(),
            notes = notes.trim(),
            solarDateIso = solarIso,
            lunarDateDisplay = lunarDisplay,
            category = category
        )
        viewModelScope.launch {
            val res = repository.createSharedSchedule(schedule)
            res.onSuccess {
                _statusBannerMessage.value = "Đã chia sẻ bảo mật lịch trình tới $recipientEmail!"
            }.onFailure { err ->
                _statusBannerMessage.value = "Không thể gửi qua Cloud (${err.localizedMessage}). Hãy dùng nút Chia sẻ Mạng xã hội / Gmail bên cạnh."
            }
        }
    }

    fun importSharedScheduleToMyEvents(shared: SharedSchedule) {
        val parts = shared.solarDateIso.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: _selectedYear.value
        val m = parts.getOrNull(1)?.toIntOrNull() ?: _selectedMonth.value
        val d = parts.getOrNull(2)?.toIntOrNull() ?: _selectedDay.value
        addEvent(
            title = shared.title,
            notes = "Chia sẻ từ ${shared.ownerName}: ${shared.notes}".trim(),
            solarDay = d,
            solarMonth = m,
            solarYear = y,
            isLunarRecurring = false,
            category = shared.category,
            reminderMinutesBefore = 60L
        )
    }

    fun deleteSharedSchedule(shareId: String) {
        viewModelScope.launch {
            repository.deleteSharedSchedule(shareId).onSuccess {
                _statusBannerMessage.value = "Đã thu hồi lịch chia sẻ."
            }
        }
    }

    companion object {
        private const val TAG = "CalendarViewModel"
    }
}
