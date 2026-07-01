import React, { useEffect, useState } from 'react';
import { db } from '../firebaseClient';
import { School, Student } from '../types';
import { useAuth } from '../context/AuthContext';
import { 
  School as SchoolIcon, 
  Plus, 
  Search, 
  MapPin, 
  Users, 
  CheckCircle, 
  AlertCircle,
  Hash,
  ArrowRight
} from 'lucide-react';

interface SekolahViewProps {
  schools: School[];
  refreshSchools: () => Promise<void>;
}

export const SekolahView: React.FC<SekolahViewProps> = ({ schools, refreshSchools }) => {
  const { user } = useAuth();
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  
  // Search state for superadmin table
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Form states
  const [npsn, setNpsn] = useState<string>('');
  const [nama, setNama] = useState<string>('');
  const [alamat, setAlamat] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    loadStudents();
  }, []);

  const loadStudents = async () => {
    try {
      setLoading(true);
      // Fetch all students to compute counts per school
      const data = await db.getStudents('ALL');
      setStudents(data);
    } catch (e) {
      console.error("Gagal memuat data siswa untuk perhitungan", e);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    // Basic Validation
    const cleanNpsn = npsn.trim();
    const cleanNama = nama.trim();
    const cleanAlamat = alamat.trim();

    if (!cleanNpsn || !cleanNama || !cleanAlamat) {
      setErrorMsg('Semua kolom (NPSN, Nama Sekolah, Alamat) harus diisi.');
      return;
    }

    if (cleanNpsn.toUpperCase() === 'SCH-DEFAULT' || cleanNpsn.toUpperCase() === 'ALL') {
      setErrorMsg('NPSN ini merupakan kata kunci cadangan sistem dan tidak boleh digunakan.');
      return;
    }

    // Check if school already exists locally
    if (schools.some(s => s.npsn.toUpperCase() === cleanNpsn.toUpperCase())) {
      setErrorMsg(`Sekolah dengan NPSN ${cleanNpsn} sudah terdaftar di sistem.`);
      return;
    }

    try {
      setSubmitting(true);
      const newSchool: School = {
        npsn: cleanNpsn,
        nama: cleanNama,
        alamat: cleanAlamat
      };

      await db.addSchool(newSchool);
      await refreshSchools();
      
      setSuccessMsg(`Sekolah "${cleanNama}" dengan NPSN ${cleanNpsn} berhasil ditambahkan!`);
      
      // Clear fields
      setNpsn('');
      setNama('');
      setAlamat('');
    } catch (err: any) {
      setErrorMsg(`Gagal menambahkan sekolah: ${err.message || String(err)}`);
    } finally {
      setSubmitting(false);
    }
  };

  // Get student count per school NPSN
  const getStudentCount = (schoolNpsn: string) => {
    return students.filter(s => s.npsn_sekolah === schoolNpsn).length;
  };

  const filteredSchools = schools.filter(school => {
    const q = searchQuery.toLowerCase();
    return (
      school.npsn.toLowerCase().includes(q) ||
      school.nama.toLowerCase().includes(q) ||
      school.alamat.toLowerCase().includes(q)
    );
  });

  const isSuperAdmin = user?.role === 'superadmin';

  return (
    <div className="space-y-6">
      {/* Upper Cards Layout */}
      <div className={`grid grid-cols-1 ${isSuperAdmin ? 'lg:grid-cols-3' : 'max-w-3xl mx-auto'} gap-6`}>
        
        {/* Form Card */}
        <div className={`${isSuperAdmin ? 'lg:col-span-1' : 'w-full'} bg-white border border-slate-100 rounded-2xl p-6 shadow-sm`}>
          <div className="flex items-center space-x-3 mb-5">
            <div className="p-2.5 bg-blue-50 border border-blue-100/50 rounded-xl text-blue-600">
              <SchoolIcon className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-800">Tambah Sekolah Baru</h3>
              <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Formulir Registrasi</p>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="school-npsn" className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                NPSN / Kode Sekolah
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <Hash className="w-4 h-4" />
                </div>
                <input
                  id="school-npsn"
                  type="text"
                  required
                  placeholder="Contoh: 50102030"
                  value={npsn}
                  onChange={(e) => setNpsn(e.target.value)}
                  className="w-full pl-9 pr-4 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono font-bold"
                />
              </div>
            </div>

            <div>
              <label htmlFor="school-nama" className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                Nama Sekolah
              </label>
              <input
                id="school-nama"
                type="text"
                required
                placeholder="Contoh: SMA Negeri 1 Jakarta"
                value={nama}
                onChange={(e) => setNama(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-semibold"
              />
            </div>

            <div>
              <label htmlFor="school-alamat" className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                Alamat Sekolah
              </label>
              <textarea
                id="school-alamat"
                required
                rows={3}
                placeholder="Contoh: Jl. Budi Utomo No.7, Jakarta Pusat"
                value={alamat}
                onChange={(e) => setAlamat(e.target.value)}
                className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all leading-relaxed"
              />
            </div>

            {errorMsg && (
              <div className="p-3 bg-rose-50 border border-rose-100 rounded-xl flex items-start space-x-2 text-rose-700 animate-fadeIn">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span className="text-[11px] leading-relaxed">{errorMsg}</span>
              </div>
            )}

            {successMsg && (
              <div className="p-3 bg-emerald-50 border border-emerald-100 rounded-xl flex items-start space-x-2 text-emerald-700 animate-fadeIn">
                <CheckCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span className="text-[11px] leading-relaxed">{successMsg}</span>
              </div>
            )}

            <button
              id="btn-submit-school"
              type="submit"
              disabled={submitting}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/10 active:scale-98 transition-all flex items-center justify-center space-x-2"
            >
              <Plus className="w-4 h-4" />
              <span>{submitting ? 'Menyimpan...' : 'Simpan Sekolah'}</span>
            </button>
          </form>
        </div>

        {/* Informative Side Card / Admin Helper */}
        <div className={`${isSuperAdmin ? 'lg:col-span-2' : 'w-full'} bg-gradient-to-br from-slate-900 to-slate-950 text-white rounded-2xl p-6 flex flex-col justify-between shadow-xl relative overflow-hidden`}>
          <div className="absolute inset-0 bg-radial-gradient from-blue-500/10 to-transparent pointer-events-none"></div>
          
          <div className="relative z-10 space-y-4">
            <span className="px-2.5 py-0.8 bg-blue-500/10 border border-blue-500/20 text-blue-400 font-bold font-mono uppercase tracking-widest text-[8px] rounded-md">
              Panduan Multi-Schooling
            </span>
            <h2 className="text-base font-bold text-slate-100 tracking-tight">
              Sistem Database Terdistribusi & Sinkronisasi
            </h2>
            <p className="text-xs text-slate-400 leading-relaxed max-w-xl">
              Setiap sekolah baru yang didaftarkan akan secara instan terintegrasi dengan basis data pusat (Google Cloud Firestore). 
              Sistem akan membagi hak akses secara ketat berdasarkan NPSN sekolah masing-masing untuk menjaga kerahasiaan dan privasi data siswa.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div className="bg-slate-900/50 border border-slate-800 p-3.5 rounded-xl space-y-1">
                <span className="text-[10px] font-bold text-blue-400 uppercase tracking-wider">Identifikasi NPSN</span>
                <p className="text-[10px] text-slate-400 leading-relaxed">
                  Gunakan NPSN resmi dari Kemendikbud sebagai ID Sekolah unik demi kemudahan pemetaan siswa secara nasional.
                </p>
              </div>
              <div className="bg-slate-900/50 border border-slate-800 p-3.5 rounded-xl space-y-1">
                <span className="text-[10px] font-bold text-indigo-400 uppercase tracking-wider">Keamanan & Peran</span>
                <p className="text-[10px] text-slate-400 leading-relaxed">
                  Admin sekolah hanya memiliki otorisasi penuh pada sekolahnya, sedangkan Superadmin mengawasi seluruh aktivitas.
                </p>
              </div>
            </div>
          </div>

          <div className="relative z-10 pt-6 mt-6 border-t border-slate-800 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-[11px] text-slate-400">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>Sistem dual-engine sinkronisasi aktif</span>
            </div>
            {!isSuperAdmin && (
              <span className="text-[10px] text-amber-500 font-semibold bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-md self-start sm:self-auto">
                Daftar Sekolah Terdaftar dikunci untuk Superadmin
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Centralized Registered Schools Table - Visible only to Superadmin */}
      {isSuperAdmin && (
        <div className="bg-white border border-slate-100 rounded-2xl shadow-sm overflow-hidden animate-slideUp">
          {/* Header & Search */}
          <div className="p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h3 className="text-sm font-bold text-slate-800">Daftar Sekolah Terdaftar</h3>
              <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Data Central Multi-School</p>
            </div>

            <div className="relative w-full sm:w-64">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <Search className="w-4 h-4" />
              </div>
              <input
                id="search-schools-input"
                type="text"
                placeholder="Cari sekolah, NPSN, alamat..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-1.5 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all"
              />
            </div>
          </div>

          {/* Table Container */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  <th className="px-6 py-3 text-[10px] font-bold text-slate-400 uppercase tracking-wider">NPSN / ID</th>
                  <th className="px-6 py-3 text-[10px] font-bold text-slate-400 uppercase tracking-wider">Nama Sekolah</th>
                  <th className="px-6 py-3 text-[10px] font-bold text-slate-400 uppercase tracking-wider">Alamat Lengkap</th>
                  <th className="px-6 py-3 text-[10px] font-bold text-slate-400 uppercase tracking-wider text-center">Jumlah Siswa</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr>
                    <td colSpan={4} className="px-6 py-10 text-center">
                      <div className="flex flex-col items-center justify-center space-y-2">
                        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
                        <span className="text-[11px] font-semibold text-slate-400 uppercase">Menghitung data sekolah...</span>
                      </div>
                    </td>
                  </tr>
                ) : filteredSchools.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-6 py-10 text-center text-slate-400 text-xs">
                      Tidak ada sekolah yang cocok dengan pencarian Anda.
                    </td>
                  </tr>
                ) : (
                  filteredSchools.map((school) => {
                    const studentCount = getStudentCount(school.npsn);
                    return (
                      <tr key={school.npsn} className="hover:bg-slate-50/50 transition-colors">
                        <td className="px-6 py-4">
                          <span className="font-mono font-bold text-xs text-blue-600 bg-blue-50 border border-blue-100/50 px-2 py-0.5 rounded-md">
                            {school.npsn}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <span className="font-bold text-slate-800 text-xs">{school.nama}</span>
                        </td>
                        <td className="px-6 py-4 max-w-xs md:max-w-md">
                          <div className="flex items-center text-xs text-slate-500 leading-relaxed">
                            <MapPin className="w-3.5 h-3.5 text-slate-400 mr-1.5 shrink-0" />
                            <span className="truncate" title={school.alamat}>{school.alamat}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-center">
                          <div className="inline-flex items-center space-x-1 px-3 py-1 bg-indigo-50/60 border border-indigo-100/30 rounded-full">
                            <Users className="w-3.5 h-3.5 text-indigo-500 mr-1 shrink-0" />
                            <span className="font-extrabold text-xs text-indigo-600">{studentCount}</span>
                            <span className="text-[9px] text-slate-400 font-semibold uppercase pl-0.5">Siswa</span>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Footer Info */}
          <div className="p-4 bg-slate-50 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-500 font-medium">
            <span>Menampilkan {filteredSchools.length} dari {schools.length} sekolah terdaftar</span>
            <span className="flex items-center text-slate-400">
              Total database NPSN terintegrasi <ArrowRight className="w-3.5 h-3.5 ml-1" />
            </span>
          </div>
        </div>
      )}
    </div>
  );
};
