import React, { useState, useEffect } from 'react';
import { db } from '../firebaseClient';
import { LoginTableRecord, School, UserRole } from '../types';
import { 
  UserPlus, 
  Users, 
  ShieldCheck, 
  Key, 
  Trash2, 
  Check, 
  AlertCircle, 
  RefreshCw, 
  School as SchoolIcon, 
  Search,
  Lock,
  Edit2,
  X,
  HelpCircle,
  Briefcase
} from 'lucide-react';

interface UserManagerViewProps {
  schools: School[];
  refreshSchools: () => Promise<void>;
}

export const UserManagerView: React.FC<UserManagerViewProps> = ({ schools, refreshSchools }) => {
  const [logins, setLogins] = useState<LoginTableRecord[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [successMsg, setSuccessMsg] = useState<string>('');

  // Form State
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [role, setRole] = useState<UserRole>('admin');
  const [npsnSekolah, setNpsnSekolah] = useState<string>('');
  const [kelasTugas, setKelasTugas] = useState<string>('');

  // Edit State
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editPassword, setEditPassword] = useState<string>('');
  const [editRole, setEditRole] = useState<UserRole>('admin');
  const [editNpsn, setEditNpsn] = useState<string>('');
  const [editKelasTugas, setEditKelasTugas] = useState<string>('');

  // Filter State
  const [searchTerm, setSearchTerm] = useState<string>('');

  const loadLogins = async () => {
    setLoading(true);
    try {
      const data = await db.getLogins();
      setLogins(data);
    } catch (err: any) {
      setErrorMsg('Gagal memuat daftar user login dari database.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadLogins();
    if (schools.length === 0) {
      refreshSchools();
    }
  }, []);

  // Autofill school if none selected when schools load
  useEffect(() => {
    if (schools.length > 0 && !npsnSekolah) {
      const validSchool = schools.find(s => s.npsn !== 'SCH-DEFAULT') || schools[0];
      setNpsnSekolah(validSchool?.npsn || '');
    }
  }, [schools]);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    if (!email.trim() || !password.trim()) {
      setErrorMsg('Email dan password tidak boleh kosong.');
      return;
    }

    if (!email.includes('@')) {
      setErrorMsg('Format email tidak valid.');
      return;
    }

    // Check if email already registered
    const exists = logins.some(l => l.email.toLowerCase() === email.toLowerCase().trim());
    if (exists) {
      setErrorMsg(`User dengan email "${email}" sudah terdaftar dalam tabel login.`);
      return;
    }

    setLoading(true);
    try {
      const newRecord = {
        email: email.trim().toLowerCase(),
        password: password,
        role: role,
        npsn_sekolah: role === 'superadmin' ? null : npsnSekolah,
        kelas_tugas: role === 'guru' ? (kelasTugas.trim() || null) : null
      };

      await db.addLoginRecord(newRecord);
      setSuccessMsg(`User baru "${email}" berhasil didaftarkan ke tabel database 'login'!`);
      
      // Reset form
      setEmail('');
      setPassword('');
      
      // Reload logins
      await loadLogins();
    } catch (err: any) {
      setErrorMsg(err.message || 'Gagal mendaftarkan user baru.');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number, userEmail: string) => {
    if (!window.confirm(`Apakah Anda yakin ingin menghapus user login "${userEmail}"?`)) {
      return;
    }

    setErrorMsg('');
    setSuccessMsg('');
    setLoading(true);

    try {
      const success = await db.deleteLoginRecord(id);
      if (success) {
        setSuccessMsg(`User "${userEmail}" berhasil dihapus.`);
        await loadLogins();
      } else {
        setErrorMsg('Gagal menghapus user dari database.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Terjadi kesalahan saat menghapus user.');
    } finally {
      setLoading(false);
    }
  };

  const startEdit = (record: LoginTableRecord) => {
    setEditingId(record.id);
    setEditPassword(record.password);
    setEditRole(record.role);
    setEditNpsn(record.npsn_sekolah || '');
    setEditKelasTugas(record.kelas_tugas || '');
  };

  const handleUpdate = async (id: number) => {
    setErrorMsg('');
    setSuccessMsg('');

    if (!editPassword.trim()) {
      setErrorMsg('Password baru tidak boleh kosong.');
      return;
    }

    setLoading(true);
    try {
      const updatedFields = {
        password: editPassword,
        role: editRole,
        npsn_sekolah: editRole === 'superadmin' ? null : editNpsn,
        kelas_tugas: editRole === 'guru' ? (editKelasTugas.trim() || null) : null
      };

      const success = await db.updateLoginRecord(id, updatedFields);
      if (success) {
        setSuccessMsg('Kredensial login berhasil diperbarui!');
        setEditingId(null);
        await loadLogins();
      } else {
        setErrorMsg('Gagal memperbarui kredensial.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Terjadi kesalahan saat memperbarui.');
    } finally {
      setLoading(false);
    }
  };

  // Filter logins by search term
  const filteredLogins = logins.filter(l => 
    l.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (l.npsn_sekolah && l.npsn_sekolah.includes(searchTerm)) ||
    l.role.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-8 animate-fadeIn">
      {/* Top Title */}
      <div>
        <h3 className="text-lg font-bold text-slate-800 flex items-center">
          <Users className="w-5 h-5 mr-2 text-indigo-600 shrink-0" />
          Registrasi & Manajer Akun Pengguna (Tabel Login)
        </h3>
        <p className="text-xs text-slate-500 mt-0.5">
          Kelola hak akses administrator sekolah dan superadmin langsung ke tabel database relasional <code className="font-mono text-indigo-600 font-bold bg-indigo-50 px-1 rounded">login</code>.
        </p>
      </div>

      {/* Role Explanation Bento Section */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        
        {/* Superadmin Card Explanation */}
        <div className="bg-gradient-to-br from-slate-900 via-slate-950 to-indigo-950 p-6 rounded-2xl border border-slate-800 text-slate-300 relative overflow-hidden group hover:shadow-lg transition">
          <div className="absolute top-0 right-0 p-8 opacity-10 text-white shrink-0 group-hover:scale-110 transition duration-300 pointer-events-none">
            <ShieldCheck className="w-28 h-28" />
          </div>
          <div className="flex items-center space-x-2.5 text-rose-400">
            <div className="p-1.5 bg-rose-500/10 border border-rose-500/20 rounded-lg text-rose-400">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <h4 className="text-xs font-bold uppercase tracking-widest">Hak Akses: superadmin</h4>
          </div>
          <h5 className="text-base font-bold text-white mt-3">Super Administrator Sistem</h5>
          <p className="text-xs text-slate-400 mt-1.5 leading-relaxed">
            Pemilik akses penuh global. Dapat melakukan pengaturan database, memodifikasi koneksi Database, mengelola semua sekolah mitra, menulis pengumuman global, dan menjalankan kueri SQL mentah di Query Terminal.
          </p>
          <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-500 font-mono">
            <span>Nilai kolom <code className="text-rose-300 font-bold">npsn_sekolah</code></span>
            <span className="bg-rose-500/10 text-rose-300 font-bold px-2 py-0.5 rounded border border-rose-500/20">NULL</span>
          </div>
        </div>

        {/* Admin Card Explanation */}
        <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-md relative overflow-hidden group hover:shadow-lg transition">
          <div className="absolute top-0 right-0 p-8 opacity-5 text-indigo-600 shrink-0 group-hover:scale-110 transition duration-300 pointer-events-none">
            <SchoolIcon className="w-28 h-28" />
          </div>
          <div className="flex items-center space-x-2.5 text-indigo-600">
            <div className="p-1.5 bg-indigo-50 border border-indigo-100 rounded-lg text-indigo-600">
              <Briefcase className="w-5 h-5" />
            </div>
            <h4 className="text-xs font-bold uppercase tracking-widest">Hak Akses: admin</h4>
          </div>
          <h5 className="text-base font-bold text-slate-800 mt-3">Admin Sekolah Mitra</h5>
          <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
            Pengelola operasional terbatas pada satu sekolah. Diizinkan mendaftarkan siswa baru, melakukan generate kartu QR siswa, serta melihat log presensi absensi harian khusus untuk sekolah tempatnya ditugaskan.
          </p>
          <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-[10px] text-slate-400 font-mono">
            <span>Nilai kolom <code className="text-indigo-600 font-bold">npsn_sekolah</code></span>
            <span className="bg-indigo-50 text-indigo-600 font-bold px-2 py-0.5 rounded border border-indigo-100 uppercase">Wajib diisi NPSN</span>
          </div>
        </div>

      </div>

      {/* Main Panel grid: Form and List */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Registration Form (1 Column) */}
        <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-md h-fit space-y-5">
          <div className="flex items-center space-x-2 pb-3 border-b border-slate-100">
            <UserPlus className="w-5 h-5 text-indigo-600 shrink-0" />
            <h4 className="text-sm font-bold text-slate-800">Daftarkan User Baru</h4>
          </div>

          <form onSubmit={handleRegister} className="space-y-4">
            
            {/* Email Field */}
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Alamat Email Login
              </label>
              <div className="relative">
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-9 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:border-indigo-500 font-mono text-slate-700"
                  placeholder="admin.sman1@xdegan.com"
                  required
                />
                <span className="absolute left-3 top-2.5 text-slate-400">@</span>
              </div>
            </div>

            {/* Password Field */}
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Kata Sandi (Password)
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-9 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:border-indigo-500 font-mono text-slate-700"
                  placeholder="Isi password atau sandi..."
                  required
                />
                <span className="absolute left-3 top-2.5 text-slate-400">
                  <Key className="w-3.5 h-3.5" />
                </span>
              </div>
            </div>

            {/* Role Selection */}
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Hak Akses (Role)
              </label>
              <div className="grid grid-cols-3 gap-1.5">
                <button
                  type="button"
                  onClick={() => setRole('admin')}
                  className={`py-2 text-[10px] font-bold rounded-xl border text-center transition ${
                    role === 'admin' 
                      ? 'bg-indigo-50 border-indigo-200 text-indigo-700' 
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Admin
                </button>
                <button
                  type="button"
                  onClick={() => setRole('guru')}
                  className={`py-2 text-[10px] font-bold rounded-xl border text-center transition ${
                    role === 'guru' 
                      ? 'bg-teal-50 border-teal-200 text-teal-700' 
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Guru
                </button>
                <button
                  type="button"
                  onClick={() => setRole('superadmin')}
                  className={`py-2 text-[10px] font-bold rounded-xl border text-center transition ${
                    role === 'superadmin' 
                      ? 'bg-rose-50 border-rose-200 text-rose-700' 
                      : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Superadmin
                </button>
              </div>
            </div>

            {/* NPSN Sekolah (Only if role is admin or guru) */}
            {(role === 'admin' || role === 'guru') && (
              <div className="animate-slideDown">
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Sekolah Tugas {role === 'guru' ? 'Guru' : 'Admin'}
                </label>
                <select
                  value={npsnSekolah}
                  onChange={(e) => setNpsnSekolah(e.target.value)}
                  className="w-full px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:border-indigo-500 text-slate-700"
                >
                  {schools.filter(s => s.npsn !== 'SCH-DEFAULT').map((sch) => (
                    <option key={sch.npsn} value={sch.npsn}>
                      [{sch.npsn}] {sch.nama}
                    </option>
                  ))}
                </select>
                <p className="text-[10px] text-slate-400 mt-1 leading-relaxed">
                  Akun {role === 'guru' ? 'guru' : 'admin'} ini hanya diperbolehkan mengakses data khusus untuk sekolah di atas.
                </p>
              </div>
            )}

            {/* Kelas Tugas (Only if role is guru) */}
            {role === 'guru' && (
              <div className="animate-slideDown">
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Kelas Tugas Mengajar
                </label>
                <input
                  type="text"
                  value={kelasTugas}
                  onChange={(e) => setKelasTugas(e.target.value)}
                  className="w-full px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:border-indigo-500 text-slate-700 font-semibold"
                  placeholder="Contoh: XI RPL 1, X TKJ 2..."
                  required
                />
                <p className="text-[10px] text-slate-400 mt-1 leading-relaxed">
                  Guru ini akan terkunci datanya hanya untuk melihat kelas yang didefinisikan di atas.
                </p>
              </div>
            )}

            {/* Feedback messages inside form container */}
            {errorMsg && (
              <div className="p-3 bg-rose-50 border border-rose-100 rounded-xl text-[11px] text-rose-700 flex items-start space-x-2">
                <AlertCircle className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
                <span>{errorMsg}</span>
              </div>
            )}

            {successMsg && (
              <div className="p-3 bg-emerald-50 border border-emerald-100 rounded-xl text-[11px] text-emerald-700 flex items-start space-x-2">
                <Check className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                <span>{successMsg}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 active:scale-95 disabled:opacity-50 text-white rounded-xl text-xs font-bold flex items-center justify-center shadow-lg shadow-indigo-600/15 transition-all"
            >
              {loading ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                  Mendaftarkan...
                </>
              ) : (
                <>
                  <UserPlus className="w-3.5 h-3.5 mr-1.5" />
                  Simpan Akun Login
                </>
              )}
            </button>

          </form>
        </div>

        {/* User Account Registry List (2 Columns) */}
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-100 shadow-md space-y-4">
          
          {/* Header of Table */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
            <div>
              <h4 className="text-sm font-bold text-slate-800 flex items-center">
                <Users className="w-4.5 h-4.5 mr-1.5 text-indigo-600 shrink-0" />
                Daftar Kredensial Akun (Tabel Login)
              </h4>
              <p className="text-[11px] text-slate-400">Total terdaftar: {logins.length} administrator</p>
            </div>
            
            {/* Search Field */}
            <div className="relative">
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Cari email / role / NPSN..."
                className="pl-8 pr-3 py-1.5 text-[11px] bg-slate-50 border border-slate-200 rounded-lg focus:outline-hidden focus:border-indigo-500 text-slate-600 w-full sm:w-48 font-mono"
              />
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>
          </div>

          {/* User Cards / List */}
          <div className="space-y-3.5 max-h-[550px] overflow-y-auto pr-1">
            {filteredLogins.length === 0 ? (
              <div className="p-8 text-center bg-slate-50 border border-slate-100 rounded-2xl text-slate-400 text-xs">
                Tidak ada data login ditemukan yang cocok dengan pencarian Anda.
              </div>
            ) : (
              filteredLogins.map((record) => {
                const isEditing = editingId === record.id;
                const assignedSchool = schools.find(s => s.npsn === record.npsn_sekolah);

                return (
                  <div 
                    key={record.id} 
                    className={`p-4 rounded-xl border transition-all ${
                      isEditing 
                        ? 'border-indigo-500/40 bg-indigo-50/5 shadow-xs' 
                        : 'border-slate-100 hover:border-slate-200 bg-white hover:shadow-xs'
                    }`}
                  >
                    {isEditing ? (
                      /* Editing View inline */
                      <div className="space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                          <span className="text-[11px] font-mono font-bold text-slate-700">{record.email}</span>
                          <span className="text-[9px] font-mono text-slate-400">ID: {record.id}</span>
                        </div>
                        
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                          {/* Edit Password */}
                          <div>
                            <label className="block text-[9px] font-bold text-slate-500 uppercase mb-1">Edit Password</label>
                            <input
                              type="text"
                              value={editPassword}
                              onChange={(e) => setEditPassword(e.target.value)}
                              className="w-full px-2 py-1.5 text-[11px] bg-white border border-slate-200 rounded-lg font-mono text-slate-700"
                            />
                          </div>

                          {/* Edit Role */}
                          <div>
                            <label className="block text-[9px] font-bold text-slate-500 uppercase mb-1">Edit Role</label>
                            <select
                              value={editRole}
                              onChange={(e) => setEditRole(e.target.value as UserRole)}
                              className="w-full px-2 py-1.5 text-[11px] bg-white border border-slate-200 rounded-lg text-slate-700"
                            >
                              <option value="admin">Admin Sekolah</option>
                              <option value="guru">Guru Sekolah</option>
                              <option value="superadmin">Superadmin</option>
                            </select>
                          </div>

                          {/* Edit NPSN */}
                          {(editRole === 'admin' || editRole === 'guru') && (
                            <div>
                              <label className="block text-[9px] font-bold text-slate-500 uppercase mb-1">Sekolah Penugasan</label>
                              <select
                                value={editNpsn}
                                onChange={(e) => setEditNpsn(e.target.value)}
                                className="w-full px-2 py-1.5 text-[11px] bg-white border border-slate-200 rounded-lg text-slate-700"
                              >
                                {schools.filter(s => s.npsn !== 'SCH-DEFAULT').map((sch) => (
                                  <option key={sch.npsn} value={sch.npsn}>
                                    {sch.nama} ({sch.npsn})
                                  </option>
                                ))}
                              </select>
                            </div>
                          )}

                          {/* Edit Kelas Tugas */}
                          {editRole === 'guru' && (
                            <div>
                              <label className="block text-[9px] font-bold text-slate-500 uppercase mb-1">Kelas Tugas</label>
                              <input
                                type="text"
                                value={editKelasTugas}
                                onChange={(e) => setEditKelasTugas(e.target.value)}
                                className="w-full px-2 py-1.5 text-[11px] bg-white border border-slate-200 rounded-lg text-slate-700 font-semibold"
                                placeholder="Contoh: XI RPL 1..."
                              />
                            </div>
                          )}
                        </div>

                        <div className="flex justify-end space-x-2 pt-2">
                          <button
                            type="button"
                            onClick={() => setEditingId(null)}
                            className="px-2.5 py-1.5 text-[10px] font-bold bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-lg flex items-center transition"
                          >
                            <X className="w-3 h-3 mr-1" /> Batal
                          </button>
                          <button
                            type="button"
                            onClick={() => handleUpdate(record.id)}
                            className="px-3 py-1.5 text-[10px] font-bold bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg flex items-center transition"
                          >
                            <Check className="w-3 h-3 mr-1" /> Simpan Update
                          </button>
                        </div>
                      </div>
                    ) : (
                      /* Display View */
                      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                        <div className="space-y-1.5 min-w-0">
                          {/* Email & Role Badge */}
                          <div className="flex items-center space-x-2.5 flex-wrap gap-1">
                            <span className="text-xs font-bold font-mono text-slate-800 break-all">{record.email}</span>
                            {record.role === 'superadmin' ? (
                              <span className="text-[9px] font-bold uppercase tracking-wider bg-rose-50 text-rose-600 border border-rose-100 px-1.5 py-0.5 rounded">
                                Superadmin
                              </span>
                            ) : record.role === 'guru' ? (
                              <span className="text-[9px] font-bold uppercase tracking-wider bg-teal-50 text-teal-600 border border-teal-100 px-1.5 py-0.5 rounded">
                                Guru Sekolah
                              </span>
                            ) : (
                              <span className="text-[9px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-600 border border-indigo-100 px-1.5 py-0.5 rounded">
                                Admin Sekolah
                              </span>
                            )}
                          </div>

                          {/* Password Hint & Assign School Description */}
                          <div className="flex flex-wrap gap-x-4 gap-y-1 items-center text-[10px] text-slate-400">
                            <span className="flex items-center font-mono">
                              <Lock className="w-3 h-3 mr-1 text-slate-300" />
                              Sandi: <b className="text-slate-600 font-bold ml-1">{record.password}</b>
                            </span>
                            
                            {(record.role === 'admin' || record.role === 'guru') && (
                              <span className="flex items-center text-slate-500 font-sans">
                                <SchoolIcon className="w-3 h-3 mr-1 text-indigo-400 shrink-0" />
                                {assignedSchool ? assignedSchool.nama : `Sekolah NPSN: ${record.npsn_sekolah}`}
                              </span>
                            )}

                            {record.role === 'guru' && record.kelas_tugas && (
                              <span className="flex items-center text-teal-600 font-bold font-sans">
                                <Users className="w-3 h-3 mr-1 shrink-0" />
                                Kelas Tugas: {record.kelas_tugas}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Edit & Delete Action Buttons */}
                        <div className="flex items-center space-x-1.5 shrink-0 justify-end">
                          <button
                            type="button"
                            onClick={() => startEdit(record)}
                            className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition"
                            title="Edit User"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(record.id, record.email)}
                            className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                            title="Hapus User"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

      </div>
    </div>
  );
};
