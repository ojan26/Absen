package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.speech.tts.TextToSpeech
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttendanceLog
import com.example.data.AttendanceRepository
import com.example.data.AttendanceSession
import com.example.data.Attendee
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class SchoolConfig(
    val id: String,
    val name: String,
    val address: String,
    val appsScriptUrl: String = ""
)

data class ScanResultState(
    val success: Boolean,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BroadcastMessage(
    val id: Long = 1,
    val title: String = "",
    val message: String = "",
    val driveLink: String = "",
    val type: String = "UPDATE", // "UPDATE" or "INSTRUCTION"
    val isActive: Boolean = false,
    val updatedId: Long = 0
)

class AttendanceViewModel(
    application: Application,
    private val repository: AttendanceRepository
) : AndroidViewModel(application) {

    companion object {
        // GANTI URL DI BAWAH INI DENGAN URL WEB APP GOOGLE APPS SCRIPT ANDA YANG SEBENARNYA.
        // JIKA SUDAH DIGANTI, PENGGUNA TIDAK PERLU MEMASUKKAN URL DI APLIKASI DAN BISA LANGSUNG MEMASUKKAN TOKEN.
        const val EMBEDDED_APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbxZJ7jZVVEcT5c6PzX6LwxpoI4dyqZJ2iRWWU9TqxnUS3HGPNcmJ2skU6LDftP0rq7z/exec"
    }

    // SharedPreferences setup
    private val sharedPrefs = application.getSharedPreferences("absenqr_prefs", Context.MODE_PRIVATE)

    fun getAppsScriptUrl(): String {
        val saved = sharedPrefs.getString("apps_script_url", "") ?: ""
        return if (saved.isNotBlank() && !saved.contains("PLACEHOLDER")) saved else EMBEDDED_APPS_SCRIPT_URL
    }

    fun saveAppsScriptUrl(url: String) {
        sharedPrefs.edit().putString("apps_script_url", url.trim()).apply()
    }

    fun getSelectedClass(): String {
        val saved = sharedPrefs.getString("selected_class", "Semua Kelas") ?: "Semua Kelas"
        return if (saved == "Semua") "Semua Kelas" else saved
    }

    fun saveSelectedClass(clazz: String) {
        sharedPrefs.edit().putString("selected_class", clazz.trim()).apply()
        _selectedClass.value = clazz.trim()
    }

    fun getTeacherName(): String {
        return sharedPrefs.getString("teacher_name", "") ?: ""
    }

    fun saveTeacherName(name: String) {
        sharedPrefs.edit().putString("teacher_name", name.trim()).apply()
        _teacherName.value = name.trim()
    }

    fun getSupabaseUrl(): String {
        return sharedPrefs.getString("supabase_url", "https://fzllwcgahiwziurbbfws.supabase.co") ?: "https://fzllwcgahiwziurbbfws.supabase.co"
    }

    fun saveSupabaseUrl(url: String) {
        sharedPrefs.edit().putString("supabase_url", url.trim()).apply()
        _supabaseUrl.value = url.trim()
        checkHariLibur()
    }

    fun getSupabaseAnonKey(): String {
        return sharedPrefs.getString("supabase_anon_key", "sb_publishable_A_E34qVHZTwPbOO__36Xmg_AO-uylwM") ?: "sb_publishable_A_E34qVHZTwPbOO__36Xmg_AO-uylwM"
    }

    fun saveSupabaseAnonKey(key: String) {
        sharedPrefs.edit().putString("supabase_anon_key", key.trim()).apply()
        _supabaseAnonKey.value = key.trim()
        checkHariLibur()
    }

    fun isSupabaseEnabled(): Boolean {
        return sharedPrefs.getBoolean("supabase_enabled", true)
    }

    fun setSupabaseEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("supabase_enabled", enabled).apply()
        _isSupabaseEnabledState.value = enabled
        if (enabled) {
            checkHariLibur()
        } else {
            _isTodayHoliday.value = false
        }
    }

    private val _supabaseUrl = MutableStateFlow(getSupabaseUrl())
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseAnonKey = MutableStateFlow(getSupabaseAnonKey())
    val supabaseAnonKey: StateFlow<String> = _supabaseAnonKey.asStateFlow()

    private val _isSupabaseEnabledState = MutableStateFlow(isSupabaseEnabled())
    val isSupabaseEnabledState: StateFlow<Boolean> = _isSupabaseEnabledState.asStateFlow()

    private val _selectedClass = MutableStateFlow(getSelectedClass())
    val selectedClass: StateFlow<String> = _selectedClass.asStateFlow()

    private val _teacherName = MutableStateFlow(getTeacherName())
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    private val _isTodayHoliday = MutableStateFlow(false)
    val isTodayHoliday: StateFlow<Boolean> = _isTodayHoliday.asStateFlow()

    private val _holidayDates = MutableStateFlow<List<String>>(emptyList())
    val holidayDates: StateFlow<List<String>> = _holidayDates.asStateFlow()

    fun isAutoSyncEnabled(): Boolean {
        return sharedPrefs.getBoolean("auto_sync_enabled", false)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
    }

    fun getDefaultCamera(): String {
        return sharedPrefs.getString("default_camera", "BACK") ?: "BACK"
    }

    fun saveDefaultCamera(camera: String) {
        sharedPrefs.edit().putString("default_camera", camera).apply()
        _defaultCamera.value = camera
    }

    private val _defaultCamera = MutableStateFlow(getDefaultCamera())
    val defaultCamera: StateFlow<String> = _defaultCamera.asStateFlow()

    // --- SOUND & TTS NOTIFICATION SETTINGS ---
    fun isSoundEnabled(): Boolean {
        return sharedPrefs.getBoolean("is_sound_enabled", true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_sound_enabled", enabled).apply()
        _isSoundEnabledState.value = enabled
    }

    fun isTtsEnabled(): Boolean {
        return sharedPrefs.getBoolean("is_tts_enabled", true)
    }

    fun setTtsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_tts_enabled", enabled).apply()
        _isTtsEnabledState.value = enabled
    }

    private val _isSoundEnabledState = MutableStateFlow(isSoundEnabled())
    val isSoundEnabledState: StateFlow<Boolean> = _isSoundEnabledState.asStateFlow()

    private val _isTtsEnabledState = MutableStateFlow(isTtsEnabled())
    val isTtsEnabledState: StateFlow<Boolean> = _isTtsEnabledState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    fun speakText(text: String) {
        if (!isTtsEnabled()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ScanTTS")
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error speaking text", e)
        }
    }

    fun playSoundNotification(success: Boolean) {
        if (!isSoundEnabled()) return
        try {
            val toneType = if (success) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error playing sound", e)
        }
    }

    // --- DEVICE BINDING HELPERS ---
    fun getDeviceId(): String {
        var deviceId = sharedPrefs.getString("device_id", "") ?: ""
        if (deviceId.isBlank()) {
            deviceId = "ZAN-" + java.util.UUID.randomUUID().toString().substring(0, 6).uppercase()
            sharedPrefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    private val _isDemoMode = MutableStateFlow(sharedPrefs.getBoolean("is_demo_mode", false))
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _demoRemainingTime = MutableStateFlow("")
    val demoRemainingTime: StateFlow<String> = _demoRemainingTime.asStateFlow()

    init {
        checkDemoExpiration()
        checkBroadcast()
        checkHariLibur()
        viewModelScope.launch {
            try {
                repository.removeDuplicateAttendees()
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error cleaning up duplicates on startup", e)
            }
        }
        
        try {
            tts = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("id", "ID")
                }
            }
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error initializing TTS or ToneGenerator", e)
        }
    }

    fun checkDemoExpiration(): Boolean {
        val bound = sharedPrefs.getBoolean("is_device_bound", false)
        if (bound) {
            val isDemo = sharedPrefs.getBoolean("is_demo_mode", false)
            if (isDemo) {
                val activationTime = sharedPrefs.getLong("demo_activation_time", 0L)
                if (activationTime > 0L) {
                    val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                    val currentTime = System.currentTimeMillis()
                    val elapsedMs = currentTime - activationTime
                    if (elapsedMs > sevenDaysMs) {
                        unbindDevice()
                        return true
                    } else {
                        val remainingMs = sevenDaysMs - elapsedMs
                        val days = remainingMs / (24 * 60 * 60 * 1000)
                        val hours = (remainingMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                        val minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000)
                        
                        _isDemoMode.value = true
                        _demoRemainingTime.value = when {
                            days > 0 -> "$days Hari $hours Jam"
                            hours > 0 -> "$hours Jam $minutes Menit"
                            else -> "$minutes Menit"
                        }
                    }
                }
            } else {
                _isDemoMode.value = false
                _demoRemainingTime.value = ""
            }
        } else {
            _isDemoMode.value = false
            _demoRemainingTime.value = ""
        }
        return false
    }

    fun isDeviceBound(): Boolean {
        val bound = sharedPrefs.getBoolean("is_device_bound", false)
        if (!bound) return false
        
        val isDemo = sharedPrefs.getBoolean("is_demo_mode", false)
        if (isDemo) {
            val activationTime = sharedPrefs.getLong("demo_activation_time", 0L)
            if (activationTime > 0L) {
                val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                val currentTime = System.currentTimeMillis()
                if (currentTime - activationTime > sevenDaysMs) {
                    viewModelScope.launch {
                        unbindDevice()
                    }
                    return false
                }
            }
        }
        return true
    }

    fun getBindingToken(): String {
        return sharedPrefs.getString("binding_token", "") ?: ""
    }

    fun getBindingDeviceName(): String {
        return sharedPrefs.getString("binding_device_name", "") ?: ""
    }

    private val _isDeviceBound = MutableStateFlow(isDeviceBound())
    val isDeviceBound: StateFlow<Boolean> = _isDeviceBound.asStateFlow()

    private val _bindingDeviceName = MutableStateFlow(getBindingDeviceName())
    val bindingDeviceName: StateFlow<String> = _bindingDeviceName.asStateFlow()

    fun getBindingSpreadsheetId(): String {
        return sharedPrefs.getString("binding_spreadsheet_id", "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0") ?: "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0"
    }

    fun saveBindingSpreadsheetId(id: String) {
        sharedPrefs.edit().putString("binding_spreadsheet_id", id.trim()).apply()
        _bindingSpreadsheetId.value = id.trim()
    }

    fun getAttendanceSpreadsheetId(): String {
        return sharedPrefs.getString("attendance_spreadsheet_id", "1wVGEU_k7kyz-5gxKAkSb4gwNgaQxtBRoQtEbaLOYzxQ") ?: "1wVGEU_k7kyz-5gxKAkSb4gwNgaQxtBRoQtEbaLOYzxQ"
    }

    fun saveAttendanceSpreadsheetId(id: String) {
        sharedPrefs.edit().putString("attendance_spreadsheet_id", id.trim()).apply()
        _attendanceSpreadsheetId.value = id.trim()
    }

    private val _bindingSpreadsheetId = MutableStateFlow(getBindingSpreadsheetId())
    val bindingSpreadsheetId: StateFlow<String> = _bindingSpreadsheetId.asStateFlow()

    private val _attendanceSpreadsheetId = MutableStateFlow(getAttendanceSpreadsheetId())
    val attendanceSpreadsheetId: StateFlow<String> = _attendanceSpreadsheetId.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(sharedPrefs.getString("last_sync_time", "Belum pernah") ?: "Belum pernah")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _isManualSyncing = MutableStateFlow(false)
    val isManualSyncing: StateFlow<Boolean> = _isManualSyncing.asStateFlow()

    fun updateLastSyncTime() {
        val now = SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale("id", "ID")).format(java.util.Date())
        sharedPrefs.edit().putString("last_sync_time", now).apply()
        _lastSyncTime.value = now
    }

    fun bindDevice(
        context: Context,
        webAppUrl: String,
        token: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanToken = token.trim().uppercase()
                if (cleanToken == "DEV123") {
                    sharedPrefs.edit()
                        .putBoolean("is_device_bound", true)
                        .putBoolean("is_demo_mode", false)
                        .putLong("demo_activation_time", 0L)
                        .putString("binding_token", token)
                        .putString("binding_device_name", "Developer Simulator Device")
                        .apply()
                    _isDeviceBound.value = true
                    _bindingDeviceName.value = "Developer Simulator Device"
                    _isDemoMode.value = false
                    _demoRemainingTime.value = ""
                    onResult(true, "Berhasil masuk dalam mode Pengembang Offline!")
                    return@launch
                }

                if (cleanToken.startsWith("DEMO")) {
                    sharedPrefs.edit()
                        .putBoolean("is_device_bound", true)
                        .putBoolean("is_demo_mode", true)
                        .putLong("demo_activation_time", System.currentTimeMillis())
                        .putString("binding_token", token.trim())
                        .putString("binding_device_name", "Demo Device (Masa Aktif 7 Hari)")
                        .apply()
                    _isDeviceBound.value = true
                    _bindingDeviceName.value = "Demo Device (Masa Aktif 7 Hari)"
                    _isDemoMode.value = true
                    checkDemoExpiration()
                    onResult(true, "Berhasil masuk dalam mode Demo 7 Hari!")
                    return@launch
                }

                if (webAppUrl.isBlank()) {
                    onResult(false, "Web App URL tidak boleh kosong.")
                    return@launch
                }
                if (token.isBlank()) {
                    onResult(false, "Token tidak boleh kosong.")
                    return@launch
                }

                val deviceId = getDeviceId()
                val separator = if (webAppUrl.contains("?")) "&" else "?"
                val verifyUrl = "$webAppUrl${separator}action=verifyToken&token=${java.net.URLEncoder.encode(token.trim(), "UTF-8")}&deviceId=${java.net.URLEncoder.encode(deviceId, "UTF-8")}"

                val responseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val url = java.net.URL(verifyUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000

                        val responseCode = connection.responseCode
                        if (responseCode == java.net.HttpURLConnection.HTTP_OK || responseCode == 302) {
                            var finalConnection = connection
                            if (responseCode == 302) {
                                val newUrl = connection.getHeaderField("Location")
                                val redirectUrl = java.net.URL(newUrl)
                                finalConnection = redirectUrl.openConnection() as java.net.HttpURLConnection
                                finalConnection.requestMethod = "GET"
                            }
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(finalConnection.inputStream))
                            reader.use { it.readText() }
                        } else {
                            throw Exception("Respon server: $responseCode")
                        }
                    } catch (e: Exception) {
                        Log.e("AttendanceViewModel", "Error verifying token", e)
                        throw e
                    }
                }

                if (responseText.isNotEmpty()) {
                    val jsonObj = org.json.JSONObject(responseText)
                    val success = jsonObj.optBoolean("success", false)
                    if (success) {
                        val schoolName = jsonObj.optString("schoolName", "")
                        val deviceName = jsonObj.optString("deviceName", "Perangkat Terikat")
                        val isDemoToken = token.trim().uppercase().startsWith("DEMO")

                        sharedPrefs.edit()
                            .putBoolean("is_device_bound", true)
                            .putBoolean("is_demo_mode", isDemoToken)
                            .putLong("demo_activation_time", if (isDemoToken) System.currentTimeMillis() else 0L)
                            .putString("binding_token", token.trim())
                            .putString("binding_device_name", deviceName)
                            .putString("apps_script_url", webAppUrl.trim())
                            .apply()

                        if (schoolName.isNotBlank()) {
                            sharedPrefs.edit().putString("school_name", schoolName).apply()
                            _schoolName.value = schoolName
                        }

                        _isDeviceBound.value = true
                        _bindingDeviceName.value = deviceName
                        _isDemoMode.value = isDemoToken
                        checkDemoExpiration()

                        onResult(true, if (isDemoToken) "Perangkat berhasil terikat sebagai DEMO 7 Hari: $deviceName" else "Perangkat berhasil terikat: $deviceName")
                    } else {
                        val message = jsonObj.optString("message", "Token tidak valid.")
                        onResult(false, message)
                    }
                } else {
                    onResult(false, "Gagal mendapatkan respon valid dari Spreadsheet.")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error binding device", e)
                onResult(false, "Gagal menghubungkan: ${e.localizedMessage ?: "Kesalahan Jaringan"}")
            }
        }
    }

    fun unbindDevice() {
        sharedPrefs.edit()
            .putBoolean("is_device_bound", false)
            .putBoolean("is_demo_mode", false)
            .putLong("demo_activation_time", 0L)
            .putString("binding_token", "")
            .putString("binding_device_name", "")
            .apply()
        _isDeviceBound.value = false
        _bindingDeviceName.value = ""
        _isDemoMode.value = false
        _demoRemainingTime.value = ""
    }

    fun registerDevice(
        context: Context,
        webAppUrl: String,
        deviceName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (webAppUrl.isBlank()) {
                    onResult(false, "Web App URL tidak boleh kosong.")
                    return@launch
                }

                val deviceId = getDeviceId()
                val separator = if (webAppUrl.contains("?")) "&" else "?"
                val registerUrl = "$webAppUrl${separator}action=registerDevice&deviceId=${java.net.URLEncoder.encode(deviceId, "UTF-8")}&deviceName=${java.net.URLEncoder.encode(deviceName, "UTF-8")}"

                val responseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val url = java.net.URL(registerUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000

                        val responseCode = connection.responseCode
                        if (responseCode == java.net.HttpURLConnection.HTTP_OK || responseCode == 302) {
                            var finalConnection = connection
                            if (responseCode == 302) {
                                val newUrl = connection.getHeaderField("Location")
                                val redirectUrl = java.net.URL(newUrl)
                                finalConnection = redirectUrl.openConnection() as java.net.HttpURLConnection
                                finalConnection.requestMethod = "GET"
                            }
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(finalConnection.inputStream))
                            reader.use { it.readText() }
                        } else {
                            throw Exception("Respon server: $responseCode")
                        }
                    } catch (e: Exception) {
                        Log.e("AttendanceViewModel", "Error registering device", e)
                        throw e
                    }
                }

                if (responseText.isNotEmpty()) {
                    val jsonObj = org.json.JSONObject(responseText)
                    val success = jsonObj.optBoolean("success", false)
                    val message = jsonObj.optString("message", "Gagal mendaftarkan perangkat.")

                    sharedPrefs.edit().putString("apps_script_url", webAppUrl.trim()).apply()

                    onResult(success, message)
                } else {
                    onResult(false, "Gagal mendapatkan respon valid dari Spreadsheet.")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error registering device", e)
                onResult(false, "Gagal mendaftarkan: ${e.localizedMessage ?: "Kesalahan Jaringan"}")
            }
        }
    }

    // School Info States & Helpers
    fun getSchoolName(): String {
        return sharedPrefs.getString("school_name", "Sekolah X-Degan QR") ?: "Sekolah X-Degan QR"
    }

    fun getSchoolAddress(): String {
        return sharedPrefs.getString("school_address", "Jl. Pendidikan No. 123") ?: "Jl. Pendidikan No. 123"
    }

    fun getSchoolLogoPath(): String {
        return sharedPrefs.getString("school_logo_path", "") ?: ""
    }

    private val _schoolName = MutableStateFlow(getSchoolName())
    val schoolName: StateFlow<String> = _schoolName.asStateFlow()

    private val _schoolAddress = MutableStateFlow(getSchoolAddress())
    val schoolAddress: StateFlow<String> = _schoolAddress.asStateFlow()

    private val _schoolLogoPath = MutableStateFlow(getSchoolLogoPath())
    val schoolLogoPath: StateFlow<String> = _schoolLogoPath.asStateFlow()

    fun updateSchoolInfo(name: String, address: String, logoPath: String) {
        sharedPrefs.edit()
            .putString("school_name", name.trim())
            .putString("school_address", address.trim())
            .putString("school_logo_path", logoPath)
            .apply()
        _schoolName.value = name
        _schoolAddress.value = address
        _schoolLogoPath.value = logoPath
    }

    // StateFlow for Device ID
    private val _deviceIdFlow = MutableStateFlow(getDeviceId())
    val deviceIdFlow: StateFlow<String> = _deviceIdFlow.asStateFlow()

    fun updateDeviceId(newId: String) {
        val formatted = newId.trim().uppercase()
        sharedPrefs.edit().putString("device_id", formatted).apply()
        _deviceIdFlow.value = formatted
    }

    fun updateActiveSchoolId(newId: String, onFinished: ((Boolean, String) -> Unit)? = null) {
        val safeOnFinished = onFinished?.let { callback ->
            { success: Boolean, msg: String ->
                viewModelScope.launch(Dispatchers.Main) {
                    callback(success, msg)
                }
            }
        }
        val oldId = getActiveSchoolId()
        val formatted = newId.trim().uppercase()
        if (formatted.isBlank()) {
            safeOnFinished?.invoke(false, "NPSN tidak boleh kosong.")
            return
        }
        if (formatted == oldId) {
            safeOnFinished?.invoke(true, "NPSN tidak berubah.")
            return
        }
        
        sharedPrefs.edit().putString("active_school_id", formatted).apply()
        _activeSchoolId.value = formatted
        
        // Update school configs list (including newly updated school name & address)
        val newName = getSchoolName()
        val newAddress = getSchoolAddress()
        val configs = getSchoolConfigs().map {
            if (it.id == oldId) {
                it.copy(id = formatted, name = newName, address = newAddress)
            } else {
                it
            }
        }
        saveSchoolConfigs(configs)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete local data for old NPSN from database (attendees & logs)
                repository.deleteAttendeesBySchool(oldId)
                repository.deleteLogsBySchool(oldId)
                Log.d("AttendanceViewModel", "Deleted old local data for NPSN: $oldId")

                // 2. Also clear local data for the new NPSN to ensure clean slate
                repository.deleteAttendeesBySchool(formatted)
                repository.deleteLogsBySchool(formatted)

                // 3. Fetch data from Supabase
                val activeSchool = configs.find { it.id == formatted }
                val sUrl = getSupabaseUrl()
                val sKey = getSupabaseAnonKey()

                if (sUrl.isBlank() || sKey.isBlank()) {
                    safeOnFinished?.invoke(false, "NPSN berhasil diubah ke $formatted. Data lokal lama telah dihapus. Silakan konfigurasi Supabase (URL / API Key) di tab 'Supabase' untuk mengimpor data siswa.")
                    return@launch
                }

                try {
                    // Pull attendees from Supabase
                    val remoteAttendees = com.example.utils.SupabaseSyncHelper.importAttendeesFromSupabase(sUrl, sKey, formatted)
                    
                    if (remoteAttendees.isNotEmpty()) {
                        val schoolName = activeSchool?.name ?: getSchoolName()
                        remoteAttendees.forEach { remoteAttendee ->
                            repository.insertAttendee(
                                remoteAttendee.copy(
                                    schoolId = formatted,
                                    schoolName = schoolName,
                                    synced = true
                                )
                            )
                        }

                        // Also pull matched logs if any exist
                        var logsCount = 0
                        try {
                            val remoteLogs = com.example.utils.SupabaseSyncHelper.importLogsFromSupabase(sUrl, sKey, formatted)
                            remoteLogs.forEach { log ->
                                repository.insertLog(log.copy(id = 0, schoolId = formatted, schoolName = schoolName, synced = true))
                                logsCount++
                            }
                        } catch (e: Exception) {
                            Log.e("AttendanceViewModel", "Failed to pull logs from Supabase during NPSN change", e)
                        }

                        safeOnFinished?.invoke(true, "NPSN berhasil diubah ke $formatted. Menemukan & mengimpor ${remoteAttendees.size} data siswa dan $logsCount log dari Supabase.")
                    } else {
                        // Keep local database empty and notify user
                        safeOnFinished?.invoke(false, "Data siswa untuk NPSN $formatted tidak ditemukan di Supabase. Database dikosongkan. Silakan isi/tambah data siswa baru secara manual!")
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Error fetching from Supabase on NPSN change", e)
                    safeOnFinished?.invoke(false, "Gagal menghubungi Supabase atau data tidak ditemukan: ${e.localizedMessage}. Database dikosongkan. Silakan isi/tambah data siswa baru secara manual!")
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Failed to update school ID and pull data", e)
                safeOnFinished?.invoke(false, "Terjadi kesalahan: ${e.localizedMessage}")
            }
        }
    }

    fun setDeviceBound(bound: Boolean) {
        sharedPrefs.edit().putBoolean("is_device_bound", bound).apply()
        _isDeviceBound.value = bound
    }

    fun getSchoolConfigs(): List<SchoolConfig> {
        val jsonStr = sharedPrefs.getString("school_configs_json", null)
        if (jsonStr.isNullOrBlank()) {
            val currentName = getSchoolName()
            val currentAddress = getSchoolAddress()
            val currentUrl = sharedPrefs.getString("apps_script_url", "") ?: ""
            val defaultSchool = SchoolConfig(
                id = "SCH-DEFAULT",
                name = currentName,
                address = currentAddress,
                appsScriptUrl = currentUrl
            )
            val list = listOf(defaultSchool)
            saveSchoolConfigs(list)
            return list
        }
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<SchoolConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SchoolConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        address = obj.getString("address"),
                        appsScriptUrl = obj.optString("appsScriptUrl", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSchoolConfigs(configs: List<SchoolConfig>) {
        val arr = org.json.JSONArray()
        configs.forEach { config ->
            val obj = org.json.JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("address", config.address)
                put("appsScriptUrl", config.appsScriptUrl)
            }
            arr.put(obj)
        }
        sharedPrefs.edit().putString("school_configs_json", arr.toString()).apply()
        _schoolConfigsListFlow.value = configs
    }

    fun getActiveSchoolId(): String {
        return sharedPrefs.getString("active_school_id", "SCH-DEFAULT") ?: "SCH-DEFAULT"
    }

    private val _activeSchoolId = MutableStateFlow(getActiveSchoolId())
    val activeSchoolId: StateFlow<String> = _activeSchoolId.asStateFlow()

    private val _schoolConfigsListFlow = MutableStateFlow<List<SchoolConfig>>(emptyList())
    val schoolConfigsListFlow: StateFlow<List<SchoolConfig>> = _schoolConfigsListFlow.asStateFlow()

    init {
        _schoolConfigsListFlow.value = getSchoolConfigs()
    }

    fun setActiveSchoolId(id: String) {
        sharedPrefs.edit().putString("active_school_id", id).apply()
        _activeSchoolId.value = id
        val activeSchool = getSchoolConfigs().find { it.id == id }
        if (activeSchool != null) {
            updateSchoolInfo(activeSchool.name, activeSchool.address, getSchoolLogoPath())
        }
        viewModelScope.launch {
            autoSyncLogs()
            autoSyncAttendees()
        }
    }

    // Student Photo Update Helper
    fun updateAttendeePhoto(attendee: Attendee, photoPath: String) {
        viewModelScope.launch {
            repository.insertAttendee(attendee.copy(photoPath = photoPath))
        }
    }

    // Network Connectivity monitoring
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        registerNetworkCallback()
        startRealtimeSyncTimer()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AttendanceViewModel", "Internet connected! Auto-sync triggering...")
                    if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1000) // slight delay to let network stabilize
                            autoSyncLogs()
                            autoSyncAttendees()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Failed to register network callback", e)
        }
    }

    private fun startRealtimeSyncTimer() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val activeId = getActiveSchoolId()
                    if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank() && activeId != "SCH-DEFAULT") {
                        Log.d("AttendanceViewModel", "Real-time background sync starting for school $activeId...")
                        autoSyncLogs()
                        autoSyncAttendees()
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Error in real-time background sync", e)
                }
                kotlinx.coroutines.delay(15000)
            }
        }
    }

    suspend fun autoSyncLogs() {
        val activeId = getActiveSchoolId()
        if (activeId == "SCH-DEFAULT") {
            Log.d("AttendanceViewModel", "Skipping autoSyncLogs for default school ID SCH-DEFAULT")
            return
        }
        val activeSchool = getSchoolConfigs().find { it.id == activeId }
        
        if (isSupabaseEnabled()) {
            val sUrl = getSupabaseUrl()
            val sKey = getSupabaseAnonKey()
            if (sUrl.isBlank() || sKey.isBlank()) return
            try {
                // 1. PUSH only unsynced logs
                val unsyncedLogs = repository.allLogs.first().filter { !it.synced && !isHolidayTimestamp(it.timestamp) }
                if (unsyncedLogs.isNotEmpty()) {
                    val success = com.example.utils.SupabaseSyncHelper.syncLogsToSupabase(sUrl, sKey, unsyncedLogs, getDeviceId())
                    if (success) {
                        repository.markAllLogsAsSynced()
                        Log.d("AttendanceViewModel", "Auto-sync (push) logs to Supabase successful: ${unsyncedLogs.size} logs.")
                    }
                }
                
                // 2. PULL logs
                try {
                    val remoteLogs = com.example.utils.SupabaseSyncHelper.importLogsFromSupabase(sUrl, sKey, activeId)
                    if (remoteLogs.isNotEmpty()) {
                        var addedCount = 0
                        val localLogs = repository.allLogs.first()
                        val schoolName = activeSchool?.name ?: getSchoolName()
                        remoteLogs.forEach { remoteLog ->
                            val isDuplicate = localLogs.any { localLog ->
                                localLog.uid == remoteLog.uid &&
                                Math.abs(localLog.timestamp - remoteLog.timestamp) < 2000 &&
                                localLog.type == remoteLog.type &&
                                localLog.schoolId?.trim()?.uppercase() == activeId.trim().uppercase()
                            }
                            if (!isDuplicate) {
                                repository.insertLog(
                                    remoteLog.copy(
                                        id = 0,
                                        schoolId = activeId,
                                        schoolName = schoolName,
                                        synced = true
                                    )
                                )
                                addedCount++
                            }
                        }
                        if (addedCount > 0) {
                            Log.d("AttendanceViewModel", "Auto-sync (pull) logs from Supabase: added $addedCount new logs.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Auto-sync (pull) logs from Supabase failed", e)
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Auto-sync logs with Supabase failed", e)
            }
        }
    }

    suspend fun autoSyncAttendees() {
        val activeId = getActiveSchoolId()
        if (activeId == "SCH-DEFAULT") {
            Log.d("AttendanceViewModel", "Skipping autoSyncAttendees for default school ID SCH-DEFAULT")
            return
        }
        val activeSchool = getSchoolConfigs().find { it.id == activeId }
        
        if (isSupabaseEnabled()) {
            val sUrl = getSupabaseUrl()
            val sKey = getSupabaseAnonKey()
            if (sUrl.isBlank() || sKey.isBlank()) return
            try {
                // 1. PUSH attendees
                val currentAttendees = repository.allAttendees.first()
                val unsyncedAttendees = currentAttendees.filter { !it.synced && it.schoolId == activeId }
                if (unsyncedAttendees.isNotEmpty()) {
                    val success = com.example.utils.SupabaseSyncHelper.syncAttendeesToSupabase(sUrl, sKey, unsyncedAttendees)
                    if (success) {
                        unsyncedAttendees.forEach { attendee ->
                            repository.insertAttendee(attendee.copy(synced = true))
                        }
                        Log.d("AttendanceViewModel", "Auto-sync (push) attendees to Supabase successful: ${unsyncedAttendees.size} attendees.")
                    }
                }

                // 2. PULL attendees
                try {
                    val remoteAttendees = com.example.utils.SupabaseSyncHelper.importAttendeesFromSupabase(sUrl, sKey, activeId)
                    if (remoteAttendees.isNotEmpty()) {
                        repository.deleteSyncedAttendeesBySchool(activeId)
                        val schoolName = activeSchool?.name ?: getSchoolName()
                        remoteAttendees.forEach { remoteAttendee ->
                            repository.insertAttendee(
                                remoteAttendee.copy(
                                    schoolId = activeId,
                                    schoolName = schoolName,
                                    synced = true
                                )
                            )
                        }
                        Log.d("AttendanceViewModel", "Auto-sync (pull) attendees from Supabase: downloaded ${remoteAttendees.size} attendees.")
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Auto-sync (pull) attendees from Supabase failed", e)
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Auto-sync attendees with Supabase failed", e)
            }
        }
    }

    // UI States from Repository
    val attendees: StateFlow<List<Attendee>> = combine(repository.allAttendees, activeSchoolId) { list, schoolId ->
        list.filter { it.schoolId?.trim()?.uppercase() == schoolId.trim().uppercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<AttendanceLog>> = combine(repository.allLogs, activeSchoolId) { list, schoolId ->
        list.filter { it.schoolId?.trim()?.uppercase() == schoolId.trim().uppercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<AttendanceSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult = _scanResult.asStateFlow()

    // Currently logged-in/selected attendee for "Self Attendance" mode
    private val _selectedAttendee = MutableStateFlow<Attendee?>(null)
    val selectedAttendee = _selectedAttendee.asStateFlow()

    fun selectAttendee(attendee: Attendee?) {
        _selectedAttendee.value = attendee
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    // Insert Attendees (Karyawan/Siswa)
    fun addAttendee(name: String, role: String, customUid: String?) {
        viewModelScope.launch {
            val finalUid = if (customUid.isNullOrBlank()) {
                "ID-${UUID.randomUUID().toString().take(6).uppercase()}"
            } else {
                customUid.trim().uppercase()
            }
            val activeId = getActiveSchoolId()
            val activeSchool = getSchoolConfigs().find { it.id == activeId }
            val schoolId = activeSchool?.id
            val schoolName = activeSchool?.name
            val attendee = Attendee(
                uid = finalUid,
                name = name.trim(),
                role = role.trim(),
                schoolId = schoolId,
                schoolName = schoolName
            )
            repository.insertAttendee(attendee)
            if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                autoSyncAttendees()
            }
        }
    }

    fun deleteAttendee(id: Int) {
        viewModelScope.launch {
            repository.deleteAttendee(id)
            if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                autoSyncAttendees()
            }
        }
    }

    // Insert Attendance Sessions (Locations/Classes)
    fun addSession(title: String) {
        viewModelScope.launch {
            val randomCode = "LOC-${(10000..99999).random()}"
            val session = AttendanceSession(code = randomCode, title = title.trim())
            repository.insertSession(session)
        }
    }

    fun deleteSession(id: Int) {
        viewModelScope.launch {
            repository.deleteSession(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    // Import from manual CSV text / CSV file content
    fun importFromCsvText(csvText: String, onFinished: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val list = parseCsvToAttendees(csvText)
                if (list.isNotEmpty()) {
                    val activeId = getActiveSchoolId()
                    val activeSchool = getSchoolConfigs().find { it.id == activeId }
                    val schoolId = activeSchool?.id
                    val schoolName = activeSchool?.name
                    list.forEach { 
                        repository.insertAttendee(it.copy(schoolId = schoolId, schoolName = schoolName, synced = false)) 
                    }
                    repository.removeDuplicateAttendees()
                    if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                        autoSyncAttendees()
                    }
                    onFinished(true, list.size)
                } else {
                    onFinished(false, 0)
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error importing CSV text", e)
                onFinished(false, 0)
            }
        }
    }

    // Import from Google Spreadsheet URL (Automatic CSV conversion and download)
    fun importFromSpreadsheetUrl(spreadsheetUrl: String, onFinished: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Convert Spreadsheet URL to CSV URL
                val csvUrl = convertSpreadsheetUrlToCsvUrl(spreadsheetUrl)
                Log.d("AttendanceViewModel", "Downloading spreadsheet from URL: $csvUrl")
                
                // 2. Fetch CSV contents using HttpURLConnection
                val url = java.net.URL(csvUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.useCaches = false
                
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val csvText = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // 3. Parse CSV on the Main thread/withContext
                    withContext(Dispatchers.Main) {
                        try {
                            val list = parseCsvToAttendees(csvText)
                            if (list.isNotEmpty()) {
                                val activeId = getActiveSchoolId()
                                val activeSchool = getSchoolConfigs().find { it.id == activeId }
                                val schoolId = activeSchool?.id
                                val schoolName = activeSchool?.name
                                list.forEach { 
                                    repository.insertAttendee(it.copy(schoolId = schoolId, schoolName = schoolName, synced = false)) 
                                }
                                repository.removeDuplicateAttendees()
                                if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                                    autoSyncAttendees()
                                }
                                onFinished(true, list.size, "Berhasil mengimpor ${list.size} siswa dari Google Spreadsheet!")
                            } else {
                                onFinished(false, 0, "Gagal mengimpor. Tidak menemukan baris data siswa yang valid di spreadsheet.")
                            }
                        } catch (e: Exception) {
                            Log.e("AttendanceViewModel", "Error parsing CSV from URL", e)
                            onFinished(false, 0, "Format data spreadsheet tidak dikenali. Pastikan kolom minimal memiliki header 'Nama Siswa'.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onFinished(false, 0, "Gagal mengunduh spreadsheet. Kode HTTP: $responseCode. Pastikan link diatur agar dapat diakses publik (Siapa saja yang memiliki link dapat melihat).")
                    }
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error downloading spreadsheet from URL", e)
                withContext(Dispatchers.Main) {
                    onFinished(false, 0, "Kesalahan koneksi internet: ${e.localizedMessage ?: "Tidak dapat menghubungi link"}")
                }
            }
        }
    }

    private fun convertSpreadsheetUrlToCsvUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.contains("docs.google.com/spreadsheets")) {
            val segments = trimmed.split("/")
            val dIndex = segments.indexOf("d")
            if (dIndex != -1 && dIndex + 1 < segments.size) {
                val spreadsheetId = segments[dIndex + 1]
                var gid: String? = null
                if (trimmed.contains("gid=")) {
                    val gidMatch = Regex("gid=(\\d+)").find(trimmed)
                    gid = gidMatch?.groupValues?.getOrNull(1)
                }
                var csvUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv"
                if (gid != null) {
                    csvUrl += "&gid=$gid"
                }
                return csvUrl
            }
        }
        return trimmed
    }

    private fun saveOfflineCache(url: String, data: String) {
        sharedPrefs.edit()
            .putString("offline_cache_data_$url", data)
            .putLong("offline_cache_time_$url", System.currentTimeMillis())
            .apply()
    }

    private fun getOfflineCache(url: String): Pair<String?, Long> {
        val data = sharedPrefs.getString("offline_cache_data_$url", null)
        val time = sharedPrefs.getLong("offline_cache_time_$url", 0L)
        return Pair(data, time)
    }

    // Import attendees from Supabase
    fun importFromSupabase(onFinished: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            val sUrl = getSupabaseUrl()
            val sKey = getSupabaseAnonKey()
            if (sUrl.isBlank() || sKey.isBlank()) {
                onFinished(false, 0, "Konfigurasi Supabase (URL / API Key) belum diisi di tab Supabase.")
                return@launch
            }
            try {
                val activeId = getActiveSchoolId()
                val activeSchool = getSchoolConfigs().find { it.id == activeId }
                val schoolName = activeSchool?.name ?: getSchoolName()
                val remoteAttendees = com.example.utils.SupabaseSyncHelper.importAttendeesFromSupabase(sUrl, sKey, activeId)
                if (remoteAttendees.isNotEmpty()) {
                    repository.deleteSyncedAttendeesBySchool(activeId)
                    remoteAttendees.forEach { remoteAttendee ->
                        repository.insertAttendee(
                            remoteAttendee.copy(
                                id = 0,
                                schoolId = activeId,
                                schoolName = schoolName,
                                synced = true
                            )
                        )
                    }
                    repository.removeDuplicateAttendees()
                    onFinished(true, remoteAttendees.size, "Berhasil mengimpor ${remoteAttendees.size} siswa dari Supabase!")
                } else {
                    onFinished(true, 0, "Koneksi berhasil, tetapi tidak ditemukan data siswa untuk sekolah ini di Supabase.")
                }
            } catch (e: Exception) {
                onFinished(false, 0, "Gagal mengimpor dari Supabase: ${e.localizedMessage}")
            }
        }
    }

    // Sync all current attendance logs and attendees to Supabase - Manual Sync
    fun syncAllData(onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isManualSyncing.value = true
            val activeId = getActiveSchoolId()
            val activeSchool = getSchoolConfigs().find { it.id == activeId }

            val sUrl = getSupabaseUrl()
            val sKey = getSupabaseAnonKey()
            if (sUrl.isBlank() || sKey.isBlank()) {
                _isManualSyncing.value = false
                onFinished(false, "Konfigurasi Supabase (URL / API Key) belum diisi di tab Supabase.")
                return@launch
            }
            try {
                // 1. Sync Attendees
                var attendeesPushed = 0
                var attendeesPulled = 0

                // Push
                val currentAttendees = repository.allAttendees.first()
                val unsyncedAttendees = currentAttendees.filter { !it.synced && it.schoolId == activeId }
                if (unsyncedAttendees.isNotEmpty()) {
                    val success = com.example.utils.SupabaseSyncHelper.syncAttendeesToSupabase(sUrl, sKey, unsyncedAttendees)
                    if (success) {
                        unsyncedAttendees.forEach { attendee ->
                            repository.insertAttendee(attendee.copy(synced = true))
                        }
                        attendeesPushed = unsyncedAttendees.size
                    }
                }

                // Pull
                try {
                    val remoteAttendees = com.example.utils.SupabaseSyncHelper.importAttendeesFromSupabase(sUrl, sKey, activeId)
                    if (remoteAttendees.isNotEmpty()) {
                        repository.deleteSyncedAttendeesBySchool(activeId)
                        val schoolName = activeSchool?.name ?: getSchoolName()
                        remoteAttendees.forEach { remoteAttendee ->
                            repository.insertAttendee(
                                remoteAttendee.copy(
                                    id = 0,
                                    schoolId = activeId,
                                    schoolName = schoolName,
                                    synced = true
                                )
                            )
                        }
                        repository.removeDuplicateAttendees()
                        attendeesPulled = remoteAttendees.size
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Error pulling attendees from Supabase during manual sync", e)
                }

                // 2. Sync Logs (Push & Pull)
                var logsPushed = 0
                var logsPulled = 0
                val unsyncedLogs = repository.allLogs.first().filter { !it.synced && !isHolidayTimestamp(it.timestamp) }
                if (unsyncedLogs.isNotEmpty()) {
                    val success = com.example.utils.SupabaseSyncHelper.syncLogsToSupabase(sUrl, sKey, unsyncedLogs, getDeviceId())
                    if (success) {
                        logsPushed = unsyncedLogs.size
                        repository.markAllLogsAsSynced()
                    }
                }

                try {
                    val remoteLogs = com.example.utils.SupabaseSyncHelper.importLogsFromSupabase(sUrl, sKey, activeId)
                    if (remoteLogs.isNotEmpty()) {
                        val localLogs = repository.allLogs.first()
                        val schoolName = activeSchool?.name ?: getSchoolName()
                        remoteLogs.forEach { remoteLog ->
                            val isDuplicate = localLogs.any { localLog ->
                                localLog.uid == remoteLog.uid &&
                                Math.abs(localLog.timestamp - remoteLog.timestamp) < 2000 &&
                                localLog.type == remoteLog.type &&
                                localLog.schoolId?.trim()?.uppercase() == activeId.trim().uppercase()
                            }
                            if (!isDuplicate) {
                                repository.insertLog(
                                    remoteLog.copy(
                                        id = 0,
                                        schoolId = activeId,
                                        schoolName = schoolName,
                                        synced = true
                                    )
                                )
                                logsPulled++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceViewModel", "Error pulling logs from Supabase during manual sync", e)
                }

                updateLastSyncTime()
                _isManualSyncing.value = false

                val msg = StringBuilder().apply {
                    append("Sinkronisasi Supabase Berhasil!\n")
                    append("• Siswa Baru Terkirim: $attendeesPushed, Diterima: $attendeesPulled\n")
                    append("• Absensi Baru Terkirim: $logsPushed, Diterima: $logsPulled")
                }.toString()

                onFinished(true, msg)
            } catch (e: Exception) {
                _isManualSyncing.value = false
                onFinished(false, "Terjadi kesalahan saat sinkronisasi Supabase: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Launch the Google Play Services Code Scanner to perform QR scanning.
     * @param type "MASUK" or "PULANG"
     * @param mode "TERMINAL" (scan attendee QRs) or "MANDIRI" (employee scans location QR)
     */
    fun startQrScanner(context: Context, type: String, mode: String = "TERMINAL") {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null) {
                    vibrateDevice(context)
                    processScannedCode(rawValue, type, mode)
                } else {
                    playSoundNotification(false)
                    speakText("Pemindaian gagal.")
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Pemindaian Gagal",
                        message = "QR Code kosong atau tidak terbaca."
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceViewModel", "Scan failed", e)
                playSoundNotification(false)
                speakText("Pemindaian dibatalkan.")
                _scanResult.value = ScanResultState(
                    success = false,
                    title = "Batal / Gagal",
                    message = "Pemindaian dibatalkan atau terjadi masalah koneksi Google Play Services."
                )
            }
    }

    /**
     * Entry point for custom in-app scanner to process a scanned QR code.
     */
    fun handleScannedCode(context: Context, rawValue: String, type: String, mode: String = "TERMINAL") {
        vibrateDevice(context)
        processScannedCode(rawValue, type, mode)
    }

    private fun processScannedCode(rawValue: String, type: String, mode: String) {
        viewModelScope.launch {
            if (mode == "TERMINAL") {
                // TERMINAL MODE: The scanner is at the office door. It scans the Attendee's QR code (rawValue is Attendee UID).
                val attendee = repository.getAttendeeByUid(rawValue)
                if (attendee != null) {
                    // Check lateness
                    val status = calculateAttendanceStatus(type)
                    
                    val log = AttendanceLog(
                        uid = attendee.uid,
                        name = attendee.name,
                        role = attendee.role,
                        type = type,
                        status = status,
                        schoolId = attendee.schoolId,
                        schoolName = attendee.schoolName
                    )
                    repository.insertLog(log)
                    
                    if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                        viewModelScope.launch {
                            autoSyncLogs()
                        }
                    }
                    
                    _scanResult.value = ScanResultState(
                        success = true,
                        title = "Absen ${type.lowercase().replaceFirstChar { it.uppercase() }} Berhasil!",
                        message = "Nama: ${attendee.name}\nJabatan/Role: ${attendee.role}\nStatus: $status"
                    )
                    playSoundNotification(true)
                    speakText("Absen ${if (type == "MASUK") "masuk" else "pulang"} berhasil. Halo, ${attendee.name}.")
                } else {
                    playSoundNotification(false)
                    speakText("ID tidak dikenali.")
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "ID Tidak Dikenali",
                        message = "Kode QR (${rawValue}) tidak terdaftar sebagai Siswa/Karyawan."
                    )
                }
            } else {
                // MANDIRI MODE: The scanner is on the Attendee's own phone. They scan a Location/Session QR code (rawValue is Location Code).
                val session = repository.getSessionByCode(rawValue)
                val attendee = _selectedAttendee.value

                if (attendee == null) {
                    playSoundNotification(false)
                    speakText("Pilih profil dahulu.")
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Pilih Profil Dahulu",
                        message = "Pilih profil Siswa terlebih dahulu sebelum melakukan Absensi Mandiri."
                    )
                } else if (session != null) {
                    val status = calculateAttendanceStatus(type)
                    val log = AttendanceLog(
                        uid = attendee.uid,
                        name = attendee.name,
                        role = attendee.role,
                        type = type,
                        status = status,
                        sessionName = session.title,
                        schoolId = attendee.schoolId,
                        schoolName = attendee.schoolName
                    )
                    repository.insertLog(log)

                    if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                        viewModelScope.launch {
                            autoSyncLogs()
                        }
                    }

                    _scanResult.value = ScanResultState(
                        success = true,
                        title = "Absen Mandiri Berhasil!",
                        message = "Profil: ${attendee.name}\nLokasi: ${session.title}\nStatus: $status"
                    )
                    playSoundNotification(true)
                    speakText("Absen mandiri berhasil. Halo, ${attendee.name}.")
                } else {
                    playSoundNotification(false)
                    speakText("Lokasi tidak valid.")
                    _scanResult.value = ScanResultState(
                        success = false,
                        title = "Lokasi Tidak Valid",
                        message = "Kode QR (${rawValue}) tidak cocok dengan lokasi/kelas absensi manapun."
                    )
                }
            }
        }
    }

    fun getJamMasukForClass(className: String): String {
        return sharedPrefs.getString("jam_masuk_${className.trim()}", "07:30") ?: "07:30"
    }

    fun saveJamMasukForClass(className: String, time: String) {
        sharedPrefs.edit().putString("jam_masuk_${className.trim()}", time.trim()).apply()
    }

    fun getJamPulangForClass(className: String): String {
        return sharedPrefs.getString("jam_pulang_${className.trim()}", "13:00") ?: "13:00"
    }

    fun saveJamPulangForClass(className: String, time: String) {
        sharedPrefs.edit().putString("jam_pulang_${className.trim()}", time.trim()).apply()
    }

    private fun calculateAttendanceStatus(type: String): String {
        val className = getSelectedClass()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        if (type == "MASUK") {
            val limitStr = getJamMasukForClass(className)
            val parts = limitStr.split(":")
            val limitHour = parts.getOrNull(0)?.toIntOrNull() ?: 7
            val limitMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30
            
            return if (currentHour > limitHour || (currentHour == limitHour && currentMinute > limitMinute)) {
                "Terlambat"
            } else {
                "Tepat Waktu"
            }
        } else { // PULANG
            val limitStr = getJamPulangForClass(className)
            val parts = limitStr.split(":")
            val limitHour = parts.getOrNull(0)?.toIntOrNull() ?: 13
            val limitMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            
            return if (currentHour < limitHour || (currentHour == limitHour && currentMinute < limitMinute)) {
                "Pulang Awal"
            } else {
                "Tepat Waktu"
            }
        }
    }

    fun recordManualAttendance(attendee: Attendee, type: String, status: String, sessionName: String? = null) {
        viewModelScope.launch {
            val log = AttendanceLog(
                uid = attendee.uid,
                name = attendee.name,
                role = attendee.role,
                type = type,
                status = status,
                sessionName = sessionName,
                schoolId = attendee.schoolId,
                schoolName = attendee.schoolName
            )
            repository.insertLog(log)
            if (isAutoSyncEnabled() && getSupabaseUrl().isNotBlank()) {
                autoSyncLogs()
            }
        }
    }

    fun backupDataToJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("backup_time", System.currentTimeMillis())

        // 1. Export attendees
        val attendeesList = attendees.value
        val attendeesArr = JSONArray()
        attendeesList.forEach { attendee ->
            val obj = JSONObject().apply {
                put("uid", attendee.uid)
                put("name", attendee.name)
                put("role", attendee.role)
                put("schoolId", attendee.schoolId ?: "")
                put("schoolName", attendee.schoolName ?: "")
            }
            attendeesArr.put(obj)
        }
        root.put("attendees", attendeesArr)

        // 2. Export logs
        val logsList = logs.value
        val logsArr = JSONArray()
        logsList.forEach { log ->
            val obj = JSONObject().apply {
                put("uid", log.uid)
                put("name", log.name)
                put("role", log.role)
                put("timestamp", log.timestamp)
                put("type", log.type)
                put("status", log.status)
                put("schoolId", log.schoolId ?: "")
                put("schoolName", log.schoolName ?: "")
            }
            logsArr.put(obj)
        }
        root.put("logs", logsArr)

        // 3. Export sessions
        val sessionsList = sessions.value
        val sessionsArr = JSONArray()
        sessionsList.forEach { session ->
            val obj = JSONObject().apply {
                put("code", session.code)
                put("title", session.title)
                put("createdAt", session.createdAt)
            }
            sessionsArr.put(obj)
        }
        root.put("sessions", sessionsArr)

        // 4. Export preferences
        val prefsObj = JSONObject()
        val allPrefs = sharedPrefs.all
        allPrefs.forEach { (key, value) ->
            if (value != null) {
                prefsObj.put(key, value)
            }
        }
        root.put("preferences", prefsObj)

        return root.toString(4) // 4 spaces indentation
    }

    suspend fun restoreDataFromJson(jsonStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonStr)
                
                // Clear existing database
                repository.clearAllAttendees()
                repository.clearAllLogs()
                repository.clearAllSessions()

                // 1. Restore attendees
                if (root.has("attendees")) {
                    val attendeesArr = root.getJSONArray("attendees")
                    for (i in 0 until attendeesArr.length()) {
                        val obj = attendeesArr.getJSONObject(i)
                        val attendee = Attendee(
                            uid = obj.getString("uid"),
                            name = obj.getString("name"),
                            role = obj.getString("role"),
                            schoolId = if (obj.has("schoolId")) obj.getString("schoolId").takeIf { it.isNotEmpty() } else null,
                            schoolName = if (obj.has("schoolName")) obj.getString("schoolName").takeIf { it.isNotEmpty() } else null
                        )
                        repository.insertAttendee(attendee)
                    }
                }

                // 2. Restore logs
                if (root.has("logs")) {
                    val logsArr = root.getJSONArray("logs")
                    for (i in 0 until logsArr.length()) {
                        val obj = logsArr.getJSONObject(i)
                        val log = AttendanceLog(
                            uid = obj.getString("uid"),
                            name = obj.getString("name"),
                            role = obj.getString("role"),
                            timestamp = obj.getLong("timestamp"),
                            type = obj.getString("type"),
                            status = obj.optString("status", ""),
                            schoolId = if (obj.has("schoolId")) obj.getString("schoolId").takeIf { it.isNotEmpty() } else null,
                            schoolName = if (obj.has("schoolName")) obj.getString("schoolName").takeIf { it.isNotEmpty() } else null
                        )
                        repository.insertLog(log)
                    }
                }

                // 3. Restore sessions
                if (root.has("sessions")) {
                    val sessionsArr = root.getJSONArray("sessions")
                    for (i in 0 until sessionsArr.length()) {
                        val obj = sessionsArr.getJSONObject(i)
                        val session = AttendanceSession(
                            code = obj.getString("code"),
                            title = obj.getString("title"),
                            createdAt = obj.getLong("createdAt")
                        )
                        repository.insertSession(session)
                    }
                }

                // 4. Restore preferences
                if (root.has("preferences")) {
                    val prefsObj = root.getJSONObject("preferences")
                    val editor = sharedPrefs.edit()
                    
                    editor.clear()
                    
                    val keys = prefsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = prefsObj.get(key)
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Double -> editor.putFloat(key, value.toFloat())
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                    
                    // Update state flows
                    _selectedClass.value = getSelectedClass()
                    _teacherName.value = getTeacherName()
                    _isDeviceBound.value = isDeviceBound()
                    _bindingDeviceName.value = getBindingDeviceName()
                }

                true
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error restoring backup", e)
                false
            }
        }
    }

    private fun parseCsvToAttendees(csvContent: String): List<Attendee> {
        val attendees = mutableListOf<Attendee>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return emptyList()

        val firstLine = lines.firstOrNull()?.trim() ?: return emptyList()
        val separator = if (firstLine.contains(";")) ";" else ","
        val headers = firstLine.split(separator).map { it.trim().lowercase() }

        var nameIndex = headers.indexOfFirst { it.contains("nama siswa") || it.contains("nama") || it.contains("name") }
        var roleIndex = headers.indexOfFirst { it.contains("kelas") || it.contains("role") || it.contains("jabatan") || it.contains("kategori") }
        var idIndex = headers.indexOfFirst { it.contains("nisn") || it.contains("nis") || it.contains("id") || it.contains("uid") || it.contains("nip") }

        if (nameIndex == -1) nameIndex = 1
        if (roleIndex == -1) roleIndex = 3
        if (idIndex == -1) idIndex = 2

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val tokens = splitCsvLine(line, separator)
            if (tokens.size <= nameIndex) continue

            val name = tokens.getOrNull(nameIndex)?.trim() ?: ""
            if (name.isEmpty()) continue

            val role = tokens.getOrNull(roleIndex)?.trim() ?: "Siswa"
            val rawUid = tokens.getOrNull(idIndex)?.trim() ?: ""
            val uid = if (rawUid.isNotBlank()) rawUid.uppercase() else "ID-${(100000..999999).random()}"

            attendees.add(Attendee(uid = uid, name = name, role = role))
        }
        return attendees
    }

    private fun splitCsvLine(line: String, separator: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = line.length
        while (i < len) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < len && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (line.startsWith(separator, i)) {
                    result.add(curVal.toString())
                    curVal = StringBuilder()
                    i += separator.length - 1
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- DEVELOPER BROADCAST MANAGEMENT ---

    private val _isDeveloperUnlocked = MutableStateFlow(false)
    val isDeveloperUnlocked: StateFlow<Boolean> = _isDeveloperUnlocked.asStateFlow()

    fun setDeveloperUnlocked(unlocked: Boolean) {
        _isDeveloperUnlocked.value = unlocked
    }

    fun getBroadcastHistory(): List<BroadcastMessage> {
        val jsonStr = sharedPrefs.getString("broadcast_history_list", "[]") ?: "[]"
        val list = mutableListOf<BroadcastMessage>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BroadcastMessage(
                        id = obj.optLong("id", 1),
                        title = obj.optString("title", ""),
                        message = obj.optString("message", ""),
                        driveLink = obj.optString("driveLink", ""),
                        type = obj.optString("type", "UPDATE"),
                        isActive = obj.optBoolean("isActive", false),
                        updatedId = obj.optLong("updatedId", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AttendanceViewModel", "Error parsing history", e)
        }
        return list.sortedByDescending { it.updatedId }
    }

    fun saveBroadcastToHistory(broadcast: BroadcastMessage) {
        val currentHistory = getBroadcastHistory().toMutableList()
        if (currentHistory.none { it.updatedId == broadcast.updatedId }) {
            currentHistory.add(broadcast)
            val limited = currentHistory.sortedByDescending { it.updatedId }.take(50)
            val jsonArray = org.json.JSONArray()
            for (item in limited) {
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("message", item.message)
                    put("driveLink", item.driveLink)
                    put("type", item.type)
                    put("isActive", item.isActive)
                    put("updatedId", item.updatedId)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("broadcast_history_list", jsonArray.toString()).apply()
        }
    }

    fun clearBroadcastHistory() {
        sharedPrefs.edit().remove("broadcast_history_list").apply()
    }

    private val _activeBroadcast = MutableStateFlow<BroadcastMessage?>(null)
    val activeBroadcast: StateFlow<BroadcastMessage?> = _activeBroadcast.asStateFlow()

    fun getLastDismissedBroadcastId(): Long {
        return sharedPrefs.getLong("last_dismissed_broadcast_id", 0L)
    }

    fun dismissBroadcast(updatedId: Long) {
        sharedPrefs.edit().putLong("last_dismissed_broadcast_id", updatedId).apply()
        _activeBroadcast.value = null
    }

    fun setLocalBroadcast(broadcast: BroadcastMessage?) {
        _activeBroadcast.value = broadcast
        if (broadcast != null) {
            saveBroadcastToHistory(broadcast)
        }
    }

    fun checkBroadcast() {
        if (!isSupabaseEnabled()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonObj = com.example.utils.SupabaseSyncHelper.fetchBroadcast(getSupabaseUrl(), getSupabaseAnonKey())
                if (jsonObj != null) {
                    val isActive = jsonObj.optBoolean("is_active", false)
                    val updatedId = jsonObj.optLong("updated_id", 0L)
                    val lastDismissed = getLastDismissedBroadcastId()

                    val broadcast = BroadcastMessage(
                        id = jsonObj.optLong("id", 1L),
                        title = jsonObj.optString("title", ""),
                        message = jsonObj.optString("message", ""),
                        driveLink = jsonObj.optString("drive_link", ""),
                        type = jsonObj.optString("type", "UPDATE"),
                        isActive = isActive,
                        updatedId = updatedId
                    )
                    
                    // Always save to history when fetched
                    saveBroadcastToHistory(broadcast)

                    if (isActive && updatedId > lastDismissed) {
                        _activeBroadcast.value = broadcast
                    }
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error checkBroadcast", e)
            }
        }
    }

    fun checkHariLibur() {
        if (!isSupabaseEnabled()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val holidays = com.example.utils.SupabaseSyncHelper.fetchHariLibur(getSupabaseUrl(), getSupabaseAnonKey())
                _holidayDates.value = holidays
                
                val formats = listOf(
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()),
                    java.text.SimpleDateFormat("d-M-yyyy", java.util.Locale.getDefault()),
                    java.text.SimpleDateFormat("yyyy-M-d", java.util.Locale.getDefault())
                )
                
                val todayStrs = formats.map { it.format(java.util.Date()) }
                val isHoliday = holidays.any { holiday ->
                    todayStrs.any { todayStr ->
                        holiday.trim() == todayStr.trim()
                    }
                }
                
                _isTodayHoliday.value = isHoliday
                Log.d("AttendanceViewModel", "Fetched holidays: $holidays. Is today holiday: $isHoliday")
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error checkHariLibur", e)
            }
        }
    }

    fun isHolidayTimestamp(timestamp: Long): Boolean {
        val holidays = _holidayDates.value
        if (holidays.isEmpty()) return false
        
        val formats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("d-M-yyyy", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-M-d", java.util.Locale.getDefault())
        )
        val date = java.util.Date(timestamp)
        val logDates = formats.map { it.format(date) }
        return holidays.any { holiday ->
            logDates.any { logDate ->
                holiday.trim() == logDate.trim()
            }
        }
    }

    fun pushBroadcastMessage(
        title: String,
        message: String,
        driveLink: String,
        type: String,
        isActive: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSupabaseEnabled()) {
            onError("Supabase belum diaktifkan di Pengaturan!")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newUpdatedId = System.currentTimeMillis()
                val success = com.example.utils.SupabaseSyncHelper.pushBroadcast(
                    getSupabaseUrl(),
                    getSupabaseAnonKey(),
                    title.trim(),
                    message.trim(),
                    driveLink.trim(),
                    type,
                    isActive,
                    newUpdatedId
                )
                withContext(Dispatchers.Main) {
                    if (success) {
                        val broadcast = BroadcastMessage(
                            id = 1,
                            title = title.trim(),
                            message = message.trim(),
                            driveLink = driveLink.trim(),
                            type = type,
                            isActive = isActive,
                            updatedId = newUpdatedId
                        )
                        saveBroadcastToHistory(broadcast)
                        onSuccess()
                        checkBroadcast()
                    } else {
                        onError("Gagal mengirim pesan broadcast ke server Supabase.")
                    }
                }
            } catch (e: Exception) {
                Log.e("AttendanceViewModel", "Error pushBroadcastMessage", e)
                withContext(Dispatchers.Main) {
                    onError("Terjadi kesalahan: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class AttendanceViewModelFactory(
    private val application: Application,
    private val repository: AttendanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
