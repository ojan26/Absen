package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(
    private val attendeeDao: AttendeeDao,
    private val attendanceLogDao: AttendanceLogDao,
    private val attendanceSessionDao: AttendanceSessionDao
) {
    val allAttendees: Flow<List<Attendee>> = attendeeDao.getAllAttendees()
    val allLogs: Flow<List<AttendanceLog>> = attendanceLogDao.getAllLogs()
    val allSessions: Flow<List<AttendanceSession>> = attendanceSessionDao.getAllSessions()

    suspend fun getAttendeeByUid(uid: String): Attendee? {
        return attendeeDao.getAttendeeByUid(uid)
    }

    suspend fun insertAttendee(attendee: Attendee) {
        val existing = attendeeDao.getAttendeeByUid(attendee.uid.trim())
        if (existing != null) {
            attendeeDao.insertAttendee(attendee.copy(id = existing.id))
        } else {
            attendeeDao.insertAttendee(attendee)
        }
    }

    suspend fun removeDuplicateAttendees() {
        val all = attendeeDao.getAllAttendeesList()
        val grouped = all.groupBy { it.uid.trim().uppercase() }
        grouped.forEach { (_, attendeesWithSameUid) ->
            if (attendeesWithSameUid.size > 1) {
                val sorted = attendeesWithSameUid.sortedBy { it.id }
                val toDelete = sorted.drop(1)
                toDelete.forEach { duplicate ->
                    attendeeDao.deleteAttendee(duplicate.id)
                }
            }
        }
    }

    suspend fun deleteAttendee(id: Int) {
        attendeeDao.deleteAttendee(id)
    }

    suspend fun deleteAttendeesBySchool(schoolId: String) {
        attendeeDao.deleteAttendeesBySchool(schoolId)
    }

    suspend fun deleteSyncedAttendeesBySchool(schoolId: String) {
        attendeeDao.deleteSyncedAttendeesBySchool(schoolId)
    }

    suspend fun deleteLogsBySchool(schoolId: String) {
        attendanceLogDao.deleteLogsBySchool(schoolId)
    }

    suspend fun clearAllAttendees() {
        attendeeDao.clearAllAttendees()
    }

    suspend fun insertLog(log: AttendanceLog) {
        attendanceLogDao.insertLog(log)
    }

    suspend fun markAllLogsAsSynced() {
        attendanceLogDao.markAllLogsAsSynced()
    }

    suspend fun deleteLog(id: Int) {
        attendanceLogDao.deleteLog(id)
    }

    suspend fun clearAllLogs() {
        attendanceLogDao.clearAllLogs()
    }

    suspend fun getSessionByCode(code: String): AttendanceSession? {
        return attendanceSessionDao.getSessionByCode(code)
    }

    suspend fun insertSession(session: AttendanceSession) {
        attendanceSessionDao.insertSession(session)
    }

    suspend fun deleteSession(id: Int) {
        attendanceSessionDao.deleteSession(id)
    }

    suspend fun clearAllSessions() {
        attendanceSessionDao.clearAllSessions()
    }
}
