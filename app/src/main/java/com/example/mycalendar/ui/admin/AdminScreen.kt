package com.example.mycalendar.ui.admin

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mycalendar.ui.AppColors
import com.example.mycalendar.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// users 테이블 조회 및 삭제용 DTO
@Serializable
private data class UserDto(
    val user_uuid: String? = null,
    val name: String? = "",
    val username: String? = "",
    val role: String? = "",
    val created_at: String? = null
)

@Composable
fun AdminScreen(
    onLogout: () -> Unit = {} // 로그아웃 성공 시 로그인 화면 이동 콜백
) {
    var userList by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 팝업(AlertDialog) 노출 상태 변수
    var showLogoutDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserDto?>(null) } // 삭제 대상 사용자

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

    // 1. 로그아웃 확인 다이얼로그 팝업
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = AppColors.Card,
            title = {
                Text(
                    text = "로그아웃",
                    color = AppColors.TextWhite,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "로그아웃 하시겠습니까?",
                    color = AppColors.TextWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("취소", color = AppColors.TextGray)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            try {
                                SupabaseClient.client.auth.signOut()
                            } catch (e: Exception) {
                                android.util.Log.e("LogoutError", "로그아웃 에러", e)
                            }
                            onLogout()
                        }
                    }
                ) {
                    Text("로그아웃", color = Color(0xFFFF8A8A))
                }
            }
        )
    }

    // 2. 사용자 삭제 확인 다이얼로그 팝업
    userToDelete?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            containerColor = AppColors.Card,
            title = {
                Text(
                    text = "사용자 삭제",
                    color = AppColors.TextWhite,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "해당 사용자를 삭제하시겠습니까?",
                    color = AppColors.TextWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { userToDelete = null } // 취소 선택 시 팝업 닫기
                ) {
                    Text("취소", color = AppColors.TextGray)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedUser = targetUser
                        userToDelete = null // 팝업 닫기

                        coroutineScope.launch {
                            try {
                                // Supabase DB에서 삭제 수행 (user_uuid 우선, 없을 경우 username 기준)
                                SupabaseClient.client.from("users")
                                    .delete {
                                        filter {
                                            if (!selectedUser.user_uuid.isNullOrBlank()) {
                                                eq("user_uuid", selectedUser.user_uuid)
                                            } else if (!selectedUser.username.isNullOrBlank()) {
                                                eq("username", selectedUser.username)
                                            }
                                        }
                                    }

                                // UI 목록에서 해당 사용자 제거
                                userList = userList.filter { it != selectedUser }
                                Toast.makeText(context, "해당 사용자를 삭제하였습니다", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e("DeleteUserError", "사용자 삭제 실패", e)
                                Toast.makeText(context, "사용자 삭제에 실패하였습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("삭제", color = Color(0xFFFF8A8A))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // 상단 헤더 영역 (아이콘 + 제목 + 로그아웃 버튼)
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

            Button(
                onClick = { showLogoutDialog = true },
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

        // 인원 수 표시
        if (!isLoading && errorMessage == null) {
            Text(
                text = "${userList.size}명",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextGray,
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 본문 영역
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
                                        Button(
                                            onClick = { /* 수정 기능 (미구현) */ },
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

                                        // 삭제 버튼 클릭 시 targetUser 지정 -> 팝업 출력
                                        Button(
                                            onClick = { userToDelete = user },
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

private fun String?.isNull_Blank(): Boolean = this == null || this.isBlank()
