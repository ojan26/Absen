package com.example.utils

import android.util.Log
import com.example.data.AttendanceLog
import com.example.data.Attendee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SupabaseSyncHelper {

    private const val TAG = "SupabaseSyncHelper"

    /**
     * Memformat URL Supabase untuk endpoint REST tabel tertentu.
     */
    private fun getTableUrl(supabaseUrl: String, tableName: String): String {
        val cleanUrl = supabaseUrl.trim().removeSuffix("/")
        return "$cleanUrl/rest/v1/$tableName"
    }

    /**
     * Memeriksa koneksi Supabase dengan melakukan GET ringan ke tabel 'siswa' dengan limit 1.
     */
    suspend fun testConnection(supabaseUrl: String, anonKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val urlString = "${getTableUrl(supabaseUrl, "siswa")}?select=uid&limit=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return@withContext Pair(true, "Koneksi Supabase Berhasil! Tabel 'siswa' dapat diakses.")
            } else {
                val errorStream = connection.errorStream
                val errorMessage = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "Kode Respon: $responseCode"
                }
                Log.e(TAG, "Test connection failed: $errorMessage")
                return@withContext Pair(false, "Gagal terhubung ke Supabase. Kode: $responseCode. Detail: $errorMessage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection error", e)
            return@withContext Pair(false, "Kesalahan jaringan: ${e.localizedMessage ?: "Tidak dapat menghubungi server"}")
        }
    }

    /**
     * Mengirim (Push) data Log Absensi ke tabel 'kehadiran' di Supabase.
     */
    suspend fun syncLogsToSupabase(
        supabaseUrl: String,
        anonKey: String,
        logs: List<AttendanceLog>,
        deviceId: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (logs.isEmpty()) return@withContext true
        try {
            val urlString = getTableUrl(supabaseUrl, "kehadiran")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")
            connection.setRequestProperty("Content-Type", "application/json")
            
            // Postgrest Upsert Header: Menyelesaikan duplikasi berdasarkan kolom id / unique keys
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates, return=minimal")

            val jsonArray = JSONArray()
            logs.forEach { log ->
                val uniqueId = if (deviceId.isNotBlank()) "${deviceId}_${log.id}" else log.id.toString()
                val jsonObj = JSONObject().apply {
                    put("id_unique", uniqueId) // Kita bisa gunakan kolom khusus atau jadikan primary key eksternal
                    put("uid", log.uid)
                    put("name", log.name)
                    put("role", log.role)
                    put("timestamp", log.timestamp)
                    put("type", log.type)
                    put("status", log.status)
                    put("session_name", log.sessionName ?: "")
                    put("school_id", log.schoolId ?: "")
                    put("school_name", log.schoolName ?: "")
                }
                jsonArray.put(jsonObj)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonArray.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            // HTTP 201 Created atau 204 No Content (karena return=minimal) menunjukkan sukses
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Logs successfully pushed to Supabase. Response: $responseCode")
                return@withContext true
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Failed pushing logs. Response code: $responseCode. Error: $errorText")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing logs to Supabase", e)
            return@withContext false
        }
    }

    /**
     * Mengirim (Push) data Siswa ke tabel 'siswa' di Supabase.
     */
    suspend fun syncAttendeesToSupabase(
        supabaseUrl: String,
        anonKey: String,
        attendees: List<Attendee>
    ): Boolean = withContext(Dispatchers.IO) {
        if (attendees.isEmpty()) return@withContext true
        try {
            val urlString = getTableUrl(supabaseUrl, "siswa")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")
            connection.setRequestProperty("Content-Type", "application/json")
            
            // Postgrest Upsert Header
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates, return=minimal")

            val jsonArray = JSONArray()
            attendees.forEach { student ->
                val jsonObj = JSONObject().apply {
                    put("uid", student.uid)
                    put("name", student.name)
                    put("role", student.role)
                    put("school_id", student.schoolId ?: "")
                    put("school_name", student.schoolName ?: "")
                }
                jsonArray.put(jsonObj)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonArray.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Attendees successfully pushed to Supabase.")
                return@withContext true
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Failed pushing attendees. Response code: $responseCode. Error: $errorText")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing attendees to Supabase", e)
            return@withContext false
        }
    }

    /**
     * Menarik (Pull) data Siswa dari tabel 'siswa' di Supabase berdasarkan NPSN Sekolah.
     */
    suspend fun importAttendeesFromSupabase(
        supabaseUrl: String,
        anonKey: String,
        schoolId: String
    ): List<Attendee> = withContext(Dispatchers.IO) {
        try {
            // Memfilter sesuai school_id di postgres: ?school_id=eq.schoolId
            val urlString = "${getTableUrl(supabaseUrl, "siswa")}?school_id=eq.${java.net.URLEncoder.encode(schoolId, "UTF-8")}"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }
                
                val jsonArray = JSONArray(responseText)
                val list = mutableListOf<Attendee>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val uid = obj.optString("uid", "ID-${(100000..999999).random()}")
                    val name = obj.optString("name", "")
                    val role = obj.optString("role", "Siswa")
                    val sId = obj.optString("school_id", schoolId)
                    val sName = obj.optString("school_name", "")
                    
                    if (name.isNotEmpty()) {
                        list.add(Attendee(uid = uid, name = name, role = role, schoolId = sId, schoolName = sName, synced = true))
                    }
                }
                return@withContext list
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("Supabase Siswa API returned $responseCode: $errorText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling attendees from Supabase", e)
            throw e
        }
    }

    /**
     * Menarik (Pull) data Log Absensi dari tabel 'kehadiran' di Supabase berdasarkan NPSN Sekolah.
     */
    suspend fun importLogsFromSupabase(
        supabaseUrl: String,
        anonKey: String,
        schoolId: String
    ): List<AttendanceLog> = withContext(Dispatchers.IO) {
        try {
            val urlString = "${getTableUrl(supabaseUrl, "kehadiran")}?school_id=eq.${java.net.URLEncoder.encode(schoolId, "UTF-8")}"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }
                
                val jsonArray = JSONArray(responseText)
                val list = mutableListOf<AttendanceLog>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val uid = obj.optString("uid", "")
                    val name = obj.optString("name", "")
                    val role = obj.optString("role", "Siswa")
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val type = obj.optString("type", "MASUK")
                    val status = obj.optString("status", "HADIR")
                    val sessionName = obj.optString("session_name", "Umum")
                    val sId = obj.optString("school_id", schoolId)
                    val sName = obj.optString("school_name", "")
                    
                    if (uid.isNotEmpty()) {
                        list.add(
                            AttendanceLog(
                                id = 0,
                                uid = uid,
                                name = name,
                                role = role,
                                timestamp = timestamp,
                                type = type,
                                status = status,
                                sessionName = if (sessionName.isBlank()) null else sessionName,
                                schoolId = sId,
                                schoolName = sName,
                                synced = true
                            )
                        )
                    }
                }
                return@withContext list
            } else {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("Supabase Kehadiran API returned $responseCode: $errorText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling logs from Supabase", e)
            throw e
        }
    }

    /**
     * Mengirim (Push) data Broadcast Developer ke tabel 'app_broadcast' di Supabase.
     */
    suspend fun pushBroadcast(
        supabaseUrl: String,
        anonKey: String,
        title: String,
        message: String,
        driveLink: String,
        type: String,
        isActive: Boolean,
        updatedId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val urlString = getTableUrl(supabaseUrl, "app_broadcast")
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.doOutput = true
        connection.setRequestProperty("apikey", anonKey.trim())
        connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates, return=minimal")

        val jsonArray = JSONArray().apply {
            val jsonObj = JSONObject().apply {
                put("id", 1)
                put("title", title)
                put("message", message)
                put("drive_link", driveLink)
                put("type", type)
                put("is_active", isActive)
                put("updated_id", updatedId)
            }
            put(jsonObj)
        }

        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(jsonArray.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            return@withContext true
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            var friendlyError = "HTTP $responseCode"
            try {
                val errorJson = JSONObject(errorText)
                val msg = errorJson.optString("message")
                val hint = errorJson.optString("hint")
                val details = errorJson.optString("details")
                friendlyError = when {
                    msg.contains("relation", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true) -> {
                        "Tabel 'app_broadcast' belum dibuat di Supabase Anda! Harap jalankan perintah SQL Setup di Supabase SQL Editor."
                    }
                    else -> {
                        val parts = mutableListOf<String>()
                        if (msg.isNotEmpty()) parts.add(msg)
                        if (hint.isNotEmpty()) parts.add("Hint: $hint")
                        if (details.isNotEmpty()) parts.add("Detail: $details")
                        parts.joinToString(". ")
                    }
                }
            } catch (jsonEx: Exception) {
                if (errorText.isNotEmpty()) {
                    friendlyError = errorText
                }
            }
            throw Exception(friendlyError)
        }
    }

    /**
     * Mengambil (Pull) Broadcast Developer dari tabel 'app_broadcast' di Supabase.
     */
    suspend fun fetchBroadcast(
        supabaseUrl: String,
        anonKey: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val urlString = "${getTableUrl(supabaseUrl, "app_broadcast")}?id=eq.1&limit=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(responseText)
                if (arr.length() > 0) {
                    return@withContext arr.getJSONObject(0)
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetchBroadcast", e)
            return@withContext null
        }
    }

    /**
     * Mengambil daftar tanggal libur dari tabel 'hari_libur'.
     * Kolom bisa berupa 'tanggal' atau 'date'.
     */
    suspend fun fetchHariLibur(
        supabaseUrl: String,
        anonKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        try {
            val urlString = getTableUrl(supabaseUrl, "hari_libur")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("apikey", anonKey.trim())
            connection.setRequestProperty("Authorization", "Bearer ${anonKey.trim()}")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(responseText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tanggal = when {
                        obj.has("tanggal") -> obj.optString("tanggal")
                        obj.has("date") -> obj.optString("date")
                        else -> ""
                    }
                    if (tanggal.isNotBlank()) {
                        list.add(tanggal.trim())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetchHariLibur", e)
        }
        return@withContext list
    }
}

