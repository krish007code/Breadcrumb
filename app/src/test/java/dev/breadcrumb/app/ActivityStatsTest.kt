package dev.breadcrumb.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ActivityStatsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 7)
    private fun time(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun multiplePhotosOnOneDayCountAsOneActiveDay() {
        val stats = ActivityStats(listOf(time(today), time(today) + 3600000, time(today.minusDays(1))), today, zone)
        assertEquals(2, stats.counts[today])
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertEquals(2, stats.activeDaysThisMonth)
    }

    @Test fun yesterdaysStreakRemainsAliveUntilTodayEnds() {
        val stats = ActivityStats(listOf(time(today.minusDays(1)), time(today.minusDays(2))), today, zone)
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
    }

    @Test fun gapsBreakCurrentStreakButPreserveBest() {
        val stats = ActivityStats(listOf(3L, 4L, 5L).map { time(today.minusDays(it)) }, today, zone)
        assertEquals(0, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
        assertEquals(today.minusDays(6), stats.dates(7).first())
        assertEquals(today, stats.dates(7).last())
    }

    @Test fun datesUsePhoneTimezoneAndThirtyDayWindow() {
        val stats = ActivityStats(listOf(time(today) - 1, time(today.minusDays(30))), today, zone)
        assertEquals(1, stats.counts[today.minusDays(1)])
        assertEquals(1, stats.activeDaysThisMonth)
    }
}
