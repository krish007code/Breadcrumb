package dev.breadcrumb.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Orchid = Color(0xFFBE54E3)
private val Sky = Color(0xFF3FA2F2)
private val Meadow = Color(0xFF4CC46A)

@Composable
fun ProgressScreen(entries: List<NotebookEntry>, open: (NotebookEntry) -> Unit) {
    var section by rememberSaveable { mutableStateOf("Overview") }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    val zone = ZoneId.systemDefault()
    val stats = remember(entries, today, zone) { ActivityStats(entries.map(NotebookEntry::createdAt), today, zone) }
    Header("Progress")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth().background(Sand, RoundedCornerShape(24.dp)).padding(4.dp)) {
            listOf("Overview", "Calendar").forEach { label ->
                Surface(onClick = { section = label }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp), color = if (section == label) Color.White else Color.Transparent) {
                    Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (section == label) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
        if (section == "Overview") {
            Overview(entries, stats)
        } else {
            OverallCalendar(entries, stats, zone, open)
        }
    }
}

@Composable
private fun Overview(entries: List<NotebookEntry>, stats: ActivityStats) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var menu by remember { mutableStateOf(false) }
    val dates = stats.dates(days)
    val inRange = entries.filter {
        val date = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        date in dates
    }
    Box {
        FilledTonalButton(onClick = { menu = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Sand)) {
            Text("Last $days days")
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(7, 30).forEach { count ->
                DropdownMenuItem(text = { Text("Last $count days") }, onClick = { days = count; menu = false })
            }
        }
    }
    Panel {
        Text("Solutions saved", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(inRange.size.toString(), style = MaterialTheme.typography.headlineLarge)
        val highest = dates.maxOf { stats.counts[it] ?: 0 }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(148.dp), horizontalArrangement = Arrangement.spacedBy(if (days == 7) 5.dp else 2.dp),
            verticalAlignment = Alignment.Bottom) {
            dates.forEach { day ->
                val count = stats.counts[day] ?: 0
                val weekend = day.dayOfWeek.value >= 6
                Box(Modifier.weight(1f).height(((count.toFloat() / highest) * 148).coerceAtLeast(4f).dp)
                    .clip(RoundedCornerShape(if (days == 7) 10.dp else 3.dp))
                    .background(if (count == 0) Sand else if (weekend) Meadow else Sky)
                    .semantics { contentDescription = "$day, $count solutions saved" })
            }
        }
        if (days == 7) Row(Modifier.fillMaxWidth()) {
            dates.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = Muted, fontSize = 11.sp)
                }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dates.first().format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodyMedium, color = Muted)
            Text("Today", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
    Text("Sites", style = MaterialTheme.typography.headlineMedium)
    Panel {
        val sites = listOf(ProblemSite.LEETCODE, ProblemSite.CODEFORCES, ProblemSite.GEEKSFORGEEKS) +
            ProblemSite.entries.filter { site ->
                site !in listOf(ProblemSite.LEETCODE, ProblemSite.CODEFORCES, ProblemSite.GEEKSFORGEEKS) && inRange.any { it.site == site }
            }
        sites.forEachIndexed { index, site ->
            val siteEntries = inRange.filter { it.site == site }
            val siteStats = ActivityStats(siteEntries.map(NotebookEntry::createdAt), stats.today, ZoneId.systemDefault())
            val color = when (site) {
                ProblemSite.LEETCODE -> Color(0xFFFF9F2E)
                ProblemSite.CODEFORCES -> Sky
                ProblemSite.GEEKSFORGEEKS -> Meadow
                else -> Orchid
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(site.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${siteEntries.size} saved", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (days == 7) 4.dp else 2.dp)) {
                    dates.forEach { day ->
                        Box(Modifier.size(if (days == 7) 10.dp else 5.dp).background(if (siteStats.counts.containsKey(day)) color else Sand, CircleShape)
                            .semantics { contentDescription = "${site.label}, $day, ${siteStats.counts[day] ?: 0} saved" })
                    }
                }
            }
            if (index < sites.lastIndex) HorizontalDivider(color = Border)
        }
    }
    Text("Activity reflects solutions you save in Breadcrumb.", style = MaterialTheme.typography.bodyMedium, color = Muted)
}

@Composable
private fun OverallCalendar(entries: List<NotebookEntry>, stats: ActivityStats, zone: ZoneId, open: (NotebookEntry) -> Unit) {
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.from(stats.today).toString()) }
    var selectedValue by rememberSaveable { mutableStateOf(stats.today.toString()) }
    val month = YearMonth.parse(monthValue)
    val selected = LocalDate.parse(selectedValue)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Current streak", "${stats.currentStreak} days", Icons.Outlined.LocalFireDepartment, Color(0xFFF25A2B), Modifier.weight(1f))
        StatTile("Best streak", "${stats.bestStreak} days", Icons.Outlined.EmojiEvents, Color(0xFFFF9F2E), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Active in 30 days", "${stats.activeDaysThisMonth} days", Icons.Outlined.CalendarMonth, Sky, Modifier.weight(1f))
        StatTile("Saved overall", entries.size.toString(), Icons.Outlined.EditNote, Meadow, Modifier.weight(1f))
    }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(month.year.toString(), color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { monthValue = month.minusMonths(1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
            IconButton(onClick = { monthValue = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(stats.today)) {
                Icon(Icons.Outlined.ChevronRight, "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(label, color = Muted, fontSize = 11.sp) }
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        (0 until cells step 7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { weekday ->
                    val number = week + weekday - offset + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (number in 1..month.lengthOfMonth()) {
                            val date = month.atDay(number)
                            val count = stats.counts[date] ?: 0
                            val marked = selected == date
                            Box(Modifier.size(34.dp).clip(CircleShape)
                                .background(if (count > 0) Orchid else Color.Transparent)
                                .then(if (marked) Modifier.border(1.5.dp, Ink, CircleShape) else Modifier)
                                .clickable { selectedValue = date.toString() }
                                .semantics { contentDescription = "$date, $count saved solutions${if (marked) ", selected" else ""}" },
                                contentAlignment = Alignment.Center) {
                                Text(number.toString(), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("Marked days include every site.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
    Text(selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
    val onDay = entries.filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == selected }
    if (onDay.isEmpty()) Text("No solutions saved on this day.", color = Muted)
    onDay.forEach { entry -> SolutionRow(entry) { open(entry) } }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(17.dp), tint = color)
                Text(label, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp)
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
