import React, { useEffect, useState } from 'react';
import { db } from '../firebaseClient';
import { Student, AttendanceLog, School, Broadcast, LoginTableRecord, Holiday } from '../types';
import { useAuth } from '../context/AuthContext';
import { 
  Users, 
  CheckCircle, 
  AlertCircle, 
  Clock, 
  TrendingUp, 
  QrCode, 
  Calendar,
  School as SchoolIcon,
  Megaphone,
  ExternalLink,
  Lock,
  UserCheck,
  CalendarDays
} from 'lucide-react';

interface DashboardViewProps {
  selectedNpsn: string;
  schools: School[];
}

export const DashboardView: React.FC<DashboardViewProps> = ({ selectedNpsn, schools }) => {
  const { user } = useAuth();
  const [students, setStudents] = useState<Student[]>([]);
  const [logs, setLogs] = useState<AttendanceLog[]>([]);
  const [broadcasts, setBroadcasts] = useState<Broadcast[]>([]);
  const [teachers, setTeachers] = useState<LoginTableRecord[]>([]);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [selectedKelas, setSelectedKelas] = useState<string>('Semua');
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (user?.role === 'guru' && user.kelas_tugas) {
      setSelectedKelas(user.kelas_tugas);
    } else {
      setSelectedKelas('Semua');
    }
  }, [user]);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [studentList, logList, broadcastList, loginsList, holidaysList] = await Promise.all([
          db.getStudents(selectedNpsn),
          db.getLogs(selectedNpsn),
          db.getBroadcasts(),
          db.getLogins(),
          db.getHolidays(selectedNpsn)
        ]);
        setStudents(studentList);
        setLogs(logList);
        setBroadcasts((broadcastList || []).filter(b => b.is_active));
        setTeachers(loginsList || []);
        setHolidays(holidaysList || []);
      } catch (e) {
        console.error("Error loading dashboard data", e);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [selectedNpsn]);

  const activeSchool = schools.find(s => s.npsn === selectedNpsn);
  const uniqueClasses = Array.from(new Set(students.map(s => s.kelas))).filter(Boolean).sort();

  // Filter students & logs based on class selection
  const filteredStudents = selectedKelas === 'Semua' ? students : students.filter(s => s.kelas === selectedKelas);
  const filteredLogs = selectedKelas === 'Semua' ? logs : logs.filter(l => l.kelas === selectedKelas);

  // Calculate today holiday status
  const todayStr = new Date().toISOString().split('T')[0];
  const todayHoliday = holidays.find(h => h.tanggal === todayStr && (h.npsn_sekolah === 'ALL' || h.npsn_sekolah === selectedNpsn));

  // Calculate statistics using filtered datasets
  const totalStudents = filteredStudents.length;
  const todayLogs = filteredLogs.filter(l => {
    const today = new Date().toISOString().split('T')[0];
    return l.waktu.startsWith(today);
  });

  const countHadir = todayLogs.filter(l => l.status === 'Hadir').length;
  const countSakit = todayLogs.filter(l => l.status === 'Sakit').length;
  const countIzin = todayLogs.filter(l => l.status === 'Izin').length;
  const countAlpa = todayHoliday ? 0 : todayLogs.filter(l => l.status === 'Alpa').length;

  const totalAbsenRecorded = todayLogs.length;
  const countNotCheckedIn = todayHoliday ? 0 : Math.max(0, totalStudents - totalAbsenRecorded);

  const attendancePercentage = todayHoliday 
    ? 100 
    : (totalStudents > 0 
        ? Math.round(((countHadir + countIzin + countSakit) / totalStudents) * 100) 
        : 0);

  // Group by Class for customized visual distribution
  const classBreakdown: { [key: string]: { hadir: number, total: number } } = {};
  filteredStudents.forEach(s => {
    if (!classBreakdown[s.kelas]) {
      classBreakdown[s.kelas] = { hadir: 0, total: 0 };
    }
    classBreakdown[s.kelas].total += 1;
  });

  todayLogs.forEach(l => {
    if (l.status === 'Hadir' && classBreakdown[l.kelas]) {
      classBreakdown[l.kelas].hadir += 1;
    }
  });

  const classesList = Object.keys(classBreakdown).map(k => ({
    name: k,
    hadir: classBreakdown[k].hadir,
    total: classBreakdown[k].total,
    rate: classBreakdown[k].total > 0 ? Math.round((classBreakdown[k].hadir / classBreakdown[k].total) * 100) : 0
  })).slice(0, 5); // top 5 classes

  // Generate last 7 calendar days ending today
  const last7DaysList = Array.from({ length: 7 }, (_, idx) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - idx));
    return d.toISOString().split('T')[0];
  });

  const dailyAttendanceTrend = last7DaysList.map(dateStr => {
    const logsOnDay = filteredLogs.filter(l => l.waktu && l.waktu.startsWith(dateStr));
    const countHadirOnDay = logsOnDay.filter(l => l.status === 'Hadir').length;
    const countSakitOnDay = logsOnDay.filter(l => l.status === 'Sakit').length;
    const countIzinOnDay = logsOnDay.filter(l => l.status === 'Izin').length;
    const countAlpaOnDay = logsOnDay.filter(l => l.status === 'Alpa').length;

    const isHoliday = holidays.find(h => h.tanggal === dateStr && (h.npsn_sekolah === 'ALL' || h.npsn_sekolah === selectedNpsn));
    const totalStudentsInSelection = filteredStudents.length;

    let rateOnDay = 0;
    if (isHoliday) {
      rateOnDay = 100;
    } else if (totalStudentsInSelection > 0) {
      // (Hadir + Sakit + Izin) / Total Students consistent with dashboard percentage
      rateOnDay = Math.min(100, Math.round(((countHadirOnDay + countSakitOnDay + countIzinOnDay) / totalStudentsInSelection) * 100));
    }

    const dateObj = new Date(dateStr);
    const dayName = dateObj.toLocaleDateString('id-ID', { weekday: 'short' });
    const dayNum = dateObj.toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });
    const label = `${dayName}, ${dayNum}`;

    return {
      dateStr,
      label,
      hadir: countHadirOnDay,
      sakit: countSakitOnDay,
      izin: countIzinOnDay,
      alpa: countAlpaOnDay,
      rate: rateOnDay,
      total: totalStudentsInSelection,
      isHoliday
    };
  });

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Beautiful Skeleton Loading States */}
      {loading && (
        <div className="space-y-6">
          {/* Skeleton Header & Filter */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-4.5 rounded-2xl border border-slate-100 shadow-md animate-pulse">
            <div className="space-y-2">
              <div className="h-4.5 bg-slate-200 rounded-lg w-44"></div>
              <div className="h-3 bg-slate-100 rounded-md w-64"></div>
            </div>
            <div className="flex items-center space-x-2 shrink-0">
              <div className="h-3 bg-slate-100 rounded-md w-16"></div>
              <div className="h-8 bg-slate-200 rounded-xl w-44"></div>
            </div>
          </div>

          {/* Skeleton Quick Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4.5">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md flex items-center justify-between animate-pulse">
                <div className="space-y-2.5 flex-1 pr-4">
                  <div className="h-3 bg-slate-100 rounded-md w-24"></div>
                  <div className="h-7 bg-slate-200 rounded-lg w-16"></div>
                  <div className="h-3 bg-slate-100 rounded-md w-32"></div>
                </div>
                <div className="p-3 bg-slate-100/70 border border-slate-100/50 rounded-xl w-11 h-11 shrink-0"></div>
              </div>
            ))}
          </div>

          {/* Skeleton Daily Attendance Trend Chart */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md animate-pulse">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-6">
              <div className="space-y-2 flex-1">
                <div className="h-4 bg-slate-200 rounded-md w-64"></div>
                <div className="h-3.5 bg-slate-100 rounded-md w-96 max-w-full"></div>
              </div>
              <div className="h-6 bg-slate-100 rounded-lg w-48 shrink-0"></div>
            </div>
            <div className="space-y-4">
              <div className="relative h-60 flex mt-2 pt-2 items-end">
                {/* Y labels */}
                <div className="flex flex-col justify-between text-[9px] w-8 pr-2 h-48 py-1 select-none">
                  <div className="h-2 bg-slate-100 rounded-sm w-5"></div>
                  <div className="h-2 bg-slate-100 rounded-sm w-5"></div>
                  <div className="h-2 bg-slate-100 rounded-sm w-5"></div>
                  <div className="h-2 bg-slate-100 rounded-sm w-5"></div>
                  <div className="h-2 bg-slate-100 rounded-sm w-5"></div>
                </div>
                {/* Chart Grid Area */}
                <div className="flex-1 h-48 border-b border-slate-100 flex justify-around items-end px-2 pb-1 relative">
                  {/* Mock grid lines */}
                  <div className="absolute inset-0 flex flex-col justify-between pointer-events-none">
                    <div className="border-t border-slate-100/50 w-full h-0"></div>
                    <div className="border-t border-slate-100/50 w-full h-0"></div>
                    <div className="border-t border-slate-100/50 w-full h-0"></div>
                    <div className="border-t border-slate-100/50 w-full h-0"></div>
                    <div className="w-full h-0"></div>
                  </div>
                  {/* Mock animated-pulse Bars */}
                  {[60, 80, 50, 90, 70, 75, 85].map((heightVal, idx) => (
                    <div key={idx} className="w-full max-w-[64px] mx-1 flex flex-col items-center justify-end h-full z-10">
                      <div className="h-3 bg-slate-100 rounded-sm w-6 mb-2"></div>
                      <div className="w-full bg-slate-200/80 hover:bg-slate-300 rounded-t-lg transition-all duration-300" style={{ height: `${heightVal}%` }}></div>
                    </div>
                  ))}
                </div>
              </div>
              {/* X labels */}
              <div className="flex pl-8">
                <div className="flex-1 flex justify-around px-2">
                  {[1, 2, 3, 4, 5, 6, 7].map((i) => (
                    <div key={i} className="text-center w-full max-w-[64px] mx-1 space-y-1">
                      <div className="h-2.5 bg-slate-100 rounded-sm w-8 mx-auto"></div>
                      <div className="h-2 bg-slate-100 rounded-sm w-12 mx-auto"></div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Skeleton Class Distribution Progress Grid */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md animate-pulse">
            <div className="flex items-center justify-between mb-5">
              <div className="space-y-2">
                <div className="h-4 bg-slate-200 rounded-md w-56"></div>
                <div className="h-3 bg-slate-100 rounded-md w-40"></div>
              </div>
              <div className="w-4 h-4 bg-slate-100 rounded-full"></div>
            </div>
            <div className="space-y-4.5">
              {[1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="space-y-2">
                  <div className="flex justify-between">
                    <div className="h-3 bg-slate-200 rounded-md w-24"></div>
                    <div className="h-3 bg-slate-100 rounded-md w-36"></div>
                  </div>
                  <div className="w-full bg-slate-100 h-2.5 rounded-full"></div>
                </div>
              ))}
            </div>
          </div>

          {/* Skeleton Recent Attendance Activity Table */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md animate-pulse">
            <div className="flex items-center justify-between mb-5">
              <div className="space-y-2">
                <div className="h-4 bg-slate-200 rounded-md w-44"></div>
                <div className="h-3 bg-slate-100 rounded-md w-56"></div>
              </div>
              <div className="h-5 bg-slate-100 rounded-full w-24"></div>
            </div>
            <div className="space-y-4">
              {[1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="flex justify-between items-center py-3 border-b border-slate-50">
                  <div className="flex-1 grid grid-cols-6 gap-4">
                    <div className="h-3.5 bg-slate-200 rounded-md col-span-2"></div>
                    <div className="h-3.5 bg-slate-100 rounded-md col-span-1"></div>
                    <div className="h-3.5 bg-slate-100 rounded-md col-span-1"></div>
                    <div className="h-3.5 bg-slate-200 rounded-md col-span-1"></div>
                    <div className="h-3.5 bg-slate-100 rounded-md col-span-1"></div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {!loading && (
        <>
          {/* Dashboard Header & Class Filter */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-4.5 rounded-2xl border border-slate-100 shadow-md">
            <div>
              <h2 className="text-base font-bold text-slate-800">Ringkasan Statistik</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Monitoring kehadiran {activeSchool ? `untuk ${activeSchool.nama}` : 'seluruh sekolah'}
              </p>
            </div>

            {/* Filter Kelas Select Dropdown */}
            <div className="flex items-center space-x-2 shrink-0">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Filter Kelas:</span>
              <div className="relative w-44">
                <select
                  id="dashboard-kelas-filter"
                  value={selectedKelas}
                  onChange={(e) => setSelectedKelas(e.target.value)}
                  disabled={user?.role === 'guru'}
                  className={`w-full pl-3 pr-8 py-1.5 border rounded-xl text-xs font-semibold appearance-none focus:outline-hidden transition-all cursor-pointer ${
                    user?.role === 'guru'
                      ? 'bg-amber-50 border-amber-100 text-amber-700 cursor-not-allowed'
                      : 'bg-slate-50 border-slate-100 text-slate-600 hover:bg-slate-100 focus:border-blue-500 focus:bg-white'
                  }`}
                >
                  {user?.role !== 'guru' && <option value="Semua">Semua Kelas</option>}
                  {user?.role === 'guru' && user.kelas_tugas && (
                    <option value={user.kelas_tugas}>{user.kelas_tugas} (Terkunci)</option>
                  )}
                  {uniqueClasses.map((kls) => {
                    if (user?.role === 'guru' && kls !== user.kelas_tugas) return null;
                    return (
                      <option key={kls} value={kls}>
                        {kls}
                      </option>
                    );
                  })}
                </select>
                <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-450">
                  {user?.role === 'guru' ? (
                    <Lock className="w-3 h-3 text-amber-500" />
                  ) : (
                    <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                    </svg>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Quick Stats Grid */}
          <div id="stats-cards-grid" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4.5">
            {/* Total Siswa */}
            <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md flex items-center justify-between">
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Siswa Terdaftar</p>
                <h3 className="text-2xl font-extrabold text-slate-800 mt-1">{totalStudents}</h3>
                <p className="text-[11px] text-slate-400 mt-0.5">Siswa aktif saat ini</p>
              </div>
              <div className="p-3 bg-blue-50 border border-blue-100/50 rounded-xl text-blue-600">
                <Users className="w-5 h-5" />
              </div>
            </div>

            {/* Kehadiran Hari Ini */}
            <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md flex items-center justify-between">
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Hadir Hari Ini</p>
                <h3 className="text-2xl font-extrabold text-slate-800 mt-1">{countHadir}</h3>
                <p className="text-[11px] text-emerald-600 mt-0.5 flex items-center">
                  <TrendingUp className="w-3 h-3 mr-0.5" />
                  Siswa terpindai masuk
                </p>
              </div>
              <div className="p-3 bg-emerald-50 border border-emerald-100/50 rounded-xl text-emerald-600">
                <CheckCircle className="w-5 h-5" />
              </div>
            </div>

            {/* Izin/Sakit/Alpa */}
            <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md flex items-center justify-between">
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Sakit / Izin / Alpa</p>
                <h3 className="text-2xl font-extrabold text-slate-800 mt-1">
                  <span className="text-amber-650">{countSakit + countIzin}</span>
                  <span className="text-slate-300 mx-1.5">/</span>
                  <span className="text-rose-500">{countAlpa}</span>
                </h3>
                <p className="text-[11px] text-slate-400 mt-0.5">Entri ketidakhadiran</p>
              </div>
              <div className="p-3 bg-amber-50 border border-amber-100/50 rounded-xl text-amber-600">
                <AlertCircle className="w-5 h-5" />
              </div>
            </div>

            {/* Persentase Kehadiran */}
            <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md flex items-center justify-between">
              <div>
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Rasio Kehadiran</p>
                <h3 className="text-2xl font-extrabold text-slate-800 mt-1">{attendancePercentage}%</h3>
                <div className="w-24 bg-slate-100 rounded-full h-1.5 mt-1.5 overflow-hidden">
                  <div 
                    className="bg-blue-600 h-1.5 rounded-full transition-all duration-500" 
                    style={{ width: `${attendancePercentage}%` }}
                  ></div>
                </div>
              </div>
              <div className="p-3 bg-blue-50 border border-blue-100/55 rounded-xl text-blue-600">
                <TrendingUp className="w-5 h-5" />
              </div>
            </div>
          </div>

          {/* Today Holiday Banner */}
          {todayHoliday && (
            <div id="today-holiday-banner" className="p-5 bg-gradient-to-r from-blue-50/50 to-indigo-50/30 border border-blue-100/50 rounded-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 animate-fadeIn">
              <div className="flex items-start space-x-3.5">
                <div className="p-2.5 bg-blue-600 text-white rounded-xl">
                  <CalendarDays className="w-5 h-5 animate-pulse" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800">Hari Ini Hari Libur: {todayHoliday.nama}</h4>
                  <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">
                    Hari ini sekolah diliburkan secara sistem ({todayHoliday.npsn_sekolah === 'ALL' ? 'Libur Nasional' : 'Libur Khusus'}). Proses absensi siswa otomatis ditangguhkan. {todayHoliday.keterangan ? `(${todayHoliday.keterangan})` : ''}
                  </p>
                </div>
              </div>
              <div className="flex items-center space-x-2 text-xs font-bold text-blue-700 bg-blue-50 border border-blue-100/50 px-3 py-1.5 rounded-xl shrink-0 self-start sm:self-center">
                <span>Status: Libur Aktif</span>
              </div>
            </div>
          )}

          {/* Warning for Locked SCH-DEFAULT State */}
          {selectedNpsn === 'SCH-DEFAULT' && (
            <div className="p-5 bg-gradient-to-r from-amber-500/5 to-orange-500/5 border border-amber-200/50 rounded-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div className="flex items-start space-x-3">
                <div className="p-2 bg-amber-100 text-amber-700 rounded-lg mt-0.5 sm:mt-0">
                  <QrCode className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800">Demo Terkunci ("SCH-DEFAULT")</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Anda berada pada sekolah default. Silakan pilih atau buat sekolah baru untuk mendapatkan hak akses penuh.
                  </p>
                </div>
              </div>
              <div className="flex items-center space-x-2 text-[10px] font-semibold text-amber-700 bg-amber-50 border border-amber-100/40 px-2.5 py-1 rounded-lg">
                <span>Demo Mode</span>
              </div>
            </div>
          )}

          {/* Daily Attendance Trend Bar Chart */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-6">
              <div>
                <h4 className="text-xs font-bold text-slate-800 flex items-center">
                  <TrendingUp className="w-4 h-4 mr-1.5 text-indigo-600" />
                  Tren Kehadiran Harian (7 Hari Terakhir) - Kelas {selectedKelas}
                </h4>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  Visualisasi persentase kehadiran siswa harian berdasarkan filter kelas saat ini
                </p>
              </div>
              <div className="flex items-center space-x-2 text-[10px] font-semibold text-indigo-600 bg-indigo-50 border border-indigo-100/50 px-2.5 py-1 rounded-lg self-start sm:self-center">
                <span>Horizontal: Hari | Vertikal: Persentase Kehadiran</span>
              </div>
            </div>

            <div className="space-y-4">
              {/* Chart Canvas Area */}
              <div className="relative h-60 flex mt-2 pt-2">
                {/* Y Axis Labels */}
                <div className="flex flex-col justify-between text-[9px] font-bold text-slate-400 w-8 pr-2 h-48 select-none">
                  <span>100%</span>
                  <span>75%</span>
                  <span>50%</span>
                  <span>25%</span>
                  <span>0%</span>
                </div>

                {/* Chart Bars Grid */}
                <div className="flex-1 relative h-48 border-b border-slate-100">
                  {/* Grid lines */}
                  <div className="absolute inset-0 flex flex-col justify-between pointer-events-none">
                    <div className="border-t border-slate-100/70 w-full h-0"></div>
                    <div className="border-t border-slate-100/70 w-full h-0"></div>
                    <div className="border-t border-slate-100/70 w-full h-0"></div>
                    <div className="border-t border-slate-100/70 w-full h-0"></div>
                    <div className="w-full h-0"></div>
                  </div>

                  {/* Bars Container */}
                  <div className="absolute inset-0 flex justify-around items-end px-2">
                    {dailyAttendanceTrend.map((item) => (
                      <div key={item.dateStr} className="group relative flex flex-col items-center w-full max-w-[64px] mx-1 h-full justify-end">
                        {/* Tooltip on Hover */}
                        <div className="absolute bottom-full mb-2 hidden group-hover:flex flex-col bg-slate-800 text-white p-2.5 rounded-xl text-[10px] shadow-lg z-30 w-44 pointer-events-none transition-all duration-200">
                          <p className="font-bold border-b border-slate-700 pb-1 mb-1 text-[11px] text-slate-100">{item.label}</p>
                          {item.isHoliday ? (
                            <p className="text-amber-400 font-semibold mb-1">Hari Libur: {item.isHoliday.nama}</p>
                          ) : null}
                          <p className="flex justify-between mt-0.5">
                            <span>Persentase:</span> 
                            <span className="font-bold text-emerald-400">{item.rate}%</span>
                          </p>
                          <p className="flex justify-between">
                            <span>Siswa Hadir:</span> 
                            <span className="font-semibold">{item.hadir} anak</span>
                          </p>
                          <p className="flex justify-between">
                            <span>Sakit / Izin:</span> 
                            <span className="font-semibold">{item.sakit + item.izin} anak</span>
                          </p>
                          <p className="flex justify-between">
                            <span>Alpa:</span> 
                            <span className="font-semibold">{item.alpa} anak</span>
                          </p>
                          <p className="flex justify-between border-t border-slate-700 mt-1 pt-1 text-slate-400">
                            <span>Total Terdaftar:</span> 
                            <span className="font-semibold">{item.total} anak</span>
                          </p>
                        </div>

                        {/* Percentage value above bar */}
                        <span className={`text-[10px] font-extrabold mb-1.5 transition-all ${
                          item.isHoliday ? 'text-indigo-600' : 'text-slate-700 group-hover:text-indigo-600 group-hover:scale-105'
                        }`}>
                          {item.rate}%
                        </span>

                        {/* The Bar */}
                        <div 
                          className={`w-full hover:scale-x-105 rounded-t-lg transition-all duration-500 ease-out shadow-xs group-hover:shadow-md cursor-pointer ${
                            item.isHoliday 
                              ? 'bg-gradient-to-t from-indigo-200 to-indigo-400/80 border-t border-x border-indigo-300'
                              : item.rate >= 90
                                ? 'bg-gradient-to-t from-emerald-500 to-emerald-600 hover:from-emerald-600 hover:to-emerald-700'
                                : item.rate >= 75
                                  ? 'bg-gradient-to-t from-indigo-500 to-indigo-600 hover:from-indigo-600 hover:to-indigo-700'
                                  : item.rate >= 50
                                    ? 'bg-gradient-to-t from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700'
                                    : 'bg-gradient-to-t from-rose-500 to-rose-600 hover:from-rose-600 hover:to-rose-700'
                          }`}
                          style={{ height: `${Math.max(6, item.rate)}%` }}
                        ></div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* X Axis Labels */}
              <div className="flex pl-8">
                <div className="flex-1 flex justify-around px-2">
                  {dailyAttendanceTrend.map((item) => (
                    <div key={item.dateStr} className="text-center w-full max-w-[64px] mx-1">
                      <p className="text-[9px] font-extrabold text-slate-600 line-clamp-1">{item.label.split(',')[0]}</p>
                      <p className="text-[9px] font-medium text-slate-400">{item.label.split(',')[1]}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>



          {/* Visual Graphs & Activity Columns */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Kehadiran Per Kelas Chart (Custom SVG Graphic) */}
            <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md lg:col-span-3">
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h4 className="text-xs font-bold text-slate-800">Distribusi Kehadiran Kelas Teratas</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Persentase kehadiran hari ini</p>
                </div>
                <Calendar className="w-4 h-4 text-slate-400" />
              </div>

              {classesList.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-slate-400">
                  <SchoolIcon className="w-10 h-10 stroke-1 mb-2 text-slate-300" />
                  <p className="text-xs font-medium">Belum ada data siswa di sekolah ini</p>
                </div>
              ) : (
                <div className="space-y-3.5">
                  {classesList.map((cls, i) => (
                    <div key={cls.name} className="space-y-1">
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-700 font-semibold">{cls.name}</span>
                        <span className="text-slate-400">{cls.hadir} dari {cls.total} siswa ({cls.rate}%)</span>
                      </div>
                      <div className="relative w-full bg-slate-50 border border-slate-100/50 h-2.5 rounded-full overflow-hidden">
                        <div 
                          className="absolute top-0 left-0 h-full bg-gradient-to-r from-sky-400 to-blue-600 rounded-full transition-all duration-700"
                          style={{ width: `${cls.rate}%` }}
                        ></div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Attendance Info Alert */}
              <div className="mt-6 pt-5 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
                <span className="flex items-center">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-500 mr-2"></span>
                  Metode presensi terintegrasi QR Code Scanner otomatis
                </span>
                <span className="font-semibold text-blue-600">Sistem Aktif</span>
              </div>
            </div>

          </div>

          {/* Recent Scans / Check-In Logs */}
          <div className="bg-white p-5 rounded-2xl border border-slate-100 shadow-md">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h4 className="text-xs font-bold text-slate-800">Aktivitas Kehadiran Terbaru</h4>
                <p className="text-[11px] text-slate-400 mt-0.5">Log absensi siswa terakhir hari ini</p>
              </div>
              <span className="text-[10px] bg-blue-50 text-blue-600 px-2.5 py-0.5 rounded-full border border-blue-100/30 font-bold">
                {todayLogs.length} Aktivitas
              </span>
            </div>

            {todayLogs.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-10 text-slate-400">
                <Clock className="w-10 h-10 stroke-1 mb-2 text-slate-200" />
                <p className="text-xs font-medium">Belum ada scan masuk hari ini</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table id="recent-logs-table" className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-100 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                      <th className="py-2.5">Siswa</th>
                      <th className="py-2.5">NISN</th>
                      <th className="py-2.5">Kelas</th>
                      <th className="py-2.5">Status</th>
                      <th className="py-2.5">Waktu Scan</th>
                      <th className="py-2.5">Metode</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50 text-xs text-slate-600">
                    {todayLogs.slice(0, 5).map((log) => (
                      <tr key={log.id} className="hover:bg-slate-50/40 transition-colors">
                        <td className="py-2.5 font-bold text-slate-700">{log.nama}</td>
                        <td className="py-2.5 font-mono text-slate-500">{log.nisn}</td>
                        <td className="py-2.5 text-slate-500">{log.kelas}</td>
                        <td className="py-2.5">
                          <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                            log.status === 'Hadir' ? 'bg-emerald-50 text-emerald-600 border border-emerald-100/50' :
                            log.status === 'Sakit' ? 'bg-amber-50 text-amber-600 border border-amber-100/50' :
                            log.status === 'Izin' ? 'bg-blue-50 text-blue-600 border border-blue-100/50' :
                            'bg-rose-50 text-rose-600 border border-rose-100/50'
                          }`}>
                            {log.status}
                          </span>
                        </td>
                        <td className="py-2.5 font-semibold text-slate-600">
                          {new Date(log.waktu).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })} WIB
                        </td>
                        <td className="py-2.5">
                          <span className="inline-flex items-center text-[10px] text-slate-400">
                            {log.scan_method === 'QR Code' ? (
                              <>
                                <QrCode className="w-3 h-3 mr-1 text-blue-500" />
                                QR Code
                              </>
                            ) : (
                              'Manual Admin'
                            )}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};
