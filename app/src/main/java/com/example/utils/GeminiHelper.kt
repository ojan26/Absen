package com.example.utils

import android.util.Log
import com.example.data.AttendanceLog
import com.example.data.Attendee
import com.example.BuildConfig
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

object GeminiHelper {
    private const val TAG = "GeminiHelper"

    private suspend fun callGeminiApi(modelName: String, apiKey: String, prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                // Request body format
                val requestBodyJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBodyJson.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        
                        // Parse response JSON
                        val jsonResponse = JSONObject(response.toString())
                        val candidates = jsonResponse.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.getJSONObject("content")
                            val parts = content.getJSONArray("parts")
                            if (parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).getString("text")
                            }
                        }
                    }
                } else {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).use { reader ->
                            val err = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                err.append(line)
                            }
                            err.toString()
                        }
                    } else {
                        "HTTP $responseCode"
                    }
                    Log.e(TAG, "HTTP Error ($modelName): $errorMessage")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Gemini API for $modelName", e)
            }
            return@withContext null
        }
    }

    suspend fun analyzeAttendance(
        attendees: List<Attendee>,
        logs: List<AttendanceLog>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Kunci API Gemini belum diatur di panel Secrets atau file .env. Silakan hubungi admin atau perbarui .env untuk mengaktifkan analisis AI."
        }

        // Prepare context data for Gemini
        val todayStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
        
        // Filter student attendees
        val students = attendees.filter { 
            val r = it.role.trim().lowercase()
            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
        }
        
        // Group logs by type and date/today
        val sdfYmd = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val todayYmd = sdfYmd.format(Date())
        val todayLogs = logs.filter { sdfYmd.format(Date(it.timestamp)) == todayYmd }
        
        // Ambil log terakhir untuk masing-masing user hari ini agar tidak ganda
        val latestLogsByUser = todayLogs.sortedBy { it.timestamp }.associateBy { it.uid }
        val activeLogs = latestLogsByUser.values
        
        val presentToday = activeLogs.filter { it.type == "MASUK" && (it.status.equals("Tepat Waktu", ignoreCase = true) || it.status.equals("Terlambat", ignoreCase = true)) }
        val tepatWaktuToday = activeLogs.count { it.status.equals("Tepat Waktu", ignoreCase = true) }
        val terlambatToday = activeLogs.count { it.status.equals("Terlambat", ignoreCase = true) }
        val sakitToday = activeLogs.count { it.status.equals("Sakit", ignoreCase = true) }
        val izinToday = activeLogs.count { it.status.equals("Ijin", ignoreCase = true) || it.status.equals("Izin", ignoreCase = true) }
        
        val alphaToday = students.filter { s -> !latestLogsByUser.containsKey(s.uid) }

        val prompt = """
            Anda adalah pakar Analisis Data Absensi Sekolah berbasis AI (bernama AI Guru X-Degan).
            Analisis data kehadiran berikut untuk tanggal: $todayStr.
            
            Informasi Ringkasan:
            - Total Siswa Terdaftar: ${students.size}
            - Hadir Hari Ini: ${presentToday.size} (Tepat Waktu: $tepatWaktuToday, Terlambat: $terlambatToday)
            - Sakit: $sakitToday
            - Izin: $izinToday
            - Tanpa Keterangan / Belum Absen: ${alphaToday.size}
            
            Daftar Siswa yang Terlambat:
            ${if (todayLogs.none { it.status.equals("Terlambat", ignoreCase = true) }) "- Tidak ada" else todayLogs.filter { it.status.equals("Terlambat", ignoreCase = true) }.joinToString("\n") { log -> "- ${students.find { it.uid == log.uid }?.name ?: log.uid} (Jam: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))})" }}
            
            Daftar Siswa Tanpa Keterangan / Belum Absen:
            ${if (alphaToday.isEmpty()) "- Semua siswa hadir/izin/sakit" else alphaToday.joinToString("\n") { "- ${it.name} (${it.role})" }}
            
            Berikan laporan analisis yang profesional, ramah, dan ringkas dalam bahasa Indonesia.
            Isi laporan harus mencakup:
            1. **Ringkasan Tingkat Kehadiran** (Persentase kehadiran, tren positif/negatif secara umum).
            2. **Rekomendasi Tindakan** untuk Guru/Sekolah (misalnya bagi siswa yang terlambat atau belum absen).
            3. **Pesan Motivasi** singkat yang ramah untuk menjaga kedisiplinan kelas.
            
            Format laporan dengan Markdown yang indah dan mudah dibaca di layar HP Android (gunakan bullet points, bold text).
        """.trimIndent()

        // Try models in order: gemini-3.5-flash, gemini-3.1-flash-lite-preview, gemini-flash-latest
        val response = callGeminiApi("gemini-3.5-flash", apiKey, prompt)
            ?: callGeminiApi("gemini-3.1-flash-lite-preview", apiKey, prompt)
            ?: callGeminiApi("gemini-flash-latest", apiKey, prompt)

        if (response != null) {
            return@withContext response
        } else {
            return@withContext "Gagal terhubung ke AI. Silakan periksa koneksi internet Anda atau pastikan kunci API Anda valid di panel Secrets."
        }
    }
}
