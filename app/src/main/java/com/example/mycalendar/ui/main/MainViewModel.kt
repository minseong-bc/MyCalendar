package com.example.mycalendar.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.schedule.CategoryDto
import com.example.mycalendar.ui.schedule.ScheduleCategoryDto
import com.example.mycalendar.ui.schedule.ScheduleDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import java.time.LocalDate

class MainViewModel : ViewModel() {
    var selectedDate by mutableStateOf(LocalDate.now().toString())
    var selectedCategoryId by mutableStateOf<String?>(null)
    var schedules by mutableStateOf<List<ScheduleDto>>(emptyList())
    var categories by mutableStateOf<List<CategoryDto>>(emptyList())
    var scheduleCategoryMappings by mutableStateOf<List<ScheduleCategoryDto>>(emptyList())

    val filteredSchedules: List<ScheduleDto>
        get() {
            val catId = selectedCategoryId ?: return schedules
            val matchedScheduleIds = scheduleCategoryMappings
                .filter { it.category_id == catId }
                .map { it.schedule_id }
            return schedules.filter { matchedScheduleIds.contains(it.schedule_id) }
        }

    val achievementRate: Float
        get() {
            val targetList = filteredSchedules
            if (targetList.isEmpty()) return 0f
            val completedCount = targetList.count { it.is_completed }
            return completedCount.toFloat() / targetList.size.toFloat()
        }

    suspend fun loadDataForSelectedDate() {
        val user = SupabaseClient.client.auth.currentUserOrNull() ?: return

        categories = SupabaseClient.client.postgrest["categories"]
            .select { filter { eq("user_uuid", user.id) } }
            .decodeList<CategoryDto>()

        schedules = SupabaseClient.client.postgrest["schedules"]
            .select {
                filter {
                    eq("user_uuid", user.id)
                    eq("schedule_date", selectedDate)
                }
            }
            .decodeList<ScheduleDto>()

        scheduleCategoryMappings = SupabaseClient.client.postgrest["schedule_categories"]
            .select()
            .decodeList<ScheduleCategoryDto>()
    }

    suspend fun toggleScheduleCompletion(schedule: ScheduleDto) {
        val updatedSchedule = schedule.copy(is_completed = !schedule.is_completed)
        SupabaseClient.client.postgrest["schedules"].update(updatedSchedule) {
            filter { eq("schedule_id", schedule.schedule_id ?: "") }
        }
        loadDataForSelectedDate()
    }
}