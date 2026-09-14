package com.example.mycalendar.ui.category

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.schedule.CategoryDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

private object CategoryEditColors {
    val Background = Color(0xFF161924)
    val Card = Color(0xFF222736)
    val TextWhite = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFA0A4B8)
    val Primary = Color(0xFF665BFF)
    val DeleteRed = Color(0xFFE11D48)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEdit(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 삭제 대상 카테고리 상태 (null이 아니면 삭제 팝업 표시)
    var categoryToDelete by remember { mutableStateOf<CategoryDto?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val fetchedCategories = SupabaseClient.client.postgrest["categories"]
                        .select {
                            filter {
                                eq("user_uuid", user.id)
                            }
                        }
                        .decodeList<CategoryDto>()
                        .filter { !it.title.isNullOrBlank() }

                    categories = fetchedCategories
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = CategoryEditColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "카테고리 관리",
                        color = CategoryEditColors.TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = CategoryEditColors.TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CategoryEditColors.Background)
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
                CircularProgressIndicator(color = CategoryEditColors.Primary)
            } else if (categories.isEmpty()) {
                Text(
                    text = "등록된 카테고리가 없습니다.",
                    color = CategoryEditColors.TextGray,
                    fontSize = 16.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(categories) { category ->
                        CategoryItemCard(
                            category = category,
                            onDeleteClick = { categoryToDelete = category }
                        )
                    }
                }
            }
        }
    }

    // 1. 삭제 확인 AlertDialog 팝업
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            containerColor = CategoryEditColors.Card,
            title = {
                Text(
                    text = "카테고리 삭제",
                    color = CategoryEditColors.TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "해당 카테고리를 삭제 하시겠습니까?",
                    color = CategoryEditColors.TextGray
                )
            },
            // 3. '삭제' 선택 시 DB 삭제 실행
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetCategory = category
                        categoryToDelete = null
                        
                        coroutineScope.launch {
                            try {
                                targetCategory.category_id?.let { id ->
                                    SupabaseClient.client.postgrest["categories"].delete {
                                        filter { eq("category_id", id) }
                                    }
                                    
                                    categories = categories.filter { it.category_id != id }
                                    Toast.makeText(context, "해당 카테고리를 삭제하였습니다", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "카테고리 삭제에 실패하였습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("삭제", color = CategoryEditColors.DeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("취소", color = CategoryEditColors.TextWhite)
                }
            }
        )
    }
}

@Composable
fun CategoryItemCard(
    category: CategoryDto,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CategoryEditColors.Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.title ?: "",
                color = CategoryEditColors.TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* 수정 기능 미구현 */ }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "수정",
                        tint = CategoryEditColors.TextGray
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = CategoryEditColors.DeleteRed
                    )
                }
            }
        }
    }
}