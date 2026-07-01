import React, { useState } from 'react';
import { db } from '../firebaseClient';
import { 
  Play, 
  Terminal, 
  Database, 
  Copy, 
  Check, 
  AlertCircle, 
  RefreshCw, 
  Info, 
  FileJson,
  Table,
  ChevronRight,
  Sparkles,
  HelpCircle,
  HelpCircle as ShieldAlert
} from 'lucide-react';

interface SqlSnippet {
  label: string;
  query: string;
  description: string;
}

export const SqlEditorView: React.FC = () => {
  const [query, setQuery] = useState<string>('SELECT * FROM login;');
  const [loading, setLoading] = useState<boolean>(false);
  const [successMsg, setSuccessMsg] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [results, setResults] = useState<any[] | null>(null);
  const [copiedText, setCopiedText] = useState<boolean>(false);
  const [affectedCount, setAffectedCount] = useState<string | null>(null);

  // Schema catalog of our system
  const TABLES_SCHEMA = [
    {
      name: 'login',
      desc: 'Menyimpan data kredensial akun administrator sekolah, guru, dan superadmin.',
      cols: [
        { name: 'id', type: 'INT / UUID', desc: 'Primary key identitas user' },
        { name: 'email', type: 'VARCHAR(150)', desc: 'Alamat email login unik' },
        { name: 'password', type: 'VARCHAR(100)', desc: 'Password teks biasa atau hash' },
        { name: 'role', type: 'VARCHAR(20)', desc: 'superadmin, admin, atau guru' },
        { name: 'npsn_sekolah', type: 'VARCHAR(20)', desc: 'Relasi ke sekolah.npsn (NULL jika superadmin)' },
        { name: 'kelas_tugas', type: 'VARCHAR(255)', desc: 'Kelas penugasan guru (khusus role=guru)' },
      ]
    },
    {
      name: 'sekolah',
      desc: 'Daftar data sekolah terdaftar dalam sistem administrasi absensi.',
      cols: [
        { name: 'npsn', type: 'VARCHAR(20) PK', desc: 'Nomor Pokok Sekolah Nasional' },
        { name: 'nama', type: 'VARCHAR(150)', desc: 'Nama lengkap instansi sekolah' },
        { name: 'alamat', type: 'TEXT', desc: 'Lokasi fisik sekolah' },
      ]
    },
    {
      name: 'siswa',
      desc: 'Tabel biodata siswa yang terdaftar untuk keperluan absensi scan QR.',
      cols: [
        { name: 'id', type: 'UUID PK', desc: 'ID unik internal siswa' },
        { name: 'nama', type: 'VARCHAR(150)', desc: 'Nama lengkap siswa' },
        { name: 'nisn', type: 'VARCHAR(12)', desc: 'Nomor Induk Siswa Nasional unik' },
        { name: 'kelas', type: 'VARCHAR(50)', desc: 'Jenjang kelas dan jurusan' },
        { name: 'npsn_sekolah', type: 'VARCHAR(20)', desc: 'Relasi sekolah asal siswa' },
        { name: 'created_at', type: 'TIMESTAMP', desc: 'Waktu registrasi siswa' },
      ]
    },
    {
      name: 'absensi',
      desc: 'Histori log hasil pemindaian kartu QR siswa maupun absensi manual.',
      cols: [
        { name: 'id', type: 'UUID PK', desc: 'ID transaksi absensi' },
        { name: 'student_id', type: 'UUID', desc: 'Relasi siswa pelapor' },
        { name: 'status', type: 'VARCHAR(20)', desc: 'Hadir, Sakit, Izin, atau Alpa' },
        { name: 'scan_method', type: 'VARCHAR(20)', desc: 'Metode: QR Code atau Manual' },
        { name: 'waktu', type: 'TIMESTAMP', desc: 'Tanggal dan waktu laporan absensi' },
        { name: 'npsn_sekolah', type: 'VARCHAR(20)', desc: 'Lokasi sekolah transaksi' },
      ]
    },
    {
      name: 'hari_libur',
      desc: 'Tabel konfigurasi hari libur nasional maupun sekolah untuk rekap absensi.',
      cols: [
        { name: 'id', type: 'SERIAL PK', desc: 'ID unik hari libur' },
        { name: 'tanggal', type: 'DATE', desc: 'Tanggal libur YYYY-MM-DD' },
        { name: 'nama', type: 'VARCHAR(150)', desc: 'Nama hari libur' },
        { name: 'npsn_sekolah', type: 'VARCHAR(20)', desc: 'NPSN sekolah spesifik, atau ALL untuk global' },
        { name: 'keterangan', type: 'TEXT', desc: 'Keterangan tambahan' },
      ]
    },
    {
      name: 'app_broadcast',
      desc: 'Informasi pengumuman yang disebarluaskan oleh superadmin.',
      cols: [
        { name: 'id', type: 'INT PK', desc: 'ID pengumuman' },
        { name: 'title', type: 'VARCHAR', desc: 'Judul pengumuman' },
        { name: 'message', type: 'TEXT', desc: 'Pesan pengumuman lengkap' },
        { name: 'drive_link', type: 'VARCHAR', desc: 'Link dokumen Google Drive eksternal' },
        { name: 'type', type: 'VARCHAR', desc: 'INSTRUCTION, BROADCAST, ANNOUNCEMENT' },
        { name: 'is_active', type: 'BOOLEAN', desc: 'Status aktif pengumuman' },
      ]
    }
  ];

  // Preset SQL Queries Snippets
  const PRESET_SNIPPETS: SqlSnippet[] = [
    {
      label: '🔧 Migrasi: Tambah Kolom kelas_tugas',
      query: 'ALTER TABLE login ADD COLUMN IF NOT EXISTS kelas_tugas VARCHAR(255);',
      description: 'Menambahkan properti kelas_tugas ke skema login di database Anda untuk fungsionalitas wali kelas/guru.'
    },
    {
      label: '🔧 Migrasi: Buat Tabel Hari Libur',
      query: 'CREATE TABLE IF NOT EXISTS hari_libur (\n  id SERIAL PRIMARY KEY,\n  tanggal DATE NOT NULL,\n  nama VARCHAR(150) NOT NULL,\n  npsn_sekolah VARCHAR(20) DEFAULT \'ALL\',\n  keterangan TEXT\n);',
      description: 'Menambahkan koleksi hari_libur di database Anda agar fitur penyesuaian kalender libur dapat tersimpan secara online.'
    },
    {
      label: 'Tampilkan Semua Login',
      query: 'SELECT * FROM login;',
      description: 'Membaca seluruh baris akun administrator dalam tabel login'
    },
    {
      label: 'Tampilkan Semua Sekolah',
      query: 'SELECT * FROM sekolah;',
      description: 'Membaca seluruh data daftar sekolah'
    },
    {
      label: 'Cari Akun Admin Tertentu',
      query: "SELECT email, role, npsn_sekolah FROM login WHERE email = 'admin.smkn1@xdegan.com';",
      description: 'Menyaring baris kredensial admin dengan email spesifik'
    },
    {
      label: 'Tambah Akun Admin Baru',
      query: "INSERT INTO login (email, password, role, npsn_sekolah) VALUES ('admin.sman1bogor@xdegan.com', 'admin123', 'admin', '10203040');",
      description: 'Menambahkan akun login baru ke dalam database'
    },
    {
      label: 'Update Password Admin',
      query: "UPDATE login SET password = 'passwordBaru999' WHERE email = 'admin.sman8@xdegan.com';",
      description: 'Memperbarui kata sandi salah satu akun admin sekolah'
    },
    {
      label: 'Hapus Akun Login',
      query: "DELETE FROM login WHERE email = 'admin.sman1bogor@xdegan.com';",
      description: 'Menghapus data akun login berdasarkan alamat email'
    },
  ];

  const handleRunQuery = async () => {
    if (!query.trim()) {
      setErrorMsg('Teks query SQL tidak boleh kosong.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');
    setResults(null);
    setAffectedCount(null);

    try {
      const res = await db.runSql(query);
      if (res.success) {
        if (res.data) {
          setResults(res.data);
          setSuccessMsg(`Kueri berhasil dijalankan! Menemukan ${res.data.length} baris.`);
        } else {
          setSuccessMsg(res.message || 'Kueri SQL berhasil dieksekusi.');
          setAffectedCount(res.message || 'Eksekusi berhasil (0 baris dikembalikan).');
        }
      } else {
        setErrorMsg(res.error || 'Terjadi kesalahan tidak dikenal saat mengeksekusi SQL.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Gagal mengeksekusi perintah SQL.');
    } finally {
      setLoading(false);
    }
  };

  const selectSnippet = (snippet: string) => {
    setQuery(snippet);
    setErrorMsg('');
    setSuccessMsg('');
  };

  const copyRpcToClipboard = () => {
    const rpcSql = `CREATE OR REPLACE FUNCTION exec_sql(sql_query text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  result json;
BEGIN
  EXECUTE 'SELECT json_agg(t) FROM (' || sql_query || ') t' INTO result;
  RETURN result;
EXCEPTION WHEN OTHERS THEN
  -- Handle non-SELECT statements (INSERT/UPDATE/DELETE) or errors
  BEGIN
    EXECUTE sql_query;
    RETURN json_build_object('success', true, 'message', 'Command executed successfully');
  EXCEPTION WHEN OTHERS THEN
    RETURN json_build_object('success', false, 'error', SQLERRM);
  END;
END;
$$;`;

    navigator.clipboard.writeText(rpcSql);
    setCopiedText(true);
    setTimeout(() => setCopiedText(false), 3000);
  };

  // Helper function to export current output as JSON file
  const handleExportJson = () => {
    if (!results || results.length === 0) return;
    const blob = new Blob([JSON.stringify(results, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `sql_results_${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-8 animate-fadeIn">
      {/* Top Title Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-800 flex items-center">
            <Terminal className="w-5 h-5 mr-2 text-indigo-600 shrink-0" />
            SQL Query Terminal & Database Manager
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Eksekusi kueri SQL interaktif ke tabel database login, sekolah, siswa, dan absensi
          </p>
        </div>
        <div className="flex items-center space-x-2 text-[10px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-700 px-3 py-1.5 rounded-full border border-indigo-100">
          <Sparkles className="w-3.5 h-3.5" />
          <span>Double-Engine SQL Sandbox Enabled</span>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
        
        {/* Left 2 Columns: Query Workspace & Output results */}
        <div className="xl:col-span-2 space-y-6">
          
          {/* SQL Editor Area */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
            {/* Editor Top Bar */}
            <div className="bg-slate-950 px-4 py-3 border-b border-slate-800/80 flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <span className="w-3 h-3 bg-rose-500 rounded-full"></span>
                <span className="w-3 h-3 bg-amber-500 rounded-full"></span>
                <span className="w-3 h-3 bg-emerald-500 rounded-full"></span>
                <span className="text-[10px] font-mono text-slate-400 font-bold tracking-wider uppercase ml-2">
                  interactive_sql_terminal.sql
                </span>
              </div>
              <span className="text-[9px] bg-indigo-500/10 text-indigo-300 font-bold px-2 py-0.5 rounded border border-indigo-500/20 font-mono">
                PostgreSQL
              </span>
            </div>

            {/* Code Field */}
            <div className="relative">
              <textarea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full h-48 bg-slate-950 p-5 font-mono text-xs text-indigo-200 focus:outline-hidden focus:ring-0 leading-relaxed placeholder-slate-700 select-all"
                placeholder="-- Tulis kueri SQL Anda di sini, lalu klik 'Jalankan Query'..."
              />
            </div>

            {/* Run Query Actions Bar */}
            <div className="bg-slate-950 px-4 py-3 border-t border-slate-800/60 flex items-center justify-between flex-wrap gap-2">
              <div className="text-[11px] text-slate-500">
                Tulis query tunggal (misal: <code className="text-slate-300 font-mono">SELECT * FROM login</code>)
              </div>
              <div className="flex items-center space-x-2">
                <button
                  type="button"
                  onClick={() => { setQuery(''); setErrorMsg(''); setSuccessMsg(''); setResults(null); }}
                  className="px-3 py-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg text-xs font-semibold transition"
                >
                  Clear
                </button>
                <button
                  type="button"
                  onClick={handleRunQuery}
                  disabled={loading}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 active:scale-95 disabled:opacity-50 text-white rounded-xl text-xs font-bold flex items-center shadow-lg shadow-indigo-600/15 transition-all"
                >
                  {loading ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                      Mengeksekusi...
                    </>
                  ) : (
                    <>
                      <Play className="w-3.5 h-3.5 mr-1.5 fill-current" />
                      Jalankan Query
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* Preset SQL Snippets Selection */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md">
            <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3.5 flex items-center">
              <Sparkles className="w-4 h-4 mr-1.5 text-amber-500" />
              Template Kueri SQL Uji Coba (Quick Click)
            </h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {PRESET_SNIPPETS.map((sn, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => selectSnippet(sn.query)}
                  className={`p-3 text-left rounded-xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 hover:border-slate-300/80 hover:shadow-xs transition duration-200 group flex items-start space-x-3.5 ${
                    query === sn.query ? 'border-indigo-500/40 bg-indigo-50/10' : ''
                  }`}
                >
                  <div className="p-1.5 bg-white border border-slate-200/60 rounded-lg text-slate-500 group-hover:text-indigo-600 transition shrink-0 mt-0.5">
                    <ChevronRight className="w-3.5 h-3.5" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-[11px] font-bold text-slate-800 truncate">{sn.label}</p>
                    <p className="text-[10px] text-slate-500 font-mono truncate mt-0.5">{sn.query}</p>
                    <p className="text-[9px] text-slate-400 mt-1">{sn.description}</p>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Diagnostics Error Alert / Instructions */}
          {errorMsg && (
            <div className="bg-rose-50 border border-rose-100 p-5 rounded-2xl text-rose-800 space-y-3.5 animate-fadeIn">
              <div className="flex items-start space-x-2.5">
                <AlertCircle className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
                <div>
                  <h4 className="text-xs font-bold uppercase tracking-wider text-rose-900">Eksekusi Gagal / RPC Error</h4>
                  <p className="text-xs text-rose-700 mt-1 leading-relaxed">{errorMsg}</p>
                </div>
              </div>
              
              {/* Firestore and Sandboxed Simulator Help Box */}
              {errorMsg.includes('not recognized') && (
                <div className="bg-slate-915 text-slate-300 p-4 rounded-xl border border-slate-800 mt-2 space-y-2 font-sans">
                  <div className="flex items-center text-xs text-indigo-400 font-bold">
                    <span>💡 Mode Simulator Sandbox Database NoSQL (Firebase Firestore)</span>
                  </div>
                  <p className="text-[10px] text-slate-400 leading-relaxed">
                    Karena Firebase Firestore menggunakan database dokumen berjenis NoSQL, string kueri SQL mentah (seperti ALTER TABLE atau CREATE TABLE) tidak didukung secara langsung di server cloud.
                  </p>
                  <div className="text-[9px] text-slate-500 leading-relaxed">
                    Terminal ini secara otomatis mensimulasikan kueri <b>SELECT</b> untuk memudahkan Anda menganalisis dan memantau isi koleksi dokumen secara lokal maupun online. Koleksi yang didukung: <code className="text-indigo-300">sekolah</code>, <code className="text-indigo-300">siswa</code>, <code className="text-indigo-300">kehadiran</code>, <code className="text-indigo-300">app_broadcast</code>, <code className="text-indigo-300">login</code>, <code className="text-indigo-300">hari_libur</code>.
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Success / Result View Panel */}
          {(successMsg || results) && (
            <div className="bg-white p-6 rounded-2xl border border-slate-100 shadow-md space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                <div>
                  <h4 className="text-sm font-bold text-slate-800 flex items-center">
                    <Table className="w-4 h-4 mr-2 text-emerald-500 shrink-0" />
                    Hasil Eksekusi Kueri
                  </h4>
                  {successMsg && <p className="text-xs text-emerald-600 mt-0.5 font-medium">{successMsg}</p>}
                </div>
                {results && results.length > 0 && (
                  <button
                    type="button"
                    onClick={handleExportJson}
                    className="px-3 py-1.5 bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 rounded-lg text-xs font-bold flex items-center transition"
                  >
                    <FileJson className="w-3.5 h-3.5 mr-1.5 text-amber-500" />
                    Export JSON
                  </button>
                )}
              </div>

              {/* Affected Message (INSERT/UPDATE/DELETE) */}
              {affectedCount && (
                <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl font-mono text-xs text-slate-600 whitespace-pre-wrap">
                  {affectedCount}
                </div>
              )}

              {/* Data Table */}
              {results && results.length > 0 ? (
                <div className="overflow-x-auto border border-slate-100 rounded-xl max-h-96 overflow-y-auto">
                  <table className="min-w-full divide-y divide-slate-100 text-xs">
                    <thead className="bg-slate-50 sticky top-0">
                      <tr>
                        {Object.keys(results[0]).map((key) => (
                          <th
                            key={key}
                            scope="col"
                            className="px-4 py-3 text-left font-bold text-slate-500 uppercase tracking-wider font-mono text-[10px]"
                          >
                            {key}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {results.map((row, idx) => (
                        <tr key={idx} className="hover:bg-slate-50/50 transition font-mono text-[11px] text-slate-700">
                          {Object.keys(row).map((key) => {
                            const cellValue = row[key];
                            return (
                              <td key={key} className="px-4 py-2.5 max-w-xs truncate">
                                {cellValue === null ? (
                                  <span className="text-slate-300 italic font-sans text-[10px]">null</span>
                                ) : typeof cellValue === 'boolean' ? (
                                  cellValue ? (
                                    <span className="bg-emerald-50 text-emerald-600 text-[9px] px-1.5 py-0.5 rounded font-bold uppercase border border-emerald-500/10">TRUE</span>
                                  ) : (
                                    <span className="bg-slate-50 text-slate-400 text-[9px] px-1.5 py-0.5 rounded font-bold uppercase border border-slate-200">FALSE</span>
                                  )
                                ) : (
                                  String(cellValue)
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : results && (
                <div className="p-8 text-center bg-slate-50 border border-slate-100 rounded-xl text-xs text-slate-400">
                  Kueri berhasil dijalankan, tetapi tidak ada baris data yang dikembalikan.
                </div>
              )}
            </div>
          )}

        </div>

        {/* Right 1 Column: Schema Guide & Tables Helper */}
        <div className="space-y-6">
          
          {/* Table Schema Catalog */}
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl text-slate-300 space-y-4 shadow-md">
            <div className="flex items-center space-x-2 pb-3 border-b border-slate-800">
              <Database className="w-5 h-5 text-indigo-400 shrink-0" />
              <h4 className="text-xs font-bold uppercase tracking-wider text-white">Katalog Skema Database</h4>
            </div>
            
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Berikut adalah peta skema relasional tabel dalam sistem absensi X-Degan QR yang dapat Anda kueri langsung:
            </p>

            <div className="space-y-4 overflow-y-auto max-h-[600px] pr-2 scrollbar-thin">
              {TABLES_SCHEMA.map((schema) => (
                <div key={schema.name} className="bg-slate-950 p-4 border border-slate-800/80 rounded-xl space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-bold text-sky-400 underline decoration-indigo-500/40 underline-offset-4">
                      {schema.name}
                    </span>
                    <span className="text-[9px] bg-indigo-500/10 text-indigo-300 px-1.5 py-0.5 rounded font-bold font-mono">
                      TABLE
                    </span>
                  </div>
                  <p className="text-[10px] text-slate-400 leading-normal">{schema.desc}</p>
                  
                  <div className="border-t border-slate-900 pt-2 space-y-1.5">
                    <p className="text-[9px] uppercase font-bold text-slate-500 tracking-wider">Struktur Kolom:</p>
                    <div className="space-y-1">
                      {schema.cols.map((col) => (
                        <div key={col.name} className="flex flex-col text-[10px] border-b border-slate-900/50 pb-1 last:border-0">
                          <div className="flex items-center justify-between font-mono">
                            <span className="text-indigo-200 font-bold">{col.name}</span>
                            <span className="text-slate-500 font-mono text-[9px]">{col.type}</span>
                          </div>
                          <span className="text-[9px] text-slate-400 font-sans mt-0.5">{col.desc}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Quick instructions guide card */}
          <div className="bg-indigo-950/40 border border-indigo-900/40 p-5 rounded-2xl text-indigo-200 space-y-3">
            <div className="flex items-center space-x-2 text-sky-400">
              <Info className="w-4 h-4 shrink-0" />
              <h5 className="text-xs font-bold uppercase tracking-wider">Panduan SQL</h5>
            </div>
            <p className="text-[11px] leading-relaxed text-indigo-300">
              Sandbox ini dilengkapi dengan parser SQL lokal yang andal. Anda dapat menyisipkan akun login baru atau memperbarui status absensi secara instan.
            </p>
            <p className="text-[11px] leading-relaxed text-indigo-300">
              Perubahan pada tabel lokal akan langsung terlihat di data absensi, siswa, dan panel login browser Anda.
            </p>
          </div>

        </div>

      </div>
    </div>
  );
};
