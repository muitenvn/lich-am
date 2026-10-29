package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.HolidayInfo
import com.example.domain.LunarCalendarEngine
import com.example.domain.LunarDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CalendarSyncStatus(
    val isOnline: Boolean,
    val sourceName: String,
    val currentSolarDate: Triple<Int, Int, Int>,
    val syncedTimeDisplay: String
)

object OfficialCalendarDataSource {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    private val fetchedHolidayYears = ConcurrentHashMap.newKeySet<Int>()
    private val fetchedLunarDates = ConcurrentHashMap.newKeySet<String>()

    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetches the current date & calendar data:
     * - When ONLINE: Fetches directly from official online time & calendar servers (UTC+7 Vietnam).
     * - When OFFLINE: Falls back to the device's local clock in Vietnam timezone.
     */
    suspend fun syncCurrentDateAndCalendar(
        context: Context,
        selectedDay: Int? = null,
        selectedMonth: Int? = null,
        selectedYear: Int? = null
    ): CalendarSyncStatus = withContext(Dispatchers.IO) {
        val hasNetwork = isNetworkConnected(context)
        if (!hasNetwork) {
            val deviceDate = LunarCalendarEngine.getDeviceDateInVietnam()
            val timeStr = formatVietnamTimeNow()
            return@withContext CalendarSyncStatus(
                isOnline = false,
                sourceName = "Ngoại tuyến (Không có mạng) • Dựa vào đồng hồ điện thoại",
                currentSolarDate = deviceDate,
                syncedTimeDisplay = timeStr
            )
        }

        // 1. Fetch official online current Vietnam date (UTC+7)
        val onlineDatePair = fetchOfficialVietnamDateOnline()
        if (onlineDatePair != null) {
            val (onlineTriple, serverLabel) = onlineDatePair
            val targetYear = selectedYear ?: onlineTriple.third
            val targetMonth = selectedMonth ?: onlineTriple.second
            val targetDay = selectedDay ?: onlineTriple.first

            // 2. Fetch official public holidays for Vietnam online (Nager.Date Official Public Holidays API)
            fetchOfficialVietnamHolidaysForYear(targetYear)
            if (targetYear != onlineTriple.third) {
                fetchOfficialVietnamHolidaysForYear(onlineTriple.third)
            }

            // 3. Fetch & cross-verify official online astronomical Lunar date for today & selected day
            fetchOfficialLunarDateOnline(onlineTriple.first, onlineTriple.second, onlineTriple.third)
            if (targetDay != onlineTriple.first || targetMonth != onlineTriple.second || targetYear != onlineTriple.third) {
                fetchOfficialLunarDateOnline(targetDay, targetMonth, targetYear)
            }

            return@withContext CalendarSyncStatus(
                isOnline = true,
                sourceName = "Trực tuyến chính thức ($serverLabel • UTC+7)",
                currentSolarDate = onlineTriple,
                syncedTimeDisplay = formatVietnamTimeNow()
            )
        }

        // If network is connected in settings but all internet servers timed out, fallback to device clock
        val fallbackDate = LunarCalendarEngine.getDeviceDateInVietnam()
        CalendarSyncStatus(
            isOnline = false,
            sourceName = "Ngoại tuyến (Mạng không phản hồi) • Dựa vào đồng hồ điện thoại",
            currentSolarDate = fallbackDate,
            syncedTimeDisplay = formatVietnamTimeNow()
        )
    }

    /**
     * Fetches official online data for a specific date when the user browses dates while online.
     */
    suspend fun fetchOnlineDataForDateIfConnected(
        context: Context,
        solarDay: Int,
        solarMonth: Int,
        solarYear: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkConnected(context)) return@withContext false
        val hOk = fetchOfficialVietnamHolidaysForYear(solarYear)
        val lOk = fetchOfficialLunarDateOnline(solarDay, solarMonth, solarYear)
        hOk || lOk
    }

    private fun fetchOfficialVietnamDateOnline(): Pair<Triple<Int, Int, Int>, String>? {
        // Source 1: Official TimeAPI.io IANA Timezone Server (Asia/Ho_Chi_Minh)
        try {
            val req = Request.Builder()
                .url("https://timeapi.io/api/time/current/zone?timeZone=Asia/Ho_Chi_Minh")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val year = json.optInt("year", 0)
                    val month = json.optInt("month", 0)
                    val day = json.optInt("day", 0)
                    if (year in 2020..2100 && month in 1..12 && day in 1..31) {
                        return Triple(day, month, year) to "TimeAPI.io IANA"
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Source 2: Official Cloudflare Global Time Trace (ts=<epoch_seconds>)
        try {
            val req = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val tsLine = body.lineSequence().firstOrNull { it.startsWith("ts=") }
                    val secondsStr = tsLine?.removePrefix("ts=")?.substringBefore(".")
                    val epochSeconds = secondsStr?.toLongOrNull()
                    if (epochSeconds != null && epochSeconds > 1700000000L) {
                        val cal = Calendar.getInstance(LunarCalendarEngine.VIETNAM_TIME_ZONE).apply {
                            timeInMillis = epochSeconds * 1000L
                        }
                        return Triple(
                            cal.get(Calendar.DAY_OF_MONTH),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.YEAR)
                        ) to "Cloudflare Time Server"
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Source 3: Official WorldTimeAPI (Asia/Ho_Chi_Minh)
        try {
            val req = Request.Builder()
                .url("https://worldtimeapi.org/api/timezone/Asia/Ho_Chi_Minh")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val datetime = json.optString("datetime") // YYYY-MM-DDTHH:mm:ss...
                    if (datetime.length >= 10) {
                        val datePart = datetime.substring(0, 10).split("-")
                        val y = datePart.getOrNull(0)?.toIntOrNull() ?: 0
                        val m = datePart.getOrNull(1)?.toIntOrNull() ?: 0
                        val d = datePart.getOrNull(2)?.toIntOrNull() ?: 0
                        if (y in 2020..2100 && m in 1..12 && d in 1..31) {
                            return Triple(d, m, y) to "WorldTimeAPI.org"
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Source 4: Authoritative HTTP Date header from Google (https://www.google.com/generate_204)
        try {
            val req = Request.Builder()
                .url("https://www.google.com/generate_204")
                .head()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val dateHeader = resp.header("Date")
                if (!dateHeader.isNullOrBlank()) {
                    val rfcFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    val parsedDate = rfcFormat.parse(dateHeader)
                    if (parsedDate != null) {
                        val cal = Calendar.getInstance(LunarCalendarEngine.VIETNAM_TIME_ZONE).apply {
                            time = parsedDate
                        }
                        return Triple(
                            cal.get(Calendar.DAY_OF_MONTH),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.YEAR)
                        ) to "Google Time Server"
                    }
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    private fun fetchOfficialVietnamHolidaysForYear(year: Int): Boolean {
        if (fetchedHolidayYears.contains(year)) return true
        return try {
            val req = Request.Builder()
                .url("https://date.nager.at/api/v3/PublicHolidays/$year/VN")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val raw = resp.body?.string().orEmpty()
                val arr = JSONArray(raw)
                val byDate = mutableMapOf<Triple<Int, Int, Int>, MutableList<HolidayInfo>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val dateStr = obj.optString("date") // YYYY-MM-DD
                    val localName = obj.optString("localName")
                    val intlName = obj.optString("name")
                    val parts = dateStr.split("-")
                    val y = parts.getOrNull(0)?.toIntOrNull() ?: continue
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val d = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    if (localName.isNotBlank()) {
                        val key = Triple(d, m, y)
                        val list = byDate.getOrPut(key) { mutableListOf() }
                        list.add(
                            HolidayInfo(
                                title = localName,
                                description = "Lễ chính thức Việt Nam ($intlName • Dữ liệu trực tuyến)",
                                isLunar = false,
                                isMajorHoliday = true,
                                isOfficialOnline = true
                            )
                        )
                    }
                }
                byDate.forEach { (triple, holidays) ->
                    LunarCalendarEngine.registerOnlineHolidaysForDate(
                        triple.first,
                        triple.second,
                        triple.third,
                        holidays
                    )
                }
                fetchedHolidayYears.add(year)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchOfficialLunarDateOnline(solarDay: Int, solarMonth: Int, solarYear: Int): Boolean {
        val dateIso = String.format("%04d-%02d-%02d", solarYear, solarMonth, solarDay)
        if (fetchedLunarDates.contains(dateIso)) return true
        return try {
            // Official Meteorological Observatory OpenData Lunar API
            val req = Request.Builder()
                .url("https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=$dateIso")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                // Format: {"LunarYear":"...", "LunarDate":"MM-DD"}
                val lunarDateStr = json.optString("LunarDate")
                if (lunarDateStr.contains("-")) {
                    val parts = lunarDateStr.split("-")
                    val onlineMonth = parts.getOrNull(0)?.filter(Char::isDigit)?.toIntOrNull() ?: 0
                    val onlineDay = parts.getOrNull(1)?.filter(Char::isDigit)?.toIntOrNull() ?: 0
                    if (onlineDay in 1..30 && onlineMonth in 1..12) {
                        // Cross-verify with Vietnam UTC+7 astronomical new moon (ICU Asia/Ho_Chi_Minh)
                        val vnCalculated = LunarCalendarEngine.solarToLunar(solarDay, solarMonth, solarYear)
                        val verifiedLunar = if (kotlin.math.abs(vnCalculated.day - onlineDay) <= 1 && vnCalculated.month == onlineMonth) {
                            vnCalculated // Preserves exact UTC+7 Vietnam midnight boundary
                        } else {
                            val lYear = if (solarMonth <= 2 && onlineMonth >= 11) solarYear - 1 else solarYear
                            LunarDate(onlineDay, onlineMonth, lYear, vnCalculated.isLeapMonth)
                        }
                        LunarCalendarEngine.registerOnlineLunarDate(solarDay, solarMonth, solarYear, verifiedLunar)
                        fetchedLunarDates.add(dateIso)
                        return true
                    }
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun formatVietnamTimeNow(): String {
        val fmt = SimpleDateFormat("HH:mm:ss 'GMT+7'", Locale.forLanguageTag("vi-VN")).apply {
            timeZone = LunarCalendarEngine.VIETNAM_TIME_ZONE
        }
        return fmt.format(Calendar.getInstance(LunarCalendarEngine.VIETNAM_TIME_ZONE).time)
    }
}
