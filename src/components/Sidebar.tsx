import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { AdminPageSetting } from '../types';
import { AppLogo } from './AppLogo';
import { 
  LayoutDashboard, 
  Users, 
  FileSpreadsheet, 
  Settings, 
  LogOut, 
  Shield, 
  Megaphone, 
  Terminal, 
  UserCheck, 
  CalendarDays, 
  Smartphone, 
  ChevronDown, 
  Sliders, 
  X, 
  CheckCircle, 
  Clock, 
  AlertOctagon,
  School,
  Database
} from 'lucide-react';

interface SidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  adminPageSettings?: AdminPageSetting[];
  isOpen?: boolean;
  onClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab, adminPageSettings, isOpen, onClose }) => {
  const { user, logout } = useAuth();

  const mainItems = [
    { id: 'dashboard', name: 'Dashboard', icon: LayoutDashboard },
    { id: 'siswa', name: 'Siswa / Anggota', icon: Users },
    { id: 'hari-libur', name: 'Hari Libur', icon: CalendarDays },
    { id: 'sekolah', name: 'Kelola Sekolah', icon: School },
  ];

  const absensiItems = [
    { id: 'absensi-semua', name: 'Semua Status', icon: FileSpreadsheet },
    { id: 'absensi-hadir', name: 'Siswa Hadir', icon: CheckCircle },
    { id: 'absensi-sakit', name: 'Siswa Sakit', icon: Clock },
    { id: 'absensi-izin', name: 'Siswa Izin', icon: UserCheck },
    { id: 'absensi-alpa', name: 'Siswa Alpa', icon: AlertOctagon },
  ];

  const systemItems = [
    { id: 'broadcast', name: 'Broadcast & Info', icon: Megaphone },
    { id: 'binding-device', name: 'Binding Device', icon: Smartphone },
    { id: 'admin-pages', name: 'Akses Role', icon: Shield },
    { id: 'user-manager', name: 'Daftar & Kelola User', icon: UserCheck },
    { id: 'sql-editor', name: 'SQL Query Editor', icon: Terminal },
    { id: 'firebase-config', name: 'Konfigurasi Firebase', icon: Database },
    { id: 'settings', name: 'Koneksi Database', icon: Settings },
  ];

  const filterItem = (item: { id: string; name: string; icon: React.ComponentType<any> }) => {
    // Menu kelola sekolah hanya untuk superadmin dan admin
    if (item.id === 'sekolah') {
      return user?.role === 'superadmin' || user?.role === 'admin';
    }

    // Hanya superadmin yang boleh mengakses menu dalam "Sistem & Pengaturan"
    if (['broadcast', 'binding-device', 'admin-pages', 'user-manager', 'sql-editor', 'settings', 'firebase-config'].includes(item.id)) {
      if (user?.role !== 'superadmin') {
        return false;
      }
    }

    if (user?.role === 'admin' || user?.role === 'guru') {
      // Cek visibilitas kustom yang diatur oleh superadmin
      if (adminPageSettings) {
        const setting = adminPageSettings.find(s => s.page_id === item.id);
        if (setting && !setting.is_visible) {
          return false;
        }
      }
    }
    return true;
  };

  const filteredMainItems = mainItems.filter(filterItem);
  const filteredSystemItems = systemItems.filter(filterItem);

  const showAbsensiMenu = filterItem({ id: 'absensi', name: 'Rekap Absensi', icon: FileSpreadsheet });
  const isAnyAbsensiActive = activeTab.startsWith('absensi');
  const [isAbsensiOpen, setIsAbsensiOpen] = useState(isAnyAbsensiActive);

  const isAnySystemActive = filteredSystemItems.some(item => item.id === activeTab);
  const [isSystemOpen, setIsSystemOpen] = useState(isAnySystemActive);

  // Automatically open the dropdown if a nested absensi tab is active
  useEffect(() => {
    if (isAnyAbsensiActive) {
      setIsAbsensiOpen(true);
    }
  }, [activeTab, isAnyAbsensiActive]);

  // Automatically open the dropdown if a nested system tab is active
  useEffect(() => {
    if (isAnySystemActive) {
      setIsSystemOpen(true);
    }
  }, [activeTab, isAnySystemActive]);

  return (
    <>
      {/* Mobile Sidebar Overlay */}
      {isOpen && (
        <div 
          onClick={onClose} 
          className="lg:hidden fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-40 transition-opacity duration-300"
        />
      )}

      <aside 
        id="sidebar-container" 
        className={`w-64 bg-white text-slate-800 flex flex-col h-screen border-r border-slate-200/60 transition-all duration-300
          lg:relative lg:z-10 fixed inset-y-0 left-0 z-50 shadow-2xl lg:shadow-[6px_0_24px_rgba(148,163,184,0.15)]
          ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
      >
        {/* Brand Logo Header */}
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center space-x-3">
            <AppLogo className="w-10 h-10 shrink-0" iconClassName="w-5 h-5" variant="color" />
            <div>
              <h1 className="text-sm font-bold tracking-tight bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
                X-Degan QR
              </h1>
              <p className="text-[9px] text-slate-400 font-semibold tracking-wider uppercase">
                Multi-School Web
              </p>
            </div>
          </div>
          
          {/* Mobile Close Button */}
          {onClose && (
            <button
              onClick={onClose}
              className="lg:hidden p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
              aria-label="Close sidebar"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

      {/* User Session Profile Card */}
      <div className="p-3.5 mx-4 my-3.5 bg-gradient-to-r from-blue-50/50 to-indigo-50/30 border border-blue-100/30 rounded-xl">
        <div className="flex items-center space-x-3">
          <div className="relative">
            <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center font-bold text-white shadow-sm text-xs uppercase">
              {user?.email.substring(0, 2)}
            </div>
            <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-500 border-2 border-white rounded-full"></div>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-slate-700 truncate">{user?.email}</p>
            <div className="flex items-center mt-0.5">
              <Shield className={`w-3 h-3 mr-1 shrink-0 ${
                user?.role === 'superadmin' 
                  ? 'text-rose-500' 
                  : user?.role === 'guru'
                    ? 'text-blue-500'
                    : 'text-indigo-500'
              }`} />
              <span className={`text-[9px] font-bold uppercase tracking-wider ${
                user?.role === 'superadmin' 
                  ? 'text-rose-600' 
                  : user?.role === 'guru'
                    ? 'text-blue-600'
                    : 'text-indigo-600'
              }`}>
                {user?.role === 'superadmin' 
                  ? 'Super Admin' 
                  : user?.role === 'guru' 
                    ? 'Guru Sekolah' 
                    : 'Admin Sekolah'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 px-3 space-y-1 py-1.5 overflow-y-auto">
        {/* Main Menu Items */}
        {filteredMainItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              id={`sidebar-tab-${item.id}`}
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`w-full flex items-center px-3 py-2 text-xs font-medium rounded-lg transition-all duration-200 group ${
                isActive 
                  ? 'bg-blue-50/60 text-blue-600 border border-blue-100/30 shadow-xs' 
                  : 'text-slate-600 hover:bg-slate-50 hover:text-blue-600 border border-transparent'
              }`}
            >
              <Icon className={`w-4 h-4 mr-2.5 transition-transform duration-200 ${
                isActive ? 'text-blue-600' : 'text-slate-400 group-hover:text-blue-500'
              }`} />
              <span>{item.name}</span>
              {isActive && (
                <div className="ml-auto w-1 h-1 bg-blue-600 rounded-full animate-pulse"></div>
              )}
            </button>
          );
        })}

        {/* Collapsible Rekap Absensi Menu */}
        {showAbsensiMenu && (
          <div className="pt-1">
            <button
              id="sidebar-group-absensi"
              onClick={() => setIsAbsensiOpen(!isAbsensiOpen)}
              className={`w-full flex items-center px-3 py-2 text-xs font-medium rounded-lg transition-all duration-200 group ${
                isAnyAbsensiActive
                  ? 'text-blue-600 bg-blue-50/40 border border-blue-100/20'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-blue-600 border border-transparent'
              }`}
            >
              <FileSpreadsheet className={`w-4 h-4 mr-2.5 transition-transform duration-200 ${
                isAnyAbsensiActive ? 'text-blue-600' : 'text-slate-400 group-hover:text-blue-500'
              }`} />
              <span>Rekap Absensi</span>
              <ChevronDown className={`w-3.5 h-3.5 ml-auto text-slate-400 transition-transform duration-200 ${
                isAbsensiOpen ? 'transform rotate-180 text-blue-600' : ''
              }`} />
            </button>

            {/* Sub-items Container */}
            {isAbsensiOpen && (
              <div className="mt-1 ml-4 pl-3.5 border-l border-slate-100 space-y-0.5">
                {absensiItems.map((item) => {
                  const Icon = item.icon;
                  const isActive = activeTab === item.id || (item.id === 'absensi-semua' && activeTab === 'absensi');
                  return (
                    <button
                      id={`sidebar-tab-${item.id}`}
                      key={item.id}
                      onClick={() => setActiveTab(item.id)}
                      className={`w-full flex items-center px-3 py-1.5 text-[11px] font-medium rounded-md transition-all duration-200 group ${
                        isActive 
                          ? 'bg-blue-50/50 text-blue-600 border border-blue-100/10 shadow-3xs' 
                          : 'text-slate-500 hover:bg-slate-50/70 hover:text-blue-600 border border-transparent'
                      }`}
                    >
                      <Icon className={`w-3.5 h-3.5 mr-2 transition-transform duration-200 ${
                        isActive ? 'text-blue-600' : 'text-slate-400 group-hover:text-blue-500'
                      }`} />
                      <span className="truncate">{item.name}</span>
                      {isActive && (
                        <div className="ml-auto w-1 h-1 bg-blue-600 rounded-full"></div>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Collapsible System & Admin Menu */}
        {filteredSystemItems.length > 0 && (
          <div className="pt-1">
            <button
              id="sidebar-group-system"
              onClick={() => setIsSystemOpen(!isSystemOpen)}
              className={`w-full flex items-center px-3 py-2 text-xs font-medium rounded-lg transition-all duration-200 group ${
                isAnySystemActive
                  ? 'text-blue-600 bg-blue-50/40 border border-blue-100/20'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-blue-600 border border-transparent'
              }`}
            >
              <Sliders className={`w-4 h-4 mr-2.5 transition-transform duration-200 ${
                isAnySystemActive ? 'text-blue-600' : 'text-slate-400 group-hover:text-blue-500'
              }`} />
              <span>Sistem & Pengaturan</span>
              <ChevronDown className={`w-3.5 h-3.5 ml-auto text-slate-400 transition-transform duration-200 ${
                isSystemOpen ? 'transform rotate-180 text-blue-600' : ''
              }`} />
            </button>

            {/* Sub-items Container */}
            {isSystemOpen && (
              <div className="mt-1 ml-4 pl-3.5 border-l border-slate-100 space-y-0.5">
                {filteredSystemItems.map((item) => {
                  const Icon = item.icon;
                  const isActive = activeTab === item.id;
                  return (
                    <button
                      id={`sidebar-tab-${item.id}`}
                      key={item.id}
                      onClick={() => setActiveTab(item.id)}
                      className={`w-full flex items-center px-3 py-1.5 text-[11px] font-medium rounded-md transition-all duration-200 group ${
                        isActive 
                          ? 'bg-blue-50/50 text-blue-600 border border-blue-100/10 shadow-3xs' 
                          : 'text-slate-500 hover:bg-slate-50/70 hover:text-blue-600 border border-transparent'
                      }`}
                    >
                      <Icon className={`w-3.5 h-3.5 mr-2 transition-transform duration-200 ${
                        isActive ? 'text-blue-600' : 'text-slate-400 group-hover:text-blue-500'
                      }`} />
                      <span className="truncate">{item.name}</span>
                      {isActive && (
                        <div className="ml-auto w-1 h-1 bg-blue-600 rounded-full"></div>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </nav>

      {/* Logout Action */}
      <div className="p-3 border-t border-slate-100 bg-slate-50/30">
        <button
          id="btn-logout"
          onClick={logout}
          className="w-full flex items-center px-3.5 py-2 text-xs font-medium text-slate-500 hover:text-rose-600 hover:bg-rose-50 border border-transparent hover:border-rose-100/40 rounded-lg transition-all duration-200 group"
        >
          <LogOut className="w-4 h-4 mr-2.5 text-slate-400 group-hover:text-rose-500 transition-transform group-hover:-translate-x-0.5" />
          <span>Keluar Aplikasi</span>
        </button>
      </div>
    </aside>
  </>
);
};
