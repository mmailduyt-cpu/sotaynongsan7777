package com.example.util

import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.floor

object LunarCalendarConverter {

    data class LunarDate(val day: Int, val month: Int, val year: Int)

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

    private fun newMoon(k: Double): Double {
        val T = k / 1236.85
        val T2 = T * T
        val T3 = T2 * T
        val dr = PI / 180.0
        var Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3
        Jd1 += 0.00033 * sin((166.56 + 132.87 * T - 0.009173 * T2) * dr)
        val M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3
        val Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3
        val F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3
        var C1 = (0.1734 - 0.000393 * T) * sin(M * dr) + 0.0021 * sin(2.0 * dr * M)
        C1 -= 0.4068 * sin(Mpr * dr) + 0.0161 * sin(2.0 * dr * Mpr)
        C1 -= 0.0004 * sin(3.0 * dr * Mpr)
        C1 += 0.0104 * sin(2.0 * dr * F) - 0.0051 * sin((M + Mpr) * dr)
        C1 -= 0.0074 * sin((M - Mpr) * dr) + 0.0004 * sin((2.0 * F + M) * dr)
        C1 -= 0.0004 * sin((2.0 * F - M) * dr) - 0.0006 * sin((2.0 * F + Mpr) * dr)
        C1 += 0.0010 * sin((2.0 * F - Mpr) * dr) + 0.0005 * sin((2.0 * Mpr + M) * dr)
        val deltaT = if (T < -11.0) {
            0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
        } else {
            -0.000278 + 0.000265 * T + 0.000262 * T2
        }
        return Jd1 + C1 - deltaT
    }

    private fun getNewMoonDay(k: Double, timeZone: Double): Double {
        return floor(newMoon(k) + 0.5 + timeZone / 24.0)
    }

    private fun sunLongitude(jdn: Double, timeZone: Double): Double {
        val T = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val T2 = T * T
        val dr = PI / 180.0
        val M = 357.5291 + 35999.0503 * T - 0.0001559 * T2 - 0.00000048 * T * T2
        val L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2
        val DL = (1.9146 - 0.004817 * T - 0.000014 * T2) * sin(dr * M) +
                (0.019993 - 0.000101 * T) * sin(2.0 * dr * M) +
                0.00029 * sin(3.0 * dr * M)
        var L = (L0 + DL) * dr
        L -= PI * 2.0 * floor(L / (PI * 2.0))
        return floor((L / PI) * 6.0)
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double): Double {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853)
        var nm = getNewMoonDay(k, timeZone)
        if (sunLongitude(nm, timeZone) >= 9.0) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Double, timeZone: Double): Double {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5)
        var last: Double
        var i = 1
        var arc = sunLongitude(getNewMoonDay(k + i.toDouble(), timeZone), timeZone)
        do {
            last = arc
            i += 1
            arc = sunLongitude(getNewMoonDay(k + i.toDouble(), timeZone), timeZone)
        } while (arc != last && i < 14)
        return (i - 1).toDouble()
    }

    fun convertSolar2Lunar(dd: Int, mm: Int, yy: Int, timeZone: Double = 7.0): LunarDate {
        val dayNumber = jdFromDate(dd, mm, yy).toDouble()
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853)
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone)
        }
        val currentYear = yy
        var a11 = getLunarMonth11(currentYear, timeZone)
        var b11 = a11
        var lunarYear = currentYear

        if (a11 >= monthStart) {
            lunarYear = currentYear
            a11 = getLunarMonth11(currentYear - 1, timeZone)
        } else {
            lunarYear = currentYear + 1
            b11 = getLunarMonth11(currentYear + 1, timeZone)
        }

        val lunarDay = (dayNumber - monthStart + 1).toInt()
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarMonth = diff + 11

        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone).toInt()
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
            }
        }

        if (lunarMonth > 12) {
            lunarMonth -= 12
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1
        }
        return LunarDate(lunarDay, lunarMonth, lunarYear)
    }

    fun lunarTextFromDateTime(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        return try {
            val parts = dateStr.substring(0, 10).split("-")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull() ?: return ""
                val m = parts[1].toIntOrNull() ?: return ""
                val d = parts[2].toIntOrNull() ?: return ""
                val lunar = convertSolar2Lunar(d, m, y)
                "ÂL ${String.format("%02d", lunar.day)}/${String.format("%02d", lunar.month)}"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
