package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarEvent
import com.example.domain.LunarCalendarEngine
import com.example.notifications.SmartReminderHelper
import com.example.ui.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickConverterScreen(
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    onJumpToDateInCalendar: (Int, Int, Int) -> Unit,
    onCreateEventForDate: (Int, Int, Int, String) -> Unit
) {
    var conversionMode by remember { mutableIntStateOf(0) } // 0 = Dương -> Âm, 1 = Âm -> Dương
    var dayText by remember { mutableStateOf(initialDay.toString()) }
    var monthText by remember { mutableStateOf(initialMonth.toString()) }
    var yearText by remember { mutableStateOf(initialYear.toString()) }
    var isLeapMonth by remember { mutableStateOf(false) }

    val inputDay = (dayText.toIntOrNull() ?: 1).coerceIn(1, 31)
    val inputMonth = (monthText.toIntOrNull() ?: 1).coerceIn(1, 12)
    val inputYear = (yearText.toIntOrNull() ?: 2026).coerceIn(1900, 2100)

    val resolvedSolarTriple = remember(conversionMode, inputDay, inputMonth, inputYear, isLeapMonth) {
        if (conversionMode == 0) {
            val maxD = LunarCalendarEngine.getDaysInSolarMonth(inputMonth, inputYear)
            Triple(inputDay.coerceAtMost(maxD), inputMonth, inputYear)
        } else {
            LunarCalendarEngine.lunarToSolar(
                inputDay.coerceAtMost(30),
                inputMonth,
                inputYear,
                isLeapMonth
            )
        }
    }

    val resultDetail = remember(resolvedSolarTriple) {
        LunarCalendarEngine.getDayDetail(
            resolvedSolarTriple.first,
            resolvedSolarTriple.second,
            resolvedSolarTriple.third
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("converter_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Đổi Ngày Âm ↔ Dương Nhanh Chóng",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Chuyển đổi chính xác hai chiều giữa Dương lịch và Âm lịch Việt Nam (Múi giờ UTC+7).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Direction selector
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = conversionMode == 0,
                    onClick = { conversionMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("mode_solar_to_lunar")
                ) {
                    Text("Dương Lịch → Âm Lịch")
                }
                SegmentedButton(
                    selected = conversionMode == 1,
                    onClick = { conversionMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("mode_lunar_to_solar")
                ) {
                    Text("Âm Lịch → Dương Lịch")
                }
            }
        }

        // Quick Cultural Presets
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Chọn nhanh các mốc Lễ Tết Âm Lịch:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val presets = listOf(
                        Triple("Mùng 1 Tết", 1, 1),
                        Triple("Rằm Tháng Giêng", 15, 1),
                        Triple("Giỗ Tổ Hùng Vương (10/3)", 10, 3),
                        Triple("Lễ Phật Đản (15/4)", 15, 4),
                        Triple("Lễ Vu Lan (15/7)", 15, 7),
                        Triple("Tết Trung Thu (15/8)", 15, 8),
                        Triple("Ông Công Ông Táo (23/12)", 23, 12)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (label, lDay, lMonth) ->
                            FilterChip(
                                selected = conversionMode == 1 && inputDay == lDay && inputMonth == lMonth,
                                onClick = {
                                    conversionMode = 1
                                    dayText = lDay.toString()
                                    monthText = lMonth.toString()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Input Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (conversionMode == 0) "Nhập Ngày Dương Lịch:" else "Nhập Ngày Âm Lịch:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { if (it.length <= 2) dayText = it.filter(Char::isDigit) },
                            label = { Text("Ngày") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("converter_day_input")
                        )
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { if (it.length <= 2) monthText = it.filter(Char::isDigit) },
                            label = { Text("Tháng") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("converter_month_input")
                        )
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { if (it.length <= 4) yearText = it.filter(Char::isDigit) },
                            label = { Text("Năm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("converter_year_input")
                        )
                    }

                    if (conversionMode == 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tháng Nhuận Âm Lịch",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = isLeapMonth,
                                onCheckedChange = { isLeapMonth = it }
                            )
                        }
                    }
                }
            }
        }

        // Conversion Result Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KẾT QUẢ CHUYỂN ĐỔI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "☀️ Dương Lịch: ${resultDetail.dayOfWeekName}, ${resultDetail.solarDay}/${resultDetail.solarMonth}/${resultDetail.solarYear}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val leapStr = if (resultDetail.lunarDate.isLeapMonth) " (Tháng Nhuận)" else ""
                    Text(
                        text = "🌙 Âm Lịch: Ngày ${resultDetail.lunarDate.day} Tháng ${resultDetail.lunarDate.month} Năm ${resultDetail.canChiYear} (${resultDetail.lunarDate.year})$leapStr",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Can Chi: Ngày ${resultDetail.canChiDay}, Tháng ${resultDetail.canChiMonth}, Năm ${resultDetail.canChiYear}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Tiết Khí: ${resultDetail.tietKhi} • ${resultDetail.dayQualityLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (resultDetail.holidays.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🎉 Ngày lễ: ${resultDetail.holidays.joinToString { it.title }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onJumpToDateInCalendar(
                                    resultDetail.solarDay,
                                    resultDetail.solarMonth,
                                    resultDetail.solarYear
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("jump_converted_date_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xem trên Lịch")
                        }
                        OutlinedButton(
                            onClick = {
                                val defaultTitle = resultDetail.holidays.firstOrNull()?.title
                                    ?: "Sự kiện ${resultDetail.lunarDate.day}/${resultDetail.lunarDate.month} Âm lịch"
                                onCreateEventForDate(
                                    resultDetail.solarDay,
                                    resultDetail.solarMonth,
                                    resultDetail.solarYear,
                                    defaultTitle
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_event_from_converter_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tạo sự kiện")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventsAndRemindersScreen(
    eventsState: UiState<List<CalendarEvent>>,
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    onAddEvent: (String, String, Int, Int, Int, Boolean, String, Long) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onSyncToGoogle: (CalendarEvent) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("all") }

    val events = (eventsState as? UiState.Success)?.data ?: emptyList()
    val filteredEvents = remember(events, selectedCategoryFilter) {
        if (selectedCategoryFilter == "all") events
        else events.filter { it.category == selectedCategoryFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("events_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sự Kiện & Nhắc Nhở Thông Minh",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Đồng bộ Google Calendar và nhắc lịch tự động theo cả Âm lịch & Dương lịch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("open_add_event_dialog_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm")
                }
            }
        }

        // Smart Reminder Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trợ lý Nhắc Lịch Mùng 1, Rằm & Giỗ Tết",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nhận thông báo sớm ngày Hoàng Đạo, Tiết Khí và sự kiện cá nhân.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(
                        onClick = {
                            onRequestNotificationPermission()
                            SmartReminderHelper.triggerSmartDailyBriefing(context)
                        },
                        modifier = Modifier.testTag("test_smart_notification_btn")
                    ) {
                        Text("Báo thử")
                    }
                }
            }
        }

        // Category Filter Row
        item {
            val filters = listOf(
                "all" to "Tất cả",
                "holiday" to "Ngày Lễ",
                "ancestral" to "Giỗ Chạp",
                "anniversary" to "Kỷ Niệm",
                "work" to "Công Việc",
                "personal" to "Cá Nhân"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.take(4).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedCategoryFilter == key,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filteredEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chưa có sự kiện cá nhân nào",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hãy bấm \"Thêm\" để lưu ngày Giỗ, Sinh nhật Âm lịch, Kỷ niệm hoặc Lịch họp và tự động đồng bộ lên đám mây.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredEvents, key = { it.id.ifEmpty { it.title } }) { event ->
                EventItemCard(
                    event = event,
                    onSyncGoogle = {
                        SmartReminderHelper.openGoogleCalendarInsertIntent(context, event)
                        onSyncToGoogle(event)
                    },
                    onShare = {
                        SmartReminderHelper.shareScheduleViaPlatform(
                            context = context,
                            title = event.title,
                            solarDateDisplay = "${event.solarDay}/${event.solarMonth}/${event.solarYear}",
                            lunarDateDisplay = "${event.lunarDay}/${event.lunarMonth}/${event.lunarYear} Âm lịch",
                            notes = event.notes
                        )
                    },
                    onDelete = {
                        if (event.id.isNotBlank()) onDeleteEvent(event.id)
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            initialSolarDay = selectedDay,
            initialSolarMonth = selectedMonth,
            initialSolarYear = selectedYear,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, notes, d, m, y, isLunarRec, cat, remMins ->
                onAddEvent(title, notes, d, m, y, isLunarRec, cat, remMins)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun EventItemCard(
    event: CalendarEvent,
    onSyncGoogle: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (event.isLunarRecurring) "Lặp lại hàng năm theo Âm Lịch" else "Theo Dương Lịch",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🌙 Âm lịch: ${event.lunarDay}/${event.lunarMonth}/${event.lunarYear}  •  ☀️ Dương: ${event.solarDay}/${event.solarMonth}/${event.solarYear}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (event.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = event.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Xóa sự kiện",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSyncGoogle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (event.syncedToGoogle) "Đã đồng bộ Google" else "Đồng bộ Google Lịch")
                }

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Chia sẻ",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateEventDialog(
    initialSolarDay: Int,
    initialSolarMonth: Int,
    initialSolarYear: Int,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, Int, Boolean, String, Long) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var dayStr by remember { mutableStateOf(initialSolarDay.toString()) }
    var monthStr by remember { mutableStateOf(initialSolarMonth.toString()) }
    var yearStr by remember { mutableStateOf(initialSolarYear.toString()) }
    var isLunarRecurring by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf("ancestral") }

    val d = (dayStr.toIntOrNull() ?: initialSolarDay).coerceIn(1, 31)
    val m = (monthStr.toIntOrNull() ?: initialSolarMonth).coerceIn(1, 12)
    val y = (yearStr.toIntOrNull() ?: initialSolarYear).coerceIn(1900, 2100)
    val previewLunar = remember(d, m, y) { LunarCalendarEngine.solarToLunar(d, m, y) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm Sự Kiện & Nhắc Nhở Mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên sự kiện (VD: Giỗ Ông Nội, Họp đầu năm)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_event_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Ghi chú chi tiết") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = { dayStr = it.filter(Char::isDigit).take(2) },
                        label = { Text("Ngày DL") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthStr,
                        onValueChange = { monthStr = it.filter(Char::isDigit).take(2) },
                        label = { Text("Tháng DL") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it.filter(Char::isDigit).take(4) },
                        label = { Text("Năm DL") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                }
                Text(
                    text = "→ Tương ứng Âm lịch: Ngày ${previewLunar.day}/${previewLunar.month}/${previewLunar.year} (${LunarCalendarEngine.getCanChiYear(previewLunar.year)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nhắc định kỳ hàng năm theo Âm lịch",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isLunarRecurring,
                        onCheckedChange = { isLunarRecurring = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, notes, d, m, y, isLunarRecurring, category, 60L)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("dialog_save_event_btn")
            ) {
                Text("Lưu đám mây")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
