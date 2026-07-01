import React, { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/Sidebar';
import { Header } from './components/Header';
import { LoginView } from './components/LoginView';
import { DashboardView } from './components/DashboardView';
import { SiswaView } from './components/SiswaView';
import { AbsensiView } from './components/AbsensiView';
import { SettingsView } from './components/SettingsView';
import { BroadcastView } from './components/BroadcastView';
import { SqlEditorView } from './components/SqlEditorView';
import { UserManagerView } from './components/UserManagerView';
import { db } from './firebaseClient';
import { School, AdminPageSetting, Broadcast } from './types';
import { School as SchoolIcon, Megaphone, ExternalLink, X } from 'lucide-react';
import { AdminPagesView } from './components/AdminPagesView';
import { SekolahView } from './components/SekolahView';
import { HariLiburView } from './components/HariLiburView';
import { BindingDeviceView } from './components/BindingDeviceView';
import { FirebaseConfigView } from './components/FirebaseConfigView';
import { motion, AnimatePresence } from 'motion/react';

const AccessDeniedView: React.FC<{ onBackToDashboard: () => void }> = ({ onBackToDashboard }) => {
  return (
    <div className="flex flex-col items-center justify-center py-20 px-4 text-center animate-fadeIn">
      <div className="bg-rose-50 border border-rose-100 p-4.5 rounded-2xl text-rose-500 mb-5 relative">
        <div className="absolute -inset-0.5 bg-rose-500/10 rounded-2xl blur-xs animate-pulse"></div>
        <svg className="w-10 h-10 relative" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
      </div>
      <h3 className="text-base font-bold text-slate-800">Akses Ditolak / Halaman Terkunci</h3>
      <p className="text-slate-500 text-xs mt-2 max-w-md leading-relaxed">
        Maaf, halaman ini membutuhkan kredensial tingkat tinggi (<span className="text-rose-500 font-bold font-mono">superadmin</span>). 
        Akun Anda saat ini dikonfigurasi sebagai <span className="text-amber-500 font-bold font-mono">admin sekolah</span> dan dibatasi dari memodifikasi struktur database atau akun pengguna lain.
      </p>
      <button
        onClick={onBackToDashboard}
        className="mt-6 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/10 active:scale-95 transition-all"
      >
        Kembali ke Dashboard Utama
      </button>
    </div>
  );
};

const MainAppLayout: React.FC = () => {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [selectedNpsn, setSelectedNpsn] = useState<string>('SCH-DEFAULT');
  const [schools, setSchools] = useState<School[]>([]);
  const [adminPageSettings, setAdminPageSettings] = useState<AdminPageSetting[]>([]);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState<boolean>(false);
  const [activeBroadcasts, setActiveBroadcasts] = useState<Broadcast[]>([]);
  const [dismissedIds, setDismissedIds] = useState<number[]>([]);

  const loadAdminPageSettings = async () => {
    try {
      const data = await db.getAdminPageSettings();
      setAdminPageSettings(data);
    } catch (e) {
      console.error("Gagal memuat pengaturan halaman admin", e);
    }
  };

  const loadBroadcasts = async () => {
    try {
      const data = await db.getBroadcasts();
      setActiveBroadcasts((data || []).filter(b => b.is_active));
    } catch (e) {
      console.error("Gagal memuat pengumuman", e);
    }
  };

  const loadSchools = async () => {
    try {
      const data = await db.getSchools();
      setSchools(data);
      
      // Select the first valid school if user is superadmin and selected is default, or lock to admin/guru's school
      if (user) {
        if ((user.role === 'admin' || user.role === 'guru') && user.npsn_sekolah) {
          setSelectedNpsn(user.npsn_sekolah);
        } else if (user.role === 'superadmin' && selectedNpsn === 'SCH-DEFAULT' && data.length > 1) {
          // Keep SCH-DEFAULT or swap to first real school
          const realSchool = data.find(s => s.npsn !== 'SCH-DEFAULT');
          if (realSchool) {
            setSelectedNpsn(realSchool.npsn);
          }
        }
      }
    } catch (e) {
      console.error("Gagal memuat daftar sekolah", e);
    }
  };

  useEffect(() => {
    if (user) {
      loadSchools();
      loadAdminPageSettings();
      loadBroadcasts();
    }
  }, [user]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem('dismissed_broadcasts');
      if (stored) {
        setDismissedIds(JSON.parse(stored));
      }
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    
    // Poll for new broadcasts every 20 seconds
    const interval = setInterval(() => {
      loadBroadcasts();
    }, 20000);
    
    return () => clearInterval(interval);
  }, [user]);

  const dismissBroadcast = (id: number) => {
    const updated = [...dismissedIds, id];
    setDismissedIds(updated);
    localStorage.setItem('dismissed_broadcasts', JSON.stringify(updated));
  };

  const isPageAllowed = (tabId: string) => {
    if (!user) return false;
    if (user.role === 'superadmin') return true;
    
    // Hanya superadmin yang boleh mengakses menu dalam "Sistem & Pengaturan"
    if (['broadcast', 'binding-device', 'admin-pages', 'user-manager', 'sql-editor', 'settings'].includes(tabId)) {
      return false;
    }
    
    const checkTabId = tabId.startsWith('absensi') ? 'absensi' : tabId;
    const setting = adminPageSettings.find(s => s.page_id === checkTabId);
    if (setting) {
      return setting.is_visible;
    }
    return true;
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-white flex flex-col items-center justify-center text-slate-800">
        <div className="p-4 bg-blue-50 border border-blue-100/55 rounded-2xl text-blue-600 mb-4 animate-pulse">
          <SchoolIcon className="w-8 h-8" />
        </div>
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        <span className="mt-4 text-xs font-bold text-slate-400 uppercase tracking-wider">
          Menyiapkan Lingkungan Aplikasi...
        </span>
      </div>
    );
  }

  if (!user) {
    return <LoginView />;
  }

  const getPageTitle = () => {
    switch (activeTab) {
      case 'sekolah':
        return 'Kelola & Tambah Sekolah';
      case 'dashboard':
        return 'Ringkasan';
      case 'siswa':
        return 'Data Anggota Siswa';
      case 'absensi':
      case 'absensi-semua':
      case 'absensi-hadir':
      case 'absensi-sakit':
      case 'absensi-izin':
      case 'absensi-alpa':
        return 'Rekap Presensi Harian';
      case 'sql-editor':
        return 'SQL Query Terminal';
      case 'user-manager':
        return 'Kelola User & Hak Akses';
      case 'settings':
        return 'Integrasi & Database';
      case 'firebase-config':
        return 'Konfigurasi Firebase';
      case 'broadcast':
        return 'Broadcast & Pengumuman';
      case 'hari-libur':
        return 'Konfigurasi Hari Libur';
      case 'binding-device':
        return 'Binding Device Terintegrasi';
      case 'admin-pages':
        return 'Akses Role';
      default:
        return 'Dashboard';
    }
  };

  const nonDismissedBroadcasts = activeBroadcasts.filter(b => !dismissedIds.includes(b.id));
  const currentBroadcast = nonDismissedBroadcasts[0];

  return (
    <div className="min-h-screen bg-slate-50 flex overflow-hidden font-sans relative">
      {/* Sidebar - left */}
      <Sidebar 
        activeTab={activeTab} 
        setActiveTab={(tab) => {
          setActiveTab(tab);
          setIsMobileMenuOpen(false);
        }} 
        adminPageSettings={adminPageSettings} 
        isOpen={isMobileMenuOpen}
        onClose={() => setIsMobileMenuOpen(false)}
      />
 
      {/* Main Panel Content - right */}
      <div className="flex-1 flex flex-col h-screen overflow-y-auto bg-slate-50">
        {/* Header containing global school filter and default warning */}
        <Header 
          title={getPageTitle()} 
          selectedNpsn={selectedNpsn} 
          setSelectedNpsn={setSelectedNpsn}
          schools={schools}
          refreshSchools={loadSchools}
          onToggleMenu={() => setIsMobileMenuOpen(true)}
        />
 
        {/* Dynamic Inner Tab Views */}
        <main className="flex-grow p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto pb-16">
          {activeTab === 'dashboard' && (
            isPageAllowed('dashboard') ? (
              <DashboardView selectedNpsn={selectedNpsn} schools={schools} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'siswa' && (
            isPageAllowed('siswa') ? (
              <SiswaView selectedNpsn={selectedNpsn} schools={schools} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab.startsWith('absensi') && (
            isPageAllowed('absensi') ? (
              <AbsensiView 
                selectedNpsn={selectedNpsn} 
                schools={schools} 
                statusFilter={
                  activeTab === 'absensi-hadir' ? 'Hadir' :
                  activeTab === 'absensi-sakit' ? 'Sakit' :
                  activeTab === 'absensi-izin' ? 'Izin' :
                  activeTab === 'absensi-alpa' ? 'Alpa' : 'Semua'
                }
              />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'sql-editor' && (
            user?.role === 'superadmin' ? (
              <SqlEditorView />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'user-manager' && (
            user?.role === 'superadmin' ? (
              <UserManagerView schools={schools} refreshSchools={loadSchools} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'settings' && (
            user?.role === 'superadmin' ? (
              <SettingsView 
                schools={schools} 
                refreshSchools={loadSchools} 
                selectedNpsn={selectedNpsn}
                setSelectedNpsn={setSelectedNpsn}
              />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'firebase-config' && (
            user?.role === 'superadmin' ? (
              <FirebaseConfigView />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'broadcast' && (
            isPageAllowed('broadcast') ? (
              <BroadcastView />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'sekolah' && (
            user?.role === 'superadmin' || user?.role === 'admin' ? (
              <SekolahView schools={schools} refreshSchools={loadSchools} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'hari-libur' && (
            isPageAllowed('hari-libur') ? (
              <HariLiburView selectedNpsn={selectedNpsn} schools={schools} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'binding-device' && (
            user?.role === 'superadmin' || user?.role === 'admin' ? (
              <BindingDeviceView />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
          {activeTab === 'admin-pages' && (
            user?.role === 'superadmin' ? (
              <AdminPagesView onSettingsUpdated={loadAdminPageSettings} />
            ) : (
              <AccessDeniedView onBackToDashboard={() => setActiveTab('dashboard')} />
            )
          )}
        </main>
      </div>

      {/* Floating Global Announcement Slide-up Popup */}
      <AnimatePresence>
        {currentBroadcast && (
          <motion.div
            initial={{ opacity: 0, y: 100, x: 100, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, x: 0, scale: 1 }}
            exit={{ opacity: 0, y: 50, x: 50, scale: 0.95 }}
            transition={{ type: "spring", stiffness: 280, damping: 28 }}
            className="fixed bottom-6 right-6 z-50 w-80 sm:w-96 bg-white rounded-2xl border border-indigo-100 shadow-2xl overflow-hidden p-5 flex flex-col justify-between"
          >
            <div>
              <div className="flex items-center justify-between mb-3 border-b border-slate-100 pb-2.5">
                <div className="flex items-center space-x-2">
                  <div className={`p-1.5 rounded-lg ${
                    currentBroadcast.type === 'ALERT' ? 'bg-rose-50 text-rose-600' :
                    currentBroadcast.type === 'INSTRUCTION' ? 'bg-blue-50 text-blue-600' :
                    'bg-indigo-50 text-indigo-600'
                  }`}>
                    <Megaphone className="w-4 h-4 animate-bounce" />
                  </div>
                  <span className={`px-2 py-0.5 rounded text-[8px] font-bold tracking-wider uppercase ${
                    currentBroadcast.type === 'ALERT' ? 'bg-rose-50 text-rose-600' :
                    currentBroadcast.type === 'INSTRUCTION' ? 'bg-blue-50 text-blue-600' :
                    'bg-sky-50 text-sky-600'
                  }`}>
                    {currentBroadcast.type || 'INFO'}
                  </span>
                </div>
                <button 
                  onClick={() => dismissBroadcast(currentBroadcast.id)}
                  className="p-1 hover:bg-slate-100 rounded-lg text-slate-400 hover:text-slate-600 transition cursor-pointer"
                  title="Tutup Pengumuman"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h5 className="text-xs font-bold text-slate-800 line-clamp-1">{currentBroadcast.title}</h5>
                  <span className="text-[9px] text-slate-400 font-medium">
                    {new Date(currentBroadcast.updated_id).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' })}
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 leading-relaxed line-clamp-4 whitespace-pre-wrap">
                  {currentBroadcast.message}
                </p>
              </div>
            </div>

            <div className="mt-4 pt-2.5 border-t border-slate-50 flex items-center justify-between">
              {nonDismissedBroadcasts.length > 1 ? (
                <span className="text-[9px] text-indigo-600 font-bold bg-indigo-50 px-2 py-0.5 rounded-full">
                  +{nonDismissedBroadcasts.length - 1} Pengumuman Lain
                </span>
              ) : (
                <span className="text-[9px] text-slate-400 font-medium">Pengumuman Terkini</span>
              )}
              {currentBroadcast.drive_link && (
                <a 
                  href={currentBroadcast.drive_link} 
                  target="_blank" 
                  rel="noopener noreferrer" 
                  className="inline-flex items-center text-[10px] text-blue-600 hover:underline font-bold"
                >
                  <ExternalLink className="w-3.5 h-3.5 mr-1" />
                  Buka Lampiran
                </a>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <MainAppLayout />
    </AuthProvider>
  );
}
