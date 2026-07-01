import React, { useEffect, useState } from 'react';
import { db } from '../firebaseClient';
import { Holiday, School } from '../types';
import { useAuth } from '../context/AuthContext';
import { 
  Calendar as CalendarIcon, 
  Plus, 
  Trash2, 
  X, 
  AlertTriangle, 
  Info,
  CalendarDays,
  Lock,
  Globe,
  School as SchoolIcon,
  Sparkles,
  Loader2,
  Check
} from 'lucide-react';

interface HariLiburViewProps {
  selectedNpsn: string;
  schools: School[];
}

export const HariLiburView: React.FC<HariLiburViewProps> = ({ selectedNpsn, schools }) => {
  const { user } = useAuth();
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  
  // Form State
  const [isAddOpen, setIsAddOpen] = useState<boolean>(false);
  const [tanggal, setTanggal] = useState<string>('');
  const [nama, setNama] = useState<string>('');
  const [scopeNpsn, setScopeNpsn] = useState<string>('ALL');
  const [keterangan, setKeterangan] = useState<string>('');
  const [error, setError] = useState<string>('');

  const isReadOnly = user?.role === 'guru';
  const isDefaultSchool = selectedNpsn === 'SCH-DEFAULT';

  // AI Holiday Assistant State
  interface SuggestedHoliday {
    tanggal: string;
    nama: string;
    keterangan: string;
    selected: boolean;
    alreadyExists?: boolean;
  }

  const [isAiOpen, setIsAiOpen] = useState<boolean>(false);
  const [aiMonth, setAiMonth] = useState<number>(new Date().getMonth() + 1);
  const [aiYear, setAiYear] = useState<number>(new Date().getFullYear());
  const [aiLoading, setAiLoading] = useState<boolean>(false);
  const [aiError, setAiError] = useState<string>('');
  const [aiSuccess, setAiSuccess] = useState<string>('');
  const [suggestions, setSuggestions] = useState<SuggestedHoliday[]>([]);

  const handleFetchAiHolidays = async () => {
    setAiLoading(true);
    setAiError('');
    setAiSuccess('');
    setSuggestions([]);
    try {
      const res = await fetch('/api/ai/holidays', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ month: aiMonth, year: aiYear }),
      });
      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.error || 'Gagal memuat saran dari AI');
      }
      const data = await res.json();
      
      if (data.holidays && Array.isArray(data.holidays)) {
        const mapped = data.holidays.map((sh: any) => {
          const exists = holidays.some(h => h.tanggal === sh.tanggal);
          return {
            tanggal: sh.tanggal,
            nama: sh.nama,
            keterangan: sh.keterangan || '',
            selected: !exists,
            alreadyExists: exists
          };
        });
        setSuggestions(mapped);
        if (mapped.length === 0) {
          setAiSuccess('Tidak ada hari libur nasional resmi yang terdeteksi untuk bulan ini.');
        }
      } else {
        throw new Error('Format respon AI tidak valid.');
      }
    } catch (err: any) {
      console.error(err);
      setAiError(err.message || 'Terjadi kesalahan saat memanggil AI.');
    } finally {
      setAiLoading(false);
    }
  };

  const handleSaveAiHolidays = async () => {
    const toSave = suggestions.filter(s => s.selected && !s.alreadyExists);
    if (toSave.length === 0) return;

    setAiLoading(true);
    setAiError('');
    try {
      const targetNpsn = user?.role === 'superadmin' ? scopeNpsn : selectedNpsn;
      
      for (const item of toSave) {
        await db.addHoliday({
          tanggal: item.tanggal,
          nama: item.nama,
          npsn_sekolah: targetNpsn,
          keterangan: item.keterangan || 'Dikonfigurasi otomatis oleh AI'
        });
      }
      
      setAiSuccess(`Berhasil menyimpan ${toSave.length} hari libur nasional.`);
      setSuggestions([]);
      loadHolidays();
      setTimeout(() => {
        setIsAiOpen(false);
      }, 1500);
    } catch (err: any) {
      setAiError(err.message || 'Gagal menyimpan beberapa atau semua hari libur.');
    } finally {
      setAiLoading(false);
    }
  };

  useEffect(() => {
    loadHolidays();
  }, [selectedNpsn]);

  const loadHolidays = async () => {
    setLoading(true);
    try {
      const data = await db.getHolidays(selectedNpsn);
      setHolidays(data);
    } catch (e) {
      console.error("Gagal memuat data hari libur", e);
    } finally {
      setLoading(false);
    }
  };

  const handleAddHoliday = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isReadOnly) return;
    if (!tanggal || !nama) {
      setError("Tanggal dan nama hari libur harus diisi");
      return;
    }

    try {
      setError('');
      const targetNpsn = user?.role === 'superadmin' ? scopeNpsn : selectedNpsn;
      
      await db.addHoliday({
        tanggal,
        nama,
        npsn_sekolah: targetNpsn,
        keterangan
      });

      setIsAddOpen(false);
      setTanggal('');
      setNama('');
      setKeterangan('');
      setScopeNpsn('ALL');
      loadHolidays();
    } catch (err: any) {
      setError(err.message || "Gagal menyimpan hari libur");
    }
  };

  const handleDeleteHoliday = async (id: string) => {
    if (isReadOnly) return;
    if (confirm("Apakah Anda yakin ingin menghapus konfigurasi hari libur ini?")) {
      try {
        await db.deleteHoliday(id);
        loadHolidays();
      } catch (err) {
        console.error(err);
      }
    }
  };

  // Find school name for display
  const getSchoolName = (npsn: string) => {
    if (npsn === 'ALL') return 'Semua Sekolah (Global)';
    const s = schools.find(sch => sch.npsn === npsn);
    return s ? s.nama : `NPSN: ${npsn}`;
  };

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Header View Section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-800">Konfigurasi Hari Libur</h3>
          <p className="text-xs text-slate-500 mt-0.5">Atur hari libur nasional maupun lokal sekolah untuk penyesuaian rekap otomatis absensi</p>
        </div>

        {/* Action Buttons */}
        {!isReadOnly && (
          <div className="flex flex-wrap items-center gap-2">
            <button
              id="btn-ai-hari-libur"
              onClick={() => {
                setAiError('');
                setAiSuccess('');
                setSuggestions([]);
                setScopeNpsn(user?.role === 'superadmin' ? 'ALL' : selectedNpsn);
                setIsAiOpen(true);
              }}
              disabled={isDefaultSchool && user?.role !== 'superadmin'}
              className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl shadow-md transition-all ${
                isDefaultSchool && user?.role !== 'superadmin'
                  ? 'bg-slate-300 text-slate-500 cursor-not-allowed'
                  : 'bg-emerald-600 hover:bg-emerald-700 text-white active:scale-95'
              }`}
            >
              <Sparkles className="w-4 h-4 mr-2" />
              Saran AI Libur Nasional
            </button>

            <button
              id="btn-tambah-hari-libur"
              onClick={() => {
                setError('');
                setScopeNpsn(user?.role === 'superadmin' ? 'ALL' : selectedNpsn);
                setIsAddOpen(true);
              }}
              disabled={isDefaultSchool && user?.role !== 'superadmin'}
              className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl shadow-md transition-all ${
                isDefaultSchool && user?.role !== 'superadmin'
                  ? 'bg-slate-300 text-slate-500 cursor-not-allowed'
                  : 'bg-indigo-600 hover:bg-indigo-700 text-white active:scale-95'
              }`}
            >
              <Plus className="w-4 h-4 mr-2" />
              Tambah Hari Libur
            </button>
          </div>
        )}
      </div>

      {/* Info Warning */}
      <div className="p-4 bg-amber-50 border border-amber-100 rounded-2xl flex items-start space-x-3 text-amber-800">
        <Info className="w-5 h-5 shrink-0 mt-0.5" />
        <div className="text-xs leading-relaxed">
          <p className="font-bold">Informasi Status Libur:</p>
          <p className="mt-1">
            Pada tanggal yang dikonfigurasi sebagai hari libur, sistem rekap absensi akan otomatis memberikan keterangan <strong>Libur</strong> bagi seluruh siswa (bukan dianggap Alpa/Tidak Hadir), dan menutup proses pemindaian kartu secara digital.
          </p>
        </div>
      </div>

      {/* Main holidays Table / Card */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-md overflow-hidden">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-500">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            <span className="mt-3 text-xs">Memuat daftar hari libur...</span>
          </div>
        ) : holidays.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400">
            <CalendarDays className="w-12 h-12 stroke-1 text-slate-300 mb-2" />
            <p className="text-xs font-semibold text-slate-500">Belum Ada Hari Libur Dikonfigurasi</p>
            <p className="text-[11px] text-slate-400 mt-1">Gunakan tombol di atas untuk mendaftarkan hari libur baru.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table id="holidays-data-table" className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/50 border-b border-slate-150 text-xs font-bold text-slate-400 uppercase tracking-wider">
                  <th className="py-4 px-6">Tanggal</th>
                  <th className="py-4 px-6">Nama Hari Libur</th>
                  <th className="py-4 px-6">Cakupan (Scope)</th>
                  <th className="py-4 px-6">Keterangan</th>
                  {!isReadOnly && <th className="py-4 px-6 text-right w-24">Aksi</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs text-slate-600">
                {holidays.map((h) => (
                  <tr key={h.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-6 font-bold text-slate-800">
                      {new Date(h.tanggal).toLocaleDateString('id-ID', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                    </td>
                    <td className="py-3.5 px-6">
                      <span className="font-semibold text-indigo-600">{h.nama}</span>
                    </td>
                    <td className="py-3.5 px-6">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        h.npsn_sekolah === 'ALL'
                          ? 'bg-sky-50 text-sky-600 border border-sky-100'
                          : 'bg-emerald-50 text-emerald-600 border border-emerald-100'
                      }`}>
                        {h.npsn_sekolah === 'ALL' ? (
                          <Globe className="w-3 h-3 mr-1" />
                        ) : (
                          <SchoolIcon className="w-3 h-3 mr-1" />
                        )}
                        {getSchoolName(h.npsn_sekolah)}
                      </span>
                    </td>
                    <td className="py-3.5 px-6 text-slate-500 italic">
                      {h.keterangan || '-'}
                    </td>
                    {!isReadOnly && (
                      <td className="py-3.5 px-6 text-right">
                        <button
                          id={`btn-delete-holiday-${h.id}`}
                          onClick={() => handleDeleteHoliday(h.id)}
                          className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors border border-transparent hover:border-rose-100"
                          title="Hapus Hari Libur"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* --- ADD HOLIDAY MODAL --- */}
      {isAddOpen && (
        <div id="add-holiday-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fadeIn">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-md w-full shadow-2xl overflow-hidden">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm">Tambah Konfigurasi Hari Libur</h4>
              <button onClick={() => setIsAddOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddHoliday} className="p-6 space-y-4">
              {error && (
                <div className="p-3 bg-rose-50 border border-rose-100 text-rose-600 rounded-xl text-xs flex items-center space-x-2">
                  <AlertTriangle className="w-4 h-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Tanggal Libur</label>
                <input
                  type="date"
                  required
                  value={tanggal}
                  onChange={(e) => setTanggal(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-semibold"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Nama Hari Libur</label>
                <input
                  type="text"
                  required
                  placeholder="Misal: Libur Idul Fitri, Hari Guru, dll."
                  value={nama}
                  onChange={(e) => setNama(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-medium"
                />
              </div>

              {user?.role === 'superadmin' && (
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Cakupan Sekolah</label>
                  <select
                    value={scopeNpsn}
                    onChange={(e) => setScopeNpsn(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 cursor-pointer"
                  >
                    <option value="ALL">Semua Sekolah (Global)</option>
                    {schools.filter(s => s.npsn !== 'SCH-DEFAULT').map(s => (
                      <option key={s.npsn} value={s.npsn}>
                        {s.nama} ({s.npsn})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Keterangan Tambahan</label>
                <textarea
                  placeholder="Beri keterangan opsional..."
                  value={keterangan}
                  onChange={(e) => setKeterangan(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 resize-none"
                />
              </div>

              <div className="pt-4 border-t border-slate-100 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsAddOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md"
                >
                  Simpan Hari Libur
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- AI HOLIDAY ASSISTANT MODAL --- */}
      {isAiOpen && (
        <div id="ai-holiday-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fadeIn">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-lg w-full shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
            <div className="px-6 py-4 bg-emerald-50 border-b border-emerald-100 flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-5 h-5 text-emerald-600 animate-pulse" />
                <div>
                  <h4 className="font-bold text-slate-800 text-sm">AI Asisten Libur Nasional</h4>
                  <p className="text-[10px] text-emerald-700">Didukung oleh Gemini AI untuk konfigurasi kalender otomatis</p>
                </div>
              </div>
              <button onClick={() => setIsAiOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-6 space-y-4 overflow-y-auto flex-1">
              {aiError && (
                <div className="p-3 bg-rose-50 border border-rose-100 text-rose-600 rounded-xl text-xs flex items-center space-x-2">
                  <AlertTriangle className="w-4 h-4 shrink-0" />
                  <span>{aiError}</span>
                </div>
              )}

              {aiSuccess && (
                <div className="p-3 bg-emerald-50 border border-emerald-100 text-emerald-700 rounded-xl text-xs flex items-center space-x-2 font-semibold">
                  <Check className="w-4 h-4 shrink-0 text-emerald-600" />
                  <span>{aiSuccess}</span>
                </div>
              )}

              {/* Selector area */}
              <div className="grid grid-cols-2 gap-3 bg-slate-50 p-4 rounded-xl border border-slate-100">
                <div>
                  <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Pilih Bulan</label>
                  <select
                    value={aiMonth}
                    onChange={(e) => setAiMonth(Number(e.target.value))}
                    disabled={aiLoading}
                    className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white focus:outline-hidden focus:border-emerald-500 font-semibold text-slate-700 cursor-pointer"
                  >
                    {[
                      "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                      "Juli", "Agustus", "September", "Oktober", "November", "Desember"
                    ].map((name, i) => (
                      <option key={i + 1} value={i + 1}>
                        {name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Pilih Tahun</label>
                  <select
                    value={aiYear}
                    onChange={(e) => setAiYear(Number(e.target.value))}
                    disabled={aiLoading}
                    className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white focus:outline-hidden focus:border-emerald-500 font-semibold text-slate-700 cursor-pointer"
                  >
                    {[aiYear - 1, aiYear, aiYear + 1, aiYear + 2].filter((v, idx, arr) => arr.indexOf(v) === idx).map((yr) => (
                      <option key={yr} value={yr}>
                        {yr}
                      </option>
                    ))}
                  </select>
                </div>

                {user?.role === 'superadmin' && (
                  <div className="col-span-2">
                    <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Tautkan Hasil ke Scope</label>
                    <select
                      value={scopeNpsn}
                      onChange={(e) => setScopeNpsn(e.target.value)}
                      disabled={aiLoading}
                      className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white focus:outline-hidden focus:border-emerald-500 font-semibold text-slate-700 cursor-pointer"
                    >
                      <option value="ALL">Semua Sekolah (Global)</option>
                      {schools.filter(s => s.npsn !== 'SCH-DEFAULT').map(s => (
                        <option key={s.npsn} value={s.npsn}>
                          {s.nama} ({s.npsn})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Action query button */}
              <button
                type="button"
                onClick={handleFetchAiHolidays}
                disabled={aiLoading}
                className="w-full py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold shadow-md transition-all flex items-center justify-center space-x-2 disabled:bg-emerald-300 disabled:cursor-not-allowed cursor-pointer active:scale-95"
              >
                {aiLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Menganalisis kalender nasional...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4" />
                    <span>Cari Hari Libur Bulan Ini via AI Gemini</span>
                  </>
                )}
              </button>

              {/* Loading indicator with premium hints */}
              {aiLoading && suggestions.length === 0 && (
                <div className="py-8 flex flex-col items-center justify-center space-y-3">
                  <div className="relative">
                    <div className="w-12 h-12 rounded-full border-4 border-emerald-100 border-t-emerald-600 animate-spin"></div>
                    <Sparkles className="w-5 h-5 text-emerald-500 absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 animate-pulse" />
                  </div>
                  <div className="text-center">
                    <p className="text-xs font-bold text-slate-700">Menganalisis Libur Nasional Indonesia</p>
                    <p className="text-[10px] text-slate-400 mt-1 max-w-xs leading-normal">
                      Menghubungi AI Gemini untuk mengambil data libur resmi dari Kementerian Agama, Menpan-RB, dan Kemenaker RI...
                    </p>
                  </div>
                </div>
              )}

              {/* Suggestions List */}
              {suggestions.length > 0 && (
                <div className="space-y-3">
                  <div className="flex justify-between items-center text-xs col-span-2">
                    <span className="font-bold text-slate-600">Saran Hari Libur Resmi ({suggestions.length})</span>
                    <button
                      type="button"
                      onClick={() => {
                        const allSelected = suggestions.every(s => s.selected || s.alreadyExists);
                        setSuggestions(suggestions.map(s => s.alreadyExists ? s : { ...s, selected: !allSelected }));
                      }}
                      className="text-[10px] font-bold text-emerald-600 hover:text-emerald-800"
                    >
                      {suggestions.every(s => s.selected || s.alreadyExists) ? 'Batal Pilih Semua' : 'Pilih Semua'}
                    </button>
                  </div>

                  <div className="border border-slate-150 rounded-xl overflow-hidden divide-y divide-slate-100 bg-white max-h-52 overflow-y-auto">
                    {suggestions.map((item, index) => (
                      <div
                        key={index}
                        onClick={() => {
                          if (item.alreadyExists) return;
                          setSuggestions(suggestions.map((s, idx) => idx === index ? { ...s, selected: !s.selected } : s));
                        }}
                        className={`p-3.5 flex items-start space-x-3 transition-colors ${
                          item.alreadyExists 
                            ? 'bg-slate-50 opacity-60 cursor-not-allowed' 
                            : 'hover:bg-slate-50 cursor-pointer'
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={item.selected}
                          disabled={item.alreadyExists}
                          onChange={() => {}} // Handled by div onClick
                          className="mt-0.5 rounded-sm text-emerald-600 focus:ring-emerald-500 h-3.5 w-3.5 border-slate-300 cursor-pointer"
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center space-x-2">
                            <p className="text-xs font-bold text-slate-800">
                              {new Date(item.tanggal).toLocaleDateString('id-ID', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
                            </p>
                            {item.alreadyExists && (
                              <span className="px-1.5 py-0.5 rounded-full text-[8px] font-bold bg-slate-200 text-slate-600">
                                Sudah Terdaftar
                              </span>
                            )}
                          </div>
                          <p className="text-xs font-semibold text-emerald-600 mt-0.5">{item.nama}</p>
                          {item.keterangan && (
                            <p className="text-[10px] text-slate-400 leading-normal mt-0.5 italic">{item.keterangan}</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex justify-end space-x-2">
              <button
                type="button"
                onClick={() => setIsAiOpen(false)}
                disabled={aiLoading}
                className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 transition-all cursor-pointer"
              >
                Tutup
              </button>
              {suggestions.length > 0 && (
                <button
                  type="button"
                  onClick={handleSaveAiHolidays}
                  disabled={aiLoading || !suggestions.some(s => s.selected && !s.alreadyExists)}
                  className={`px-4 py-2 rounded-xl text-xs font-bold shadow-md transition-all flex items-center space-x-1.5 cursor-pointer ${
                    aiLoading || !suggestions.some(s => s.selected && !s.alreadyExists)
                      ? 'bg-slate-300 text-slate-500 cursor-not-allowed shadow-none'
                      : 'bg-emerald-600 hover:bg-emerald-700 text-white active:scale-95'
                  }`}
                >
                  {aiLoading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Menyimpan...</span>
                    </>
                  ) : (
                    <>
                      <Check className="w-3.5 h-3.5" />
                      <span>Simpan ({suggestions.filter(s => s.selected && !s.alreadyExists).length}) Hari Libur</span>
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
