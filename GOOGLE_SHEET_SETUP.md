# Panduan Integrasi Google Sheets & Google Apps Script
### Aplikasi Absensi Mandiri "X-Degan QR"

Aplikasi **X-Degan QR** mendukung sinkronisasi dua arah secara real-time dengan Google Sheets melalui Google Apps Script Web App:
1. **Import Anggota (Siswa/Guru)**: Mengunduh daftar nama & UID langsung dari Google Sheets ke dalam aplikasi.
2. **Export Log Presensi**: Mengirimkan data absensi yang tersimpan di dalam aplikasi (termasuk absensi offline) ke Google Sheets secara otomatis.

Berikut adalah petunjuk lengkap untuk membuat Spreadsheet dan memasang Google Apps Script-nya.

---

## Bagian 1: Menyiapkan Google Spreadsheet

1. Buka [Google Sheets](https://sheets.google.com/) dan buat sebuah **Spreadsheet Baru**.
2. Beri nama Spreadsheet Anda (contoh: `Database Absensi X-Degan`).
3. Di dalam Spreadsheet tersebut, buat **2 buah sheet (tab)** di bagian bawah:
   
   ### Tab 1: Beri nama `Siswa`
   Tab ini digunakan untuk menyimpan daftar anggota/siswa yang akan di-import ke aplikasi. Buat kolom di baris pertama (header) sebagai berikut:
   * **Kolom A**: `uid` (Nomor Identitas / NISN / NIP)
   * **Kolom B**: `name` (Nama Lengkap)
   * **Kolom C**: `role` (Kategori: `Siswa` atau `Guru`)

   *Contoh Pengisian Data:*
   | uid | name | role |
   | :--- | :--- | :--- |
   | 20260101 | Budi Santoso | Siswa |
   | 20260102 | Siti Aminah | Siswa |
   | 19881010 | H. Degan, S.Pd. | Guru |

   ### Tab 2: Beri nama `LogAbsensi`
   Tab ini digunakan sebagai tempat penyimpanan log absensi yang dikirimkan dari aplikasi. Buat kolom di baris pertama (header) sebagai berikut:
   * **Kolom A**: `ID Log`
   * **Kolom B**: `UID`
   * **Kolom C**: `Nama`
   * **Kolom D**: `Role / Kategori`
   * **Kolom E**: `Waktu Presensi`
   * **Kolom F**: `Tipe` (MASUK / KELUAR)
   * **Kolom G**: `Status` (HADIR / TERLAMBAT / SAKIT / IZIN)
   * **Kolom H**: `Sesi / Lokasi`

---

## Bagian 2: Menulis Google Apps Script

1. Di menu atas Google Sheets Anda, klik **Ekstensi (Extensions)** -> **Apps Script**.
2. Hapus semua kode default yang ada di editor `Kode.gs`.
3. Salin dan tempelkan (copy-paste) seluruh kode di bawah ini ke dalam editor tersebut:

```javascript
/**
 * Google Apps Script Web App untuk Integrasi Absensi X-Degan QR
 * 
 * Fitur:
 * 1. GET (doGet) - Menangani pendaftaran Device ID, verifikasi token, dan import daftar Siswa.
 * 2. POST (doPost) - Menerima log absensi dari aplikasi dalam format JSON dan menyimpannya.
 */

// ================= CONFIGURATION =================
// 1. Spreadsheet ID khusus untuk Fitur Device Binding (Pendaftaran & Verifikasi Perangkat)
// Silakan isi dengan ID Spreadsheet Device Binding Anda (Contoh: "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0")
var BINDING_SPREADSHEET_ID = "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0";

// 2. Spreadsheet ID untuk Database Absensi (Daftar Siswa & Log Absensi)
// Jika Anda mengosongkan/menggunakan "", maka script akan secara otomatis menggunakan Spreadsheet tempat Script ini dipasang (Active Spreadsheet) sebagai database absensi.
// Jika ingin menggunakan Spreadsheet eksternal yang berbeda untuk absensi, isi dengan ID Spreadsheet absensi Anda di bawah ini.
var ATTENDANCE_SPREADSHEET_ID = "1wVGEU_k7kyz-5gxKAkSb4gwNgaQxtBRoQtEbaLOYzxQ"; 
// =================================================

function getBindingSpreadsheet() {
  try {
    return SpreadsheetApp.openById(BINDING_SPREADSHEET_ID);
  } catch (e) {
    throw new Error("Gagal membuka Spreadsheet Device Binding. Pastikan ID benar dan akun Apps Script memiliki akses: " + e.toString());
  }
}

function getAttendanceSpreadsheet() {
  if (ATTENDANCE_SPREADSHEET_ID && ATTENDANCE_SPREADSHEET_ID.trim() !== "") {
    try {
      return SpreadsheetApp.openById(ATTENDANCE_SPREADSHEET_ID.trim());
    } catch (e) {
      // Fallback ke active spreadsheet jika eksternal gagal dibuka
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
    
    // Cek apakah Device ID sudah terdaftar
    for (var i = 1; i < data.length; i++) {
      var rowDeviceId = String(data[i][1] || "").trim();
      if (rowDeviceId === deviceId) {
        foundIndex = i;
        break;
      }
    }
    
    if (foundIndex !== -1) {
      // Update nama perangkat jika dikirim ulang
      tokenSheet.getRange(foundIndex + 1, 3).setValue(deviceName);
      var currentToken = String(data[foundIndex][0] || "").trim();
      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        message: "Perangkat sudah terdaftar sebelumnya! " + (currentToken ? "Gunakan token: " + currentToken : "Silakan hubungi Admin untuk menginput token di Spreadsheet Device Binding.")
      })).setMimeType(ContentService.MimeType.JSON);
    } else {
      // Tambahkan baris baru dengan token kosong
      tokenSheet.appendRow(["", deviceId, deviceName]);
      return ContentService.createTextOutput(JSON.stringify({
        success: true,
        message: "Perangkat berhasil terdaftar di Spreadsheet Device Binding! Silakan hubungi Admin untuk memberikan Token Aktivasi di kolom A."
      })).setMimeType(ContentService.MimeType.JSON);
    }
  }

  // 2. VERIFIKASI DEVICE BINDING TOKEN (action=verifyToken)
  if (e && e.parameter && e.parameter.action === 'verifyToken') {
    var token = String(e.parameter.token || "").trim();
    var deviceId = String(e.parameter.deviceId || "").trim();
    
    if (token === "") {
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        message: "Token tidak boleh kosong!"
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    var spreadsheet = getBindingSpreadsheet();
    var tokenSheet = spreadsheet.getSheetByName("Tokens");
    if (!tokenSheet) {
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        message: "Sheet 'Tokens' belum dibuat. Silakan daftarkan perangkat terlebih dahulu."
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
      var sheet = spreadsheet.getSheetByName("LogAbsensi");
      if (!sheet) {
        return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
      }
      var data = sheet.getDataRange().getValues();
      var headers = data[0];
      
      var idIndex = headers.indexOf("ID Log");
      var uidIndex = headers.indexOf("UID");
      var nameIndex = headers.indexOf("Nama");
      var roleIndex = headers.indexOf("Role / Kategori");
      var timestampIndex = headers.indexOf("Waktu Presensi");
      var typeIndex = headers.indexOf("Tipe");
      var statusIndex = headers.indexOf("Status");
      var sessionIndex = headers.indexOf("Sesi / Lokasi");
      
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
        var type = row[typeIndex].toString().trim() || "MASUK";
        var status = row[statusIndex].toString().trim() || "HADIR";
        var sessionName = row[sessionIndex] ? row[sessionIndex].toString().trim() : "Umum";
        
        if (uid !== "") {
          result.push({
            id: logId,
            uid: uid,
            name: name,
            role: role,
            timestamp: rawTimestamp,
            type: type,
            status: status,
            sessionName: sessionName
          });
        }
      }
      return ContentService.createTextOutput(JSON.stringify(result))
                           .setMimeType(ContentService.MimeType.JSON);
    } catch (err) {
      return ContentService.createTextOutput(JSON.stringify({ error: err.toString() }))
                           .setMimeType(ContentService.MimeType.JSON);
    }
  }

  // 4. DEFAULT: IMPORT DATA SISWA (Jika dipanggil biasa)
  try {
    var spreadsheet = getAttendanceSpreadsheet();
    var sheet = spreadsheet.getSheetByName("Siswa") || spreadsheet.getSheets()[0];
    var data = sheet.getDataRange().getValues();
    var headers = data[0];
    
    var uidIndex = headers.indexOf("uid");
    var nameIndex = headers.indexOf("name");
    var roleIndex = headers.indexOf("role");
    
    if (uidIndex === -1) uidIndex = 0;
    if (nameIndex === -1) nameIndex = 1;
    if (roleIndex === -1) roleIndex = 2;
    
    var result = [];
    for (var i = 1; i < data.length; i++) {
      var row = data[i];
      var uid = row[uidIndex].toString().trim();
      var name = row[nameIndex].toString().trim();
      var role = row[roleIndex].toString().trim() || "Siswa";
      
      if (name !== "") {
        result.push({
          uid: uid,
          name: name,
          role: role
        });
      }
    }
    
    return ContentService.createTextOutput(JSON.stringify(result))
                         .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ error: err.toString() }))
                         .setMimeType(ContentService.MimeType.JSON);
  }
}

// 4. POST (EXPORT/SYNCHRONIZE LOG ABSENSI ATAU DAFTAR SISWA)
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

    var sheet = ss.getSheetByName("LogAbsensi");
    
    if (!sheet) {
      sheet = ss.insertSheet("LogAbsensi");
      sheet.appendRow(["ID Log", "UID", "Nama", "Role / Kategori", "Waktu Presensi", "Tipe", "Status", "Sesi / Lokasi"]);
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
    
    var addedCount = 0;
    for (var i = 0; i < logs.length; i++) {
      var log = logs[i];
      var logId = log.id ? log.id.toString() : "";
      
      if (logId !== "" && existingIds[logId]) {
        continue;
      }
      
      var uid = log.uid || "";
      var name = log.name || "";
      var role = log.role || "";
      var timestamp = log.timestamp || "";
      var type = log.type || "MASUK";
      var status = log.status || "HADIR";
      var sessionName = log.sessionName || "Umum";
      
      sheet.appendRow([logId, uid, name, role, timestamp, type, status, sessionName]);
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
```

4. Klik tombol **Simpan** (ikon disket) di bagian atas editor Apps Script.

---

## Bagian 3: Mendeploy sebagai Web App (SANGAT PENTING!)

Agar aplikasi Android dapat mengakses skrip ini, Anda harus mendeploy-nya dengan benar sebagai Web App publik:

1. Di kanan atas halaman Apps Script, klik tombol **Terapkan (Deploy)** -> **Penerapan Baru (New deployment)**.
2. Pada jenis penerapan, klik ikon roda gigi ⚙️ di sebelah kiri "Pilih Jenis" dan pilih **Aplikasi Web (Web App)**.
3. Konfigurasikan pengaturan berikut:
   * **Deskripsi (Description)**: `Konektor Aplikasi Absensi X-Degan QR`
   * **Jalankan sebagai (Execute as)**: Pilih **Saya (Email Anda)**.
   * **Siapa yang memiliki akses (Who has access)**: Pilih **Siapa saja (Anyone)**. *(Catatan: Ini aman karena aplikasi menggunakan ini secara otomatis di latar belakang untuk mengirim log).*
4. Klik tombol **Terapkan (Deploy)**.
5. Jika ini penerapan pertama Anda, Google akan meminta **Otorisasi Akses (Authorize Access)**:
   * Klik **Otorisasi Akses (Authorize Access)**.
   * Pilih akun Google Anda.
   * Anda mungkin akan melihat peringatan *"Google has not verified this app"*. Jangan khawatir, klik **Advanced (Lanjutan)** di bagian bawah kiri, kemudian klik **Go to Database Absensi X-Degan (unsafe) / Buka... (tidak aman)**.
   * Klik **Izinkan (Allow)**.
6. Setelah penerapan berhasil, Anda akan diberikan **URL Aplikasi Web (Web App URL)**.
   * URL ini akan berakhiran dengan `/exec` (Contoh: `https://script.google.com/macros/s/AKfycb..._abc/exec`).
   * **Salin URL Aplikasi Web ini!** Anda akan membutuhkannya untuk dimasukkan ke dalam pengaturan Aplikasi Android.

---

## Bagian 4: Menghubungkan ke Aplikasi Android X-Degan QR

1. Buka aplikasi **X-Degan QR** pada perangkat Android Anda.
2. Klik ikon **Pengaturan (Settings)** di pojok kanan atas aplikasi.
3. Pada kolom **"Google Apps Script Web App URL"**, tempelkan (paste) URL Web App yang telah Anda salin sebelumnya.
4. Klik **Simpan Pengaturan**.

### Cara Melakukan Sinkronisasi Data:
* **Import Anggota**: Di Tab **Anggota**, klik tombol **"Sinkronisasi Sheets"** atau klik **Import Google Sheets** di dalam Pengaturan. Semua siswa dari sheet `Siswa` akan di-import ke database lokal aplikasi secara instan.
* **Export Log Absensi**: Di Tab **Histori**, klik tombol **"Sinkronisasi Ke Google Sheets"** (ikon Cloud Upload). Aplikasi akan mengirimkan seluruh log presensi baru yang belum di-upload ke Spreadsheet secara otomatis.

---

## Bagaimana Generator Token QR & Absensi Bekerja?
Aplikasi ini memanfaatkan nomor identitas unik (**UID / NISN / NIP**) setiap anggota untuk menghasilkan token QR Code:
1. **Pembuatan QR Code Mandiri**: Di tab **Anggota**, setiap profil siswa memiliki kartu absensi digital yang menampilkan detail profil beserta **QR Code** yang digenerate secara real-time dari kolom `uid` siswa tersebut.
2. **Metode Scan QR (Terminal)**: Guru/Operator mengaktifkan kamera scanner di pintu masuk dalam **Mode Terminal**. Siswa hanya perlu menunjukkan QR Code mereka (di HP atau kartu yang dicetak), scanner akan langsung mengenali UID, memverifikasi dengan database lokal, dan mencatat absensi.
3. **Cetak Kartu Absensi Fisik**: Tersedia fitur cetak kartu siswa ber-QR Code langsung dari aplikasi ke printer atau disimpan sebagai format PDF yang rapi!
