package com.example.mycalendar.ui.schedule

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.main.MainColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.UUID

@Serializable
data class CategoryDto(
    val category_id: String? = null,
    val user_uuid: String,
    val title: String
)

@Serializable
data class ScheduleDto(
    val schedule_id: String? = null,
    val user_uuid: String,
    val title: String,
    val schedule_date: String,
    val schedule_time: String,
    val is_completed: Boolean = false,
    val dday: Boolean = false
)

@Serializable
data class ScheduleCategoryDto(val schedule_id: String, val category_id: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    scheduleToEdit: ScheduleDto? = null,
    initialDate: String? = null,
    viewModel: ScheduleViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToAddCategory: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isEditMode = scheduleToEdit != null
    var expandedCategory by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showWheelTimePicker by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            viewModel.scheduleDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(scheduleToEdit) {
        try {
            viewModel.loadCategoriesAndData(scheduleToEdit, initialDate)
        } catch (e: Exception) {
            Toast.makeText(context, "카테고리 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MainColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "일정 수정" else "일정 등록", color = MainColors.TextWhite) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = MainColors.TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainColors.Background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {

            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("일정 이름 *", color = MainColors.TextGray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MainColors.TextWhite, unfocusedTextColor = MainColors.TextWhite)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f).clickable { datePickerDialog.show() }) {
                    OutlinedTextField(
                        value = viewModel.scheduleDate, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text("날짜 선택", color = MainColors.TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MainColors.TextWhite, disabledBorderColor = MainColors.TextGray, disabledLabelColor = MainColors.TextGray)
                    )
                }

                Box(modifier = Modifier.weight(1f).clickable { showWheelTimePicker = true }) {
                    OutlinedTextField(
                        value = viewModel.scheduleTime.ifEmpty { "시간 선택" }, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text("시간 선택 (시:분)", color = MainColors.TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MainColors.TextWhite, disabledBorderColor = MainColors.TextGray, disabledLabelColor = MainColors.TextGray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                val selectedNames = viewModel.availableCategories
                    .filter { viewModel.selectedCategoryIds.contains(it.category_id) }
                    .joinToString(", ") { it.title }
                val displayText = if (selectedNames.isEmpty()) "카테고리를 선택하세요 (기본: 일반)" else selectedNames

                OutlinedTextField(
                    value = displayText, onValueChange = {}, readOnly = true, label = { Text("카테고리 (다중 선택 가능)", color = MainColors.TextGray) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MainColors.TextWhite, unfocusedTextColor = MainColors.TextWhite)
                )

                ExposedDropdownMenu(
                    expanded = expandedCategory, onDismissRequest = { expandedCategory = false },
                    modifier = Modifier.background(MainColors.Card)
                ) {
                    viewModel.availableCategories.forEach { category ->
                        category.category_id?.let { catId ->
                            val isSelected = viewModel.selectedCategoryIds.contains(catId)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MainColors.Primary))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.title, color = MainColors.TextWhite)
                                    }
                                },
                                onClick = {
                                    viewModel.selectedCategoryIds = if (isSelected) viewModel.selectedCategoryIds - catId else viewModel.selectedCategoryIds + catId
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = MainColors.Background)

                    DropdownMenuItem(
                        text = { Text("+ 새 카테고리 추가", color = MainColors.Primary, fontWeight = FontWeight.Bold) },
                        onClick = {
                            expandedCategory = false
                            onNavigateToAddCategory()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MainColors.Card),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("D-Day 등록", color = MainColors.TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("D-Day 화면에서 남은 일수를 확인할 수 있습니다.", color = MainColors.TextGray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = viewModel.isDday,
                        onCheckedChange = { viewModel.isDday = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MainColors.TextWhite,
                            checkedTrackColor = MainColors.Primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        viewModel.clearInputs()
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    Text("취소", color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (viewModel.title.isBlank() || viewModel.scheduleDate.isBlank() || viewModel.scheduleTime.isBlank()) {
                            Toast.makeText(context, "일정 이름, 날짜, 시간을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSaving = true
                        coroutineScope.launch {
                            try {
                                val user = SupabaseClient.client.auth.currentUserOrNull() ?: throw Exception("로그인 정보가 없습니다.")
                                val targetScheduleId = scheduleToEdit?.schedule_id ?: UUID.randomUUID().toString()

                                if (isEditMode) {
                                    // ★ buildJsonObject를 사용해 Any 시리얼라이즈 에러 방지 및 dday=false 값 보장
                                    val updateData = buildJsonObject {
                                        put("user_uuid", user.id)
                                        put("title", viewModel.title)
                                        put("schedule_date", viewModel.scheduleDate)
                                        put("schedule_time", viewModel.scheduleTime)
                                        put("is_completed", scheduleToEdit?.is_completed ?: false)
                                        put("dday", viewModel.isDday)
                                    }

                                    SupabaseClient.client.postgrest["schedules"].update(updateData) {
                                        filter { eq("schedule_id", targetScheduleId) }
                                    }
                                    SupabaseClient.client.postgrest["schedule_categories"].delete {
                                        filter { eq("schedule_id", targetScheduleId) }
                                    }
                                } else {
                                    val scheduleData = ScheduleDto(
                                        schedule_id = targetScheduleId,
                                        user_uuid = user.id,
                                        title = viewModel.title,
                                        schedule_date = viewModel.scheduleDate,
                                        schedule_time = viewModel.scheduleTime,
                                        is_completed = false,
                                        dday = viewModel.isDday
                                    )
                                    SupabaseClient.client.postgrest["schedules"].insert(scheduleData)
                                }

                                var finalCategoryIds = viewModel.selectedCategoryIds
                                if (finalCategoryIds.isEmpty()) {
                                    val generalCategory = viewModel.availableCategories.find { it.title == "일반" }
                                    if (generalCategory?.category_id != null) {
                                        finalCategoryIds = setOf(generalCategory.category_id)
                                    }
                                }

                                val mappingDataList = finalCategoryIds.map { catId -> ScheduleCategoryDto(targetScheduleId, catId) }
                                if (mappingDataList.isNotEmpty()) {
                                    SupabaseClient.client.postgrest["schedule_categories"].insert(mappingDataList)
                                }

                                Toast.makeText(context, if (isEditMode) "일정이 수정되었습니다." else "일정이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                                viewModel.clearInputs()
                                onSaveComplete()
                            } catch (e: Exception) {
                                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MainColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(color = MainColors.TextWhite, modifier = Modifier.size(24.dp))
                    else Text(if (isEditMode) "수정 완료" else "등록 완료", color = MainColors.TextWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showWheelTimePicker) {
        val timeParts = viewModel.scheduleTime.split(":")
        val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: calendar.get(Calendar.MINUTE)

        WheelTimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onDismissRequest = { showWheelTimePicker = false },
            onTimeSelected = { hour, minute ->
                viewModel.scheduleTime = String.format("%02d:%02d", hour, minute)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismissRequest: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = MainColors.Card,
        title = {
            Text(
                text = "시간 선택",
                color = MainColors.TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelPicker(
                    items = (0..23).map { String.format("%02d시", it) },
                    initialIndex = initialHour,
                    onItemSelected = { selectedHour = it }
                )

                Spacer(modifier = Modifier.width(16.dp))
                Text(":", color = MainColors.TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(16.dp))

                WheelPicker(
                    items = (0..59).map { String.format("%02d분", it) },
                    initialIndex = initialMinute,
                    onItemSelected = { selectedMinute = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(selectedHour, selectedMinute)
                    onDismissRequest()
                }
            ) {
                Text("확인", color = MainColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("취소", color = MainColors.TextWhite)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val currentCenteredIndex by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > 50) (firstVisibleIndex + 1).coerceAtMost(items.size - 1)
            else firstVisibleIndex
        }
    }

    LaunchedEffect(currentCenteredIndex) {
        onItemSelected(currentCenteredIndex)
    }

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            color = MainColors.Background,
            shape = RoundedCornerShape(8.dp)
        ) {}

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = 70.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { index ->
                val isSelected = index == currentCenteredIndex
                Text(
                    text = items[index],
                    color = if (isSelected) MainColors.Primary else MainColors.TextGray,
                    fontSize = if (isSelected) 18.sp else 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .wrapContentHeight()
                )
            }
        }
    }
}