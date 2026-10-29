package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.LunarCalendarEngine
import java.util.Calendar

class LunarCalendarAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val (day, month, year) = LunarCalendarEngine.getDeviceDateInVietnam()

            val detail = LunarCalendarEngine.getDayDetail(day, month, year)
            val views = RemoteViews(context.packageName, R.layout.widget_lunar_calendar)

            views.setTextViewText(R.id.widget_solar_month_year, "THÁNG $month, $year")
            views.setTextViewText(R.id.widget_solar_day, day.toString().padStart(2, '0'))
            views.setTextViewText(R.id.widget_day_of_week, "${detail.dayOfWeekName} • Dương lịch")

            val leapSuffix = if (detail.lunarDate.isLeapMonth) " (Nhuận)" else ""
            val lunarStr = "${detail.lunarDate.day.toString().padStart(2, '0')} / ${detail.lunarDate.month.toString().padStart(2, '0')} Âm$leapSuffix"
            views.setTextViewText(R.id.widget_lunar_day_month, lunarStr)
            views.setTextViewText(
                R.id.widget_can_chi,
                "Ngày ${detail.canChiDay} • Năm ${detail.canChiYear}"
            )

            val footerText = if (detail.holidays.isNotEmpty()) {
                "★ ${detail.holidays.first().title}"
            } else {
                "Tiết ${detail.tietKhi} • ${if (detail.isGoodDay) "Hoàng Đạo" else "Bình Thường"}"
            }
            views.setTextViewText(R.id.widget_holiday_or_tiet_khi, footerText)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, LunarCalendarAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                val myProvider = ComponentName(context, LunarCalendarAppWidgetProvider::class.java)
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    return true
                }
            }
            return false
        }
    }
}
