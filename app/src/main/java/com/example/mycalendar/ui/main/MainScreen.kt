package com.example.mycalendar.ui.main

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.schedule.CategoryDto
import com.example.mycalendar.ui.schedule.ScheduleCategoryDto
import com.example.mycalendar.ui.schedule.ScheduleDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.time.YearMonth
import com.example.mycalendar.ui.category.CategoryAddScreen
import com.example.mycalendar.ui.D_day.DdayScreen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.example.mycalendar.ui.category.CategoryEdit
import androidx.activity.compose.BackHandler
import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.List

object MainColors {
    val Background = Color(0xFF161924)
    val Card = Color(0xFF222736)
    val TextWhite = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFA0A4B8)
    val Primary = Color(0xFF665BFF)

    val CategoryColors = listOf(
        Color(0xFF10B981),
        Color(0xFFD97706),
        Color(0xFFE11D48),
        Color(0xFF8B5CF6),
        Color(0xFF0EA5E9),
        Color(0xFF18786D)
    )
}

@Composable
fun MainScreen(
    initialTab: String = "홈",
    onNavigateToAddSchedule: (String?) -> Unit,
    onNavigateToEditSchedule: (ScheduleDto, String) -> Unit,
    onNavigateToAddCategory: () -> Unit,
    onLogout: () -> Unit
) {
    val tabHistory = remember { mutableStateListOf(initialTab) }
    val currentTab = tabHistory.lastOrNull() ?: initialTab

    BackHandler(enabled = tabHistory.size > 1) {
        tabHistory.removeAt(tabHistory.lastIndex)
    }

    fun selectTab(tab: String) {
        if (currentTab != tab) {
            tabHistory.add(tab)
        }
    }

    Scaffold(
        containerColor = MainColors.Background,
        bottomBar = {
            BottomNavigationBar(
                selectedTab = currentTab,
                onTabSelected = { tab -> selectTab(tab) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                "홈" -> HomeCalendarContent(
                    onLogout = onLogout,
                    onNavigateToAddSchedule = onNavigateToAddSchedule,
                    onNavigateToEditSchedule = { schedule ->
                        onNavigateToEditSchedule(schedule, "홈")
                    },
                    onNavigateToCategoryEdit = { selectTab("카테고리") }
                )

                "카테고리" -> CategoryEdit(
                    onBack = {
                        if (tabHistory.size > 1) {
                            tabHistory.removeAt(tabHistory.lastIndex)
                        } else {
                            selectTab("홈")
                        }
                    }
                )

                "D-Day" -> DdayScreen(
                    onBack = {
                        if (tabHistory.size > 1) {
                            tabHistory.removeAt(tabHistory.lastIndex)
                        } else {
                            selectTab("홈")
                        }
                    },
                    onNavigateToEdit = { schedule ->
                        onNavigateToEditSchedule(schedule, "D-Day")
                    }
                )
            }
        }
    }
}

@Composable
fun HomeCalendarContent(
    onLogout: () -> Unit,
    onNavigateToAddSchedule: (String?) -> Unit,
    onNavigateToEditSchedule: (ScheduleDto) -> Unit,
    onNavigateToCategoryEdit: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var scheduleMappings by remember { mutableStateOf<List<ScheduleCategoryDto>>(emptyList()) }

    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Int?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var showTodayPopup by remember { mutableStateOf(false) }
    var hasShownTodayPopup by remember { mutableStateOf(false) }
    var todayUncompletedSchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }

    fun fetchAllData() {
        coroutineScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val fetchedCategories = SupabaseClient.client.postgrest["categories"]
                        .select { filter { eq("user_uuid", user.id) } }
                        .decodeList<CategoryDto>()
                        .filter { it.category_id != null && !it.title.isNullOrBlank() }

                    categories = fetchedCategories

                    val fetchedSchedules = SupabaseClient.client.postgrest["schedules"]
                        .select { filter { eq("user_uuid", user.id) } }
                        .decodeList<ScheduleDto>()

                    schedules = fetchedSchedules

                    val scheduleIds = fetchedSchedules.mapNotNull { it.schedule_id }
                    if (scheduleIds.isNotEmpty()) {
                        scheduleMappings = try {
                            SupabaseClient.client.postgrest["schedule_categories"]
                                .select { filter { isIn("schedule_id", scheduleIds) } }
                                .decodeList<ScheduleCategoryDto>()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        scheduleMappings = emptyList()
                    }

                    val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val prefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
                    val hideDate = prefs.getString("hide_today_popup_date", "")

                    val uncompletedToday = fetchedSchedules.filter {
                        it.schedule_date == todayStr && !it.is_completed
                    }

                    if (uncompletedToday.isNotEmpty() && !hasShownTodayPopup && hideDate != todayStr) {
                        todayUncompletedSchedules = uncompletedToday
                        showTodayPopup = true
                        hasShownTodayPopup = true
                    }

                } else {
                    categories = emptyList()
                    schedules = emptyList()
                    scheduleMappings = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAllData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchAllData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredSchedules = if (selectedCategoryIds.isEmpty()) {
        schedules
    } else {
        schedules.filter { schedule ->
            val targetScheduleId = schedule.schedule_id?.toString() ?: return@filter false

            val assignedCategoryIds = scheduleMappings
                .filter { it.schedule_id?.toString() == targetScheduleId }
                .mapNotNull { it.category_id?.toString() }

            assignedCategoryIds.any { selectedCategoryIds.contains(it) }
        }
    }

    val displayCategories = remember(categories) {
        categories
            .filter { it.category_id != null && !it.title.isNullOrBlank() }
            .distinctBy { it.category_id?.toString() }
    }

    val currentMonthPrefix = "${currentYearMonth.year}-${String.format("%02d", currentYearMonth.monthValue)}"
    val currentMonthSchedules = filteredSchedules.filter { it.schedule_date.startsWith(currentMonthPrefix) }

    val totalSchedulesCount = currentMonthSchedules.size
    val completedSchedulesCount = currentMonthSchedules.count { it.is_completed }
    val achievementRate = if (totalSchedulesCount > 0) completedSchedulesCount.toFloat() / totalSchedulesCount else 0f
    val achievementPercent = (achievementRate * 100).toInt()

    val selectedCategoryTitle = when {
        selectedCategoryIds.isEmpty() -> "전체"
        else -> {
            val names = displayCategories
                .filter { selectedCategoryIds.contains(it.category_id?.toString()) }
                .map { it.title }
            if (names.isEmpty()) "카테고리" else names.joinToString(", ")
        }
    }

    val activeAccentColor = if (selectedCategoryIds.size == 1) {
        val catIndex = displayCategories.indexOfFirst { it.category_id?.toString() == selectedCategoryIds.first() }
        if (catIndex != -1) MainColors.CategoryColors[catIndex % MainColors.CategoryColors.size] else MainColors.Primary
    } else {
        MainColors.Primary
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MainColors.TextWhite, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("마이 캘린더", style = MaterialTheme.typography.titleLarge, color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { showLogoutDialog = true }) {
                Text("로그아웃", color = MainColors.TextGray)
            }
        }

        CategoryFilterBarSection(
            displayCategories = displayCategories,
            selectedCategoryIds = selectedCategoryIds,
            onCategoryToggle = { categoryId: String? ->
                selectedCategoryIds = if (categoryId == null) {
                    emptySet()
                } else {
                    if (selectedCategoryIds.contains(categoryId)) {
                        selectedCategoryIds - categoryId
                    } else {
                        selectedCategoryIds + categoryId
                    }
                }
            },
            onNavigateToCategoryEdit = onNavigateToCategoryEdit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigateToCategoryEdit() },
            colors = CardDefaults.cardColors(containerColor = MainColors.Card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedCategoryTitle 달성률",
                        color = activeAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$completedSchedulesCount / ${totalSchedulesCount}개 완료 ",
                            color = MainColors.TextGray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "$achievementPercent%",
                            color = MainColors.TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { achievementRate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = activeAccentColor,
                    trackColor = activeAccentColor.copy(alpha = 0.2f),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        totalSchedulesCount == 0 -> "이번 달 등록된 일정이 없습니다."
                        achievementPercent == 100 -> "이번 달 모든 일정을 달성했습니다!"
                        else -> "목표를 향해 차근차근 달성해 보세요!"
                    },
                    color = activeAccentColor,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).weight(1f),
            colors = CardDefaults.cardColors(containerColor = MainColors.Card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = MainColors.TextWhite) }
                    Text("${currentYearMonth.year}년 ${currentYearMonth.monthValue}월", color = MainColors.TextWhite, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MainColors.TextWhite) }
                }

                Spacer(modifier = Modifier.height(16.dp))
                val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
                Row(modifier = Modifier.fillMaxWidth()) {
                    daysOfWeek.forEachIndexed { index, day ->
                        Text(text = day, color = if (index == 0) Color.Red else if (index == 6) Color(0xFF3B82F6) else MainColors.TextGray, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MainColors.Background, thickness = 1.dp)

                val firstDayOfWeek = currentYearMonth.atDay(1).dayOfWeek.value
                val emptyCells = if (firstDayOfWeek == 7) 0 else firstDayOfWeek
                val daysInMonth = currentYearMonth.lengthOfMonth()

                LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
                    items(emptyCells) { Spacer(modifier = Modifier.height(80.dp)) }
                    items(daysInMonth) { dayIndex ->
                        val day = dayIndex + 1
                        val dateString = "${currentYearMonth.year}-${String.format("%02d", currentYearMonth.monthValue)}-${String.format("%02d", day)}"
                        val dailySchedules = filteredSchedules.filter { it.schedule_date == dateString }

                        CalendarCell(
                            day = day,
                            schedules = dailySchedules,
                            onClick = { selectedDate = day; showDialog = true }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showTodayPopup) {
        TodayScheduleDialog(
            schedules = todayUncompletedSchedules,
            onDismiss = { showTodayPopup = false },
            onDoNotShowToday = {
                val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val prefs = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("hide_today_popup_date", todayStr).apply()
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MainColors.Card,
            title = { Text("로그아웃", color = MainColors.TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("정말 로그아웃 하시겠습니까?", color = MainColors.TextGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        selectedCategoryIds = emptySet()
                        categories = emptyList()
                        schedules = emptyList()
                        scheduleMappings = emptyList()
                        onLogout()
                    }
                ) { Text("로그아웃", color = Color(0xFFE11D48), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("취소", color = MainColors.TextWhite) }
            }
        )
    }

    if (showDialog && selectedDate != null) {
        val year = currentYearMonth.year
        val month = currentYearMonth.monthValue
        val day = selectedDate!!
        val dateString = "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
        val displayDateText = "${year}년 ${month}월 ${day}일"

        var dialogSelectedCategoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        val dateSchedules = schedules.filter { it.schedule_date == dateString }

        val dialogFilteredSchedules = if (dialogSelectedCategoryIds.isEmpty()) {
            dateSchedules
        } else {
            dateSchedules.filter { schedule ->
                val sId = schedule.schedule_id?.toString() ?: return@filter false
                val assignedCategoryIds = scheduleMappings
                    .filter { it.schedule_id?.toString() == sId }
                    .mapNotNull { it.category_id?.toString() }

                assignedCategoryIds.any { dialogSelectedCategoryIds.contains(it) }
            }
        }

        val dailyTotal = dateSchedules.size
        val dailyCompleted = dateSchedules.count { it.is_completed }
        val dailyRate = if (dailyTotal > 0) dailyCompleted.toFloat() / dailyTotal else 0f
        val dailyPercent = (dailyRate * 100).toInt()

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MainColors.Card)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayDateText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MainColors.TextWhite,
                            fontSize = 18.sp
                        )
                        IconButton(
                            onClick = { showDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "닫기", tint = MainColors.TextGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("달성률", color = MainColors.TextGray, fontSize = 13.sp)
                        Text("$dailyPercent%", color = MainColors.TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { dailyRate },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MainColors.Primary,
                        trackColor = MainColors.Primary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            DialogCategoryChip(
                                title = "전체",
                                isSelected = dialogSelectedCategoryIds.isEmpty(),
                                onClick = { dialogSelectedCategoryIds = emptySet() }
                            )
                        }
                        itemsIndexed(displayCategories) { index, category ->
                            val catId = category.category_id?.toString()
                            val isSelected = catId != null && dialogSelectedCategoryIds.contains(catId)
                            val chipColor = MainColors.CategoryColors[index % MainColors.CategoryColors.size]

                            DialogCategoryChip(
                                title = category.title,
                                isSelected = isSelected,
                                accentColor = chipColor,
                                onClick = {
                                    if (catId != null) {
                                        dialogSelectedCategoryIds = if (isSelected) {
                                            dialogSelectedCategoryIds - catId
                                        } else {
                                            dialogSelectedCategoryIds + catId
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (dialogFilteredSchedules.isEmpty()) {
                        Text(
                            text = "등록된 일정이 없습니다.",
                            color = MainColors.TextGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dialogFilteredSchedules.forEach { schedule ->
                                val assignedCatIds = scheduleMappings
                                    .filter { it.schedule_id?.toString() == schedule.schedule_id?.toString() }
                                    .mapNotNull { it.category_id?.toString() }

                                val assignedCategoryItems = displayCategories
                                    .filter { assignedCatIds.contains(it.category_id?.toString()) }
                                    .map { cat ->
                                        val index = displayCategories.indexOf(cat)
                                        val color = if (index >= 0) MainColors.CategoryColors[index % MainColors.CategoryColors.size] else MainColors.Primary
                                        cat.title to color
                                    }

                                DailyScheduleItem(
                                    schedule = schedule,
                                    categories = assignedCategoryItems,
                                    onToggleComplete = {
                                        val updatedCompleted = !schedule.is_completed
                                        coroutineScope.launch {
                                            try {
                                                schedule.schedule_id?.let { id ->
                                                    SupabaseClient.client.postgrest["schedules"].update(
                                                        mapOf("is_completed" to updatedCompleted)
                                                    ) {
                                                        filter { eq("schedule_id", id) }
                                                    }
                                                    schedules = schedules.map {
                                                        if (it.schedule_id == id) it.copy(is_completed = updatedCompleted) else it
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "상태 변경 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onEdit = {
                                        showDialog = false
                                        onNavigateToEditSchedule(schedule)
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            try {
                                                schedule.schedule_id?.let { id ->
                                                    SupabaseClient.client.postgrest["schedules"].delete {
                                                        filter { eq("schedule_id", id) }
                                                    }
                                                    schedules = schedules.filter { it.schedule_id != id }
                                                    Toast.makeText(context, "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            showDialog = false
                            onNavigateToAddSchedule(dateString)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MainColors.Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MainColors.TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("새 일정 등록", color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilterBarSection(
    displayCategories: List<CategoryDto>,
    selectedCategoryIds: Set<String>,
    onCategoryToggle: (String?) -> Unit,
    onNavigateToCategoryEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryIds.isEmpty(),
                    onClick = { onCategoryToggle(null) },
                    label = { Text("전체", color = MainColors.TextWhite) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MainColors.Primary,
                        containerColor = MainColors.Card
                    )
                )
            }

            itemsIndexed(displayCategories) { index, category ->
                val catId = category.category_id?.toString()
                val isSelected = catId != null && selectedCategoryIds.contains(catId)
                val chipColor = MainColors.CategoryColors[index % MainColors.CategoryColors.size]

                FilterChip(
                    selected = isSelected,
                    onClick = { if (catId != null) onCategoryToggle(catId) },
                    label = { Text(category.title, color = MainColors.TextWhite) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        containerColor = MainColors.Card
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onNavigateToCategoryEdit,
            modifier = Modifier
                .size(40.dp)
                .background(MainColors.Card, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "카테고리 상세 목록",
                tint = MainColors.TextWhite
            )
        }
    }
}
@Composable
fun TodayScheduleDialog(
    schedules: List<ScheduleDto>,
    onDismiss: () -> Unit,
    onDoNotShowToday: () -> Unit
) {
    val todayDateFormatted = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")) }
    val currentTimeStr = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MainColors.Card,
        title = {
            Column {
                Text(
                    text = "오늘의 일정",
                    color = MainColors.TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = todayDateFormatted,
                    color = MainColors.TextGray,
                    fontSize = 13.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                schedules.forEach { schedule ->
                    val isPast = schedule.schedule_time.take(5) < currentTimeStr

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPast) MainColors.Background.copy(alpha = 0.6f) else MainColors.Background
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = schedule.title,
                                color = if (isPast) MainColors.TextGray else MainColors.TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isPast) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "지남",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = schedule.schedule_time,
                                    color = if (isPast) MainColors.TextGray else MainColors.Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDoNotShowToday()
                    onDismiss()
                }
            ) {
                Text(
                    text = "오늘 하루 보지 않기",
                    color = MainColors.TextGray,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MainColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("닫기", color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DialogCategoryChip(
    title: String,
    isSelected: Boolean,
    accentColor: Color = MainColors.Primary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) accentColor else accentColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else accentColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) MainColors.TextWhite else accentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun CategoryMiniTag(name: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = name,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun DailyScheduleItem(
    schedule: ScheduleDto,
    categories: List<Pair<String, Color>>,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val primaryColor = categories.firstOrNull()?.second ?: MainColors.Primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MainColors.Background),
        border = BorderStroke(0.5.dp, MainColors.TextGray.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (schedule.is_completed) primaryColor else MainColors.TextGray,
                            shape = CircleShape
                        )
                        .background(if (schedule.is_completed) primaryColor else Color.Transparent)
                        .clickable { onToggleComplete() },
                    contentAlignment = Alignment.Center
                ) {
                    if (schedule.is_completed) {
                        Text("✓", color = MainColors.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = schedule.title,
                        color = MainColors.TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.schedule_time,
                            color = MainColors.TextGray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (categories.isEmpty()) {
                                CategoryMiniTag(name = "일반", color = MainColors.Primary)
                            } else {
                                categories.take(2).forEach { (name, color) ->
                                    CategoryMiniTag(name = name, color = color)
                                }
                                if (categories.size > 2) {
                                    Text(
                                        text = "+${categories.size - 2}",
                                        color = MainColors.TextGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onEdit() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "수정",
                        tint = MainColors.TextGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarCell(
    day: Int,
    schedules: List<ScheduleDto>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .height(85.dp)
            .border(0.5.dp, MainColors.Card)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = day.toString(), color = MainColors.TextWhite, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))

        schedules.take(3).forEach { schedule ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MainColors.Primary.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = schedule.title,
                    color = MainColors.Primary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (schedules.size > 3) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF374151))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "+${schedules.size - 3}",
                    color = MainColors.TextWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) activeColor else activeColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = if (isSelected) activeColor else activeColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) MainColors.TextWhite else activeColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun BottomNavigationBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(containerColor = MainColors.Background, contentColor = MainColors.TextWhite) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "홈") },
            label = { Text("홈") },
            selected = selectedTab == "홈",
            onClick = { onTabSelected("홈") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MainColors.Primary,
                unselectedIconColor = MainColors.TextGray,
                selectedTextColor = MainColors.Primary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, contentDescription = "카테고리") },
            label = { Text("카테고리") },
            selected = selectedTab == "카테고리",
            onClick = { onTabSelected("카테고리") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MainColors.Primary,
                unselectedIconColor = MainColors.TextGray,
                selectedTextColor = MainColors.Primary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.DateRange, contentDescription = "D-Day") },
            label = { Text("D-Day") },
            selected = selectedTab == "D-Day",
            onClick = { onTabSelected("D-Day") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MainColors.Primary,
                unselectedIconColor = MainColors.TextGray,
                selectedTextColor = MainColors.Primary,
                indicatorColor = Color.Transparent
            )
        )
    }
}