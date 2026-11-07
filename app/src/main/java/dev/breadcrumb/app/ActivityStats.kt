package dev.breadcrumb.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val ActivityWindowDays = 30L

class ActivityStats(timestamps: List<Long>, val today: LocalDate, zone: ZoneId) {
    val counts: Map<LocalDate, Int> =
        timestamps.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount()

    val currentStreak: Int
        get() {
            var date = if (counts.containsKey(today)) today else today.minusDays(1)
            var streak = 0
            while (counts.containsKey(date)) {
                streak++
                date = date.minusDays(1)
            }
            return streak
        }

    val bestStreak: Int
        get() {
            var best = 0
            var streak = 0
            var previous: LocalDate? = null
            counts.keys.sorted().forEach { date ->
                streak = if (previous?.plusDays(1) == date) streak + 1 else 1
                best = maxOf(best, streak)
                previous = date
            }
            return best
        }

    val activeDaysThisMonth
        get() =
            counts.keys.count {
                !it.isBefore(today.minusDays(ActivityWindowDays - 1)) && !it.isAfter(today)
            }

    fun dates(days: Int): List<LocalDate> = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
}
