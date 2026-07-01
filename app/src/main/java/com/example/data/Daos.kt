package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendeeDao {
    @Query("SELECT * FROM attendees ORDER BY name ASC")
    fun getAllAttendees(): Flow<List<Attendee>>

    @Query("SELECT * FROM attendees")
    suspend fun getAllAttendeesList(): List<Attendee>

    @Query("SELECT * FROM attendees WHERE uid = :uid LIMIT 1")
    suspend fun getAttendeeByUid(uid: String): Attendee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendee(attendee: Attendee)

    @Query("DELETE FROM attendees")
    suspend fun clearAllAttendees()

    @Query("DELETE FROM attendees WHERE id = :id")
    suspend fun deleteAttendee(id: Int)

    @Query("DELETE FROM attendees WHERE schoolId = :schoolId")
    suspend fun deleteAttendeesBySchool(schoolId: String)

    @Query("DELETE FROM attendees WHERE schoolId = :schoolId AND synced = 1")
    suspend fun deleteSyncedAttendeesBySchool(schoolId: String)
}

@Dao
interface AttendanceLogDao {
    @Query("SELECT * FROM attendance_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AttendanceLog>>

    @Query("SELECT * FROM attendance_logs WHERE uid = :uid ORDER BY timestamp DESC")
    fun getLogsForAttendee(uid: String): Flow<List<AttendanceLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AttendanceLog)

    @Query("UPDATE attendance_logs SET synced = 1 WHERE synced = 0")
    suspend fun markAllLogsAsSynced()

    @Query("DELETE FROM attendance_logs WHERE id = :id")
    suspend fun deleteLog(id: Int)

    @Query("DELETE FROM attendance_logs WHERE schoolId = :schoolId")
    suspend fun deleteLogsBySchool(schoolId: String)

    @Query("DELETE FROM attendance_logs")
    suspend fun clearAllLogs()
}

@Dao
interface AttendanceSessionDao {
    @Query("SELECT * FROM attendance_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<AttendanceSession>>

    @Query("SELECT * FROM attendance_sessions WHERE code = :code LIMIT 1")
    suspend fun getSessionByCode(code: String): AttendanceSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSession)

    @Query("DELETE FROM attendance_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM attendance_sessions WHERE id = :id")
    suspend fun deleteSession(id: Int)
}
