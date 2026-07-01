import React, { useEffect, useState, useRef } from 'react';
import { db } from '../firebaseClient';
import { Student, School } from '../types';
import { useAuth } from '../context/AuthContext';
import * as XLSX from 'xlsx';
import { QRCodeSVG } from 'qrcode.react';
import { 
  Plus, 
  Search, 
  Trash2, 
  Edit3, 
  FileUp, 
  FileDown, 
  Lock, 
  AlertTriangle, 
  Check, 
  X, 
  Upload, 
  Download,
  Printer,
  ChevronRight,
  FileSpreadsheet,
  QrCode
} from 'lucide-react';

interface SiswaViewProps {
  selectedNpsn: string;
  schools: School[];
}

export const SiswaView: React.FC<SiswaViewProps> = ({ selectedNpsn, schools }) => {
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedKelas, setSelectedKelas] = useState<string>('Semua');
  const { user } = useAuth();

  useEffect(() => {
    if (user?.role === 'guru' && user.kelas_tugas) {
      setSelectedKelas(user.kelas_tugas);
    } else {
      setSelectedKelas('Semua');
    }
  }, [user]);

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState<boolean>(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState<boolean>(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState<boolean>(false);
  const [activeStudent, setActiveStudent] = useState<Student | null>(null);

  // Selection states & Print Workspace states
  const [selectedStudentIds, setSelectedStudentIds] = useState<string[]>([]);
  const [isPrintQrModalOpen, setIsPrintQrModalOpen] = useState<boolean>(false);
  const [cardTheme, setCardTheme] = useState<'blue' | 'amber' | 'dark' | 'minimal'>('blue');
  const [cardSize, setCardSize] = useState<'sm' | 'md' | 'lg'>('md');
  const [showSchoolName, setShowSchoolName] = useState<boolean>(true);
  const [showClass, setShowClass] = useState<boolean>(true);
  const [showNisnText, setShowNisnText] = useState<boolean>(true);
  const [customHeaderTitle, setCustomHeaderTitle] = useState<string>('KARTU ABSENSI SISWA');

  // Form states
  const [formNama, setFormNama] = useState<string>('');
  const [formNisn, setFormNisn] = useState<string>('');
  const [formKelas, setFormKelas] = useState<string>('');
  const [formNpsn, setFormNpsn] = useState<string>('');

  // CSV / Excel Import State
  const [importTab, setImportTab] = useState<'csv' | 'excel'>('excel');
  const [isDragActive, setIsDragActive] = useState<boolean>(false);
  const [csvText, setCsvText] = useState<string>('');
  const [importPreview, setImportPreview] = useState<any[]>([]);
  const [importStatus, setImportStatus] = useState<string>('');
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isDefaultSchool = selectedNpsn === 'SCH-DEFAULT';
  const activeSchool = schools.find(s => s.npsn === selectedNpsn);

  useEffect(() => {
    loadStudents();
    setSelectedStudentIds([]); // Reset selection when NPSN changes
  }, [selectedNpsn]);

  const loadStudents = async () => {
    setLoading(true);
    try {
      const data = await db.getStudents(selectedNpsn);
      setStudents(data);
    } catch (e) {
      console.error("Gagal memuat data siswa", e);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenAddModal = () => {
    if (isDefaultSchool) return;
    setFormNama('');
    setFormNisn('');
    setFormKelas('');
    
    if (selectedNpsn === 'ALL') {
      const firstReal = schools.find(s => s.npsn !== 'SCH-DEFAULT');
      setFormNpsn(firstReal ? firstReal.npsn : '');
    } else {
      setFormNpsn(selectedNpsn);
    }
    
    setIsAddModalOpen(true);
  };

  const handleOpenEditModal = (student: Student) => {
    if (isDefaultSchool) return;
    setActiveStudent(student);
    setFormNama(student.nama);
    setFormNisn(student.nisn);
    setFormKelas(student.kelas);
    setIsEditModalOpen(true);
  };

  const handleAddSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formNama || !formNisn || !formKelas || !formNpsn) {
      alert("Semua field wajib diisi");
      return;
    }
    try {
      await db.addStudent({
        nama: formNama,
        nisn: formNisn,
        kelas: formKelas,
        npsn_sekolah: formNpsn
      });
      setIsAddModalOpen(false);
      loadStudents();
    } catch (err) {
      console.error(err);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeStudent) return;
    if (!formNama || !formNisn || !formKelas) {
      alert("Semua field wajib diisi");
      return;
    }
    try {
      await db.updateStudent(activeStudent.id, {
        nama: formNama,
        nisn: formNisn,
        kelas: formKelas
      });
      setIsEditModalOpen(false);
      loadStudents();
    } catch (err) {
      console.error(err);
    }
  };

  const toggleSelectStudent = (id: string) => {
    setSelectedStudentIds(prev => 
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    );
  };

  const toggleSelectAllFiltered = () => {
    const filteredIds = filteredStudents.map(s => s.id);
    const allSelected = filteredIds.every(id => selectedStudentIds.includes(id));
    
    if (allSelected) {
      // Deselect all filtered
      setSelectedStudentIds(prev => prev.filter(id => !filteredIds.includes(id)));
    } else {
      // Select all filtered
      setSelectedStudentIds(prev => {
        const unique = new Set([...prev, ...filteredIds]);
        return Array.from(unique);
      });
    }
  };

  const handleDelete = async (id: string, nama: string) => {
    if (isDefaultSchool) return;
    if (confirm(`Apakah Anda yakin ingin menghapus siswa "${nama}"?`)) {
      try {
        await db.deleteStudent(id);
        loadStudents();
      } catch (err) {
        console.error(err);
      }
    }
  };

  // Excel template downloader using XLSX
  const downloadExcelTemplate = () => {
    const wsData = [
      ["Nama", "NISN", "Kelas"],
      ["Muhammad Haikal", "0095566771", "XI RPL 1"],
      ["Syafira Putri", "0095566772", "XI RPL 1"],
      ["Christian Wijaya", "0102233441", "X TKJ 2"],
      ["Nabila Anggraini", "0081122331", "XII MM 3"]
    ];
    
    const ws = XLSX.utils.aoa_to_sheet(wsData);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Daftar Siswa");
    XLSX.writeFile(wb, "template_import_siswa.xlsx");
  };

  const handleExcelUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    parseExcelFile(file);
  };

  const parseExcelFile = (file: File) => {
    if (!file.name.match(/\.(xlsx|xls)$/i)) {
      setImportStatus('Gagal: Berkas harus berupa file Excel (.xlsx atau .xls)');
      setImportPreview([]);
      return;
    }

    const reader = new FileReader();
    reader.onload = (evt) => {
      try {
        const bstr = evt.target?.result;
        const wb = XLSX.read(bstr, { type: 'binary' });
        const wsname = wb.SheetNames[0];
        const ws = wb.Sheets[wsname];
        const data = XLSX.utils.sheet_to_json(ws, { header: 1 }) as any[][];
        
        const parsed: any[] = [];
        let startIndex = 0;
        let nameColIndex = 0;
        let nisnColIndex = 1;
        let kelasColIndex = 2;
        
        if (data.length > 0) {
          // Look for headers
          const firstRow = data[0].map(h => String(h || '').toLowerCase().trim());
          const hasHeader = firstRow.some(h => h.includes('nama') || h.includes('nisn') || h.includes('kelas') || h.includes('name'));
          
          if (hasHeader) {
            startIndex = 1;
            const nameIdx = firstRow.findIndex(h => h.includes('nama') || h.includes('name'));
            const nisnIdx = firstRow.findIndex(h => h.includes('nisn') || h.includes('nis') || h.includes('uid'));
            const kelasIdx = firstRow.findIndex(h => h.includes('kelas') || h.includes('class') || h.includes('grade'));
            
            if (nameIdx !== -1) nameColIndex = nameIdx;
            if (nisnIdx !== -1) nisnColIndex = nisnIdx;
            if (kelasIdx !== -1) kelasColIndex = kelasIdx;
          }
        }
        
        for (let i = startIndex; i < data.length; i++) {
          const row = data[i];
          if (!row || row.length === 0) continue;
          
          const nama = String(row[nameColIndex] || '').trim();
          const nisn = String(row[nisnColIndex] || '').trim().replace(/\D/g, '');
          const kelas = String(row[kelasColIndex] || '').trim();
          
          if (nama && nisn && kelas) {
            parsed.push({ nama, nisn, kelas, npsn_sekolah: selectedNpsn });
          }
        }
        
        if (parsed.length === 0) {
          setImportStatus('Tidak ada data valid yang bisa diekstrak dari berkas Excel. Pastikan terdapat kolom Nama, NISN, dan Kelas.');
          setImportPreview([]);
        } else {
          setImportPreview(parsed);
          setImportStatus(`Berhasil mendeteksi ${parsed.length} siswa siap diimport dari berkas Excel!`);
        }
      } catch (err: any) {
        console.error(err);
        setImportStatus('Gagal membaca berkas Excel: ' + err.message);
        setImportPreview([]);
      }
    };
    reader.readAsBinaryString(file);
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setIsDragActive(true);
    } else if (e.type === "dragleave") {
      setIsDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      parseExcelFile(file);
    }
  };

  // CSV Parse Handler
  const handleParseCsv = () => {
    if (!csvText.trim()) {
      setImportStatus('Harap masukkan text CSV atau copy dari excel.');
      return;
    }

    const lines = csvText.split('\n');
    const parsed: any[] = [];
    
    // Parse line by line
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      // split by comma or semicolon
      const parts = line.split(/[;,]/);
      if (parts.length >= 3) {
        const nama = parts[0].replace(/['"]/g, '').trim();
        const nisn = parts[1].replace(/['"]/g, '').trim();
        const kelas = parts[2].replace(/['"]/g, '').trim();
        
        if (nama && nisn && kelas) {
          parsed.push({ nama, nisn, kelas, npsn_sekolah: selectedNpsn });
        }
      }
    }

    if (parsed.length === 0) {
      setImportStatus('Format salah atau tidak ada data valid yang bisa diekstrak.');
      setImportPreview([]);
    } else {
      setImportPreview(parsed);
      setImportStatus(`Berhasil mendeteksi ${parsed.length} siswa siap diimport!`);
    }
  };

  const handleLoadSampleCsv = () => {
    const sample = `Muhammad Haikal;0095566771;XI RPL 1
Syafira Putri;0095566772;XI RPL 1
Christian Wijaya;0102233441;X TKJ 2
Nabila Anggraini;0081122331;XII MM 3`;
    setCsvText(sample);
    setImportStatus('Sample data termuat. Klik "Proses Data" untuk memvalidasi.');
  };

  const handleImportSubmit = async () => {
    if (importPreview.length === 0) return;
    try {
      await db.importStudents(importPreview);
      setIsImportModalOpen(false);
      setCsvText('');
      setImportPreview([]);
      setImportStatus('');
      loadStudents();
    } catch (e) {
      console.error(e);
      setImportStatus('Gagal menyimpan data ke database.');
    }
  };

  // PDF / Print Report Generator
  const handleExportPDF = () => {
    if (isDefaultSchool) return;
    
    // Create a temporary beautiful printable document window
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      alert("Browser pop-up diblokir. Izinkan pop-up untuk mencetak PDF laporan.");
      return;
    }

    const today = new Date().toLocaleDateString('id-ID', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
    const tableRows = filteredStudents.map((s, idx) => `
      <tr style="border-bottom: 1px solid #e2e8f0;">
        <td style="padding: 10px; text-align: center;">${idx + 1}</td>
        <td style="padding: 10px; font-weight: bold; color: #1e293b;">${s.nama}</td>
        <td style="padding: 10px; font-family: monospace;">${s.nisn}</td>
        <td style="padding: 10px;">${s.kelas}</td>
        <td style="padding: 10px;">${selectedNpsn}</td>
      </tr>
    `).join('');

    printWindow.document.write(`
      <html>
        <head>
          <title>Daftar Siswa - ${activeSchool?.nama || 'Sekolah'}</title>
          <style>
            body { font-family: 'Inter', sans-serif; padding: 40px; color: #334155; }
            .header { border-bottom: 3px double #cbd5e1; padding-bottom: 20px; margin-bottom: 30px; text-align: center; }
            .school-title { font-size: 24px; font-weight: 800; color: #1e293b; margin: 0; }
            .school-meta { font-size: 13px; color: #64748b; margin: 5px 0 0 0; }
            .report-title { font-size: 16px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; color: #4f46e5; margin: 30px 0 15px 0; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th { background-color: #f1f5f9; padding: 12px 10px; font-size: 12px; font-weight: bold; text-align: left; color: #475569; text-transform: uppercase; border-bottom: 2px solid #cbd5e1; }
            .footer { margin-top: 50px; display: flex; justify-content: space-between; font-size: 12px; color: #64748b; }
            .signature { width: 200px; text-align: center; margin-top: 40px; }
            .stamp { height: 80px; }
            @media print {
              .no-print { display: none; }
            }
          </style>
        </head>
        <body>
          <div class="header">
            <h1 class="school-title">${activeSchool?.nama || 'X-DEGAN QR SCHOOL'}</h1>
            <p class="school-meta">NPSN: ${selectedNpsn} | Alamat: ${activeSchool?.alamat || 'Alamat Sekolah'}</p>
          </div>
          
          <h2 class="report-title">Daftar Anggota / Siswa Aktif</h2>
          <p style="font-size: 12px; color: #64748b; margin-top: -10px;">Hasil Cetak: ${today}</p>

          <table>
            <thead>
              <tr>
                <th style="width: 50px; text-align: center;">No</th>
                <th>Nama Siswa</th>
                <th>NISN</th>
                <th>Kelas</th>
                <th>NPSN Asal</th>
              </tr>
            </thead>
            <tbody>
              ${tableRows}
            </tbody>
          </table>

          <div class="footer">
            <div>
              <p>Dicetak melalui aplikasi: <b>X-Degan QR Multi-Sekolah Web Admin</b></p>
            </div>
            <div class="signature">
              <p>Mengetahui,</p>
              <p style="font-weight: bold; margin-top: 80px;">Kepala Sekolah / Admin</p>
              <p style="font-size: 10px; color: #94a3b8; border-top: 1px solid #cbd5e1; padding-top: 5px;">NPSN: ${selectedNpsn}</p>
            </div>
          </div>
          
          <script>
            window.onload = function() {
              window.print();
            };
          </script>
        </body>
      </html>
    `);
    printWindow.document.close();
  };

  // Filter students based on search and class filter
  const filteredStudents = students.filter(student => {
    const q = searchQuery.toLowerCase();
    const matchesSearch = (
      student.nama.toLowerCase().includes(q) ||
      student.nisn.includes(q) ||
      student.kelas.toLowerCase().includes(q)
    );
    const matchesKelas = selectedKelas === 'Semua' || student.kelas === selectedKelas;
    return matchesSearch && matchesKelas;
  });

  const studentsToPrint = selectedStudentIds.length > 0 
    ? students.filter(s => selectedStudentIds.includes(s.id))
    : filteredStudents;

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Header View Section with actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-800">Manajemen Anggota Siswa</h3>
          <p className="text-xs text-slate-500 mt-0.5">Kelola data murid, edit detail, import CSV, atau export PDF secara real-time</p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap gap-2.5">
          {/* Print QR Cards Button */}
          <button
            id="btn-print-qr-cards"
            onClick={() => setIsPrintQrModalOpen(true)}
            disabled={isDefaultSchool || filteredStudents.length === 0}
            className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl transition-all border shadow-xs ${
              isDefaultSchool || filteredStudents.length === 0
                ? 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed shadow-none'
                : 'bg-indigo-600 hover:bg-indigo-700 text-white border-transparent hover:shadow-lg active:scale-95'
            }`}
            title={isDefaultSchool ? "Pilih NPSN Sekolah valid untuk mencetak kartu QR" : "Cetak Kartu QR Siswa secara massal"}
          >
            <QrCode className="w-4 h-4 mr-2" />
            Cetak Kartu QR {selectedStudentIds.length > 0 ? `(${selectedStudentIds.length})` : '(Semua)'}
          </button>

          {/* Export PDF Button */}
          <button
            id="btn-export-pdf"
            onClick={handleExportPDF}
            disabled={isDefaultSchool || filteredStudents.length === 0}
            className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl transition-all border shadow-xs ${
              isDefaultSchool || filteredStudents.length === 0
                ? 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed'
                : 'bg-white text-indigo-600 border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700 active:scale-95'
            }`}
            title={isDefaultSchool ? "Pilih NPSN Sekolah valid untuk mengunduh laporan PDF" : "Unduh daftar siswa sebagai PDF"}
          >
            <Printer className="w-4 h-4 mr-2" />
            Cetak Laporan / PDF
          </button>

          {/* Import CSV Button */}
          <button
            id="btn-import-csv"
            onClick={() => !isDefaultSchool && setIsImportModalOpen(true)}
            disabled={isDefaultSchool}
            className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl transition-all border shadow-xs ${
              isDefaultSchool
                ? 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed'
                : 'bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100 active:scale-95'
            }`}
          >
            <FileUp className="w-4 h-4 mr-2" />
            Import CSV
          </button>

          {/* Add Student Button */}
          <button
            id="btn-add-student"
            onClick={handleOpenAddModal}
            disabled={isDefaultSchool}
            className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl shadow-md transition-all ${
              isDefaultSchool
                ? 'bg-slate-300 text-slate-500 cursor-not-allowed'
                : 'bg-indigo-600 hover:bg-indigo-700 text-white active:scale-95 hover:shadow-lg'
            }`}
          >
            {isDefaultSchool ? (
              <Lock className="w-4 h-4 mr-2" />
            ) : (
              <Plus className="w-4 h-4 mr-2" />
            )}
            Tambah Siswa
          </button>
        </div>
      </div>

      {/* Warning on default school for writing */}
      {isDefaultSchool && (
        <div className="bg-red-50 border border-red-200/80 p-4 rounded-xl flex items-start space-x-3 text-red-800">
          <AlertTriangle className="w-5 h-5 text-red-600 shrink-0 mt-0.5 animate-pulse" />
          <div className="text-xs">
            <span className="font-bold">Akses Modifikasi Database Terkunci (SCH-DEFAULT)</span>
            <p className="text-red-700 mt-1">
              Untuk melakukan penambahan, pengeditan, atau mengimport daftar siswa, Anda wajib memilih NPSN sekolah yang aktif terlebih dahulu pada dropdown filter di atas.
            </p>
          </div>
        </div>
      )}

      {/* Filter and Search Panel */}
      <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-md flex flex-col md:flex-row gap-4 items-center justify-between">
        <div className="flex flex-col sm:flex-row gap-3 w-full md:max-w-2xl">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4" />
            <input
              id="search-siswa-input"
              type="text"
              placeholder="Cari siswa berdasarkan nama, NISN, atau kelas..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-800 placeholder-slate-400 focus:outline-hidden focus:border-indigo-500 focus:bg-white transition-all"
            />
          </div>

          {/* Filter Kelas Select Dropdown */}
          <div className="relative w-full sm:w-52 shrink-0">
            <select
              id="filter-kelas-select"
              value={selectedKelas}
              onChange={(e) => setSelectedKelas(e.target.value)}
              disabled={user?.role === 'guru'}
              className={`w-full pl-3 pr-8 py-2.5 border rounded-xl text-xs font-semibold appearance-none focus:outline-hidden transition-all ${
                user?.role === 'guru'
                  ? 'bg-amber-50 border-amber-200 text-amber-700 cursor-not-allowed'
                  : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100 focus:border-indigo-500 focus:bg-white'
              }`}
            >
              {user?.role !== 'guru' && <option value="Semua">Semua Kelas</option>}
              {user?.role === 'guru' && user.kelas_tugas && (
                <option value={user.kelas_tugas}>{user.kelas_tugas} (Terkunci)</option>
              )}
              {Array.from(new Set(students.map(s => s.kelas))).filter(Boolean).sort().map((kls) => {
                // If guru, skip render options other than their own class to keep dropdown fully locked to single option
                if (user?.role === 'guru' && kls !== user.kelas_tugas) return null;
                return (
                  <option key={kls} value={kls}>
                    {kls}
                  </option>
                );
              })}
            </select>
            <div className="absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400">
              {user?.role === 'guru' ? (
                <Lock className="w-3.5 h-3.5 text-amber-500" />
              ) : (
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                </svg>
              )}
            </div>
          </div>
        </div>
        <div className="text-xs text-slate-500 whitespace-nowrap shrink-0">
          Menampilkan <span className="font-bold text-slate-800">{filteredStudents.length}</span> dari <span className="font-bold text-slate-800">{students.length}</span> total siswa
        </div>
      </div>

      {/* Selection Status Bar */}
      {selectedStudentIds.length > 0 && (
        <div className="bg-indigo-50 border border-indigo-200/80 p-3.5 rounded-2xl flex items-center justify-between animate-fadeIn">
          <div className="flex items-center space-x-2 text-indigo-800 text-xs">
            <Check className="w-4 h-4 text-indigo-600 bg-indigo-100 rounded-full p-0.5 shrink-0" />
            <span>Terpilih <strong className="font-bold text-indigo-950">{selectedStudentIds.length}</strong> siswa untuk dicetak kartu QR.</span>
          </div>
          <button
            id="btn-clear-selection"
            onClick={() => setSelectedStudentIds([])}
            className="text-xs text-indigo-600 hover:text-indigo-800 font-bold transition-all hover:underline"
          >
            Batal Pilih Semua
          </button>
        </div>
      )}

      {/* Main Student Table */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-md overflow-hidden">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-500">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            <span className="mt-3 text-xs">Memuat daftar anggota siswa...</span>
          </div>
        ) : filteredStudents.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400">
            <AlertTriangle className="w-12 h-12 stroke-1 text-slate-300 mb-2" />
            <p className="text-xs font-semibold text-slate-500">Tidak ada data siswa ditemukan</p>
            <p className="text-[11px] text-slate-400 mt-1">Silakan tambahkan data baru atau sesuaikan filter pencarian.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table id="siswa-data-table" className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/50 border-b border-slate-150 text-xs font-bold text-slate-400 uppercase tracking-wider">
                  <th className="py-4 px-4 text-center w-12">
                    <input
                      type="checkbox"
                      checked={filteredStudents.length > 0 && filteredStudents.every(s => selectedStudentIds.includes(s.id))}
                      onChange={toggleSelectAllFiltered}
                      className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500 cursor-pointer"
                    />
                  </th>
                  <th className="py-4 px-4 text-center w-16">No</th>
                  <th className="py-4 px-6">Nama Lengkap</th>
                  <th className="py-4 px-6">NISN (Kode QR)</th>
                  <th className="py-4 px-6">Kelas</th>
                  <th className="py-4 px-6">NPSN Sekolah</th>
                  <th className="py-4 px-6 text-right w-32">Aksi</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs text-slate-600">
                {filteredStudents.map((student, index) => (
                  <tr key={student.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-4 text-center">
                      <input
                        type="checkbox"
                        checked={selectedStudentIds.includes(student.id)}
                        onChange={() => toggleSelectStudent(student.id)}
                        className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500 cursor-pointer"
                      />
                    </td>
                    <td className="py-3.5 px-4 font-bold text-slate-400 text-center">{index + 1}</td>
                    <td className="py-3.5 px-6">
                      <div className="flex items-center space-x-3">
                        <div className="w-8 h-8 rounded-full bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold text-[11px] uppercase">
                          {student.nama.substring(0, 2)}
                        </div>
                        <span className="font-bold text-slate-800">{student.nama}</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-6">
                      <span className="font-mono text-xs bg-slate-100 text-slate-700 px-2 py-1 rounded-md font-bold">
                        {student.nisn}
                      </span>
                    </td>
                    <td className="py-3.5 px-6 font-medium text-slate-700">{student.kelas}</td>
                    <td className="py-3.5 px-6 font-semibold text-slate-500">{student.npsn_sekolah}</td>
                    <td className="py-3.5 px-6 text-right">
                      <div className="flex items-center justify-end space-x-2">
                        {/* Edit button */}
                        <button
                          id={`btn-edit-student-${student.id}`}
                          onClick={() => handleOpenEditModal(student)}
                          disabled={isDefaultSchool}
                          className={`p-2 rounded-lg border transition-all ${
                            isDefaultSchool
                              ? 'text-slate-300 border-slate-100 cursor-not-allowed'
                              : 'text-slate-500 border-slate-200 hover:text-indigo-600 hover:border-indigo-100 hover:bg-indigo-50/50'
                          }`}
                          title="Ubah data"
                        >
                          <Edit3 className="w-3.5 h-3.5" />
                        </button>

                        {/* Delete button */}
                        <button
                          id={`btn-delete-student-${student.id}`}
                          onClick={() => handleDelete(student.id, student.nama)}
                          disabled={isDefaultSchool}
                          className={`p-2 rounded-lg border transition-all ${
                            isDefaultSchool
                              ? 'text-slate-300 border-slate-100 cursor-not-allowed'
                              : 'text-slate-500 border-slate-200 hover:text-rose-600 hover:border-rose-100 hover:bg-rose-50/50'
                          }`}
                          title="Hapus data"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* --- ADD STUDENT MODAL --- */}
      {isAddModalOpen && (
        <div id="add-student-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-md w-full shadow-2xl overflow-hidden animate-fadeIn">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm">Tambah Siswa Baru</h4>
              <button onClick={() => setIsAddModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleAddSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Nama Lengkap</label>
                <input
                  id="add-nama-input"
                  type="text"
                  required
                  placeholder="Contoh: Muhammad Rafli"
                  value={formNama}
                  onChange={(e) => setFormNama(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">NISN (Kode QR)</label>
                  <input
                    id="add-nisn-input"
                    type="text"
                    required
                    maxLength={12}
                    placeholder="Contoh: 0098765432"
                    value={formNisn}
                    onChange={(e) => setFormNisn(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Kelas</label>
                  <input
                    id="add-kelas-input"
                    type="text"
                    required
                    placeholder="Contoh: XI RPL 1"
                    value={formKelas}
                    onChange={(e) => setFormKelas(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                  />
                </div>
              </div>
              {selectedNpsn === 'ALL' ? (
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">NPSN Sekolah Tujuan</label>
                  <select
                    id="add-school-select"
                    required
                    value={formNpsn}
                    onChange={(e) => setFormNpsn(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-semibold text-slate-800"
                  >
                    {schools.filter(s => s.npsn !== 'SCH-DEFAULT').map((school) => (
                      <option key={school.npsn} value={school.npsn}>
                        {school.npsn} - {school.nama}
                      </option>
                    ))}
                  </select>
                </div>
              ) : (
                <div className="bg-indigo-50 p-3 rounded-xl border border-indigo-100 flex items-center space-x-2.5 text-[11px] text-indigo-700">
                  <Check className="w-4 h-4 text-indigo-500 shrink-0" />
                  <span>Siswa akan otomatis ditautkan ke NPSN Sekolah aktif: <b>{selectedNpsn}</b></span>
                </div>
              )}
              <div className="pt-4 border-t border-slate-100 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md"
                >
                  Simpan Siswa
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- EDIT STUDENT MODAL --- */}
      {isEditModalOpen && (
        <div id="edit-student-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-md w-full shadow-2xl overflow-hidden animate-fadeIn">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm">Ubah Data Siswa</h4>
              <button onClick={() => setIsEditModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>
            <form onSubmit={handleEditSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Nama Lengkap</label>
                <input
                  id="edit-nama-input"
                  type="text"
                  required
                  value={formNama}
                  onChange={(e) => setFormNama(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">NISN</label>
                  <input
                    id="edit-nisn-input"
                    type="text"
                    required
                    value={formNisn}
                    onChange={(e) => setFormNisn(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Kelas</label>
                  <input
                    id="edit-kelas-input"
                    type="text"
                    required
                    value={formKelas}
                    onChange={(e) => setFormKelas(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500"
                  />
                </div>
              </div>
              <div className="pt-4 border-t border-slate-100 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md"
                >
                  Simpan Perubahan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- IMPORT EXCEL/CSV MODAL --- */}
      {isImportModalOpen && (
        <div id="import-student-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-lg w-full shadow-2xl overflow-hidden animate-fadeIn">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm flex items-center">
                <FileUp className="w-4 h-4 mr-2 text-indigo-600" />
                Import Anggota Siswa
              </h4>
              <button onClick={() => setIsImportModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>

            {selectedNpsn === 'ALL' ? (
              <div className="p-8 text-center space-y-4">
                <div className="mx-auto w-12 h-12 rounded-full bg-amber-50 border border-amber-200 text-amber-500 flex items-center justify-center animate-bounce">
                  <AlertTriangle className="w-6 h-6" />
                </div>
                <div className="max-w-md mx-auto space-y-2">
                  <h5 className="text-sm font-bold text-slate-800">Sekolah Aktif Belum Dipilih</h5>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Anda saat ini berada di <span className="font-bold text-indigo-600">Mode Semua Sekolah</span>. Untuk melakukan import data siswa, silakan pilih salah satu sekolah spesifik terlebih dahulu melalui pemilih sekolah di bagian pojok kanan atas halaman.
                  </p>
                </div>
                <div className="pt-4 flex justify-center">
                  <button
                    type="button"
                    onClick={() => setIsImportModalOpen(false)}
                    className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl transition-all active:scale-95"
                  >
                    Tutup
                  </button>
                </div>
              </div>
            ) : (
              <>
                {/* Tab navigation inside Modal */}
                <div className="flex border-b border-slate-250 bg-slate-50/50">
                  <button
                    id="btn-tab-excel"
                    type="button"
                    onClick={() => { setImportTab('excel'); setImportStatus(''); setImportPreview([]); }}
                    className={`flex-1 py-3 text-xs font-bold border-b-2 text-center transition-all ${
                      importTab === 'excel'
                        ? 'border-indigo-600 text-indigo-600 bg-white'
                        : 'border-transparent text-slate-400 hover:text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    Unggah Berkas Excel (.xlsx, .xls)
                  </button>
                  <button
                    id="btn-tab-csv"
                    type="button"
                    onClick={() => { setImportTab('csv'); setImportStatus(''); setImportPreview([]); }}
                    className={`flex-1 py-3 text-xs font-bold border-b-2 text-center transition-all ${
                      importTab === 'csv'
                        ? 'border-indigo-600 text-indigo-600 bg-white'
                        : 'border-transparent text-slate-400 hover:text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    Salin & Tempel Teks CSV
                  </button>
                </div>

                <div className="p-6 space-y-4">
                  {importTab === 'excel' ? (
                    /* EXCEL UPLOAD TAB CONTENT */
                    <div className="space-y-4">
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-slate-500">Mendukung format file <strong className="text-slate-700">.xlsx / .xls</strong></span>
                        <button
                          id="btn-download-template"
                          type="button"
                          onClick={downloadExcelTemplate}
                          className="flex items-center text-[11px] font-bold text-indigo-600 hover:text-indigo-800"
                        >
                          <Download className="w-3.5 h-3.5 mr-1" />
                          Unduh Template Excel
                        </button>
                      </div>
                      
                      <div
                        id="excel-dropzone"
                        onDragEnter={handleDrag}
                        onDragOver={handleDrag}
                        onDragLeave={handleDrag}
                        onDrop={handleDrop}
                        onClick={() => fileInputRef.current?.click()}
                        className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all flex flex-col items-center justify-center space-y-2.5 ${
                          isDragActive
                            ? 'border-indigo-500 bg-indigo-50/50'
                            : 'border-slate-200 hover:border-indigo-400 hover:bg-slate-50/50'
                        }`}
                      >
                        <input
                          type="file"
                          ref={fileInputRef}
                          onChange={handleExcelUpload}
                          accept=".xlsx, .xls"
                          className="hidden"
                        />
                        <FileSpreadsheet className="w-10 h-10 text-indigo-500 stroke-1" />
                        <div className="text-xs font-bold text-slate-700">
                          Seret & taruh berkas Excel di sini, atau <span className="text-indigo-600">pilih dari komputer</span>
                        </div>
                        <div className="text-[10px] text-slate-400 leading-normal max-w-sm mx-auto">
                          Pastikan tabel Excel Anda memiliki 3 kolom pertama: <strong>Nama</strong>, <strong>NISN</strong>, dan <strong>Kelas</strong>.
                        </div>
                      </div>
                    </div>
                  ) : (
                    /* CSV PASTE TAB CONTENT */
                    <div className="space-y-4">
                      <p className="text-xs text-slate-500 leading-relaxed">
                        Salin baris data tabel siswa dari Excel, lalu paste di bawah. Use pembatas titik koma (<code className="bg-slate-100 px-1 py-0.5 rounded-sm">;</code>) atau koma (<code className="bg-slate-100 px-1 py-0.5 rounded-sm">,</code>) dengan susunan:
                      </p>
                      <div className="bg-slate-900 text-slate-300 p-2.5 rounded-lg font-mono text-[10px] text-center border border-slate-800">
                        [NAMA LENGKAP] ; [NISN] ; [KELAS]
                      </div>

                      <div>
                        <div className="flex justify-between items-center mb-1.5">
                          <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">Tempel Teks CSV</label>
                          <button 
                            onClick={handleLoadSampleCsv}
                            className="text-[10px] font-bold text-indigo-600 hover:text-indigo-800"
                          >
                            + Masukkan Sample Data
                          </button>
                        </div>
                        <textarea
                          id="csv-textarea"
                          rows={4}
                          placeholder="Contoh:&#10;Ahmad Dahlan;0089123441;XII TKJ 2&#10;Bella Safira;0089123442;XII TKJ 2"
                          value={csvText}
                          onChange={(e) => setCsvText(e.target.value)}
                          className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-mono"
                        ></textarea>
                      </div>
                    </div>
                  )}

                  {importStatus && (
                    <div className={`p-3 rounded-xl text-[11px] font-semibold border ${
                      importStatus.includes('Gagal') || importStatus.includes('Format salah') || importStatus.includes('masukkan text') || importStatus.includes('Tidak ada data')
                        ? 'bg-rose-50 text-rose-700 border-rose-100'
                        : 'bg-emerald-50 text-emerald-700 border-emerald-100'
                    }`}>
                      {importStatus}
                    </div>
                  )}

                  {/* Parsed List Preview */}
                  {importPreview.length > 0 && (
                    <div className="border border-slate-150 rounded-xl overflow-hidden">
                      <div className="bg-slate-50 px-3 py-1.5 border-b border-slate-150 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                        Pratinjau Data Impor ({importPreview.length} Murid)
                      </div>
                      <div className="max-h-28 overflow-y-auto divide-y divide-slate-100 text-[11px]">
                        {importPreview.map((item, index) => (
                          <div key={index} className="px-3 py-1.5 flex justify-between items-center bg-white hover:bg-slate-50">
                            <span className="font-bold text-slate-800">{item.nama}</span>
                            <div className="flex space-x-3 text-slate-500">
                              <span className="font-mono">NISN: {item.nisn}</span>
                              <span>Kelas: {item.kelas}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="pt-4 border-t border-slate-100 flex justify-between items-center">
                    {importTab === 'csv' ? (
                      <button
                        type="button"
                        onClick={handleParseCsv}
                        className="px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold border border-slate-200 transition-all active:scale-95"
                      >
                        Proses Data Teks
                      </button>
                    ) : (
                      <div className="text-[11px] text-slate-400 font-medium">
                        Berkas diuraikan otomatis
                      </div>
                    )}
                    <div className="flex space-x-2">
                      <button
                        type="button"
                        onClick={() => setIsImportModalOpen(false)}
                        className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 transition-all active:scale-95"
                      >
                        Batal
                      </button>
                      <button
                        type="button"
                        onClick={handleImportSubmit}
                        disabled={importPreview.length === 0}
                        className={`px-4 py-2 rounded-xl text-xs font-bold shadow-md transition-all ${
                          importPreview.length === 0
                            ? 'bg-slate-300 text-slate-500 cursor-not-allowed shadow-none'
                            : 'bg-indigo-600 hover:bg-indigo-700 text-white active:scale-95'
                        }`}
                      >
                        Simpan dan Import
                      </button>
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* --- BULK QR CARD PRINTING WORKSPACE --- */}
      {isPrintQrModalOpen && (
        <div id="print-qr-modal" className="fixed inset-0 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4 z-50 overflow-y-auto">
          <div className="bg-slate-50 rounded-3xl border border-slate-200 max-w-6xl w-full h-[90vh] shadow-2xl overflow-hidden flex flex-col animate-fadeIn">
            
            {/* Modal Header */}
            <div className="px-6 py-4 bg-white border-b border-slate-200 flex items-center justify-between shrink-0">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold">
                  <QrCode className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="font-bold text-slate-800 text-sm">Workspace Cetak Kartu QR Massal</h4>
                  <p className="text-[11px] text-slate-500">Sesuaikan tata letak, tema, dan pratinjau kartu sebelum dicetak langsung ke printer</p>
                </div>
              </div>
              <button 
                onClick={() => setIsPrintQrModalOpen(false)} 
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-xl transition-all"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Content - Two Panel Layout */}
            <div className="flex-1 overflow-hidden flex flex-col md:flex-row">
              
              {/* Left Panel: Configuration Controls */}
              <div className="w-full md:w-80 bg-white border-r border-slate-200 p-6 overflow-y-auto shrink-0 space-y-6">
                
                {/* Information Badge */}
                <div className="bg-indigo-50/70 border border-indigo-100 p-3.5 rounded-2xl space-y-1">
                  <span className="text-[10px] font-bold text-indigo-500 uppercase tracking-wider">Sumber Data</span>
                  <p className="text-xs text-indigo-950 font-bold leading-tight">
                    Mencetak {studentsToPrint.length} Kartu QR Siswa
                  </p>
                  <p className="text-[10px] text-indigo-700 leading-normal">
                    {selectedStudentIds.length > 0 
                      ? "Menggunakan siswa yang Anda centang secara manual di tabel." 
                      : `Menggunakan semua ${filteredStudents.length} siswa hasil filter saat ini.`}
                  </p>
                </div>

                {/* Header Title Input */}
                <div className="space-y-2">
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">Judul Atas Kartu</label>
                  <input
                    type="text"
                    value={customHeaderTitle}
                    onChange={(e) => setCustomHeaderTitle(e.target.value)}
                    placeholder="Contoh: KARTU ABSENSI SISWA"
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 font-semibold"
                  />
                </div>

                {/* Theme Selector */}
                <div className="space-y-3">
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">Tema & Desain Kartu</label>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => setCardTheme('blue')}
                      className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        cardTheme === 'blue'
                          ? 'border-indigo-600 bg-indigo-50/50 text-indigo-950 font-bold'
                          : 'border-slate-200 hover:bg-slate-50 text-slate-600'
                      }`}
                    >
                      <span className="w-4 h-4 rounded-full bg-indigo-600 mb-1.5 shadow-xs"></span>
                      <span className="text-[11px] font-semibold leading-tight">Biru Edukasi</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setCardTheme('amber')}
                      className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        cardTheme === 'amber'
                          ? 'border-amber-500 bg-amber-50/40 text-amber-950 font-bold'
                          : 'border-slate-200 hover:bg-slate-50 text-slate-600'
                      }`}
                    >
                      <span className="w-4 h-4 rounded-full bg-amber-500 mb-1.5 shadow-xs"></span>
                      <span className="text-[11px] font-semibold leading-tight">Kuning Ceria</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setCardTheme('dark')}
                      className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        cardTheme === 'dark'
                          ? 'border-slate-800 bg-slate-100 text-slate-900 font-bold'
                          : 'border-slate-200 hover:bg-slate-50 text-slate-600'
                      }`}
                    >
                      <span className="w-4 h-4 rounded-full bg-slate-900 mb-1.5 shadow-xs"></span>
                      <span className="text-[11px] font-semibold leading-tight">Hitam Premium</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setCardTheme('minimal')}
                      className={`flex flex-col items-start p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        cardTheme === 'minimal'
                          ? 'border-slate-900 bg-slate-50 text-slate-950 font-bold'
                          : 'border-slate-200 hover:bg-slate-50 text-slate-600'
                      }`}
                    >
                      <span className="w-4 h-4 rounded-full bg-white border border-slate-900 mb-1.5 shadow-xs"></span>
                      <span className="text-[11px] font-semibold leading-tight">Hemat Tinta</span>
                    </button>
                  </div>
                </div>

                {/* Card Size Selector */}
                <div className="space-y-2">
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">Ukuran Kartu (A4 Grid)</label>
                  <div className="flex rounded-xl bg-slate-100 p-1">
                    <button
                      type="button"
                      onClick={() => setCardSize('sm')}
                      className={`flex-1 py-1.5 text-center text-[10px] font-bold rounded-lg transition-all cursor-pointer ${
                        cardSize === 'sm' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                      }`}
                    >
                      Kecil (3 Kolom)
                    </button>
                    <button
                      type="button"
                      onClick={() => setCardSize('md')}
                      className={`flex-1 py-1.5 text-center text-[10px] font-bold rounded-lg transition-all cursor-pointer ${
                        cardSize === 'md' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                      }`}
                    >
                      Sedang (2 Kolom)
                    </button>
                    <button
                      type="button"
                      onClick={() => setCardSize('lg')}
                      className={`flex-1 py-1.5 text-center text-[10px] font-bold rounded-lg transition-all cursor-pointer ${
                        cardSize === 'lg' ? 'bg-white text-slate-800 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                      }`}
                    >
                      Besar (1 Kolom)
                    </button>
                  </div>
                </div>

                {/* Content Visibility Toggles */}
                <div className="space-y-3.5 border-t border-slate-100 pt-5">
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">Visibilitas Detail Kartu</label>
                  
                  <label className="flex items-center space-x-3 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={showSchoolName}
                      onChange={(e) => setShowSchoolName(e.target.checked)}
                      className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500"
                    />
                    <span className="font-semibold">Tampilkan Nama Sekolah</span>
                  </label>

                  <label className="flex items-center space-x-3 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={showClass}
                      onChange={(e) => setShowClass(e.target.checked)}
                      className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500"
                    />
                    <span className="font-semibold">Tampilkan Kelas</span>
                  </label>

                  <label className="flex items-center space-x-3 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={showNisnText}
                      onChange={(e) => setShowNisnText(e.target.checked)}
                      className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500"
                    />
                    <span className="font-semibold">Tampilkan Kode UID / NISN</span>
                  </label>
                </div>

                {/* Printing instructions */}
                <div className="border-t border-slate-100 pt-5 space-y-2 text-[11px] text-slate-400">
                  <span className="font-bold text-slate-500 flex items-center">
                    <AlertTriangle className="w-3.5 h-3.5 mr-1 text-amber-500" />
                    Petunjuk Cetak Printer
                  </span>
                  <p className="leading-normal">
                    Pastikan mengaktifkan opsi <strong>Grafis Latar Belakang (Background Graphics)</strong> pada dialog printer browser agar warna dan tema kartu tercetak sempurna.
                  </p>
                </div>
              </div>

              {/* Right Panel: Interactive Live Print Preview */}
              <div className="flex-grow bg-slate-200 p-8 overflow-y-auto flex justify-center items-start">
                
                {/* Print area container */}
                <div 
                  id="print-area-workspace" 
                  className={`bg-white p-[12mm] rounded-2xl shadow-xl w-full max-w-[210mm] min-h-[297mm] transition-all ${
                    cardSize === 'sm' 
                      ? 'grid grid-cols-3 gap-3 justify-items-center' 
                      : cardSize === 'md' 
                        ? 'grid grid-cols-2 gap-4 justify-items-center' 
                        : 'flex flex-col items-center space-y-4'
                  }`}
                  style={{
                    boxSizing: 'border-box',
                    fontFamily: 'Inter, sans-serif'
                  }}
                >
                  
                  {/* CSS Injector for printing layout */}
                  <style dangerouslySetInnerHTML={{__html: `
                    @media print {
                      body * {
                        visibility: hidden !important;
                        overflow: visible !important;
                      }
                      #print-area-workspace, #print-area-workspace * {
                        visibility: visible !important;
                      }
                      #print-area-workspace {
                        position: absolute !important;
                        left: 0 !important;
                        top: 0 !important;
                        width: 100% !important;
                        max-width: none !important;
                        box-shadow: none !important;
                        border: none !important;
                        padding: 10mm !important;
                        margin: 0 !important;
                        background: white !important;
                        display: ${cardSize === 'lg' ? 'flex' : 'grid'} !important;
                        ${cardSize === 'sm' ? 'grid-template-columns: repeat(3, minmax(0, 1fr)) !important; gap: 12px !important;' : ''}
                        ${cardSize === 'md' ? 'grid-template-columns: repeat(2, minmax(0, 1fr)) !important; gap: 16px !important;' : ''}
                        ${cardSize === 'lg' ? 'flex-direction: column !important; align-items: center !important; gap: 20px !important;' : ''}
                      }
                      .print-card-item {
                        page-break-inside: avoid !important;
                        break-inside: avoid !important;
                        -webkit-print-color-adjust: exact !important;
                        print-color-adjust: exact !important;
                        box-shadow: none !important;
                        border: ${cardTheme === 'minimal' ? '2px solid black' : '1px solid #e2e8f0'} !important;
                      }
                      .no-print {
                        display: none !important;
                      }
                    }
                  `}} />

                  {studentsToPrint.map((student) => {
                    // Card dimension setups
                    const widthStyle = cardSize === 'sm' ? '65mm' : cardSize === 'md' ? '85.6mm' : '100mm';
                    const heightStyle = cardSize === 'sm' ? '40mm' : cardSize === 'md' ? '54mm' : '65mm';
                    const qrSizeValue = cardSize === 'sm' ? 44 : cardSize === 'md' ? 62 : 78;

                    return (
                      <div
                        key={student.id}
                        className={`print-card-item rounded-2xl border text-left flex flex-col justify-between overflow-hidden shadow-xs relative shrink-0 transition-colors bg-white ${
                          cardTheme === 'blue' 
                            ? 'border-indigo-150' 
                            : cardTheme === 'amber' 
                              ? 'border-amber-200' 
                              : cardTheme === 'dark' 
                                ? 'border-slate-800' 
                                : 'border-2 border-black rounded-none'
                        }`}
                        style={{
                          width: widthStyle,
                          height: heightStyle,
                          boxSizing: 'border-box'
                        }}
                      >
                        {/* Card Header Strip */}
                        <div className={`px-3 py-1.5 shrink-0 flex flex-col justify-center ${
                          cardTheme === 'blue' 
                            ? 'bg-indigo-700 text-white' 
                            : cardTheme === 'amber' 
                              ? 'bg-amber-500 text-white' 
                              : cardTheme === 'dark' 
                                ? 'bg-slate-900 text-white border-b-2 border-amber-400' 
                                : 'bg-slate-50 text-slate-950 border-b-2 border-black'
                        }`}>
                          <div className="flex items-center justify-between">
                            <span className="text-[8px] font-extrabold uppercase tracking-widest truncate max-w-[70%]">
                              {customHeaderTitle}
                            </span>
                            <span className="text-[6px] font-bold opacity-80 font-mono">
                              X-DEGAN QR
                            </span>
                          </div>
                          {showSchoolName && (
                            <span className="text-[7px] font-medium opacity-90 leading-tight truncate">
                              {activeSchool?.nama || 'Sekolah Terintegrasi'}
                            </span>
                          )}
                        </div>

                        {/* Card Body */}
                        <div className="flex-1 p-2.5 flex items-center space-x-3 overflow-hidden">
                          
                          {/* Left Panel: QR Code Box */}
                          <div className={`p-1 bg-white rounded-lg flex items-center justify-center shrink-0 border ${
                            cardTheme === 'blue' 
                              ? 'border-indigo-100 shadow-xs' 
                              : cardTheme === 'amber' 
                                ? 'border-amber-100' 
                                : cardTheme === 'dark' 
                                  ? 'border-slate-200 shadow-sm' 
                                  : 'border-black rounded-none'
                          }`}>
                            <QRCodeSVG
                              value={student.id} // Student UID is the s.uid inside the id field
                              size={qrSizeValue}
                              level="M"
                              includeMargin={false}
                            />
                          </div>

                          {/* Right Panel: Student Info */}
                          <div className="flex-grow flex flex-col justify-center overflow-hidden h-full space-y-1">
                            <div>
                              <div className="text-[8px] text-slate-400 font-bold uppercase tracking-wider leading-none">
                                Nama Siswa
                              </div>
                              <div className={`font-extrabold truncate leading-tight ${
                                cardSize === 'sm' ? 'text-[10px]' : cardSize === 'md' ? 'text-xs' : 'text-sm'
                              } ${
                                cardTheme === 'dark' ? 'text-slate-900' : 'text-slate-800'
                              }`}>
                                {student.nama}
                              </div>
                            </div>

                            {showClass && (
                              <div>
                                <span className={`inline-flex items-center px-1.5 py-0.5 rounded-md font-extrabold leading-none ${
                                  cardSize === 'sm' ? 'text-[7px]' : 'text-[8px]'
                                } ${
                                  cardTheme === 'blue' 
                                    ? 'bg-indigo-50 text-indigo-700' 
                                    : cardTheme === 'amber' 
                                      ? 'bg-amber-50/70 text-amber-800' 
                                      : cardTheme === 'dark' 
                                        ? 'bg-slate-100 text-slate-800' 
                                        : 'bg-white border border-black rounded-none text-black'
                                }`}>
                                  Kelas: {student.kelas}
                                </span>
                              </div>
                            )}

                            {showNisnText && (
                              <div>
                                <div className="text-[7px] text-slate-400 font-bold uppercase leading-none">
                                  UID / NISN
                                </div>
                                <div className="font-mono font-extrabold text-[9px] text-indigo-600 leading-tight">
                                  {student.nisn}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Card Accent Footer Strip */}
                        <div className={`h-1.5 w-full shrink-0 ${
                          cardTheme === 'blue' 
                            ? 'bg-indigo-700/80' 
                            : cardTheme === 'amber' 
                              ? 'bg-amber-500/80' 
                              : cardTheme === 'dark' 
                                ? 'bg-amber-400' 
                                : 'bg-black'
                        }`}></div>

                        {/* Background Watermark/Logo */}
                        {cardTheme !== 'minimal' && (
                          <div className="absolute right-2 bottom-4 opacity-[0.04] pointer-events-none">
                            <QrCode className="w-16 h-16 text-slate-900" />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Modal Footer Controls */}
            <div className="px-6 py-4 bg-white border-t border-slate-200 flex items-center justify-between shrink-0">
              <div className="text-xs text-slate-500">
                Akan mencetak <span className="font-bold text-slate-800">{studentsToPrint.length}</span> kartu QR.
              </div>
              <div className="flex space-x-2">
                <button
                  type="button"
                  onClick={() => setIsPrintQrModalOpen(false)}
                  className="px-5 py-2.5 border border-slate-200 text-slate-600 hover:text-slate-800 rounded-xl text-xs font-bold hover:bg-slate-50 transition-all active:scale-95 cursor-pointer"
                >
                  Tutup Workspace
                </button>
                <button
                  type="button"
                  onClick={() => window.print()}
                  className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md hover:shadow-lg transition-all active:scale-95 flex items-center cursor-pointer"
                >
                  <Printer className="w-4 h-4 mr-2" />
                  Cetak Sekarang
                </button>
              </div>
            </div>

          </div>
        </div>
      )}
    </div>
  );
};
