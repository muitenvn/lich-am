package com.example.domain

import kotlin.math.floor
import kotlin.math.sin

data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val isLeapMonth: Boolean = false
)

data class HolidayInfo(
    val title: String,
    val description: String,
    val isLunar: Boolean,
    val isMajorHoliday: Boolean = false
)

data class DayDetailInfo(
    val solarDay: Int,
    val solarMonth: Int,
    val solarYear: Int,
    val dayOfWeekName: String,
    val lunarDate: LunarDate,
    val canChiYear: String,
    val canChiMonth: String,
    val canChiDay: String,
    val tietKhi: String,
    val zodiacHours: List<String>,
    val holidays: List<HolidayInfo>,
    val isGoodDay: Boolean,
    val dayQualityLabel: String
)

/**
 * Astronomical Vietnamese Lunar-Solar Calendar Engine (UTC+7 Vietnam Timezone)
 * Based on Ho Ngoc Duc's astronomical algorithm.
 */
object LunarCalendarEngine {

    private val CAN = listOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = listOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
    private val CHI_HOURS = listOf(
        "Tý (23h-1h)", "Sửu (1h-3h)", "Dần (3h-5h)", "Mão (5h-7h)",
        "Thìn (7h-9h)", "Tỵ (9h-11h)", "Ngọ (11h-13h)", "Mùi (13h-15h)",
        "Thân (15h-17h)", "Dậu (17h-19h)", "Tuất (19h-21h)", "Hợi (21h-23h)"
    )

    private val TIET_KHI = listOf(
        "Xuân Phân", "Thanh Minh", "Cốc Vũ", "Lập Hạ", "Tiểu Mãn", "Mang Chủng",
        "Hạ Chí", "Tiểu Thử", "Đại Thử", "Lập Thu", "Xử Thử", "Bạch Lộ",
        "Thu Phân", "Hàn Lộ", "Sương Giáng", "Lập Đông", "Tiểu Tuyết", "Đại Tuyết",
        "Đông Chí", "Tiểu Hàn", "Đại Hàn", "Lập Xuân", "Vũ Thủy", "Kinh Trập"
    )

    private const val TIME_ZONE = 7.0

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    fun jdToDate(jd: Int): Triple<Int, Int, Int> {
        val a: Int
        val b: Int
        val c: Int
        if (jd > 2299160) {
            a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - (b * 146097) / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return Triple(day, month, year)
    }

    private fun getNewMoonDay(k: Int, timeZone: Double = TIME_ZONE): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 += 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        val jdNew = jd1 + c1 - deltat
        return floor(jdNew + 0.5 + timeZone / 24.0).toInt()
    }

    private fun getSunLongitude(jdn: Int, timeZone: Double = TIME_ZONE): Int {
        val t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val t2 = t * t
        val dr = Math.PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = l0 + dl
        l *= dr
        l -= Math.PI * 2 * floor(l / (Math.PI * 2))
        return floor(l / Math.PI * 6).toInt()
    }

    private fun getSolarTermIndex(jdn: Int, timeZone: Double = TIME_ZONE): Int {
        val t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val t2 = t * t
        val dr = Math.PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = l0 + dl
        l *= dr
        l -= Math.PI * 2 * floor(l / (Math.PI * 2))
        return floor(l / Math.PI * 12).toInt() % 24
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double = TIME_ZONE): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitude(nm, timeZone)
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double = TIME_ZONE): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    fun solarToLunar(dd: Int, mm: Int, yy: Int): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k)
        }
        var a11 = getLunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = ((monthStart - a11) / 29)
        var lunarLeap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) {
                    lunarLeap = true
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1
        }
        return LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }

    fun lunarToSolar(lunarDay: Int, lunarMonth: Int, lunarYear: Int, lunarLeap: Boolean = false): Triple<Int, Int, Int> {
        val a11: Int
        val b11: Int
        if (lunarMonth < 11) {
            a11 = getLunarMonth11(lunarYear - 1)
            b11 = getLunarMonth11(lunarYear)
        } else {
            a11 = getLunarMonth11(lunarYear)
            b11 = getLunarMonth11(lunarYear + 1)
        }
        val k = floor(0.5 + (a11 - 2415021.076998695) / 29.530588853).toInt()
        var off = lunarMonth - 11
        if (off < 0) {
            off += 12
        }
        if (b11 - a11 > 365) {
            val leapOff = getLeapMonthOffset(a11)
            var leapMonth = leapOff - 2
            if (leapMonth < 0) {
                leapMonth += 12
            }
            if (lunarLeap && lunarMonth != leapMonth) {
                return Triple(1, 1, lunarYear)
            } else if (lunarLeap || off >= leapOff) {
                off += 1
            }
        }
        val monthStart = getNewMoonDay(k + off)
        return jdToDate(monthStart + lunarDay - 1)
    }

    fun getCanChiYear(lunarYear: Int): String {
        val can = CAN[(lunarYear + 6) % 10]
        val chi = CHI[(lunarYear + 8) % 12]
        return "$can $chi"
    }

    fun getCanChiMonth(lunarMonth: Int, lunarYear: Int): String {
        val canIndex = (lunarYear * 12 + lunarMonth + 3) % 10
        val chiIndex = (lunarMonth + 1) % 12
        return "${CAN[canIndex]} ${CHI[chiIndex]}"
    }

    fun getCanChiDay(jd: Int): String {
        val can = CAN[(jd + 9) % 10]
        val chi = CHI[(jd + 1) % 12]
        return "$can $chi"
    }

    fun getAuspiciousHours(jd: Int): List<String> {
        val chiOfDay = (jd + 1) % 12
        val pattern = when (chiOfDay % 6) {
            0 -> listOf(0, 1, 4, 5, 7, 10) // Tý, Ngọ
            1 -> listOf(2, 3, 6, 7, 9, 11) // Sửu, Mùi
            2 -> listOf(0, 1, 4, 5, 8, 9)  // Dần, Thân
            3 -> listOf(0, 2, 3, 6, 7, 11) // Mão, Dậu
            4 -> listOf(2, 4, 5, 8, 9, 10) // Thìn, Tuất
            else -> listOf(1, 4, 6, 7, 10, 11) // Tỵ, Hợi
        }
        return pattern.map { CHI_HOURS[it] }
    }

    fun getHolidaysForDate(solarDay: Int, solarMonth: Int, lunarDate: LunarDate): List<HolidayInfo> {
        val list = mutableListOf<HolidayInfo>()
        // Lunar holidays
        when ("${lunarDate.day}/${lunarDate.month}") {
            "1/1" -> list.add(HolidayInfo("Mùng 1 Tết Nguyên Đán", "Tết cổ truyền dân tộc Việt Nam", isLunar = true, isMajorHoliday = true))
            "2/1" -> list.add(HolidayInfo("Mùng 2 Tết Nguyên Đán", "Du xuân chúc Tết họ hàng", isLunar = true, isMajorHoliday = true))
            "3/1" -> list.add(HolidayInfo("Mùng 3 Tết Nguyên Đán", "Lễ tạ thần linh, chúc Tết thầy cô", isLunar = true, isMajorHoliday = true))
            "15/1" -> list.add(HolidayInfo("Tết Nguyên Tiêu (Rằm Tháng Giêng)", "Lễ Thượng Nguyên cầu bình an", isLunar = true, isMajorHoliday = true))
            "3/3" -> list.add(HolidayInfo("Tết Hàn Thực", "Ngày lễ bánh trôi bánh chay (3/3 Âm lịch)", isLunar = true))
            "10/3" -> list.add(HolidayInfo("Giỗ Tổ Hùng Vương", "Quốc lễ tưởng nhớ các Vua Hùng (10/3 Âm lịch)", isLunar = true, isMajorHoliday = true))
            "15/4" -> list.add(HolidayInfo("Lễ Phật Đản", "Kỷ niệm ngày Đức Phật đản sinh (15/4 Âm lịch)", isLunar = true, isMajorHoliday = true))
            "5/5" -> list.add(HolidayInfo("Tết Đoan Ngọ", "Tết diệt sâu bọ giữa năm (5/5 Âm lịch)", isLunar = true))
            "15/7" -> list.add(HolidayInfo("Lễ Vu Lan Báo Hiếu", "Rằm tháng Bảy - Mùa báo hiếu cha mẹ", isLunar = true, isMajorHoliday = true))
            "15/8" -> list.add(HolidayInfo("Tết Trung Thu", "Tết đoàn viên và trông trăng (15/8 Âm lịch)", isLunar = true, isMajorHoliday = true))
            "9/9" -> list.add(HolidayInfo("Tết Trùng Cửu", "Ngày lễ trường thọ cổ truyền (9/9 Âm lịch)", isLunar = true))
            "10/10" -> list.add(HolidayInfo("Tết Thường Tân", "Mừng mùa lúa mới (10/10 Âm lịch)", isLunar = true))
            "15/10" -> list.add(HolidayInfo("Tết Hạ Nguyên", "Lễ tạ ơn thần linh cuối mùa vụ", isLunar = true))
            "23/12" -> list.add(HolidayInfo("Ông Công Ông Táo", "Tiễn Táo Quân về trời (23 tháng Chạp)", isLunar = true, isMajorHoliday = true))
            "30/12" -> list.add(HolidayInfo("Đêm Giao Thừa (Tất Niên)", "Lễ cúng Tất niên đón năm mới", isLunar = true, isMajorHoliday = true))
        }

        if (lunarDate.day == 1 && list.none { it.isLunar }) {
            list.add(HolidayInfo("Mùng 1 Đầu Tháng Âm Lịch", "Ngày Sóc - Khởi đầu tháng Âm lịch mới", isLunar = true))
        } else if (lunarDate.day == 15 && list.none { it.isLunar }) {
            list.add(HolidayInfo("Ngày Rằm Tháng ${lunarDate.month}", "Ngày Vọng - Trăng tròn giữa tháng Âm lịch", isLunar = true))
        }

        // Solar holidays
        when ("$solarDay/$solarMonth") {
            "1/1" -> list.add(HolidayInfo("Tết Dương Lịch", "Chào đón năm mới Dương lịch", isLunar = false, isMajorHoliday = true))
            "14/2" -> list.add(HolidayInfo("Lễ Tình Nhân (Valentine)", "Ngày tôn vinh tình yêu đôi lứa", isLunar = false))
            "27/2" -> list.add(HolidayInfo("Ngày Thầy Thuốc Việt Nam", "Tôn vinh ngành Y tế Việt Nam", isLunar = false))
            "8/3" -> list.add(HolidayInfo("Quốc Tế Phụ Nữ 8/3", "Tôn vinh phụ nữ toàn cầu", isLunar = false, isMajorHoliday = true))
            "26/3" -> list.add(HolidayInfo("Ngày Thành Lập Đoàn TNCS", "Kỷ niệm ngày thành lập Đoàn 26/3", isLunar = false))
            "30/4" -> list.add(HolidayInfo("Ngày Giải Phóng Miền Nam", "Kỷ niệm thống nhất đất nước 30/4", isLunar = false, isMajorHoliday = true))
            "1/5" -> list.add(HolidayInfo("Quốc Tế Lao Động 1/5", "Ngày lễ người lao động quốc tế", isLunar = false, isMajorHoliday = true))
            "19/5" -> list.add(HolidayInfo("Ngày Sinh Chủ Tịch Hồ Chí Minh", "Kỷ niệm ngày sinh Bác Hồ 19/5", isLunar = false))
            "1/6" -> list.add(HolidayInfo("Quốc Tế Thiếu Nhi 1/6", "Ngày hội chăm sóc trẻ em", isLunar = false))
            "28/6" -> list.add(HolidayInfo("Ngày Gia Đình Việt Nam", "Tôn vinh mái ấm gia đình Việt", isLunar = false))
            "27/7" -> list.add(HolidayInfo("Ngày Thương Binh Liệt Sĩ", "Uống nước nhớ nguồn, tri ân anh hùng", isLunar = false))
            "19/8" -> list.add(HolidayInfo("Cách Mạng Tháng Tám", "Kỷ niệm Cách mạng Tháng Tám thành công", isLunar = false))
            "2/9" -> list.add(HolidayInfo("Quốc Khánh Việt Nam 2/9", "Lễ Quốc Khánh nước CHXHCN Việt Nam", isLunar = false, isMajorHoliday = true))
            "10/10" -> list.add(HolidayInfo("Giải Phóng Thủ Đô", "Kỷ niệm giải phóng Thủ đô Hà Nội", isLunar = false))
            "13/10" -> list.add(HolidayInfo("Ngày Doanh Nhân Việt Nam", "Tôn vinh cộng đồng doanh nhân", isLunar = false))
            "20/10" -> list.add(HolidayInfo("Ngày Phụ Nữ Việt Nam 20/10", "Tôn vinh phụ nữ Việt Nam", isLunar = false, isMajorHoliday = true))
            "20/11" -> list.add(HolidayInfo("Ngày Nhà Giáo Việt Nam 20/11", "Tôn sư trọng đạo, tri ân thầy cô", isLunar = false, isMajorHoliday = true))
            "22/12" -> list.add(HolidayInfo("Ngày Thành Lập QĐND Việt Nam", "Kỷ niệm Quân đội Nhân dân Việt Nam", isLunar = false))
            "24/12" -> list.add(HolidayInfo("Lễ Giáng Sinh (Noel)", "Đêm Giáng Sinh an lành", isLunar = false))
        }
        return list
    }

    fun getDayDetail(solarDay: Int, solarMonth: Int, solarYear: Int): DayDetailInfo {
        val jd = jdFromDate(solarDay, solarMonth, solarYear)
        val lunar = solarToLunar(solarDay, solarMonth, solarYear)
        val dayOfWeekIdx = (jd + 1) % 7 // 0 = Sunday, 1 = Monday, ...
        val dayOfWeekName = when (dayOfWeekIdx) {
            0 -> "Chủ Nhật"
            1 -> "Thứ Hai"
            2 -> "Thứ Ba"
            3 -> "Thứ Tư"
            4 -> "Thứ Năm"
            5 -> "Thứ Sáu"
            else -> "Thứ Bảy"
        }
        val tietKhi = TIET_KHI[getSolarTermIndex(jd)]
        val isGoodDay = ((jd + lunar.month) % 3 != 1)
        val qualityLabel = if (isGoodDay) "Ngày Hoàng Đạo (Tốt cho khai trương, xuất hành, cầu phúc)" else "Ngày Bình Thường (Nên thận trọng việc lớn)"

        return DayDetailInfo(
            solarDay = solarDay,
            solarMonth = solarMonth,
            solarYear = solarYear,
            dayOfWeekName = dayOfWeekName,
            lunarDate = lunar,
            canChiYear = getCanChiYear(lunar.year),
            canChiMonth = getCanChiMonth(lunar.month, lunar.year),
            canChiDay = getCanChiDay(jd),
            tietKhi = tietKhi,
            zodiacHours = getAuspiciousHours(jd),
            holidays = getHolidaysForDate(solarDay, solarMonth, lunar),
            isGoodDay = isGoodDay,
            dayQualityLabel = qualityLabel
        )
    }

    fun getDaysInSolarMonth(month: Int, year: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    fun getAllUpcomingHolidays(fromDay: Int, fromMonth: Int, fromYear: Int, count: Int = 12): List<Pair<DayDetailInfo, HolidayInfo>> {
        val results = mutableListOf<Pair<DayDetailInfo, HolidayInfo>>()
        val startJd = jdFromDate(fromDay, fromMonth, fromYear)
        for (offset in 0..365) {
            val (d, m, y) = jdToDate(startJd + offset)
            val detail = getDayDetail(d, m, y)
            for (h in detail.holidays) {
                if (h.isMajorHoliday || h.title.startsWith("Mùng 1") || h.title.startsWith("Ngày Rằm") || !h.isLunar) {
                    results.add(detail to h)
                    if (results.size >= count) return results
                }
            }
        }
        return results
    }
}
