package com.example

import com.example.domain.LunarCalendarEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun tetNguyenDan2026_isAccurateInVietnamCalendar() {
        // Mùng 1 Tết Bính Ngọ 2026 là ngày 17/02/2026 Dương lịch
        val lunar = LunarCalendarEngine.solarToLunar(17, 2, 2026)
        assertEquals(1, lunar.day)
        assertEquals(1, lunar.month)
        assertEquals(2026, lunar.year)
        assertEquals("Bính Ngọ", LunarCalendarEngine.getCanChiYear(lunar.year))

        val detail = LunarCalendarEngine.getDayDetail(17, 2, 2026)
        assertTrue(detail.saoHoangDao.isNotBlank())
        assertTrue(detail.trucNgay.isNotBlank())
    }
}
