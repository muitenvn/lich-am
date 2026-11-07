package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CalendarEvent
import com.example.data.remote.CalendarSyncStatus
import com.example.domain.DayDetailInfo
import com.example.domain.LunarCalendarEngine
import com.example.notifications.SmartReminderHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCalendarScreen(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    todayTriple: Triple<Int, Int, Int> = LunarCalendarEngine.getDeviceDateInVietnam(),
    syncStatus: CalendarSyncStatus? = null,
    isSyncingOfficialDate: Boolean = false,
    dataVersion: Int = 0,
    events: List<CalendarEvent>,
    onSelectDate: (Int, Int, Int) -> Unit,
    onNavigateMonth: (Int) -> Unit,
    onJumpToToday: () -> Unit,
    onSyncOfficialDate: () -> Unit = {},
    onOpenQuickConverter: () -> Unit,
    onOpenAddEventDialog: () -> Unit,
    onSyncEventToGoogle: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    val dayDetail = remember(selectedDay, selectedMonth, selectedYear, dataVersion) {
        LunarCalendarEngine.getDayDetail(selectedDay, selectedMonth, selectedYear)
    }

    val dayEvents = remember(selectedDay, selectedMonth, selectedYear, events, dataVersion) {
        events.filter { evt ->
            val matchesSolar = evt.solarDay.toInt() == selectedDay &&
                    evt.solarMonth.toInt() == selectedMonth &&
                    (evt.isLunarRecurring || evt.solarYear.toInt() == selectedYear)
            val matchesLunar = evt.isLunarRecurring &&
                    evt.lunarDay.toInt() == dayDetail.lunarDate.day &&
                    evt.lunarMonth.toInt() == dayDetail.lunarDate.month
            matchesSolar || matchesLunar
        }
    }

    val upcomingHolidays = remember(selectedDay, selectedMonth, selectedYear, dataVersion) {
        LunarCalendarEngine.getAllUpcomingHolidays(selectedDay, selectedMonth, selectedYear, count = 6)
    }

    var showMonthYearPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("monthly_calendar_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Dual Lunar-Solar Card with integrated Online/Offline Official Source Bar
        item {
            HeroDayBannerCard(
                dayDetail = dayDetail,
                syncStatus = syncStatus,
                todayTriple = todayTriple,
                isSyncing = isSyncingOfficialDate,
                onJumpToToday = onJumpToToday,
                onSyncOfficialDate = onSyncOfficialDate,
                onOpenQuickConverter = onOpenQuickConverter
            )
        }

        // 2. Interactive Month Grid Card
        item {
            MonthCalendarGridCard(
                selectedDay = selectedDay,
                selectedMonth = selectedMonth,
                selectedYear = selectedYear,
                todayTriple = todayTriple,
                dataVersion = dataVersion,
                events = events,
                onSelectDate = onSelectDate,
                onNavigateMonth = onNavigateMonth,
                onOpenMonthYearPicker = { showMonthYearPicker = true }
            )
        }

        // 3. Selected Day Auspicious Hours & Can Chi Detail Card
        item {
            SelectedDayInsightCard(
                dayDetail = dayDetail,
                onAddEventClick = onOpenAddEventDialog,
                onShareDayClick = {
                    val solarStr = "${dayDetail.dayOfWeekName}, ${dayDetail.solarDay}/${dayDetail.solarMonth}/${dayDetail.solarYear}"
                    val lunarStr = "${dayDetail.lunarDate.day}/${dayDetail.lunarDate.month}/${dayDetail.lunarDate.year} (${dayDetail.canChiYear})"
                    val notes = "Ngày ${dayDetail.canChiDay}, Tháng ${dayDetail.canChiMonth} • Tiết ${dayDetail.tietKhi} • ${dayDetail.dayQualityLabel}"
                    SmartReminderHelper.shareScheduleViaPlatform(
                        context = context,
                        title = dayDetail.holidays.firstOrNull()?.title ?: "Lịch Âm Dương Ngày ${dayDetail.solarDay}/${dayDetail.solarMonth}",
                        solarDateDisplay = solarStr,
                        lunarDateDisplay = lunarStr,
                        notes = notes
                    )
                }
            )
        }

        // 4. Personal Events on Selected Day
        if (dayEvents.isNotEmpty()) {
            item {
                Text(
                    text = "Sự kiện cá nhân trong ngày (${dayEvents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(dayEvents, key = { it.id.ifEmpty { it.title } }) { evt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = evt.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Âm lịch: ${evt.lunarDay}/${evt.lunarMonth} • Dương lịch: ${evt.solarDay}/${evt.solarMonth}/${evt.solarYear}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (evt.notes.isNotBlank()) {
                                Text(
                                    text = evt.notes,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                SmartReminderHelper.openGoogleCalendarInsertIntent(context, evt)
                                onSyncEventToGoogle(evt)
                            },
                            modifier = Modifier.testTag("sync_google_btn_${evt.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "Đồng bộ Google Calendar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (evt.syncedToGoogle) "Đã đồng bộ" else "Google Lịch")
                        }
                    }
                }
            }
        }

        // 5. Upcoming Holidays & Lunar Milestones
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ngày Lễ & Mùng 1 / Rằm Sắp Tới",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {
                        SmartReminderHelper.triggerSmartDailyBriefing(context)
                    },
                    label = { Text("Báo thông minh") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("smart_briefing_chip")
                )
            }
        }

        items(upcomingHolidays) { (info, holiday) ->
            val daysAway = LunarCalendarEngine.daysBetweenSolarDates(
                selectedDay, selectedMonth, selectedYear,
                info.solarDay, info.solarMonth, info.solarYear
            )
            val countdownLabel = when {
                daysAway <= 0 -> "Hôm nay"
                daysAway == 1 -> "Ngày mai"
                else -> "Còn $daysAway ngày"
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectDate(info.solarDay, info.solarMonth, info.solarYear)
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (holiday.isLunar) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${info.solarDay}/${info.solarMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (holiday.isLunar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${info.lunarDate.day}/${info.lunarDate.month} ÂL",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = holiday.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${info.dayOfWeekName} • ${holiday.description}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (daysAway == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = countdownLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (daysAway == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    if (showMonthYearPicker) {
        MonthYearQuickPickerDialog(
            initialMonth = selectedMonth,
            initialYear = selectedYear,
            onDismiss = { showMonthYearPicker = false },
            onConfirm = { newMonth, newYear ->
                showMonthYearPicker = false
                onSelectDate(selectedDay, newMonth, newYear)
            }
        )
    }
}

@Composable
private fun HeroDayBannerCard(
    dayDetail: DayDetailInfo,
    syncStatus: CalendarSyncStatus?,
    todayTriple: Triple<Int, Int, Int>,
    isSyncing: Boolean,
    onJumpToToday: () -> Unit,
    onSyncOfficialDate: () -> Unit,
    onOpenQuickConverter: () -> Unit
) {
    val isOnline = syncStatus?.isOnline == true
    val todayFormatted = String.format("%02d/%02d/%04d", todayTriple.first, todayTriple.second, todayTriple.third)
    val modeBadgeText = when {
        isSyncing -> "⏳ Đang đồng bộ trực tuyến..."
        isOnline -> "🌐 Trực tuyến • Hôm nay: $todayFormatted"
        else -> "📱 Ngoại tuyến (Đồng hồ máy) • $todayFormatted"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("official_data_source_card"),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background art fills whatever height the content requires
            Image(
                painter = painterResource(id = R.drawable.img_lunar_hero),
                contentDescription = "Bầu trời đêm Âm lịch",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            // Rich protective dark-crimson & midnight scrim so text is 100% legible
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC3B0D14),
                                Color(0xBB161224),
                                Color(0xE60D111C)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Top Row: Date header pill & Action chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "${dayDetail.dayOfWeekName.uppercase()} • THÁNG ${dayDetail.solarMonth}, ${dayDetail.solarYear}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD966),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = onJumpToToday,
                            label = { Text("Hôm nay", color = Color.White, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD966),
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Black.copy(alpha = 0.45f)
                            ),
                            border = null,
                            modifier = Modifier.testTag("jump_today_chip")
                        )
                        AssistChip(
                            onClick = onOpenQuickConverter,
                            label = { Text("Đổi ngày", color = Color.White, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD966),
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Black.copy(alpha = 0.45f)
                            ),
                            border = null,
                            modifier = Modifier.testTag("quick_convert_chip")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Dual Display: Solar Left + Lunar Right Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Solar Big Number Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DƯƠNG LỊCH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFE082)
                        )
                        Text(
                            text = dayDetail.solarDay.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 54.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                        if (dayDetail.holidays.isNotEmpty()) {
                            Text(
                                text = "★ ${dayDetail.holidays.first().title}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFAB91),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "✦ Tiết ${dayDetail.tietKhi} • ${dayDetail.saoHoangDao}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Lunar Right Glassmorphic Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.52f),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = Color(0x66FFD966),
                            shape = RoundedCornerShape(20.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            val leapLabel = if (dayDetail.lunarDate.isLeapMonth) " (Nhuận)" else ""
                            Text(
                                text = "🌙 ÂM LỊCH$leapLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD966)
                            )
                            Text(
                                text = "${dayDetail.lunarDate.day.toString().padStart(2, '0')} / ${dayDetail.lunarDate.month.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Ngày ${dayDetail.canChiDay}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFE082)
                            )
                            Text(
                                text = "Tháng ${dayDetail.canChiMonth} • Năm ${dayDetail.canChiYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Data Source Status Strip inside Hero Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = modeBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE8EAF6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x33FFD966))
                                .clickable(enabled = !isSyncing) { onSyncOfficialDate() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("sync_official_source_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Tra trực tuyến",
                                tint = Color(0xFFFFD966),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSyncing) "Đang tải..." else "Tra trực tuyến",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD966)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarGridCard(
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    todayTriple: Triple<Int, Int, Int>,
    dataVersion: Int,
    events: List<CalendarEvent>,
    onSelectDate: (Int, Int, Int) -> Unit,
    onNavigateMonth: (Int) -> Unit,
    onOpenMonthYearPicker: () -> Unit
) {
    val todayDay = todayTriple.first
    val todayMonth = todayTriple.second
    val todayYear = todayTriple.third

    val daysInMonth = LunarCalendarEngine.getDaysInSolarMonth(selectedMonth, selectedYear)
    // Monday-first grid offset (0 = T2, 6 = CN)
    val firstDayJd = LunarCalendarEngine.jdFromDate(1, selectedMonth, selectedYear)
    val sundayBasedDow = ((firstDayJd + 1) % 7 + 7) % 7 // 0 = Sun, 1 = Mon ... 6 = Sat
    val leadingEmptyCells = if (sundayBasedDow == 0) 6 else sundayBasedDow - 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month Switcher Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigateMonth(-1) },
                    modifier = Modifier.testTag("prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Tháng trước"
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenMonthYearPicker() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("open_month_year_picker_btn")
                ) {
                    Text(
                        text = "Tháng $selectedMonth, $selectedYear ▾",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val midLunar = remember(selectedMonth, selectedYear, dataVersion) {
                        LunarCalendarEngine.solarToLunar(15, selectedMonth, selectedYear)
                    }
                    Text(
                        text = "Tháng ${midLunar.month} Âm lịch • Năm ${LunarCalendarEngine.getCanChiYear(midLunar.year)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onNavigateMonth(1) },
                    modifier = Modifier.testTag("next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tháng sau"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Days of week header (T2 .. CN)
            val weekHeaders = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekHeaders.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (index) {
                            6 -> MaterialTheme.colorScheme.primary
                            5 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Cells
            val totalSlots = leadingEmptyCells + daysInMonth
            val rows = (totalSlots + 6) / 7

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - leadingEmptyCells + 1
                        if (dayNum in 1..daysInMonth) {
                            val cellDetail = remember(dayNum, selectedMonth, selectedYear, dataVersion) {
                                LunarCalendarEngine.getDayDetail(dayNum, selectedMonth, selectedYear)
                            }
                            val lunar = cellDetail.lunarDate
                            val isSelected = dayNum == selectedDay
                            val isToday = dayNum == todayDay && selectedMonth == todayMonth && selectedYear == todayYear
                            val isMung1OrRam = lunar.day == 1 || lunar.day == 15
                            val isHoangDao = cellDetail.isGoodDay
                            val hasUserEvent = events.any {
                                (it.solarDay.toInt() == dayNum && it.solarMonth.toInt() == selectedMonth) ||
                                        (it.isLunarRecurring && it.lunarDay.toInt() == lunar.day && it.lunarMonth.toInt() == lunar.month)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(12.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable {
                                        onSelectDate(dayNum, selectedMonth, selectedYear)
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("calendar_day_$dayNum"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected || isToday || col == 6) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            col == 6 -> MaterialTheme.colorScheme.primary
                                            col == 5 -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    val lunarLabel = if (lunar.day == 1 || dayNum == 1) "${lunar.day}/${lunar.month}" else lunar.day.toString()
                                    Text(
                                        text = lunarLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = if (isMung1OrRam) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                                            isMung1OrRam -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(5.dp)
                                    ) {
                                        if (isHoangDao) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else Color(0xFFFFB300)
                                                    )
                                            )
                                        }
                                        if (hasUserEvent || cellDetail.holidays.any { it.isMajorHoliday }) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Ngày Hoàng Đạo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Lễ Tết / Sự kiện",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Mùng 1/Rằm: Màu vàng",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthYearQuickPickerDialog(
    initialMonth: Int,
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var pickedMonth by remember { mutableIntStateOf(initialMonth) }
    var pickedYear by remember { mutableIntStateOf(initialYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Chọn Nhanh Tháng & Năm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickedYear = (pickedYear - 1).coerceAtLeast(1900) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Năm trước")
                    }
                    Text(
                        text = "Năm $pickedYear (${LunarCalendarEngine.getCanChiYear(pickedYear)})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { pickedYear = (pickedYear + 1).coerceAtMost(2100) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Năm sau")
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (m in 1..12) {
                        FilterChip(
                            selected = pickedMonth == m,
                            onClick = { pickedMonth = m },
                            label = { Text("Tháng $m") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pickedMonth, pickedYear) }) {
                Text("Xem lịch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedDayInsightCard(
    dayDetail: DayDetailInfo,
    onAddEventClick: () -> Unit,
    onShareDayClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dayDetail.dayQualityLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ngày ${dayDetail.canChiDay} • Tháng ${dayDetail.canChiMonth} • Năm ${dayDetail.canChiYear} • Tiết ${dayDetail.tietKhi}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (dayDetail.nguHanhNgay.isNotBlank() || dayDetail.trucNgay.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "☯ Ngũ hành: ${dayDetail.nguHanhNgay}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (dayDetail.saoHoangDao.isNotBlank()) {
                            Text(
                                text = "🌟 Nhị Thập Bát Tú / Thập Nhị Sao: ${dayDetail.saoHoangDao}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "✦ Trực (Kiến Trừ): ${dayDetail.trucNgay}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "🧭 Xuất hành: ${dayDetail.huongXuatHanh}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (dayDetail.viecNenLam.isNotEmpty()) {
                            Text(
                                text = "✓ Nên làm: ${dayDetail.viecNenLam.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (dayDetail.viecKiengKy.isNotEmpty()) {
                            Text(
                                text = "⚠ Lưu ý: ${dayDetail.viecKiengKy.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "📡 Nguồn lịch: ${dayDetail.dataSourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Giờ Hoàng Đạo trong ngày:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dayDetail.zodiacHours.forEach { hourLabel ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = hourLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddEventClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_event_selected_day_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tạo nhắc nhở")
                }

                OutlinedButton(
                    onClick = onShareDayClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_selected_day_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chia sẻ ngày")
                }
            }
        }
    }
}
