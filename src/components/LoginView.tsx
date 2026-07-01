import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { db } from '../firebaseClient';
import { School } from '../types';
import { AppLogo } from './AppLogo';
import { 
  Lock, 
  Mail, 
  ArrowRight, 
  AlertCircle, 
  UserPlus, 
  CheckCircle2, 
  Building2, 
  Plus, 
  ArrowLeft,
  MapPin,
  KeyRound,
  Shield,
  GraduationCap,
  Chrome
} from 'lucide-react';

export const LoginView: React.FC = () => {
  const { login, loginWithGoogle } = useAuth();
  
  // Login States
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [successMsg, setSuccessMsg] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [googleLoading, setGoogleLoading] = useState<boolean>(false);
  const [domainCopied, setDomainCopied] = useState<boolean>(false);

  const isUnauthorizedDomainError = errorMsg.toLowerCase().includes('unauthorized-domain') || errorMsg.toLowerCase().includes('auth/unauthorized-domain');

  // Mode state: Login vs Register
  const [isRegister, setIsRegister] = useState<boolean>(false);

  // Register States
  const [regEmail, setRegEmail] = useState<string>('');
  const [regPassword, setRegPassword] = useState<string>('');
  const [regConfirmPassword, setRegConfirmPassword] = useState<string>('');
  const [regRole, setRegRole] = useState<'admin' | 'guru'>('admin');
  const [schools, setSchools] = useState<School[]>([]);
  const [schoolMode, setSchoolMode] = useState<'select' | 'create'>('select');
  const [selectedNpsn, setSelectedNpsn] = useState<string>('');
  const [newSchoolNpsn, setNewSchoolNpsn] = useState<string>('');
  const [newSchoolNama, setNewSchoolNama] = useState<string>('');
  const [newSchoolAlamat, setNewSchoolAlamat] = useState<string>('');

  // Load registered schools
  useEffect(() => {
    async function loadSchools() {
      try {
        const list = await db.getSchools();
        const filtered = list.filter(s => s.npsn !== 'SCH-DEFAULT');
        setSchools(filtered);
        if (filtered.length > 0) {
          setSelectedNpsn(filtered[0].npsn);
          setSchoolMode('select');
        } else {
          setSchoolMode('create');
        }
      } catch (e) {
        console.error("Gagal memuat sekolah untuk pendaftaran", e);
        setSchoolMode('create');
      }
    }
    loadSchools();
  }, [isRegister]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg('Email dan password wajib diisi.');
      return;
    }

    const trimmed = email.trim().toLowerCase();
    if (!trimmed.endsWith('@xdegan.com') && !trimmed.endsWith('@degan.com')) {
      setErrorMsg('Pengisian ditolak. Email wajib menggunakan akhiran @xdegan.com');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      const result = await login(email, password);
      if (!result.success) {
        setErrorMsg(result.error || 'Autentikasi gagal.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Terjadi kesalahan sistem.');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setGoogleLoading(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      const result = await loginWithGoogle();
      if (!result.success) {
        setErrorMsg(result.error || 'Gagal login dengan Google.');
      } else {
        setSuccessMsg('Masuk dengan Google sukses!');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Terjadi kesalahan saat masuk dengan Google.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    if (!regEmail || !regPassword || !regConfirmPassword) {
      setErrorMsg('Semua field wajib diisi.');
      return;
    }

    const trimmedReg = regEmail.trim().toLowerCase();
    if (!trimmedReg.endsWith('@xdegan.com') && !trimmedReg.endsWith('@xdegan.com') && !trimmedReg.endsWith('@guru.sd.belajar.id')) {
      setErrorMsg('Pengisian ditolak. Email wajib menggunakan akhiran @xdegan.com');
      return;
    }

    if (regPassword !== regConfirmPassword) {
      setErrorMsg('Konfirmasi sandi tidak cocok.');
      return;
    }

    if (regPassword.length < 6) {
      setErrorMsg('Sandi minimal harus 6 karakter.');
      return;
    }

    setLoading(true);

    try {
      let finalNpsn = '';

      if (schoolMode === 'create') {
        if (!newSchoolNpsn || !newSchoolNama) {
          setErrorMsg('NPSN dan Nama Sekolah baru wajib diisi.');
          setLoading(false);
          return;
        }

        const newSchool: School = {
          npsn: newSchoolNpsn.trim(),
          nama: newSchoolNama.trim(),
          alamat: newSchoolAlamat.trim() || 'Alamat Sekolah Terintegrasi'
        };

        await db.addSchool(newSchool);
        finalNpsn = newSchool.npsn;
      } else {
        if (!selectedNpsn) {
          setErrorMsg('Silakan pilih salah satu sekolah terdaftar.');
          setLoading(false);
          return;
        }
        finalNpsn = selectedNpsn;
      }

      await db.addLoginRecord({
        email: regEmail.trim(),
        password: regPassword,
        role: regRole,
        npsn_sekolah: finalNpsn
      });

      setSuccessMsg(`Pendaftaran Berhasil sebagai ${regRole === 'guru' ? 'Guru' : 'Admin'} Sekolah! Silakan masuk dengan akun baru Anda.`);
      setEmail(regEmail.trim());
      setPassword(regPassword);

      // Clean registration states
      setRegEmail('');
      setRegPassword('');
      setRegConfirmPassword('');
      setRegRole('admin');
      setNewSchoolNpsn('');
      setNewSchoolNama('');
      setNewSchoolAlamat('');

      setIsRegister(false);
    } catch (err: any) {
      console.error("Registrasi gagal", err);
      setErrorMsg(err.message || 'Gagal mendaftarkan akun baru. Silakan coba lagi.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (mockEmail: string) => {
    setEmail(mockEmail);
    setPassword('admin123'); // Default password for evaluation
    setErrorMsg('');
    setSuccessMsg('');
  };

  return (
    <div id="login-screen" className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative overflow-hidden font-sans">
      
      {/* Decorative backdrop mesh */}
      <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-indigo-500/10 rounded-full blur-[100px] pointer-events-none"></div>
      <div className="absolute bottom-0 right-1/4 w-[400px] h-[400px] bg-teal-500/5 rounded-full blur-[80px] pointer-events-none"></div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        <div className="flex justify-center">
          <AppLogo className="w-16 h-16 animate-pulse" iconClassName="w-8 h-8" variant="color" />
        </div>
        <h2 className="mt-4 text-center text-2xl font-black text-white tracking-tight">
          X-Degan QR
        </h2>
        <p className="mt-1.5 text-center text-xs text-slate-400 font-medium">
          Sistem Presensi & Kartu QR Multi-Sekolah Terintegrasi
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md relative z-10 px-4">
        <div className="bg-slate-900 border border-slate-800/80 py-8 px-6 shadow-2xl rounded-3xl sm:px-10">
          
          <div className="mb-6 flex justify-between border-b border-slate-800 pb-4">
            <button
              onClick={() => {
                setIsRegister(false);
                setErrorMsg('');
                setSuccessMsg('');
              }}
              className={`pb-2 text-sm font-bold border-b-2 transition-all cursor-pointer ${
                !isRegister 
                  ? 'border-indigo-500 text-white' 
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Masuk
            </button>
            <button
              onClick={() => {
                setIsRegister(true);
                setErrorMsg('');
                setSuccessMsg('');
              }}
              className={`pb-2 text-sm font-bold border-b-2 transition-all cursor-pointer ${
                isRegister 
                  ? 'border-indigo-500 text-white' 
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Daftar
            </button>
          </div>

          {errorMsg && (
            <div className="mb-4 p-3.5 bg-rose-500/10 border border-rose-500/20 text-rose-300 rounded-xl flex flex-col space-y-2 text-xs">
              <div className="flex items-start space-x-2">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-400" />
                <span>{errorMsg}</span>
              </div>
              
              {isUnauthorizedDomainError && (
                <div className="mt-2.5 pt-2.5 border-t border-rose-500/10 text-[11px] text-slate-300 space-y-2 leading-relaxed">
                  <p className="font-bold text-amber-400">⚠️ Masalah Otorisasi Domain Firebase:</p>
                  <p>
                    Masuk dengan Google dibatasi hanya untuk domain yang telah Anda otorisasi di Konsol Firebase Anda. Silakan tambahkan domain aplikasi saat ini agar Google Auth diizinkan berjalan.
                  </p>
                  <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-800 space-y-1.5 font-mono">
                    <div className="text-[10px] text-slate-400 font-sans">Salin domain ini:</div>
                    <div className="flex items-center justify-between text-indigo-400 select-all font-bold">
                      <span className="break-all">{window.location.hostname}</span>
                      <button 
                        type="button"
                        onClick={() => {
                          navigator.clipboard.writeText(window.location.hostname);
                          setDomainCopied(true);
                          setTimeout(() => setDomainCopied(false), 2000);
                        }}
                        className="text-[9px] bg-indigo-600 hover:bg-indigo-700 text-white px-2 py-0.5 rounded cursor-pointer transition-colors shrink-0 ml-2"
                      >
                        {domainCopied ? 'Tersalin!' : 'Salin'}
                      </button>
                    </div>
                  </div>
                  <ol className="list-decimal list-inside space-y-1 text-slate-400 pl-1">
                    <li>Buka <a href="https://console.firebase.google.com/" target="_blank" rel="noopener noreferrer" className="text-indigo-400 underline hover:text-indigo-300">Firebase Console</a> Anda.</li>
                    <li>Pilih proyek Anda, lalu navigasikan ke <strong>Build &gt; Authentication</strong>.</li>
                    <li>Buka tab <strong>Settings</strong> di bagian atas.</li>
                    <li>Pilih menu <strong>Authorized domains</strong> di sebelah kiri.</li>
                    <li>Klik <strong>Add domain</strong> lalu tempelkan domain yang sudah disalin di atas.</li>
                    <li>Setelah disimpan, tunggu beberapa detik dan coba masuk lagi!</li>
                  </ol>
                </div>
              )}
            </div>
          )}

          {successMsg && (
            <div className="mb-4 p-3.5 bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 rounded-xl flex items-start space-x-2 text-xs">
              <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5 text-emerald-400" />
              <span>{successMsg}</span>
            </div>
          )}

          {!isRegister ? (
            /* LOGIN FORM */
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div>
                <label htmlFor="email" className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Alamat Email
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Mail className="w-4 h-4" />
                  </div>
                  <input
                    id="email"
                    type="email"
                    required
                    autoComplete="email"
                    placeholder="contoh@xdegan.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-hidden focus:border-indigo-500 font-medium transition-all"
                  />
                </div>
                <p className="mt-1.5 text-[10px] text-slate-400 leading-normal">
                  <span className="font-semibold text-indigo-400">Pendaftaran & Masuk:</span> Email wajib menggunakan akhiran <span className="font-bold text-slate-200">@xdegan.com</span>
                </p>
                <p className="mt-1 text-[10px] text-slate-500">
                  Contoh: <span className="font-mono text-slate-400">hatip@xdegan.com, hanipah@xdegan.com</span>
                </p>
              </div>

              <div>
                <label htmlFor="password" className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Kata Sandi
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input
                    id="password"
                    type="password"
                    required
                    autoComplete="current-password"
                    placeholder="••••••••••••"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-hidden focus:border-indigo-500 font-medium transition-all"
                  />
                </div>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={loading || googleLoading}
                  className="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-xl text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-700 active:scale-98 focus:outline-hidden shadow-lg transition-all cursor-pointer"
                >
                  {loading ? (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                  ) : (
                    <>
                      <span>Masuk ke Dashboard</span>
                      <ArrowRight className="w-4 h-4 ml-2" />
                    </>
                  )}
                </button>
              </div>

              <div className="relative my-4 flex py-1 items-center">
                <div className="flex-grow border-t border-slate-800"></div>
                <span className="flex-shrink mx-3 text-[10px] text-slate-500 font-bold uppercase tracking-wider">atau</span>
                <div className="flex-grow border-t border-slate-800"></div>
              </div>

              <div>
                <button
                  type="button"
                  onClick={handleGoogleLogin}
                  disabled={loading || googleLoading}
                  className="w-full flex justify-center items-center py-3 px-4 border border-slate-850 rounded-xl text-xs font-bold text-slate-200 bg-slate-950/40 hover:bg-slate-900 active:scale-98 focus:outline-hidden transition-all cursor-pointer space-x-2.5"
                >
                  {googleLoading ? (
                    <div className="w-4 h-4 border-2 border-slate-400 border-t-white rounded-full animate-spin"></div>
                  ) : (
                    <>
                      <Chrome className="w-4 h-4 text-rose-500" />
                      <span>Masuk dengan Google</span>
                    </>
                  )}
                </button>
              </div>

              <div className="mt-6 text-center text-xs">
                <span className="text-slate-400">Belum memiliki akun ? </span>
                <button
                  type="button"
                  onClick={() => {
                    setIsRegister(true);
                    setErrorMsg('');
                    setSuccessMsg('');
                  }}
                  className="text-indigo-400 hover:text-indigo-300 font-bold hover:underline cursor-pointer"
                >
                  Daftar Sekarang
                </button>
              </div>

            </form>
          ) : (
            /* REGISTRATION FORM */
            <form className="space-y-4" onSubmit={handleRegisterSubmit}>
              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Alamat Email
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Mail className="w-4 h-4" />
                  </div>
                  <input
                    type="email"
                    required
                    placeholder="contohbaru@xdegan.com"
                    value={regEmail}
                    onChange={(e) => setRegEmail(e.target.value)}
                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-hidden focus:border-indigo-500 font-medium transition-all"
                  />
                </div>
                <p className="mt-1.5 text-[10px] text-slate-400 leading-normal">
                  <span className="font-semibold text-indigo-400">Pendaftaran & Masuk:</span> Email wajib menggunakan akhiran <span className="font-bold text-slate-200">@xdegan.com</span>
                </p>
                <p className="mt-1 text-[10px] text-slate-500">
                  Contoh: <span className="font-mono text-slate-400">dulmawi@xdegan.com, sukri@xdegan.com</span>
                </p>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Kata Sandi Baru (Min. 6 Karakter)
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input
                    type="password"
                    required
                    placeholder="••••••••"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-hidden focus:border-indigo-500 font-medium transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Konfirmasi Kata Sandi
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Lock className="w-4 h-4" />
                  </div>
                  <input
                    type="password"
                    required
                    placeholder="••••••••"
                    value={regConfirmPassword}
                    onChange={(e) => setRegConfirmPassword(e.target.value)}
                    className="block w-full pl-10 pr-3 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-hidden focus:border-indigo-500 font-medium transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Pilih Hak Akses / Peran
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setRegRole('admin')}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      regRole === 'admin'
                        ? 'bg-indigo-600/10 border-indigo-500 text-white'
                        : 'bg-slate-950/40 border-slate-800 text-slate-400 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center space-x-2">
                      <Shield className={`w-4 h-4 ${regRole === 'admin' ? 'text-indigo-400' : 'text-slate-500'}`} />
                      <span className="text-xs font-bold">Admin</span>
                    </div>
                    <p className="text-[10px] text-slate-500 mt-1 leading-normal">
                      Kelola siswa, kelas, absensi, & setelan sekolah.
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => setRegRole('guru')}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      regRole === 'guru'
                        ? 'bg-indigo-600/10 border-indigo-500 text-white'
                        : 'bg-slate-950/40 border-slate-800 text-slate-400 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center space-x-2">
                      <GraduationCap className={`w-4 h-4 ${regRole === 'guru' ? 'text-indigo-400' : 'text-slate-500'}`} />
                      <span className="text-xs font-bold">Guru</span>
                    </div>
                    <p className="text-[10px] text-slate-500 mt-1 leading-normal">
                      Melihat siswa, input rekap, & broadcast info.
                    </p>
                  </button>
                </div>
              </div>

              {/* School Association Selector */}
              <div className="pt-2 border-t border-slate-800/60 mt-4">
                <div className="flex items-center justify-between mb-2">
                  <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                    Asosiasi Sekolah
                  </label>
                  <div className="flex space-x-2">
                    <button
                      type="button"
                      onClick={() => setSchoolMode('select')}
                      className={`text-[10px] font-bold px-2 py-1 rounded-md transition-all cursor-pointer ${
                        schoolMode === 'select' 
                          ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' 
                          : 'text-slate-500 hover:text-slate-400'
                      }`}
                    >
                      Pilih Terdaftar
                    </button>
                    <button
                      type="button"
                      onClick={() => setSchoolMode('create')}
                      className={`text-[10px] font-bold px-2 py-1 rounded-md transition-all cursor-pointer ${
                        schoolMode === 'create' 
                          ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30' 
                          : 'text-slate-500 hover:text-slate-400'
                      }`}
                    >
                      + Sekolah Baru
                    </button>
                  </div>
                </div>

                {schoolMode === 'select' ? (
                  <div>
                    {schools.length > 0 ? (
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                          <Building2 className="w-4 h-4" />
                        </div>
                        <select
                          value={selectedNpsn}
                          onChange={(e) => setSelectedNpsn(e.target.value)}
                          className="block w-full pl-10 pr-10 py-2.5 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 focus:outline-hidden focus:border-indigo-500 font-medium transition-all appearance-none"
                        >
                          {schools.map((sch) => (
                            <option key={sch.npsn} value={sch.npsn}>
                              {sch.nama} ({sch.npsn})
                            </option>
                          ))}
                        </select>
                        <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-500">
                          <Plus className="w-4 h-4 rotate-45" />
                        </div>
                      </div>
                    ) : (
                      <div className="text-[11px] text-amber-400 bg-amber-500/5 border border-amber-500/10 p-2.5 rounded-xl">
                        Tidak ada sekolah terdaftar. Silakan pilih opsi "+ Sekolah Baru" untuk mendaftarkan sekolah baru Anda terlebih dahulu.
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="space-y-3 bg-slate-950/40 p-3 rounded-2xl border border-slate-800/60">
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wide mb-1">
                        NPSN Sekolah Baru (8 Digit)
                      </label>
                      <input
                        type="text"
                        placeholder="Contoh: 50102030"
                        maxLength={12}
                        value={newSchoolNpsn}
                        onChange={(e) => setNewSchoolNpsn(e.target.value)}
                        className="block w-full px-3 py-2 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-600 focus:outline-hidden focus:border-indigo-500 font-medium"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wide mb-1">
                        Nama Lengkap Sekolah
                      </label>
                      <input
                        type="text"
                        placeholder="Contoh: SMKN 1 Pasongsongan"
                        value={newSchoolNama}
                        onChange={(e) => setNewSchoolNama(e.target.value)}
                        className="block w-full px-3 py-2 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-600 focus:outline-hidden focus:border-indigo-500 font-medium"
                      />
                    </div>
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-500 uppercase tracking-wide mb-1">
                        Alamat Lengkap Sekolah (Opsional)
                      </label>
                      <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-600">
                          <MapPin className="w-3.5 h-3.5" />
                        </div>
                        <input
                          type="text"
                          placeholder="Contoh: Jl. Pasongsongan No. 45"
                          value={newSchoolAlamat}
                          onChange={(e) => setNewSchoolAlamat(e.target.value)}
                          className="block w-full pl-9 pr-3 py-2 bg-slate-950/80 border border-slate-800 rounded-xl text-xs text-slate-200 placeholder-slate-600 focus:outline-hidden focus:border-indigo-500 font-medium"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>

              <div className="pt-4">
                <button
                  type="submit"
                  disabled={loading || googleLoading}
                  className="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-xl text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-700 active:scale-98 focus:outline-hidden shadow-lg transition-all cursor-pointer"
                >
                  {loading ? (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                  ) : (
                    <>
                      <UserPlus className="w-4 h-4 mr-2" />
                      <span>Daftar Akun {regRole === 'guru' ? 'Guru' : 'Admin'}</span>
                    </>
                  )}
                </button>
              </div>

              <div className="relative my-4 flex py-1 items-center">
                <div className="flex-grow border-t border-slate-800"></div>
                <span className="flex-shrink mx-3 text-[10px] text-slate-500 font-bold uppercase tracking-wider">atau</span>
                <div className="flex-grow border-t border-slate-800"></div>
              </div>

              <div>
                <button
                  type="button"
                  onClick={handleGoogleLogin}
                  disabled={loading || googleLoading}
                  className="w-full flex justify-center items-center py-3 px-4 border border-slate-850 rounded-xl text-xs font-bold text-slate-200 bg-slate-950/40 hover:bg-slate-900 active:scale-98 focus:outline-hidden transition-all cursor-pointer space-x-2.5"
                >
                  {googleLoading ? (
                    <div className="w-4 h-4 border-2 border-slate-400 border-t-white rounded-full animate-spin"></div>
                  ) : (
                    <>
                      <Chrome className="w-4 h-4 text-rose-500" />
                      <span>Daftar / Masuk dengan Google</span>
                    </>
                  )}
                </button>
              </div>

              <div className="mt-6 text-center text-xs">
                <span className="text-slate-400">Sudah memiliki akun? </span>
                <button
                  type="button"
                  onClick={() => {
                    setIsRegister(false);
                    setErrorMsg('');
                    setSuccessMsg('');
                  }}
                  className="text-indigo-400 hover:text-indigo-300 font-bold hover:underline cursor-pointer"
                >
                  Masuk Sekarang
                </button>
              </div>
            </form>
          )}

        </div>
      </div>
    </div>
  );
};
