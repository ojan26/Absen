package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.ui.SchoolConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AttendanceLog
import com.example.data.AttendanceSession
import com.example.data.Attendee
import com.example.ui.AttendanceViewModel
import com.example.ui.ScanResultState
import com.example.utils.QrHelper
import com.example.utils.GeminiHelper
import coil.compose.AsyncImage
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.text.SimpleDateFormat
import java.util.*

private val APPS_SCRIPT_TEMPLATE = """
// ================= CONFIGURATION =================
// 1. Spreadsheet ID khusus untuk Fitur Device Binding (Pendaftaran & Verifikasi Perangkat)
var BINDING_SPREADSHEET_ID = "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0";

// 2. Spreadsheet ID untuk Database Absensi (Daftar Siswa & Log Absensi)
// Kosongkan "" untuk menggunakan Spreadsheet aktif (tempat script dipasang), atau isi ID Spreadsheet eksternal lain.
var ATTENDANCE_SPREADSHEET_ID = ""; 
// =================================================

function getBindingSpreadsheet() {
  try {
    return SpreadsheetApp.openById(BINDING_SPREADSHEET_ID);
  } catch (e) {
    throw new Error("Gagal membuka Spreadsheet Device Binding: " + e.toString());
  }
}

function getAttendanceSpreadsheet() {
  if (ATTENDANCE_SPREADSHEET_ID && ATTENDANCE_SPREADSHEET_ID.trim() !== "") {
    try {
      return SpreadsheetApp.openById(ATTENDANCE_SPREADSHEET_ID.trim());
    } catch (e) {
      // Fallback
    }
  }
  return SpreadsheetApp.getActiveSpreadsheet();
}

function doGet(e) {
  // 1. PENDAFTARAN DEVICE ID (action=registerDevice)
  if (e && e.parameter && e.parameter.action === 'registerDevice') {
    var deviceId = String(e.parameter.deviceId || "").trim();
    var deviceName = String(e.parameter.deviceName || "Perangkat Absensi").trim();
    
    if (deviceId === "") {
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        message: "Device ID tidak boleh kosong!"
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    var spreadsheet = getBindingSpreadsheet();
    var tokenSheet = spreadsheet.getSheetByName("Tokens");
    if (!tokenSheet) {
      tokenSheet = spreadsheet.insertSheet("Tokens");
      tokenSheet.appendRow(["Token", "Device ID", "Nama Perangkat"]);
    }
    
    var data = tokenSheet.getDataRange().getValues();
    var foundIndex = -1;
    
    for (var i = 1; i < data.length; i++) {
      var rowDeviceId = String(data[i][1] || "").trim();
      if (rowDeviceId === deviceId) {
        foundIndex = i;
        break;
      }
    }
    
    if (foundIndex !== -1) {
      tokenSheet.getRange(foundIndex + 1, 3).setValue(deviceName);
      var currentToken = String(data[foundIndex][0] || "").trim();
      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        message: "Perangkat sudah terdaftar! " + (currentToken ? "Gunakan token: " + currentToken : "Silakan hubungi Admin untuk menginput token di Spreadsheet Device Binding.")
      })).setMimeType(ContentService.MimeType.JSON);
    } else {
      tokenSheet.appendRow(["", deviceId, deviceName]);
      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        message: "Perangkat berhasil terdaftar di Spreadsheet Device Binding! Silakan masukkan Token di kolom A pada baris baru."
      })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  // 2. VERIFIKASI DEVICE BINDING TOKEN (action=verifyToken)
  if (e && e.parameter && e.parameter.action === 'verifyToken') {
    var token = String(e.parameter.token || "").trim();
    var deviceId = String(e.parameter.deviceId || "").trim();
    
    var spreadsheet = getBindingSpreadsheet();
    var tokenSheet = spreadsheet.getSheetByName("Tokens");
    if (!tokenSheet) {
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        message: "Sheet 'Tokens' belum dibuat. Daftarkan perangkat Anda terlebih dahulu."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    var data = tokenSheet.getDataRange().getValues();
    
    for (var i = 1; i < data.length; i++) {
      var rowToken = String(data[i][0] || "").trim();
      var rowDeviceId = String(data[i][1] || "").trim();
      var rowDeviceName = String(data[i][2] || "Perangkat Absensi").trim();
      
      if (rowToken === token) {
        if (rowDeviceId === "" || rowDeviceId === deviceId) {
          if (rowDeviceId === "") {
            tokenSheet.getRange(i + 1, 2).setValue(deviceId);
          }
          var response = {
            success: true,
            schoolName: spreadsheet.getName(),
            deviceName: rowDeviceName
          };
          return ContentService.createTextOutput(JSON.stringify(response)).setMimeType(ContentService.MimeType.JSON);
        }
      }
    }
    return ContentService.createTextOutput(JSON.stringify({
      success: false,
      message: "Token tidak valid atau sudah digunakan di perangkat lain!"
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // 3. IMPORT LOG ABSENSI (action=getLogs)
  if (e && e.parameter && e.parameter.action === 'getLogs') {
    try {
      var spreadsheet = getAttendanceSpreadsheet();
      var sheet = spreadsheet.getSheetByName("Kehadiran") || spreadsheet.getSheetByName("LogAbsensi");
      if (!sheet) {
        return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
      }
      var data = sheet.getDataRange().getValues();
      var headers = data[0];
      
      var idIndex = headers.indexOf("ID Log");
      var uidIndex = headers.indexOf("NISN (UID)") !== -1 ? headers.indexOf("NISN (UID)") : headers.indexOf("UID");
      var nameIndex = headers.indexOf("Nama Siswa") !== -1 ? headers.indexOf("Nama Siswa") : headers.indexOf("Nama");
      var roleIndex = headers.indexOf("Kelas") !== -1 ? headers.indexOf("Kelas") : headers.indexOf("Role / Kategori");
      var timestampIndex = headers.indexOf("Waktu Absen") !== -1 ? headers.indexOf("Waktu Absen") : headers.indexOf("Waktu Presensi");
      var typeIndex = headers.indexOf("Tipe (MASUK/PULANG)") !== -1 ? headers.indexOf("Tipe (MASUK/PULANG)") : headers.indexOf("Tipe");
      var statusIndex = headers.indexOf("Status (Hadir/Ijin/Sakit/Alpa)") !== -1 ? headers.indexOf("Status (Hadir/Ijin/Sakit/Alpa)") : headers.indexOf("Status");
      var sessionIndex = headers.indexOf("Nama Sesi / Lokasi QR") !== -1 ? headers.indexOf("Nama Sesi / Lokasi QR") : headers.indexOf("Sesi / Lokasi");
      var schoolIdIndex = headers.indexOf("NPSN Sekolah") !== -1 ? headers.indexOf("NPSN Sekolah") : headers.indexOf("NPSN");
      var schoolNameIndex = headers.indexOf("Nama Sekolah");
      
      if (idIndex === -1) idIndex = 0;
      if (uidIndex === -1) uidIndex = 1;
      if (nameIndex === -1) nameIndex = 2;
      if (roleIndex === -1) roleIndex = 3;
      if (timestampIndex === -1) timestampIndex = 4;
      if (typeIndex === -1) typeIndex = 5;
      if (statusIndex === -1) statusIndex = 6;
      if (sessionIndex === -1) sessionIndex = 7;
      
      var result = [];
      for (var i = 1; i < data.length; i++) {
        var row = data[i];
        var logId = row[idIndex].toString().trim();
        var uid = row[uidIndex].toString().trim();
        var name = row[nameIndex].toString().trim();
        var role = row[roleIndex].toString().trim();
        var rawTimestamp = row[timestampIndex].toString().trim();
        var type = row[typeIndex] ? row[typeIndex].toString().trim() : "MASUK";
        var status = row[statusIndex] ? row[statusIndex].toString().trim() : "HADIR";
        var sessionName = row[sessionIndex] ? row[sessionIndex].toString().trim() : "Umum";
        var schoolId = schoolIdIndex !== -1 ? row[schoolIdIndex].toString().trim() : "";
        var schoolName = schoolNameIndex !== -1 ? row[schoolNameIndex].toString().trim() : "";
        
        if (uid !== "") {
          result.push({
            id: logId,
            uid: uid,
            name: name,
            role: role,
            timestamp: rawTimestamp,
            type: type,
            status: status,
            sessionName: sessionName,
            schoolId: schoolId,
            schoolName: schoolName
          });
        }
      }
      return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
    } catch (err) {
      return ContentService.createTextOutput(JSON.stringify({ error: err.toString() })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  // 4. DEFAULT: IMPORT DATA SISWA
  try {
    var spreadsheet = getAttendanceSpreadsheet();
    var sheet = spreadsheet.getSheetByName("Siswa") || spreadsheet.getSheets()[0];
    if (!sheet) {
      return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
    }
    var data = sheet.getDataRange().getValues();
    var headers = data[0];
    var list = [];
    
    var filterSchoolId = (e && e.parameter && e.parameter.schoolId) ? String(e.parameter.schoolId).trim() : "";
    
    var colUid = headers.indexOf("ID / UID / NISN") !== -1 ? headers.indexOf("ID / UID / NISN") :
                 (headers.indexOf("NISN (UID)") !== -1 ? headers.indexOf("NISN (UID)") :
                 (headers.indexOf("NISN") !== -1 ? headers.indexOf("NISN") :
                 (headers.indexOf("uid") !== -1 ? headers.indexOf("uid") : 0)));
                 
    var colName = headers.indexOf("Nama") !== -1 ? headers.indexOf("Nama") :
                  (headers.indexOf("Nama Siswa") !== -1 ? headers.indexOf("Nama Siswa") :
                  (headers.indexOf("name") !== -1 ? headers.indexOf("name") : 1));
                  
    var colRole = headers.indexOf("Kelas / Peran / Jabatan") !== -1 ? headers.indexOf("Kelas / Peran / Jabatan") :
                  (headers.indexOf("Kelas") !== -1 ? headers.indexOf("Kelas") :
                  (headers.indexOf("role") !== -1 ? headers.indexOf("role") : 2));
                  
    var colSchoolId = headers.indexOf("NPSN Sekolah") !== -1 ? headers.indexOf("NPSN Sekolah") :
                      (headers.indexOf("NPSN") !== -1 ? headers.indexOf("NPSN") :
                      (headers.indexOf("schoolId") !== -1 ? headers.indexOf("schoolId") : -1));

    for (var i = 1; i < data.length; i++) {
      var row = data[i];
      var name = row[colName] ? row[colName].toString().trim() : "";
      if (name === "") continue;
      
      var uid = row[colUid] ? row[colUid].toString().trim() : "";
      var role = row[colRole] ? row[colRole].toString().trim() : "Siswa";
      var schoolId = (colSchoolId !== -1 && row[colSchoolId]) ? row[colSchoolId].toString().trim() : "";
      
      if (filterSchoolId !== "" && schoolId !== "" && schoolId !== filterSchoolId) {
        continue;
      }
      
      list.push({
        uid: uid,
        name: name,
        role: role,
        schoolId: schoolId
      });
    }
    return ContentService.createTextOutput(JSON.stringify(list)).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ error: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doPost(e) {
  try {
    var ss = getAttendanceSpreadsheet();
    
    // Check if syncSiswa action is specified
    if (e && e.parameter && e.parameter.action === 'syncSiswa') {
      var sheet = ss.getSheetByName("Siswa");
      if (!sheet) {
        sheet = ss.insertSheet("Siswa");
        sheet.appendRow(["ID / UID / NISN", "Nama", "Kelas / Peran / Jabatan", "NPSN Sekolah"]);
      }
      var data = sheet.getDataRange().getValues();
      var headers = data[0];
      
      var colUid = headers.indexOf("ID / UID / NISN") !== -1 ? headers.indexOf("ID / UID / NISN") :
                   (headers.indexOf("NISN (UID)") !== -1 ? headers.indexOf("NISN (UID)") :
                   (headers.indexOf("NISN") !== -1 ? headers.indexOf("NISN") :
                   (headers.indexOf("uid") !== -1 ? headers.indexOf("uid") : 0)));
                   
      var colName = headers.indexOf("Nama") !== -1 ? headers.indexOf("Nama") :
                    (headers.indexOf("Nama Siswa") !== -1 ? headers.indexOf("Nama Siswa") :
                    (headers.indexOf("name") !== -1 ? headers.indexOf("name") : 1));
                    
      var colRole = headers.indexOf("Kelas / Peran / Jabatan") !== -1 ? headers.indexOf("Kelas / Peran / Jabatan") :
                    (headers.indexOf("Kelas") !== -1 ? headers.indexOf("Kelas") :
                    (headers.indexOf("role") !== -1 ? headers.indexOf("role") : 2));
                    
      var colSchoolId = headers.indexOf("NPSN Sekolah") !== -1 ? headers.indexOf("NPSN Sekolah") :
                        (headers.indexOf("NPSN") !== -1 ? headers.indexOf("NPSN") :
                        (headers.indexOf("schoolId") !== -1 ? headers.indexOf("schoolId") : -1));
      
      if (colSchoolId === -1) {
        colSchoolId = headers.length;
        sheet.getRange(1, colSchoolId + 1).setValue("NPSN Sekolah");
        headers.push("NPSN Sekolah");
      }

      var existingMap = {};
      for (var j = 1; j < data.length; j++) {
        var existingUid = data[j][colUid] ? data[j][colUid].toString().trim() : "";
        if (existingUid !== "") {
          existingMap[existingUid] = j + 1;
        }
      }
      
      var jsonString = e.postData.contents;
      var students = JSON.parse(jsonString);
      if (!Array.isArray(students)) {
        students = [students];
      }
      
      var added = 0;
      var updated = 0;
      for (var i = 0; i < students.length; i++) {
        var student = students[i];
        var uid = student.uid ? student.uid.toString().trim() : "";
        var name = student.name ? student.name.toString().trim() : "";
        var role = student.role ? student.role.toString().trim() : "Siswa";
        var schoolId = student.schoolId ? student.schoolId.toString().trim() : "";
        
        if (uid !== "") {
          if (existingMap[uid]) {
            var rowNum = existingMap[uid];
            sheet.getRange(rowNum, colName + 1).setValue(name);
            sheet.getRange(rowNum, colRole + 1).setValue(role);
            if (colSchoolId !== -1) {
              sheet.getRange(rowNum, colSchoolId + 1).setValue(schoolId);
            }
            updated++;
          } else {
            var newRow = new Array(headers.length).fill("");
            if (colUid !== -1) newRow[colUid] = uid;
            if (colName !== -1) newRow[colName] = name;
            if (colRole !== -1) newRow[colRole] = role;
            if (colSchoolId !== -1) newRow[colSchoolId] = schoolId;
            sheet.appendRow(newRow);
            added++;
          }
        }
      }
      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        message: "Sinkronisasi siswa berhasil. Ditambahkan: " + added + ", Diperbarui: " + updated,
        added: added,
        updated: updated
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var sheet = ss.getSheetByName("Kehadiran") || ss.getSheetByName("LogAbsensi");
    
    if (!sheet) {
      sheet = ss.insertSheet("Kehadiran");
      sheet.appendRow([
        "ID Log", 
        "NISN (UID)", 
        "Nama Siswa", 
        "Kelas", 
        "Waktu Absen", 
        "Tipe (MASUK/PULANG)", 
        "Status (Hadir/Ijin/Sakit/Alpa)", 
        "Nama Sesi / Lokasi QR", 
        "NPSN Sekolah", 
        "Nama Sekolah"
      ]);
    }
    
    var jsonString = e.postData.contents;
    var logs = JSON.parse(jsonString);
    
    if (!Array.isArray(logs)) {
      logs = [logs];
    }
    
    var existingIds = {};
    if (sheet.getLastRow() > 1) {
      var idValues = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
      for (var j = 0; j < idValues.length; j++) {
        existingIds[idValues[j][0].toString()] = true;
      }
    }
    
    var headers = sheet.getDataRange().getValues()[0];
    
    var idIndex = headers.indexOf("ID Log");
    var uidIndex = headers.indexOf("NISN (UID)") !== -1 ? headers.indexOf("NISN (UID)") : headers.indexOf("UID");
    var nameIndex = headers.indexOf("Nama Siswa") !== -1 ? headers.indexOf("Nama Siswa") : headers.indexOf("Nama");
    var roleIndex = headers.indexOf("Kelas") !== -1 ? headers.indexOf("Kelas") : headers.indexOf("Role / Kategori");
    var timestampIndex = headers.indexOf("Waktu Absen") !== -1 ? headers.indexOf("Waktu Absen") : headers.indexOf("Waktu Presensi");
    var typeIndex = headers.indexOf("Tipe (MASUK/PULANG)") !== -1 ? headers.indexOf("Tipe (MASUK/PULANG)") : headers.indexOf("Tipe");
    var statusIndex = headers.indexOf("Status (Hadir/Ijin/Sakit/Alpa)") !== -1 ? headers.indexOf("Status (Hadir/Ijin/Sakit/Alpa)") : headers.indexOf("Status");
    var sessionIndex = headers.indexOf("Nama Sesi / Lokasi QR") !== -1 ? headers.indexOf("Nama Sesi / Lokasi QR") : headers.indexOf("Sesi / Lokasi");
    var schoolIdIndex = headers.indexOf("NPSN Sekolah") !== -1 ? headers.indexOf("NPSN Sekolah") : headers.indexOf("NPSN");
    var schoolNameIndex = headers.indexOf("Nama Sekolah");
    
    if (idIndex === -1) idIndex = 0;
    if (uidIndex === -1) uidIndex = 1;
    if (nameIndex === -1) nameIndex = 2;
    if (roleIndex === -1) roleIndex = 3;
    if (timestampIndex === -1) timestampIndex = 4;
    if (typeIndex === -1) typeIndex = 5;
    if (statusIndex === -1) statusIndex = 6;
    if (sessionIndex === -1) sessionIndex = 7;
    
    var addedCount = 0;
    for (var i = 0; i < logs.length; i++) {
      var log = logs[i];
      var logId = log.id ? log.id.toString() : "";
      
      if (logId !== "" && existingIds[logId]) {
        continue;
      }
      
      var row = new Array(headers.length).fill("");
      if (idIndex !== -1) row[idIndex] = logId;
      if (uidIndex !== -1) row[uidIndex] = log.uid || "";
      if (nameIndex !== -1) row[nameIndex] = log.name || "";
      if (roleIndex !== -1) row[roleIndex] = log.role || "";
      if (timestampIndex !== -1) row[timestampIndex] = log.timestamp || "";
      if (typeIndex !== -1) row[typeIndex] = log.type || "MASUK";
      if (statusIndex !== -1) row[statusIndex] = log.status || "HADIR";
      if (sessionIndex !== -1) row[sessionIndex] = log.sessionName || "Umum";
      if (schoolIdIndex !== -1) row[schoolIdIndex] = log.schoolId || "";
      if (schoolNameIndex !== -1) row[schoolNameIndex] = log.schoolName || "";
      
      sheet.appendRow(row);
      addedCount++;
    }
    
    return ContentService.createTextOutput(JSON.stringify({ 
      success: true, 
      message: addedCount + " log absensi berhasil ditambahkan ke Google Sheets.",
      added: addedCount
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ 
      success: false, 
      error: err.toString() 
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
""".trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }
    
    // Collecting flows
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val selectedAttendee by viewModel.selectedAttendee.collectAsStateWithLifecycle()
    val selectedClass by viewModel.selectedClass.collectAsStateWithLifecycle()
    val teacherName by viewModel.teacherName.collectAsStateWithLifecycle()
    val defaultCamera by viewModel.defaultCamera.collectAsStateWithLifecycle()
    val isDeviceBound by viewModel.isDeviceBound.collectAsStateWithLifecycle()
    val bindingDeviceName by viewModel.bindingDeviceName.collectAsStateWithLifecycle()
    val activeSchoolId by viewModel.activeSchoolId.collectAsStateWithLifecycle()
    val isManualSyncing by viewModel.isManualSyncing.collectAsStateWithLifecycle()
    val isSupabaseEnabled by viewModel.isSupabaseEnabledState.collectAsStateWithLifecycle()
    val activeBroadcast by viewModel.activeBroadcast.collectAsStateWithLifecycle()
    val isTodayHoliday by viewModel.isTodayHoliday.collectAsStateWithLifecycle()

    // Auto-pull/sync from Supabase on startup / activeSchoolId change to ensure statistics display latest Supabase database
    LaunchedEffect(activeSchoolId, isSupabaseEnabled) {
        if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank() && activeSchoolId != "SCH-DEFAULT") {
            viewModel.syncAllData { success, msg ->
                android.util.Log.d("MainScreen", "Initial database sync from Supabase: success=$success, msg=$msg")
            }
        }
    }

    val filteredAttendees = remember(attendees, selectedClass, activeSchoolId) {
        val schoolFiltered = attendees.filter { 
            it.schoolId == activeSchoolId
        }
        if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
            schoolFiltered
        } else {
            schoolFiltered.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
        }
    }

    val filteredLogs = remember(logs, selectedClass, activeSchoolId) {
        val schoolFiltered = logs.filter { 
            it.schoolId == activeSchoolId
        }
        if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
            schoolFiltered
        } else {
            schoolFiltered.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
        }
    }

    // Real-time Clock
    var currentClockTime by remember { mutableStateOf("") }
    var currentClockDate by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentClockTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            currentClockDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Dialog trigger states
    var showAddAttendeeDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var qrDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) } // pair of (title, encoded_content)
    var qrDialogAttendee by remember { mutableStateOf<Attendee?>(null) }
    var selectedDetailAttendee by remember { mutableStateOf<Attendee?>(null) }
    var showSelectProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var manualAttendanceAttendee by remember { mutableStateOf<Attendee?>(null) }

    // In-App Scanner Dialog states
    var showInAppScanner by remember { mutableStateOf(false) }
    var inAppScannerType by remember { mutableStateOf("MASUK") }
    var inAppScannerMode by remember { mutableStateOf("TERMINAL") }

    // School Identity states
    val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    val schoolAddress by viewModel.schoolAddress.collectAsStateWithLifecycle()
    val schoolLogoPath by viewModel.schoolLogoPath.collectAsStateWithLifecycle()


    var showManualSelectStudentDialog by remember { mutableStateOf(false) }
    var photoCaptureAttendee by remember { mutableStateOf<Attendee?>(null) }

    val attendeePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val attendee = photoCaptureAttendee
        if (bitmap != null && attendee != null) {
            try {
                val photoDir = java.io.File(context.filesDir, "photos")
                if (!photoDir.exists()) photoDir.mkdirs()
                val file = java.io.File(photoDir, "student_${attendee.uid}.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                viewModel.updateAttendeePhoto(attendee, file.absolutePath)
                Toast.makeText(context, "Foto ${attendee.name} berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        photoCaptureAttendee = null
    }

    val attendeePhotoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            attendeePhotoLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_SHORT).show()
            photoCaptureAttendee = null
        }
    }

    val calculatedAttendanceType = remember(attendees, logs) {
        val todaySdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val todayStr = todaySdf.format(java.util.Date())
        val presentUids = logs.filter { log ->
            log.type == "MASUK" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr
        }.map { it.uid }.toSet()
        val studentCount = attendees.size
        val presentCount = attendees.count { it.uid in presentUids }
        if (studentCount > 0 && presentCount == studentCount) "PULANG" else "MASUK"
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Main Bottom Bar Surface with Shadow
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    color = Color.Transparent,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab 0: Absen
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { currentTab = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Absen",
                                modifier = Modifier.size(28.dp),
                                tint = if (currentTab == 0) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Tab 1: Siswa/Anggota
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { currentTab = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Siswa",
                                modifier = Modifier.size(28.dp),
                                tint = if (currentTab == 1) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Spacer placeholder for the floating QRIS button in the middle
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        )

                        // Tab 2: Rekap
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { currentTab = 2 },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Rekap",
                                modifier = Modifier.size(28.dp),
                                tint = if (currentTab == 2) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Tab 3: Pengaturan
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { currentTab = 3 },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDeviceBound) Icons.Default.Settings else Icons.Default.Lock,
                                contentDescription = "Pengaturan",
                                modifier = Modifier.size(28.dp),
                                tint = if (currentTab == 3) Color.White else if (isDeviceBound) Color.White.copy(alpha = 0.6f) else Color(0xFFFCA5A5)
                            )
                        }
                    }
                }

                // Floating QRIS Action Button (completely unclipped and elevated)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                        .size(74.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (isTodayHoliday) {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF94A3B8), Color(0xFF64748B))
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                                )
                            }
                        )
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isTodayHoliday) {
                            inAppScannerType = calculatedAttendanceType
                            inAppScannerMode = "TERMINAL"
                            showInAppScanner = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Scan QRIS",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 0.dp,
                    bottom = innerPadding.calculateBottomPadding()
                )
                .background(Color(0xFFF8FAFC))
        ) {
            when (currentTab) {
                0 -> ScannerTab(
                    clockTime = currentClockTime,
                    clockDate = currentClockDate,
                    selectedAttendee = selectedAttendee,
                    schoolName = schoolName,
                    schoolAddress = schoolAddress,
                    schoolLogoPath = schoolLogoPath,
                    attendees = filteredAttendees,
                    logs = filteredLogs,
                    isDeviceBound = isDeviceBound,
                    bindingDeviceName = bindingDeviceName,
                    deviceId = viewModel.getDeviceId(),
                    appsScriptUrl = viewModel.getAppsScriptUrl(),
                    onBindClick = { url, tok, onFin -> viewModel.bindDevice(context, url, tok, onFin) },
                    onRegisterDeviceClick = { url, devName, onFin -> viewModel.registerDevice(context, url, devName, onFin) },
                    onUnbindClick = { viewModel.unbindDevice() },
                    onSelectProfileClick = { showSelectProfileDialog = true },
                    onScanClick = { type, mode ->
                        inAppScannerType = type
                        inAppScannerMode = mode
                        showInAppScanner = true
                    },
                    onManualAttendanceClick = {
                        showManualSelectStudentDialog = true
                    },
                    isManualSyncing = isManualSyncing,
                    onSyncClick = { onFinished -> viewModel.syncAllData(onFinished) },
                    isSupabaseEnabled = isSupabaseEnabled,
                    supabaseUrl = viewModel.getSupabaseUrl(),
                    activeBroadcast = activeBroadcast,
                    onDismissBroadcast = { id -> viewModel.dismissBroadcast(id) },
                    broadcastHistory = viewModel.getBroadcastHistory(),
                    isTodayHoliday = isTodayHoliday
                )
                1 -> AttendeesTab(
                    attendees = filteredAttendees,
                    onAddClick = { showAddAttendeeDialog = true },
                    onImportClick = { showImportDialog = true },
                    onDeleteClick = { viewModel.deleteAttendee(it) },
                    onShowQrClick = { name, uid ->
                        val found = filteredAttendees.find { it.uid == uid }
                        if (found != null) {
                            qrDialogAttendee = found
                        } else {
                            qrDialogContent = Pair("QR CODE SISWA\n$name ($uid)", uid)
                        }
                    },
                    onManualAttendanceClick = { attendee ->
                        manualAttendanceAttendee = attendee
                    },
                    onPhotoCaptureClick = { attendee ->
                        photoCaptureAttendee = attendee
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        )
                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            attendeePhotoLauncher.launch(null)
                        } else {
                            attendeePhotoPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    onPrintQrCardsClick = {
                        QrHelper.printStudentQrCards(context, filteredAttendees, schoolName, schoolAddress, schoolLogoPath)
                    },
                    onAttendeeClick = { selectedDetailAttendee = it },
                    activeSchoolId = activeSchoolId
                )
                2 -> RekapTab(
                    attendees = filteredAttendees,
                    logs = filteredLogs,
                    viewModel = viewModel
                )
                3 -> SettingsTab(
                    viewModel = viewModel
                )
            }

            // QR Overlay Result Notification (only if in-app scanner is not open)
            scanResult?.let { result ->
                if (!showInAppScanner) {
                    ScanResultOverlay(
                        result = result,
                        onDismiss = { viewModel.clearScanResult() }
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // In-App QR Scanner Dialog
    if (showInAppScanner) {
        InAppScannerDialog(
            type = inAppScannerType,
            mode = inAppScannerMode,
            defaultCamera = defaultCamera,
            scanResult = scanResult,
            onDismiss = { 
                showInAppScanner = false 
                viewModel.clearScanResult()
            },
            onCodeScanned = { rawValue ->
                viewModel.handleScannedCode(context, rawValue, inAppScannerType, inAppScannerMode)
            },
            onClearResult = { viewModel.clearScanResult() }
        )
    }

    // 1. Add Attendee Dialog
    if (showAddAttendeeDialog) {
        val activeSchoolId = viewModel.getActiveSchoolId()
        val activeSchool = viewModel.getSchoolConfigs().find { it.id == activeSchoolId }
        val activeSchoolName = activeSchool?.name ?: viewModel.schoolName.value

        AddAttendeeDialog(
            activeSchoolId = activeSchoolId,
            activeSchoolName = activeSchoolName,
            onDismiss = { showAddAttendeeDialog = false },
            onConfirm = { name, role, customUid ->
                viewModel.addAttendee(name, role, customUid)
                showAddAttendeeDialog = false
            },
            onImportClick = {
                showAddAttendeeDialog = false
                showImportDialog = true
            }
        )
    }

    // 1b. Import Dialog
    if (showImportDialog) {
        ImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false }
        )
    }

    // 2. Add Session Dialog
    if (showAddSessionDialog) {
        AddSessionDialog(
            onDismiss = { showAddSessionDialog = false },
            onConfirm = { title ->
                viewModel.addSession(title)
                showAddSessionDialog = false
            }
        )
    }

    // 3. QR Displayer Dialog
    qrDialogContent?.let { (title, contentText) ->
        QrDisplayerDialog(
            title = title,
            content = contentText,
            onDismiss = { qrDialogContent = null }
        )
    }

    // 3b. Student ID Card Dialog
    qrDialogAttendee?.let { attendee ->
        StudentCardDialog(
            attendee = attendee,
            viewModel = viewModel,
            onDismiss = { qrDialogAttendee = null }
        )
    }

    // 3c. Student Detail Dialog (Popup on clicking student's name)
    selectedDetailAttendee?.let { attendee ->
        AttendeeDetailDialog(
            attendee = attendee,
            onDismiss = { selectedDetailAttendee = null }
        )
    }

    // Manual Student Selection Dialog (for ScannerTab "Absen Manual" button)
    if (showManualSelectStudentDialog) {
        val todaySdf = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()) }
        val todayStr = remember { todaySdf.format(java.util.Date()) }

        val manualSelectAttendees = remember(filteredAttendees, filteredLogs, todayStr) {
            val studentsList = filteredAttendees.filter { it.role.equals("siswa", ignoreCase = true) }
            val targets = if (studentsList.isNotEmpty()) studentsList else filteredAttendees

            // Get UIDs of those who have taken "MASUK" attendance today
            val presentUids = filteredLogs.filter { log ->
                log.type == "MASUK" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr
            }.map { it.uid }.toSet()

            val sCount = targets.size
            val pCount = targets.count { it.uid in presentUids }
            
            // If all target students are present today (MASUK), then we are in PULANG mode.
            // Otherwise, we are in MASUK mode.
            val activeType = if (sCount > 0 && pCount == sCount) "PULANG" else "MASUK"

            if (activeType == "MASUK") {
                // Only show attendees who have NOT taken MASUK attendance today
                filteredAttendees.filter { it.uid !in presentUids }
            } else {
                // Get UIDs of those who have taken "PULANG" attendance today
                val pulangUids = filteredLogs.filter { log ->
                    log.type == "PULANG" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr
                }.map { it.uid }.toSet()
                // Only show attendees who have NOT taken PULANG attendance today
                filteredAttendees.filter { it.uid !in pulangUids }
            }
        }

        ManualSelectStudentDialog(
            attendees = manualSelectAttendees,
            onDismiss = { showManualSelectStudentDialog = false },
            onSelect = { student ->
                manualAttendanceAttendee = student
                showManualSelectStudentDialog = false
            }
        )
    }

    // 4. Profile Selector Dialog (for Self Mode)
    if (showSelectProfileDialog) {
        ProfileSelectorDialog(
            attendees = attendees,
            onDismiss = { showSelectProfileDialog = false },
            onSelect = {
                viewModel.selectAttendee(it)
                showSelectProfileDialog = false
            }
        )
    }

    // 5. Sync Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showInfoDialog) {
        InfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }

    // 6. Manual Attendance Dialog (Izin / Sakit / Hadir)
    manualAttendanceAttendee?.let { attendee ->
        ManualAttendanceDialog(
            attendee = attendee,
            onDismiss = { manualAttendanceAttendee = null },
            onConfirm = { type, status ->
                viewModel.recordManualAttendance(attendee, type, status)
                manualAttendanceAttendee = null
            }
        )
    }
}

// =====================================
// TAB 1: SCANNER TAB
// =====================================
@Composable
fun ScannerTab(
    clockTime: String,
    clockDate: String,
    selectedAttendee: Attendee?,
    schoolName: String,
    schoolAddress: String,
    schoolLogoPath: String,
    attendees: List<Attendee>,
    logs: List<AttendanceLog>,
    isDeviceBound: Boolean,
    bindingDeviceName: String,
    deviceId: String,
    appsScriptUrl: String,
    onBindClick: (webAppUrl: String, token: String, onFinished: (Boolean, String) -> Unit) -> Unit,
    onRegisterDeviceClick: (webAppUrl: String, deviceName: String, onFinished: (Boolean, String) -> Unit) -> Unit,
    onUnbindClick: () -> Unit,
    onSelectProfileClick: () -> Unit,
    onScanClick: (type: String, mode: String) -> Unit,
    onManualAttendanceClick: () -> Unit,
    isManualSyncing: Boolean,
    onSyncClick: (onFinished: (Boolean, String) -> Unit) -> Unit,
    isSupabaseEnabled: Boolean,
    supabaseUrl: String,
    activeBroadcast: com.example.ui.BroadcastMessage?,
    onDismissBroadcast: (Long) -> Unit,
    broadcastHistory: List<com.example.ui.BroadcastMessage> = emptyList(),
    isTodayHoliday: Boolean = false
) {
    var inputToken by remember { mutableStateOf("DEMO7HARI") }
    var inputDeviceName by remember { mutableStateOf("Perangkat Absensi " + deviceId.takeLast(4)) }
    var isBindingProcess by remember { mutableStateOf(false) }
    var isRegisteringProcess by remember { mutableStateOf(false) }
    var bindingError by remember { mutableStateOf<String?>(null) }
    var bindingSuccess by remember { mutableStateOf<String?>(null) }
    var showLocalInfoDialog by remember { mutableStateOf(false) }
    var showBroadcastHistoryDialog by remember { mutableStateOf(false) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }
    var showAiAnalysis by remember { mutableStateOf(false) }

    val androidContext = LocalContext.current
    val blogPrefs = remember { androidContext.getSharedPreferences("blog_prefs", android.content.Context.MODE_PRIVATE) }
    var newBlogPostNotification by remember { mutableStateOf<com.example.utils.BlogPost?>(null) }

    // RSS Blog Feed States
    var blogPosts by remember { mutableStateOf<List<com.example.utils.BlogPost>>(emptyList()) }
    var isBlogLoading by remember { mutableStateOf(false) }
    var blogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isBlogLoading = true
        blogError = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fetched = com.example.utils.BlogFeedHelper.fetchLatestPosts()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    blogPosts = fetched
                    isBlogLoading = false
                    if (fetched.isEmpty()) {
                        blogError = "Gagal memuat feed atau feed kosong."
                    } else {
                        // Check for new post notification
                        val latestPost = fetched.firstOrNull()
                        if (latestPost != null) {
                            val lastSeenLink = blogPrefs.getString("last_seen_post_link", null)
                            if (lastSeenLink == null || lastSeenLink != latestPost.link) {
                                newBlogPostNotification = latestPost
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    blogError = e.message ?: "Kesalahan koneksi feed."
                    isBlogLoading = false
                }
            }
        }
    }

    val schoolLogoBitmap = remember(schoolLogoPath) {
        if (!schoolLogoPath.isNullOrEmpty()) {
            try {
                android.graphics.BitmapFactory.decodeFile(schoolLogoPath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Dynamic attendance type calculation: MASUK or PULANG
    val students = remember(attendees) {
        attendees.filter { it.role.equals("siswa", ignoreCase = true) }
    }
    val targetAttendees = remember(students, attendees) {
        if (students.isNotEmpty()) students else attendees
    }

    val todaySdf = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()) }
    val todayStr = remember(clockDate) { todaySdf.format(java.util.Date()) }

    val todayLogs = remember(logs, clockDate) {
        logs.filter { log ->
            todaySdf.format(java.util.Date(log.timestamp)) == todayStr
        }.sortedByDescending { it.timestamp }
    }

    val ijinCountToday = remember(todayLogs) {
        todayLogs.filter { log ->
            log.type == "MASUK" && (log.status.equals("Ijin", ignoreCase = true) || log.status.equals("Izin", ignoreCase = true))
        }.map { it.uid }.distinct().size
    }

    val sakitCountToday = remember(todayLogs) {
        todayLogs.filter { log ->
            log.type == "MASUK" && log.status.equals("Sakit", ignoreCase = true)
        }.map { it.uid }.distinct().size
    }

    val presentUidsToday = remember(logs, clockDate) {
        logs.filter { log ->
            log.type == "MASUK" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr &&
            !log.status.equals("Ijin", ignoreCase = true) && !log.status.equals("Izin", ignoreCase = true) &&
            !log.status.equals("Sakit", ignoreCase = true)
        }.map { it.uid }.toSet()
    }

    val studentCount = targetAttendees.size
    val presentCount = targetAttendees.count { it.uid in presentUidsToday }

    val pulangUidsToday = remember(logs, clockDate) {
        logs.filter { log ->
            log.type == "PULANG" && todaySdf.format(java.util.Date(log.timestamp)) == todayStr
        }.map { it.uid }.toSet()
    }
    val pulangCount = targetAttendees.count { it.uid in pulangUidsToday }

    // If all target attendees are present or ijin or sakit today, toggle type to PULANG. Otherwise, MASUK.
    val attendanceType = if (studentCount > 0 && (presentCount + ijinCountToday + sakitCountToday) == studentCount) "PULANG" else "MASUK"
    val scanMode = "TERMINAL"

    val buttonColor = if (attendanceType == "MASUK") Color(0xFF10B981) else Color(0xFFEF4444)

    val tepatWaktuToday = remember(todayLogs) {
        todayLogs.count { it.status.equals("Tepat Waktu", ignoreCase = true) }
    }
    val terlambatToday = remember(todayLogs) {
        todayLogs.count { it.status.equals("Terlambat", ignoreCase = true) }
    }
    val pulangAwalToday = remember(todayLogs) {
        todayLogs.count { it.status.equals("Pulang Awal", ignoreCase = true) }
    }

    if (showLocalInfoDialog) {
        InfoDialog(
            onDismiss = { showLocalInfoDialog = false }
        )
    }

    if (showBroadcastHistoryDialog) {
        IncomingBroadcastHistoryDialog(
            historyList = broadcastHistory,
            onDismiss = { showBroadcastHistoryDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 1. HEADER (Gradient Background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                    ),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Top Bar: Logo, Title, Bell
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Shield Check Icon Box
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(
                                text = "X-Degan QR",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Sistem Absensi Pemindaian Real-time",
                                fontSize = 10.5.sp,
                                color = Color(0xFFDBEAFE)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Huge Clock
                Text(
                    text = clockTime.ifEmpty { "00:00:00" },
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                
                // Date Display
                Text(
                    text = clockDate.ifEmpty { "Memuat Tanggal..." },
                    fontSize = 12.sp,
                    color = Color(0xFFDBEAFE),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(y = (-4).dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 2. MAIN CONTENT (overlapping the header with negative offset)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-10).dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Holiday Banner
            if (isTodayHoliday) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF2F2)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFEE2E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Hari Libur",
                                tint = Color(0xFFDC2626)
                            )
                        }
                        Column {
                            Text(
                                text = "Hari Ini Libur 📅",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Tombol scan QR dan absen manual dinonaktifkan.",
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }

            // Developer Broadcast Banner
            activeBroadcast?.let { broadcast ->
                BroadcastBanner(
                    broadcast = broadcast,
                    onDismiss = { onDismissBroadcast(broadcast.updatedId) }
                )
            }

            // School Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Gloss reflection layer for premium look
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height * 0.45f)
                            quadraticTo(size.width * 0.5f, size.height * 0.55f, 0f, size.height * 0.45f)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1FAE5))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (schoolLogoBitmap != null) {
                                Image(
                                    bitmap = schoolLogoBitmap.asImageBitmap(),
                                    contentDescription = "Logo Sekolah",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Logo Sekolah",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = schoolName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = schoolAddress,
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Progress Section
            val progress = if (studentCount > 0) {
                if (attendanceType == "MASUK") presentCount.toFloat() / studentCount else pulangCount.toFloat() / studentCount
            } else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "AttendanceProgress"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // State for choosing statistics view source
                    var selectedSource by remember { mutableStateOf(if (isSupabaseEnabled && supabaseUrl.isNotBlank()) "SUPABASE" else "LOKAL") }

                    val currentGreeting = remember {
                        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        when (hour) {
                            in 5..10 -> "Selamat Pagi 🌅"
                            in 11..14 -> "Selamat Siang ☀️"
                            in 15..17 -> "Selamat Sore 🌇"
                            else -> "Selamat Malam 🌙"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Greeting and Active Real-time Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentGreeting,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            // Elegant double-circle blue real-time indicator
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6))
                                )
                            }
                        }

                        // BEAUTIFUL CUSTOM PILL SELECTOR (SEGMENTED SWITCHER)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // "Lokal" Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selectedSource == "LOKAL") Color.White else Color.Transparent)
                                    .clickable { selectedSource = "LOKAL" }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = if (selectedSource == "LOKAL") Color(0xFF1E293B) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Lokal",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSource == "LOKAL") Color(0xFF1E293B) else Color(0xFF64748B)
                                    )
                                }
                            }
                            
                            // "Cloud" Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selectedSource == "SUPABASE") Color(0xFFE0F2FE) else Color.Transparent)
                                    .clickable { selectedSource = "SUPABASE" }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = if (selectedSource == "SUPABASE") Color(0xFF0284C7) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Cloud",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSource == "SUPABASE") Color(0xFF0369A1) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                    
                    androidx.compose.material3.HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (selectedSource == "SUPABASE" && (!isSupabaseEnabled || supabaseUrl.isBlank())) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEE2E2)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Koneksi Supabase Belum Aktif",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Aktifkan dan lakukan konfigurasi kredensial database Supabase Anda melalui menu Pengaturan agar data statistik dapat disinkronkan secara real-time di Cloud.",
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = Color(0xFF7F1D1D),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val sudahCount = if (attendanceType == "MASUK") presentCount else pulangCount
                        val belumCount = (studentCount - sudahCount - ijinCountToday - sakitCountToday).coerceAtLeast(0)

                        // Box 1: Sudah Absen (Green Card - High Gloss Saturated Premium Glass)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), Color(0xFF10B981).copy(alpha = 0.2f))
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF34D399), // Bright Emerald Green
                                                Color(0xFF059669), // Rich Teal Green
                                                Color(0xFF064E3B)  // Deep Forest Green
                                             )
                                        )
                                    )
                            ) {
                                // Gloss Reflection Curved Highlight Layer
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width, size.height * 0.42f)
                                        quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Sudah Absen",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "$sudahCount",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Siswa",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFFD1FAE5),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Box 2: Belum Absen (Red Card - High Gloss Saturated Premium Glass)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), Color(0xFFEF4444).copy(alpha = 0.2f))
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFF87171), // Vibrant Red
                                                Color(0xFFDC2626), // Solid Crimson
                                                Color(0xFF7F1D1D)  // Dark Cherry Red
                                            )
                                        )
                                    )
                            ) {
                                // Gloss Reflection Curved Highlight Layer
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width, size.height * 0.42f)
                                        quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Belum Absen",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "$belumCount",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Siswa",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFFFEE2E2),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Second Row: Siswa Izin and Siswa Sakit (Light Blue and Light Amber Cards)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Box 3: Siswa Izin (Blue Card - High Gloss Saturated Premium Glass)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), Color(0xFF3B82F6).copy(alpha = 0.2f))
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF60A5FA), // Brilliant Blue
                                                Color(0xFF2563EB), // Royal Indigo
                                                Color(0xFF1E3A8A)  // Deep Royal Blue
                                            )
                                        )
                                    )
                            ) {
                                // Gloss Reflection Curved Highlight Layer
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width, size.height * 0.42f)
                                        quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Siswa Izin",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "$ijinCountToday",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Siswa",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFFDBEAFE),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Box 4: Siswa Sakit (Amber Card - High Gloss Saturated Premium Glass)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .shadow(2.dp, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), Color(0xFFF59E0B).copy(alpha = 0.2f))
                                )
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFFBBF24), // Golden Amber
                                                Color(0xFFD97706), // Sunburnt Orange
                                                Color(0xFF78350F)  // Warm Mahogany
                                            )
                                        )
                                    )
                            ) {
                                // Gloss Reflection Curved Highlight Layer
                                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, 0f)
                                        lineTo(size.width, size.height * 0.42f)
                                        quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Siswa Sakit",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "$sakitCountToday",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Siswa",
                                        fontSize = 9.5.sp,
                                        color = Color(0xFFFEF3C7),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tombol Berdampingan (Side-by-side Buttons: AI Analysis & Manual Attendance)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Analisis AI (AI Analysis Button - Purple/Indigo Gradient)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp))
                                .clickable { showAiAnalysis = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                        )
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Analisis AI",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Analisis AI",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Analisis Kehadiran",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Tombol Absen Manual (Manual Attendance Button - Premium Blue Gradient)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .shadow(if (isTodayHoliday) 1.dp else 4.dp, RoundedCornerShape(16.dp))
                                .clickable(enabled = !isTodayHoliday) { onManualAttendanceClick() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isTodayHoliday) {
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF94A3B8), Color(0xFF64748B))
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))
                                            )
                                        }
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Absen Manual",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Absen Manual",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Tanpa Scan QR",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // ----------------------------------------------------
            // BLOG RSS FEED CARD (https://fasen.my.id)
            // ----------------------------------------------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFFEEF2FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RssFeed,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy((-3).dp)
                            ) {
                                Text(
                                    text = "Artikel Terbaru",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    lineHeight = 14.sp
                                )
                                Text(
                                    text = "fasen.my.id",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                        
                        // Button row to refresh and open blog
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Manual Refresh Button
                            IconButton(
                                onClick = {
                                    isBlogLoading = true
                                    blogError = null
                                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                    scope.launch {
                                        try {
                                            val fetched = com.example.utils.BlogFeedHelper.fetchLatestPosts()
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                blogPosts = fetched
                                                isBlogLoading = false
                                                if (fetched.isEmpty()) {
                                                    blogError = "Gagal memuat feed atau feed kosong."
                                                } else {
                                                    val latestPost = fetched.firstOrNull()
                                                    if (latestPost != null) {
                                                        val lastSeenLink = blogPrefs.getString("last_seen_post_link", null)
                                                        if (lastSeenLink == null || lastSeenLink != latestPost.link) {
                                                            newBlogPostNotification = latestPost
                                                        } else {
                                                            Toast.makeText(androidContext, "Feed diperbarui! Tidak ada artikel baru.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                blogError = e.message ?: "Kesalahan koneksi feed."
                                                isBlogLoading = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Perbarui Feed",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fasen.my.id"))
                                        androidContext.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(androidContext, "Gagal membuka blog", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Kunjungi Blog",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    if (isBlogLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF4F46E5),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Memuat postingan terbaru...",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    } else if (blogError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = blogError ?: "Gagal memuat feed.",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    // Trigger reload
                                    isBlogLoading = true
                                    blogError = null
                                    // Start local coroutine to reload
                                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                    scope.launch {
                                        try {
                                            val fetched = com.example.utils.BlogFeedHelper.fetchLatestPosts()
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                blogPosts = fetched
                                                isBlogLoading = false
                                                if (fetched.isEmpty()) {
                                                    blogError = "Gagal memuat feed atau feed kosong."
                                                } else {
                                                    val latestPost = fetched.firstOrNull()
                                                    if (latestPost != null) {
                                                        val lastSeenLink = blogPrefs.getString("last_seen_post_link", null)
                                                        if (lastSeenLink == null || lastSeenLink != latestPost.link) {
                                                            newBlogPostNotification = latestPost
                                                        } else {
                                                            Toast.makeText(androidContext, "Feed diperbarui! Tidak ada artikel baru.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                blogError = e.message ?: "Kesalahan koneksi feed."
                                                isBlogLoading = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Coba Lagi",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            blogPosts.forEachIndexed { index, post ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (post.link.isNotBlank()) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(post.link))
                                                    androidContext.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(androidContext, "Gagal membuka artikel", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp, horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Image Thumbnail
                                    if (!post.thumbnailUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = post.thumbnailUrl,
                                            contentDescription = post.title,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF1F5F9)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (post.title.isNotEmpty()) post.title.take(1).uppercase() else "B",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    // Title, Description, Date Column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(0.dp)
                                    ) {
                                        Text(
                                            text = post.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 12.5.sp
                                        )
                                        
                                        if (post.description.isNotEmpty()) {
                                            Text(
                                                text = post.description,
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 10.5.sp
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(8.dp)
                                            )
                                            Text(
                                                text = post.pubDate,
                                                fontSize = 8.5.sp,
                                                color = Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                if (index < blogPosts.lastIndex) {
                                    androidx.compose.material3.HorizontalDivider(
                                        color = Color(0xFFF1F5F9),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

        // Floating Action Buttons with Collapsible Speed Dial FAB (All Small)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 10.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Speed Dial items (only shown when expanded)
            AnimatedVisibility(
                visible = isSpeedDialExpanded,
                enter = fadeIn() + expandVertically() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + shrinkVertically() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: Sync (Blue, Small)
                    FloatingActionButton(
                        onClick = {
                            isSpeedDialExpanded = false
                            if (!isManualSyncing) {
                                onSyncClick { success, msg ->
                                    Toast.makeText(androidContext, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        containerColor = Color(0xFF1E40AF),
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                    ) {
                        if (isManualSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sinkronisasi Manual",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Item 2: Broadcast History (Orange, Small)
                    FloatingActionButton(
                        onClick = {
                            isSpeedDialExpanded = false
                            showBroadcastHistoryDialog = true
                        },
                        containerColor = Color(0xFFD97706),
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Riwayat Broadcast",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Item 3: Info (Slate, Small)
                    FloatingActionButton(
                        onClick = {
                            isSpeedDialExpanded = false
                            showLocalInfoDialog = true
                        },
                        containerColor = Color(0xFF475569),
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Aplikasi",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Main Trigger FAB (Small, toggles other sub-FABs)
            FloatingActionButton(
                onClick = { isSpeedDialExpanded = !isSpeedDialExpanded },
                containerColor = Color.Transparent,
                contentColor = if (isSpeedDialExpanded) Color(0xFFDC2626) else Color(0xFF1E293B),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                ),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier
                    .size(38.dp)
                    .shadow(6.dp, androidx.compose.foundation.shape.CircleShape, clip = false)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Realistic glossy glass reflection sheen
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width * 0.45f, 0f)
                                lineTo(size.width * 0.15f, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0.0f)
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height)
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSpeedDialExpanded) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = "Menu Pintasan",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Floating Notification for New Blog Post (Left Side of FAB / Bottom Center)
        AnimatedVisibility(
            visible = newBlogPostNotification != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 72.dp) // end padding leaves room for Speed Dial FAB
        ) {
            if (newBlogPostNotification != null) {
                val post = newBlogPostNotification!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RssFeed,
                                        contentDescription = null,
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Artikel Baru Tersedia!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                            
                            // Close button
                            IconButton(
                                onClick = {
                                    blogPrefs.edit().putString("last_seen_post_link", post.link).apply()
                                    newBlogPostNotification = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = post.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = post.description,
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    // Mark as read/dismiss
                                    blogPrefs.edit().putString("last_seen_post_link", post.link).apply()
                                    newBlogPostNotification = null
                                    
                                    // Open link
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(post.link))
                                        androidContext.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(androidContext, "Gagal membuka artikel", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.White,
                                    containerColor = Color(0xFF4F46E5)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Baca Artikel",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiAnalysis) {
        AiAnalysisDialog(
            attendees = attendees,
            logs = logs,
            onDismiss = { showAiAnalysis = false }
        )
    }
}

@Composable
fun AiAnalysisDialog(
    attendees: List<Attendee>,
    logs: List<AttendanceLog>,
    onDismiss: () -> Unit
) {
    var analysisText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        analysisText = GeminiHelper.analyzeAttendance(attendees, logs)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp),
        confirmButton = {},
        dismissButton = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Analisis AI Kehadiran",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Didukung oleh Gemini AI",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Menganalisis pola absensi siswa...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4B5563),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "AI Guru sedang membaca data kehadiran hari ini",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    val reportText = analysisText ?: "Gagal menghasilkan laporan analisis."
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            // Split by lines and parse basic markdown-like structures
                            reportText.split("\n").forEach { line ->
                                when {
                                    line.startsWith("###") -> {
                                        Text(
                                            text = line.substringAfter("###").trim(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1F2937),
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                        )
                                    }
                                    line.startsWith("##") -> {
                                        Text(
                                            text = line.substringAfter("##").trim(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1E3A8A),
                                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                                        )
                                    }
                                    line.startsWith("#") -> {
                                        Text(
                                            text = line.substringAfter("#").trim(),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1E3A8A),
                                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                                        )
                                    }
                                    line.trim().startsWith("-") || line.trim().startsWith("*") -> {
                                        val content = line.trim().substring(1).trim()
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "•",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4F46E5)
                                            )
                                            Text(
                                                text = content.replace("**", ""), // strip basic bold markers
                                                fontSize = 13.sp,
                                                color = Color(0xFF374151),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                    else -> {
                                        if (line.isNotBlank()) {
                                            val parts = line.split("**")
                                            if (parts.size > 1) {
                                                Text(
                                                    text = buildAnnotatedString {
                                                        parts.forEachIndexed { index, part ->
                                                            if (index % 2 == 1) {
                                                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF111827))) {
                                                                    append(part)
                                                                }
                                                            } else {
                                                                append(part)
                                                            }
                                                        }
                                                    },
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF374151),
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.padding(vertical = 3.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = line,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF374151),
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.padding(vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("Laporan Analisis AI", reportText)
                                    clipboardManager.setPrimaryClip(clipData)
                                    Toast.makeText(context, "Laporan disalin!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Bagikan Laporan AI")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0xFF9CA3AF)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF4B5563)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
                            }
                        }
                    }
                }
            }
        }
    )
}

// =====================================
// TAB 2: ATTENDEES TAB (ANGGOTA)
// =====================================
@Composable
fun AttendeesTab(
    attendees: List<Attendee>,
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    onShowQrClick: (name: String, uid: String) -> Unit,
    onManualAttendanceClick: (Attendee) -> Unit,
    onPhotoCaptureClick: (Attendee) -> Unit,
    onPrintQrCardsClick: () -> Unit,
    onAttendeeClick: (Attendee) -> Unit,
    activeSchoolId: String
) {
    val context = LocalContext.current
    val isNpsnDefault = activeSchoolId.isBlank() || activeSchoolId == "SCH-DEFAULT"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. HEADER (Gradient Background)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                            ),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Top Bar: Logo, Title, Bell
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.mipmap.ic_launcher),
                                        contentDescription = "App Icon",
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column {
                                    Text(
                                        text = "X-Degan QR",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Sistem Absensi Pemindaian Real-time",
                                        fontSize = 11.sp,
                                        color = Color(0xFFDBEAFE)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Huge metric
                        Text(
                            text = "${attendees.size} Siswa",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Kelola data siswa dan cetak kartu QR",
                            fontSize = 13.sp,
                            color = Color(0xFFDBEAFE),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (attendees.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-10).dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                EmptyStateView(
                                    icon = Icons.Default.Person,
                                    title = "Belum Ada Siswa",
                                    description = "Silakan tambah siswa secara manual atau gunakan tombol Impor di bawah untuk memasukkan daftar siswa dari Spreadsheet/Excel."
                                )
                                Button(
                                    onClick = {
                                        if (isNpsnDefault) {
                                            Toast.makeText(context, "NPSN belum diisi/diubah dari default. Silakan ubah NPSN di Pengaturan terlebih dahulu.", Toast.LENGTH_LONG).show()
                                        } else {
                                            onImportClick()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isNpsnDefault) Color(0xFF64748B) else Color(0xFF1E40AF),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isNpsnDefault) Icons.Default.Lock else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isNpsnDefault) "Impor Data Siswa (Terkunci)" else "Impor Data Siswa", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Top control card: Print QR & Import buttons (Overlapping the header)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-10).dp)
                            .padding(horizontal = 16.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPrintQrCardsClick,
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFAF5FF),
                                    contentColor = Color(0xFF7C3AED)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFF3E8FF))
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Cetak QR", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cetak QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (isNpsnDefault) {
                                        Toast.makeText(context, "NPSN belum diisi/diubah dari default. Silakan ubah NPSN di Pengaturan terlebih dahulu.", Toast.LENGTH_LONG).show()
                                    } else {
                                        onImportClick()
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNpsnDefault) Color(0xFFF1F5F9) else Color(0xFFEFF6FF),
                                    contentColor = if (isNpsnDefault) Color(0xFF94A3B8) else Color(0xFF1E40AF)
                                ),
                                border = BorderStroke(1.dp, if (isNpsnDefault) Color(0xFFCBD5E1) else Color(0xFFDBEAFE))
                            ) {
                                Icon(
                                    imageVector = if (isNpsnDefault) Icons.Default.Lock else Icons.Default.CloudDownload,
                                    contentDescription = "Import",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isNpsnDefault) "Impor (Terkunci)" else "Impor Siswa", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Member items
                items(attendees, key = { it.id }) { attendee ->
                    val studentBitmap = remember(attendee.photoPath) {
                        if (!attendee.photoPath.isNullOrEmpty()) {
                            try {
                                android.graphics.BitmapFactory.decodeFile(attendee.photoPath)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-10).dp)
                            .padding(horizontal = 16.dp)
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAttendeeClick(attendee) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Photo / Initials Avatar
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDBEAFE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (studentBitmap != null) {
                                        Image(
                                            bitmap = studentBitmap.asImageBitmap(),
                                            contentDescription = "Foto ${attendee.name}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = attendee.name.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E40AF)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = attendee.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${attendee.role}  •  ID: ${attendee.uid}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Manual Attendance Button (for Siswa)
                                val isSiswa = remember(attendee.role) {
                                    val r = attendee.role.trim().lowercase()
                                    r == "siswa" || (r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu")
                                }
                                if (isSiswa) {
                                    IconButton(
                                        onClick = { onManualAttendanceClick(attendee) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Absen Manual",
                                            tint = Color(0xFF10B981)
                                        )
                                    }
                                }

                                // Take Photo Button (Direct from Camera)
                                IconButton(
                                    onClick = { onPhotoCaptureClick(attendee) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Ambil Foto",
                                        tint = Color(0xFFF59E0B)
                                    )
                                }

                                // Show QR Button
                                IconButton(
                                    onClick = { onShowQrClick(attendee.name, attendee.uid) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "Generate QR",
                                        tint = Color(0xFF1E40AF)
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { onDeleteClick(attendee.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Extra padding at the bottom of the list to prevent FAB overlapping
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Add Member Floating Action Button
        FloatingActionButton(
            onClick = {
                if (isNpsnDefault) {
                    Toast.makeText(context, "NPSN belum diisi/diubah dari default. Silakan ubah NPSN di Pengaturan terlebih dahulu.", Toast.LENGTH_LONG).show()
                } else {
                    onAddClick()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_attendee_fab"),
            containerColor = if (isNpsnDefault) Color(0xFF64748B) else Color(0xFF1E40AF),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isNpsnDefault) Icons.Default.Lock else Icons.Default.Add,
                contentDescription = if (isNpsnDefault) "Tambah Siswa (Terkunci)" else "Tambah Siswa"
            )
        }
    }
}

// =====================================
// TAB 3: SETTINGS TAB (PENGATURAN)
// =====================================
@Composable
fun SettingsTab(
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDeviceBound by viewModel.isDeviceBound.collectAsStateWithLifecycle()
    val bindingDeviceName by viewModel.bindingDeviceName.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val demoRemainingTime by viewModel.demoRemainingTime.collectAsStateWithLifecycle()

    var appsScriptUrl by remember { mutableStateOf(viewModel.getAppsScriptUrl()) }
    var isAutoSyncEnabled by remember { mutableStateOf(viewModel.isAutoSyncEnabled()) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(true) }

    var supabaseUrlInput by remember { mutableStateOf(viewModel.getSupabaseUrl()) }
    var supabaseAnonKeyInput by remember { mutableStateOf(viewModel.getSupabaseAnonKey()) }
    var isSupabaseEnabledInput by remember { mutableStateOf(viewModel.isSupabaseEnabled()) }
    var isSupaTesting by remember { mutableStateOf(false) }
    var supaTestStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSupaTestSuccess by remember { mutableStateOf(true) }
    var showSupaSqlHelp by remember { mutableStateOf(false) }

    var selectedClassInput by remember { mutableStateOf(viewModel.getSelectedClass()) }
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val activeSchoolIdForDialog by viewModel.activeSchoolId.collectAsStateWithLifecycle()
    val classOptions = remember(attendees, activeSchoolIdForDialog) {
        val schoolFiltered = attendees.filter {
            it.schoolId == activeSchoolIdForDialog
        }
        val studentRoles = schoolFiltered.map { it.role.trim() }.filter {
            val r = it.lowercase()
            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu" && r.isNotBlank()
        }.distinct().sorted()
        val baseList = listOf("Semua Kelas") + studentRoles
        if (baseList.size == 1) {
            listOf("Semua Kelas", "Siswa", "Kelas 1", "Kelas 2", "Kelas 3", "Kelas 4", "Kelas 5", "Kelas 6")
        } else {
            baseList
        }
    }
    var showClassDropdown by remember { mutableStateOf(false) }
    var teacherNameInput by remember { mutableStateOf(viewModel.getTeacherName()) }
    var jamMasukInput by remember(selectedClassInput) {
        mutableStateOf(viewModel.getJamMasukForClass(selectedClassInput))
    }
    var jamPulangInput by remember(selectedClassInput) {
        mutableStateOf(viewModel.getJamPulangForClass(selectedClassInput))
    }
    var showHelpSection by remember { mutableStateOf(false) }
    var defaultCameraSetting by remember { mutableStateOf(viewModel.getDefaultCamera()) }
    var selectedSettingTab by remember { mutableStateOf("Profil") }

    // Developer Lock State
    val isDeveloperUnlocked by viewModel.isDeveloperUnlocked.collectAsStateWithLifecycle()
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Developer Dashboard Broadcast States
    var devBroadcastTitle by remember { mutableStateOf("") }
    var devBroadcastMessage by remember { mutableStateOf("") }
    var devBroadcastDriveLink by remember { mutableStateOf("") }
    var devBroadcastType by remember { mutableStateOf("UPDATE") }
    var devBroadcastIsActive by remember { mutableStateOf(true) }
    var isPushingBroadcast by remember { mutableStateOf(false) }
    var broadcastPushStatus by remember { mutableStateOf<String?>(null) }
    var isBroadcastPushSuccess by remember { mutableStateOf(true) }
    var isSqlSetupExpanded by remember { mutableStateOf(false) }

    // Multi-School state variables
    var showAddSchoolDialog by remember { mutableStateOf(false) }
    var showEditSchoolDialog by remember { mutableStateOf<SchoolConfig?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<SchoolConfig?>(null) }

    // Multi-School Dialogs rendering
    val schoolConfigsForDialog by viewModel.schoolConfigsListFlow.collectAsStateWithLifecycle()

    if (showAddSchoolDialog) {
        var newId by remember { mutableStateOf("SCH-${(1000..9999).random()}") }
        var newName by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddSchoolDialog = false },
            title = { Text("Tambah Sekolah Baru", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newId,
                        onValueChange = { newId = it.trim().uppercase() },
                        label = { Text("ID Sekolah / NPSN") },
                        placeholder = { Text("misal: 20101234 (NPSN) atau SCH-01") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Sekolah") },
                        placeholder = { Text("misal: SDN 1 Merdeka") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Alamat Sekolah") },
                        placeholder = { Text("misal: Jl. Raya No. 10") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it.trim() },
                        label = { Text("Google Apps Script URL (Opsional)") },
                        placeholder = { Text("https://script.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "Jika URL dikosongkan, sekolah ini akan menggunakan URL default global yang tersetting di tab 'Sheets'.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newId.isNotBlank()) {
                            val newList = schoolConfigsForDialog + SchoolConfig(
                                id = newId,
                                name = newName.trim(),
                                address = newAddress.trim(),
                                appsScriptUrl = newUrl.trim()
                            )
                            viewModel.saveSchoolConfigs(newList)
                            showAddSchoolDialog = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSchoolDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    showEditSchoolDialog?.let { config ->
        var editName by remember(config) { mutableStateOf(config.name) }
        var editAddress by remember(config) { mutableStateOf(config.address) }
        var editUrl by remember(config) { mutableStateOf(config.appsScriptUrl) }
        
        AlertDialog(
            onDismissRequest = { showEditSchoolDialog = null },
            title = { Text("Edit Sekolah: ${config.id}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nama Sekolah") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("Alamat Sekolah") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it.trim() },
                        label = { Text("Google Apps Script URL (Opsional)") },
                        placeholder = { Text("https://script.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            val newList = schoolConfigsForDialog.map {
                                if (it.id == config.id) {
                                    it.copy(name = editName.trim(), address = editAddress.trim(), appsScriptUrl = editUrl.trim())
                                } else {
                                    it
                                }
                            }
                            viewModel.saveSchoolConfigs(newList)
                            if (config.id == activeSchoolIdForDialog) {
                                viewModel.updateSchoolInfo(editName.trim(), editAddress.trim(), viewModel.getSchoolLogoPath())
                            }
                            showEditSchoolDialog = null
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSchoolDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }

    showDeleteConfirmDialog?.let { config ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Hapus Sekolah?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus '${config.name}' dari perangkat? Data absensi dan siswa yang terdaftar untuk sekolah ini tidak akan terhapus dari database lokal, namun sekolah ini tidak lagi terdaftar dalam multi-sekolah.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val newList = schoolConfigsForDialog.filter { it.id != config.id }
                        viewModel.saveSchoolConfigs(newList)
                        if (config.id == activeSchoolIdForDialog && newList.isNotEmpty()) {
                            viewModel.setActiveSchoolId(newList.first().id)
                        }
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // School Profile Edit States
    val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
    val schoolAddress by viewModel.schoolAddress.collectAsStateWithLifecycle()
    val schoolLogoPath by viewModel.schoolLogoPath.collectAsStateWithLifecycle()
    val activeSchoolId by viewModel.activeSchoolId.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceIdFlow.collectAsStateWithLifecycle()

    var nameInput by remember(schoolName) { mutableStateOf(schoolName) }
    var addressInput by remember(schoolAddress) { mutableStateOf(schoolAddress) }
    var logoPathInput by remember(schoolLogoPath) { mutableStateOf(schoolLogoPath) }
    var idInput by remember(activeSchoolId) { mutableStateOf(activeSchoolId) }
    var deviceIdInput by remember(deviceId) { mutableStateOf(deviceId) }
    var isNpsnError by remember { mutableStateOf(false) }
    var npsnErrorText by remember { mutableStateOf<String?>(null) }

    // Setup Gallery Picker for Logo
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy selected URI content to a local private file
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val logoDir = java.io.File(context.filesDir, "logos")
                    if (!logoDir.exists()) logoDir.mkdirs()
                    val destFile = java.io.File(logoDir, "school_logo_${System.currentTimeMillis()}.jpg")
                    val outputStream = java.io.FileOutputStream(destFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    logoPathInput = destFile.absolutePath
                    Toast.makeText(context, "Logo berhasil dipilih!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Setup Camera Capture for Logo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val logoDir = java.io.File(context.filesDir, "logos")
                if (!logoDir.exists()) logoDir.mkdirs()
                val destFile = java.io.File(logoDir, "school_logo_${System.currentTimeMillis()}.jpg")
                val out = java.io.FileOutputStream(destFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                logoPathInput = destFile.absolutePath
                Toast.makeText(context, "Foto logo berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengambil foto logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto logo.", Toast.LENGTH_SHORT).show()
        }
    }

    val schoolLogoBitmap = remember(logoPathInput) {
        if (!logoPathInput.isNullOrEmpty()) {
            try {
                android.graphics.BitmapFactory.decodeFile(logoPathInput)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val appsScriptTemplate = APPS_SCRIPT_TEMPLATE

    if (!isDeviceBound) {
        // --- COLOURED GLOWING ACTIVATION SCREEN (LOCKED/UNBOUND) ---
        var inputToken by remember { mutableStateOf("DEMO7HARI") }
        var inputDeviceName by remember { mutableStateOf("Ahmad Fawzan Rohman") }
        var isBindingProcess by remember { mutableStateOf(false) }
        var isRegisteringProcess by remember { mutableStateOf(false) }
        var bindingError by remember { mutableStateOf<String?>(null) }
        var bindingSuccess by remember { mutableStateOf<String?>(null) }
        var registrationStatus by remember { mutableStateOf<Boolean?>(null) }
        var showActivationInfoDialog by remember { mutableStateOf(false) }

        val registerButtonColor by animateColorAsState(
            targetValue = when {
                registrationStatus == true -> Color(0xFF10B981) // Green
                registrationStatus == false -> Color(0xFFEF4444) // Red
                else -> MaterialTheme.colorScheme.secondary
            },
            label = "registerButtonColor"
        )

        val registerButtonText = when {
            isRegisteringProcess -> "Mendaftarkan..."
            registrationStatus == true -> "TERDAFTAR"
            registrationStatus == false -> "COBA LAGI"
            else -> "Daftarkan"
        }

        val registerButtonScale by animateFloatAsState(
            targetValue = if (isRegisteringProcess) 0.95f else 1.0f,
            label = "registerButtonScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF1F5F9), // Light Slate
                            Color(0xFFE2E8F0), // Cool Gray
                            Color(0xFFD8B4FE).copy(alpha = 0.15f) // Ultra soft neon violet tint at the bottom
                        )
                    )
                )
                .drawBehind {
                    // Draw soft glowing ambient radial blobs
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.08f), Color.Transparent),
                            radius = size.width * 0.7f
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.1f),
                        radius = size.width * 0.7f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFEC4899).copy(alpha = 0.08f), Color.Transparent),
                            radius = size.width * 0.7f
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.9f),
                        radius = size.width * 0.7f
                    )
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Lock/Security Emblem with layered translucent glowing backgrounds
                Box(
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Glowing outer ring 1
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.05f))
                    )
                    // Glowing outer ring 2
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                    )
                    // Solid inner container
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFCA5A5), Color(0xFFEF4444))
                                )
                            )
                            .shadow(2.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Aktivasi Perangkat",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFF6366F1).copy(alpha = 0.1f),
                                Color(0xFF3B82F6).copy(alpha = 0.1f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Aktivasi Perangkat",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = (-0.5).sp,
                                color = Color(0xFF1E293B),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Hubungkan perangkat ini ke sistem cloud absensi",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }

                        // WhatsApp Support Card Banner
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val waUrl = "https://api.whatsapp.com/send?phone=6282301838321&text=" + Uri.encode("Assalamu alaikum, saya pengguna X-Degan QR ..")
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF25D366)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "WhatsApp Chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hubungi Admin via WhatsApp",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "Butuh bantuan aktivasi? Hubungi kami langsung",
                                        fontSize = 10.sp,
                                        color = Color(0xFF047857)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // --- STEP 1: DAFTARKAN DEVICE ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Daftarkan Nama Anda",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        OutlinedTextField(
                            value = inputDeviceName,
                            onValueChange = { inputDeviceName = it },
                            label = { Text("Nama Bapak/Ibu", fontSize = 11.sp) },
                            placeholder = { Text("Contoh: Tablet Lab IPA", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TabletAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && inputDeviceName == "Ahmad Fawzan Rohman") {
                                        inputDeviceName = ""
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        Button(
                            onClick = {
                                isRegisteringProcess = true
                                bindingError = null
                                bindingSuccess = null
                                registrationStatus = null
                                viewModel.registerDevice(context, appsScriptUrl, inputDeviceName) { success, msg ->
                                    isRegisteringProcess = false
                                    if (success) {
                                        registrationStatus = true
                                    } else {
                                        registrationStatus = false
                                        bindingError = msg
                                    }
                                }
                            },
                            enabled = !isRegisteringProcess && !isBindingProcess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .graphicsLayer(scaleX = registerButtonScale, scaleY = registerButtonScale),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = registerButtonColor
                            )
                        ) {
                            if (isRegisteringProcess) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (registrationStatus == true) Icons.Default.CheckCircle else Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Text(registerButtonText, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // --- STEP 2: VERIFIKASI TOKEN ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFF472B6), Color(0xFFEC4899))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "2",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Masukkan Token Aktivasi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        OutlinedTextField(
                            value = inputToken,
                            onValueChange = { inputToken = it },
                            label = { Text("Token Aktivasi", fontSize = 11.sp) },
                            placeholder = { Text("Contoh: TOK-001, DEV123 atau DEMO7HARI", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        if (!bindingError.isNullOrEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = bindingError!!,
                                        color = Color(0xFF991B1B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (!bindingSuccess.isNullOrEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = bindingSuccess!!,
                                        color = Color(0xFF065F46),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isBindingProcess = true
                                bindingError = null
                                bindingSuccess = null
                                viewModel.bindDevice(context, appsScriptUrl, inputToken) { success, msg ->
                                    isBindingProcess = false
                                    if (success) {
                                        bindingSuccess = msg
                                    } else {
                                        bindingError = msg
                                    }
                                }
                            },
                            enabled = !isBindingProcess && !isRegisteringProcess,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5) // Royal Indigo
                            )
                        ) {
                            if (isBindingProcess) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Text("Hubungkan & Aktifkan Perangkat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }


                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gradient Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                            ),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.mipmap.ic_launcher),
                                        contentDescription = "App Icon",
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column {
                                    Text(
                                        text = "X-Degan QR",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Sistem Absensi Pemindaian Real-time",
                                        fontSize = 11.sp,
                                        color = Color(0xFFDBEAFE)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Pengaturan",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-1).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Konfigurasi identitas kelas, wali kelas, dan integrasi database Supabase",
                            fontSize = 13.sp,
                            color = Color(0xFFDBEAFE),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

        // Tab Menu Pengaturan
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabsRow1 = listOf(
                        "Profil" to Icons.Default.School,
                        "Kelas" to Icons.Default.AssignmentInd,
                        "Perangkat" to Icons.Default.PhoneAndroid
                    )
                    tabsRow1.forEach { (tabName, icon) ->
                        val isSelected = selectedSettingTab == tabName
                        val containerColor = if (isSelected) Color(0xFF1E40AF) else Color.White
                        val contentColor = if (isSelected) Color.White else Color(0xFF475569)
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable { selectedSettingTab = tabName },
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tabName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabsRow2 = listOf(
                        "Notif" to Icons.Default.Notifications,
                        "Developer" to Icons.Default.Code
                    )
                    tabsRow2.forEach { (tabName, icon) ->
                        val isSelected = selectedSettingTab == tabName
                        val containerColor = if (isSelected) Color(0xFF1E40AF) else Color.White
                        val contentColor = if (isSelected) Color.White else Color(0xFF475569)
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable { selectedSettingTab = tabName },
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tabName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 1.5: Edit Profil Sekolah
        if (selectedSettingTab == "Profil") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Profil Sekolah",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = idInput,
                            onValueChange = { 
                                idInput = it 
                                isNpsnError = false
                                npsnErrorText = null
                            },
                            label = { Text("ID Sekolah / NPSN") },
                            placeholder = { Text("Contoh: 20101234 (8 digit angka)") },
                            isError = isNpsnError,
                            supportingText = npsnErrorText?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Nama Sekolah") },
                            placeholder = { Text("Contoh: SMA Negeri 1 Jakarta") },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text("Alamat Sekolah") },
                            placeholder = { Text("Contoh: Jl. Budi Utomo No. 7, Jakarta Pusat") },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        OutlinedTextField(
                            value = deviceIdInput,
                            onValueChange = { /* Kolom terkunci, diisi otomatis */ },
                            readOnly = true,
                            label = { Text("ID Perangkat / Device ID (Terkunci)") },
                            placeholder = { Text("Diisi otomatis oleh aplikasi") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Terkunci",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo preview
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (schoolLogoBitmap != null) {
                                    Image(
                                        bitmap = schoolLogoBitmap.asImageBitmap(),
                                        contentDescription = "Pratinjau Logo Sekolah",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = "Pratinjau Logo Sekolah",
                                        tint = Color(0xFF1E40AF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Logo Resmi Sekolah",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { galleryPickerLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Galeri", fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.CAMERA
                                            )
                                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                cameraLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kamera", fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val trimmedId = idInput.trim()
                                val errorMsg = if (trimmedId.isBlank()) {
                                    "NPSN / ID Sekolah tidak boleh kosong"
                                } else if (trimmedId != "SCH-DEFAULT" && (trimmedId.length != 8 || !trimmedId.all { it.isDigit() })) {
                                    "NPSN harus terdiri dari 8 digit angka (0-9)"
                                } else {
                                    null
                                }

                                if (errorMsg != null) {
                                    isNpsnError = true
                                    npsnErrorText = errorMsg
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                } else {
                                    isNpsnError = false
                                    npsnErrorText = null
                                    val oldId = viewModel.getActiveSchoolId()
                                    val newId = trimmedId
                                    viewModel.updateSchoolInfo(nameInput.trim(), addressInput.trim(), logoPathInput)
                                    viewModel.updateDeviceId(deviceIdInput.trim())
                                    if (newId.uppercase() != oldId.uppercase()) {
                                        Toast.makeText(context, "Mengubah NPSN & menyinkronkan data...", Toast.LENGTH_SHORT).show()
                                        viewModel.updateActiveSchoolId(newId) { success, message ->
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Profil, ID Sekolah & ID Perangkat berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Profil Sekolah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Card 1: Identity & Class Filter
        if (selectedSettingTab == "Kelas") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Identitas Kelas & Guru",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedClassInput,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kelas yang Diaktifkan") },
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (showClassDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showClassDropdown = true }
                            )
                            DropdownMenu(
                                expanded = showClassDropdown,
                                onDismissRequest = { showClassDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                classOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, fontSize = 13.sp) },
                                        onClick = {
                                            selectedClassInput = option
                                            showClassDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Aplikasi hanya akan menampilkan & mencatat kehadiran untuk siswa dengan Kelas/Role ini. Pilih 'Semua' untuk menampilkan semua.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = teacherNameInput,
                            onValueChange = { teacherNameInput = it },
                            label = { Text("Nama Guru / Wali Kelas") },
                            placeholder = { Text("Nama Lengkap...") },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = jamMasukInput,
                                onValueChange = { jamMasukInput = it },
                                label = { Text("Jam Masuk (HH:mm)") },
                                placeholder = { Text("07:30") },
                                modifier = Modifier.weight(1f).height(58.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )

                            OutlinedTextField(
                                value = jamPulangInput,
                                onValueChange = { jamPulangInput = it },
                                label = { Text("Jam Pulang (HH:mm)") },
                                placeholder = { Text("13:00") },
                                modifier = Modifier.weight(1f).height(58.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                        }

                        Text(
                            text = "Jam batas masuk & pulang ini digunakan untuk menghitung status (Tepat Waktu, Terlambat, atau Pulang Awal) khusus untuk Kelas '$selectedClassInput'.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.saveSelectedClass(selectedClassInput.trim())
                                viewModel.saveTeacherName(teacherNameInput.trim())
                                viewModel.saveJamMasukForClass(selectedClassInput.trim(), jamMasukInput.trim())
                                viewModel.saveJamPulangForClass(selectedClassInput.trim(), jamPulangInput.trim())
                                Toast.makeText(context, "Identitas Kelas & Guru berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Identitas Kelas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


        // Card 3b: Camera Selection Setting
        if (selectedSettingTab == "Perangkat") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Kamera Default Absensi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Pilih kamera yang secara default aktif saat membuka pemindai QR code.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = defaultCameraSetting == "BACK",
                                    onClick = { defaultCameraSetting = "BACK" }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kamera Belakang", fontSize = 13.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = defaultCameraSetting == "FRONT",
                                    onClick = { defaultCameraSetting = "FRONT" }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kamera Depan", fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.saveDefaultCamera(defaultCameraSetting)
                                Toast.makeText(context, "Kamera Default berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Kamera Default", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Card: Synchronization Option (moved from Supabase page)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1.5f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Sinkronisasi Otomatis",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Kirim otomatis data absensi secara real-time ke Supabase saat HP terkoneksi internet.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = isAutoSyncEnabled,
                                onCheckedChange = { isAutoSyncEnabled = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                viewModel.setAutoSyncEnabled(isAutoSyncEnabled)
                                Toast.makeText(context, "Pengaturan Sinkronisasi Otomatis disimpan!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Status Sinkronisasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (selectedSettingTab == "Notif") {
            item {
                val isSoundEnabled by viewModel.isSoundEnabledState.collectAsStateWithLifecycle()
                val isTtsEnabled by viewModel.isTtsEnabledState.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pengaturan Suara & Notifikasi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Konfigurasi bunyi beep (buzzer) dan pengumuman suara otomatis (Text-To-Speech) Bahasa Indonesia setelah pemindaian kartu berhasil atau gagal.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switch 1: Beep Sound
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Bunyi Beep (Buzzer)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Mainkan nada buzzer bip setelah pemindaian berhasil.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { viewModel.setSoundEnabled(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Switch 2: Text To Speech
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Pengumuman Suara (TTS)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sebutkan nama dan status otomatis dalam Bahasa Indonesia setelah pemindaian berhasil.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isTtsEnabled,
                                onCheckedChange = { viewModel.setTtsEnabled(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Test Button for Speech and Sound
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Pratinjau / Tes Bunyi & Suara",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.playSoundNotification(true)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tes Beep Sukses", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.playSoundNotification(false)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.VolumeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tes Beep Gagal", fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.speakText("Absen masuk berhasil. Halo, Budi Pratama.")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tes Suara Google TTS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (selectedSettingTab == "Developer") {
            if (!isDeveloperUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "Halaman Developer Terkunci",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Masukkan Sandi Developer untuk mengakses pengaturan lanjutan dan dashboard pengumuman.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    if (it.length <= 16) {
                                        pinInput = it
                                        pinError = null
                                    }
                                },
                                label = { Text("Sandi Keamanan") },
                                placeholder = { Text("Masukkan Sandi") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                                ),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                isError = pinError != null,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            pinError?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = {
                                    if (pinInput == "123sukses") {
                                        viewModel.setDeveloperUnlocked(true)
                                        pinInput = ""
                                        pinError = null
                                        Toast.makeText(context, "Buka kunci sukses!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pinError = "Sandi Salah! Silakan coba lagi."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Buka Kunci", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Card: Supabase Activation & Configuration
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Integrasi Supabase Database",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1.5f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Aktifkan Supabase",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Gunakan database Supabase PostgreSQL sebagai backend sinkronisasi real-time.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = isSupabaseEnabledInput,
                                    onCheckedChange = { isSupabaseEnabledInput = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            OutlinedTextField(
                                value = supabaseUrlInput,
                                onValueChange = { supabaseUrlInput = it },
                                label = { Text("Supabase Project URL") },
                                placeholder = { Text("https://your-project.supabase.co") },
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )

                            OutlinedTextField(
                                value = supabaseAnonKeyInput,
                                onValueChange = { supabaseAnonKeyInput = it },
                                label = { Text("Supabase API Key / Anon Key") },
                                placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5...") },
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )

                            // Collapsible SQL Setup Guide
                            OutlinedButton(
                                onClick = { showSupaSqlHelp = !showSupaSqlHelp },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (showSupaSqlHelp) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showSupaSqlHelp) "Sembunyikan SQL Setup" else "Petunjuk & Salin SQL Setup Supabase",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (showSupaSqlHelp) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Langkah Integrasi Supabase:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "1. Buat project baru gratis di supabase.com.\n" +
                                                "2. Masuk ke SQL Editor di dashboard Supabase.\n" +
                                                "3. Salin SQL Query di bawah ini lalu klik 'Run' untuk membuat tabel 'siswa' dan 'kehadiran' secara otomatis.\n" +
                                                "4. Salin Project URL & Anon Key dari menu Settings -> API ke kolom di atas.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = {
                                            val sqlQuery = """
                                                -- 1. Buat tabel siswa
                                                create table if not exists siswa (
                                                  id bigint generated by default as identity primary key,
                                                  uid text unique not null,
                                                  name text not null,
                                                  role text default 'Siswa',
                                                  school_id text,
                                                  school_name text,
                                                  created_at timestamp with time zone default timezone('utc'::text, now()) not null
                                                );

                                                -- 2. Buat tabel kehadiran
                                                create table if not exists kehadiran (
                                                  id bigint generated by default as identity primary key,
                                                  id_unique text unique,
                                                  uid text not null,
                                                  name text not null,
                                                  role text not null,
                                                  timestamp bigint not null,
                                                  type text not null,
                                                  status text not null,
                                                  session_name text,
                                                  school_id text,
                                                  school_name text,
                                                  created_at timestamp with time zone default timezone('utc'::text, now()) not null
                                                );

                                                -- 3. Nonaktifkan RLS untuk kemudahan akses anon atau aktifkan kebijakan publik
                                                alter table siswa disable row level security;
                                                alter table kehadiran disable row level security;
                                            """.trimIndent()

                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("SupabaseSQLTemplate", sqlQuery)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "SQL Query disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salin SQL Setup Database", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.saveSupabaseUrl(supabaseUrlInput.trim())
                                    viewModel.saveSupabaseAnonKey(supabaseAnonKeyInput.trim())
                                    viewModel.setSupabaseEnabled(isSupabaseEnabledInput)
                                    Toast.makeText(context, "Pengaturan Supabase berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan Konfigurasi Supabase", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Card: Test Connection
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Uji Jaringan & Koneksi Supabase",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = {
                                    if (supabaseUrlInput.isBlank() || supabaseAnonKeyInput.isBlank()) {
                                        isSupaTestSuccess = false
                                        supaTestStatusMessage = "Masukkan URL dan API Key terlebih dahulu."
                                    } else {
                                        isSupaTesting = true
                                        supaTestStatusMessage = null
                                        coroutineScope.launch {
                                            val result = com.example.utils.SupabaseSyncHelper.testConnection(
                                                supabaseUrlInput.trim(),
                                                supabaseAnonKeyInput.trim()
                                            )
                                            isSupaTesting = false
                                            isSupaTestSuccess = result.first
                                            supaTestStatusMessage = result.second
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isSupaTesting
                            ) {
                                if (isSupaTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menguji...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Uji Jaringan Sekarang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            supaTestStatusMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSupaTestSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Dashboard Developer & Broadcast",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Kirim pesan pembaruan aplikasi atau instruksi penting kepada seluruh perangkat pengguna melalui server Supabase.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Form Title
                        OutlinedTextField(
                            value = devBroadcastTitle,
                            onValueChange = { devBroadcastTitle = it },
                            label = { Text("Judul Pengumuman") },
                            placeholder = { Text("misal: Pembaruan Sistem Versi 6.2") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        // Form Message
                        OutlinedTextField(
                            value = devBroadcastMessage,
                            onValueChange = { devBroadcastMessage = it },
                            label = { Text("Pesan Pengumuman / Instruksi") },
                            placeholder = { Text("Tuliskan deskripsi lengkap atau instruksi khusus...") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 5,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        // Form Drive Link
                        OutlinedTextField(
                            value = devBroadcastDriveLink,
                            onValueChange = { devBroadcastDriveLink = it },
                            label = { Text("Tautan Download / Google Drive Link (Opsional)") },
                            placeholder = { Text("https://drive.google.com/...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        // Select Type Row
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tipe Pengumuman",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val types = listOf("UPDATE" to "Pembaruan", "INSTRUCTION" to "Instruksi")
                                types.forEach { (typeVal, typeLabel) ->
                                    val isTypeSelected = devBroadcastType == typeVal
                                    OutlinedButton(
                                        onClick = { devBroadcastType = typeVal },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isTypeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                            contentColor = if (isTypeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(
                                            width = if (isTypeSelected) 2.dp else 1.dp,
                                            color = if (isTypeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Text(typeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Active Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Status Aktif",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Aktifkan untuk langsung menampilkan banner ke seluruh perangkat pengguna.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = devBroadcastIsActive,
                                onCheckedChange = { devBroadcastIsActive = it }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Status Messages
                        broadcastPushStatus?.let { statusMsg ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isBroadcastPushSuccess) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                                ),
                                border = BorderStroke(1.dp, if (isBroadcastPushSuccess) Color(0xFFA7F3D0) else Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isBroadcastPushSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isBroadcastPushSuccess) Color(0xFF10B981) else Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = statusMsg,
                                        color = if (isBroadcastPushSuccess) Color(0xFF065F46) else Color(0xFF991B1B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Buttons for Preset templates
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Gunakan Template Pesan Quick-Fill",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        devBroadcastTitle = "Pembaruan Aplikasi v6.2"
                                        devBroadcastMessage = "Silakan unduh pembaruan aplikasi X-Degan QR versi terbaru untuk memperbaiki bug pemindaian dan meningkatkan performa database."
                                        devBroadcastDriveLink = "https://drive.google.com/drive/folders/1abc123_placeholder"
                                        devBroadcastType = "UPDATE"
                                        devBroadcastIsActive = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Template Update", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        devBroadcastTitle = "Sinkronisasi Manual Sebelum Jam 12:00"
                                        devBroadcastMessage = "Diimbau bagi seluruh wali kelas/petugas pemindai kartu agar melakukan sinkronisasi data manual sebelum jam istirahat agar laporan rekap absensi dapat langsung ditarik."
                                        devBroadcastDriveLink = ""
                                        devBroadcastType = "INSTRUCTION"
                                        devBroadcastIsActive = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Template Instruksi", fontSize = 11.sp)
                                }
                            }
                        }

                        // Action Buttons: Send to Supabase or Preview Locally
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Button 1: Push to Supabase (Publish)
                            Button(
                                onClick = {
                                    if (devBroadcastTitle.isBlank() || devBroadcastMessage.isBlank()) {
                                        broadcastPushStatus = "Judul dan Pesan tidak boleh kosong!"
                                        isBroadcastPushSuccess = false
                                        return@Button
                                    }
                                    isPushingBroadcast = true
                                    broadcastPushStatus = "Sedang mengirim broadcast..."
                                    isBroadcastPushSuccess = true
                                    
                                    viewModel.pushBroadcastMessage(
                                        title = devBroadcastTitle,
                                        message = devBroadcastMessage,
                                        driveLink = devBroadcastDriveLink,
                                        type = devBroadcastType,
                                        isActive = devBroadcastIsActive,
                                        onSuccess = {
                                            isPushingBroadcast = false
                                            broadcastPushStatus = "Sukses! Broadcast berhasil dipublikasikan ke server Supabase."
                                            isBroadcastPushSuccess = true
                                            Toast.makeText(context, "Broadcast berhasil dipublikasikan!", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { error ->
                                            isPushingBroadcast = false
                                            broadcastPushStatus = "Gagal mempublikasikan: $error"
                                            isBroadcastPushSuccess = false
                                        }
                                    )
                                },
                                enabled = !isPushingBroadcast,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isPushingBroadcast) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Kirim ke Semua Pengguna (Supabase)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Button 2: Preview Locally
                            OutlinedButton(
                                onClick = {
                                    if (devBroadcastTitle.isBlank() || devBroadcastMessage.isBlank()) {
                                        broadcastPushStatus = "Tulis Judul dan Pesan untuk melihat pratinjau lokal!"
                                        isBroadcastPushSuccess = false
                                        return@OutlinedButton
                                    }
                                    val simulatedBroadcast = com.example.ui.BroadcastMessage(
                                        id = 999999L,
                                        title = devBroadcastTitle.trim(),
                                        message = devBroadcastMessage.trim(),
                                        driveLink = devBroadcastDriveLink.trim(),
                                        type = devBroadcastType,
                                        isActive = devBroadcastIsActive,
                                        updatedId = System.currentTimeMillis()
                                    )
                                    viewModel.setLocalBroadcast(simulatedBroadcast)
                                    broadcastPushStatus = "Pratinjau Lokal Aktif! Silakan periksa halaman Utama (Absen) untuk melihat tampilan pengumuman."
                                    isBroadcastPushSuccess = true
                                    Toast.makeText(context, "Pratinjau lokal diaktifkan!", Toast.LENGTH_LONG).show()
                                },
                                enabled = !isPushingBroadcast,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Simulasikan Lokal (Pratinjau)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Button 3: Disable / Reset
                            OutlinedButton(
                                onClick = {
                                    viewModel.setLocalBroadcast(null)
                                    devBroadcastTitle = ""
                                    devBroadcastMessage = ""
                                    devBroadcastDriveLink = ""
                                    devBroadcastType = "UPDATE"
                                    devBroadcastIsActive = false
                                    broadcastPushStatus = "Simulasi lokal dinonaktifkan."
                                    isBroadcastPushSuccess = true
                                    Toast.makeText(context, "Pratinjau lokal dimatikan.", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isPushingBroadcast,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Reset & Matikan Broadcast", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Button 4: Riwayat Broadcast
                            OutlinedButton(
                                onClick = {
                                    showHistoryDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF94A3B8))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Riwayat Broadcast", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            if (showHistoryDialog) {
                                val historyList = viewModel.getBroadcastHistory()
                                AlertDialog(
                                    onDismissRequest = { showHistoryDialog = false },
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Riwayat Broadcast",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    },
                                    text = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 400.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Daftar pengumuman dan broadcast yang pernah dikirim atau diterima:",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (historyList.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(150.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Belum ada riwayat broadcast.",
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            } else {
                                                androidx.compose.foundation.lazy.LazyColumn(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1.0f),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(historyList.size) { index ->
                                                        val item = historyList[index]
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                            ),
                                                            shape = RoundedCornerShape(10.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(12.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = item.type,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (item.type == "UPDATE") Color(0xFF2563EB) else Color(0xFFD97706),
                                                                        modifier = Modifier
                                                                            .background(
                                                                                color = (if (item.type == "UPDATE") Color(0xFFDBEAFE) else Color(0xFFFEF3C7)),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            )
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                    
                                                                    val dateStr = try {
                                                                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                                                        sdf.format(java.util.Date(item.updatedId))
                                                                    } catch (e: Exception) {
                                                                        "-"
                                                                    }
                                                                    Text(
                                                                        text = dateStr,
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                                    )
                                                                }

                                                                Text(
                                                                    text = item.title,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )

                                                                Text(
                                                                    text = item.message,
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    lineHeight = 15.sp
                                                                )

                                                                if (item.driveLink.isNotBlank()) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Link,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = item.driveLink,
                                                                            fontSize = 10.sp,
                                                                            color = MaterialTheme.colorScheme.primary,
                                                                            maxLines = 1,
                                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showHistoryDialog = false }) {
                                            Text("Tutup", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        if (historyList.isNotEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.clearBroadcastHistory()
                                                    Toast.makeText(context, "Riwayat berhasil dihapus!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Hapus Semua")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp), clip = false),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White, Color(0xFFCBD5E1))
                        )
                    )
                ) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                // Background "putih bersih" gradient
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
                                )
                                // Realistic diagonal glossy glass reflection sheen
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width * 0.4f, 0f)
                                    lineTo(size.width * 0.12f, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 0.0f)
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height)
                                    )
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Clickable Header for Collapsing
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSqlSetupExpanded = !isSqlSetupExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color(0xFFEFF6FF), CircleShape)
                                            .border(1.dp, Color(0xFFDBEAFE), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "Petunjuk SQL Setup (Supabase)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFF1F5F9), CircleShape)
                                        .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSqlSetupExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = if (isSqlSetupExpanded) "Collapse" else "Expand",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Expandable Content
                            AnimatedVisibility(visible = isSqlSetupExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    androidx.compose.material3.HorizontalDivider(
                                        color = Color(0xFFE2E8F0),
                                        thickness = 1.dp
                                    )

                                    Text(
                                        text = "Jika pengiriman gagal atau Anda mendapatkan error tabel tidak ditemukan, silakan salin query SQL di bawah ini dan jalankan pada SQL Editor di dashboard Supabase Anda:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569),
                                        lineHeight = 16.sp
                                    )

                                    val sqlQuery = """
                                        CREATE TABLE IF NOT EXISTS public.app_broadcast (
                                            id bigint primary key default 1,
                                            title text not null,
                                            message text not null,
                                            drive_link text default '',
                                            type text default 'UPDATE',
                                            is_active boolean default true,
                                            updated_id bigint default 0
                                        );

                                        -- Aktifkan Row Level Security (RLS) & Izinkan Akses Publik
                                        ALTER TABLE public.app_broadcast ENABLE ROW LEVEL SECURITY;
                                        CREATE POLICY "Allow public read" ON public.app_broadcast FOR SELECT USING (true);
                                        CREATE POLICY "Allow public all" ON public.app_broadcast FOR ALL USING (true);
                                    """.trimIndent()

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = sqlQuery,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sqlQuery))
                                            Toast.makeText(context, "Query SQL berhasil disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(2.dp, RoundedCornerShape(8.dp), clip = false),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF475569), Color(0xFF334155))
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Salin Query SQL", 
                                                    fontSize = 11.sp, 
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isDeviceBound) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.unbindDevice()
                            Toast.makeText(context, "Kaitan perangkat berhasil dilepas!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEE2E2),
                            contentColor = Color(0xFF7F1D1D)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Lepas Kaitan",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF7F1D1D)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LEPAS KAITAN PERANGKAT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF7F1D1D)
                        )
                    }
                }
            }
        }
    }
        }
    }
}

// =====================================
// TAB 4: HISTORY TAB (RIWAYAT LOG)
// =====================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(
    logs: List<AttendanceLog>,
    attendees: List<Attendee>,
    viewModel: AttendanceViewModel,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    
    // States for Supabase sync
    var isSyncing by remember { mutableStateOf(false) }
    
    // Dynamic calculation of who's Present and who's Absent today
    val todayString = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val selectedClass by viewModel.selectedClass.collectAsStateWithLifecycle()
    
    val registeredStudents = remember(attendees, selectedClass) {
        val allSiswa = attendees.filter { 
            val r = it.role.trim().lowercase()
            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
        }
        if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
            allSiswa
        } else {
            allSiswa.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
        }
    }
    
    val todayMasukLogs = remember(logs, todayString) {
        logs.filter { log ->
            val logDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(log.timestamp))
            logDateString == todayString && log.type == "MASUK"
        }
    }
    
    val studentLogsMap = remember(todayMasukLogs) { todayMasukLogs.associateBy { it.uid } }
    
    val presentStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s ->
            val status = studentLogsMap[s.uid]?.status
            status != null && status != "Ijin" && status != "Sakit"
        }
    }
    
    val ijinStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> studentLogsMap[s.uid]?.status == "Ijin" }
    }
    
    val sakitStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> studentLogsMap[s.uid]?.status == "Sakit" }
    }
    
    val absentStudents = remember(registeredStudents, studentLogsMap) {
        registeredStudents.filter { s -> !studentLogsMap.containsKey(s.uid) }
    }

    // Google Apps Script Code Template
    val appsScriptTemplate = APPS_SCRIPT_TEMPLATE

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gradient Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                        ),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "X-Degan QR",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sistem Absensi Pemindaian Real-time",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Riwayat Absensi",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Sinkronkan dengan Supabase DB, bagikan ke WhatsApp, & lihat log kehadiran",
                        fontSize = 13.sp,
                        color = Color(0xFFDBEAFE),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // CARD 1: SUPABASE MANUAL SYNC
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-10).dp)
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Sinkronisasi Manual Supabase",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "Unggah semua data siswa baru dan riwayat log kehadiran lokal Anda ke database cloud Supabase, serta unduh data terbaru agar tetap sinkron dua arah.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            isSyncing = true
                            viewModel.syncAllData { success, msg ->
                                isSyncing = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mensinkronisasikan data...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sinkronkan Sekarang", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // CARD 2: WHATSAPP NOTIFICATION FOR PARENT GROUP (WALI MURID)
        item {
            val isDeviceBound by viewModel.isDeviceBound.collectAsStateWithLifecycle()
            if (!isDeviceBound) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp)
                        .padding(horizontal = 16.dp)
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Laporan WhatsApp Wali Murid (Terkunci)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Fitur pengiriman laporan WhatsApp Wali Murid terkunci. Silakan lakukan aktivasi perangkat di menu Pengaturan terlebih dahulu untuk mengaktifkan fitur ini.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = {
                                Toast.makeText(context, "Perangkat belum aktif! Silakan masuk ke tab Pengaturan untuk aktivasi.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Laporan WA Wali Murid (Terkunci)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp)
                        .padding(horizontal = 16.dp)
                        .shadow(3.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color(0xFF128C7E) // WhatsApp signature color
                            )
                            Text(
                                text = "Laporan WhatsApp Wali Murid",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Bagikan rekapitulasi presensi siswa hari ini ke WhatsApp Group Wali Murid secara instan.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Stats indicators Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Terdaftar", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text("${registeredStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Hadir", fontSize = 9.sp, color = Color(0xFF047857), maxLines = 1)
                                    Text("${presentStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        // Stats indicators Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Izin", fontSize = 9.sp, color = Color(0xFF1D4ED8), maxLines = 1)
                                    Text("${ijinStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Sakit", fontSize = 9.sp, color = Color(0xFFD97706), maxLines = 1)
                                    Text("${sakitStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Alpa/Absen", fontSize = 9.sp, color = Color(0xFFBE123C), maxLines = 1)
                                    Text("${absentStudents.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (registeredStudents.isEmpty()) {
                                    Toast.makeText(context, "Daftarkan siswa dengan role 'Siswa' terlebih dahulu.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val reportText = buildString {
                                    append("*LAPORAN KEHADIRAN SISWA*\n")
                                    append("Hari/Tanggal: ${SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())}\n")
                                    append("=========================\n\n")
                                    
                                    append("✅ *HADIR:* (${presentStudents.size} Siswa)\n")
                                    if (presentStudents.isEmpty()) {
                                        append("- Belum ada siswa masuk\n")
                                    } else {
                                        presentStudents.forEachIndexed { index, student ->
                                            val log = todayMasukLogs.find { it.uid == student.uid }
                                            val timeStr = log?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp)) } ?: "--:--"
                                            val statusStr = log?.status ?: "Hadir"
                                            append("${index + 1}. ${student.name} ($timeStr - $statusStr)\n")
                                        }
                                    }
                                    append("\n")

                                    append("📬 *IZIN:* (${ijinStudents.size} Siswa)\n")
                                    if (ijinStudents.isEmpty()) {
                                        append("- Nihil\n")
                                    } else {
                                        ijinStudents.forEachIndexed { index, student ->
                                            append("${index + 1}. ${student.name}\n")
                                        }
                                    }
                                    append("\n")

                                    append("🤒 *SAKIT:* (${sakitStudents.size} Siswa)\n")
                                    if (sakitStudents.isEmpty()) {
                                        append("- Nihil\n")
                                    } else {
                                        sakitStudents.forEachIndexed { index, student ->
                                            append("${index + 1}. ${student.name}\n")
                                        }
                                    }
                                    append("\n")
                                    
                                    append("❌ *TANPA KETERANGAN / ALPA:* (${absentStudents.size} Siswa)\n")
                                    if (absentStudents.isEmpty()) {
                                        append("- Nihil (Semua hadir / berketerangan)\n")
                                    } else {
                                        absentStudents.forEachIndexed { index, student ->
                                            append("${index + 1}. ${student.name}\n")
                                        }
                                    }
                                    append("\n_Laporan presensi otomatis via X-Degan QR_")
                                }

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, reportText)
                                }
                                shareIntent.setPackage("com.whatsapp")

                                try {
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    // Fallback to normal chooser if WA is not installed
                                    val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                    }, "Kirim Laporan Wali Murid")
                                    context.startActivity(chooser)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366) // WA brand color
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Laporan WA Wali Murid", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // --- SUBTITLE LOGS ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat Log Absensi (${logs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { showConfirmClearDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-20).dp)
                        .padding(horizontal = 16.dp)
                        .shadow(3.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("Log Riwayat Masih Kosong", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Absensi scan yang sukses hari ini akan muncul di sini.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(logs) { log ->
                val formattedTime = remember(log.timestamp) {
                    SimpleDateFormat("HH:mm  •  dd MMM", Locale("id", "ID")).format(Date(log.timestamp))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-20).dp)
                        .padding(horizontal = 16.dp)
                        .shadow(1.dp, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = log.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Masuk / Pulang badge
                                val typeColor = if (log.type == "MASUK") Color(0xFF0369A1) else Color(0xFF64748B)
                                val typeBg = if (log.type == "MASUK") Color(0xFFE0F2FE) else Color(0xFFF1F5F9)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(typeBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = log.type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = typeColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Role: ${log.role}  •  $formattedTime",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            log.sessionName?.let { session ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Lokasi: $session",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Attendance Status Badge
                        val statusBgColor = if (log.status == "Tepat Waktu") Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        val statusTextColor = if (log.status == "Tepat Waktu") Color(0xFF047857) else Color(0xFFBE123C)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBgColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = log.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh riwayat log absensi? Tindakan ini permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// =====================================
// HELPER VIEW COMPONENTS & DIALOGS
// =====================================

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun ScanResultOverlay(
    result: ScanResultState,
    onDismiss: () -> Unit
) {
    // Beautiful spring entry animations
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(0.6f) }
    val alphaAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        )
    }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 350)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer(
                    scaleX = scaleAnim.value,
                    scaleY = scaleAnim.value,
                    alpha = alphaAnim.value
                )
                .shadow(16.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Status Icon with pulsing animated effect
                val pulseScale = remember { androidx.compose.animation.core.Animatable(0.8f) }
                LaunchedEffect(Unit) {
                    pulseScale.animateTo(
                        targetValue = 1.1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer(
                            scaleX = pulseScale.value,
                            scaleY = pulseScale.value
                        )
                        .clip(CircleShape)
                        .background(
                            if (result.success) Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (result.success) Color(0xFF059669) else Color(0xFFE11D48),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = result.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAttendeeDialog(
    activeSchoolId: String,
    activeSchoolName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, customUid: String?) -> Unit,
    onImportClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var customUid by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    var isUidError by remember { mutableStateOf(false) }
    var isRoleError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrasi Siswa Baru", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (isNameError) isNameError = false
                    },
                    label = { Text("Nama Siswa") },
                    placeholder = { Text("Misal: Ahmad Fauzi") },
                    isError = isNameError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isNameError) {
                    Text("Nama siswa tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = customUid,
                    onValueChange = {
                        customUid = it
                        if (isUidError) isUidError = false
                    },
                    label = { Text("NISN (ID Siswa)") },
                    placeholder = { Text("Misal: 1234567890") },
                    isError = isUidError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isUidError) {
                    Text("NISN tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = role,
                    onValueChange = {
                        role = it
                        if (isRoleError) isRoleError = false
                    },
                    label = { Text("Kelas") },
                    placeholder = { Text("Misal: XII IPA 1 atau Kelas 3") },
                    isError = isRoleError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isRoleError) {
                    Text("Kelas tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = activeSchoolId,
                    onValueChange = {},
                    label = { Text("NPSN (ID Sekolah)") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = activeSchoolName,
                    onValueChange = {},
                    label = { Text("Nama Sekolah") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onImportClick() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Impor Banyak Siswa Sekaligus",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Impor massal dari file Excel, CSV, atau Google Spreadsheet",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Buka",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameBlank = name.isBlank()
                    val uidBlank = customUid.isBlank()
                    val roleBlank = role.isBlank()

                    if (nameBlank) isNameError = true
                    if (uidBlank) isUidError = true
                    if (roleBlank) isRoleError = true

                    if (!nameBlank && !uidBlank && !roleBlank) {
                        onConfirm(name, role, customUid)
                    }
                }
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Lokasi / Kelas QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Buat titik penanda QR Code untuk dipindai oleh peserta absensi mandiri.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (isError) isError = false
                    },
                    label = { Text("Nama Lokasi atau Kelas") },
                    placeholder = { Text("Misal: Aula Kantor Utama") },
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                if (isError) {
                    Text("Nama lokasi tidak boleh kosong", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(title)
                    }
                }
            ) {
                Text("Buat QR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun QrDisplayerDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success feedback chip for immediate confirmation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color(0xFFECFDF5), RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "QR Berhasil Dibuat",
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // Holographic laser scan line animation
                val infiniteTransition = rememberInfiniteTransition(label = "scanline")
                val scanLineProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanLineProgress"
                )

                // Render QR code Bitmap reactively inside a white protective box for scan contrast
                val qrBitmap = remember(content) { QrHelper.generateQrCode(content, 400) }
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Laser scan line overlay
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * scanLineProgress
                        drawLine(
                            color = Color(0xFF10B981),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 3.dp.toPx(),
                            pathEffect = null,
                            alpha = 0.8f
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Gunakan QR Code ini saat pemindaian absensi.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Kode Raw: $content",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProfileSelectorDialog(
    attendees: List<Attendee>,
    onDismiss: () -> Unit,
    onSelect: (Attendee) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Pilih Profil Absen Mandiri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (attendees.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Silakan daftarkan siswa baru di tab 'Siswa' terlebih dahulu.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attendees) { attendee ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(attendee) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = attendee.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${attendee.role}  •  ID: ${attendee.uid}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf(0) } // 0: Google Sheets, 1: Supabase DB
    var spreadsheetUrlInput by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    // File Picker for local CSV files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusMessage = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    viewModel.importFromCsvText(content) { success, count ->
                        isLoading = false
                        if (success) {
                            isSuccessMessage = true
                            statusMessage = "Berhasil mengimpor $count siswa dari file CSV!"
                        } else {
                            isSuccessMessage = false
                            statusMessage = "Gagal mengimpor file. Pastikan format CSV valid."
                        }
                    }
                } else {
                    isLoading = false
                    isSuccessMessage = false
                    statusMessage = "File tidak dapat dibaca atau kosong."
                }
            } catch (e: Exception) {
                isLoading = false
                isSuccessMessage = false
                statusMessage = "Error: ${e.localizedMessage}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Impor Data Siswa", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Method Selection Tab/Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Google Sheets", "Supabase DB").forEachIndexed { index, title ->
                        val selected = selectedMethod == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { 
                                    selectedMethod = index 
                                    statusMessage = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Render Content based on selected method
                when (selectedMethod) {
                    0 -> { // Google Sheets URL
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Langkah Impor dari Google Spreadsheet:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "1. Buat daftar siswa di Google Sheets dengan nama kolom: Nama Siswa, NISN, Kelas.\n" +
                                        "2. Klik tombol Bagikan -> Ubah akses menjadi \"Siapa saja yang memiliki link dapat melihat\".\n" +
                                        "3. Salin URL Google Sheets dan tempelkan di bawah ini.\n" +
                                        "4. Klik tombol \"Tarik Data dari Spreadsheet\".",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            
                            OutlinedTextField(
                                value = spreadsheetUrlInput,
                                onValueChange = { spreadsheetUrlInput = it },
                                label = { Text("Link Google Spreadsheet", fontSize = 11.sp) },
                                placeholder = { Text("https://docs.google.com/spreadsheets/d/...", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            
                            Button(
                                onClick = {
                                    if (spreadsheetUrlInput.isBlank()) {
                                        isSuccessMessage = false
                                        statusMessage = "Masukkan URL Google Spreadsheet terlebih dahulu."
                                        return@Button
                                    }
                                    isLoading = true
                                    statusMessage = null
                                    viewModel.importFromSpreadsheetUrl(spreadsheetUrlInput) { success, count, msg ->
                                        isLoading = false
                                        isSuccessMessage = success
                                        statusMessage = msg
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981) // Emerald Green
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Text("Tarik Data dari Spreadsheet", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            
                            // Alternative local CSV
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Atau impor file .CSV lokal:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { filePickerLauncher.launch("text/comma-separated-values|text/plain|application/octet-stream|*/*") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pilih File .CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    1 -> { // Pull from Supabase
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Impor Melalui Database Supabase:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Tarik daftar siswa langsung dari database Supabase PostgreSQL ke HP Anda. Pastikan Anda sudah mengonfigurasi URL dan API Key dengan benar di menu Pengaturan -> Supabase.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    statusMessage = null
                                    viewModel.importFromSupabase { success, count, msg ->
                                        isLoading = false
                                        isSuccessMessage = success
                                        statusMessage = msg
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tarik Data Siswa dari Supabase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Status Message display
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Memproses data...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                statusMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccessMessage) Color(0xFFECFDF5) else Color(0xFFFFF1F2)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (isSuccessMessage) Color(0xFF047857) else Color(0xFFBE123C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Selesai")
            }
        }
    )
}

@Composable
fun StudentCardDialog(
    attendee: Attendee,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val photoDir = java.io.File(context.filesDir, "photos")
                if (!photoDir.exists()) photoDir.mkdirs()
                val file = java.io.File(photoDir, "student_${attendee.uid}.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
                out.close()
                viewModel.updateAttendeePhoto(attendee, file.absolutePath)
                Toast.makeText(context, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Indonesian School Student Card Header (Dark Blue)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                                )
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "KARTU TANDA SISWA",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "SISTEM ABSENSI ONLINE - ABSENQR",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Body
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Profile Photo / Avatar
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        )
                                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            cameraLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val studentBitmap = remember(attendee.photoPath) {
                                    if (!attendee.photoPath.isNullOrEmpty()) {
                                        try {
                                            android.graphics.BitmapFactory.decodeFile(attendee.photoPath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else {
                                        null
                                    }
                                }

                                if (studentBitmap != null) {
                                    Image(
                                        bitmap = studentBitmap.asImageBitmap(),
                                        contentDescription = "Foto Siswa",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Photo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                // Mini Camera Overlay Badge
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Ambil Foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            
                            val isSiswa = remember(attendee.role) {
                                val r = attendee.role.trim().lowercase()
                                r == "siswa" || (r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu")
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSiswa) Color(0xFFE0F2FE) else Color(0xFFF1F5F9)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val roleText = attendee.role.uppercase()
                                Text(
                                    text = roleText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSiswa) Color(0xFF0369A1) else Color(0xFF475569)
                                )
                            }
                        }

                        // Middle Info Details
                        Column(
                            modifier = Modifier.weight(1.5f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "NAMA LENGKAP",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = attendee.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "NOMOR IDENTITAS (UID)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = attendee.uid,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right QR Code with Scanner Scanline Animation
                        val qrBitmap = remember(attendee.uid) { QrHelper.generateQrCode(attendee.uid, 250) }
                        val infiniteTransition = rememberInfiniteTransition(label = "scanline_card")
                        val scanLineProgress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scanLineProgress_card"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Laser scan line overlay
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * scanLineProgress
                                    drawLine(
                                        color = Color(0xFF10B981),
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = 2.dp.toPx(),
                                        pathEffect = null,
                                        alpha = 0.8f
                                    )
                                }
                            }
                            Text(
                                text = "PINDAI SAYA",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Card Footer Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Tunjukkan kartu ini kepada Guru untuk memindai presensi.",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons below ID Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        )
                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE28743))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ambil Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ID_Siswa", attendee.uid)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ID Siswa disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin ID", fontSize = 11.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AttendeeDetailDialog(
    attendee: Attendee,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Detail Data Siswa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E40AF)
                )

                val studentBitmap = remember(attendee.photoPath) {
                    if (!attendee.photoPath.isNullOrEmpty()) {
                        try {
                            android.graphics.BitmapFactory.decodeFile(attendee.photoPath)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDBEAFE)),
                    contentAlignment = Alignment.Center
                ) {
                    if (studentBitmap != null) {
                        Image(
                            bitmap = studentBitmap.asImageBitmap(),
                            contentDescription = "Foto ${attendee.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = attendee.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailItemRow(
                        icon = Icons.Default.Person,
                        label = "Nama Lengkap",
                        value = attendee.name
                    )
                    DetailItemRow(
                        icon = Icons.Default.QrCode,
                        label = "ID / UID / NISN",
                        value = attendee.uid
                    )
                    DetailItemRow(
                        icon = Icons.Default.Info,
                        label = "Kelas / Peran / Jabatan",
                        value = attendee.role
                    )
                    DetailItemRow(
                        icon = Icons.Default.School,
                        label = "Nama Sekolah",
                        value = attendee.schoolName.orEmpty().ifBlank { "-" }
                    )
                    DetailItemRow(
                        icon = Icons.Default.School,
                        label = "NPSN Sekolah (School ID)",
                        value = attendee.schoolId.orEmpty().ifBlank { "-" }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (attendee.synced) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (attendee.synced) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Status Sinkronisasi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (attendee.synced) "Tersinkronisasi ke Google Sheets" else "Data Lokal (Belum Sinkron)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (attendee.synced) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E40AF)
                    )
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DetailItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1E40AF),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var selectedClassInput by remember { mutableStateOf(viewModel.getSelectedClass()) }
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val activeSchoolId by viewModel.activeSchoolId.collectAsStateWithLifecycle()
    val classOptions = remember(attendees, activeSchoolId) {
        val schoolFiltered = attendees.filter {
            it.schoolId == activeSchoolId
        }
        val studentRoles = schoolFiltered.map { it.role.trim() }.filter {
            val r = it.lowercase()
            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu" && r.isNotBlank()
        }.distinct().sorted()
        val baseList = listOf("Semua Kelas") + studentRoles
        if (baseList.size == 1) {
            listOf("Semua Kelas", "Siswa", "Kelas 1", "Kelas 2", "Kelas 3", "Kelas 4", "Kelas 5", "Kelas 6")
        } else {
            baseList
        }
    }
    var showClassDropdown by remember { mutableStateOf(false) }
    var teacherNameInput by remember { mutableStateOf(viewModel.getTeacherName()) }
    var jamMasukInput by remember(selectedClassInput) {
        mutableStateOf(viewModel.getJamMasukForClass(selectedClassInput))
    }
    var jamPulangInput by remember(selectedClassInput) {
        mutableStateOf(viewModel.getJamPulangForClass(selectedClassInput))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pengaturan Umum & Kelas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Identity & Class Filter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Identitas Kelas & Guru:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedClassInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kelas yang Diaktifkan") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showClassDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showClassDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showClassDropdown,
                            onDismissRequest = { showClassDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            classOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 12.sp) },
                                    onClick = {
                                        selectedClassInput = option
                                        showClassDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "Aplikasi hanya akan menampilkan & mencatat kehadiran untuk siswa dengan Kelas/Role ini. Pilih 'Semua' untuk menampilkan semua.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = teacherNameInput,
                        onValueChange = { teacherNameInput = it },
                        label = { Text("Nama Guru / Wali Kelas") },
                        placeholder = { Text("Nama Lengkap...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = jamMasukInput,
                            onValueChange = { jamMasukInput = it },
                            label = { Text("Jam Masuk (HH:mm)") },
                            placeholder = { Text("07:30") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )

                        OutlinedTextField(
                            value = jamPulangInput,
                            onValueChange = { jamPulangInput = it },
                            label = { Text("Jam Pulang (HH:mm)") },
                            placeholder = { Text("13:00") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                    }

                    Text(
                        text = "Jam batas masuk & pulang ini digunakan untuk menghitung status (Tepat Waktu, Terlambat, atau Pulang Awal) khusus untuk Kelas '$selectedClassInput'.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }

            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveSelectedClass(selectedClassInput.trim())
                    viewModel.saveTeacherName(teacherNameInput.trim())
                    viewModel.saveJamMasukForClass(selectedClassInput.trim(), jamMasukInput.trim())
                    viewModel.saveJamPulangForClass(selectedClassInput.trim(), jamPulangInput.trim())
                    Toast.makeText(context, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simpan & Tutup", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAttendanceDialog(
    attendee: Attendee,
    onDismiss: () -> Unit,
    onConfirm: (type: String, status: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Absen Manual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Siswa: ${attendee.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pilih keterangan atau status kehadiran untuk siswa ini tanpa memindai QR Code:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sakit Option (Amber/Yellow)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Sakit") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBEB)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Sakit",
                                tint = Color(0xFFD97706)
                            )
                        }
                        Column {
                            Text(
                                text = "Sakit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "Tandai siswa berhalangan karena Sakit",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }

                // Izin Option (Blue)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Ijin") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEFF6FF)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Izin",
                                tint = Color(0xFF1D4ED8)
                            )
                        }
                        Column {
                            Text(
                                text = "Izin / Ijin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1D4ED8)
                            )
                            Text(
                                text = "Tandai siswa berhalangan dengan Izin resmi",
                                fontSize = 11.sp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                // Alternative manual Hadir/Masuk (Green)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("MASUK", "Tepat Waktu") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFECFDF5)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Hadir",
                                tint = Color(0xFF059669)
                            )
                        }
                        Column {
                            Text(
                                text = "Hadir Manual (Masuk)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF047857)
                            )
                            Text(
                                text = "Absen masuk manual tanpa pindai QR",
                                fontSize = 11.sp,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }

                // Alternative manual Pulang (Slate/Grey)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm("PULANG", "Tepat Waktu") },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF1F5F9)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Pulang",
                                tint = Color(0xFF475569)
                            )
                        }
                        Column {
                            Text(
                                text = "Pulang Manual",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Absen pulang manual tanpa pindai QR",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// =====================================
// TAB 5: SCHOOL CONFIGURATION TAB (SEKOLAH)
// =====================================
@Composable
fun SchoolTab(
    schoolName: String,
    schoolAddress: String,
    schoolLogoPath: String
) {
    val schoolLogoBitmap = remember(schoolLogoPath) {
        if (!schoolLogoPath.isNullOrEmpty()) {
            try {
                android.graphics.BitmapFactory.decodeFile(schoolLogoPath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gradient Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                        ),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "X-Degan QR",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sistem Absensi Pemindaian Real-time",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Profil Sekolah",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Identitas, alamat, dan logo resmi sekolah aktif",
                        fontSize = 13.sp,
                        color = Color(0xFFDBEAFE),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // School Preview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-10).dp)
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Logo circle with fallback icon
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (schoolLogoBitmap != null) {
                            Image(
                                bitmap = schoolLogoBitmap.asImageBitmap(),
                                contentDescription = "Logo Sekolah",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Logo Sekolah",
                                tint = Color(0xFF1E40AF),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = schoolName.ifBlank { "Nama Sekolah Belum Diisi" },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = schoolAddress.ifBlank { "Alamat Sekolah Belum Diisi" },
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// =====================================
// MANUAL ATTENDANCE STUDENT SELECTOR
// =====================================
@Composable
fun ManualSelectStudentDialog(
    attendees: List<Attendee>,
    onDismiss: () -> Unit,
    onSelect: (Attendee) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Dynamic Role Filter: Default to "Siswa" if present, otherwise "Semua"
    val rolesList = remember(attendees) {
        val uniqueRoles = attendees.map { it.role.trim() }.filter { it.isNotEmpty() }.distinct()
        val list = mutableListOf("Semua")
        list.addAll(uniqueRoles)
        list
    }
    
    var selectedRoleFilter by remember(rolesList) {
        val defaultRole = if (rolesList.contains("Siswa")) "Siswa" else "Semua"
        mutableStateOf(defaultRole)
    }
    
    // Case-insensitive, robust search that matches name or uid, and trimmed roles
    val filteredStudents = remember(attendees, searchQuery, selectedRoleFilter) {
        attendees.filter { attendee ->
            val matchesRole = if (selectedRoleFilter == "Semua") {
                true
            } else {
                attendee.role.trim().equals(selectedRoleFilter, ignoreCase = true)
            }
            val matchesSearch = attendee.name.contains(searchQuery, ignoreCase = true) || 
                                attendee.uid.contains(searchQuery, ignoreCase = true)
            matchesRole && matchesSearch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Absen Manual (Tanpa QR)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Pilih siswa untuk mencatatkan kehadiran secara manual",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama atau NISN/NIP...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // Role Filter Chips Row for easy filtering
                if (rolesList.size > 1) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(rolesList) { roleName ->
                            val isSelected = selectedRoleFilter == roleName
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(containerColor)
                                    .clickable { selectedRoleFilter = roleName }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = roleName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // List of filtered attendees
                if (filteredStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Siswa tidak ditemukan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Pastikan role & filter pencarian Anda sesuai.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { student ->
                            val studentBitmap = remember(student.photoPath) {
                                if (!student.photoPath.isNullOrEmpty()) {
                                    try {
                                        android.graphics.BitmapFactory.decodeFile(student.photoPath)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(student) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Profile photo or initials
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (studentBitmap != null) {
                                            Image(
                                                bitmap = studentBitmap.asImageBitmap(),
                                                contentDescription = student.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = student.name.take(2).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = student.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            // Tiny role indicator tag
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = student.role,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        Text(
                                            text = "ID / UID: ${student.uid}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Batal")
                }
            }
        }
    }
}

// =====================================
// DATA CLASS FOR STUDENT STATS
// =====================================
data class StudentAttendanceStats(
    val tepatWaktu: Int,
    val terlambat: Int,
    val sakit: Int,
    val izin: Int,
    val hadir: Int,
    val pulangAwal: Int = 0
)

// =====================================
// TAB 6: STUDENT ATTENDANCE RECAP TAB
// =====================================
@Composable
fun StudentStatusListCard(
    title: String,
    emptyMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    badgeTextColor: Color,
    statusLabel: String,
    students: List<Pair<Attendee, AttendanceLog?>>,
    isSupabaseEnabled: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = badgeTextColor)
                    Text(
                        text = "$title (${students.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isSupabaseEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0F2FE))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Supabase",
                            tint = Color(0xFF0369A1),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Supabase",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }
                }
            }
            
            Divider(color = Color(0xFFF1F5F9))

            if (students.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = emptyMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                students.forEachIndexed { idx, (student, log) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ID: ${student.uid}  •  Kelas: ${student.role}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeColor)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (log != null) {
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                                    "$statusLabel ($timeStr)"
                                } else {
                                    statusLabel
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                    }

                    if (idx < students.lastIndex) {
                        Divider(color = Color(0xFFF8FAFC))
                    }
                }
            }
        }
    }
}

@Composable
fun RekapTab(
    attendees: List<Attendee>,
    logs: List<AttendanceLog>,
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var selectedReportSource by remember { mutableStateOf("LOKAL") }
    val isSupabaseEnabled by viewModel.isSupabaseEnabledState.collectAsStateWithLifecycle()
    val isManualSyncing by viewModel.isManualSyncing.collectAsStateWithLifecycle()
    val activeSchoolId by viewModel.activeSchoolId.collectAsStateWithLifecycle()

    // Auto-pull/sync from Supabase on tab load or activeSchoolId change to update stats
    LaunchedEffect(activeSchoolId, isSupabaseEnabled) {
        if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank() && activeSchoolId != "SCH-DEFAULT") {
            viewModel.syncAllData { success, msg ->
                android.util.Log.d("RekapTab", "Auto-sync on RekapTab: success=$success, msg=$msg")
            }
        }
    }

    val isDeviceBound by viewModel.isDeviceBound.collectAsStateWithLifecycle()
    val bindingDeviceName by viewModel.bindingDeviceName.collectAsStateWithLifecycle()
    val sharedPrefs = remember { context.getSharedPreferences("app_devices_prefs", Context.MODE_PRIVATE) }
    var devicesList by remember {
        mutableStateOf(
            run {
                val savedString = sharedPrefs.getString("devices_list", "") ?: ""
                if (savedString.isBlank()) {
                    listOf(
                        "Tablet Absensi Lobby Utama",
                        "Terminal Gerbang Depan",
                        "Smartphone Piket Guru",
                        "Tablet Ruang Staf & Guru",
                        "Terminal Lab Komputer",
                        "Smartphone Admin Absensi"
                    )
                } else {
                    savedString.split("##").filter { it.isNotBlank() }
                }
            }
        )
    }

    fun saveDevices(list: List<String>) {
        devicesList = list
        sharedPrefs.edit().putString("devices_list", list.joinToString("##")).apply()
    }

    LaunchedEffect(bindingDeviceName, isDeviceBound) {
        if (isDeviceBound && bindingDeviceName.isNotBlank() && !devicesList.contains(bindingDeviceName)) {
            saveDevices(listOf(bindingDeviceName) + devicesList)
        }
    }

    val todaySdfYmd = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val todayStrYmd = remember { todaySdfYmd.format(Date()) }
    val todayLogs = remember(logs) {
        logs.filter { log ->
            todaySdfYmd.format(Date(log.timestamp)) == todayStrYmd
        }.sortedByDescending { it.timestamp }
    }

    var searchQuery by remember { mutableStateOf("") }
    
    // Select the currently chosen settings class
    val selectedClass by viewModel.selectedClass.collectAsStateWithLifecycle()

    // Filter students belonging to the chosen class setting
    val classSiswa = remember(attendees, selectedClass) {
        val students = attendees.filter { 
            val r = it.role.trim().lowercase()
            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
        }
        if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
            students
        } else {
            students.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
        }
    }

    // Filter attendees of chosen class for general export table
    val activeClassAttendeesForTable = remember(attendees, selectedClass) {
        if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
            attendees
        } else {
            attendees.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
        }
    }

    // Roles list for export filter
    val rolesList = remember(activeClassAttendeesForTable) {
        val uniqueRoles = activeClassAttendeesForTable.map { it.role.trim() }.filter { it.isNotEmpty() }.distinct()
        val list = mutableListOf("Semua")
        list.addAll(uniqueRoles)
        list
    }
    
    var selectedRoleFilter by remember(rolesList) {
        val defaultRole = if (rolesList.contains("Siswa")) "Siswa" else "Semua"
        mutableStateOf(defaultRole)
    }

    val filteredAttendees = remember(activeClassAttendeesForTable, searchQuery, selectedRoleFilter) {
        activeClassAttendeesForTable.filter { attendee ->
            val matchesRole = if (selectedRoleFilter == "Semua") {
                true
            } else {
                attendee.role.trim().equals(selectedRoleFilter, ignoreCase = true)
            }
            val matchesSearch = attendee.name.contains(searchQuery, ignoreCase = true) || 
                                attendee.uid.contains(searchQuery, ignoreCase = true)
            matchesRole && matchesSearch
        }
    }

    // --- HARIAN DATA ---
    val todaySdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { todaySdf.format(Date()) }

    val todayLogsFull = remember(logs, todayStr) {
        logs.filter { log ->
            todaySdf.format(Date(log.timestamp)) == todayStr
        }
    }

    // Today's logs filtered to only active class/students
    val todayLogsFiltered = remember(todayLogsFull, classSiswa) {
        todayLogsFull.filter { log ->
            classSiswa.any { it.uid == log.uid }
        }
    }

    // Today's attendance counts for class
    val todayHadirCount = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "MASUK" && (log.status.equals("Tepat Waktu", ignoreCase = true) || log.status.equals("Terlambat", ignoreCase = true))
        }
    }

    val todaySakitCount = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "MASUK" && log.status.equals("Sakit", ignoreCase = true)
        }
    }

    val todayIzinCount = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "MASUK" && (log.status.equals("Ijin", ignoreCase = true) || log.status.equals("Izin", ignoreCase = true))
        }
    }

    val todayAlphaCount = remember(classSiswa, todayLogsFiltered) {
        val enteredUids = todayLogsFiltered.filter { it.type == "MASUK" }.map { it.uid }.toSet()
        classSiswa.count { it.uid !in enteredUids }
    }

    val tepatWaktuToday = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "MASUK" && log.status.equals("Tepat Waktu", ignoreCase = true)
        }
    }

    val terlambatToday = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "MASUK" && log.status.equals("Terlambat", ignoreCase = true)
        }
    }

    val pulangAwalToday = remember(todayLogsFiltered) {
        todayLogsFiltered.count { log ->
            log.type == "PULANG" && log.status.equals("Pulang Awal", ignoreCase = true)
        }
    }

    // --- BULANAN DATA & SELECTION ---
    var tableOption by remember { mutableStateOf("HARIAN") }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    val monthNames = remember { listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember") }
    val yearList = remember { listOf(2024, 2025, 2026, 2027, 2028, 2029, 2030) }
    val selectedMonthName = monthNames.getOrNull(selectedMonth) ?: "Bulan Ini"

    val monthlyLogs = remember(logs, selectedMonth, selectedYear) {
        logs.filter { log ->
            val cal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
            cal.get(Calendar.MONTH) == selectedMonth && cal.get(Calendar.YEAR) == selectedYear
        }
    }

    // Monthly logs filtered to active class
    val monthlyLogsFiltered = remember(monthlyLogs, classSiswa) {
        monthlyLogs.filter { log ->
            classSiswa.any { it.uid == log.uid }
        }
    }

    val monthlyStatsMap = remember(monthlyLogs, classSiswa) {
        val logGroup = monthlyLogs.groupBy { it.uid }
        classSiswa.associate { attendee ->
            val userLogs = logGroup[attendee.uid] ?: emptyList()
            val masukLogs = userLogs.filter { it.type == "MASUK" }
            
            val tepatWaktu = masukLogs.count { it.status.equals("Tepat Waktu", ignoreCase = true) }
            val terlambat = masukLogs.count { it.status.equals("Terlambat", ignoreCase = true) }
            val sakit = masukLogs.count { it.status.equals("Sakit", ignoreCase = true) }
            val izin = masukLogs.count { it.status.equals("Ijin", ignoreCase = true) || it.status.equals("Izin", ignoreCase = true) }
            val totalHadir = tepatWaktu + terlambat
            
            val pulangLogs = userLogs.filter { it.type == "PULANG" }
            val pulangAwal = pulangLogs.count { it.status.equals("Pulang Awal", ignoreCase = true) }
            
            attendee.uid to StudentAttendanceStats(
                tepatWaktu = tepatWaktu,
                terlambat = terlambat,
                sakit = sakit,
                izin = izin,
                hadir = totalHadir,
                pulangAwal = pulangAwal
            )
        }
    }

    val totalActiveDays = remember(monthlyLogsFiltered) {
        monthlyLogsFiltered.map { log ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(log.timestamp))
        }.distinct().size
    }

    val totalMonthlyHadir = remember(monthlyLogsFiltered) {
        monthlyLogsFiltered.count { log ->
            log.type == "MASUK" && (log.status.equals("Tepat Waktu", ignoreCase = true) || log.status.equals("Terlambat", ignoreCase = true))
        }
    }

    val totalMonthlySakit = remember(monthlyLogsFiltered) {
        monthlyLogsFiltered.count { log ->
            log.type == "MASUK" && log.status.equals("Sakit", ignoreCase = true)
        }
    }

    val totalMonthlyIzin = remember(monthlyLogsFiltered) {
        monthlyLogsFiltered.count { log ->
            log.type == "MASUK" && (log.status.equals("Ijin", ignoreCase = true) || log.status.equals("Izin", ignoreCase = true))
        }
    }

    // Horizontal Scrolling Menu Option
    var selectedMenu by remember { mutableStateOf("Statistik") }

    val menus = listOf(
        "Statistik" to Icons.Default.Assessment,
        "Export" to Icons.Default.Download,
        "Peringkat" to Icons.Default.Star,
        "Tepat Waktu" to Icons.Default.CheckCircle,
        "Terlambat" to Icons.Default.Schedule,
        "Pulang Awal" to Icons.Default.Logout,
        "Alpha" to Icons.Default.Cancel
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Gradient Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                        ),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.mipmap.ic_launcher),
                                    contentDescription = "App Icon",
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column {
                                Text(
                                    text = "X-Degan QR",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sistem Absensi Pemindaian Real-time",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Rekap & Statistik",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Laporan harian, peringkat tingkat ketidakhadiran, & statistik bulanan",
                        fontSize = 13.sp,
                        color = Color(0xFFDBEAFE),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(30.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                            .clickable(enabled = !isManualSyncing) {
                                viewModel.syncAllData { success, msg ->
                                    android.widget.Toast.makeText(context, "Sinkronisasi Supabase: $msg", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isManualSyncing) Icons.Default.Sync else Icons.Default.CloudQueue,
                                contentDescription = "Status Supabase",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isManualSyncing) "Sinkronisasi Cloud..." else "Database Supabase Aktif",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isManualSyncing) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Unified WhatsApp Report Panel with beautiful, cohesive design
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF9FBF9) // Clean background with a very light WhatsApp teal-green warmth
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2F0E7)) // Soft green accent border
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Row: Laporan WhatsApp Title & Description
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // WhatsApp-colored visual accent circle
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Laporan WhatsApp",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Kirim rekap absensi harian ke wali murid",
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        
                        // Small status label based on active source choice
                        if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedReportSource == "SUPABASE") Color(0xFFE0F2FE) else Color(0xFFF1F5F9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedReportSource == "SUPABASE") Color(0xFF0284C7) else Color(0xFF64748B))
                                )
                                Text(
                                    text = if (selectedReportSource == "SUPABASE") "Cloud Sync" else "Lokal Offline",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedReportSource == "SUPABASE") Color(0xFF0369A1) else Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    // Source data selector for WhatsApp report
                    if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SUMBER DATA LAPORAN",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.5.sp
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "LOKAL" to Triple("Database Lokal", Icons.Default.Storage, Color(0xFF475569)),
                                    "SUPABASE" to Triple("Supabase Cloud", Icons.Default.Cloud, Color(0xFF0284C7))
                                ).forEach { (value, info) ->
                                    val (label, iconOption, activeColor) = info
                                    val isSelected = selectedReportSource == value
                                    
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) activeColor.copy(alpha = 0.12f) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) activeColor.copy(alpha = 0.4f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedReportSource = value }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = iconOption,
                                            contentDescription = null,
                                            tint = if (isSelected) activeColor else Color(0xFF64748B),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected) activeColor else Color(0xFF475569),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sharing Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Button 1: Kirim Laporan WA
                        Button(
                            onClick = {
                                if (!isDeviceBound) {
                                    Toast.makeText(context, "Perangkat belum aktif! Silakan masuk ke tab Pengaturan untuk aktivasi.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val generateAndSendReport = { finalAttendees: List<Attendee>, finalTodayLogsFull: List<AttendanceLog> ->
                                        val teacherName = viewModel.getTeacherName()
                                        val selectedClass = viewModel.getSelectedClass()
                                        val todayFormatted = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())

                                        val reportText = buildString {
                                            append("*LAPORAN KEHADIRAN SISWA*\n")
                                            if (selectedClass.isNotBlank() && !selectedClass.equals("Semua", ignoreCase = true) && !selectedClass.equals("Semua Kelas", ignoreCase = true)) {
                                                append("Kelas: $selectedClass\n")
                                            }
                                            if (teacherName.isNotBlank()) {
                                                append("Guru Kelas: $teacherName\n")
                                            }
                                            append("Hari/Tanggal: $todayFormatted\n")
                                            if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                                append("Sumber Data: ${if (selectedReportSource == "SUPABASE") "Supabase (Cloud)" else "Database Lokal"}\n")
                                            }
                                            append("=========================\n\n")

                                            val allSiswa = finalAttendees.filter { 
                                                val r = it.role.trim().lowercase()
                                                r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
                                            }
                                            val registered = if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
                                                allSiswa
                                            } else {
                                                allSiswa.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
                                            }
                                            val present = mutableListOf<String>()
                                            val sakit = mutableListOf<String>()
                                            val izin = mutableListOf<String>()
                                            val alpha = mutableListOf<String>()

                                            val studentLogs = finalTodayLogsFull.associateBy { it.uid }

                                            registered.forEach { student ->
                                                val log = studentLogs[student.uid]
                                                if (log != null) {
                                                    when {
                                                        log.status.equals("Sakit", ignoreCase = true) -> sakit.add(student.name)
                                                        log.status.equals("Ijin", ignoreCase = true) || log.status.equals("Izin", ignoreCase = true) -> izin.add(student.name)
                                                        else -> present.add("${student.name} (${log.status})")
                                                    }
                                                } else {
                                                    alpha.add(student.name)
                                                }
                                            }

                                            append("🟢 *HADIR (${present.size}/${registered.size})*:\n")
                                            if (present.isEmpty()) append("- Nihil\n") else present.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                                            append("\n")

                                            append("🟡 *IZIN (${izin.size})*:\n")
                                            if (izin.isEmpty()) append("- Nihil\n") else izin.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                                            append("\n")

                                            append("🟠 *SAKIT (${sakit.size})*:\n")
                                            if (sakit.isEmpty()) append("- Nihil\n") else sakit.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                                            append("\n")

                                            append("🔴 *ALPA / BELUM ABSEN (${alpha.size})*:\n")
                                            if (alpha.isEmpty()) append("- Nihil\n") else alpha.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                                            append("\n")

                                            append("Terima kasih atas perhatian Bapak/Ibu Wali Murid.\n")
                                        }

                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, reportText)
                                            setPackage("com.whatsapp")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallbackIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, reportText)
                                            }, "Kirim Laporan")
                                            context.startActivity(fallbackIntent)
                                        }
                                    }

                                    if (isSupabaseEnabled && selectedReportSource == "SUPABASE") {
                                        Toast.makeText(context, "Sinkronisasi Supabase sebelum membuat laporan...", Toast.LENGTH_SHORT).show()
                                        viewModel.syncAllData { success, msg ->
                                            if (!success) {
                                                Toast.makeText(context, "Gagal sinkronisasi, menggunakan data lokal: $msg", Toast.LENGTH_LONG).show()
                                                generateAndSendReport(attendees, todayLogsFull)
                                            } else {
                                                scope.launch {
                                                    kotlinx.coroutines.delay(500)
                                                    val latestAttendees = viewModel.attendees.value
                                                    val latestLogs = viewModel.logs.value
                                                    val latestTodayLogsFull = latestLogs.filter { log ->
                                                        todaySdf.format(Date(log.timestamp)) == todayStr
                                                    }
                                                    generateAndSendReport(latestAttendees, latestTodayLogsFull)
                                                }
                                            }
                                        }
                                    } else {
                                        generateAndSendReport(attendees, todayLogsFull)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDeviceBound) Color(0xFF25D366) else Color.LightGray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeviceBound) Icons.Default.Share else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDeviceBound) "Laporan WA Siswa" else "WA Siswa (Kunci)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }

                        // Button 2: Kirim Laporan Pulang Awal
                        Button(
                            onClick = {
                                if (!isDeviceBound) {
                                    Toast.makeText(context, "Perangkat belum aktif! Silakan masuk ke tab Pengaturan untuk aktivasi.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val generateAndSendPulangReport = { finalAttendees: List<Attendee>, finalTodayLogsFull: List<AttendanceLog> ->
                                        val teacherName = viewModel.getTeacherName()
                                        val selectedClass = viewModel.getSelectedClass()
                                        val todayFormatted = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())

                                        val reportText = buildString {
                                            append("*LAPORAN SISWA PULANG AWAL*\n")
                                            if (selectedClass.isNotBlank() && !selectedClass.equals("Semua", ignoreCase = true) && !selectedClass.equals("Semua Kelas", ignoreCase = true)) {
                                                append("Kelas: $selectedClass\n")
                                            }
                                            if (teacherName.isNotBlank()) {
                                                append("Guru Kelas: $teacherName\n")
                                            }
                                            append("Hari/Tanggal: $todayFormatted\n")
                                            if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                                append("Sumber Data: ${if (selectedReportSource == "SUPABASE") "Supabase (Cloud)" else "Database Lokal"}\n")
                                            }
                                            append("=========================\n\n")

                                            val allSiswa = finalAttendees.filter { 
                                                val r = it.role.trim().lowercase()
                                                r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
                                            }
                                            val registered = if (selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)) {
                                                allSiswa
                                            } else {
                                                allSiswa.filter { it.role.trim().equals(selectedClass, ignoreCase = true) }
                                            }

                                            val pulangAwalLogs = finalTodayLogsFull.filter { 
                                                it.type == "PULANG" && it.status.equals("Pulang Awal", ignoreCase = true) 
                                            }
                                            val studentLogsMap = pulangAwalLogs.associateBy { it.uid }
                                            val pulangAwalSiswa = registered.filter { it.uid in studentLogsMap.keys }

                                            append("Berikut adalah daftar siswa yang pulang awal hari ini:\n\n")
                                            if (pulangAwalSiswa.isEmpty()) {
                                                append("- Nihil (Tidak ada siswa pulang awal hari ini)\n")
                                            } else {
                                                pulangAwalSiswa.forEachIndexed { i, student ->
                                                    val log = studentLogsMap[student.uid]
                                                    val timeStr = if (log != null) {
                                                        SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(log.timestamp))
                                                    } else {
                                                        "-"
                                                    }
                                                    append("${i + 1}. *${student.name}* (Jam Pulang: $timeStr)\n")
                                                }
                                            }
                                            append("\nTotal Siswa Pulang Awal: ${pulangAwalSiswa.size} Siswa\n\n")
                                            append("Terima kasih atas perhatian Bapak/Ibu Wali Murid.\n")
                                        }

                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, reportText)
                                            setPackage("com.whatsapp")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallbackIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, reportText)
                                            }, "Kirim Laporan")
                                            context.startActivity(fallbackIntent)
                                        }
                                    }

                                    if (isSupabaseEnabled && selectedReportSource == "SUPABASE") {
                                        Toast.makeText(context, "Sinkronisasi Supabase sebelum membuat laporan...", Toast.LENGTH_SHORT).show()
                                        viewModel.syncAllData { success, msg ->
                                            if (!success) {
                                                Toast.makeText(context, "Gagal sinkronisasi, menggunakan data lokal: $msg", Toast.LENGTH_LONG).show()
                                                generateAndSendPulangReport(attendees, todayLogsFull)
                                            } else {
                                                scope.launch {
                                                    kotlinx.coroutines.delay(500)
                                                    val latestAttendees = viewModel.attendees.value
                                                    val latestLogs = viewModel.logs.value
                                                    val latestTodayLogsFull = latestLogs.filter { log ->
                                                        todaySdf.format(Date(log.timestamp)) == todayStr
                                                    }
                                                    generateAndSendPulangReport(latestAttendees, latestTodayLogsFull)
                                                }
                                            }
                                        }
                                    } else {
                                        generateAndSendPulangReport(attendees, todayLogsFull)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDeviceBound) Color(0xFFF59E0B) else Color.LightGray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeviceBound) Icons.Default.Share else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDeviceBound) "WA Pulang Awal" else "Pulang Awal (Kunci)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Horizontal Scrolling Sub-Menu/Tabs Section
        item {
            val visibleMenus = listOf(
                "Statistik" to Icons.Default.Assessment,
                "Export" to Icons.Default.Download,
                "Log" to Icons.Default.History,
                "Warning" to Icons.Default.Warning,
                "Pelanggan" to Icons.Default.Devices
            )
            val dropdownMenus = listOf(
                "Tepat Waktu" to Icons.Default.CheckCircle,
                "Terlambat" to Icons.Default.Schedule,
                "Pulang Awal" to Icons.Default.Logout,
                "Alpha" to Icons.Default.Cancel,
                "Peringkat" to Icons.Default.Star
            )

            var showDropdown by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1 (Statistik, Export, Log)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val row1Items = visibleMenus.take(3)
                    row1Items.forEach { (menuName, menuIcon) ->
                        val isSelected = selectedMenu == menuName
                        val containerColor = if (isSelected) Color(0xFF1E40AF) else Color.White
                        val contentColor = if (isSelected) Color.White else Color(0xFF475569)
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable { selectedMenu = menuName },
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = menuIcon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = menuName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Row 2 (Warning, Pelanggan, Menu Lainnya)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val row2Items = visibleMenus.drop(3).take(2)
                    row2Items.forEach { (menuName, menuIcon) ->
                        val isSelected = selectedMenu == menuName
                        val containerColor = if (isSelected) Color(0xFF1E40AF) else Color.White
                        val contentColor = if (isSelected) Color.White else Color(0xFF475569)
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable { selectedMenu = menuName },
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = menuIcon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = menuName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Menu Lainnya (Dropdown Trigger)
                    val isDropdownItemSelected = selectedMenu in dropdownMenus.map { it.first }
                    val containerColor = if (isDropdownItemSelected) Color(0xFF1E40AF) else Color.White
                    val contentColor = if (isDropdownItemSelected) Color.White else Color(0xFF475569)
                    val borderStroke = if (isDropdownItemSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Card(
                            modifier = Modifier
                                .height(42.dp)
                                .fillMaxWidth()
                                .clickable { showDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDropdownItemSelected) 3.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu Lainnya",
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Lainnya",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            dropdownMenus.forEach { (menuName, menuIcon) ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = menuIcon,
                                            contentDescription = null,
                                            tint = if (selectedMenu == menuName) Color(0xFF1E40AF) else Color(0xFF475569),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = menuName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedMenu == menuName) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedMenu == menuName) Color(0xFF1E40AF) else Color(0xFF475569)
                                        )
                                    },
                                    onClick = {
                                        selectedMenu = menuName
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // CONDITIONAL CONTENT BASED ON SELECTED MENU
        when (selectedMenu) {
            "Statistik" -> {
                // Today's Stats Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(3.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Statistik Kehadiran Hari Ini (${selectedRoleFilter})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE0F2FE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = "Supabase",
                                            tint = Color(0xFF0369A1),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Supabase",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }
                            }

                            // Detail Status Hari Ini (Tepat Waktu, Terlambat, Pulang Awal)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Tepat Waktu (Green Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(5.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFF10B981).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFF34D399), // Bright Emerald Green
                                                        Color(0xFF059669), // Rich Teal Green
                                                        Color(0xFF064E3B)  // Deep Forest Green
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "TEPAT WAKTU",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$tepatWaktuToday",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Terlambat (Amber Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(5.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFFF59E0B).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFFFBBF24), // Golden Amber
                                                        Color(0xFFD97706), // Sunburnt Orange
                                                        Color(0xFF78350F)  // Warm Mahogany
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "TERLAMBAT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$terlambatToday",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Pulang Awal (Pink Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(5.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFFC2185B).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFFF472B6), // Vibrant Pink
                                                        Color(0xFFEC4899), // Pink Rose
                                                        Color(0xFF9D174D)  // Deep Wine Red
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Logout,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "PULANG AWAL",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$pulangAwalToday",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Monthly Stats Card
                item {
                    var showMonthDropdown by remember { mutableStateOf(false) }
                    var showYearDropdown by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(3.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        text = "Statistik Bulanan: $selectedMonthName $selectedYear",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE0F2FE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = "Supabase",
                                            tint = Color(0xFF0369A1),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Supabase",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Pilih bulan dan tahun di bawah untuk menampilkan statistik kehadiran.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )

                            // Month & Year Selectors Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Month Dropdown
                                Box(modifier = Modifier.weight(1.2f)) {
                                    OutlinedButton(
                                        onClick = { showMonthDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = selectedMonthName, fontSize = 12.sp, maxLines = 1)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showMonthDropdown,
                                        onDismissRequest = { showMonthDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.5f)
                                    ) {
                                        monthNames.forEachIndexed { index, name ->
                                            DropdownMenuItem(
                                                text = { Text(name, fontSize = 12.sp) },
                                                onClick = {
                                                    selectedMonth = index
                                                    showMonthDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Year Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showYearDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = selectedYear.toString(), fontSize = 12.sp, maxLines = 1)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showYearDropdown,
                                        onDismissRequest = { showYearDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.4f)
                                    ) {
                                        yearList.forEach { yr ->
                                            DropdownMenuItem(
                                                text = { Text(yr.toString(), fontSize = 12.sp) },
                                                onClick = {
                                                    selectedYear = yr
                                                    showYearDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // HADIR Box (Green Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFF10B981).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFF34D399), // Bright Emerald Green
                                                        Color(0xFF059669), // Rich Teal Green
                                                        Color(0xFF064E3B)  // Deep Forest Green
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "HADIR",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = totalMonthlyHadir.toString(),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // SAKIT Box (Amber Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFFF59E0B).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFFFBBF24), // Golden Amber
                                                        Color(0xFFD97706), // Sunburnt Orange
                                                        Color(0xFF78350F)  // Warm Mahogany
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "SAKIT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = totalMonthlySakit.toString(),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // IZIN Box (Blue Card - High Gloss Saturated Premium Glass)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        width = 1.2.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.5f), Color(0xFF3B82F6).copy(alpha = 0.2f))
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFF60A5FA), // Brilliant Blue
                                                        Color(0xFF2563EB), // Royal Indigo
                                                        Color(0xFF1E3A8A)  // Deep Royal Blue
                                                    )
                                                )
                                            )
                                    ) {
                                        // Gloss Reflection Curved Highlight Layer
                                        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(size.width, size.height * 0.42f)
                                                quadraticTo(size.width * 0.5f, size.height * 0.52f, 0f, size.height * 0.42f)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.05f)
                                                    )
                                                )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mail,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "IZIN",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = totalMonthlyIzin.toString(),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Export" -> {
                // Search, Role Filters, and the interactive Attendance Table Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Kehadiran Bulan $selectedMonthName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE0F2FE))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = "Supabase",
                                        tint = Color(0xFF0369A1),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Supabase",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1)
                                    )
                                }
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari nama atau NISN/NIP...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        // Filter chips
                        if (rolesList.size > 1) {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(rolesList) { roleName ->
                                    val isSelected = selectedRoleFilter == roleName
                                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(containerColor)
                                            .clickable { selectedRoleFilter = roleName }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = roleName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(3.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header with option toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Tabel Status Kehadiran",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Segmented toggle controls
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // HARIAN Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (tableOption == "HARIAN") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { tableOption = "HARIAN" }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Harian",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tableOption == "HARIAN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    // BULANAN Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (tableOption == "BULANAN") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { tableOption = "BULANAN" }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Bulanan",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tableOption == "BULANAN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Export CSV Action Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tableOption == "HARIAN") "Laporan Kehadiran Hari Ini" else "Laporan Bulanan: $selectedMonthName $selectedYear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        exportToCsv(
                                            context = context,
                                            tableOption = tableOption,
                                            filteredAttendees = filteredAttendees,
                                            todayLogs = todayLogsFull,
                                            monthlyStatsMap = monthlyStatsMap,
                                            totalActiveDays = totalActiveDays,
                                            selectedMonthName = selectedMonthName,
                                            selectedYear = selectedYear
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("download_csv_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Unduh CSV",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Unduh CSV",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Horizontally scrollable table container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column(
                                    modifier = Modifier.width(620.dp) // Total table column width budget
                                ) {
                                    // Table Headers Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ID Siswa",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        Text(
                                            text = "Nama Siswa",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.width(180.dp)
                                        )
                                        Text(
                                            text = "Hadir",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(90.dp)
                                        )
                                        Text(
                                            text = "Ijin",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(90.dp)
                                        )
                                        Text(
                                            text = "Sakit",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(90.dp)
                                        )
                                        Text(
                                            text = "Absen",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.width(90.dp)
                                        )
                                    }

                                    Divider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        thickness = 1.dp
                                    )

                                    // Table Rows Content
                                    if (filteredAttendees.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Tidak ada data siswa untuk ditampilkan",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        filteredAttendees.forEachIndexed { index, attendee ->
                                            val rowBgColor = if (index % 2 == 1) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                            } else {
                                                Color.Transparent
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(rowBgColor)
                                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Student ID
                                                Text(
                                                    text = attendee.uid,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(80.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                // Student Name
                                                Text(
                                                    text = attendee.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.width(180.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (tableOption == "HARIAN") {
                                                    // HARIAN: Hanya Centang Saja
                                                    val todayLog = todayLogsFull.find { it.uid == attendee.uid }
                                                    val isHadir = todayLog != null && (todayLog.status.equals("Tepat Waktu", ignoreCase = true) || todayLog.status.equals("Terlambat", ignoreCase = true))
                                                    val isIjin = todayLog != null && (todayLog.status.equals("Ijin", ignoreCase = true) || todayLog.status.equals("Izin", ignoreCase = true))
                                                    val isSakit = todayLog != null && todayLog.status.equals("Sakit", ignoreCase = true)
                                                    val isAbsen = todayLog == null

                                                    // Hadir Column (Green tick icon)
                                                    Box(
                                                        modifier = Modifier.width(90.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isHadir) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Hadir",
                                                                tint = Color(0xFF10B981),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    // Ijin Column (Blue tick icon)
                                                    Box(
                                                        modifier = Modifier.width(90.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isIjin) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Ijin",
                                                                tint = Color(0xFF0284C7),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    // Sakit Column (Yellow/Orange tick icon)
                                                    Box(
                                                        modifier = Modifier.width(90.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSakit) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Sakit",
                                                                tint = Color(0xFFD97706),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    // Absen Column (Red tick icon)
                                                    Box(
                                                        modifier = Modifier.width(90.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isAbsen) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Absen",
                                                                tint = Color(0xFFDC2626),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    // BULANAN: Hadir=Jumlah Hadir, Ijin=Jumlah Ijin, Sakit=Jumlah Sakit, Absen=Jumlah Tidak Hadir
                                                    val stats = monthlyStatsMap[attendee.uid] ?: StudentAttendanceStats(0, 0, 0, 0, 0, 0)
                                                    val absentCount = if (totalActiveDays > 0) {
                                                        (totalActiveDays - (stats.hadir + stats.sakit + stats.izin)).coerceAtLeast(0)
                                                    } else {
                                                        0
                                                    }

                                                    // Hadir Count
                                                    Text(
                                                        text = stats.hadir.toString(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981),
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.width(90.dp)
                                                    )

                                                    // Ijin Count
                                                    Text(
                                                        text = stats.izin.toString(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0284C7),
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.width(90.dp)
                                                    )

                                                    // Sakit Count
                                                    Text(
                                                        text = stats.sakit.toString(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFD97706),
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.width(90.dp)
                                                    )

                                                    // Absen Count
                                                    Text(
                                                        text = absentCount.toString(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFDC2626),
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.width(90.dp)
                                                    )
                                                }
                                            }

                                            if (index < filteredAttendees.lastIndex) {
                                                Divider(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Peringkat" -> {
                // Class rankings card (Only shown if selectedClass is empty or "Semua" or "Semua Kelas")
                val isAllClassesSelected = selectedClass.isBlank() || selectedClass.equals("Semua", ignoreCase = true) || selectedClass.equals("Semua Kelas", ignoreCase = true)
                if (isAllClassesSelected) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .shadow(3.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF8B5CF6))
                                        Text(
                                            text = "Peringkat Kehadiran Kelas",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = "Supabase",
                                                tint = Color(0xFF0369A1),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Supabase",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Rata-rata persentase kehadiran seluruh siswa kelas pada bulan $selectedMonthName.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Divider(color = Color(0xFFF1F5F9))

                                // Calculate Class Attendance Ranking
                                val classRankings = remember(attendees, monthlyStatsMap, totalActiveDays) {
                                    // Group attendees (only students) by class (their role)
                                    val students = attendees.filter { 
                                        val r = it.role.trim().lowercase()
                                        r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
                                    }
                                    val classesGroup = students.groupBy { it.role.trim() }
                                    
                                    classesGroup.map { (className, classStudents) ->
                                        val studentCount = classStudents.size
                                        val totalPossibleDays = studentCount * totalActiveDays
                                        
                                        val totalHadir = classStudents.sumOf { student ->
                                            val stats = monthlyStatsMap[student.uid]
                                            stats?.hadir ?: 0
                                        }

                                        val percentage = if (totalPossibleDays > 0) {
                                            ((totalHadir.toFloat() / totalPossibleDays) * 100).coerceAtMost(100f)
                                        } else {
                                            0f
                                        }

                                        Triple(className, studentCount, percentage)
                                    }.sortedByDescending { it.third }
                                }

                                if (classRankings.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Belum ada data kelas", fontSize = 12.sp, color = Color.Gray)
                                    }
                                } else {
                                    classRankings.forEachIndexed { rank, (className, studentCount, percentage) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (rank == 0) Color(0xFFFAF5FF) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Rank badge
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when (rank) {
                                                            0 -> Color(0xFFD8B4FE)
                                                            1 -> Color(0xFFE2E8F0)
                                                            else -> Color(0xFFF1F5F9)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${rank + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (rank) {
                                                        0 -> Color(0xFF6B21A8)
                                                        else -> Color(0xFF475569)
                                                    }
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Kelas $className",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = "$studentCount Siswa",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", percentage)}%",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (percentage >= 85f) Color(0xFF059669) else Color(0xFFD97706)
                                                )
                                                // Minimal progress bar
                                                Box(
                                                    modifier = Modifier
                                                        .width(60.dp)
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(Color(0xFFE2E8F0))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(percentage / 100f)
                                                            .background(
                                                                if (percentage >= 85f) Color(0xFF10B981) else Color(0xFFF59E0B)
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Student rankings card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(3.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B))
                                    Text(
                                        text = "Peringkat Kehadiran Siswa (Bulan $selectedMonthName)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE0F2FE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = "Supabase",
                                            tint = Color(0xFF0369A1),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Supabase",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Daftar siswa dengan tingkat kehadiran tertinggi ke terendah.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Divider(color = Color(0xFFF1F5F9))

                            // Calculate and sort student rankings
                            val studentRankings = remember(classSiswa, monthlyStatsMap, totalActiveDays) {
                                classSiswa.map { student ->
                                    val stats = monthlyStatsMap[student.uid] ?: StudentAttendanceStats(0, 0, 0, 0, 0, 0)
                                    val pct = if (totalActiveDays > 0) {
                                        ((stats.hadir.toFloat() / totalActiveDays) * 100).coerceAtMost(100f)
                                    } else {
                                        0f
                                    }
                                    Triple(student, stats, pct)
                                }.sortedWith(
                                    compareByDescending<Triple<Attendee, StudentAttendanceStats, Float>> { it.third }
                                        .thenBy { it.first.name }
                                )
                            }

                            if (studentRankings.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Belum ada data kehadiran siswa", fontSize = 12.sp, color = Color.Gray)
                                }
                            } else {
                                studentRankings.forEachIndexed { rank, (student, stats, percentage) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Rank indicator
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (rank) {
                                                        0 -> Color(0xFFFEF3C7) // Gold
                                                        1 -> Color(0xFFF1F5F9) // Silver
                                                        2 -> Color(0xFFFFE4E6) // Bronze
                                                        else -> Color.Transparent
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${rank + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (rank) {
                                                    0 -> Color(0xFFD97706)
                                                    2 -> Color(0xFFBE123C)
                                                    else -> Color(0xFF475569)
                                                }
                                            )
                                        }

                                        // Student details
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = student.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            )
                                            Text(
                                                text = "NISN: ${student.uid} | Hadir: ${stats.hadir}, Izin: ${stats.izin}, Sakit: ${stats.sakit}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        // Percentage
                                        Text(
                                            text = "${percentage.toInt()}%",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (percentage >= 90f) Color(0xFF10B981) else if (percentage >= 75f) Color(0xFFF59E0B) else Color(0xFFEF4444)
                                        )
                                    }

                                    if (rank < studentRankings.lastIndex) {
                                        Divider(color = Color(0xFFF8FAFC), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Tepat Waktu" -> {
                item {
                    val list = remember(todayLogsFiltered, classSiswa) {
                        todayLogsFiltered.filter { log ->
                            log.type == "MASUK" && log.status.equals("Tepat Waktu", ignoreCase = true)
                        }.mapNotNull { log ->
                            val match = classSiswa.find { it.uid == log.uid } ?: return@mapNotNull null
                            Pair(match, log)
                        }
                    }

                    StudentStatusListCard(
                        title = "Siswa Tepat Waktu Hari Ini",
                        emptyMessage = "Belum ada siswa yang absen Tepat Waktu hari ini.",
                        icon = Icons.Default.CheckCircle,
                        badgeColor = Color(0xFFD1FAE5),
                        badgeTextColor = Color(0xFF065F46),
                        statusLabel = "Tepat Waktu",
                        students = list,
                        isSupabaseEnabled = isSupabaseEnabled
                    )
                }
            }
            "Terlambat" -> {
                item {
                    val list = remember(todayLogsFiltered, classSiswa) {
                        todayLogsFiltered.filter { log ->
                            log.type == "MASUK" && log.status.equals("Terlambat", ignoreCase = true)
                        }.mapNotNull { log ->
                            val match = classSiswa.find { it.uid == log.uid } ?: return@mapNotNull null
                            Pair(match, log)
                        }
                    }

                    StudentStatusListCard(
                        title = "Siswa Terlambat Hari Ini",
                        emptyMessage = "Hebat! Tidak ada siswa yang terlambat hari ini.",
                        icon = Icons.Default.Schedule,
                        badgeColor = Color(0xFFFEF3C7),
                        badgeTextColor = Color(0xFF92400E),
                        statusLabel = "Terlambat",
                        students = list,
                        isSupabaseEnabled = isSupabaseEnabled
                    )
                }
            }
            "Pulang Awal" -> {
                item {
                    val list = remember(todayLogsFiltered, classSiswa) {
                        todayLogsFiltered.filter { log ->
                            log.type == "PULANG" && log.status.equals("Pulang Awal", ignoreCase = true)
                        }.mapNotNull { log ->
                            val match = classSiswa.find { it.uid == log.uid } ?: return@mapNotNull null
                            Pair(match, log)
                        }
                    }

                    StudentStatusListCard(
                        title = "Siswa Pulang Awal Hari Ini",
                        emptyMessage = "Tidak ada siswa yang pulang awal hari ini.",
                        icon = Icons.Default.Logout,
                        badgeColor = Color(0xFFFCE4EC),
                        badgeTextColor = Color(0xFF880E4F),
                        statusLabel = "Pulang Awal",
                        students = list,
                        isSupabaseEnabled = isSupabaseEnabled
                    )
                }
            }
            "Alpha" -> {
                item {
                    val alphaStudents = remember(classSiswa, todayLogsFiltered) {
                        val enteredUids = todayLogsFiltered.filter { it.type == "MASUK" }.map { it.uid }.toSet()
                        classSiswa.filter { it.uid !in enteredUids }
                    }

                    val formattedList = remember(alphaStudents, todayLogsFiltered) {
                        alphaStudents.map { student ->
                            Pair(student, null as AttendanceLog?)
                        }
                    }

                    StudentStatusListCard(
                        title = "Siswa Belum Absen / Alpha Hari Ini",
                        emptyMessage = "Alhamdulillah, seluruh siswa sudah melakukan absensi hari ini!",
                        icon = Icons.Default.Cancel,
                        badgeColor = Color(0xFFFEE2E2),
                        badgeTextColor = Color(0xFF991B1B),
                        statusLabel = "Alpha",
                        students = formattedList,
                        isSupabaseEnabled = isSupabaseEnabled
                    )
                }
            }
            "Log" -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Aktivitas Presensi Terbaru",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = "Supabase",
                                                tint = Color(0xFF0369A1),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Supabase",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF))
                                    )
                                    Text(
                                        text = "HARI INI",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            if (todayLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Belum ada aktivitas presensi hari ini",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            } else {
                                val timeSdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                todayLogs.take(15).forEach { log ->
                                    val isMasuk = log.type == "MASUK"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isMasuk) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = log.name.firstOrNull()?.uppercase() ?: "?",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isMasuk) Color(0xFF065F46) else Color(0xFF991B1B)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = log.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = log.role,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(3.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFCBD5E1))
                                                    )
                                                    Text(
                                                        text = if (isMasuk) "Masuk" else "Pulang",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isMasuk) Color(0xFF059669) else Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = timeSdf.format(Date(log.timestamp)),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            if (log.status.isNotEmpty()) {
                                                Text(
                                                    text = log.status,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (log.status.equals("Tepat Waktu", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Warning" -> {
                item {
                    val rankClassOptions = remember(attendees) {
                        val studentClasses = attendees.map { it.role.trim() }.filter {
                            val r = it.lowercase()
                            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu" && r.isNotBlank()
                        }.distinct().sorted()
                        listOf("Semua Kelas") + studentClasses
                    }
                    var rankSelectedClass by remember { mutableStateOf("Semua Kelas") }
                    var showRankClassDropdown by remember { mutableStateOf(false) }
                    
                    val topAbsentStudents = remember(attendees, monthlyStatsMap, totalActiveDays, rankSelectedClass) {
                        val students = attendees.filter {
                            val r = it.role.trim().lowercase()
                            r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
                        }
                        val filteredForRank = if (rankSelectedClass == "Semua Kelas") {
                            students
                        } else {
                            students.filter { it.role.trim().equals(rankSelectedClass, ignoreCase = true) }
                        }
                        filteredForRank.map { student ->
                            val stats = monthlyStatsMap[student.uid] ?: StudentAttendanceStats(0, 0, 0, 0, 0)
                            val absentCount = if (totalActiveDays > 0) {
                                (totalActiveDays - (stats.hadir + stats.sakit + stats.izin)).coerceAtLeast(0)
                            } else {
                                0
                            }
                            student to absentCount
                        }
                        .sortedByDescending { it.second }
                        .take(10)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(3.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "10 Besar Siswa Paling Banyak Absen / Alpha",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSupabaseEnabled && viewModel.getSupabaseUrl().isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE0F2FE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = "Supabase",
                                            tint = Color(0xFF0369A1),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Supabase",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "Peringkat siswa dengan akumulasi tidak hadir (alpha) paling banyak di bulan $selectedMonthName $selectedYear.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filter Peringkat:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box {
                                    OutlinedButton(
                                        onClick = { showRankClassDropdown = true },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = rankSelectedClass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showRankClassDropdown,
                                        onDismissRequest = { showRankClassDropdown = false }
                                    ) {
                                        rankClassOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option, fontSize = 12.sp) },
                                                onClick = {
                                                    rankSelectedClass = option
                                                    showRankClassDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (topAbsentStudents.isEmpty() || topAbsentStudents.all { it.second == 0 }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFECFDF5))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF059669)
                                    )
                                    Text(
                                        text = "Luar Biasa! Semua siswa hadir penuh (0 absen) pada bulan $selectedMonthName.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF065F46),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "No", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                        Text(text = "Nama Siswa", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(text = "Total Absen", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                                    }
                                    
                                    topAbsentStudents.forEachIndexed { idx, pair ->
                                        val student = pair.first
                                        val absentCount = pair.second
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (idx) {
                                                    0 -> Color(0xFFDC2626)
                                                    1 -> Color(0xFFEA580C)
                                                    2 -> Color(0xFFD97706)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.width(28.dp)
                                            )
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = student.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Kelas: ${student.role}",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (absentCount >= 5) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$absentCount Hari",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (absentCount >= 5) Color(0xFFDC2626) else Color(0xFFD97706)
                                                )
                                            }
                                        }
                                        if (idx < topAbsentStudents.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                thickness = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Pelanggan" -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF1E40AF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Daftar Perangkat Pengguna",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            
                            Text(
                                text = "Daftar nama perangkat yang menggunakan sistem pemindaian presensi X-Degan QR. Tersedia scrolling jika terdaftar lebih dari 5 perangkat.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )

                            // Scrollable Box with fixed height of 220dp (suitable for 5 items)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                if (devicesList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Belum ada perangkat terdaftar", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(devicesList) { deviceName ->
                                            val isCurrentActive = isDeviceBound && deviceName == bindingDeviceName
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isCurrentActive) Color(0xFFEFF6FF) else Color.White)
                                                    .border(1.dp, if (isCurrentActive) Color(0xFFBFDBFE) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrentActive) Icons.Default.CheckCircle else Icons.Default.Smartphone,
                                                        contentDescription = null,
                                                        tint = if (isCurrentActive) Color(0xFF1E40AF) else Color(0xFF64748B),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = deviceName,
                                                            fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 12.sp,
                                                            color = if (isCurrentActive) Color(0xFF1E40AF) else Color(0xFF1E293B),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (isCurrentActive) {
                                                            Text(
                                                                text = "Perangkat Terikat (Aktif)",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF2563EB)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tentang Aplikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main slogan card/badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "X-Degan QR: Scan Cepat, Absen Tepat, Guru Gak Penat!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Author Info Text
                Text(
                    text = "Aplikasi ini dikembangkan oleh Ahmad Fawzan Rohman (Guru SDN Lombang Laok 1 Blega Bangkalan). Fiturnya belum aktif? Tenang, jangan panggil dukun, cukup hubungi 082301838321 biar diserpis sampai tuntas! Mau nyawer ? yuk scan kode QRIS dibawah",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Justify
                )

                // QRIS Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .shadow(2.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_qr_banner),
                        contentDescription = "QRIS Donasi Kopi",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun IncomingBroadcastHistoryDialog(
    historyList: List<com.example.ui.BroadcastMessage>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Riwayat Pengumuman",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Berikut adalah daftar pengumuman penting yang telah diterima oleh aplikasi ini.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada riwayat pengumuman.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyList.size) { index ->
                            val item = historyList[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(6.dp, RoundedCornerShape(14.dp), clip = false),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                border = BorderStroke(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White, Color(0xFFE2E8F0))
                                    )
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawRoundRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                                                ),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                            )
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width * 0.45f, 0f)
                                                lineTo(size.width * 0.15f, size.height)
                                                lineTo(0f, size.height)
                                                close()
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.7f),
                                                        Color.White.copy(alpha = 0.0f)
                                                    ),
                                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                    end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height)
                                                )
                                            )
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.type,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.type == "UPDATE") Color(0xFF2563EB) else Color(0xFFD97706),
                                                modifier = Modifier
                                                    .background(
                                                        color = (if (item.type == "UPDATE") Color(0xFFDBEAFE) else Color(0xFFFEF3C7)),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            
                                            val dateStr = try {
                                                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                                sdf.format(java.util.Date(item.updatedId))
                                            } catch (e: Exception) {
                                                "-"
                                            }
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }

                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )

                                        Text(
                                            text = item.message,
                                            fontSize = 11.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 15.sp
                                        )

                                        if (item.driveLink.isNotBlank()) {
                                            val context = LocalContext.current
                                            Row(
                                                modifier = Modifier.clickable {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.driveLink))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        // Ignore
                                                    }
                                                },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Buka Tautan Lampiran",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
fun BroadcastBanner(
    broadcast: com.example.ui.BroadcastMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUpdate = broadcast.type == "UPDATE"
    
    // Choose glossy colors based on type
    val iconColor = if (isUpdate) Color(0xFF2563EB) else Color(0xFFEA580C)
    val badgeBg = if (isUpdate) Color(0xFFEFF6FF) else Color(0xFFFFF7ED)
    val badgeBorder = if (isUpdate) Color(0xFFBFDBFE) else Color(0xFFFED7AA)
    val badgeText = if (isUpdate) "PEMBARUAN" else "INSTRUKSI"
    val icon = if (isUpdate) Icons.Default.CloudDownload else Icons.Default.Campaign

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), clip = false),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFFE2E8F0)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Draw clean "putih bersih" background gradient
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )
                    
                    // Draw realistic glossy glass reflection sheen
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width * 0.45f, 0f)
                        lineTo(size.width * 0.15f, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.0f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height)
                        )
                    )
                }
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glassy capsule badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = iconColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Glass circular Dismiss Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(0xFFF1F5F9).copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sembunyikan",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = broadcast.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = broadcast.message,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 19.sp
                    )
                }
                
                if (broadcast.driveLink.isNotBlank()) {
                    val buttonGradient = if (isUpdate) {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFF97316), Color(0xFFD97706))
                        )
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(broadcast.driveLink))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal membuka link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(buttonGradient, RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isUpdate) "Unduh Pembaruan (Google Drive)" else "Buka Tautan Informasi",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportToCsv(
    context: android.content.Context,
    tableOption: String,
    filteredAttendees: List<com.example.data.Attendee>,
    todayLogs: List<com.example.data.AttendanceLog>,
    monthlyStatsMap: Map<String, StudentAttendanceStats>,
    totalActiveDays: Int,
    selectedMonthName: String,
    selectedYear: Int
) {
    val csvContent = java.lang.StringBuilder()
    
    if (tableOption == "HARIAN") {
        csvContent.append("ID Siswa,Nama Siswa,Peran,Status Kehadiran\n")
        filteredAttendees.forEach { attendee ->
            val todayLog = todayLogs.find { it.uid == attendee.uid }
            val status = when {
                todayLog == null -> "Absen"
                todayLog.status.equals("Tepat Waktu", ignoreCase = true) || todayLog.status.equals("Terlambat", ignoreCase = true) -> "Hadir (${todayLog.status})"
                todayLog.status.equals("Ijin", ignoreCase = true) || todayLog.status.equals("Izin", ignoreCase = true) -> "Ijin"
                todayLog.status.equals("Sakit", ignoreCase = true) -> "Sakit"
                else -> todayLog.status
            }
            val escapedName = attendee.name.replace("\"", "\"\"")
            csvContent.append("\"${attendee.uid}\",\"$escapedName\",\"${attendee.role}\",\"$status\"\n")
        }
    } else {
        csvContent.append("Laporan Absensi Bulanan - $selectedMonthName $selectedYear\n")
        csvContent.append("Total Hari Aktif: $totalActiveDays\n\n")
        csvContent.append("ID Siswa,Nama Siswa,Peran,Hadir,Ijin,Sakit,Absen (Tidak Hadir),Tepat Waktu,Terlambat,Pulang Awal\n")
        filteredAttendees.forEach { attendee ->
            val stats = monthlyStatsMap[attendee.uid] ?: StudentAttendanceStats(0, 0, 0, 0, 0, 0)
            val absentCount = if (totalActiveDays > 0) {
                (totalActiveDays - (stats.hadir + stats.sakit + stats.izin)).coerceAtLeast(0)
            } else {
                0
            }
            val escapedName = attendee.name.replace("\"", "\"\"")
            csvContent.append("\"${attendee.uid}\",\"$escapedName\",\"${attendee.role}\",${stats.hadir},${stats.izin},${stats.sakit},$absentCount,${stats.tepatWaktu},${stats.terlambat},${stats.pulangAwal}\n")
        }
    }

    try {
        val fileName = if (tableOption == "HARIAN") "Laporan_Harian_${System.currentTimeMillis()}.csv" else "Laporan_Bulanan_${selectedMonthName.replace(" ", "_")}_${selectedYear}.csv"
        
        val cacheFile = java.io.File(context.cacheDir, fileName)
        cacheFile.writeText(csvContent.toString())
        
        var savedToDownloads = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toString().toByteArray())
                    savedToDownloads = true
                }
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val destFile = java.io.File(downloadsDir, fileName)
                destFile.writeText(csvContent.toString())
                savedToDownloads = true
            }
        }
        
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)
        
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, if (tableOption == "HARIAN") "Laporan Absensi Harian" else "Laporan Absensi Bulanan - $selectedMonthName $selectedYear")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = android.content.Intent.createChooser(shareIntent, "Simpan / Bagikan Laporan (CSV)")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        
        if (savedToDownloads) {
            android.widget.Toast.makeText(context, "Laporan berhasil disimpan ke folder Download dan siap dibagikan!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "Laporan berhasil diekspor!", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("CSVExport", "Error exporting CSV", e)
        android.widget.Toast.makeText(context, "Gagal mengunduh CSV: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// =====================================
// TAB: RIWAYAT & PERANGKAT TAB
// =====================================
@Composable
fun RiwayatTab(
    attendees: List<Attendee>,
    logs: List<AttendanceLog>,
    viewModel: AttendanceViewModel
) {
    return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Backup JSON file launcher
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = viewModel.backupDataToJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Data berhasil di-backup ke penyimpanan lokal!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuat backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Restore JSON file launcher
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    coroutineScope.launch {
                        val success = viewModel.restoreDataFromJson(jsonString)
                        if (success) {
                            Toast.makeText(context, "Data berhasil di-restore dari file lokal!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Format file backup tidak valid atau gagal melakukan restore.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memproses file backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var selectedSubMenu by remember { mutableStateOf("Log") }
    
    // Devices list from SharedPreferences
    val sharedPrefs = remember { context.getSharedPreferences("app_devices_prefs", Context.MODE_PRIVATE) }
    var devicesList by remember {
        mutableStateOf(
            run {
                val savedString = sharedPrefs.getString("devices_list", "") ?: ""
                if (savedString.isBlank()) {
                    listOf(
                        "Tablet Absensi Lobby Utama",
                        "Terminal Gerbang Depan",
                        "Smartphone Piket Guru",
                        "Tablet Ruang Staf & Guru",
                        "Terminal Lab Komputer",
                        "Smartphone Admin Absensi"
                    )
                } else {
                    savedString.split("##").filter { it.isNotBlank() }
                }
            }
        )
    }

    fun saveDevices(list: List<String>) {
        devicesList = list
        sharedPrefs.edit().putString("devices_list", list.joinToString("##")).apply()
    }

    val bindingDeviceName by viewModel.bindingDeviceName.collectAsStateWithLifecycle()
    val isDeviceBound by viewModel.isDeviceBound.collectAsStateWithLifecycle()
    var newDeviceNameInput by remember { mutableStateOf("") }

    LaunchedEffect(bindingDeviceName, isDeviceBound) {
        if (isDeviceBound && bindingDeviceName.isNotBlank() && !devicesList.contains(bindingDeviceName)) {
            saveDevices(listOf(bindingDeviceName) + devicesList)
        }
    }

    // Today Logs Calculation
    val todaySdf = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val todayStr = remember { todaySdf.format(Date()) }
    val todayLogs = remember(logs) {
        logs.filter { log ->
            todaySdf.format(Date(log.timestamp)) == todayStr
        }.sortedByDescending { it.timestamp }
    }

    // Monthly stats calculations for Top 10 Card
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    val monthNames = remember { listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember") }
    val selectedMonthName = monthNames.getOrNull(selectedMonth) ?: "Bulan Ini"

    val monthlyLogs = remember(logs, selectedMonth, selectedYear) {
        logs.filter { log ->
            val cal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
            cal.get(Calendar.MONTH) == selectedMonth && cal.get(Calendar.YEAR) == selectedYear
        }
    }

    val monthlyStatsMap = remember(monthlyLogs, attendees) {
        val logGroup = monthlyLogs.groupBy { it.uid }
        attendees.associate { attendee ->
            val userLogs = logGroup[attendee.uid] ?: emptyList()
            val masukLogs = userLogs.filter { it.type == "MASUK" }
            
            val tepatWaktu = masukLogs.count { it.status.equals("Tepat Waktu", ignoreCase = true) }
            val terlambat = masukLogs.count { it.status.equals("Terlambat", ignoreCase = true) }
            val sakit = masukLogs.count { it.status.equals("Sakit", ignoreCase = true) }
            val izin = masukLogs.count { it.status.equals("Ijin", ignoreCase = true) || it.status.equals("Izin", ignoreCase = true) }
            val totalHadir = tepatWaktu + terlambat
            
            val pulangLogs = userLogs.filter { it.type == "PULANG" }
            val pulangAwal = pulangLogs.count { it.status.equals("Pulang Awal", ignoreCase = true) }
            
            attendee.uid to StudentAttendanceStats(
                tepatWaktu = tepatWaktu,
                terlambat = terlambat,
                sakit = sakit,
                izin = izin,
                hadir = totalHadir,
                pulangAwal = pulangAwal
            )
        }
    }

    val totalActiveDays = remember(monthlyLogs) {
        monthlyLogs.map { log ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(log.timestamp))
        }.distinct().size
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Header Gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E40AF), Color(0xFF581C87))
                        ),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "X-Degan QR",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Sistem Absensi Pemindaian Real-time",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE)
                                )
                            }
                        }
                        // Notification icon
                        Box(
                            modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Riwayat & Perangkat",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Pantau aktivitas kehadiran terbaru serta atur daftar perangkat pengguna",
                        fontSize = 13.sp,
                        color = Color(0xFFDBEAFE),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Horizontal Scrolling Sub-Menu/Tabs Section
        item {
            val visibleSubMenus = listOf(
                "Log" to Icons.Default.History,
                "Warning" to Icons.Default.Warning,
                "Pelanggan" to Icons.Default.Devices
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-5).dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleSubMenus) { (menuName, menuIcon) ->
                    val isSelected = selectedSubMenu == menuName
                    val containerColor = if (isSelected) Color(0xFF1E40AF) else Color.White
                    val contentColor = if (isSelected) Color.White else Color(0xFF475569)
                    val borderStroke = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))

                    Card(
                        modifier = Modifier
                            .height(42.dp)
                            .clickable { selectedSubMenu = menuName },
                        shape = RoundedCornerShape(20.dp),
                        border = borderStroke,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = menuIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = menuName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }

        if (selectedSubMenu == "Log") {
            // 2. Aktivitas Presensi Terbaru Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-10).dp)
                        .padding(horizontal = 16.dp)
                        .shadow(1.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Aktivitas Presensi Terbaru",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                            )
                            Text(
                                text = "HARI INI",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    if (todayLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada aktivitas presensi hari ini",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    } else {
                        val timeSdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        todayLogs.take(5).forEach { log ->
                            val isMasuk = log.type == "MASUK"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isMasuk) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = log.name.firstOrNull()?.uppercase() ?: "?",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isMasuk) Color(0xFF065F46) else Color(0xFF991B1B)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = log.role,
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFCBD5E1))
                                            )
                                            Text(
                                                text = if (isMasuk) "Masuk" else "Pulang",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isMasuk) Color(0xFF059669) else Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = timeSdf.format(Date(log.timestamp)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    if (log.status.isNotEmpty()) {
                                        Text(
                                            text = log.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (log.status.equals("Tepat Waktu", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (selectedSubMenu == "Warning") {
            // 3. 10 Besar Siswa Paling Banyak Absen / Alpha Card
            item {
            val rankClassOptions = remember(attendees) {
                val studentClasses = attendees.map { it.role.trim() }.filter {
                    val r = it.lowercase()
                    r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu" && r.isNotBlank()
                }.distinct().sorted()
                listOf("Semua Kelas") + studentClasses
            }
            var rankSelectedClass by remember { mutableStateOf("Semua Kelas") }
            var showRankClassDropdown by remember { mutableStateOf(false) }
            
            val topAbsentStudents = remember(attendees, monthlyStatsMap, totalActiveDays, rankSelectedClass) {
                val students = attendees.filter {
                    val r = it.role.trim().lowercase()
                    r != "guru" && r != "staf" && r != "karyawan" && r != "admin" && r != "tamu"
                }
                val filteredForRank = if (rankSelectedClass == "Semua Kelas") {
                    students
                } else {
                    students.filter { it.role.trim().equals(rankSelectedClass, ignoreCase = true) }
                }
                filteredForRank.map { student ->
                    val stats = monthlyStatsMap[student.uid] ?: StudentAttendanceStats(0, 0, 0, 0, 0)
                    val absentCount = if (totalActiveDays > 0) {
                        (totalActiveDays - (stats.hadir + stats.sakit + stats.izin)).coerceAtLeast(0)
                    } else {
                        0
                    }
                    student to absentCount
                }
                .sortedByDescending { it.second }
                .take(10)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-10).dp)
                    .padding(horizontal = 16.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "10 Besar Siswa Paling Banyak Absen / Alpha",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = "Peringkat siswa dengan akumulasi tidak hadir (alpha) paling banyak di bulan $selectedMonthName $selectedYear.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter Peringkat:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            OutlinedButton(
                                onClick = { showRankClassDropdown = true },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = rankSelectedClass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = showRankClassDropdown,
                                onDismissRequest = { showRankClassDropdown = false }
                            ) {
                                rankClassOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, fontSize = 12.sp) },
                                        onClick = {
                                            rankSelectedClass = option
                                            showRankClassDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    if (topAbsentStudents.isEmpty() || topAbsentStudents.all { it.second == 0 }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFECFDF5))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669)
                            )
                            Text(
                                text = "Luar Biasa! Semua siswa hadir penuh (0 absen) pada bulan $selectedMonthName.",
                                fontSize = 11.sp,
                                color = Color(0xFF065F46),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "No", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Text(text = "Nama Siswa", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(text = "Total Absen", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            }
                            
                            topAbsentStudents.forEachIndexed { idx, pair ->
                                val student = pair.first
                                val absentCount = pair.second
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (idx) {
                                            0 -> Color(0xFFDC2626)
                                            1 -> Color(0xFFEA580C)
                                            2 -> Color(0xFFD97706)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.width(28.dp)
                                    )
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Kelas: ${student.role}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (absentCount >= 5) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "$absentCount Hari",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (absentCount >= 5) Color(0xFFDC2626) else Color(0xFFD97706)
                                        )
                                    }
                                }
                                if (idx < topAbsentStudents.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (selectedSubMenu == "Pelanggan") {
            // 4. Daftar Perangkat Card (Scrollable Box inside with space for 5 items)
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-10).dp)
                    .padding(horizontal = 16.dp)
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = Color(0xFF1E40AF),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Daftar Perangkat Pengguna",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    
                    Text(
                        text = "Daftar nama perangkat yang menggunakan sistem pemindaian presensi X-Degan QR. Tersedia scrolling jika terdaftar lebih dari 5 perangkat.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )

                    // Scrollable Box with fixed height of 220dp (suitable for 5 items)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        if (devicesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Belum ada perangkat terdaftar", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(devicesList) { deviceName ->
                                    val isCurrentActive = isDeviceBound && deviceName == bindingDeviceName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrentActive) Color(0xFFEFF6FF) else Color.White)
                                            .border(1.dp, if (isCurrentActive) Color(0xFFBFDBFE) else Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentActive) Icons.Default.CheckCircle else Icons.Default.Smartphone,
                                                contentDescription = null,
                                                tint = if (isCurrentActive) Color(0xFF1E40AF) else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = deviceName,
                                                    fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.sp,
                                                    color = if (isCurrentActive) Color(0xFF1E40AF) else Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isCurrentActive) {
                                                    Text(
                                                        text = "Perangkat Terikat (Aktif)",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2563EB)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}


