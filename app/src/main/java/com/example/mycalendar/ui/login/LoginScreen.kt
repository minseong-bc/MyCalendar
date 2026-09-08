package com.example.mycalendar.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mycalendar.AppColors
import com.example.mycalendar.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var userPassword by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "마이 캘린더", style = MaterialTheme.typography.headlineMedium, color = AppColors.TextWhite)
        }
        Spacer(modifier = Modifier.height(32.dp))


        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                CustomTextField(
                    label = "아이디",
                    placeholder = "아이디 입력 (이메일)",
                    value = userId,
                    onValueChange = { userId = it }
                )
                Spacer(modifier = Modifier.height(16.dp))

                CustomTextField(
                    label = "비밀번호",
                    placeholder = "비밀번호 입력",
                    value = userPassword,
                    onValueChange = { userPassword = it },
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (userId.isBlank() || userPassword.isBlank()) {
                            Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            try {
                                SupabaseClient.client.auth.signInWith(Email) {
                                    email = userId
                                    password = userPassword
                                }
                                Toast.makeText(context, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(context, "아이디가 존재하지 않거나 비밀번호가 맞지 않습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("로그인", color = AppColors.TextWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToSignUp) {
            Text("아직 계정이 없으신가요? 회원가입", color = AppColors.TextGray)
        }
    }
}

@Composable
fun CustomTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    Column {
        Text(text = label, color = AppColors.TextGray, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = AppColors.TextGray) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.InputField,
                unfocusedContainerColor = AppColors.InputField,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = AppColors.TextWhite,
                unfocusedTextColor = AppColors.TextWhite,
                cursorColor = AppColors.Primary
            ),
            singleLine = true
        )
    }
}

