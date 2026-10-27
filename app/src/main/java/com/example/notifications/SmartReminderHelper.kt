package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CalendarEvent
import com.example.domain.LunarCalendarEngine
import java.util.Calendar

class SmartReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Nhắc nhở Lịch Vạn Niên"
        val body = intent.getStringExtra("EXTRA_BODY") ?: "Hôm nay có sự kiện Âm - Dương lịch quan trọng."
        SmartReminderHelper.showInstantNotification(context, title, body)
    }
}

object SmartReminderHelper {
    private const val CHANNEL_ID = "lich_van_nien_smart_reminders"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nhắc nhở Lịch Âm Dương & Ngày Lễ",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo tự động ngày Mùng 1, Rằm, Lễ Tết và sự kiện cá nhân"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showInstantNotification(context: Context, title: String, message: String): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(
                (System.currentTimeMillis() % 10000).toInt(),
                notification
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun triggerSmartDailyBriefing(context: Context): Boolean {
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        val detail = LunarCalendarEngine.getDayDetail(d, m, y)
        val holidayText = if (detail.holidays.isNotEmpty()) {
            "Sự kiện: ${detail.holidays.joinToString { it.title }}."
        } else {
            "Tiết ${detail.tietKhi} • ${detail.dayQualityLabel}."
        }
        val title = "Lịch Vạn Niên: ${detail.dayOfWeekName} ($d/$m) • ${detail.lunarDate.day}/${detail.lunarDate.month} Âm"
        val body = "Ngày ${detail.canChiDay}, Tháng ${detail.canChiMonth}, Năm ${detail.canChiYear}. $holidayText Giờ tốt: ${detail.zodiacHours.take(3).joinToString(", ")}."
        return showInstantNotification(context, title, body)
    }

    fun scheduleEventReminder(context: Context, event: CalendarEvent, reminderHour: Int = 8) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, event.solarYear.toInt())
            set(Calendar.MONTH, (event.solarMonth.toInt() - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, event.solarDay.toInt().coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, reminderHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.MINUTE, -event.reminderMinutesBefore.toInt())
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, SmartReminderReceiver::class.java).apply {
            putExtra("EXTRA_TITLE", "Nhắc lịch: ${event.title}")
            putExtra(
                "EXTRA_BODY",
                "Ngày ${event.solarDay}/${event.solarMonth}/${event.solarYear} (${event.lunarDay}/${event.lunarMonth} Âm lịch) • ${event.notes}"
            )
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Fallback if exact/idle alarm is restricted
        }
    }

    fun openGoogleCalendarInsertIntent(context: Context, event: CalendarEvent) {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, event.solarYear.toInt())
            set(Calendar.MONTH, (event.solarMonth.toInt() - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, event.solarDay.toInt().coerceIn(1, 31))
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }

        val descriptionWithLunar = buildString {
            append("📅 Âm lịch: Ngày ${event.lunarDay}/${event.lunarMonth}/${event.lunarYear}")
            if (event.isLunarLeapMonth) append(" (Nhuận)")
            append("\n")
            if (event.notes.isNotBlank()) {
                append("📝 Ghi chú: ${event.notes}\n")
            }
            append("✨ Đồng bộ từ ứng dụng Lịch Vạn Niên")
        }

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "[Âm-Dương] ${event.title}")
            putExtra(CalendarContract.Events.DESCRIPTION, descriptionWithLunar)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endCal.timeInMillis)
            if (event.isLunarRecurring) {
                putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to web Google Calendar template URL if Calendar app is not installed
            val dateStr = String.format(
                "%04d%02d%02d",
                event.solarYear,
                event.solarMonth,
                event.solarDay
            )
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(
                    "https://calendar.google.com/calendar/render?action=TEMPLATE&text=${
                        android.net.Uri.encode(event.title)
                    }&dates=${dateStr}/${dateStr}&details=${
                        android.net.Uri.encode(descriptionWithLunar)
                    }"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }

    fun shareScheduleViaPlatform(
        context: Context,
        title: String,
        solarDateDisplay: String,
        lunarDateDisplay: String,
        notes: String,
        targetEmail: String = ""
    ) {
        val shareText = buildString {
            appendLine("🏮 CHIA SẺ LỊCH TRÌNH - LỊCH VẠN NIÊN 🏮")
            appendLine("📌 Sự kiện: $title")
            appendLine("☀️ Dương lịch: $solarDateDisplay")
            appendLine("🌙 Âm lịch: $lunarDateDisplay")
            if (notes.isNotBlank()) {
                appendLine("📝 Ghi chú: $notes")
            }
            appendLine("🔒 Đã đồng bộ bảo mật qua tài khoản Lịch Vạn Niên.")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "[Lịch Vạn Niên] Lịch trình: $title ($solarDateDisplay)")
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (targetEmail.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(targetEmail.trim()))
            }
        }
        val chooser = Intent.createChooser(intent, "Chia sẻ lịch trình qua Email hoặc Mạng xã hội")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
