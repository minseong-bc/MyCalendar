package com.example.mycalendar.ui.category

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

data class CategoryStat(
    val category: CategoryDto,
    val totalSchedules: Int,
    val completedSchedules: Int
) {
    val achievementRate: Float
        get() = if (totalSchedules > 0) completedSchedules.toFloat() / totalSchedules else 0f
}

class CategoryManageViewModel : ViewModel() {
    var categoryStats by mutableStateOf<List<CategoryStat>>(emptyList())
    var isLoading by mutableStateOf(false)

    suspend fun loadCategoryStats() {
        isLoading = true
        try {
            val user = SupabaseClient.client.auth.currentUserOrNull() ?: return

            val categories = SupabaseClient.client.postgrest["categories"]
                .select { filter { eq("user_uuid", user.id) } }
                .decodeList<CategoryDto>()

            val schedules = SupabaseClient.client.postgrest["schedules"]
                .select { filter { eq("user_uuid", user.id) } }
                .decodeList<ScheduleDto>()

            val mappings = SupabaseClient.client.postgrest["schedule_categories"]
                .select()
                .decodeList<ScheduleCategoryDto>()

            val scheduleMap = schedules.associateBy { it.schedule_id }

            categoryStats = categories.map { cat ->
                val catScheduleIds = mappings.filter { it.category_id == cat.category_id }.map { it.schedule_id }
                val catSchedules = catScheduleIds.mapNotNull { scheduleMap[it] }

                val total = catSchedules.size
                val completed = catSchedules.count { it.is_completed }

                CategoryStat(
                    category = cat,
                    totalSchedules = total,
                    completedSchedules = completed
                )
            }
        } finally {
            isLoading = false
        }
    }

    suspend fun updateCategoryTitle(categoryId: String, newTitle: String) {
        SupabaseClient.client.postgrest["categories"].update(
            { set("title", newTitle) }
        ) {
            filter { eq("category_id", categoryId) }
        }
        loadCategoryStats()
    }

    suspend fun deleteCategory(categoryId: String) {
        SupabaseClient.client.postgrest["schedule_categories"].delete {
            filter { eq("category_id", categoryId) }
        }
        SupabaseClient.client.postgrest["categories"].delete {
            filter { eq("category_id", categoryId) }
        }
        loadCategoryStats()
    }
}