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

class LunarCalendarAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            refreshAllWidgets(context)
        }
    }

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
        private const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val (day, month, year) = LunarCalendarEngine.getDeviceDateInVietnam()

            val detail = LunarCalendarEngine.getDayDetail(day, month, year)
            val views = RemoteViews(context.packageName, R.layout.widget_lunar_calendar)

            views.setTextViewText(R.id.widget_solar_month_year, "THÁNG $month • $year")
            views.setTextViewText(R.id.widget_solar_day, day.toString().padStart(2, '0'))
            views.setTextViewText(R.id.widget_day_of_week, detail.dayOfWeekName)

            val leapSuffix = if (detail.lunarDate.isLeapMonth) " (Nhuận)" else ""
            views.setTextViewText(
                R.id.widget_lunar_badge,
                "🌙 ÂM LỊCH • NĂM ${detail.canChiYear.uppercase()}"
            )
            val lunarStr = "${detail.lunarDate.day.toString().padStart(2, '0')} / ${detail.lunarDate.month.toString().padStart(2, '0')} Âm$leapSuffix"
            views.setTextViewText(R.id.widget_lunar_day_month, lunarStr)
            views.setTextViewText(
                R.id.widget_can_chi,
                "Ngày ${detail.canChiDay} • Tháng ${detail.canChiMonth}"
            )

            val footerText = if (detail.holidays.isNotEmpty()) {
                "★ ${detail.holidays.first().title}"
            } else {
                val saoShort = detail.saoHoangDao.ifBlank { if (detail.isGoodDay) "Hoàng Đạo" else "Bình Thường" }
                "✦ Tiết ${detail.tietKhi} • $saoShort"
            }
            views.setTextViewText(R.id.widget_holiday_or_tiet_khi, footerText)

            // Tap root -> open MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

            // Tap Refresh badge -> refresh widget immediately
            val refreshIntent = Intent(context, LunarCalendarAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

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
