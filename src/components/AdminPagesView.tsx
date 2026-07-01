import React, { useState, useEffect } from 'react';
import { db } from '../firebaseClient';
import { AdminPageSetting } from '../types';
import { 
  LayoutDashboard, 
  Users, 
  FileSpreadsheet, 
  Megaphone, 
  Save, 
  Shield, 
  Eye, 
  EyeOff, 
  Check, 
  CheckSquare, 
  Square, 
  Info,
  RefreshCw,
  AlertCircle,
  Smartphone,
  CalendarDays
} from 'lucide-react';

export const AdminPagesView: React.FC<{ onSettingsUpdated?: () => void }> = ({ onSettingsUpdated }) => {
  const [settings, setSettings] = useState<AdminPageSetting[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string>('');

  const loadSettings = async () => {
    setLoading(true);
    try {
      const data = await db.getAdminPageSettings();
      setSettings(data);
    } catch (err: any) {
      setErrorMsg(`Gagal memuat konfigurasi halaman: ${err.message || String(err)}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const handleToggleVisibility = (pageId: string) => {
    setSettings(prev => prev.map(item => {
      if (item.page_id === pageId) {
        return { ...item, is_visible: !item.is_visible };
      }
      return item;
    }));
  };

  const handleSaveSettings = async () => {
    setSaving(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      const success = await db.updateAdminPageSettings(settings);
      if (success) {
        setSuccessMsg('Konfigurasi hak akses halaman berhasil disimpan dan diterapkan!');
        if (onSettingsUpdated) {
          onSettingsUpdated();
        }
        setTimeout(() => setSuccessMsg(''), 4000);
      } else {
        setErrorMsg('Gagal memperbarui konfigurasi halaman.');
      }
    } catch (err: any) {
      setErrorMsg(`Gagal menyimpan ke Database: ${err.message || String(err)}`);
    } finally {
      setSaving(false);
    }
  };

  const getPageIcon = (pageId: string) => {
    switch (pageId) {
      case 'dashboard': return <LayoutDashboard className="w-5 h-5" />;
      case 'siswa': return <Users className="w-5 h-5" />;
      case 'absensi': return <FileSpreadsheet className="w-5 h-5" />;
      case 'broadcast': return <Megaphone className="w-5 h-5" />;
      case 'hari-libur': return <CalendarDays className="w-5 h-5" />;
      case 'binding-device': return <Smartphone className="w-5 h-5" />;
      default: return <LayoutDashboard className="w-5 h-5" />;
    }
  };

  const getPageDescription = (pageId: string) => {
    switch (pageId) {
      case 'dashboard': return 'Halaman ringkasan statistik kehadiran, rasio siswa hadir, jumlah alpa, dan chart interaktif.';
      case 'siswa': return 'Halaman daftar siswa, filter berdasarkan kelas, input siswa baru, edit data, dan cetak QR Code kartu siswa.';
      case 'absensi': return 'Halaman scan presensi (menggunakan web cam / scanner) dan rekapitulasi data kehadiran harian.';
      case 'broadcast': return 'Halaman pengumuman sekolah, instruksi tugas harian, dan link download dari Google Drive.';
      case 'hari-libur': return 'Halaman konfigurasi hari libur nasional atau sekolah untuk penyesuaian kalender absensi harian.';
      case 'binding-device': return 'Halaman manajemen token otentikasi hardware scanner & device mobile yang terpasang langsung ke spreadsheet.';
      default: return '';
    }
  };

  return (
    <div className="space-y-8 animate-fadeIn">
      {/* Page Header */}
      <div>
        <h3 className="text-lg font-bold text-slate-800">Akses Role</h3>
        <p className="text-xs text-slate-500 mt-0.5">
          Atur visibilitas halaman/modul yang dapat diakses oleh user dengan tingkat hak akses <strong>Admin Sekolah</strong>.
        </p>
      </div>

      {successMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl flex items-center space-x-3 text-xs font-semibold animate-fadeIn">
          <Check className="w-5 h-5 text-emerald-500 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-xl flex items-center space-x-3 text-xs font-semibold animate-fadeIn">
          <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        
        {/* Left: Checkbox Settings Grid (7 Columns) */}
        <div className="lg:col-span-7 space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-6">
              <div className="flex items-center space-x-2.5">
                <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                  <Shield className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="font-bold text-slate-800 text-sm">Daftar Modul Aplikasi</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Tandai modul untuk menampilkan di menu Admin</p>
                </div>
              </div>
              <button 
                onClick={loadSettings}
                disabled={loading}
                className="p-1.5 hover:bg-slate-100 text-slate-400 hover:text-slate-600 rounded-lg transition-colors"
                title="Muat Ulang Data"
              >
                <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              </button>
            </div>

            {loading ? (
              <div className="py-12 flex flex-col items-center justify-center text-slate-400 text-xs">
                <RefreshCw className="w-8 h-8 animate-spin text-indigo-500 mb-3" />
                <span>Memuat rincian halaman...</span>
              </div>
            ) : (
              <div className="space-y-4">
                {settings.map((item) => (
                  <div 
                    key={item.page_id}
                    onClick={() => handleToggleVisibility(item.page_id)}
                    className={`p-4 rounded-xl border transition-all cursor-pointer select-none flex items-start space-x-4 ${
                      item.is_visible 
                        ? 'bg-indigo-50/30 border-indigo-200 hover:bg-indigo-50/50' 
                        : 'bg-white border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    {/* Checkbox Icon */}
                    <div className="mt-0.5 shrink-0">
                      {item.is_visible ? (
                        <div className="text-indigo-600">
                          <CheckSquare className="w-5 h-5 fill-indigo-50" />
                        </div>
                      ) : (
                        <div className="text-slate-400">
                          <Square className="w-5 h-5" />
                        </div>
                      )}
                    </div>

                    {/* Page Icon */}
                    <div className={`p-2.5 rounded-lg shrink-0 ${
                      item.is_visible ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-400'
                    }`}>
                      {getPageIcon(item.page_id)}
                    </div>

                    {/* Title and Description */}
                    <div className="flex-grow space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-slate-800 text-xs">{item.page_name}</span>
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[9px] font-bold ${
                          item.is_visible 
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-100' 
                            : 'bg-slate-100 text-slate-500 border border-slate-200'
                        }`}>
                          {item.is_visible ? 'Ditampilkan' : 'Disembunyikan'}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-400 leading-relaxed">
                        {getPageDescription(item.page_id)}
                      </p>
                    </div>
                  </div>
                ))}

                {/* Info Note */}
                <div className="flex items-start space-x-3 bg-amber-50/50 border border-amber-100 p-4 rounded-xl mt-6 text-[11px] text-amber-700 leading-relaxed">
                  <Info className="w-4 h-4 shrink-0 text-amber-500 mt-0.5" />
                  <p>
                    Perubahan ini akan langsung berdampak pada menu navigasi (sidebar) yang dapat dilihat oleh Admin Sekolah saat login kembali atau memuat ulang halaman.
                  </p>
                </div>

                {/* Save Button */}
                <div className="pt-4 border-t border-slate-100 flex justify-end">
                  <button
                    onClick={handleSaveSettings}
                    disabled={saving}
                    className="flex items-center space-x-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-400 text-white font-bold text-xs rounded-xl shadow-md shadow-indigo-600/10 active:scale-95 transition-all"
                  >
                    {saving ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Menyimpan...</span>
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" />
                        <span>Simpan Pengaturan</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right: Live Visual Preview of Admin Sidebar (5 Columns) */}
        <div className="lg:col-span-5 space-y-6">
          <div className="bg-slate-900 text-slate-100 rounded-2xl border border-slate-800 p-6 shadow-xl sticky top-6">
            <div className="flex items-center space-x-2 pb-4 border-b border-slate-800 mb-5">
              <div className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse"></div>
              <div className="w-2.5 h-2.5 rounded-full bg-amber-500"></div>
              <div className="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
              <span className="text-[10px] text-slate-500 uppercase tracking-widest font-bold ml-2">Live Preview (Role: Admin)</span>
            </div>

            <div className="space-y-4">
              <div className="flex items-center space-x-2 px-2 py-3 bg-slate-950/40 rounded-xl border border-slate-800/60 mb-2">
                <div className="p-1.5 bg-sky-500/10 border border-sky-500/20 rounded-lg text-sky-400 shrink-0">
                  <Shield className="w-4 h-4" />
                </div>
                <div className="truncate">
                  <h5 className="text-xs font-bold text-slate-200">SMKN 1 Jakarta</h5>
                  <p className="text-[9px] text-slate-500 truncate mt-0.5">NPSN: 50102030</p>
                </div>
              </div>

              <div className="text-[10px] uppercase font-bold text-slate-500 tracking-wider px-2">
                Menu Utama Admin
              </div>

              <div className="space-y-1">
                {settings.length === 0 ? (
                  <div className="py-8 text-center text-slate-500 text-xs">
                    Memuat menu...
                  </div>
                ) : (
                  settings.map((item) => {
                    const isVisible = item.is_visible;
                    return (
                      <div 
                        key={item.page_id}
                        className={`flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                          isVisible 
                            ? 'bg-slate-800/40 text-slate-200 border border-transparent hover:bg-slate-800/60' 
                            : 'text-slate-600 border border-dashed border-slate-800 bg-slate-950/10 line-through select-none cursor-not-allowed opacity-40'
                        }`}
                      >
                        <div className="flex items-center space-x-3">
                          <span className={isVisible ? 'text-sky-400' : 'text-slate-700'}>
                            {getPageIcon(item.page_id)}
                          </span>
                          <span>{item.page_name}</span>
                        </div>
                        
                        <span>
                          {isVisible ? (
                            <Eye className="w-3.5 h-3.5 text-emerald-500" />
                          ) : (
                            <EyeOff className="w-3.5 h-3.5 text-rose-500" />
                          )}
                        </span>
                      </div>
                    );
                  })
                )}
              </div>

              <div className="pt-4 border-t border-slate-800 text-center">
                <p className="text-[10px] text-slate-500 leading-relaxed italic">
                  *Halaman yang dicoret di atas tidak akan muncul pada menu navigasi akun Admin Sekolah.
                </p>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};
