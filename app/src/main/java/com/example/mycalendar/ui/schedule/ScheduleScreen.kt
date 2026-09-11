package com.example.mycalendar.ui.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.main.MainColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
    val schedule_time: String
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

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            viewModel.scheduleTime = String.format("%02d:%02d", hourOfDay, minute)
        },
        12, 0, true
    )

    LaunchedEffect(Unit) {
        try {
            viewModel.loadCategoriesAndData(scheduleToEdit, initialDate)
        } catch (e: Exception) {
            android.util.Log.e("ScheduleError", "카테고리 오류 상세", e)
            Toast.makeText(context, "카테고리 오류: ${e.message}", Toast.LENGTH_SHORT).show()
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

                Box(modifier = Modifier.weight(1f).clickable { timePickerDialog.show() }) {
                    OutlinedTextField(
                        value = viewModel.scheduleTime, onValueChange = {}, readOnly = true, enabled = false,
                        label = { Text("시간 선택", color = MainColors.TextGray) },
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

                                val scheduleData = ScheduleDto(targetScheduleId, user.id, viewModel.title, viewModel.scheduleDate, viewModel.scheduleTime)

                                if (isEditMode) {
                                    SupabaseClient.client.postgrest["schedules"].update(scheduleData) {
                                        filter { eq("schedule_id", targetScheduleId) }
                                    }
                                    SupabaseClient.client.postgrest["schedule_categories"].delete {
                                        filter { eq("schedule_id", targetScheduleId) }
                                    }
                                } else {
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
}

