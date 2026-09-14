package com.example.mycalendar.ui.D_day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdayScreen(
    onBack: () -> Unit = {}
) {
    var ddaySchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    // 1, 2. user_uuid가 본인이면서 dday 컬럼이 true인 스케줄 조회
                    val fetchedSchedules = SupabaseClient.client.postgrest["schedules"]
                        .select {
                            filter {
                                eq("user_uuid", user.id)
                                eq("dday", true)
                            }
                        }
                        .decodeList<ScheduleDto>()

                    // ★ schedule_date 오름차순 정렬 (날짜가 가까운 일정 우선 표시: D-6, D-7, D-8...)
                    ddaySchedules = fetchedSchedules.sortedBy { schedule ->
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

    Scaffold(
        containerColor = DdayColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "D-Day 목록",
                        color = DdayColors.TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = DdayColors.TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DdayColors.Background)
            )
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
                    text = "등록된 D-Day 일정이 없습니다.",
                    color = DdayColors.TextGray,
                    fontSize = 16.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ddaySchedules) { schedule ->
                        DdayCardItem(schedule = schedule)
                    }
                }
            }
        }
    }
}

@Composable
fun DdayCardItem(schedule: ScheduleDto) {
    // 3. 현재 날짜 - schedule_date 값 계산
    val ddayCalculated = remember(schedule.schedule_date) {
        calculateDDay(schedule.schedule_date)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = schedule.schedule_date,
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

/**
 * D-Day 계산 로직
 * 오늘 날짜 기준 targetDate가 미래이면 D-X, 오늘이면 D-Day, 과거면 D+X 형태로 반환
 */
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