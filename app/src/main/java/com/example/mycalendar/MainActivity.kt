package com.example.mycalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mycalendar.ui.admin.AdminScreen
import com.example.mycalendar.ui.category.CategoryAddScreen
import com.example.mycalendar.ui.category.CategoryEdit
import com.example.mycalendar.ui.D_day.DdayScreen
import com.example.mycalendar.ui.login.LoginScreen
import com.example.mycalendar.ui.main.MainColors
import com.example.mycalendar.ui.main.MainScreen
import com.example.mycalendar.ui.schedule.ScheduleDto
import com.example.mycalendar.ui.schedule.ScheduleScreen
import com.example.mycalendar.ui.schedule.ScheduleViewModel
import com.example.mycalendar.ui.signup.SignUpScreen
import com.example.mycalendar.ui.theme.MyCalendarTheme
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyCalendarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MainColors.Background
                ) {
                    val navController = rememberNavController()
                    val coroutineScope = rememberCoroutineScope()

                    NavHost(navController = navController, startDestination = "login") {

                        composable("login") {
                            LoginScreen(
                                onNavigateToSignUp = { navController.navigate("signup") },
                                onLoginSuccess = { role ->
                                    val destination = if (role.uppercase() == "ADMIN") "admin" else "main"
                                    navController.navigate(destination) {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("signup") {
                            SignUpScreen(
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("signup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("main") {
                            MainScreen(
                                onNavigateToAddSchedule = { selectedDate ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("initialDate", selectedDate)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("scheduleJson", null as String?)
                                    navController.navigate("schedule_add")
                                },
                                onNavigateToEditSchedule = { schedule ->
                                    val scheduleJson = Json.encodeToString(ScheduleDto.serializer(), schedule)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("scheduleJson", scheduleJson)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("initialDate", null as String?)
                                    navController.navigate("schedule_add")
                                },
                                onNavigateToAddCategory = {
                                    navController.navigate("category_add")
                                },
                                onLogout = {
                                    coroutineScope.launch {
                                        try {
                                            SupabaseClient.client.auth.signOut()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        navController.navigate("login") {
                                            popUpTo("main") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable("schedule_add") { backStackEntry ->
                            val scheduleViewModel: ScheduleViewModel = viewModel(backStackEntry)

                            val initialDate = navController.previousBackStackEntry?.savedStateHandle?.get<String>("initialDate")
                            val scheduleJson = navController.previousBackStackEntry?.savedStateHandle?.get<String>("scheduleJson")
                            val scheduleToEdit = scheduleJson?.let {
                                Json.decodeFromString(ScheduleDto.serializer(), it)
                            }

                            ScheduleScreen(
                                viewModel = scheduleViewModel,
                                scheduleToEdit = scheduleToEdit,
                                initialDate = initialDate,
                                onBack = { navController.popBackStack() },
                                onNavigateToAddCategory = { navController.navigate("category_add") },
                                onSaveComplete = { navController.popBackStack() }
                            )
                        }

                        composable("category_add") {
                            CategoryAddScreen(
                                onBack = { navController.popBackStack() },
                                onSaveComplete = { navController.popBackStack() }
                            )
                        }

                        composable("category_edit") {
                            CategoryEdit(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("dday_screen") {
                            DdayScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("admin") {
                            AdminScreen(
                                onLogout = {
                                    coroutineScope.launch {
                                        try {
                                            SupabaseClient.client.auth.signOut()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        navController.navigate("login") {
                                            popUpTo("admin") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}