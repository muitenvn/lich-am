package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthGateScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.notifications.SmartReminderHelper
import com.example.ui.CalendarViewModel
import com.example.ui.UiState
import com.example.ui.screens.CreateEventDialog
import com.example.ui.screens.EventsAndRemindersScreen
import com.example.ui.screens.MonthlyCalendarScreen
import com.example.ui.screens.QuickConverterScreen
import com.example.ui.screens.SettingsAndCloudScreen
import com.example.ui.screens.ShareScheduleScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.LunarCalendarAppWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SmartReminderHelper.ensureNotificationChannel(this)
        setContent {
            val context = LocalContext.current
            val prefsManager = remember { LocalPreferencesManager(context.applicationContext) }
            val localSettings by prefsManager.settingsFlow.collectAsStateWithLifecycle(
                initialValue = LocalAppSettings()
            )

            MyApplicationTheme(
                themeMode = localSettings.themeMode,
                accentPalette = localSettings.accentPalette
            ) {
                LichVanNienAppRoot(
                    localSettings = localSettings,
                    prefsManager = prefsManager
                )
            }
        }
    }
}

@Composable
fun LichVanNienAppRoot(
    localSettings: LocalAppSettings,
    prefsManager: LocalPreferencesManager
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthGateScreen(onAuthSuccess = {})
    } else {
        AuthenticatedMainScaffold(
            currentUserId = user.uid,
            currentUserEmail = user.email ?: "",
            currentUserName = user.displayName ?: "",
            localSettings = localSettings,
            prefsManager = prefsManager
        )
    }
}

@Composable
fun AuthenticatedMainScaffold(
    currentUserId: String,
    currentUserEmail: String,
    currentUserName: String,
    localSettings: LocalAppSettings,
    prefsManager: LocalPreferencesManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: CalendarViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CalendarViewModel(
                    repository = CalendarRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val cloudPreferenceState by viewModel.cloudPreferenceState.collectAsStateWithLifecycle()
    val sharedWithMeState by viewModel.sharedWithMeState.collectAsStateWithLifecycle()
    val sharedByMeState by viewModel.sharedByMeState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    // Sync cloud preferences to local DataStore when loaded
    LaunchedEffect(cloudPreferenceState) {
        val cloudPref = (cloudPreferenceState as? UiState.Success)?.data
        if (cloudPref != null) {
            prefsManager.updateSettings(
                themeMode = cloudPref.themeMode,
                accentPalette = cloudPref.accentPalette,
                notifyHolidays = cloudPref.notifyHolidays,
                notifyMung1Ram = cloudPref.notifyMung1Ram,
                defaultReminderHour = cloudPref.defaultReminderHour.toInt()
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SmartReminderHelper.triggerSmartDailyBriefing(context)
            viewModel.postBannerMessage("Đã bật thông báo nhắc lịch thông minh!")
        }
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var quickAddDialogSpec by remember { mutableStateOf<Quadruple<Int, Int, Int, String>?>(null) }

    // BackHandler for secondary tabs to return to Home Calendar tab
    BackHandler(enabled = currentTab != 0) {
        currentTab = 0
    }

    val eventsList = (eventsState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Lịch Tháng") },
                    label = { Text("Lịch Tháng") },
                    modifier = Modifier.testTag("nav_tab_calendar")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "Đổi Ngày") },
                    label = { Text("Đổi Ngày") },
                    modifier = Modifier.testTag("nav_tab_converter")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.EventNote, contentDescription = "Sự Kiện") },
                    label = { Text("Sự Kiện") },
                    modifier = Modifier.testTag("nav_tab_events")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Share, contentDescription = "Chia Sẻ") },
                    label = { Text("Chia Sẻ") },
                    modifier = Modifier.testTag("nav_tab_share")
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Cài Đặt") },
                    label = { Text("Cài Đặt") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = bannerMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bannerMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBannerMessage() }) {
                            Text("Đóng")
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> MonthlyCalendarScreen(
                        selectedDay = selectedDay,
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        events = eventsList,
                        onSelectDate = { d, m, y -> viewModel.selectSolarDate(d, m, y) },
                        onNavigateMonth = { delta -> viewModel.navigateMonth(delta) },
                        onJumpToToday = { viewModel.jumpToToday() },
                        onOpenQuickConverter = { currentTab = 1 },
                        onOpenAddEventDialog = {
                            quickAddDialogSpec = Quadruple(selectedDay, selectedMonth, selectedYear, "")
                        },
                        onSyncEventToGoogle = { evt -> viewModel.markEventSyncedToGoogle(evt) }
                    )

                    1 -> QuickConverterScreen(
                        initialDay = selectedDay,
                        initialMonth = selectedMonth,
                        initialYear = selectedYear,
                        onJumpToDateInCalendar = { d, m, y ->
                            viewModel.selectSolarDate(d, m, y)
                            currentTab = 0
                        },
                        onCreateEventForDate = { d, m, y, defaultTitle ->
                            quickAddDialogSpec = Quadruple(d, m, y, defaultTitle)
                        }
                    )

                    2 -> EventsAndRemindersScreen(
                        eventsState = eventsState,
                        selectedDay = selectedDay,
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        onAddEvent = { title, notes, d, m, y, isLunarRec, cat, remMins ->
                            viewModel.addEvent(title, notes, d, m, y, isLunarRec, cat, remMins) { created ->
                                SmartReminderHelper.scheduleEventReminder(
                                    context,
                                    created,
                                    localSettings.defaultReminderHour
                                )
                            }
                        },
                        onDeleteEvent = { id -> viewModel.deleteEvent(id) },
                        onSyncToGoogle = { evt -> viewModel.markEventSyncedToGoogle(evt) },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !SmartReminderHelper.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    3 -> ShareScheduleScreen(
                        currentUserEmail = currentUserEmail,
                        selectedDay = selectedDay,
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        sharedWithMeState = sharedWithMeState,
                        sharedByMeState = sharedByMeState,
                        onShareViaEmailCloud = { email, title, notes, d, m, y, cat ->
                            viewModel.shareScheduleToFriendEmail(email, title, notes, d, m, y, cat)
                        },
                        onImportSharedSchedule = { shared ->
                            viewModel.importSharedScheduleToMyEvents(shared)
                        },
                        onDeleteSharedSchedule = { shareId ->
                            viewModel.deleteSharedSchedule(shareId)
                        }
                    )

                    4 -> SettingsAndCloudScreen(
                        userEmail = currentUserEmail,
                        userName = currentUserName,
                        localSettings = localSettings,
                        eventsCount = eventsList.size,
                        onUpdateThemeMode = { mode ->
                            scope.launch { prefsManager.updateSettings(themeMode = mode) }
                            viewModel.syncPreferencesToCloud(
                                mode,
                                localSettings.accentPalette,
                                localSettings.notifyHolidays,
                                localSettings.notifyMung1Ram,
                                localSettings.defaultReminderHour
                            )
                        },
                        onUpdateAccentPalette = { palette ->
                            scope.launch { prefsManager.updateSettings(accentPalette = palette) }
                            viewModel.syncPreferencesToCloud(
                                localSettings.themeMode,
                                palette,
                                localSettings.notifyHolidays,
                                localSettings.notifyMung1Ram,
                                localSettings.defaultReminderHour
                            )
                        },
                        onUpdateNotifyHolidays = { enabled ->
                            scope.launch { prefsManager.updateSettings(notifyHolidays = enabled) }
                        },
                        onUpdateNotifyMung1Ram = { enabled ->
                            scope.launch { prefsManager.updateSettings(notifyMung1Ram = enabled) }
                        },
                        onBackupSettingsToCloud = {
                            viewModel.syncPreferencesToCloud(
                                localSettings.themeMode,
                                localSettings.accentPalette,
                                localSettings.notifyHolidays,
                                localSettings.notifyMung1Ram,
                                localSettings.defaultReminderHour
                            )
                        },
                        onPinWidget = {
                            val pinned = LunarCalendarAppWidgetProvider.requestPinWidget(context)
                            if (pinned) {
                                viewModel.postBannerMessage("Đã gửi yêu cầu ghim Widget Lịch Vạn Niên ra màn hình chính!")
                            } else {
                                viewModel.postBannerMessage("Bạn có thể nhấn giữ màn hình chính -> Tiện ích (Widgets) -> chọn Lịch Vạn Niên.")
                            }
                        },
                        onSignOut = {
                            signOutUser(
                                credentialManager = credentialManager,
                                onSignOutComplete = {},
                                scope = scope
                            )
                        },
                        onShowMessage = { msg -> viewModel.postBannerMessage(msg) }
                    )
                }
            }
        }
    }

    quickAddDialogSpec?.let { spec ->
        CreateEventDialog(
            initialSolarDay = spec.first,
            initialSolarMonth = spec.second,
            initialSolarYear = spec.third,
            initialTitle = spec.fourth,
            onDismiss = { quickAddDialogSpec = null },
            onConfirm = { title, notes, d, m, y, isLunarRec, cat, remMins ->
                viewModel.addEvent(title, notes, d, m, y, isLunarRec, cat, remMins) { created ->
                    SmartReminderHelper.scheduleEventReminder(
                        context,
                        created,
                        localSettings.defaultReminderHour
                    )
                }
                quickAddDialogSpec = null
            }
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
