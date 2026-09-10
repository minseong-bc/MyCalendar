package com.example.mycalendar.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mycalendar.AppColors
import com.example.mycalendar.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

// users 테이블 조회용 DTO (created_at 필드 추가)
@Serializable
private data class UserDto(
    val name: String? = "",
    val username: String? = "",
    val role: String? = "",
    val created_at: String? = null
)

@Composable
fun AdminScreen(
    onLogout: () -> Unit = {} // 로그아웃 콜백 함수
) {
    var userList by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 화면 진입 시 DB 조회
    LaunchedEffect(Unit) {
        try {
            val response = SupabaseClient.client.from("users")
                .select {
                    filter {
                        eq("role", "USER")
                    }
                }.decodeList<UserDto>()

            userList = response
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "사용자 목록을 불러오지 못했습니다."
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // 1. 상단 헤더 영역 (아이콘 + 제목 + 로그아웃 버튼)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AppColors.TextGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "가입 사용자 목록",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.TextWhite
                )
            }

            // 로그아웃 버튼 (기능 미연동 UI)
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B3145)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "로그아웃",
                    color = AppColors.TextWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 인원 수 표시
        if (!isLoading && errorMessage == null) {
            Text(
                text = "${userList.size}명",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextGray,
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. 본문 영역
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = AppColors.Primary)
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                userList.isEmpty() -> {
                    Text(
                        text = "등록된 사용자가 없습니다.",
                        color = AppColors.TextGray
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(userList) { user ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 사용자 정보 (좌측)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = user.name ?: "이름 없음",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = AppColors.TextWhite
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (!user.username.isNull_Blank()) "${user.username}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppColors.TextGray
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    // 버튼 영역 (우측: 수정 / 삭제)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // 수정 버튼
                                        Button(
                                            onClick = { /* 기능 없음 */ },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF384055)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                        ) {
                                            Text(
                                                text = "수정",
                                                color = AppColors.TextWhite,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        // 삭제 버튼
                                        Button(
                                            onClick = { /* 기능 없음 */ },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E2D38)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                        ) {
                                            Text(
                                                text = "삭제",
                                                color = Color(0xFFFF8A8A),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// isNullOrBlank 확장 함수 안전망
private fun String?.isNull_Blank(): Boolean = this == null || this.isBlank()