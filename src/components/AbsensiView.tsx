import React, { useEffect, useState } from 'react';
import { db } from '../firebaseClient';
import { AttendanceLog, Student, School, Holiday } from '../types';
import { useAuth } from '../context/AuthContext';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { 
  Calendar as CalendarIcon, 
  Search, 
  Trash2, 
  Plus, 
  CheckCircle, 
  Clock, 
  UserCheck, 
  FileCheck,
  AlertOctagon,
  X,
  QrCode,
  UserPlus,
  Lock,
  CalendarDays,
  Globe
} from 'lucide-react';

interface AbsensiViewProps {
  selectedNpsn: string;
  schools: School[];
  statusFilter?: string;
}

export const AbsensiView: React.FC<AbsensiViewProps> = ({ selectedNpsn, schools, statusFilter = 'Semua' }) => {
  const [logs, setLogs] = useState<AttendanceLog[]>([]);
  const [students, setStudents] = useState<Student[]>([]);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  
  // Filters state
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>(statusFilter);
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0]); // Default to today
  const [selectedKelas, setSelectedKelas] = useState<string>('Semua');
  const { user } = useAuth();

  useEffect(() => {
    setSelectedStatus(statusFilter);
  }, [statusFilter]);

  useEffect(() => {
    if (user?.role === 'guru' && user.kelas_tugas) {
      setSelectedKelas(user.kelas_tugas);
    } else {
      setSelectedKelas('Semua');
    }
  }, [user]);

  // Manual Check-In Modal
  const [isAddLogOpen, setIsAddLogOpen] = useState<boolean>(false);
  const [selectedStudentId, setSelectedStudentId] = useState<string>('');
  const [manualStatus, setManualStatus] = useState<'Hadir' | 'Sakit' | 'Izin' | 'Alpa'>('Hadir');

  const isDefaultSchool = selectedNpsn === 'SCH-DEFAULT';

  useEffect(() => {
    loadData();
  }, [selectedNpsn]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [logsData, studentsData, holidaysData] = await Promise.all([
        db.getLogs(selectedNpsn),
        db.getStudents(selectedNpsn),
        db.getHolidays(selectedNpsn)
      ]);
      setLogs(logsData);
      setStudents(studentsData);
      setHolidays(holidaysData);
    } catch (e) {
      console.error("Gagal memuat log kehadiran", e);
    } finally {
      setLoading(false);
    }
  };

  const handleManualCheckIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isDefaultSchool) return;
    if (!selectedStudentId) {
      alert("Harap pilih siswa terlebih dahulu");
      return;
    }

    const targetStudent = students.find(s => s.id === selectedStudentId);
    if (!targetStudent) return;

    try {
      await db.addLog({
        student_id: selectedStudentId,
        nama: targetStudent.nama,
        nisn: targetStudent.nisn,
        kelas: targetStudent.kelas,
        npsn_sekolah: targetStudent.npsn_sekolah,
        status: manualStatus,
        scan_method: 'Manual'
      });
      setIsAddLogOpen(false);
      setSelectedStudentId('');
      loadData();
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeleteLog = async (id: string) => {
    if (confirm("Apakah Anda yakin ingin menghapus log kehadiran ini?")) {
      try {
        await db.deleteLog(id);
        loadData();
      } catch (err) {
        console.error(err);
      }
    }
  };

  const handleExportPDF = () => {
    try {
      const doc = new jsPDF({
        orientation: 'portrait',
        unit: 'mm',
        format: 'a4'
      });

      const schoolName = schools.find(s => s.npsn === selectedNpsn)?.nama || 'Seluruh Sekolah';
      
      // Title
      doc.setFont('Helvetica', 'bold');
      doc.setFontSize(16);
      doc.text('REKAPITULASI PRESENSI SISWA', 14, 20);
      
      doc.setFontSize(11);
      doc.setFont('Helvetica', 'normal');
      doc.text(schoolName, 14, 26);
      doc.text(`NPSN: ${selectedNpsn}`, 14, 31);
      
      // Divider
      doc.setDrawColor(226, 232, 240); // slate-200
      doc.line(14, 35, 196, 35);
      
      // Filter & Meta Info
      doc.setFontSize(9);
      doc.text(`Tanggal Laporan: ${selectedDate || 'Semua Tanggal'}`, 14, 42);
      doc.text(`Filter Kelas: ${selectedKelas}`, 14, 47);
      doc.text(`Filter Status: ${selectedStatus}`, 14, 52);
      doc.text(`Waktu Cetak: ${new Date().toLocaleString('id-ID')} WIB`, 14, 57);

      // Summary Stats
      doc.setFont('Helvetica', 'bold');
      doc.text('Ringkasan Kehadiran:', 130, 42);
      doc.setFont('Helvetica', 'normal');
      doc.text(`Hadir: ${totalHadir} siswa`, 130, 47);
      doc.text(`Sakit: ${totalSakit} siswa`, 130, 52);
      doc.text(`Izin: ${totalIzin} siswa`, 130, 57);
      if (activeHoliday) {
        doc.text(`Libur: ${totalLibur} siswa`, 130, 62);
      } else {
        doc.text(`Alpa: ${totalAlpa} siswa`, 130, 62);
      }

      // Prepare Table Data
      const tableHeaders = [['No', 'Nama Siswa', 'NISN', 'Kelas', 'Status', 'Waktu Record', 'Metode']];
      const tableRows = displayRows.map((row, idx) => {
        const waktuStr = row.waktu 
          ? `${new Date(row.waktu).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} WIB`
          : '-';
        const metodeStr = row.is_holiday_default 
          ? 'Sistem (Hari Libur)'
          : row.scan_method === 'QR Code' 
            ? 'QR Dashboard'
            : row.scan_method || 'Admin Manual';
        return [
          idx + 1,
          row.nama,
          row.nisn,
          row.kelas,
          row.status,
          waktuStr,
          metodeStr
        ];
      });

      // Render table
      autoTable(doc, {
        startY: 68,
        head: tableHeaders,
        body: tableRows,
        theme: 'striped',
        headStyles: {
          fillColor: [79, 70, 229], // indigo-600 color
          textColor: [255, 255, 255],
          fontSize: 9,
          fontStyle: 'bold'
        },
        bodyStyles: {
          fontSize: 8
        },
        columnStyles: {
          0: { cellWidth: 10 },
          1: { cellWidth: 50 },
          2: { cellWidth: 25 },
          3: { cellWidth: 20 },
          4: { cellWidth: 20 },
          5: { cellWidth: 32 },
          6: { cellWidth: 25 }
        },
        didParseCell: (data) => {
          if (data.column.index === 4 && data.cell.section === 'body') {
            const val = data.cell.text[0];
            if (val === 'Hadir') {
              data.cell.styles.textColor = [16, 124, 65]; // green
            } else if (val === 'Sakit' || val === 'Izin') {
              data.cell.styles.textColor = [217, 119, 6]; // amber-600
            } else if (val === 'Libur') {
              data.cell.styles.textColor = [79, 70, 229]; // indigo-600
            } else if (val === 'Alpa') {
              data.cell.styles.textColor = [220, 38, 38]; // rose-600
            }
          }
        }
      });

      // Save PDF file
      const dateStr = selectedDate ? `-${selectedDate}` : '';
      const schoolClean = schoolName.toLowerCase().replace(/[^a-z0-9]/g, '-');
      doc.save(`laporan-absensi-${schoolClean}${dateStr}.pdf`);
    } catch (error) {
      console.error('Gagal mengunduh PDF:', error);
      alert('Terjadi kesalahan saat membuat file PDF.');
    }
  };

  // Find if selected date is configured as a holiday
  const activeHoliday = holidays.find(h => h.tanggal === selectedDate && (h.npsn_sekolah === 'ALL' || h.npsn_sekolah === selectedNpsn));

  // Filter logs based on search query, date, status, and class
  const filteredLogs = logs.filter(log => {
    const q = searchQuery.toLowerCase();
    const matchesSearch = 
      log.nama.toLowerCase().includes(q) || 
      log.nisn.includes(q) || 
      log.kelas.toLowerCase().includes(q);
    
    const matchesStatus = selectedStatus === 'Semua' || log.status === selectedStatus;
    
    // date match (log.waktu format is ISO, so we split at T)
    const logDate = log.waktu.split('T')[0];
    const matchesDate = !selectedDate || logDate === selectedDate;

    const matchesKelas = selectedKelas === 'Semua' || log.kelas === selectedKelas;

    return matchesSearch && matchesStatus && matchesDate && matchesKelas;
  });

  // Calculate dynamic stats based on chosen date filter and class filter
  const statsLogs = logs.filter(log => {
    const logDate = log.waktu.split('T')[0];
    const matchesDate = !selectedDate || logDate === selectedDate;
    const matchesKelas = selectedKelas === 'Semua' || log.kelas === selectedKelas;
    return matchesDate && matchesKelas;
  });

  // If activeHoliday is found, construct rows for ALL students showing "Libur" as default status
  const filteredStudents = students.filter(s => {
    const q = searchQuery.toLowerCase();
    const matchesSearch = 
      s.nama.toLowerCase().includes(q) || 
      s.nisn.includes(q) || 
      s.kelas.toLowerCase().includes(q);
    const matchesKelas = selectedKelas === 'Semua' || s.kelas === selectedKelas;
    return matchesSearch && matchesKelas;
  });

  const displayRows = activeHoliday 
    ? filteredStudents.map(student => {
        // Find if they have any check-in logs for this date
        const studentLog = filteredLogs.find(l => l.student_id === student.id);
        return {
          id: studentLog ? studentLog.id : `libur-${student.id}`,
          student_id: student.id,
          nama: student.nama,
          nisn: student.nisn,
          kelas: student.kelas,
          status: studentLog ? studentLog.status : ('Libur' as const),
          waktu: studentLog ? studentLog.waktu : null,
          scan_method: studentLog ? studentLog.scan_method : null,
          is_holiday_default: !studentLog
        };
      }).filter(row => selectedStatus === 'Semua' || row.status === selectedStatus)
    : filteredLogs.map(log => ({
        id: log.id,
        student_id: log.student_id,
        nama: log.nama,
        nisn: log.nisn,
        kelas: log.kelas,
        status: log.status,
        waktu: log.waktu,
        scan_method: log.scan_method,
        is_holiday_default: false
      }));

  const totalHadir = statsLogs.filter(l => l.status === 'Hadir').length;
  const totalSakit = statsLogs.filter(l => l.status === 'Sakit').length;
  const totalIzin = statsLogs.filter(l => l.status === 'Izin').length;
  const totalAlpa = activeHoliday ? 0 : statsLogs.filter(l => l.status === 'Alpa').length;
  const totalLibur = activeHoliday 
    ? (students.filter(s => selectedKelas === 'Semua' || s.kelas === selectedKelas).length - (totalHadir + totalSakit + totalIzin))
    : 0;

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Header View Section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-800">Rekap Log Absensi</h3>
          <p className="text-xs text-slate-500 mt-0.5">Pantau data kehadiran harian, ketidakhadiran, dan override manual scan siswa</p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            id="btn-export-pdf"
            onClick={handleExportPDF}
            className="flex items-center px-4 py-2.5 text-xs font-bold bg-white hover:bg-slate-50 text-slate-700 border border-slate-200 rounded-xl shadow-xs active:scale-95 transition-all cursor-pointer"
          >
            <FileCheck className="w-4 h-4 mr-2 text-indigo-600" />
            Cetak PDF / Laporan
          </button>

          <button
            id="btn-manual-absensi"
            onClick={() => !isDefaultSchool && setIsAddLogOpen(true)}
            disabled={isDefaultSchool}
            className={`flex items-center px-4 py-2.5 text-xs font-bold rounded-xl shadow-md transition-all ${
              isDefaultSchool
                ? 'bg-slate-300 text-slate-500 cursor-not-allowed'
                : 'bg-indigo-600 hover:bg-indigo-700 text-white active:scale-95'
            }`}
          >
            <UserPlus className="w-4 h-4 mr-2" />
            Input Absen Manual
          </button>
        </div>
      </div>

      {/* Dynamic Counter Cards for Selected Date */}
      <div id="absensi-stats-summary" className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Hadir Card */}
        <div className="bg-white p-4 rounded-xl border border-slate-100 shadow-md flex items-center space-x-3.5">
          <div className="p-3 bg-emerald-50 text-emerald-600 rounded-xl">
            <CheckCircle className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Hadir</p>
            <h4 className="text-xl font-bold text-slate-800 mt-0.5">{totalHadir}</h4>
          </div>
        </div>

        {/* Sakit Card */}
        <div className="bg-white p-4 rounded-xl border border-slate-100 shadow-md flex items-center space-x-3.5">
          <div className="p-3 bg-amber-50 text-amber-600 rounded-xl">
            <Clock className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Sakit</p>
            <h4 className="text-xl font-bold text-slate-800 mt-0.5">{totalSakit}</h4>
          </div>
        </div>

        {/* Izin Card */}
        <div className="bg-white p-4 rounded-xl border border-slate-100 shadow-md flex items-center space-x-3.5">
          <div className="p-3 bg-blue-50 text-blue-600 rounded-xl">
            <UserCheck className="w-5 h-5" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Izin</p>
            <h4 className="text-xl font-bold text-slate-800 mt-0.5">{totalIzin}</h4>
          </div>
        </div>

        {/* Alpa / Libur Card */}
        <div className="bg-white p-4 rounded-xl border border-slate-100 shadow-md flex items-center space-x-3.5">
          {activeHoliday ? (
            <>
              <div className="p-3 bg-indigo-50 text-indigo-600 rounded-xl">
                <CalendarDays className="w-5 h-5" />
              </div>
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Libur</p>
                <h4 className="text-xl font-bold text-slate-800 mt-0.5">{totalLibur}</h4>
              </div>
            </>
          ) : (
            <>
              <div className="p-3 bg-rose-50 text-rose-600 rounded-xl">
                <AlertOctagon className="w-5 h-5" />
              </div>
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Alpa</p>
                <h4 className="text-xl font-bold text-slate-800 mt-0.5">{totalAlpa}</h4>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Filter Options Strip */}
      <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-md grid grid-cols-1 md:grid-cols-4 gap-4 items-center">
        {/* Date Filter */}
        <div className="flex flex-col">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Filter Tanggal</label>
          <div className="relative">
            <CalendarIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4 pointer-events-none" />
            <input
              id="absensi-date-filter"
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-700 focus:outline-hidden focus:bg-white"
            />
          </div>
        </div>

        {/* Status Filter */}
        <div className="flex flex-col">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Filter Status</label>
          <select
            id="absensi-status-filter"
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value)}
            className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-700 focus:outline-hidden focus:bg-white cursor-pointer"
          >
            <option value="Semua">Semua Status</option>
            <option value="Hadir">Hadir</option>
            <option value="Sakit">Sakit</option>
            <option value="Izin">Izin</option>
            <option value="Alpa">Alpa</option>
          </select>
        </div>

        {/* Class Filter */}
        <div className="flex flex-col">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Filter Kelas</label>
          <div className="relative">
            <select
              id="absensi-kelas-filter"
              value={selectedKelas}
              onChange={(e) => setSelectedKelas(e.target.value)}
              disabled={user?.role === 'guru'}
              className={`w-full pl-3 pr-8 py-2 border rounded-xl text-xs font-semibold appearance-none focus:outline-hidden cursor-pointer ${
                user?.role === 'guru'
                  ? 'bg-amber-50 border-amber-200 text-amber-700 cursor-not-allowed'
                  : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100 focus:bg-white'
              }`}
            >
              {user?.role !== 'guru' && <option value="Semua">Semua Kelas</option>}
              {user?.role === 'guru' && user.kelas_tugas && (
                <option value={user.kelas_tugas}>{user.kelas_tugas} (Terkunci)</option>
              )}
              {Array.from(new Set(logs.map(l => l.kelas))).filter(Boolean).sort().map((kls) => {
                if (user?.role === 'guru' && kls !== user.kelas_tugas) return null;
                return (
                  <option key={kls} value={kls}>
                    {kls}
                  </option>
                );
              })}
            </select>
            <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400">
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

        {/* Query Search */}
        <div className="flex flex-col">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">Pencarian Murid</label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 w-4 h-4" />
            <input
              id="absensi-search-input"
              type="text"
              placeholder="Ketik nama, NISN, atau kelas..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 placeholder-slate-400 focus:outline-hidden focus:bg-white"
            />
          </div>
        </div>
      </div>

      {/* Holiday Banner if selected date is a holiday */}
      {activeHoliday && (
        <div id="holiday-active-banner" className="p-5 bg-gradient-to-r from-indigo-500/10 to-sky-500/10 border border-indigo-200 rounded-2xl flex items-center space-x-3.5 animate-fadeIn">
          <div className="p-3 bg-indigo-500 text-white rounded-xl shrink-0">
            <CalendarDays className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-slate-800">Hari Libur Terdeteksi: {activeHoliday.nama}</h4>
            <p className="text-xs text-slate-500 mt-1 leading-relaxed">
              Sistem mendeteksi tanggal ini sebagai hari libur ({activeHoliday.npsn_sekolah === 'ALL' ? 'Nasional/Global' : 'Khusus Sekolah'}). Status default siswa diset sebagai <span className="font-bold text-indigo-600">Libur</span>. Pemindai otomatis ditangguhkan. {activeHoliday.keterangan ? `(${activeHoliday.keterangan})` : ''}
            </p>
          </div>
        </div>
      )}

      {/* Main logs Table */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-md overflow-hidden">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-500">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            <span className="mt-3 text-xs">Memuat data log absensi...</span>
          </div>
        ) : displayRows.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400">
            <CalendarIcon className="w-12 h-12 stroke-1 text-slate-300 mb-2 animate-bounce" />
            <p className="text-xs font-semibold text-slate-500">Tidak ada log kehadiran cocok</p>
            <p className="text-[11px] text-slate-400 mt-1">Sesuaikan tanggal atau status untuk melihat data histori lainnya.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table id="logs-data-table" className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/50 border-b border-slate-150 text-xs font-bold text-slate-400 uppercase tracking-wider">
                  <th className="py-4 px-6">Siswa</th>
                  <th className="py-4 px-6">NISN</th>
                  <th className="py-4 px-6">Kelas</th>
                  <th className="py-4 px-6">Status</th>
                  <th className="py-4 px-6">Waktu Record</th>
                  <th className="py-4 px-6">Metode</th>
                  <th className="py-4 px-6 text-right w-24">Aksi</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs text-slate-600">
                {displayRows.map((row) => (
                  <tr key={row.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-6">
                      <span className="font-bold text-slate-800">{row.nama}</span>
                    </td>
                    <td className="py-3.5 px-6 font-mono text-slate-500">{row.nisn}</td>
                    <td className="py-3.5 px-6 font-medium text-slate-700">{row.kelas}</td>
                    <td className="py-3.5 px-6">
                      <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                        row.status === 'Hadir' ? 'bg-emerald-50 text-emerald-600 border border-emerald-100' :
                        row.status === 'Sakit' ? 'bg-amber-50 text-amber-600 border border-amber-100' :
                        row.status === 'Izin' ? 'bg-blue-50 text-blue-600 border border-blue-100' :
                        row.status === 'Libur' ? 'bg-indigo-50/70 text-indigo-600 border border-indigo-100' :
                        'bg-rose-50 text-rose-600 border border-rose-100'
                      }`}>
                        {row.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-6 font-semibold text-slate-700">
                      {row.waktu ? (
                        `${new Date(row.waktu).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} WIB`
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="py-3.5 px-6">
                      <span className="inline-flex items-center text-[10px] text-slate-500 font-medium">
                        {row.is_holiday_default ? (
                          <span className="inline-flex items-center text-indigo-500 font-bold">
                            <CalendarDays className="w-3.5 h-3.5 mr-1" />
                            Sistem (Hari Libur)
                          </span>
                        ) : row.scan_method === 'QR Code' ? (
                          <>
                            <QrCode className="w-3.5 h-3.5 mr-1 text-sky-500 animate-pulse" />
                            QR Dashboard
                          </>
                        ) : (
                          'Admin Manual'
                        )}
                      </span>
                    </td>
                    <td className="py-3.5 px-6 text-right">
                      {!row.is_holiday_default ? (
                        <button
                          id={`btn-delete-log-${row.id}`}
                          onClick={() => handleDeleteLog(row.id)}
                          className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors border border-transparent hover:border-rose-100"
                          title="Hapus log"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      ) : (
                        <span className="text-slate-300">-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* --- MANUAL CHECK-IN OVERRIDE MODAL --- */}
      {isAddLogOpen && (
        <div id="add-log-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-md w-full shadow-2xl overflow-hidden animate-fadeIn">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <h4 className="font-bold text-slate-800 text-sm">Input Kehadiran Manual</h4>
              <button onClick={() => setIsAddLogOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-4 h-4" />
              </button>
            </div>
            
            {students.length === 0 ? (
              <div className="p-6 text-center text-slate-500 text-xs">
                Tidak ada siswa terdaftar di sekolah ini untuk melakukan input absen manual.
              </div>
            ) : (
              <form onSubmit={handleManualCheckIn} className="p-6 space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Pilih Anggota Siswa</label>
                  <select
                    id="manual-select-siswa"
                    required
                    value={selectedStudentId}
                    onChange={(e) => setSelectedStudentId(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 cursor-pointer"
                  >
                    <option value="">-- Pilih Siswa --</option>
                    {students.map((student) => (
                      <option key={student.id} value={student.id}>
                        {student.nama} ({student.kelas} - {student.nisn})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-1.5">Status Kehadiran</label>
                  <div className="grid grid-cols-4 gap-2">
                    {(['Hadir', 'Sakit', 'Izin', 'Alpa'] as const).map((status) => (
                      <button
                        type="button"
                        key={status}
                        onClick={() => setManualStatus(status)}
                        className={`py-2 text-xs font-bold rounded-xl border transition-all ${
                          manualStatus === status
                            ? status === 'Hadir' ? 'bg-emerald-50 text-emerald-700 border-emerald-400 shadow-xs' :
                              status === 'Sakit' ? 'bg-amber-50 text-amber-700 border-amber-400 shadow-xs' :
                              status === 'Izin' ? 'bg-blue-50 text-blue-700 border-blue-400 shadow-xs' :
                              'bg-rose-50 text-rose-700 border-rose-400 shadow-xs'
                            : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                        }`}
                      >
                        {status}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 text-[11px] text-slate-600 leading-relaxed">
                  Catatan: Absensi manual ini akan tercatat atas tanggal dan waktu saat ini (<span className="font-semibold">{new Date().toLocaleTimeString()} WIB</span>) sebagai bypass audit.
                </div>

                <div className="pt-4 border-t border-slate-100 flex justify-end space-x-2">
                  <button
                    type="button"
                    onClick={() => setIsAddLogOpen(false)}
                    className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50"
                  >
                    Batal
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md"
                  >
                    Rekam Kehadiran
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
