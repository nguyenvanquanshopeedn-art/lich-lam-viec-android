package com.lichlamviec.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class GoalItem(
    var text: String = "",
    var done: Boolean = false,
    var priority: String = "Trung bình",
    var progress: Float = 0f
)

data class TimeSlotItem(
    val id: Long = System.nanoTime(),
    val hour: Int = 8,
    val minute: Int = 0,
    var label: String = "08:00 - 09:00",
    var task: String = "",
    var note: String = "",
    var done: Boolean = false,
    var alarmEnabled: Boolean = false
)

data class WeekPlanItem(
    var focus: String = "",
    var note: String = "",
    var progress: Float = 0f
)

data class DayPlan(
    val goals: MutableList<GoalItem> = mutableListOf(
        GoalItem(),
        GoalItem(),
        GoalItem()
    ),
    val slots: MutableList<TimeSlotItem> = mutableListOf(),
    var summary: String = "",
    var leadMinutes: Int = 0,
    val tomorrowTasks: MutableList<String> = mutableListOf("", "", "")
)

data class MonthPlan(
    val monthGoals: MutableList<GoalItem> = mutableListOf(
        GoalItem(priority = "Cao"),
        GoalItem(priority = "Trung bình"),
        GoalItem(priority = "Trung bình")
    ),
    val weeks: MutableList<WeekPlanItem> = MutableList(4) { WeekPlanItem() },
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
            gson.fromJson(json, DayPlan::class.java) ?: DayPlan()
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
            gson.fromJson(json, MonthPlan::class.java) ?: MonthPlan()
        } catch (_: Exception) {
            MonthPlan()
        }
    }

    fun saveMonth(monthKey: String, plan: MonthPlan) {
        prefs.edit().putString("month_$monthKey", gson.toJson(plan)).apply()
    }
}

object AppThemeColors {
    val HeaderDark = Color(0xFF0F172A)
    val HeaderSurface = Color(0xFF1E293B)
    val HeaderBorder = Color(0xFF334155)
    val PageBg = Color(0xFFF1F5F9)
    val CardBg = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE2E8F0)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)

    val BluePrimary = Color(0xFF2563EB)
    val BlueSoftBg = Color(0xFFEFF6FF)
    val BlueSoftBorder = Color(0xFFBFDBFE)

    val GreenPrimary = Color(0xFF059669)
    val GreenSoftBg = Color(0xFFECFDF5)
    val GreenSoftBorder = Color(0xFFA7F3D0)

    val AmberPrimary = Color(0xFFD97706)
    val AmberButton = Color(0xFFF59E0B)
    val AmberSoftBg = Color(0xFFFFFBEB)
    val AmberSoftBorder = Color(0xFFFDE68A)

    val PurplePrimary = Color(0xFF7C3AED)
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
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AppThemeColors.BluePrimary,
                    background = AppThemeColors.PageBg,
                    surface = AppThemeColors.CardBg,
                    onSurface = AppThemeColors.TextPrimary,
                    outline = AppThemeColors.CardBorder
                )
            ) {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val repo = remember { StorageRepo(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var ringtoneSettings by remember { mutableStateOf(RingtonePrefs.load(context)) }
    var showRingtoneDialog by remember { mutableStateOf(false) }

    val systemRingtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Nhạc chuông hệ thống"
                ringtoneSettings = ringtoneSettings.copy(ringtoneUri = uri.toString(), ringtoneTitle = title)
                RingtonePrefs.save(context, ringtoneSettings)
                AlarmSoundPlayer.playPreview(context, ringtoneSettings.ringtoneUri, ringtoneSettings.volumePercent)
            }
        }
    }

    val customAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "File nhạc chuông tùy chọn"
            ringtoneSettings = ringtoneSettings.copy(ringtoneUri = uri.toString(), ringtoneTitle = fileName)
            RingtonePrefs.save(context, ringtoneSettings)
            AlarmSoundPlayer.playPreview(context, ringtoneSettings.ringtoneUri, ringtoneSettings.volumePercent)
        }
    }

    var liveClock by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
    }
    LaunchedEffect(Unit) {
        while (true) {
            liveClock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            delay(1000L)
        }
    }

    val dateKey = currentDate.toString()
    val monthKey = currentMonth.toString()

    val dayPlan = remember(dateKey, refreshTrigger) { repo.loadDay(dateKey) }
    val monthPlan = remember(monthKey, refreshTrigger) { repo.loadMonth(monthKey) }

    if (showRingtoneDialog) {
        AlertDialog(
            onDismissRequest = {
                AlarmSoundPlayer.stop()
                showRingtoneDialog = false
            },
            title = {
                Text("Settings nhạc chuông", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nhạc chuông hiện tại:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                    Text(
                        ringtoneSettings.ringtoneTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppThemeColors.BluePrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (ringtoneSettings.ringtoneUri.isNotBlank()) {
                                        putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            Uri.parse(ringtoneSettings.ringtoneUri)
                                        )
                                    }
                                }
                                systemRingtonePicker.launch(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text("Chọn chuông máy", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { customAudioPicker.launch("audio/*") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text("Chọn file âm thanh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Âm lượng chuông:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                        Text("${ringtoneSettings.volumePercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ringtoneSettings.volumePercent / 100f,
                        onValueChange = { frac ->
                            val newVol = (frac * 100).toInt().coerceIn(0, 100)
                            ringtoneSettings = ringtoneSettings.copy(volumePercent = newVol)
                            RingtonePrefs.save(context, ringtoneSettings)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rung khi báo thức", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = ringtoneSettings.vibrateEnabled,
                            onCheckedChange = { checked ->
                                ringtoneSettings = ringtoneSettings.copy(vibrateEnabled = checked)
                                RingtonePrefs.save(context, ringtoneSettings)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lặp lại chuông khi báo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = ringtoneSettings.loopAlarm,
                            onCheckedChange = { checked ->
                                ringtoneSettings = ringtoneSettings.copy(loopAlarm = checked)
                                RingtonePrefs.save(context, ringtoneSettings)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                AlarmSoundPlayer.playPreview(
                                    context,
                                    ringtoneSettings.ringtoneUri,
                                    ringtoneSettings.volumePercent
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nghe thử", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { AlarmSoundPlayer.stop() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dừng tiếng", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlarmSoundPlayer.stop()
                        RingtonePrefs.save(context, ringtoneSettings)
                        showRingtoneDialog = false
                        Toast.makeText(context, "Đã lưu Settings nhạc chuông", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary)
                ) {
                    Text("Lưu")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppThemeColors.HeaderDark)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "LỊCH LÀM VIỆC HÀNG NGÀY",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Kế hoạch thời gian & mục tiêu trong ngày",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .background(AppThemeColors.HeaderSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, AppThemeColors.HeaderBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            liveClock,
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Báo thức tự động",
                                color = Color(0xFF6EE7B7),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppThemeColors.HeaderSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, AppThemeColors.HeaderBorder, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) AppThemeColors.BluePrimary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Lịch Ngày",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.25f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) AppThemeColors.BluePrimary else Color.Transparent)
                            .clickable {
                                currentMonth = YearMonth.from(currentDate)
                                selectedTab = 1
                            }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Kế hoạch & Tổng kết Tháng",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            AlarmSoundPlayer.playPreview(
                                context,
                                ringtoneSettings.ringtoneUri,
                                ringtoneSettings.volumePercent
                            )
                            Toast.makeText(context, "Đang phát thử: ${ringtoneSettings.ringtoneTitle}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.AmberButton),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            "Test Chuông",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { showRingtoneDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AppThemeColors.HeaderBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AppThemeColors.HeaderSurface,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Settings nhạc chuông",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            repo.saveDay(dateKey, dayPlan)
                            repo.saveMonth(monthKey, monthPlan)
                            Toast.makeText(context, "Đã lưu dữ liệu vào bộ nhớ thiết bị", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AppThemeColors.HeaderBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AppThemeColors.HeaderSurface,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Sao lưu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(AppThemeColors.PageBg)
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
                        dayPlan.tomorrowTasks.filter { it.isNotBlank() }.forEach { taskText ->
                            val emptyGoal = nextPlan.goals.firstOrNull { it.text.isBlank() }
                            if (emptyGoal != null) {
                                emptyGoal.text = taskText
                            } else {
                                nextPlan.goals.add(GoalItem(text = taskText))
                            }
                        }
                        repo.saveDay(nextDate.toString(), nextPlan)
                        Toast.makeText(
                            context,
                            "Đã chuyển việc sang mục tiêu ngày ${nextDate.format(DateTimeFormatter.ofPattern("dd/MM"))}",
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
fun SectionHeaderDark(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppThemeColors.HeaderSurface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
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
    var mutationCount by remember { mutableIntStateOf(0) }

        var showAddSlotDialog by remember { mutableStateOf(false) }
    var startHourText by remember { mutableStateOf("8") }
    var startMinText by remember { mutableStateOf("00") }
    var endHourText by remember { mutableStateOf("9") }
    var endMinText by remember { mutableStateOf("00") }
    var newCustomLabel by remember { mutableStateOf("") }
    var newSlotTask by remember { mutableStateOf("") }

    var leadDropdownExpanded by remember { mutableStateOf(false) }
    val leadOptions = listOf(0 to "Đúng giờ (0p)", 5 to "Trước 5 phút", 10 to "Trước 10 phút", 15 to "Trước 15 phút")

    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("vi", "VN"))
        .replaceFirstChar { it.uppercase() }
    val formattedDate = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    val nonEmptyGoals = plan.goals.filter { it.text.isNotBlank() }
    val doneGoalsCount = nonEmptyGoals.count { it.done }
    val totalGoalCount = nonEmptyGoals.size
    val goalPercent = if (totalGoalCount > 0) (doneGoalsCount * 100) / totalGoalCount else 0
    val activeAlarmsCount = plan.slots.count { it.alarmEnabled }

    if (showAddSlotDialog) {
        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            title = { Text("Thêm khung giờ / nhiệm vụ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Giờ bắt đầu (00-23) & phút:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newStartHour.toString(),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 23)?.let { v -> newStartHour = v } },
                            label = { Text("Giờ đầu") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newStartMin.toString(),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 59)?.let { v -> newStartMin = v } },
                            label = { Text("Phút đầu") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("Giờ kết thúc (00-23) & phút:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newEndHour.toString(),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 23)?.let { v -> newEndHour = v } },
                            label = { Text("Giờ cuối") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newEndMin.toString(),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 59)?.let { v -> newEndMin = v } },
                            label = { Text("Phút cuối") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = newCustomLabel,
                        onValueChange = { newCustomLabel = it },
                        label = { Text("Nhãn thời gian tùy chọn (tùy chọn)") },
                        placeholder = { Text("VD: 08:30 - 09:45") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newSlotTask,
                        onValueChange = { newSlotTask = it },
                        label = { Text("Nội dung nhiệm vụ / công việc") },
                        placeholder = { Text("Nhập nhiệm vụ...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val computedLabel = if (newCustomLabel.isNotBlank()) {
                            newCustomLabel.trim()
                        } else {
                            String.format(
                                "%02d:%02d - %02d:%02d",
                                newStartHour,
                                newStartMin,
                                newEndHour,
                                newEndMin
                            )
                        }
                        plan.slots.add(
                            TimeSlotItem(
                                hour = newStartHour,
                                minute = newStartMin,
                                label = computedLabel,
                                task = newSlotTask.trim()
                            )
                        )
                        mutationCount++
                        onSave()
                        newCustomLabel = ""
                        newSlotTask = ""
                        showAddSlotDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary)
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onDateChange(date.minusDays(1)) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text("Hôm trước", fontSize = 11.sp, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> onDateChange(LocalDate.of(y, m + 1, d)) },
                                    date.year,
                                    date.monthValue - 1,
                                    date.dayOfMonth
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.35f).height(34.dp)
                        ) {
                            Text(formattedDate, fontSize = 11.sp, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = AppThemeColors.TextSecondary, modifier = Modifier.size(15.dp))
                        }

                        Button(
                            onClick = { onDateChange(LocalDate.now()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.95f).height(34.dp)
                        ) {
                            Text("Hôm nay", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { onDateChange(date.plusDays(1)) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text("Ngày mai", fontSize = 11.sp, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppThemeColors.PageBg, RoundedCornerShape(10.dp))
                            .border(1.dp, AppThemeColors.CardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Thứ:", color = AppThemeColors.TextSecondary, fontSize = 12.sp)
                            Text(dayOfWeek, color = AppThemeColors.BluePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ngày:", color = AppThemeColors.TextSecondary, fontSize = 12.sp)
                            Text(formattedDate, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Tiến độ hoàn thành trong ngày:",
                                fontSize = 12.sp,
                                color = AppThemeColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(AppThemeColors.AmberSoftBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, AppThemeColors.AmberSoftBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Chuông bật: $activeAlarmsCount khung giờ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeColors.AmberPrimary,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "$doneGoalsCount/$totalGoalCount việc ($goalPercent%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppThemeColors.TextPrimary,
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = if (totalGoalCount > 0) doneGoalsCount.toFloat() / totalGoalCount else 0f,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AppThemeColors.BluePrimary,
                                trackColor = AppThemeColors.CardBorder
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderDark(
                        title = "1. MỤC TIÊU CHÍNH TRONG NGÀY",
                        trailing = {
                            Button(
                                onClick = {
                                    plan.goals.add(GoalItem())
                                    mutationCount++
                                    onSave()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Thêm mục tiêu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppThemeColors.PageBg)
                            .border(BorderStroke(1.dp, AppThemeColors.CardBorder))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppThemeColors.TextSecondary, modifier = Modifier.width(26.dp))
                        Text("NỘI DUNG MỤC TIÊU QUAN TRỌNG", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppThemeColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("XONG", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                    }

                    plan.goals.forEachIndexed { index, goal ->
                        var text by remember(date, index, mutationCount) { mutableStateOf(goal.text) }
                        var done by remember(date, index, mutationCount) { mutableStateOf(goal.done) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(0.5.dp, AppThemeColors.CardBorder))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AppThemeColors.TextSecondary,
                                modifier = Modifier.width(26.dp)
                            )
                            TextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    goal.text = it
                                    onSave()
                                },
                                placeholder = {
                                    Text("Nhập mục tiêu số ${index + 1}...", fontSize = 13.sp, color = AppThemeColors.TextMuted, maxLines = 1)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.sp,
                                    color = if (done) AppThemeColors.TextSecondary else AppThemeColors.TextPrimary,
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
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
                            if (plan.goals.size > 1) {
                                IconButton(
                                    onClick = {
                                        plan.goals.removeAt(index)
                                        mutationCount++
                                        onSave()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = AppThemeColors.TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderDark(
                        title = "2. THỜI GIAN BIỂU & NHIỆM VỤ CHI TIẾT",
                        subtitle = "Tự thêm khung giờ và bật chuông theo lịch cá nhân",
                        trailing = {
                            Button(
                                onClick = { showAddSlotDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Thêm thời gian", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppThemeColors.HeaderSurface)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Báo trước:", color = Color(0xFFCBD5E1), fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Box {
                            val currentLeadText = leadOptions.firstOrNull { it.first == plan.leadMinutes }?.second ?: "Đúng giờ (0p)"
                            OutlinedButton(
                                onClick = { leadDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AppThemeColors.HeaderBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = AppThemeColors.HeaderDark,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(currentLeadText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                            DropdownMenu(
                                expanded = leadDropdownExpanded,
                                onDismissRequest = { leadDropdownExpanded = false }
                            ) {
                                leadOptions.forEach { (mins, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = {
                                            plan.leadMinutes = mins
                                            leadDropdownExpanded = false
                                            mutationCount++
                                            onSave()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppThemeColors.PageBg)
                            .border(BorderStroke(1.dp, AppThemeColors.CardBorder))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("KHUNG GIỜ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppThemeColors.TextSecondary, modifier = Modifier.width(105.dp), maxLines = 1)
                        Text("NHIỆM VỤ / CÔNG VIỆC", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppThemeColors.TextSecondary, modifier = Modifier.weight(1.2f), maxLines = 1)
                        Text("GHI CHÚ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AppThemeColors.TextSecondary, modifier = Modifier.weight(0.9f), maxLines = 1)
                    }

                    if (plan.slots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Chưa có khung giờ nào. Bấm \"+ Thêm thời gian\" để tạo nhiệm vụ theo giờ.",
                                color = AppThemeColors.TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        plan.slots.forEachIndexed { idx, slot ->
                            var task by remember(date, slot.id, mutationCount) { mutableStateOf(slot.task) }
                            var note by remember(date, slot.id, mutationCount) { mutableStateOf(slot.note) }
                            var done by remember(date, slot.id, mutationCount) { mutableStateOf(slot.done) }
                            var alarmOn by remember(date, slot.id, mutationCount) { mutableStateOf(slot.alarmEnabled) }

                            val rowBg = when {
                                done -> AppThemeColors.GreenSoftBg
                                alarmOn -> AppThemeColors.AmberSoftBg
                                else -> AppThemeColors.CardBg
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .border(BorderStroke(0.5.dp, AppThemeColors.CardBorder))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.width(105.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            slot.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = AppThemeColors.TextPrimary,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (alarmOn) AppThemeColors.AmberButton else AppThemeColors.CardBorder
                                                )
                                                .clickable {
                                                    alarmOn = !alarmOn
                                                    slot.alarmEnabled = alarmOn
                                                    mutationCount++
                                                    onSave()
                                                    val alarmId = (date.dayOfYear * 1000 + slot.hour * 10 + idx).coerceAtLeast(1)
                                                    if (alarmOn) {
                                                        val totalMins = (slot.hour * 60 + slot.minute - plan.leadMinutes).coerceAtLeast(0)
                                                        AlarmHelper.scheduleAlarm(
                                                            context,
                                                            alarmId,
                                                            totalMins / 60,
                                                            totalMins % 60,
                                                            task.ifBlank { slot.label },
                                                            slot.label
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "Bật chuông ${slot.label}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        AlarmHelper.cancelAlarm(context, alarmId)
                                                        Toast.makeText(
                                                            context,
                                                            "Tắt chuông ${slot.label}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                        )
                                    }

                                    TextField(
                                        value = task,
                                        onValueChange = {
                                            task = it
                                            slot.task = it
                                            onSave()
                                        },
                                        placeholder = { Text("Nội dung công việc...", fontSize = 12.sp, color = AppThemeColors.TextMuted, maxLines = 1) },
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 13.sp,
                                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )

                                    TextField(
                                        value = note,
                                        onValueChange = {
                                            note = it
                                            slot.note = it
                                            onSave()
                                        },
                                        placeholder = { Text("Ghi chú...", fontSize = 12.sp, color = AppThemeColors.TextMuted, maxLines = 1) },
                                        modifier = Modifier.weight(0.9f),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )

                                    Checkbox(
                                        checked = done,
                                        onCheckedChange = {
                                            done = it
                                            slot.done = it
                                            mutationCount++
                                            onSave()
                                        }
                                    )

                                    IconButton(
                                        onClick = {
                                            plan.slots.removeAt(idx)
                                            mutationCount++
                                            onSave()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppThemeColors.TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "TỔNG KẾT & ĐÁNH GIÁ CUỐI NGÀY",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = AppThemeColors.TextPrimary
                    )

                    var summaryText by remember(date, mutationCount) { mutableStateOf(plan.summary) }
                    OutlinedTextField(
                        value = summaryText,
                        onValueChange = {
                            summaryText = it
                            plan.summary = it
                            onSave()
                        },
                        shape = RoundedCornerShape(10.dp),
                        placeholder = {
                            Text(
                                "Viết tổng kết ngày hôm nay: Những việc đã hoàn thành tốt, bài học rút ra, điều cần cải thiện...",
                                fontSize = 12.sp,
                                color = AppThemeColors.TextMuted
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "VIỆC CẦN LÀM NGÀY MAI",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = AppThemeColors.TextPrimary
                        )
                        TextButton(
                            onClick = {
                                plan.tomorrowTasks.add("")
                                mutationCount++
                                onSave()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Thêm việc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onPushTomorrow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.GreenPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Chuyển sang mục tiêu ngày mai", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }

                    plan.tomorrowTasks.forEachIndexed { idx, item ->
                        var tText by remember(date, idx, mutationCount) { mutableStateOf(item) }
                        OutlinedTextField(
                            value = tText,
                            onValueChange = {
                                tText = it
                                plan.tomorrowTasks[idx] = it
                                onSave()
                            },
                            shape = RoundedCornerShape(8.dp),
                            leadingIcon = {
                                Text(
                                    "${idx + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    color = AppThemeColors.TextSecondary
                                )
                            },
                            placeholder = {
                                Text("Nhập việc quan trọng ${idx + 1} cho ngày mai...", fontSize = 13.sp, color = AppThemeColors.TextMuted, maxLines = 1)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Text(
                        "* Bấm \"Chuyển sang mục tiêu ngày mai\" để tự động điền các việc này vào mục tiêu ngày kế tiếp.",
                        fontSize = 11.sp,
                        color = AppThemeColors.TextSecondary
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lịch Làm Việc Hàng Ngày", fontSize = 11.sp, color = AppThemeColors.TextSecondary)
                Text("✓ Đã lưu tự động", fontSize = 11.sp, color = AppThemeColors.GreenPrimary, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    var mutationCount by remember { mutableIntStateOf(0) }
    val daysInMonth = yearMonth.lengthOfMonth()

    var daysWithData = 0
    var totalGoalsDone = 0
    var totalGoalsAll = 0
    var totalSlotsDone = 0

    for (d in 1..daysInMonth) {
        val dp = repo.loadDay(yearMonth.atDay(d).toString())
        val filledGoals = dp.goals.filter { it.text.isNotBlank() }
        val gDone = filledGoals.count { it.done }
        val sDone = dp.slots.count { it.done }
        if (filledGoals.isNotEmpty() || dp.slots.isNotEmpty() || dp.summary.isNotBlank()) {
            daysWithData++
        }
        totalGoalsAll += filledGoals.size
        totalGoalsDone += gDone
        totalSlotsDone += sDone
    }

    val goalRate = if (totalGoalsAll > 0) (totalGoalsDone * 100) / totalGoalsAll else 0
    val priorities = listOf("Cao", "Trung bình", "Thấp")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppThemeColors.HeaderSurface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "THỐNG KÊ HIỆU SUẤT & LỊCH TỔNG QUAN THÁNG",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .background(AppThemeColors.BluePrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Tháng ${yearMonth.monthValue} / ${yearMonth.year}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onMonthChange(yearMonth.minusMonths(1)) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("Tháng trước", fontSize = 11.sp, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onMonthChange(yearMonth.plusMonths(1)) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("Tháng sau", fontSize = 11.sp, color = AppThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Button(
                                onClick = { onMonthChange(YearMonth.now()) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("Tháng này", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(AppThemeColors.BlueSoftBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, AppThemeColors.BlueSoftBorder, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("NGÀY ĐÃ LẬP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.BluePrimary, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text("$daysWithData/$daysInMonth", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeColors.TextPrimary, maxLines = 1)
                                Text("ngày có lịch", fontSize = 10.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(AppThemeColors.GreenSoftBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, AppThemeColors.GreenSoftBorder, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("MỤC TIÊU (%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.GreenPrimary, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text("$goalRate%", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeColors.TextPrimary, maxLines = 1)
                                Text("$totalGoalsDone/$totalGoalsAll xong", fontSize = 10.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(AppThemeColors.AmberSoftBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, AppThemeColors.AmberSoftBorder, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("KHUNG GIỜ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.AmberPrimary, maxLines = 1)
                                Spacer(Modifier.height(3.dp))
                                Text("$totalSlotsDone", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AppThemeColors.TextPrimary, maxLines = 1)
                                Text("đã hoàn thành", fontSize = 10.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                            }
                        }

                        Text(
                            "Lưới lịch tháng (Chạm vào ngày bất kỳ để mở Lịch Ngày đó):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppThemeColors.TextPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trống", fontSize = 11.sp, color = AppThemeColors.TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(
                                    AppThemeColors.PageBg,
                                    Color(0xFFD1FAE5),
                                    Color(0xFF6EE7B7),
                                    AppThemeColors.GreenPrimary
                                ).forEach { c ->
                                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                                }
                            }
                            Text("Năng suất cao", fontSize = 11.sp, color = AppThemeColors.TextSecondary)
                        }

                        val dayHeaders = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            dayHeaders.forEach { h ->
                                Text(
                                    h,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (h == "CN") Color(0xFFDC2626) else AppThemeColors.TextSecondary
                                )
                            }
                        }

                        val firstDayOfWeekOffset = (yearMonth.atDay(1).dayOfWeek.value - 1) % 7
                        val totalCells = firstDayOfWeekOffset + daysInMonth
                        val calendarRows = (0 until totalCells).chunked(7)

                        calendarRows.forEach { weekCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                weekCells.forEach { cellIdx ->
                                    val dayNum = cellIdx - firstDayOfWeekOffset + 1
                                    if (dayNum < 1) {
                                        Spacer(modifier = Modifier.weight(1f).height(42.dp))
                                    } else {
                                        val d = yearMonth.atDay(dayNum)
                                        val dp = repo.loadDay(d.toString())
                                        val doneCount = dp.goals.count { it.done && it.text.isNotBlank() } + dp.slots.count { it.done }
                                        val cellBg = when {
                                            doneCount >= 4 -> AppThemeColors.GreenPrimary
                                            doneCount >= 2 -> Color(0xFF6EE7B7)
                                            doneCount == 1 -> Color(0xFFD1FAE5)
                                            else -> AppThemeColors.PageBg
                                        }
                                        val numColor = if (doneCount >= 4) Color.White else AppThemeColors.TextPrimary
                                        val subColor = if (doneCount >= 4) Color(0xFFECFDF5) else AppThemeColors.TextSecondary

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cellBg)
                                                .border(1.dp, AppThemeColors.CardBorder, RoundedCornerShape(8.dp))
                                                .clickable { onSelectDay(d) }
                                                .padding(vertical = 3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("$dayNum", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = numColor)
                                            Text(
                                                if (doneCount > 0) "$doneCount việc" else "—",
                                                fontSize = 9.sp,
                                                color = subColor,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                repeat(7 - weekCells.size) {
                                    Spacer(modifier = Modifier.weight(1f).height(42.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderDark(
                        title = "1. MỤC TIÊU TRỌNG TÂM TRONG THÁNG",
                        subtitle = "Thiết lập mức độ ưu tiên, theo dõi tiến độ % và đánh dấu hoàn thành",
                        trailing = {
                            Button(
                                onClick = {
                                    plan.monthGoals.add(GoalItem(priority = "Trung bình"))
                                    mutationCount++
                                    onSave()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.BluePrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("+ Thêm mục tiêu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    )

                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        plan.monthGoals.forEachIndexed { idx, g ->
                            var txt by remember(yearMonth, idx, mutationCount) { mutableStateOf(g.text) }
                            var dn by remember(yearMonth, idx, mutationCount) { mutableStateOf(g.done) }
                            var prio by remember(yearMonth, idx, mutationCount) { mutableStateOf(g.priority) }
                            var prog by remember(yearMonth, idx, mutationCount) { mutableFloatStateOf(g.progress) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppThemeColors.PageBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, AppThemeColors.CardBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(AppThemeColors.HeaderSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = txt,
                                        onValueChange = { txt = it; g.text = it; onSave() },
                                        shape = RoundedCornerShape(8.dp),
                                        placeholder = { Text("Nhập mục tiêu trọng tâm tháng...", fontSize = 13.sp, color = AppThemeColors.TextMuted, maxLines = 1) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (prio == "Cao") Color(0xFFFEE2E2) else AppThemeColors.AmberSoftBg
                                            )
                                            .clickable {
                                                val next = priorities[(priorities.indexOf(prio) + 1) % priorities.size]
                                                prio = next
                                                g.priority = next
                                                onSave()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            prio,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (prio == "Cao") Color(0xFFB91C1C) else AppThemeColors.AmberPrimary,
                                            maxLines = 1
                                        )
                                    }
                                    Checkbox(
                                        checked = dn,
                                        onCheckedChange = { dn = it; g.done = it; onSave() }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tiến độ:", fontSize = 12.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                                    Slider(
                                        value = prog,
                                        onValueChange = { prog = it; g.progress = it; onSave() },
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    Text("${(prog * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.BluePrimary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderDark(
                        title = "2. KẾ HOẠCH HÀNH ĐỘNG THEO 4 TUẦN",
                        subtitle = "Phân bổ trọng tâm công việc, ghi chú kết quả và kéo thanh % hoàn thành từng tuần"
                    )

                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val weekRanges = listOf("NGÀY 01 - 07", "NGÀY 08 - 14", "NGÀY 15 - 21", "NGÀY 22 - $daysInMonth")
                        for (w in 0..3) {
                            val wItem = plan.weeks[w]
                            var wFocus by remember(yearMonth, w, mutationCount) { mutableStateOf(wItem.focus) }
                            var wNote by remember(yearMonth, w, mutationCount) { mutableStateOf(wItem.note) }
                            var wProg by remember(yearMonth, w, mutationCount) { mutableFloatStateOf(wItem.progress) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppThemeColors.PageBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, AppThemeColors.CardBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "TUẦN ${w + 1} (${weekRanges[w]})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = AppThemeColors.TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${(wProg * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = AppThemeColors.BluePrimary,
                                        maxLines = 1
                                    )
                                }
                                Text("Trọng tâm công việc tuần ${w + 1}:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                                OutlinedTextField(
                                    value = wFocus,
                                    onValueChange = { wFocus = it; wItem.focus = it; onSave() },
                                    shape = RoundedCornerShape(8.dp),
                                    placeholder = { Text("Các đầu việc chính cần hoàn thành...", fontSize = 12.sp, color = AppThemeColors.TextMuted, maxLines = 1) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Text("Ghi chú kết quả tuần ${w + 1}:", fontSize = 12.sp, color = AppThemeColors.TextSecondary)
                                OutlinedTextField(
                                    value = wNote,
                                    onValueChange = { wNote = it; wItem.note = it; onSave() },
                                    shape = RoundedCornerShape(8.dp),
                                    placeholder = { Text("Kết quả đạt được / vướng mắc...", fontSize = 12.sp, color = AppThemeColors.TextMuted, maxLines = 1) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hoàn thành:", fontSize = 12.sp, color = AppThemeColors.TextSecondary, maxLines = 1)
                                    Slider(
                                        value = wProg,
                                        onValueChange = { wProg = it; wItem.progress = it; onSave() },
                                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppThemeColors.CardBg),
                border = BorderStroke(1.dp, AppThemeColors.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderDark(title = "3. TỔNG KẾT & ĐÁNH GIÁ CUỐI THÁNG")

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(AppThemeColors.GreenSoftBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.GreenPrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("THÀNH TỰU NỔI BẬT TRONG THÁNG", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        var ach by remember(yearMonth, mutationCount) { mutableStateOf(plan.achievements) }
                        OutlinedTextField(
                            value = ach,
                            onValueChange = { ach = it; plan.achievements = it; onSave() },
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Liệt kê những kết quả, cột mốc xuất sắc nhất bạn đã đạt được trong tháng...", fontSize = 12.sp, color = AppThemeColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(AppThemeColors.AmberSoftBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.AmberPrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("BÀI HỌC KINH NGHIỆM & ĐIỂM CẦN CẢI THIỆN", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        var les by remember(yearMonth, mutationCount) { mutableStateOf(plan.lessons) }
                        OutlinedTextField(
                            value = les,
                            onValueChange = { les = it; plan.lessons = it; onSave() },
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Những khó khăn gặp phải, nguyên nhân chưa đạt mục tiêu và giải pháp khắc phục...", fontSize = 12.sp, color = AppThemeColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth().height(90.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(AppThemeColors.BlueSoftBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("3", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.BluePrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("THÓI QUEN / KỶ LUẬT DUY TRÌ TỐT", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        var hab by remember(yearMonth, mutationCount) { mutableStateOf(plan.goodHabits) }
                        OutlinedTextField(
                            value = hab,
                            onValueChange = { hab = it; plan.goodHabits = it; onSave() },
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Ghi lại thói quen và nhịp sinh hoạt tích cực bạn đã duy trì...", fontSize = 12.sp, color = AppThemeColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth().height(85.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFF3E8FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("4", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.PurplePrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("ĐỊNH HƯỚNG & MỤC TIÊU CHO THÁNG SAU", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        var nxt by remember(yearMonth, mutationCount) { mutableStateOf(plan.nextMonthFocus) }
                        OutlinedTextField(
                            value = nxt,
                            onValueChange = { nxt = it; plan.nextMonthFocus = it; onSave() },
                            shape = RoundedCornerShape(10.dp),
                            placeholder = { Text("Định hướng và mục tiêu ưu tiên cho tháng tiếp theo...", fontSize = 12.sp, color = AppThemeColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth().height(85.dp)
                        )

                    Button(
                            onClick = {
                                val nextM = yearMonth.plusMonths(1)
                                val nextPlan = repo.loadMonth(nextM.toString())
                                if (nxt.isNotBlank()) {
                                    val emptyG = nextPlan.monthGoals.firstOrNull { it.text.isBlank() }
                                    if (emptyG != null) emptyG.text = nxt else nextPlan.monthGoals.add(GoalItem(text = nxt, priority = "Cao"))
                                }
                                repo.saveMonth(nextM.toString(), nextPlan)
                                Toast.makeText(context, "Đã đẩy định hướng sang Tháng ${nextM.monthValue}/${nextM.year}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppThemeColors.PurplePrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Đẩy sang tháng sau", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
