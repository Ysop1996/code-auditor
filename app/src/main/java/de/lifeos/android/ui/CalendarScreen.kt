package de.lifeos.android.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer
import net.sqlcipher.database.SQLiteDatabase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

// MMSI V3.8 Calendar Module
// Deterministic calendar with tasks, appointments, and event triggers

data class CalendarEvent(
    val eventId: String,
    val title: String,
    val description: String?,
    val eventType: String,
    val startEpoch: Long,
    val endEpoch: Long?,
    val location: String?,
    val isAllDay: Boolean,
    val isRecurring: Boolean,
    val recurrenceRule: String?,
    val priority: Int,
    val status: String
)

data class CalendarTask(
    val taskId: String,
    val eventId: String?,
    val title: String,
    val description: String?,
    val taskType: String,
    val dueEpoch: Long?,
    val isCompleted: Boolean,
    val completedEpoch: Long?,
    val priority: Int,
    val triggerType: String,
    val triggerPayload: String?
)

@Composable
fun CalendarScreen(
    vaultDb: SQLiteDatabase?,
    morphState: AdaptiveWorkshopSynthesizer.UiMorphState = AdaptiveWorkshopSynthesizer.UiMorphState.Default
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<CalendarTask>>(emptyList()) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    // Load data when month or date changes
    LaunchedEffect(currentMonth, selectedDate) {
        vaultDb?.let { db ->
            val startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
            val endOfMonth = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
            
            events = loadEvents(db, startOfMonth, endOfMonth)
            tasks = loadPendingTasks(db)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Vorheriger Monat", tint = AccentCyan)
            }
            
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Nächster Monat", tint = AccentCyan)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            val weekdays = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
            weekdays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar Grid
        CalendarGrid(
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            events = events,
            onDateSelected = { selectedDate = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAddEventDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("TERMIN", color = AccentCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            
            Button(
                onClick = { showAddTaskDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AUFGABE", color = AccentGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Date Details
        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy", Locale.GERMAN)),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Events for selected date
        val selectedDateEvents = events.filter { event ->
            val eventDate = LocalDate.ofEpochDay(event.startEpoch / 86400000)
            eventDate == selectedDate
        }

        if (selectedDateEvents.isNotEmpty()) {
            Text(
                text = "TERMINE",
                color = AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            selectedDateEvents.forEach { event ->
                EventCard(event = event, morphState = morphState)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Tasks for selected date
        val selectedDateTasks = tasks.filter { task ->
            task.dueEpoch?.let { 
                LocalDate.ofEpochDay(it / 86400000) == selectedDate 
            } ?: false
        }

        if (selectedDateTasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AUFGABEN",
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            selectedDateTasks.forEach { task ->
                TaskCard(task = task, vaultDb = vaultDb, morphState = morphState)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (selectedDateEvents.isEmpty() && selectedDateTasks.isEmpty()) {
            EmptyStateCard(
                message = "Keine Termine oder Aufgaben für diesen Tag.",
                icon = Icons.Filled.Event,
                morphState = morphState
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Add Event Dialog
    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, description, startEpoch, endEpoch, location, eventType, priority ->
                vaultDb?.let { db ->
                    val eventId = "evt_${System.currentTimeMillis()}"
                    de.lifeos.android.security.BlackboxVaultManager.insertCalendarEvent(
                        eventId = eventId,
                        title = title,
                        description = description,
                        eventType = eventType,
                        startEpoch = startEpoch,
                        endEpoch = endEpoch,
                        location = location,
                        isAllDay = false,
                        isRecurring = false,
                        recurrenceRule = null,
                        priority = priority,
                        status = "PENDING"
                    )
                    // Refresh events
                    val startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
                    val endOfMonth = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
                    events = loadEvents(db, startOfMonth, endOfMonth)
                }
                showAddEventDialog = false
            }
        )
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, description, dueEpoch, taskType, priority, triggerType ->
                vaultDb?.let { db ->
                    val taskId = "tsk_${System.currentTimeMillis()}"
                    de.lifeos.android.security.BlackboxVaultManager.insertCalendarTask(
                        taskId = taskId,
                        eventId = null,
                        title = title,
                        description = description,
                        taskType = taskType,
                        dueEpoch = dueEpoch,
                        isCompleted = false,
                        completedEpoch = null,
                        priority = priority,
                        triggerType = triggerType,
                        triggerPayload = null
                    )
                    tasks = loadPendingTasks(db)
                }
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val lastDayOfMonth = currentMonth.atEndOfMonth()
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Adjust for Monday start
    
    val daysInMonth = lastDayOfMonth.dayOfMonth
    val days = mutableListOf<Int?>()
    
    // Add empty cells for days before the first day of month
    repeat(firstDayOfWeek) {
        days.add(null)
    }
    
    // Add days of the month
    repeat(daysInMonth) {
        days.add(it + 1)
    }

    Column {
        val rows = days.chunked(7)
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val isSelected = day != null && selectedDate == currentMonth.atDay(day)
                    val hasEvents = day != null && events.any { event ->
                        val eventDate = LocalDate.ofEpochDay(event.startEpoch / 86400000)
                        eventDate == currentMonth.atDay(day)
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> AccentCyan.copy(alpha = 0.3f)
                                    hasEvents -> AccentYellow.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable(enabled = day != null) {
                                day?.let { onDateSelected(currentMonth.atDay(it)) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.toString(),
                                    color = if (isSelected) AccentCyan else TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (hasEvents) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(AccentYellow)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(
    event: CalendarEvent,
    morphState: AdaptiveWorkshopSynthesizer.UiMorphState
) {
    val eventDate = LocalDate.ofEpochDay(event.startEpoch / 86400000)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when (event.eventType) {
                            "APPOINTMENT" -> AccentCyan
                            "DEADLINE" -> AccentRed
                            "MEETING" -> AccentPurple
                            "REMINDER" -> AccentYellow
                            else -> AccentGreen
                        }
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                if (!event.description.isNullOrEmpty()) {
                    Text(
                        text = event.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row {
                    Text(
                        text = eventDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!event.isAllDay) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeFormatter.format(java.time.LocalDateTime.ofEpochSecond(event.startEpoch / 1000, 0, ZoneId.systemDefault().rules.getOffset(java.time.Instant.ofEpochMilli(event.startEpoch)))),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (!event.location.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📍 ${event.location}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (event.priority > 0) {
                Box(
                    modifier = Modifier
                        .background(AccentRed.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "!${event.priority}",
                        color = AccentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: CalendarTask,
    vaultDb: SQLiteDatabase?,
    morphState: AdaptiveWorkshopSynthesizer.UiMorphState
) {
    var isCompleted by remember { mutableStateOf(task.isCompleted) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { checked ->
                    isCompleted = checked
                    vaultDb?.let { db ->
                        de.lifeos.android.security.BlackboxVaultManager.completeTask(task.taskId)
                    }
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentGreen,
                    uncheckedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = if (isCompleted) TextSecondary else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        text = task.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row {
                    task.dueEpoch?.let { due ->
                        val dueDate = LocalDate.ofEpochDay(due / 86400000)
                        Text(
                            text = "Fällig: ${dueDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))}",
                            color = if (isCompleted) TextSecondary else AccentYellow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (task.triggerType) {
                            "MANUAL" -> "Manuell"
                            "AUTOMATED" -> "Automatisch"
                            "FIELD_TRIGGER" -> "Feld-Trigger"
                            else -> task.triggerType
                        },
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (task.priority > 0) {
                Box(
                    modifier = Modifier
                        .background(AccentRed.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "!${task.priority}",
                        color = AccentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Long, Long?, String?, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var location by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("APPOINTMENT") }
    var priority by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("NEUER TERMIN", color = AccentCyan, fontFamily = FontFamily.Monospace)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ort", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var startDateStr by remember { mutableStateOf(startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
                    var startTimeStr by remember { mutableStateOf("09:00") }
                    
                    OutlinedTextField(
                        value = startDateStr,
                        onValueChange = { startDateStr = it },
                        label = { Text("Datum", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = startTimeStr,
                        onValueChange = { startTimeStr = it },
                        label = { Text("Zeit", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startDateTime = startDate.atTime(9, 0)
                    val startEpoch = startDateTime.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
                    val endEpoch = startDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
                    onConfirm(title, description.takeIf { it.isNotEmpty() }, startEpoch, endEpoch, location.takeIf { it.isNotEmpty() }, eventType, priority)
                }
            ) {
                Text("ERSTELLEN", color = AccentCyan, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ABBRECHEN", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Long?, String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now()) }
    var taskType by remember { mutableStateOf("ACTION") }
    var priority by remember { mutableStateOf(0) }
    var triggerType by remember { mutableStateOf("MANUAL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("NEUE AUFGABE", color = AccentGreen, fontFamily = FontFamily.Monospace)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                var dueDateStr by remember { mutableStateOf(dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
                OutlinedTextField(
                    value = dueDateStr,
                    onValueChange = { dueDateStr = it },
                    label = { Text("Fälligkeitsdatum", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dueEpoch = dueDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
                    onConfirm(title, description.takeIf { it.isNotEmpty() }, dueEpoch, taskType, priority, triggerType)
                }
            ) {
                Text("ERSTELLEN", color = AccentGreen, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ABBRECHEN", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

// Data loading helpers
private fun loadEvents(db: SQLiteDatabase, startEpoch: Long, endEpoch: Long): List<CalendarEvent> {
    val cursor = db.rawQuery(
        """SELECT event_id, title, description, event_type, start_epoch, end_epoch, 
                  location, is_all_day, is_recurring, recurrence_rule, priority, status 
           FROM calendar_events 
           WHERE start_epoch >= ? AND start_epoch <= ? 
           ORDER BY start_epoch ASC""",
        arrayOf(startEpoch.toString(), endEpoch.toString())
    )
    val results = mutableListOf<CalendarEvent>()
    cursor.use {
        while (it.moveToNext()) {
            results.add(
                CalendarEvent(
                    eventId = it.getString(0),
                    title = it.getString(1),
                    description = it.getString(2),
                    eventType = it.getString(3),
                    startEpoch = it.getLong(4),
                    endEpoch = it.getLong(5),
                    location = it.getString(6),
                    isAllDay = it.getInt(7) == 1,
                    isRecurring = it.getInt(8) == 1,
                    recurrenceRule = it.getString(9),
                    priority = it.getInt(10),
                    status = it.getString(11)
                )
            )
        }
    }
    return results
}

private fun loadPendingTasks(db: SQLiteDatabase): List<CalendarTask> {
    val cursor = db.rawQuery(
        """SELECT task_id, event_id, title, description, task_type, due_epoch, 
                  is_completed, completed_epoch, priority, trigger_type, trigger_payload 
           FROM calendar_tasks 
           WHERE is_completed = 0 
           ORDER BY priority DESC, due_epoch ASC 
           LIMIT 100""",
        null
    )
    val results = mutableListOf<CalendarTask>()
    cursor.use {
        while (it.moveToNext()) {
            results.add(
                CalendarTask(
                    taskId = it.getString(0),
                    eventId = it.getString(1),
                    title = it.getString(2),
                    description = it.getString(3),
                    taskType = it.getString(4),
                    dueEpoch = it.getLong(5),
                    isCompleted = it.getInt(6) == 1,
                    completedEpoch = it.getLong(7),
                    priority = it.getInt(8),
                    triggerType = it.getString(9),
                    triggerPayload = it.getString(10)
                )
            )
        }
    }
    return results
}
