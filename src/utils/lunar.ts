/**
 * Vietnam Lunar Calendar Calculation Utility
 * Ported from com.example.util.LunarCalendarConverter
 */

export interface LunarDate {
  day: number;
  month: number;
  year: number;
}

export function jdFromDate(dd: number, mm: number, yy: number): number {
  let a = Math.floor((14 - mm) / 12);
  let y = yy + 4800 - a;
  let m = mm + 12 * a - 3;
  let jd = dd + Math.floor((153 * m + 2) / 5) + 365 * y + Math.floor(y / 4) - Math.floor(y / 100) + Math.floor(y / 400) - 32045;
  if (jd < 2299161) {
    jd = dd + Math.floor((153 * m + 2) / 5) + 365 * y + Math.floor(y / 4) - 32083;
  }
  return jd;
}

function newMoon(k: number): number {
  const T = k / 1236.85;
  const T2 = T * T;
  const T3 = T2 * T;
  const dr = Math.PI / 180.0;
  let Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
  Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
  const M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
  const Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
  const F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
  let C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2.0 * dr * M);
  C1 -= 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(2.0 * dr * Mpr);
  C1 -= 0.0004 * Math.sin(3.0 * dr * Mpr);
  C1 += 0.0104 * Math.sin(2.0 * dr * F) - 0.0051 * Math.sin((M + Mpr) * dr);
  C1 -= 0.0074 * Math.sin((M - Mpr) * dr) + 0.0004 * Math.sin((2.0 * F + M) * dr);
  C1 -= 0.0004 * Math.sin((2.0 * F - M) * dr) - 0.0006 * Math.sin((2.0 * F + Mpr) * dr);
  C1 += 0.0010 * Math.sin((2.0 * F - Mpr) * dr) + 0.0005 * Math.sin((2.0 * Mpr + M) * dr);
  
  const deltaT = T < -11.0 
    ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3 
    : -0.000278 + 0.000265 * T + 0.000262 * T2;
    
  return Jd1 + C1 - deltaT;
}

function getNewMoonDay(k: number, timeZone: number): number {
  return Math.floor(newMoon(k) + 0.5 + timeZone / 24.0);
}

function sunLongitude(jdn: number, timeZone: number): number {
  const T = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0;
  const T2 = T * T;
  const dr = Math.PI / 180.0;
  const M = 357.5291 + 35999.0503 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
  const L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
  const DL = (1.9146 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M) +
             (0.019993 - 0.000101 * T) * Math.sin(2.0 * dr * M) +
             0.00029 * Math.sin(3.0 * dr * M);
  let L = (L0 + DL) * dr;
  L -= Math.PI * 2.0 * Math.floor(L / (Math.PI * 2.0));
  return Math.floor((L / Math.PI) * 6.0);
}

function getLunarMonth11(yy: number, timeZone: number): number {
  const off = jdFromDate(31, 12, yy) - 2415021;
  const k = Math.floor(off / 29.530588853);
  let nm = getNewMoonDay(k, timeZone);
  if (sunLongitude(nm, timeZone) >= 9.0) {
    nm = getNewMoonDay(k - 1, timeZone);
  }
  return nm;
}

function getLeapMonthOffset(a11: number, timeZone: number): number {
  const k = Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
  let last: number;
  let i = 1;
  let arc = sunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
  do {
    last = arc;
    i += 1;
    arc = sunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
  } while (arc !== last && i < 14);
  return i - 1;
}

export function convertSolar2Lunar(dd: number, mm: number, yy: number, timeZone: number = 7.0): LunarDate {
  const dayNumber = jdFromDate(dd, mm, yy);
  const k = Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
  let monthStart = getNewMoonDay(k + 1, timeZone);
  if (monthStart > dayNumber) {
    monthStart = getNewMoonDay(k, timeZone);
  }
  const currentYear = yy;
  let a11 = getLunarMonth11(currentYear, timeZone);
  let b11 = a11;
  let lunarYear = currentYear;

  if (a11 >= monthStart) {
    lunarYear = currentYear;
    a11 = getLunarMonth11(currentYear - 1, timeZone);
  } else {
    lunarYear = currentYear + 1;
    b11 = getLunarMonth11(currentYear + 1, timeZone);
  }

  const lunarDay = dayNumber - monthStart + 1;
  const diff = Math.floor((monthStart - a11) / 29.0);
  let lunarMonth = diff + 11;

  if (b11 - a11 > 365) {
    const leapMonthDiff = getLeapMonthOffset(a11, timeZone);
    if (diff >= leapMonthDiff) {
      lunarMonth = diff + 10;
    }
  }

  if (lunarMonth > 12) {
    lunarMonth -= 12;
  }
  if (lunarMonth >= 11 && diff < 4) {
    lunarYear -= 1;
  }
  return { day: lunarDay, month: lunarMonth, year: lunarYear };
}

export function lunarTextFromDateTime(dateStr: string): string {
  if (!dateStr) return "";
  try {
    const parts = dateStr.substring(0, 10).split("-");
    if (parts.length === 3) {
      const y = parseInt(parts[0], 10);
      const m = parseInt(parts[1], 10);
      const d = parseInt(parts[2], 10);
      if (!isNaN(y) && !isNaN(m) && !isNaN(d)) {
        const lunar = convertSolar2Lunar(d, m, y);
        return `ÂL ${lunar.day.toString().padStart(2, "0")}/${lunar.month.toString().padStart(2, "0")}`;
      }
    }
    return "";
  } catch (e) {
    return "";
  }
}
