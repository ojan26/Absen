package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AttendanceRepository
import com.example.ui.AttendanceViewModel
import com.example.ui.AttendanceViewModelFactory
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database, DAOs, and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = AttendanceRepository(
            attendeeDao = database.attendeeDao(),
            attendanceLogDao = database.attendanceLogDao(),
            attendanceSessionDao = database.attendanceSessionDao()
        )

        // Instantiate ViewModel with factory
        val viewModel: AttendanceViewModel by viewModels {
            AttendanceViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
