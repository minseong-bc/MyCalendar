package com.example.mycalendar.ui.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.mycalendar.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class ScheduleViewModel : ViewModel() {
    var title by mutableStateOf("")
    var scheduleDate by mutableStateOf("")
    var scheduleTime by mutableStateOf("")
    var selectedCategoryIds by mutableStateOf<Set<String>>(emptySet())
    var availableCategories by mutableStateOf<List<CategoryDto>>(emptyList())
    var isInitialized by mutableStateOf(false)

    suspend fun loadCategoriesAndData(scheduleToEdit: ScheduleDto?, initialDate: String?) {
        val user = SupabaseClient.client.auth.currentUserOrNull() ?: return

        if (!isInitialized) {
            title = scheduleToEdit?.title ?: ""
            scheduleDate = scheduleToEdit?.schedule_date ?: initialDate ?: ""
            scheduleTime = scheduleToEdit?.schedule_time ?: ""
            isInitialized = true
        }

        val fetchedCategories = SupabaseClient.client.postgrest["categories"]
            .select { filter { eq("user_uuid", user.id) } }
            .decodeList<CategoryDto>()

        if (fetchedCategories.isEmpty()) {
            val defaultCategory = CategoryDto(user_uuid = user.id, title = "일반")
            SupabaseClient.client.postgrest["categories"].insert(defaultCategory)
            availableCategories = SupabaseClient.client.postgrest["categories"]
                .select { filter { eq("user_uuid", user.id) } }
                .decodeList<CategoryDto>()
        } else {
            availableCategories = fetchedCategories
        }
        
        if (scheduleToEdit?.schedule_id != null && selectedCategoryIds.isEmpty()) {
            val mappings = SupabaseClient.client.postgrest["schedule_categories"]
                .select { filter { eq("schedule_id", scheduleToEdit.schedule_id) } }
                .decodeList<ScheduleCategoryDto>()
            selectedCategoryIds = mappings.map { it.category_id }.toSet()
        }
    }

    fun clearInputs() {
        title = ""
        scheduleDate = ""
        scheduleTime = ""
        selectedCategoryIds = emptySet()
        isInitialized = false
    }
}

