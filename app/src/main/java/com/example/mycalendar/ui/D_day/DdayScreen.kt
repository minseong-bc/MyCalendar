package com.example.mycalendar.ui.D_day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.schedule.ScheduleDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private object DdayColors {
    val Background = Color(0xFF161924)
    val Card = Color(0xFF222736)
    val TextWhite = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFA0A4B8)
    val Primary = Color(0xFF665BFF)
    val ErrorRed = Color(0xFFFF5252)
    val WarningOrange = Color(0xFFFF9800)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdayScreen(
    onBack: () -> Unit = {},
    onNavigateToEdit: (ScheduleDto) -> Unit = {}
) {
    var ddaySchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var nonDdaySchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isListLoading by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedScheduleForManage by remember { mutableStateOf<ScheduleDto?>(null) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleDto?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun fetchDdaySchedules() {
        coroutineScope.launch {
            try {
                isLoading = true
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val fetched = SupabaseClient.client.postgrest["schedules"]
                        .select {
                            filter {
                                eq("user_uuid", user.id)
                                eq("dday", true)
                            }
                        }
                        .decodeList<ScheduleDto>()

                    ddaySchedules = fetched.sortedBy { schedule ->
                        try {
                            LocalDate.parse(schedule.schedule_date)
                        } catch (e: Exception) {
                            LocalDate.MAX
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchNonDdaySchedules() {
        coroutineScope.launch {
            try {
                isListLoading = true
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val fetched = SupabaseClient.client.postgrest["schedules"]
                        .select {
                            filter {
                                eq("user_uuid", user.id)
                                eq("dday", false)
                            }
                        }
                        .decodeList<ScheduleDto>()

                    nonDdaySchedules = fetched.sortedBy { schedule ->
                        try {
                            LocalDate.parse(schedule.schedule_date)
                        } catch (e: Exception) {
                            LocalDate.MAX
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isListLoading = false
            }
        }
    }

    fun updateDdayStatus(scheduleId: String, isDday: Boolean) {
        coroutineScope.launch {
            try {
                SupabaseClient.client.postgrest["schedules"]
                    .update({
                        set("dday", isDday)
                    }) {
                        filter { eq("schedule_id", scheduleId) }
                    }
                showAddDialog = false
                selectedScheduleForManage = null
                fetchDdaySchedules()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        coroutineScope.launch {
            try {
                SupabaseClient.client.postgrest["schedules"]
                    .delete {
                        filter { eq("schedule_id", scheduleId) }
                    }
                scheduleToDelete = null
                selectedScheduleForManage = null
                fetchDdaySchedules()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchDdaySchedules()
    }

    Scaffold(
        containerColor = DdayColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("D-Day 목록", color = DdayColors.TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = DdayColors.TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DdayColors.Background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    fetchNonDdaySchedules()
                    showAddDialog = true
                },
                containerColor = DdayColors.Primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "D-Day 추가")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = DdayColors.Primary)
            } else if (ddaySchedules.isEmpty()) {
                Text(
                    text = "등록된 D-Day 일정이 없습니다.\n+ 버튼을 눌러 추가해보세요.",
                    color = DdayColors.TextGray,
                    fontSize = 16.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ddaySchedules) { schedule ->
                        DdayCardItem(
                            schedule = schedule,
                            onClick = { selectedScheduleForManage = schedule }
                        )
                    }
                }
            }
        }

        selectedScheduleForManage?.let { schedule ->
            AlertDialog(
                onDismissRequest = { selectedScheduleForManage = null },
                containerColor = DdayColors.Card,
                title = {
                    Text("D-Day 일정 관리", color = DdayColors.TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = schedule.title,
                            color = DdayColors.TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "일시: ${schedule.schedule_date} ${schedule.schedule_time}",
                            color = DdayColors.TextGray,
                            fontSize = 14.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetSchedule = schedule
                            selectedScheduleForManage = null
                            onNavigateToEdit(targetSchedule)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DdayColors.Primary)
                    ) {
                        Text("수정", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                schedule.schedule_id?.let { updateDdayStatus(it, false) }
                            }
                        ) {
                            Text("D-Day 해제", color = DdayColors.WarningOrange)
                        }
                        TextButton(
                            onClick = {
                                scheduleToDelete = schedule
                            }
                        ) {
                            Text("삭제", color = DdayColors.ErrorRed)
                        }
                    }
                }
            )
        }

        scheduleToDelete?.let { schedule ->
            AlertDialog(
                onDismissRequest = { scheduleToDelete = null },
                containerColor = DdayColors.Card,
                title = {
                    Text("일정 삭제", color = DdayColors.TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "'${schedule.title}' 일정을 정말로 삭제하시겠습니까?\n삭제된 일정은 복구할 수 없습니다.",
                        color = DdayColors.TextGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            schedule.schedule_id?.let { deleteSchedule(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DdayColors.ErrorRed)
                    ) {
                        Text("삭제", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { scheduleToDelete = null }) {
                        Text("취소", color = DdayColors.TextWhite)
                    }
                }
            )
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = DdayColors.Card,
                title = {
                    Text(
                        text = "D-Day로 지정할 일정 선택",
                        color = DdayColors.TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    if (isListLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DdayColors.Primary)
                        }
                    } else if (nonDdaySchedules.isEmpty()) {
                        Text(
                            text = "D-Day로 지정 가능한 일반 일정이 없습니다.",
                            color = DdayColors.TextGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(nonDdaySchedules) { schedule ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            schedule.schedule_id?.let { id ->
                                                updateDdayStatus(id, true)
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DdayColors.Background
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = schedule.title,
                                                color = DdayColors.TextWhite,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${schedule.schedule_date} ${schedule.schedule_time}",
                                                color = DdayColors.TextGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = "+ 추가",
                                            color = DdayColors.Primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("닫기", color = DdayColors.TextGray)
                    }
                }
            )
        }
    }
}

@Composable
fun DdayCardItem(
    schedule: ScheduleDto,
    onClick: () -> Unit = {}
) {
    val ddayCalculated = remember(schedule.schedule_date) {
        calculateDDay(schedule.schedule_date)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DdayColors.Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    color = DdayColors.TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${schedule.schedule_date} ${schedule.schedule_time}",
                    color = DdayColors.TextGray,
                    fontSize = 14.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DdayColors.Primary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, DdayColors.Primary)
            ) {
                Text(
                    text = ddayCalculated,
                    color = DdayColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun calculateDDay(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "D-?"
    return try {
        val targetDate = LocalDate.parse(dateString)
        val today = LocalDate.now()
        val daysBetween = ChronoUnit.DAYS.between(today, targetDate)

        when {
            daysBetween > 0 -> "D-$daysBetween"
            daysBetween == 0L -> "D-Day"
            else -> "D+${-daysBetween}"
        }
    } catch (e: Exception) {
        "D-?"
    }
}