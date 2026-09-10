package com.example.mycalendar.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.mycalendar.AppColors

@Composable
fun AdminScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "관리자 화면",
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextWhite
        )
    }
}