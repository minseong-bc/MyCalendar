package com.example.mycalendar.ui.category

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mycalendar.SupabaseClient
import com.example.mycalendar.ui.main.MainColors
import com.example.mycalendar.ui.schedule.CategoryDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAddScreen(
    onBack: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var categoryTitle by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MainColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("카테고리 추가", color = MainColors.TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = MainColors.TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainColors.Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MainColors.Card),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("카테고리 이름", color = MainColors.TextGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = categoryTitle,
                        onValueChange = { categoryTitle = it },
                        placeholder = { Text("이름 입력", color = MainColors.TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MainColors.TextWhite,
                            unfocusedTextColor = MainColors.TextWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                if (categoryTitle.isBlank()) {
                                    Toast.makeText(context, "카테고리 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val user = SupabaseClient.client.auth.currentUserOrNull()
                                            ?: throw Exception("로그인 정보가 없습니다.")
                                        val newCategory = CategoryDto(
                                            user_uuid = user.id,
                                            title = categoryTitle
                                        )
                                        SupabaseClient.client.postgrest["categories"].insert(newCategory)
                                        Toast.makeText(context, "카테고리가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                                        onSaveComplete()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "추가 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MainColors.Primary),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSaving
                        ) {
                            if (isSaving) CircularProgressIndicator(color = MainColors.TextWhite, modifier = Modifier.size(20.dp))
                            else Text("추가 완료", color = MainColors.TextWhite)
                        }

                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isSaving
                        ) {
                            Text("취소", color = MainColors.TextWhite)
                        }
                    }
                }
            }
        }
    }
}

