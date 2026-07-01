import React, { useState, useEffect } from 'react';
import { 
  Smartphone, 
  Key, 
  Cpu, 
  Link2, 
  ExternalLink, 
  Code, 
  Copy, 
  Check, 
  Plus, 
  Edit2, 
  Trash2, 
  Search, 
  Settings, 
  AlertCircle, 
  HelpCircle, 
  RefreshCw, 
  CheckCircle,
  Database,
  X,
  Sparkles,
  Info
} from 'lucide-react';

interface DeviceToken {
  token: string;
  deviceId: string;
  namaPerangkat: string;
  rowNum?: number;
}

export const BindingDeviceView: React.FC = () => {
  const [scriptUrl, setScriptUrl] = useState<string>(() => {
    return localStorage.getItem('X_DEGAN_APPS_SCRIPT_URL') || 'https://script.google.com/macros/s/AKfycby1wCQy4WgwWfrcnDLWhovhcLPARtiGiRJgllu5cEtjRBmufIgP_w2OiCt-nrn1kvJS/exec';
  });
  
  const [devices, setDevices] = useState<DeviceToken[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [autoSyncEnabled, setAutoSyncEnabled] = useState<boolean>(true);
  const [autoSyncing, setAutoSyncing] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [successMsg, setSuccessMsg] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  
  // Connection Test State
  const [testingConnection, setTestingConnection] = useState<boolean>(false);
  const [connectionStatus, setConnectionStatus] = useState<'unconfigured' | 'success' | 'failed'>(
    scriptUrl ? 'success' : 'unconfigured'
  );

  // Form states for Add/Edit Modal
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [modalMode, setModalMode] = useState<'add' | 'edit'>('add');
  const [formToken, setFormToken] = useState<string>('');
  const [formDeviceId, setFormDeviceId] = useState<string>('');
  const [formNamaPerangkat, setFormNamaPerangkat] = useState<string>('');
  const [formRowNum, setFormRowNum] = useState<number | undefined>(undefined);
  const [formError, setFormError] = useState<string>('');
  const [savingDevice, setSavingDevice] = useState<boolean>(false);

  // Active Tab for Workspace Instruction vs. Live Table
  const [activeSubTab, setActiveSubTab] = useState<'devices' | 'setup'>('devices');
  const [copiedCode, setCopiedCode] = useState<boolean>(false);

  const googleAppsScriptCode = `// ==========================================
// GOOGLE APPS SCRIPT - BINDING DEVICE ENGINE
// ==========================================
// Spreadsheet URL: https://docs.google.com/spreadsheets/d/1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0/
//
// CARA DEPLOY:
// 1. Buka Spreadsheet di atas, pilih Extensions -> Apps Script
// 2. Hapus semua kode bawaan lalu Paste kode di bawah ini
// 3. Klik tombol Save (ikon floppy disk)
// 4. Klik "Deploy" -> "New deployment"
// 5. Pilih tipe "Web app" (ikon gir)
// 6. Isi Deskripsi, lalu set:
//    - Execute as: "Me" (email Anda)
//    - Who has access: "Anyone" (wajib!)
// 7. Klik Deploy, lalu berikan otorisasi Google jika diminta
// 8. Salin URL Web App yang dihasilkan lalu Paste ke input setelan di aplikasi ini!

const SPREADSHEET_ID = "1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0";
const SHEET_NAME = "Tokens";

function doGet(e) {
  try {
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    let sheet = ss.getSheetByName(SHEET_NAME);
    
    if (!sheet) {
      // Buat sheet baru jika belum ada
      sheet = ss.insertSheet(SHEET_NAME);
      sheet.appendRow(["Token", "Device ID", "Nama Perangkat"]);
    }
    
    const rows = sheet.getDataRange().getValues();
    if (rows.length <= 1) {
      return createJsonResponse({ data: [] });
    }
    
    // Ambil header di baris ke-0
    const headers = rows[0].map(h => String(h).trim().toLowerCase());
    
    const data = [];
    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      const item = {
        rowNum: i + 1
      };
      
      headers.forEach((header, index) => {
        let key = header;
        if (header === 'token') key = 'token';
        else if (header === 'device id' || header === 'device_id') key = 'deviceId';
        else if (header === 'nama perangkat' || header === 'nama_perangkat') key = 'namaPerangkat';
        
        item[key] = String(row[index] || '').trim();
      });
      
      // Tampilkan data meskipun token kosong, selama barisnya tidak sepenuhnya kosong
      if (item.token || item.deviceId || item.namaPerangkat) {
        data.push(item);
      }
    }
    
    return createJsonResponse({ data: data });
  } catch (err) {
    return createJsonResponse({ error: err.toString() }, 500);
  }
}

function doPost(e) {
  try {
    let postData;
    if (e.postData && e.postData.contents) {
      postData = JSON.parse(e.postData.contents);
    } else {
      postData = e.parameter;
    }
    
    const action = postData.action;
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    let sheet = ss.getSheetByName(SHEET_NAME);
    
    if (!sheet) {
      sheet = ss.insertSheet(SHEET_NAME);
      sheet.appendRow(["Token", "Device ID", "Nama Perangkat"]);
    }
    
    if (action === "create" || action === "add") {
      const token = String(postData.token || "").trim();
      const deviceId = String(postData.deviceId || "").trim();
      const namaPerangkat = String(postData.namaPerangkat || "").trim();
      
      // Cek duplikasi token hanya jika diisi
      if (token) {
        const rows = sheet.getDataRange().getValues();
        for (let i = 1; i < rows.length; i++) {
          if (String(rows[i][0]).trim() === token) {
            return createJsonResponse({ error: "Token '" + token + "' sudah digunakan oleh perangkat lain." }, 400);
          }
        }
      }
      
      sheet.appendRow([token, deviceId, namaPerangkat]);
      return createJsonResponse({ success: true, message: "Device berhasil di-bind ke spreadsheet!" });
      
    } else if (action === "update" || action === "edit") {
      const token = String(postData.token || "").trim();
      const deviceId = String(postData.deviceId || "").trim();
      const namaPerangkat = String(postData.namaPerangkat || "").trim();
      const rowNum = Number(postData.rowNum || 0);
      
      const rows = sheet.getDataRange().getValues();
      let foundIndex = -1;
      
      if (rowNum > 1 && rowNum <= rows.length) {
        foundIndex = rowNum;
      } else {
        // Fallback pencarian dengan token
        for (let i = 1; i < rows.length; i++) {
          if (token && String(rows[i][0]).trim() === token) {
            foundIndex = i + 1;
            break;
          }
        }
      }
      
      if (foundIndex === -1) {
        return createJsonResponse({ error: "Perangkat tidak ditemukan di spreadsheet." }, 404);
      }
      
      sheet.getRange(foundIndex, 1).setValue(token);
      sheet.getRange(foundIndex, 2).setValue(deviceId);
      sheet.getRange(foundIndex, 3).setValue(namaPerangkat);
      
      return createJsonResponse({ success: true, message: "Detail perangkat berhasil diperbarui!" });
      
    } else if (action === "delete") {
      const token = String(postData.token || "").trim();
      const rowNum = Number(postData.rowNum || 0);
      
      const rows = sheet.getDataRange().getValues();
      let foundIndex = -1;
      
      if (rowNum > 1 && rowNum <= rows.length) {
        foundIndex = rowNum;
      } else {
        // Fallback pencarian dengan token
        for (let i = 1; i < rows.length; i++) {
          if (token && String(rows[i][0]).trim() === token) {
            foundIndex = i + 1;
            break;
          }
        }
      }
      
      if (foundIndex === -1) {
        return createJsonResponse({ error: "Perangkat tidak ditemukan." }, 404);
      }
      
      sheet.deleteRow(foundIndex);
      return createJsonResponse({ success: true, message: "Binding device berhasil dihapus!" });
      
    } else {
      return createJsonResponse({ error: "Aksi '" + action + "' tidak didukung." }, 400);
    }
  } catch (err) {
    return createJsonResponse({ error: err.toString() }, 500);
  }
}

function createJsonResponse(obj, statusCode) {
  const JSONString = JSON.stringify(obj);
  return ContentService.createTextOutput(JSONString)
    .setMimeType(ContentService.MimeType.JSON);
}`;

  useEffect(() => {
    if (scriptUrl) {
      loadDevices();
    }
  }, []);

  // Auto Sync Interval - Melakukan sinkronisasi data secara berkala tanpa mengganggu aktivitas user
  useEffect(() => {
    if (!scriptUrl || !autoSyncEnabled) return;

    const intervalId = setInterval(() => {
      // Hanya sync jika tab/modal dialog tidak sedang aktif menyimpan atau memuat utama
      if (!loading && !savingDevice) {
        loadDevices(true);
      }
    }, 10000); // Sinkronisasi otomatis setiap 10 detik

    return () => clearInterval(intervalId);
  }, [scriptUrl, autoSyncEnabled, loading, savingDevice]);

  const saveScriptUrl = (url: string) => {
    const cleanUrl = url.trim();
    setScriptUrl(cleanUrl);
    localStorage.setItem('X_DEGAN_APPS_SCRIPT_URL', cleanUrl);
    if (!cleanUrl) {
      setConnectionStatus('unconfigured');
    }
  };

  const callAppsScript = async (url: string, method: 'GET' | 'POST', payload?: any) => {
    // 1. Coba koneksi LANGSUNG (Direct Browser-to-Google) terlebih dahulu.
    // Ini sangat krusial agar aplikasi berjalan 100% lancar di Vercel (yang merupakan static hosting tanpa persistent Express backend server).
    try {
      if (method === 'GET') {
        const res = await fetch(url, { method: 'GET', mode: 'cors' });
        if (res.ok) {
          const data = await res.json();
          return data;
        }
      } else {
        // Menggunakan standard POST. Mengirim string JSON tanpa custom headers (seperti application/json)
        // akan menghindari request preflight OPTIONS CORS, yang membuat Google Apps Script redirect jauh lebih stabil.
        const res = await fetch(url, {
          method: 'POST',
          mode: 'cors',
          body: JSON.stringify(payload)
        });
        if (res.ok) {
          const data = await res.json();
          return data;
        }
      }
    } catch (directErr) {
      console.warn("Panggilan langsung ke Apps Script diblokir CORS browser atau gagal. Mencoba lewat backend proxy server...", directErr);
    }

    // 2. FALLBACK: Jika panggilan langsung diblokir CORS di browser tertentu, gunakan Express proxy server.
    if (method === 'GET') {
      const res = await fetch(`/api/binding-device/proxy?scriptUrl=${encodeURIComponent(url)}`);
      if (!res.ok) {
        throw new Error(`Koneksi Gagal (HTTP ${res.status}). Pastikan Apps Script Web App di-deploy sebagai "Anyone" (Siapa saja).`);
      }
      return await res.json();
    } else {
      const res = await fetch('/api/binding-device/proxy', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          scriptUrl: url,
          payload
        })
      });
      if (!res.ok) {
        throw new Error(`Koneksi Gagal (HTTP ${res.status}). Pastikan Apps Script Web App di-deploy sebagai "Anyone" (Siapa saja).`);
      }
      return await res.json();
    }
  };

  const handleTestConnection = async () => {
    if (!scriptUrl) {
      setConnectionStatus('unconfigured');
      setErrorMsg('Tolong paste URL Google Apps Script Anda terlebih dahulu.');
      return;
    }

    setTestingConnection(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      const result = await callAppsScript(scriptUrl, 'GET');
      
      if (result.error) {
        throw new Error(result.error);
      }

      setConnectionStatus('success');
      setSuccessMsg('Koneksi ke Google Spreadsheet berhasil terjalin secara instan!');
      if (result.data) {
        setDevices(result.data);
      }
    } catch (err: any) {
      console.error(err);
      setConnectionStatus('failed');
      setErrorMsg(`Koneksi Gagal: ${err.message || 'Pastikan Apps Script dideploy sebagai "Anyone" dan URL sudah benar.'}`);
    } finally {
      setTestingConnection(false);
    }
  };

  const loadDevices = async (quiet = false) => {
    if (!scriptUrl) return;
    if (quiet) {
      setAutoSyncing(true);
    } else {
      setLoading(true);
    }
    if (!quiet) {
      setErrorMsg('');
    }
    try {
      const result = await callAppsScript(scriptUrl, 'GET');
      if (result.error) {
        throw new Error(result.error);
      }
      setDevices(result.data || []);
      setConnectionStatus('success');
    } catch (err: any) {
      console.error(err);
      if (!quiet) {
        setErrorMsg(`Gagal sinkronisasi data: ${err.message}`);
      }
      setConnectionStatus('failed');
    } finally {
      setLoading(false);
      setAutoSyncing(false);
    }
  };

  const handleAddDeviceClick = () => {
    setModalMode('add');
    setFormToken(generateRandomToken());
    setFormDeviceId('');
    setFormNamaPerangkat('');
    setFormRowNum(undefined);
    setFormError('');
    setIsModalOpen(true);
  };

  const handleEditDeviceClick = (device: DeviceToken) => {
    setModalMode('edit');
    setFormToken(device.token);
    setFormDeviceId(device.deviceId);
    setFormNamaPerangkat(device.namaPerangkat);
    setFormRowNum(device.rowNum);
    setFormError('');
    setIsModalOpen(true);
  };

  const generateRandomToken = () => {
    // Menghasilkan 'ZAN' diikuti oleh 3 angka acak unik (masing-masing angka berbeda)
    const digits = '0123456789'.split('');
    let numStr = '';
    for (let i = 0; i < 3; i++) {
      const idx = Math.floor(Math.random() * digits.length);
      numStr += digits.splice(idx, 1)[0];
    }
    return `ZAN${numStr}`;
  };

  const handleSaveDevice = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');

    if (!formDeviceId.trim() && !formNamaPerangkat.trim()) {
      setFormError('Device ID atau Nama Perangkat harus diisi.');
      return;
    }

    setSavingDevice(true);
    try {
      const payload = {
        action: modalMode === 'add' ? 'create' : 'update',
        token: formToken.trim(),
        deviceId: formDeviceId.trim(),
        namaPerangkat: formNamaPerangkat.trim(),
        rowNum: formRowNum
      };

      const result = await callAppsScript(scriptUrl, 'POST', payload);
      if (result.error) {
        throw new Error(result.error);
      }

      setSuccessMsg(modalMode === 'add' ? 'Device baru berhasil di-bind ke spreadsheet!' : 'Data device berhasil diperbarui!');
      setIsModalOpen(false);
      
      // Reload devices to sync with cloud sheet
      loadDevices();
      
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err: any) {
      setFormError(err.message || 'Gagal menyimpan data ke Spreadsheet.');
    } finally {
      setSavingDevice(false);
    }
  };

  const handleInlineGenerateToken = async (device: DeviceToken) => {
    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      const newToken = generateRandomToken();
      const payload = {
        action: 'update',
        token: newToken,
        deviceId: device.deviceId,
        namaPerangkat: device.namaPerangkat,
        rowNum: device.rowNum
      };

      const result = await callAppsScript(scriptUrl, 'POST', payload);
      if (result.error) {
        throw new Error(result.error);
      }

      setSuccessMsg(`Token "${newToken}" berhasil di-generate untuk "${device.namaPerangkat || 'Device'}"!`);
      loadDevices();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err: any) {
      setErrorMsg(`Gagal generate token: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteDevice = async (token: string, name: string, rowNum?: number) => {
    if (!confirm(`Apakah Anda yakin ingin menghapus binding device "${name || 'Tanpa Nama'}"?\nTindakan ini akan menghapus baris data di Google Sheet secara permanen.`)) {
      return;
    }

    setLoading(true);
    setErrorMsg('');
    try {
      const payload = {
        action: 'delete',
        token: token,
        rowNum: rowNum
      };

      const result = await callAppsScript(scriptUrl, 'POST', payload);
      if (result.error) {
        throw new Error(result.error);
      }

      setSuccessMsg(`Binding device "${name || 'Tanpa Nama'}" berhasil dihapus.`);
      loadDevices();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err: any) {
      setErrorMsg(`Gagal menghapus device: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleCopyCode = () => {
    navigator.clipboard.writeText(googleAppsScriptCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2500);
  };

  const filteredDevices = devices.filter(d => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return true;
    return (
      (d.token && d.token.toLowerCase().includes(q)) ||
      (d.deviceId && d.deviceId.toLowerCase().includes(q)) ||
      (d.namaPerangkat && d.namaPerangkat.toLowerCase().includes(q))
    );
  });

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Overview/Header Section */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-800 flex items-center">
            <Smartphone className="w-5 h-5 mr-2 text-indigo-600" />
            Binding Device Panel
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Manajemen token otentikasi hardware scanner & device mobile yang terhubung langsung ke Google Spreadsheet.
          </p>
        </div>

        {/* Tab Buttons & Settings Indicator */}
        <div className="flex items-center space-x-2">
          <div className="flex rounded-xl bg-slate-100 p-1 shrink-0">
            <button
              onClick={() => setActiveSubTab('devices')}
              className={`px-4 py-2 text-xs font-bold rounded-lg transition-all flex items-center cursor-pointer ${
                activeSubTab === 'devices' 
                  ? 'bg-white text-slate-800 shadow-xs' 
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              <Database className="w-3.5 h-3.5 mr-1.5" />
              Daftar Binding Device
            </button>
            <button
              onClick={() => setActiveSubTab('setup')}
              className={`px-4 py-2 text-xs font-bold rounded-lg transition-all flex items-center cursor-pointer ${
                activeSubTab === 'setup' 
                  ? 'bg-white text-slate-800 shadow-xs' 
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              <Code className="w-3.5 h-3.5 mr-1.5" />
              Panduan Setup Script
            </button>
          </div>
        </div>
      </div>

      {/* Global Alerts */}
      {successMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl flex items-center space-x-3 text-xs font-semibold animate-fadeIn">
          <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl flex items-center space-x-3 text-xs font-semibold animate-fadeIn">
          <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Main Connection Setup Bar */}
      <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-md space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-start space-x-3">
            <div className={`p-2.5 rounded-xl shrink-0 ${
              connectionStatus === 'success' 
                ? 'bg-emerald-50 text-emerald-600 border border-emerald-100' 
                : connectionStatus === 'failed' 
                  ? 'bg-rose-50 text-rose-600 border border-rose-100' 
                  : 'bg-indigo-50 text-indigo-600 border border-indigo-100'
            }`}>
              <Link2 className="w-5 h-5" />
            </div>
            <div>
              <h4 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                Integrasi Google Apps Script Web App
                {connectionStatus === 'success' && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-emerald-100 text-emerald-800">
                    AKTIF & TERHUBUNG
                  </span>
                )}
                {connectionStatus === 'failed' && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-rose-100 text-rose-800 animate-pulse">
                    KONEKSI TERPUTUS
                  </span>
                )}
                {connectionStatus === 'unconfigured' && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-slate-100 text-slate-600">
                    BELUM DIKONFIGURASI
                  </span>
                )}
              </h4>
              <p className="text-[11px] text-slate-400 mt-0.5">
                Masukkan URL Web App hasil deploy Google Apps Script Anda untuk sinkronisasi ke tabel sheet <strong className="font-semibold text-slate-600">Tokens</strong>.
              </p>
            </div>
          </div>
          <div className="flex items-center space-x-2 shrink-0">
            <button
              onClick={() => setActiveSubTab('setup')}
              className="px-3.5 py-2 border border-slate-200 text-slate-600 hover:bg-slate-50 font-bold text-xs rounded-xl transition-all cursor-pointer flex items-center"
            >
              <HelpCircle className="w-3.5 h-3.5 mr-1.5" />
              Bagaimana caranya?
            </button>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <input
              type="text"
              value={scriptUrl}
              onChange={(e) => saveScriptUrl(e.target.value)}
              placeholder="Contoh: https://script.google.com/macros/s/AKfycb.../exec"
              className="w-full pl-3 pr-10 py-3 border border-slate-200 rounded-xl text-xs font-semibold bg-slate-50/50 focus:bg-white focus:outline-hidden focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 transition-all font-mono"
            />
            {scriptUrl && (
              <button
                onClick={() => saveScriptUrl('')}
                className="absolute right-3 top-3.5 text-slate-400 hover:text-slate-600"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
          <button
            onClick={handleTestConnection}
            disabled={testingConnection || !scriptUrl}
            className={`px-5 py-3 rounded-xl font-bold text-xs transition-all active:scale-95 flex items-center justify-center shrink-0 cursor-pointer ${
              !scriptUrl
                ? 'bg-slate-100 text-slate-400 border border-slate-200 cursor-not-allowed'
                : 'bg-indigo-600 hover:bg-indigo-700 text-white shadow-md hover:shadow-indigo-600/10'
            }`}
          >
            <RefreshCw className={`w-3.5 h-3.5 mr-2 ${testingConnection ? 'animate-spin' : ''}`} />
            {testingConnection ? 'Menguji...' : 'Uji & Muat Data'}
          </button>
        </div>
      </div>

      {activeSubTab === 'setup' ? (
        /* Workspace Setup / Instruction Panel */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 animate-fadeIn">
          {/* Steps Explanation (5 cols) */}
          <div className="lg:col-span-5 space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 shadow-md p-6 space-y-6">
              <div>
                <h4 className="font-bold text-slate-800 text-sm flex items-center">
                  <Sparkles className="w-4 h-4 mr-1.5 text-amber-500" />
                  Alur Setup Otomatis Spreadsheet
                </h4>
                <p className="text-[11px] text-slate-400 mt-1">
                  Database binding device ini terpusat pada file Google Spreadsheet Anda. Ikuti petunjuk singkat berikut:
                </p>
              </div>

              <div className="space-y-4">
                <div className="flex space-x-3">
                  <div className="w-6 h-6 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-bold flex items-center justify-center shrink-0">
                    1
                  </div>
                  <div>
                    <h5 className="font-bold text-slate-700 text-xs">Buka Google Sheet Anda</h5>
                    <p className="text-[10px] text-slate-400 mt-0.5 leading-normal">
                      Buka spreadsheet tujuan Anda yaitu:{' '}
                      <a 
                        href="https://docs.google.com/spreadsheets/d/1MjNR4lAJf02-jfsoT51OPT-XegchiCruwZyYODdpPP0/" 
                        target="_blank" 
                        rel="noreferrer"
                        className="text-indigo-600 hover:underline font-semibold inline-flex items-center"
                      >
                        1MjNR4l... <ExternalLink className="w-3 h-3 ml-0.5" />
                      </a>
                    </p>
                  </div>
                </div>

                <div className="flex space-x-3">
                  <div className="w-6 h-6 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-bold flex items-center justify-center shrink-0">
                    2
                  </div>
                  <div>
                    <h5 className="font-bold text-slate-700 text-xs">Buka Apps Script</h5>
                    <p className="text-[10px] text-slate-400 mt-0.5 leading-normal">
                      Di menu atas Google Sheet, klik <strong className="font-semibold text-slate-600">Extensions / Ekstensi</strong> &rarr; <strong className="font-semibold text-slate-600">Apps Script</strong>.
                    </p>
                  </div>
                </div>

                <div className="flex space-x-3">
                  <div className="w-6 h-6 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-bold flex items-center justify-center shrink-0">
                    3
                  </div>
                  <div>
                    <h5 className="font-bold text-slate-700 text-xs">Salin & Tempel Kode</h5>
                    <p className="text-[10px] text-slate-400 mt-0.5 leading-normal">
                      Salin seluruh kode dari panel sebelah kanan, hapus kode default di editor Apps Script, lalu tempel kode tersebut.
                    </p>
                  </div>
                </div>

                <div className="flex space-x-3">
                  <div className="w-6 h-6 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-bold flex items-center justify-center shrink-0">
                    4
                  </div>
                  <div>
                    <h5 className="font-bold text-slate-700 text-xs">Deploy sebagai Web App</h5>
                    <p className="text-[10px] text-slate-400 mt-0.5 leading-normal">
                      Klik <strong className="font-semibold text-slate-600">Deploy</strong> &rarr; <strong className="font-semibold text-slate-600">New deployment</strong>. Pilih tipe <strong className="font-semibold text-slate-600">Web app</strong>. Atur "Execute as" ke <strong className="font-semibold text-slate-600">Me</strong>, dan "Who has access" ke <strong className="font-bold text-rose-500">Anyone</strong>.
                    </p>
                  </div>
                </div>

                <div className="flex space-x-3">
                  <div className="w-6 h-6 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-bold flex items-center justify-center shrink-0">
                    5
                  </div>
                  <div>
                    <h5 className="font-bold text-slate-700 text-xs">Paste URL & Hubungkan</h5>
                    <p className="text-[10px] text-slate-400 mt-0.5 leading-normal">
                      Salin Web App URL yang dihasilkan (biasanya diakhiri dengan <code className="bg-slate-100 px-1 rounded text-rose-600 text-[9px]">/exec</code>) lalu paste ke kolom integrasi di atas.
                    </p>
                  </div>
                </div>
              </div>

              <div className="p-3.5 bg-amber-50 border border-amber-100 rounded-xl space-y-1">
                <span className="text-[10px] font-bold text-amber-600 uppercase tracking-wider flex items-center">
                  <Info className="w-3.5 h-3.5 mr-1 shrink-0" /> PENTING!
                </span>
                <p className="text-[10px] text-amber-900 leading-relaxed">
                  Apabila Anda memodifikasi atau memperbarui kode Apps Script di kemudian hari, pastikan untuk melakukan <strong>Manage Deployments</strong> dan mengklik <strong>Edit</strong> &rarr; pilih <strong>New version</strong> agar perubahan kode ter-update secara online.
                </p>
              </div>
            </div>
          </div>

          {/* Copyable Code Panel (7 cols) */}
          <div className="lg:col-span-7">
            <div className="bg-slate-900 rounded-2xl border border-slate-800 shadow-xl overflow-hidden flex flex-col h-[520px]">
              {/* Terminal Header */}
              <div className="px-5 py-3.5 bg-slate-950/80 border-b border-slate-800 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-rose-500"></div>
                  <div className="w-2.5 h-2.5 rounded-full bg-amber-500"></div>
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
                  <span className="text-[10px] font-bold text-slate-400 font-mono ml-2">google_apps_script_engine.js</span>
                </div>
                <button
                  type="button"
                  onClick={handleCopyCode}
                  className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center cursor-pointer ${
                    copiedCode 
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                      : 'bg-white/5 hover:bg-white/10 text-slate-300 border border-white/10'
                  }`}
                >
                  {copiedCode ? (
                    <>
                      <Check className="w-3.5 h-3.5 mr-1" />
                      Berhasil Disalin!
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5 mr-1" />
                      Salin Seluruh Kode
                    </>
                  )}
                </button>
              </div>

              {/* Code Box */}
              <div className="flex-grow p-5 overflow-auto">
                <pre className="font-mono text-[11px] text-slate-300 leading-relaxed select-all">
                  {googleAppsScriptCode}
                </pre>
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* Live Device Database Grid & Actions */
        <div className="space-y-4">
          
          {/* Info Banner tentang cara memunculkan data tanpa token */}
          {scriptUrl && (
            <div className="p-4 bg-amber-50/70 border border-amber-100 rounded-2xl flex items-start space-x-3 text-slate-700">
              <Info className="w-4.5 h-4.5 text-amber-600 mt-0.5 shrink-0" />
              <div className="space-y-1">
                <h5 className="font-bold text-amber-800 text-xs">PENTING: Agar baris tanpa Token muncul di aplikasi</h5>
                <p className="text-[11px] text-slate-600 leading-relaxed">
                  Jika data perangkat baru yang Anda ketik langsung di Google Spreadsheet (yang belum memiliki token) belum muncul di tabel bawah, pastikan Anda telah menyalin kode Google Apps Script terbaru di tab <span className="font-bold text-indigo-600 cursor-pointer underline hover:text-indigo-800" onClick={() => setActiveSubTab('setup')}>Panduan Setup</span>, lalu deploy ulang sebagai <strong className="font-semibold text-slate-800">New version</strong> di editor Google Apps Script Anda agar filter token kosong dihilangkan.
                </p>
              </div>
            </div>
          )}
          
          {/* Table Toolbar */}
          <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
            {/* Search inputs */}
            <div className="relative w-full sm:w-80">
              <Search className="absolute left-3.5 top-3.5 w-4 h-4 text-slate-400" />
              <input
                type="text"
                placeholder="Cari Token, ID Device, Nama..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-2.5 border border-slate-200 bg-white rounded-xl text-xs font-semibold focus:outline-hidden focus:border-indigo-500"
              />
            </div>

            {/* Sync & Add actions */}
            <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto justify-end">
              {scriptUrl && (
                <div className="flex items-center bg-slate-50 border border-slate-200/80 px-3 py-1.5 rounded-xl text-[11px] font-semibold text-slate-600 space-x-2">
                  <div className="flex items-center space-x-1.5">
                    <span className={`relative flex h-2 w-2`}>
                      {autoSyncEnabled && (
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      )}
                      <span className={`relative inline-flex rounded-full h-2 w-2 ${autoSyncEnabled ? 'bg-emerald-500' : 'bg-slate-300'}`}></span>
                    </span>
                    <span>Auto Sync:</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setAutoSyncEnabled(!autoSyncEnabled)}
                    className={`px-2 py-0.5 rounded-md font-bold text-[10px] transition-all cursor-pointer ${
                      autoSyncEnabled 
                        ? 'bg-emerald-100 text-emerald-800 border border-emerald-200 hover:bg-emerald-200' 
                        : 'bg-slate-200 text-slate-700 border border-slate-300 hover:bg-slate-300'
                    }`}
                  >
                    {autoSyncEnabled ? 'AKTIF' : 'NONAKTIF'}
                  </button>
                  {autoSyncing && (
                    <span className="text-slate-400 italic font-medium animate-pulse flex items-center ml-1">
                      <RefreshCw className="w-3 h-3 animate-spin mr-1 text-indigo-500" />
                      Syncing...
                    </span>
                  )}
                </div>
              )}

              <button
                type="button"
                onClick={() => loadDevices()}
                disabled={loading || !scriptUrl}
                className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-xs transition-all flex items-center justify-center cursor-pointer active:scale-95 disabled:bg-slate-100 disabled:text-slate-400 disabled:border-slate-200"
                title="Refresh Sinkronisasi Sheet"
              >
                <RefreshCw className={`w-4 h-4 mr-1.5 ${loading || autoSyncing ? 'animate-spin' : ''}`} />
                Sinkronkan Spreadsheet
              </button>
            </div>
          </div>

          {/* Table Container */}
          <div className="bg-white border border-slate-200/60 rounded-2xl shadow-md overflow-hidden">
            {loading ? (
              <div className="py-24 flex flex-col items-center justify-center text-slate-400 text-xs">
                <RefreshCw className="w-10 h-10 animate-spin text-indigo-500 mb-3" />
                <span className="font-semibold text-slate-600">Mengambil database dari Google Spreadsheet...</span>
                <span className="text-[10px] text-slate-400 mt-1">Menggunakan proxy server aman bebas CORS</span>
              </div>
            ) : !scriptUrl ? (
              /* Setup Missing State */
              <div className="py-16 text-center max-w-md mx-auto px-4">
                <div className="w-12 h-12 rounded-2xl bg-slate-50 border border-slate-200 flex items-center justify-center text-slate-400 mx-auto mb-4">
                  <Database className="w-6 h-6" />
                </div>
                <h4 className="font-bold text-slate-800 text-sm">Integrasi Spreadsheet Belum Siap</h4>
                <p className="text-slate-500 text-xs mt-1.5 leading-relaxed">
                  Silakan konfigurasikan Web App URL dari Google Apps Script Anda terlebih dahulu melalui tab <span className="font-semibold text-indigo-600 hover:underline cursor-pointer" onClick={() => setActiveSubTab('setup')}>Panduan Setup Script</span> atau kolom di atas.
                </p>
                <button
                  type="button"
                  onClick={() => setActiveSubTab('setup')}
                  className="mt-5 px-4 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-600 font-bold text-xs rounded-xl transition-all inline-flex items-center cursor-pointer"
                >
                  <Code className="w-4 h-4 mr-1.5" />
                  Lihat Kode Apps Script
                </button>
              </div>
            ) : filteredDevices.length === 0 ? (
              /* Empty State */
              <div className="py-20 text-center max-w-sm mx-auto px-4">
                <div className="w-12 h-12 rounded-2xl bg-indigo-50/50 border border-indigo-100 text-indigo-500 flex items-center justify-center mx-auto mb-4">
                  <Smartphone className="w-6 h-6" />
                </div>
                <h4 className="font-bold text-slate-800 text-sm">Tidak Ada Device yang Terdaftar</h4>
                <p className="text-slate-500 text-xs mt-1 leading-relaxed">
                  {searchQuery 
                    ? 'Hasil pencarian Anda tidak ditemukan. Coba bersihkan filter pencarian atau kata kunci.' 
                    : 'Belum ada data perangkat terdaftar di Google Spreadsheet Anda pada tab Tokens.'}
                </p>
                {!searchQuery && (
                  <p className="text-[11px] text-slate-400 mt-3 italic leading-normal">
                    Silakan tambahkan data baris perangkat baru langsung di file Google Spreadsheet Anda. Data akan otomatis muncul di sini setelah Anda mengklik tombol "Sinkronkan Spreadsheet" di atas.
                  </p>
                )}
              </div>
            ) : (
              /* Responsive Data Table */
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-150 text-xs font-bold text-slate-400 uppercase tracking-wider">
                      <th className="py-4 px-6 text-center w-16">No</th>
                      <th className="py-4 px-6">Otorisasi Token</th>
                      <th className="py-4 px-6">ID Perangkat (Device ID)</th>
                      <th className="py-4 px-6">Nama Perangkat</th>
                      <th className="py-4 px-6 text-center w-28">Aksi</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-xs text-slate-600 bg-white">
                    {filteredDevices.map((device, index) => (
                      <tr key={`device-row-${device.rowNum || index}`} className="hover:bg-slate-50/40 transition-colors">
                        <td className="py-3.5 px-6 font-bold text-slate-400 text-center">{index + 1}</td>
                        <td className="py-3.5 px-6">
                          <div className="flex items-center space-x-2.5">
                            <div className="p-1.5 bg-indigo-50 text-indigo-600 rounded-lg shrink-0">
                              <Key className="w-3.5 h-3.5" />
                            </div>
                            <div>
                              <div className={`font-mono font-bold tracking-wide text-[11px] px-2 py-0.5 rounded-md border ${
                                device.token 
                                  ? 'text-slate-800 bg-slate-100 border-slate-200' 
                                  : 'text-amber-600 bg-amber-50 border-amber-200 italic font-semibold'
                              }`}>
                                {device.token || 'Tanpa Token'}
                              </div>
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 px-6">
                          <span className="font-mono text-slate-500 select-all font-semibold">
                            {device.deviceId || '-'}
                          </span>
                        </td>
                        <td className="py-3.5 px-6 font-bold text-slate-700">
                          {device.namaPerangkat || '-'}
                        </td>
                        <td className="py-3.5 px-6 text-center">
                          <div className="flex items-center justify-center gap-1.5">
                            {!device.token ? (
                              <button
                                type="button"
                                onClick={() => handleInlineGenerateToken(device)}
                                className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-[11px] rounded-lg transition-all flex items-center shadow-xs cursor-pointer active:scale-95 whitespace-nowrap"
                                title="Generate Token Otomatis untuk Perangkat ini"
                              >
                                <Sparkles className="w-3.5 h-3.5 mr-1 text-white animate-pulse" />
                                Generate
                              </button>
                            ) : null}
                            <button
                              type="button"
                              onClick={() => handleEditDeviceClick(device)}
                              className="p-2 text-indigo-600 hover:bg-indigo-50 border border-slate-100 hover:border-indigo-100 rounded-lg transition-all cursor-pointer flex items-center justify-center"
                              title="Edit Perangkat / Input Token Manual"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* --- ADD / EDIT BINDING DEVICE MODAL --- */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-950/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full overflow-hidden animate-fadeIn">
            {/* Modal Header */}
            <div className="px-6 py-4 border-b border-slate-150 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm flex items-center">
                <Smartphone className="w-4.5 h-4.5 text-indigo-600 mr-2" />
                Edit & Bind Detail Device
              </h4>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
              >
                <X className="w-4.5 h-4.5" />
              </button>
            </div>

            {/* Modal Body / Form */}
            <form onSubmit={handleSaveDevice} className="p-6 space-y-4">
              {formError && (
                <div className="p-3.5 bg-rose-50 border border-rose-100 rounded-xl text-rose-800 text-[11px] font-semibold flex items-center space-x-2">
                  <AlertCircle className="w-4 h-4 text-rose-500 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              {/* Token field */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Token Otorisasi
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={formToken}
                    onChange={(e) => setFormToken(e.target.value)}
                    placeholder="Contoh: ZAN123"
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-xl text-xs font-bold bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                  />
                  <button
                    type="button"
                    onClick={() => setFormToken(generateRandomToken())}
                    className="px-3 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 text-indigo-600 font-bold text-[10px] rounded-xl transition-colors shrink-0 flex items-center cursor-pointer font-sans"
                    title="Generate Token Otomatis (ZAN + 3 Angka)"
                  >
                    <Sparkles className="w-3.5 h-3.5 mr-1 text-indigo-500" />
                    Generate Token
                  </button>
                </div>
                <p className="text-[10px] text-slate-400 leading-normal">
                  Gunakan tombol "Generate Token" di atas untuk men-generate token (ZAN + 3 angka acak unik) atau isi token kustom Anda sendiri.
                </p>
              </div>

              {/* Device ID Field */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                  ID Perangkat (Device ID)
                </label>
                <input
                  type="text"
                  value={formDeviceId}
                  onChange={(e) => setFormDeviceId(e.target.value)}
                  placeholder="Masukkan IMEI, UUID, IP, atau ID Hardware Browser..."
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs font-semibold bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                />
                <p className="text-[10px] text-slate-400">
                  ID unik dari mesin scanner fisik atau device Android/iOS yang berpasangan.
                </p>
              </div>

              {/* Nama Perangkat Field */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Nama / Deskripsi Perangkat
                </label>
                <input
                  type="text"
                  value={formNamaPerangkat}
                  onChange={(e) => setFormNamaPerangkat(e.target.value)}
                  placeholder="Contoh: Mesin Scanner Gerbang Utama, Tablet Guru Kelas 11"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs font-semibold bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                />
              </div>

              {/* Modal Actions */}
              <div className="pt-4 flex justify-end space-x-2 border-t border-slate-100 mt-6">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-500 hover:text-slate-800 rounded-xl text-xs font-bold hover:bg-slate-50 transition-all cursor-pointer"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={savingDevice}
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md hover:shadow-indigo-600/10 transition-all flex items-center cursor-pointer"
                >
                  {savingDevice && <RefreshCw className="w-3.5 h-3.5 mr-1.5 animate-spin" />}
                  {savingDevice ? 'Menyimpan...' : 'Simpan Perangkat'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
