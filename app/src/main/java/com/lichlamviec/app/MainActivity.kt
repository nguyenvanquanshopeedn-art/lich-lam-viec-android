package com.lichlamviec.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class GoalItem(var text: String = "", var done: Boolean = false)

data class TimeSlotItem(
    val hour: Int,
    val label: String,
    var task: String = "",
    var note: String = "",
    var done: Boolean = false,
    var alarmEnabled: Boolean = false
)

data class DayPlan(
    val goals: MutableList<GoalItem> = MutableList(5) { GoalItem() },
    val slots: MutableList<TimeSlotItem> = (4..22).map { h ->
        TimeSlotItem(h, String.format("%02d:00 - %02d:00", h, h + 1))
    }.toMutableList(),
    var summary: String = "",
    var rating: Int = 0,
    val tomorrowTasks: MutableList<String> = MutableList(3) { "" }
)

data class MonthPlan(
    val monthGoals: MutableList<GoalItem> = MutableList(5) { GoalItem() },
    val weekFocus: MutableList<String> = MutableList(4) { "" },
    val weekProgress: MutableList<Float> = MutableList(4) { 0f },
    var achievements: String = "",
    var lessons: String = "",
    var goodHabits: String = "",
    var nextMonthFocus: String = ""
)

class StorageRepo(context: Context) {
    private val prefs = context.getSharedPreferences("lich_lam_viec_db", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadDay(dateKey: String): DayPlan {
        val json = prefs.getString("day_$dateKey", null) ?: return DayPlan()
        return try {
            gson.fromJson(json, DayPlan::class.java)
        } catch (_: Exception) {
            DayPlan()
        }
    }

    fun saveDay(dateKey: String, plan: DayPlan) {
        prefs.edit().putString("day_$dateKey", gson.toJson(plan)).apply()
    }

    fun loadMonth(monthKey: String): MonthPlan {
        val json = prefs.getString("month_$monthKey", null) ?: return MonthPlan()
        return try {
            gson.fromJson(json, MonthPlan::class.java)
        } catch (_: Exception) {
            MonthPlan()
        }
    }

    fun saveMonth(monthKey: String, plan: MonthPlan) {
        prefs.edit().putString("month_$monthKey", gson.toJson(plan)).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        setContent {
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val repo = remember { StorageRepo(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val dateKey = currentDate.toString()
    val monthKey = currentMonth.toString()

    val dayPlan = remember(dateKey, refreshTrigger) { repo.loadDay(dateKey) }
    val monthPlan = remember(monthKey, refreshTrigger) { repo.loadMonth(monthKey) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF1E293B)).padding(12.dp)) {
                Text(
                    "LỊCH LÀM VIỆC & KẾ HOẠCH",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    TabChip("Lịch Ngày", selectedTab == 0, Modifier.weight(1f)) { selectedTab = 0 }
                    TabChip("Kế Hoạch & Tổng Kết Tháng", selectedTab == 1, Modifier.weight(1f)) {
                        currentMonth = YearMonth.from(currentDate)
                        selectedTab = 1
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
        ) {
            if (selectedTab == 0) {
                DailyScheduleScreen(
                    date = currentDate,
                    plan = dayPlan,
                    onDateChange = { currentDate = it },
                    onSave = {
                        repo.saveDay(dateKey, dayPlan)
                        refreshTrigger++
                    },
                    onPushTomorrow = {
                        val nextDate = currentDate.plusDays(1)
                        val nextPlan = repo.loadDay(nextDate.toString())
                        var idx = 0
                        dayPlan.tomorrowTasks.filter { it.isNotBlank() }.forEach { taskText ->
                            while (idx < 5 && nextPlan.goals[idx].text.isNotBlank()) idx++
                            if (idx < 5) nextPlan.goals[idx].text = taskText
                        }
                        repo.saveDay(nextDate.toString(), nextPlan)
                        Toast.makeText(
                            context,
                            "Đã chuyển việc sang ngày ${nextDate.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } else {
                MonthlyPlanScreen(
                    yearMonth = currentMonth,
                    plan = monthPlan,
                    repo = repo,
                    onMonthChange = { currentMonth = it },
                    onSelectDay = { date ->
                        currentDate = date
                        selectedTab = 0
                    },
                    onSave = {
                        repo.saveMonth(monthKey, monthPlan)
                        refreshTrigger++
                    }
                )
            }
        }
    }
}

@Composable
fun TabChip(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) Color(0xFF2563EB) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
fun DailyScheduleScreen(
    date: LocalDate,
    plan: DayPlan,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onPushTomorrow: () -> Unit
) {
    val context = LocalContext.current
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("vi", "VN"))
    val formattedDate = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayOfWeek.replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                        Text("Ngày: $formattedDate", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "1. NĂM MỤC TIÊU CHÍNH TRONG NGÀY",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(8.dp))
                    plan.goals.forEachIndexed { index, goal ->
                        var text by remember(date, index) { mutableStateOf(goal.text) }
                        var done by remember(date, index) { mutableStateOf(goal.done) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp)
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    goal.text = it
                                    onSave()
                                },
                                placeholder = { Text("Mục tiêu quan trọng #${index + 1}", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                    fontSize = 14.sp
                                )
                            )
                            Checkbox(
                                checked = done,
                                onCheckedChange = {
                                    done = it
                                    goal.done = it
                                    onSave()
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "2. THỜI GIAN BIỂU CHI TIẾT (04:00 - 23:00)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Bấm biểu tượng chuông để bật/tắt báo thức đúng khung giờ",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))

                    plan.slots.forEachIndexed { idx, slot ->
                        var task by remember(date, idx) { mutableStateOf(slot.task) }
                        var note by remember(date, idx) { mutableStateOf(slot.note) }
                        var done by remember(date, idx) { mutableStateOf(slot.done) }
                        var alarmOn by remember(date, idx) { mutableStateOf(slot.alarmEnabled) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    1.dp,
                                    if (alarmOn) Color(0xFFF59E0B) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (done) Color(0xFFF0FDF4) else Color.White,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    slot.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        alarmOn = !alarmOn
                                        slot.alarmEnabled = alarmOn
                                        onSave()
                                        val alarmId = date.dayOfYear * 100 + slot.hour
                                        if (alarmOn) {
                                            AlarmHelper.scheduleAlarm(
                                                context,
                                                alarmId,
                                                slot.hour,
                                                0,
                                                task,
                                                slot.label
                                            )
                                            Toast.makeText(
                                                context,
                                                "Đã đặt báo thức lúc ${String.format("%02d:00", slot.hour)}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            AlarmHelper.cancelAlarm(context, alarmId)
                                            Toast.makeText(
                                                context,
                                                "Đã tắt báo thức ${slot.label}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (alarmOn) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                        contentDescription = "Báo thức",
                                        tint = if (alarmOn) Color(0xFFD97706) else Color.Gray
                                    )
                                }
                                Checkbox(
                                    checked = done,
                                    onCheckedChange = {
                                        done = it
                                        slot.done = it
                                        onSave()
                                    }
                                )
                            }
                            OutlinedTextField(
                                value = task,
                                onValueChange = {
                                    task = it
                                    slot.task = it
                                    onSave()
                                },
                                placeholder = { Text("Nội dung công việc / hoạt động...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = note,
                                onValueChange = {
                                    note = it
                                    slot.note = it
                                    onSave()
                                },
                                placeholder = { Text("Ghi chú / Kết quả...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "3. TỔNG KẾT CUỐI NGÀY & VIỆC CẦN LÀM NGÀY MAI",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(8.dp))

                    var summaryText by remember(date) { mutableStateOf(plan.summary) }
                    OutlinedTextField(
                        value = summaryText,
                        onValueChange = {
                            summaryText = it
                            plan.summary = it
                            onSave()
                        },
                        label = { Text("Tổng kết & Đánh giá cuối ngày") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Việc cần làm ngày mai:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    plan.tomorrowTasks.forEachIndexed { idx, item ->
                        var tText by remember(date, idx) { mutableStateOf(item) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text("${idx + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            OutlinedTextField(
                                value = tText,
                                onValueChange = {
                                    tText = it
                                    plan.tomorrowTasks[idx] = it
                                    onSave()
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onPushTomorrow,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Chuyển 3 việc này sang Mục tiêu ngày mai")
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyPlanScreen(
    yearMonth: YearMonth,
    plan: MonthPlan,
    repo: StorageRepo,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onSave: () -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    var totalGoalsDone = 0
    var totalSlotsDone = 0
    var activeDays = 0

    for (d in 1..daysInMonth) {
        val dp = repo.loadDay(yearMonth.atDay(d).toString())
        val gDone = dp.goals.count { it.done && it.text.isNotBlank() }
        val sDone = dp.slots.count { it.done && it.task.isNotBlank() }
        if (gDone > 0 || sDone > 0 || dp.goals.any { it.text.isNotBlank() } || dp.slots.any { it.task.isNotBlank() }) {
            activeDays++
        }
        totalGoalsDone += gDone
        totalSlotsDone += sDone
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onMonthChange(yearMonth.minusMonths(1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    }
                    Text(
                        "THÁNG ${yearMonth.monthValue} / ${yearMonth.year}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(onClick = { onMonthChange(yearMonth.plusMonths(1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatBox("Ngày hoạt động", "$activeDays/$daysInMonth")
                    StatBox("Mục tiêu xong", "$totalGoalsDone")
                    StatBox("Khung giờ xong", "$totalSlotsDone")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "BẢN ĐỒ NGÀY TRONG THÁNG (Chạm để mở lịch ngày)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val rows = (1..daysInMonth).chunked(7)
                    rows.forEach { weekDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            weekDays.forEach { dayNum ->
                                val d = yearMonth.atDay(dayNum)
                                val dp = repo.loadDay(d.toString())
                                val hasWork = dp.slots.any { it.done } || dp.goals.any { it.done }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 2.dp)
                                        .background(
                                            if (hasWork) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSelectDay(d) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$dayNum", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            repeat(7 - weekDays.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("1. MỤC TIÊU TRỌNG TÂM THÁNG", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    plan.monthGoals.forEachIndexed { idx, g ->
                        var txt by remember(yearMonth, idx) { mutableStateOf(g.text) }
                        var dn by remember(yearMonth, idx) { mutableStateOf(g.done) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = txt,
                                onValueChange = { txt = it; g.text = it; onSave() },
                                placeholder = { Text("Mục tiêu tháng #${idx + 1}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Checkbox(
                                checked = dn,
                                onCheckedChange = { dn = it; g.done = it; onSave() }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("2. LỘ TRÌNH & TIẾN ĐỘ 4 TUẦN", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    for (w in 0..3) {
                        var wText by remember(yearMonth, w) { mutableStateOf(plan.weekFocus[w]) }
                        var wProg by remember(yearMonth, w) { mutableFloatStateOf(plan.weekProgress[w]) }
                        Text(
                            "Tuần ${w + 1} (${(wProg * 100).toInt()}%)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = wText,
                            onValueChange = { wText = it; plan.weekFocus[w] = it; onSave() },
                            placeholder = { Text("Trọng tâm tuần ${w + 1}...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Slider(
                            value = wProg,
                            onValueChange = { wProg = it; plan.weekProgress[w] = it; onSave() }
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("3. TỔNG KẾT CUỐI THÁNG", fontWeight = FontWeight.Bold)

                    var ach by remember(yearMonth) { mutableStateOf(plan.achievements) }
                    OutlinedTextField(
                        value = ach,
                        onValueChange = { ach = it; plan.achievements = it; onSave() },
                        label = { Text("Thành tựu nổi bật đạt được") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                    )

                    var les by remember(yearMonth) { mutableStateOf(plan.lessons) }
                    OutlinedTextField(
                        value = les,
                        onValueChange = { les = it; plan.lessons = it; onSave() },
                        label = { Text("Khó khăn & Bài học kinh nghiệm") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                    )

                    var hab by remember(yearMonth) { mutableStateOf(plan.goodHabits) }
                    OutlinedTextField(
                        value = hab,
                        onValueChange = { hab = it; plan.goodHabits = it; onSave() },
                        label = { Text("Thói quen duy trì tốt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                    )

                    var nxt by remember(yearMonth) { mutableStateOf(plan.nextMonthFocus) }
                    OutlinedTextField(
                        value = nxt,
                        onValueChange = { nxt = it; plan.nextMonthFocus = it; onSave() },
                        label = { Text("Định hướng & Cải thiện tháng tới") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
        )
        Text(label, color = Color(0xFFCBD5E1), fontSize = 12.sp)
    }
}
