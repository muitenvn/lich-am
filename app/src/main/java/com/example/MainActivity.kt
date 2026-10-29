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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.GoogleSignInDialog
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.local.LocalAccountSession
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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureFirebaseInitialized()
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

    private fun ensureFirebaseInitialized() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("gen-lang-client-0420449921")
                    .setApplicationId("1:801726861967:android:64579e6ee43420000b96fa")
                    .setApiKey("AIzaSyAQXRe0DYXKfO_gRek2Pn79PWhTaQ7s6D8")
                    .setGcmSenderId("801726861967")
                    .setStorageBucket("gen-lang-client-0420449921.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun LichVanNienAppRoot(
    localSettings: LocalAppSettings,
    prefsManager: LocalPreferencesManager
) {
    val auth = Firebase.auth
    val firebaseUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val storedSession by prefsManager.accountSessionFlow.collectAsStateWithLifecycle(
        initialValue = LocalAccountSession()
    )
    var immediateSession by remember { mutableStateOf<LocalAccountSession?>(null) }

    val effectiveSession = remember(firebaseUser, storedSession, immediateSession) {
        val fb = firebaseUser
        when {
            immediateSession != null && immediateSession!!.isSignedIn -> immediateSession!!
            fb != null -> LocalAccountSession(
                uid = fb.uid,
                email = fb.email ?: storedSession.email,
                displayName = fb.displayName ?: storedSession.displayName
            )
            storedSession.isSignedIn -> storedSession
            else -> LocalAccountSession()
        }
    }

    // Sync Firebase user into local session storage whenever Firebase Auth emits a user
    LaunchedEffect(firebaseUser) {
        val fb = firebaseUser
        if (fb != null) {
            prefsManager.saveAccountSession(
                uid = fb.uid,
                email = fb.email ?: "",
                displayName = fb.displayName ?: ""
            )
        }
    }

    // No login wall: Always show the full calendar app immediately!
    LichVanNienMainScaffold(
        currentUserId = effectiveSession.uid,
        currentUserEmail = effectiveSession.email,
        currentUserName = effectiveSession.displayName,
        localSettings = localSettings,
        prefsManager = prefsManager,
        onSessionUpdated = { newSession ->
            immediateSession = newSession
        }
    )
}

@Composable
fun LichVanNienMainScaffold(
    currentUserId: String,
    currentUserEmail: String,
    currentUserName: String,
    localSettings: LocalAppSettings,
    prefsManager: LocalPreferencesManager,
    onSessionUpdated: (LocalAccountSession?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val isSignedIn = currentUserId.isNotBlank() || currentUserEmail.isNotBlank()

    val viewModelKey = if (isSignedIn) "user_${currentUserId}_${currentUserEmail}" else "guest_local_mode"
    val viewModel: CalendarViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CalendarViewModel(
                    repository = CalendarRepository(db),
                    prefsManager = prefsManager,
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
    val todayTriple by viewModel.todayTriple.collectAsStateWithLifecycle()
    val calendarSyncStatus by viewModel.calendarSyncStatus.collectAsStateWithLifecycle()
    val isSyncingOfficialDate by viewModel.isSyncingOfficialDate.collectAsStateWithLifecycle()
    val dataVersion by viewModel.dataVersion.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val cloudPreferenceState by viewModel.cloudPreferenceState.collectAsStateWithLifecycle()
    val sharedWithMeState by viewModel.sharedWithMeState.collectAsStateWithLifecycle()
    val sharedByMeState by viewModel.sharedByMeState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    var showSignInDialog by rememberSaveable { mutableStateOf(false) }

    // Fetch current Vietnam date & official calendar data from online servers when connected,
    // or fall back to the phone's clock (Asia/Ho_Chi_Minh) when offline.
    LaunchedEffect(Unit) {
        viewModel.syncWithOfficialSource(
            context = context,
            updateSelectedDate = true,
            showToastBanner = false
        )
    }

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
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lịch Vạn Niên",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSignedIn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .clickable {
                                if (isSignedIn) {
                                    currentTab = 4
                                } else {
                                    showSignInDialog = true
                                }
                            }
                            .testTag("top_bar_account_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSignedIn) {
                                    currentUserName.ifBlank { currentUserEmail.substringBefore("@").ifBlank { "Đã đăng nhập" } }
                                } else {
                                    "Đăng nhập Google"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
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
                        todayTriple = todayTriple,
                        syncStatus = calendarSyncStatus,
                        isSyncingOfficialDate = isSyncingOfficialDate,
                        dataVersion = dataVersion,
                        events = eventsList,
                        onSelectDate = { d, m, y -> viewModel.selectSolarDate(d, m, y, context) },
                        onNavigateMonth = { delta -> viewModel.navigateMonth(delta, context) },
                        onJumpToToday = { viewModel.jumpToToday(context) },
                        onSyncOfficialDate = {
                            viewModel.syncWithOfficialSource(
                                context = context,
                                updateSelectedDate = false,
                                showToastBanner = true
                            )
                        },
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
                            viewModel.selectSolarDate(d, m, y, context)
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
                        isSignedIn = isSignedIn,
                        onRequestSignIn = { showSignInDialog = true },
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
                        isSignedIn = isSignedIn,
                        onRequestSignIn = { showSignInDialog = true },
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
                        isSignedIn = isSignedIn,
                        onRequestSignIn = { showSignInDialog = true },
                        localSettings = localSettings,
                        eventsCount = eventsList.size,
                        onUpdateThemeMode = { mode ->
                            scope.launch { prefsManager.updateSettings(themeMode = mode) }
                            if (isSignedIn) {
                                viewModel.syncPreferencesToCloud(
                                    mode,
                                    localSettings.accentPalette,
                                    localSettings.notifyHolidays,
                                    localSettings.notifyMung1Ram,
                                    localSettings.defaultReminderHour
                                )
                            }
                        },
                        onUpdateAccentPalette = { palette ->
                            scope.launch { prefsManager.updateSettings(accentPalette = palette) }
                            if (isSignedIn) {
                                viewModel.syncPreferencesToCloud(
                                    localSettings.themeMode,
                                    palette,
                                    localSettings.notifyHolidays,
                                    localSettings.notifyMung1Ram,
                                    localSettings.defaultReminderHour
                                )
                            }
                        },
                        onUpdateNotifyHolidays = { enabled ->
                            scope.launch { prefsManager.updateSettings(notifyHolidays = enabled) }
                        },
                        onUpdateNotifyMung1Ram = { enabled ->
                            scope.launch { prefsManager.updateSettings(notifyMung1Ram = enabled) }
                        },
                        onBackupSettingsToCloud = {
                            if (!isSignedIn) {
                                showSignInDialog = true
                            } else {
                                viewModel.syncPreferencesToCloud(
                                    localSettings.themeMode,
                                    localSettings.accentPalette,
                                    localSettings.notifyHolidays,
                                    localSettings.notifyMung1Ram,
                                    localSettings.defaultReminderHour
                                )
                            }
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
                                prefsManager = prefsManager,
                                onSignOutComplete = {
                                    onSessionUpdated(null)
                                    viewModel.postBannerMessage("Đã đăng xuất. Bạn đang ở chế độ Không Đăng Nhập.")
                                }
                            )
                        },
                        onShowMessage = { msg -> viewModel.postBannerMessage(msg) }
                    )
                }
            }
        }
    }

    if (showSignInDialog) {
        GoogleSignInDialog(
            prefsManager = prefsManager,
            onDismiss = { showSignInDialog = false },
            onSignedIn = { session ->
                showSignInDialog = false
                onSessionUpdated(session)
                viewModel.postBannerMessage("Đăng nhập thành công tài khoản ${session.email}!")
            }
        )
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
