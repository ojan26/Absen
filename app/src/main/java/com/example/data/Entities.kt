package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendees")
data class Attendee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uid: String, // Unique NIP/NIS or auto-generated UUID
    val name: String,
    val role: String, // e.g. "Staf", "Siswa", "Guru"
    val createdAt: Long = System.currentTimeMillis(),
    val photoPath: String? = null, // Path to the student's photo taken from camera
    val schoolId: String? = null,
    val schoolName: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "attendance_logs")
data class AttendanceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uid: String,
    val name: String,
    val role: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "MASUK" or "PULANG"
    val status: String, // "Tepat Waktu", "Terlambat"
    val sessionName: String? = null, // Optional session/location where they checked in
    val schoolId: String? = null,
    val schoolName: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "attendance_sessions")
data class AttendanceSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String, // Unique scanner code
    val title: String, // Name of the session, class, or location
    val createdAt: Long = System.currentTimeMillis()
)
