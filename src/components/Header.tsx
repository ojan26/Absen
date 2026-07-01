import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { db, subscribeToDbError, DbErrorInfo } from '../firebaseClient';
import { School } from '../types';
import { School as SchoolIcon, ShieldAlert, Lock, Unlock, HelpCircle, Menu } from 'lucide-react';

interface HeaderProps {
  title: string;
  selectedNpsn: string;
  setSelectedNpsn: (npsn: string) => void;
  schools: School[];
  refreshSchools: () => void;
  onToggleMenu?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ 
  title, 
  selectedNpsn, 
  setSelectedNpsn,
  schools,
  refreshSchools,
  onToggleMenu
}) => {
  const { user } = useAuth();
  const [activeSchool, setActiveSchool] = useState<School | null>(null);
  const [dbError, setDbError] = useState<DbErrorInfo | null>(null);

  useEffect(() => {
    return subscribeToDbError((err) => {
      setDbError(err);
    });
  }, []);

  // Auto load and lock npsn if school admin or guru
  useEffect(() => {
    if (user && (user.role === 'admin' || user.role === 'guru') && user.npsn_sekolah) {
      setSelectedNpsn(user.npsn_sekolah);
    }
  }, [user, setSelectedNpsn]);

  useEffect(() => {
    const found = schools.find(s => s.npsn === selectedNpsn);
    setActiveSchool(found || null);
  }, [selectedNpsn, schools]);

  const handleNpsnChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedNpsn(e.target.value);
  };

  const isDefaultSchool = selectedNpsn === 'SCH-DEFAULT';
  const isAdminLocked = user?.role === 'admin' || user?.role === 'guru';

  return (
    <header id="app-header" className="bg-white border-b border-slate-100 flex flex-col z-20 sticky top-0 shadow-3xs">
      {/* Top Bar with Title and School Filter */}
      <div className="px-4 sm:px-6 lg:px-8 py-3.5 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div className="flex items-center space-x-3">
          {onToggleMenu && (
            <button
              onClick={onToggleMenu}
              className="lg:hidden p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-blue-600 hover:bg-blue-50 transition-colors focus:outline-hidden focus:ring-2 focus:ring-blue-500/10"
              aria-label="Toggle navigation menu"
            >
              <Menu className="w-5.5 h-5.5" />
            </button>
          )}
          <h1 className="text-sm font-bold text-slate-800 md:text-base tracking-tight">{title}</h1>
        </div>

        {/* Global School Filter Dropdown & DB Status */}
        <div className="flex flex-wrap items-center gap-2.5 self-start md:self-auto">
          {/* Database Connection Status */}
          <div className="flex items-center space-x-2 bg-slate-50/80 p-1.5 px-3 rounded-xl border border-slate-100">
            <div className="relative flex h-2 w-2">
              <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${dbError ? 'bg-amber-400' : 'bg-emerald-400'}`}></span>
              <span className={`relative inline-flex rounded-full h-2 w-2 ${dbError ? 'bg-amber-500' : 'bg-emerald-500'}`}></span>
            </div>
            <div>
              <span className="block text-[8px] font-bold text-slate-400 uppercase tracking-wider">
                Database Status
              </span>
              <span className="block text-[10px] font-extrabold text-slate-700 leading-none mt-0.5">
                {dbError ? 'OFFLINE' : 'ONLINE'}
              </span>
            </div>
          </div>

          {/* Global School Filter Dropdown */}
          <div className="flex items-center space-x-2.5 bg-slate-50/80 p-1.5 px-3 rounded-xl border border-slate-100">
          <div className="p-1.5 bg-blue-50 border border-blue-100/50 rounded-lg text-blue-600">
            <SchoolIcon className="w-3.5 h-3.5" />
          </div>
          <div>
            <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider">
              NPSN Sekolah
            </label>
            <div className="flex items-center space-x-1">
              <select
                id="npsn-global-filter"
                value={selectedNpsn}
                onChange={handleNpsnChange}
                disabled={isAdminLocked}
                className={`text-xs font-semibold bg-transparent text-slate-700 focus:outline-hidden cursor-pointer pr-5 ${
                  isAdminLocked ? 'opacity-85 cursor-not-allowed' : ''
                }`}
              >
                {user?.role === 'superadmin' && (
                  <option value="ALL">
                    ✨ SEMUA SEKOLAH
                  </option>
                )}
                {schools.map((school) => (
                  <option key={school.npsn} value={school.npsn}>
                    {school.npsn} - {school.nama}
                  </option>
                ))}
              </select>
              {isAdminLocked ? (
                <Lock className="w-3 h-3 text-amber-500" title="Terkunci untuk Admin Sekolah" />
              ) : (
                <Unlock className="w-3 h-3 text-emerald-500" title="Akses Superadmin Terbuka" />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>

      {/* Warning Notice for Default / Locked State */}
      {isDefaultSchool && (
        <div id="default-npsn-warning" className="bg-amber-50/40 border-t border-b border-amber-100 px-4 sm:px-6 lg:px-8 py-2 flex items-center justify-between text-amber-800 animate-fadeIn">
          <div className="flex items-center space-x-2.5">
            <ShieldAlert className="w-4 h-4 text-amber-500 shrink-0" />
            <div className="text-[11px]">
              <span className="font-bold">NPSN Default ("SCH-DEFAULT")</span>
              <span className="text-slate-500 ml-2">Pilih NPSN sekolah valid di atas untuk membuka semua fitur edit, import, dan export.</span>
            </div>
          </div>
          <div className="hidden lg:flex items-center space-x-1 text-[10px] bg-amber-100/40 text-amber-700 border border-amber-200/50 px-2 py-0.5 rounded-md">
            <Lock className="w-3 h-3 mr-0.5" />
            <span className="font-semibold">Read-Only Mode</span>
          </div>
        </div>
      )}

      {/* Firebase Firestore Error Alert */}
      {dbError && (
        <div id="firebase-db-error-alert" className="bg-rose-50/40 border-t border-b border-rose-100 px-4 sm:px-6 lg:px-8 py-2.5 flex flex-col md:flex-row md:items-center md:justify-between gap-3 text-rose-800 animate-fadeIn">
          <div className="flex items-start space-x-2.5">
            <ShieldAlert className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
            <div className="text-[11px]">
              <span className="font-bold text-rose-950 mr-1.5">
                Koneksi Database Firestore Bermasalah:
              </span>
              <span className="font-mono bg-rose-50 border border-rose-200/50 px-1 py-0.5 rounded-sm text-rose-700 font-semibold">
                {dbError.message}
              </span>
              <p className="text-slate-500 mt-1 max-w-3xl">
                Sistem menggunakan <strong className="text-rose-700 font-semibold">Local Storage DB (Offline Fallback)</strong> agar aplikasi tetap berjalan lancar tanpa hambatan.
              </p>
            </div>
          </div>
          <div className="shrink-0 self-start md:self-center">
            <span className="text-[9px] bg-rose-50 text-rose-700 border border-rose-100 font-bold px-2 py-0.5 rounded-md">
              OFFLINE ENGINE
            </span>
          </div>
        </div>
      )}

      {/* School Detail Info Strip */}
      {!isDefaultSchool && activeSchool && (
        <div id="school-info-strip" className="bg-slate-50/30 border-t border-slate-100/50 px-4 sm:px-6 lg:px-8 py-1.5 flex items-center space-x-4 text-[11px] text-slate-500">
          <div className="flex items-center">
            <span className="font-medium text-slate-400 mr-1">Alamat:</span>
            <span className="font-semibold text-slate-600">{activeSchool.alamat}</span>
          </div>
          <div className="hidden sm:block text-slate-200">|</div>
          <div className="hidden sm:flex items-center">
            <span className="font-medium text-slate-400 mr-1">NPSN:</span>
            <span className="bg-slate-100 text-slate-700 font-mono font-bold px-1.5 py-0.2 rounded-sm">{activeSchool.npsn}</span>
          </div>
        </div>
      )}

      {/* Global Consolidated Info Strip for Superadmin */}
      {selectedNpsn === 'ALL' && (
        <div id="global-info-strip" className="bg-blue-50/30 border-t border-blue-100/50 px-4 sm:px-6 lg:px-8 py-1.5 flex items-center space-x-3 text-[11px] text-blue-700">
          <div className="flex items-center">
            <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse mr-1.5"></span>
            <span className="font-medium text-blue-500 mr-1">Mode:</span>
            <span className="font-semibold text-blue-800">Semua Sekolah Terpilih (Data Gabungan)</span>
          </div>
        </div>
      )}
    </header>
  );
};
