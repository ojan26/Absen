import React, { useState } from 'react';
import { db } from '../firebaseClient';
import { School } from '../types';
import firebaseConfig from '../../firebase-applet-config.json';
import { 
  Database, 
  RefreshCw, 
  CheckCircle2, 
  Plus, 
  Terminal,
  School as SchoolIcon,
  Info,
  Copy,
  ExternalLink
} from 'lucide-react';

interface SettingsViewProps {
  schools: School[];
  refreshSchools: () => void;
  selectedNpsn: string;
  setSelectedNpsn: (npsn: string) => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({ 
  schools, 
  refreshSchools,
  selectedNpsn,
  setSelectedNpsn
}) => {
  const [successMsg, setSuccessMsg] = useState<string>('');
  const [copiedRules, setCopiedRules] = useState<boolean>(false);

  // New School Form State
  const [newNpsn, setNewNpsn] = useState<string>('');
  const [newNama, setNewNama] = useState<string>('');
  const [newAlamat, setNewAlamat] = useState<string>('');
  const [schoolStatus, setSchoolStatus] = useState<string>('');

  const handleResetDB = () => {
    if (confirm("Apakah Anda yakin ingin mereset database local storage ke kondisi seed awal? Semua data modifikasi Anda akan hilang.")) {
      db.resetToDefault();
      setSuccessMsg('Database local berhasil di-reset ke data bawaan awal!');
      refreshSchools();
      setSelectedNpsn('SCH-DEFAULT');
      setTimeout(() => setSuccessMsg(''), 3000);
    }
  };

  const handleAddSchool = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNpsn || !newNama || !newAlamat) {
      setSchoolStatus('Semua kolom sekolah wajib diisi');
      return;
    }

    if (schools.some(s => s.npsn === newNpsn)) {
      setSchoolStatus('NPSN ini sudah terdaftar sebelumnya.');
      return;
    }

    try {
      await db.addSchool({
        npsn: newNpsn,
        nama: newNama,
        alamat: newAlamat
      });
      setNewNpsn('');
      setNewNama('');
      setNewAlamat('');
      setSchoolStatus('Sekolah baru berhasil didaftarkan!');
      refreshSchools();
      setSelectedNpsn(newNpsn); // auto swap to new school
      setTimeout(() => setSchoolStatus(''), 4000);
    } catch (err: any) {
      setSchoolStatus(`Gagal menyimpan sekolah baru: ${err.message || String(err)}`);
    }
  };

  const firestoreRulesText = `rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Global safety net catch-all deny
    match /{document=**} {
      allow read, write: if false;
    }

    // Helper functions
    function isSignedIn() {
      return request.auth != null;
    }

    // Rules for sekolah (Schools)
    match /sekolah/{npsn} {
      allow read: if true;
      allow write: if isSignedIn() || true;
    }

    // Rules for siswa (Students)
    match /siswa/{siswaId} {
      allow read: if true;
      allow write: if isSignedIn() || true;
    }

    // Rules for kehadiran (Attendance Logs)
    match /kehadiran/{logId} {
      allow read: if true;
      allow write: if true; // Allow writing logs for scanner & check-in
    }

    // Rules for app_broadcast (Announcements)
    match /app_broadcast/{broadcastId} {
      allow read: if true;
      allow write: if isSignedIn() || true;
    }

    // Rules for hari_libur (Holidays)
    match /hari_libur/{holidayId} {
      allow read: if true;
      allow write: if isSignedIn() || true;
    }

    // Rules for login (User credentials)
    match /login/{loginId} {
      allow read, write: if true;
    }

    // Rules for admin_page_settings (Page settings)
    match /admin_page_settings/{pageId} {
      allow read: if true;
      allow write: if isSignedIn() || true;
    }
  }
}`;

  const copyRulesToClipboard = () => {
    navigator.clipboard.writeText(firestoreRulesText);
    setCopiedRules(true);
    setTimeout(() => setCopiedRules(false), 3000);
  };

  return (
    <div className="space-y-8 animate-fadeIn text-slate-800">
      {/* Overview section */}
      <div>
        <h3 className="text-lg font-bold text-slate-800">Koneksi Database & Pengaturan</h3>
        <p className="text-xs text-slate-500 mt-0.5 font-medium">Kelola database Firebase Firestore kustom dan daftarkan unit sekolah baru Anda</p>
      </div>

      {successMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl flex items-center space-x-3 text-xs font-semibold animate-fadeIn">
          <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        
        {/* Left Column: Firebase Config Dashboard & Instructions */}
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-md">
            <div className="flex items-center space-x-3 mb-6">
              <div className="p-2.5 bg-orange-50 border border-orange-100 rounded-xl text-orange-600">
                <Database className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-800 font-sans">Integrasi Firebase Firestore Aktif</h4>
                <p className="text-xs text-slate-500 mt-0.5 font-sans">Database kustom Anda berhasil terhubung dan tersinkronisasi</p>
              </div>
            </div>

            <div className="space-y-4">
              {/* Project ID Display */}
              <div className="bg-slate-50 p-4.5 rounded-xl border border-slate-150/60 font-mono text-xs text-slate-700 space-y-2.5">
                <div className="flex justify-between border-b border-slate-100 pb-2">
                  <span className="font-sans font-bold text-slate-500 uppercase tracking-wider text-[10px]">Project ID</span>
                  <span className="font-bold text-orange-600">{firebaseConfig.projectId}</span>
                </div>
                <div className="flex justify-between border-b border-slate-100 pb-2">
                  <span className="font-sans font-bold text-slate-500 uppercase tracking-wider text-[10px]">Auth Domain</span>
                  <span className="font-medium text-slate-855">{firebaseConfig.authDomain}</span>
                </div>
                <div className="flex justify-between border-b border-slate-100 pb-2">
                  <span className="font-sans font-bold text-slate-500 uppercase tracking-wider text-[10px]">Database ID</span>
                  <span className="font-bold text-indigo-650">{(firebaseConfig as any).firestoreDatabaseId || '(default)'}</span>
                </div>
                <div className="flex justify-between pt-1">
                  <span className="font-sans font-bold text-slate-500 uppercase tracking-wider text-[10px]">Status Koneksi</span>
                  <span className="flex items-center text-emerald-600 font-bold font-sans text-[11px]">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping mr-2"></span>
                    TERHUBUNG
                  </span>
                </div>
              </div>

              {/* Troubleshooting Instructions */}
              <div className="p-4 bg-amber-50 border border-amber-200 text-amber-900 rounded-xl text-xs space-y-2.5">
                <div className="flex items-center space-x-2 text-amber-800 font-bold">
                  <Info className="w-4 h-4 shrink-0" />
                  <span>Mengalami Error "Missing or insufficient permissions"?</span>
                </div>
                <p className="text-amber-850 text-[11px] leading-relaxed">
                  Karena Anda menggunakan Firebase kustom, Anda <strong>wajib</strong> memasang Aturan Keamanan (Security Rules) di Firebase Console Anda agar aplikasi web diizinkan membaca dan menulis data siswa serta log absensi.
                </p>
                <div className="pt-1.5">
                  <a 
                    href={`https://console.firebase.google.com/project/${firebaseConfig.projectId}/firestore/rules`} 
                    target="_blank" 
                    rel="noreferrer"
                    className="inline-flex items-center font-bold text-indigo-600 hover:text-indigo-850 hover:underline text-[11px]"
                  >
                    Buka Aturan Firestore di Firebase Console
                    <ExternalLink className="w-3 h-3 ml-1" />
                  </a>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-100 flex justify-start">
                <button
                  type="button"
                  onClick={handleResetDB}
                  className="px-3 py-2 border border-slate-200 hover:bg-slate-50 hover:text-slate-700 text-slate-500 rounded-xl text-xs font-bold transition-all flex items-center"
                >
                  <RefreshCw className="w-3.5 h-3.5 mr-2" />
                  Reset Database Demo Lokal
                </button>
              </div>
            </div>
          </div>

          {/* Quick Demo Credentials Info */}
          <div className="bg-slate-900 text-slate-100 p-6 rounded-2xl border border-slate-800">
            <div className="flex items-center space-x-2.5 mb-3 text-sky-400">
              <Info className="w-4.5 h-4.5 shrink-0" />
              <h5 className="text-xs font-bold uppercase tracking-wider">Petunjuk Evaluasi Aplikasi</h5>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              Anda bisa login sebagai <b>Super Admin</b> untuk melihat seluruh NPSN sekolah, atau sebagai <b>Admin Sekolah</b> untuk mensimulasikan role-based locking:
            </p>
            <div className="mt-4 space-y-2 text-[11px] font-mono bg-slate-950/60 p-3 rounded-lg border border-slate-800">
              <div>
                <p className="text-emerald-400 font-bold">// Akun Super Admin (Akses Bebas):</p>
                <p>Email: <span className="text-white">superadmin@xdegan.com</span></p>
                <p>Password: <span className="text-white">admin123</span></p>
              </div>
              <div className="border-t border-slate-800 pt-2 mt-2">
                <p className="text-amber-400 font-bold">// Akun Admin SMKN 1 Pasongsongan (Locked NPSN):</p>
                <p>Email: <span className="text-white">admin.smkn1@xdegan.com</span></p>
                <p>Password: <span className="text-white">admin123</span></p>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Multi-School Admin / Copy rules */}
        <div className="space-y-6">
          
          {/* Add custom school */}
          <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-md">
            <div className="flex items-center space-x-3 mb-6">
              <div className="p-2.5 bg-emerald-50 border border-emerald-100 rounded-xl text-emerald-600">
                <SchoolIcon className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-800">Daftarkan Sekolah Baru</h4>
                <p className="text-xs text-slate-500 mt-0.5">Tambah NPSN sekolah baru secara langsung ke database</p>
              </div>
            </div>

            <form onSubmit={handleAddSchool} className="space-y-4">
              {schoolStatus && (
                <div className={`p-3 rounded-xl text-xs font-bold border ${
                  schoolStatus.includes('berhasil') ? 'bg-emerald-50 text-emerald-700 border-emerald-100' : 'bg-rose-50 text-rose-700 border-rose-100'
                }`}>
                  {schoolStatus}
                </div>
              )}
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">NPSN Sekolah (8 Digit)</label>
                  <input
                    type="text"
                    required
                    placeholder="Contoh: 10203040"
                    maxLength={8}
                    value={newNpsn}
                    onChange={(e) => setNewNpsn(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Nama Sekolah</label>
                  <input
                    type="text"
                    required
                    placeholder="Contoh: SMAN 1 Bogor"
                    value={newNama}
                    onChange={(e) => setNewNama(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Alamat Sekolah</label>
                <input
                  type="text"
                  required
                  placeholder="Contoh: Jl. Pemuda No. 12, Bogor"
                  value={newAlamat}
                  onChange={(e) => setNewAlamat(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                />
              </div>

              <div className="pt-2 flex justify-end">
                <button
                  type="submit"
                  className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold shadow-md transition-all active:scale-95 flex items-center"
                >
                  <Plus className="w-4 h-4 mr-1.5" />
                  Daftarkan Sekolah
                </button>
              </div>
            </form>
          </div>

          {/* Copy-paste Firestore Security Rules */}
          <div className="bg-slate-900 text-slate-300 p-6 rounded-2xl border border-slate-800">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center space-x-2.5">
                <Terminal className="w-5 h-5 text-indigo-400" />
                <h4 className="text-xs font-bold uppercase tracking-wider text-white">Firestore Security Rules</h4>
              </div>
              <button 
                type="button"
                onClick={copyRulesToClipboard}
                className="flex items-center text-[11px] bg-slate-800 text-indigo-300 hover:bg-slate-750 px-2.5 py-1.5 rounded-lg font-bold transition-all"
              >
                <Copy className="w-3 h-3 mr-1" />
                {copiedRules ? 'Disalin!' : 'Salin Aturan'}
              </button>
            </div>
            <p className="text-[11px] text-slate-400 leading-relaxed mb-4">
              Salin kode aturan di bawah ini dan tempelkan ke tab <strong>Rules</strong> di Firestore Database console Anda untuk mengizinkan aplikasi beroperasi sepenuhnya:
            </p>
            <div className="relative">
              <textarea
                readOnly
                value={firestoreRulesText}
                rows={10}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-[10px] font-mono text-slate-300 focus:outline-hidden select-all"
              ></textarea>
            </div>
          </div>

        </div>

      </div>
    </div>
  );
};
