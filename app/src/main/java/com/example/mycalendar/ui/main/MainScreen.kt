package com.example.mycalendar.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

object MainColors {
    val Background = Color(0xFF161924)
    val Card = Color(0xFF222736)
    val TextWhite = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFA0A4B8)
    val Primary = Color(0xFF665BFF)

    val CategoryColors = listOf(
        Color(0xFF18786D),
        Color(0xFFD97706),
        Color(0xFFE11D48),
        Color(0xFF8B5CF6),
        Color(0xFF0EA5E9),
        Color(0xFF10B981)
    )
}

@Composable
fun MainScreen(
    onNavigateToAddSchedule: (String?) -> Unit,
    onNavigateToEditSchedule: (ScheduleDto) -> Unit,
    onNavigateToCategoryEdit: () -> Unit,
    onNavigateToDDay: () -> Unit, // ★ D-Day 화면 이동 콜백 추가
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("홈") }

    Scaffold(
        containerColor = MainColors.Background,
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    when (tab) {
                        "카테고리" -> onNavigateToCategoryEdit() // '카테고리' 클릭 시 이동
                        "D-Day" -> onNavigateToDDay()         // ★ 'D-Day' 클릭 시 이동
                        else -> selectedTab = tab
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                "홈" -> HomeCalendarContent(onLogout, onNavigateToAddSchedule, onNavigateToEditSchedule)
            }
        }
    }
}

@Composable
fun HomeCalendarContent(
    onLogout: () -> Unit,
    onNavigateToAddSchedule: (String?) -> Unit,
    onNavigateToEditSchedule: (ScheduleDto) -> Unit
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

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "ALL_CATEGORY") {
                CategoryChip(
                    title = "전체",
                    isSelected = selectedCategoryIds.isEmpty(),
                    activeColor = MainColors.Primary,
                    onSelect = { selectedCategoryIds = emptySet() }
                )
            }

            itemsIndexed(
                items = displayCategories,
                key = { _, category -> category.category_id?.toString() ?: category.hashCode().toString() }
            ) { index, category ->
                val categoryId = category.category_id?.toString()
                val isSelected = categoryId != null && selectedCategoryIds.contains(categoryId)
                val chipColor = MainColors.CategoryColors[index % MainColors.CategoryColors.size]

                CategoryChip(
                    title = category.title,
                    isSelected = isSelected,
                    activeColor = chipColor,
                    onSelect = {
                        if (categoryId != null) {
                            selectedCategoryIds = if (isSelected) {
                                selectedCategoryIds - categoryId
                            } else {
                                selectedCategoryIds + categoryId
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                Divider(color = MainColors.Background, thickness = 1.dp)

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
        val dateString = "${currentYearMonth.year}-${String.format("%02d", currentYearMonth.monthValue)}-${String.format("%02d", selectedDate)}"
        val dailySchedules = filteredSchedules.filter { it.schedule_date == dateString }

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MainColors.Card)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "${currentYearMonth.monthValue}월 ${selectedDate}일 일정", style = MaterialTheme.typography.titleLarge, color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (dailySchedules.isEmpty()) {
                        Text("등록된 일정이 없습니다.", color = MainColors.TextGray, modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        dailySchedules.forEach { schedule ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MainColors.Background, RoundedCornerShape(8.dp)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = schedule.title, color = MainColors.TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(text = schedule.schedule_time, color = MainColors.TextGray, fontSize = 12.sp)
                                }

                                IconButton(onClick = {
                                    showDialog = false
                                    onNavigateToEditSchedule(schedule)
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "수정", tint = MainColors.TextGray, modifier = Modifier.size(20.dp))
                                }

                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try {
                                            schedule.schedule_id?.let { id ->
                                                SupabaseClient.client.postgrest["schedules"].delete { filter { eq("schedule_id", id) } }
                                                schedules = schedules.filter { it.schedule_id != id }
                                                Toast.makeText(context, "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color(0xFFE11D48), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showDialog = false
                            onNavigateToAddSchedule(dateString)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MainColors.Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MainColors.TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("새 일정 등록", color = MainColors.TextWhite)
                    }
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
            icon = { Icon(Icons.Default.Home, contentDescription = "홈") }, label = { Text("홈") }, selected = selectedTab == "홈", onClick = { onTabSelected("홈") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = MainColors.Primary, unselectedIconColor = MainColors.TextGray, selectedTextColor = MainColors.Primary, indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, contentDescription = "카테고리") }, label = { Text("카테고리") }, selected = selectedTab == "카테고리", onClick = { onTabSelected("카테고리") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = MainColors.Primary, unselectedIconColor = MainColors.TextGray, selectedTextColor = MainColors.Primary, indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.DateRange, contentDescription = "D-Day") }, label = { Text("D-Day") }, selected = selectedTab == "D-Day", onClick = { onTabSelected("D-Day") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = MainColors.Primary, unselectedIconColor = MainColors.TextGray, selectedTextColor = MainColors.Primary, indicatorColor = Color.Transparent)
        )
    }
}