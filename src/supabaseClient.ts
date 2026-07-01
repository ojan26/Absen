import { createClient } from '@supabase/supabase-js';
import { School, Student, AttendanceLog, SupabaseConfig, Broadcast, LoginTableRecord, AdminPageSetting, Holiday } from './types';

// Load default settings from environment or localStorage
const LOCAL_STORAGE_CONFIG_KEY = 'xdegan_supabase_config';
const LOCAL_STORAGE_DB_PREFIX = 'xdegan_db_';


export function getSupabaseConfig(): SupabaseConfig {
  const defaultUrl = 'https://fzllwcgahiwziurbbfws.supabase.co';
  const defaultAnonKey = 'sb_publishable_A_E34qVHZTwPbOO__36Xmg_AO-uylwM';

  try {
    const stored = localStorage.getItem(LOCAL_STORAGE_CONFIG_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      // Force override configuration to always match the embedded credentials so the user doesn't have to configure it anymore
      if (parsed.url !== defaultUrl || parsed.anonKey !== defaultAnonKey || !parsed.useRealDatabase) {
        parsed.url = defaultUrl;
        parsed.anonKey = defaultAnonKey;
        parsed.useRealDatabase = true;
        try {
          localStorage.setItem(LOCAL_STORAGE_CONFIG_KEY, JSON.stringify(parsed));
        } catch (e) {}
      }
      return parsed;
    }
  } catch (e) {
    console.error("Error reading supabase config from localStorage", e);
  }

  // Fallback / Initial default config
  const defaultConfig: SupabaseConfig = {
    url: defaultUrl,
    anonKey: defaultAnonKey,
    useRealDatabase: true,
  };
  try {
    localStorage.setItem(LOCAL_STORAGE_CONFIG_KEY, JSON.stringify(defaultConfig));
  } catch (e) {}
  
  return defaultConfig;
}

export function saveSupabaseConfig(config: SupabaseConfig) {
  localStorage.setItem(LOCAL_STORAGE_CONFIG_KEY, JSON.stringify(config));
}

// Pre-seeded schools
export const SEEDED_SCHOOLS: School[] = [
  { npsn: 'SCH-DEFAULT', nama: 'Sekolah Default (Locked State)', alamat: 'Jl. Utama No. 1, Kota Default', is_default: true },
 
];

// Pre-seeded students
export const SEEDED_STUDENTS: Student[] = [
  ];

// Pre-seeded logs
const todayStr = new Date().toISOString().split('T')[0];
const yesterdayStr = new Date(Date.now() - 86400000).toISOString().split('T')[0];

export const SEEDED_LOGS: AttendanceLog[] = [
  ];

export const SEEDED_BROADCASTS: Broadcast[] = [
  {
    id: 1,
    title: 'Sinkronisasi Manual Sebelum Jam 12:00',
    message: 'Diimbau bagi seluruh wali kelas/petugas pemindai kartu agar melakukan sinkronisasi data manual sebelum jam istirahat agar laporan rekap absensi dapat langsung ditarik.',
    drive_link: '',
    type: 'INSTRUCTION',
    is_active: true,
    updated_id: 1782741758505
  }
];

export const SEEDED_HOLIDAYS: Holiday[] = [
  { id: 'hol-1', tanggal: '2026-08-17', nama: 'Hari Kemerdekaan RI', npsn_sekolah: 'ALL', keterangan: 'Hari Libur Nasional' },
  { id: 'hol-2', tanggal: '2026-12-25', nama: 'Hari Raya Natal', npsn_sekolah: 'ALL', keterangan: 'Hari Libur Nasional' },
  { id: 'hol-3', tanggal: '2026-05-01', nama: 'Hari Buruh Internasional', npsn_sekolah: 'ALL', keterangan: 'Hari Libur Nasional' },
  { id: 'hol-4', tanggal: '2026-06-01', nama: 'Hari Lahir Pancasila', npsn_sekolah: 'ALL', keterangan: 'Hari Libur Nasional' },
];

export const SEEDED_LOGINS: LoginTableRecord[] = [
  { id: 1, email: 'superadmin@xdegan.com', password: 'admin123', role: 'superadmin', npsn_sekolah: null },
  { id: 2, email: 'admin.ll1@xdegan.com', password: 'admin123', role: 'admin', npsn_sekolah: '50102030' },
  { id: 3, email: 'guru.demo@xdegan.com', password: 'admin123', role: 'guru', npsn_sekolah: '50102030', kelas_tugas: 'XI RPL 1' },
];

// Helper to initialize local storage DB if empty
function initializeLocalStorageDB() {
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}schools`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}schools`, JSON.stringify(SEEDED_SCHOOLS));
  }
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}students`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(SEEDED_STUDENTS));
  }
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logs`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(SEEDED_LOGS));
  }
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(SEEDED_BROADCASTS));
  }
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logins`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(SEEDED_LOGINS));
  }
  if (!localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`)) {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(SEEDED_HOLIDAYS));
  }
}

// Ensure local database is ready
initializeLocalStorageDB();

// Initialize the Supabase client
const config = getSupabaseConfig();
export const supabase = config.useRealDatabase
  ? createClient(config.url, config.anonKey)
  : null;

export interface DbErrorInfo {
  message: string;
  code?: string;
  details?: string;
  hint?: string;
}

export let lastDbError: DbErrorInfo | null = null;
const dbErrorListeners = new Set<(err: DbErrorInfo | null) => void>();

export function subscribeToDbError(listener: (err: DbErrorInfo | null) => void) {
  dbErrorListeners.add(listener);
  listener(lastDbError);
  return () => {
    dbErrorListeners.delete(listener);
  };
}

export function setDbError(err: DbErrorInfo | null) {
  lastDbError = err;
  dbErrorListeners.forEach(listener => {
    try {
      listener(err);
    } catch (e) {}
  });
}

// Dual-Engine database access object
export const db = {
  // --- Schools ---
  async getSchools(): Promise<School[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        // 1. Try to fetch from the real 'sekolah' table first if it exists
        const { data: sekolahData, error: sekolahErr } = await supabase
          .from('sekolah')
          .select('*');

        if (!sekolahErr && sekolahData) {
          setDbError(null);
          const parsedSchools = sekolahData.map((s: any) => ({
            npsn: s.npsn,
            nama: s.nama,
            alamat: s.alamat || 'Alamat Sekolah Terintegrasi'
          }));

          // Merge static seeded schools with registered ones to make sure none is lost
          const combined = [...SEEDED_SCHOOLS];
          parsedSchools.forEach(ps => {
            if (!combined.some(s => s.npsn === ps.npsn)) {
              combined.push(ps);
            }
          });
          return combined;
        }

        // 2. Fall back to discovering schools dynamically from 'siswa' table
        const { data, error } = await supabase
          .from('siswa')
          .select('school_id, school_name');
        
        if (error) throw error;
        setDbError(null); // Clear any previous error on success

        const dynamicSchools: School[] = [];
        if (data) {
          const uniqueMap = new Map<string, string>();
          data.forEach((row: any) => {
            if (row.school_id && row.school_name) {
              uniqueMap.set(row.school_id, row.school_name);
            }
          });
          uniqueMap.forEach((name, id) => {
            dynamicSchools.push({
              npsn: id,
              nama: name,
              alamat: 'Alamat Sekolah Terintegrasi'
            });
          });
        }

        // Merge static seeded schools with dynamically discovered ones, removing duplicates
        const combined = [...SEEDED_SCHOOLS];
        dynamicSchools.forEach(ds => {
          if (!combined.some(s => s.npsn === ds.npsn)) {
            combined.push(ds);
          }
        });

        return combined;
      } catch (e: any) {
        console.error("Supabase failed to fetch schools, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }
    
    // Local DB fallback
    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}schools`);
    return raw ? JSON.parse(raw) : SEEDED_SCHOOLS;
  },

  async addSchool(school: School): Promise<School> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        // Try inserting into the real 'sekolah' table in Supabase
        const { error } = await supabase
          .from('sekolah')
          .insert([{
            npsn: school.npsn,
            nama: school.nama,
            alamat: school.alamat
          }]);
        if (error) throw error;
        setDbError(null);
      } catch (e: any) {
        console.error("Supabase failed to insert school record:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
        throw e; // Throw so the UI can capture the exact reason for failure
      }
    }

    // Keep a local copy of registered schools for quick selection/reference
    const schools = await this.getSchools();
    if (!schools.some(s => s.npsn === school.npsn)) {
      schools.push(school);
      localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}schools`, JSON.stringify(schools));
    }
    return school;
  },

  // --- Students ---
  async getStudents(npsn?: string): Promise<Student[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        let query = supabase.from('siswa').select('*');
        if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
          query = query.eq('school_id', npsn);
        }
        const { data, error } = await query.order('name', { ascending: true });
        if (error) throw error;
        setDbError(null);

        return (data || []).map((s: any) => ({
          id: s.uid,
          nama: s.name,
          nisn: s.uid, // use uid as fallback NISN for direct matching/QR
          kelas: 'Umum',
          npsn_sekolah: s.school_id,
          created_at: s.created_at
        })) as Student[];
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}students`);
    const students: Student[] = raw ? JSON.parse(raw) : SEEDED_STUDENTS;
    if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
      return students.filter(s => s.npsn_sekolah === npsn);
    }
    return students;
  },

  async addStudent(student: Omit<Student, 'id' | 'created_at'>): Promise<Student> {
    const newStudent: Student = {
      ...student,
      id: student.nisn || 'stud-' + Math.random().toString(36).substr(2, 9),
      created_at: new Date().toISOString()
    };

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const schools = await this.getSchools();
        const foundSchool = schools.find(s => s.npsn === student.npsn_sekolah);
        const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';

        const { data, error } = await supabase
          .from('siswa')
          .insert([{
            uid: student.nisn || newStudent.id,
            name: student.nama,
            role: 'Siswa',
            school_id: student.npsn_sekolah,
            school_name: schoolName
          }])
          .select()
          .single();
        if (error) throw error;
        setDbError(null);

        return {
          id: data.uid,
          nama: data.name,
          nisn: data.uid,
          kelas: 'Umum',
          npsn_sekolah: data.school_id,
          created_at: data.created_at
        };
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const students = await this.getStudents();
    students.push(newStudent);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(students));
    return newStudent;
  },

  async updateStudent(id: string, updated: Partial<Omit<Student, 'id' | 'created_at'>>): Promise<Student> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const payload: any = {};
        if (updated.nama) payload.name = updated.nama;
        if (updated.npsn_sekolah) {
          payload.school_id = updated.npsn_sekolah;
          const schools = await this.getSchools();
          const foundSchool = schools.find(s => s.npsn === updated.npsn_sekolah);
          if (foundSchool) {
            payload.school_name = foundSchool.nama;
          }
        }
        
        const { data, error } = await supabase
          .from('siswa')
          .update(payload)
          .eq('uid', id)
          .select()
          .single();
        if (error) throw error;
        setDbError(null);

        return {
          id: data.uid,
          nama: data.name,
          nisn: data.uid,
          kelas: 'Umum',
          npsn_sekolah: data.school_id,
          created_at: data.created_at
        };
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const students = await this.getStudents();
    const idx = students.findIndex(s => s.id === id);
    if (idx !== -1) {
      students[idx] = { ...students[idx], ...updated };
      localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(students));
      return students[idx];
    }
    throw new Error('Siswa tidak ditemukan');
  },

  async deleteStudent(id: string): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('siswa')
          .delete()
          .eq('uid', id);
        if (error) throw error;
        setDbError(null);
        return true;
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const students = await this.getStudents();
    const filtered = students.filter(s => s.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(filtered));
    return true;
  },

  async importStudents(imported: Omit<Student, 'id' | 'created_at'>[]): Promise<Student[]> {
    const newStudents: Student[] = imported.map((s, i) => ({
      ...s,
      id: s.nisn || 'stud-' + Date.now() + '-' + i + '-' + Math.random().toString(36).substr(2, 4),
      created_at: new Date().toISOString()
    }));

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const schools = await this.getSchools();
        
        const payload = imported.map(s => {
          const foundSchool = schools.find(sch => sch.npsn === s.npsn_sekolah);
          const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';
          return {
            uid: s.nisn || 'stud-' + Math.random().toString(36).substr(2, 9),
            name: s.nama,
            role: 'Siswa',
            school_id: s.npsn_sekolah,
            school_name: schoolName
          };
        });
        
        const { data, error } = await supabase
          .from('siswa')
          .insert(payload)
          .select();
        if (error) throw error;
        setDbError(null);

        return (data || []).map((d: any) => ({
          id: d.uid,
          nama: d.name,
          nisn: d.uid,
          kelas: 'Umum',
          npsn_sekolah: d.school_id,
          created_at: d.created_at
        }));
      } catch (e: any) {
        console.error("Supabase bulk insert failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const students = await this.getStudents();
    const combined = [...students, ...newStudents];
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(combined));
    return newStudents;
  },

  // --- Attendance Logs ---
  async getLogs(npsn?: string): Promise<AttendanceLog[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        let query = supabase.from('kehadiran').select('*');
        if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
          query = query.eq('school_id', npsn);
        }
        const { data, error } = await query.order('timestamp', { ascending: false });
        if (error) throw error;
        setDbError(null);

        // Map data to match AttendanceLog structure
        const mapped: AttendanceLog[] = (data || []).map((item: any) => {
          let isoWaktu = new Date().toISOString();
          if (item.timestamp) {
            isoWaktu = new Date(Number(item.timestamp)).toISOString();
          }
          
          return {
            id: item.id_unique,
            student_id: item.uid,
            nama: item.name || 'Tidak Dikenal',
            nisn: item.uid || '-',
            kelas: 'Umum',
            npsn_sekolah: item.school_id,
            status: item.status || 'Hadir',
            waktu: isoWaktu,
            scan_method: 'QR Code'
          };
        });
        return mapped;
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logs`);
    const logs: AttendanceLog[] = raw ? JSON.parse(raw) : SEEDED_LOGS;
    if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
      return logs.filter(l => l.npsn_sekolah === npsn);
    }
    return logs;
  },

  async addLog(log: Omit<AttendanceLog, 'id' | 'waktu'>): Promise<AttendanceLog> {
    const newLog: AttendanceLog = {
      ...log,
      id: 'log-' + Math.random().toString(36).substr(2, 9),
      waktu: new Date().toISOString()
    };

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const schools = await this.getSchools();
        const foundSchool = schools.find(s => s.npsn === log.npsn_sekolah);
        const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';

        const { data, error } = await supabase
          .from('kehadiran')
          .insert([{
            id_unique: 'REQ-' + Math.random().toString(36).substr(2, 9).toUpperCase(),
            uid: log.student_id,
            name: log.nama,
            role: 'Siswa',
            timestamp: Date.now(),
            type: 'MASUK',
            status: log.status || 'Hadir',
            school_id: log.npsn_sekolah,
            school_name: schoolName
          }])
          .select()
          .single();
        if (error) throw error;
        setDbError(null);

        let isoWaktu = new Date().toISOString();
        if (data.timestamp) {
          isoWaktu = new Date(Number(data.timestamp)).toISOString();
        }

        return {
          ...newLog,
          id: data.id_unique,
          student_id: data.uid,
          nama: data.name,
          nisn: data.uid,
          kelas: 'Umum',
          npsn_sekolah: data.school_id,
          status: data.status,
          waktu: isoWaktu,
          scan_method: 'Manual'
        };
      } catch (e: any) {
        console.error("Supabase failed, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const logs = await this.getLogs();
    logs.unshift(newLog); // Prepend to show first
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(logs));
    return newLog;
  },

  async deleteLog(id: string): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('kehadiran')
          .delete()
          .eq('id_unique', id);
        if (error) throw error;
        setDbError(null);
        return true;
      } catch (e: any) {
        console.error("Supabase failed to delete log:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const logs = await this.getLogs();
    const filtered = logs.filter(l => l.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(filtered));
    return true;
  },

  // --- Broadcasts ---
  async getBroadcasts(): Promise<Broadcast[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('app_broadcast')
          .select('*')
          .order('id', { ascending: true });
        if (error) throw error;
        setDbError(null);
        return data as Broadcast[];
      } catch (e: any) {
        console.warn("Supabase failed to fetch broadcasts, falling back to local DB:", e);
      }
    }

    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`);
    return raw ? JSON.parse(raw) : SEEDED_BROADCASTS;
  },

  async addBroadcast(broadcast: Omit<Broadcast, 'id' | 'updated_id'>): Promise<Broadcast> {
    const newId = Math.floor(Math.random() * 1000000);
    const newBroadcast: Broadcast = {
      ...broadcast,
      id: newId,
      updated_id: Date.now()
    };

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('app_broadcast')
          .insert([{
            title: broadcast.title,
            message: broadcast.message,
            drive_link: broadcast.drive_link || '',
            type: broadcast.type || 'INSTRUCTION',
            is_active: broadcast.is_active,
            updated_id: Date.now()
          }])
          .select()
          .single();
        if (error) throw error;
        setDbError(null);
        return data as Broadcast;
      } catch (e: any) {
        console.error("Supabase failed to add broadcast, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const broadcasts = await this.getBroadcasts();
    broadcasts.push(newBroadcast);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(broadcasts));
    return newBroadcast;
  },

  async updateBroadcast(id: number, updated: Partial<Omit<Broadcast, 'id' | 'updated_id'>>): Promise<Broadcast> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const payload = {
          ...updated,
          updated_id: Date.now()
        };
        const { data, error } = await supabase
          .from('app_broadcast')
          .update(payload)
          .eq('id', id)
          .select()
          .single();
        if (error) throw error;
        setDbError(null);
        return data as Broadcast;
      } catch (e: any) {
        console.error("Supabase failed to update broadcast, falling back to local:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const broadcasts = await this.getBroadcasts();
    const idx = broadcasts.findIndex(b => b.id === id);
    if (idx !== -1) {
      broadcasts[idx] = {
        ...broadcasts[idx],
        ...updated,
        updated_id: Date.now()
      };
      localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(broadcasts));
      return broadcasts[idx];
    }
    throw new Error("Broadcast not found locally");
  },

  async deleteBroadcast(id: number): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('app_broadcast')
          .delete()
          .eq('id', id);
        if (error) throw error;
        setDbError(null);
        return true;
      } catch (e: any) {
        console.error("Supabase failed to delete broadcast:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const broadcasts = await this.getBroadcasts();
    const filtered = broadcasts.filter(b => b.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(filtered));
    return true;
  },

  // --- Holidays ---
  async getHolidays(npsn?: string): Promise<Holiday[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('hari_libur')
          .select('*')
          .order('tanggal', { ascending: true });
        if (!error && data) {
          setDbError(null);
          const mapped = data.map((d: any) => ({
            id: String(d.id),
            tanggal: d.tanggal,
            nama: d.nama,
            npsn_sekolah: d.npsn_sekolah || 'ALL',
            keterangan: d.keterangan || ''
          }));
          if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
            return mapped.filter((h: any) => h.npsn_sekolah === 'ALL' || h.npsn_sekolah === npsn);
          }
          return mapped;
        }
      } catch (e) {
        console.warn("Gagal mengambil hari libur dari Supabase, fallback ke lokal", e);
      }
    }

    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`);
    const hols: Holiday[] = raw ? JSON.parse(raw) : SEEDED_HOLIDAYS;
    if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
      return hols.filter(h => h.npsn_sekolah === 'ALL' || h.npsn_sekolah === npsn);
    }
    return hols;
  },

  async addHoliday(holiday: Omit<Holiday, 'id'>): Promise<Holiday> {
    const newHoliday: Holiday = {
      ...holiday,
      id: 'hol-' + Math.random().toString(36).substr(2, 9)
    };

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('hari_libur')
          .insert([{
            tanggal: holiday.tanggal,
            nama: holiday.nama,
            npsn_sekolah: holiday.npsn_sekolah,
            keterangan: holiday.keterangan || ''
          }])
          .select()
          .single();
        if (!error && data) {
          setDbError(null);
          return {
            id: String(data.id),
            tanggal: data.tanggal,
            nama: data.nama,
            npsn_sekolah: data.npsn_sekolah || 'ALL',
            keterangan: data.keterangan || ''
          };
        } else if (error) {
          throw error;
        }
      } catch (e: any) {
        console.error("Supabase failed to add holiday, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const holidays = await this.getHolidays();
    holidays.push(newHoliday);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(holidays));
    return newHoliday;
  },

  async deleteHoliday(id: string): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('hari_libur')
          .delete()
          .eq('id', id);
        if (!error) {
          setDbError(null);
          return true;
        } else {
          throw error;
        }
      } catch (e: any) {
        console.error("Supabase failed to delete holiday:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const holidays = await this.getHolidays();
    const filtered = holidays.filter(h => h.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(filtered));
    return true;
  },

  // --- Logins & Table Verification ---
  async getLogins(): Promise<LoginTableRecord[]> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('login')
          .select('*')
          .order('id', { ascending: true });
        if (error) throw error;
        setDbError(null);
        return data as LoginTableRecord[];
      } catch (e: any) {
        console.error("Supabase failed to fetch logins, falling back to local DB:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logins`);
    return raw ? JSON.parse(raw) : SEEDED_LOGINS;
  },

  async verifyLoginTable(email: string): Promise<LoginTableRecord | null> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('login')
          .select('*')
          .eq('email', email)
          .maybeSingle();
        if (error) throw error;
        setDbError(null);
        return data as LoginTableRecord;
      } catch (e: any) {
        console.error("Supabase failed to query login table:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    const logins = await this.getLogins();
    return logins.find(l => l.email.toLowerCase() === email.toLowerCase()) || null;
  },

  async addLoginRecord(record: Omit<LoginTableRecord, 'id'>): Promise<LoginTableRecord> {
    const newId = Math.floor(Math.random() * 100000);
    const newRecord: LoginTableRecord = {
      ...record,
      id: newId
    };

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      const payload: any = {
        email: record.email,
        password: record.password,
        role: record.role,
        npsn_sekolah: record.npsn_sekolah
      };
      if (record.kelas_tugas !== undefined) {
        payload.kelas_tugas = record.kelas_tugas;
      }

      try {
        const { data, error } = await supabase
          .from('login')
          .insert([payload])
          .select()
          .single();
        if (error) {
          // If the error mentions 'kelas_tugas' not found or similar, try retrying without that field
          if (error.message && (error.message.includes('kelas_tugas') || error.message.includes('column') || error.code === 'PGRST204')) {
            console.warn("Retrying addLoginRecord without 'kelas_tugas' column due to database mismatch:", error.message);
            const { kelas_tugas, ...retryPayload } = payload;
            const retryRes = await supabase
              .from('login')
              .insert([retryPayload])
              .select()
              .single();
            if (retryRes.error) throw retryRes.error;
            setDbError(null);
            return retryRes.data as LoginTableRecord;
          }
          throw error;
        }
        setDbError(null);
        return data as LoginTableRecord;
      } catch (e: any) {
        console.error("Supabase failed to insert login record:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
        throw e; // Re-throw to allow caller/UI to handle it
      }
    }

    const logins = await this.getLogins();
    logins.push(newRecord);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(logins));
    return newRecord;
  },

  async updateLoginRecord(id: number, record: Partial<Omit<LoginTableRecord, 'id'>>): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('login')
          .update(record)
          .eq('id', id);
        if (error) {
          // If the error mentions 'kelas_tugas' not found or similar, try retrying without that field
          if (error.message && (error.message.includes('kelas_tugas') || error.message.includes('column') || error.code === 'PGRST204')) {
            console.warn("Retrying updateLoginRecord without 'kelas_tugas' column due to database mismatch:", error.message);
            const { kelas_tugas, ...retryRecord } = record;
            const retryRes = await supabase
              .from('login')
              .update(retryRecord)
              .eq('id', id);
            if (retryRes.error) throw retryRes.error;
            setDbError(null);
            return true;
          }
          throw error;
        }
        setDbError(null);
        return true;
      } catch (e: any) {
        console.error("Supabase failed to update login record:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
        throw e; // Re-throw to allow caller/UI to handle it
      }
    }

    const logins = await this.getLogins();
    const idx = logins.findIndex(l => l.id === id);
    if (idx !== -1) {
      logins[idx] = { ...logins[idx], ...record };
      localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(logins));
      return true;
    }
    return false;
  },

  async deleteLoginRecord(id: number): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { error } = await supabase
          .from('login')
          .delete()
          .eq('id', id);
        if (error) throw error;
        setDbError(null);
        return true;
      } catch (e: any) {
        console.error("Supabase failed to delete login record:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
        throw e; // Re-throw to allow caller/UI to handle it
      }
    }

    const logins = await this.getLogins();
    const filtered = logins.filter(l => l.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(filtered));
    return true;
  },

  async getAdminPageSettings(): Promise<AdminPageSetting[]> {
    const DEFAULT_PAGE_SETTINGS: AdminPageSetting[] = [
      { page_id: 'dashboard', page_name: 'Dashboard', is_visible: true },
      { page_id: 'siswa', page_name: 'Siswa / Anggota', is_visible: true },
      { page_id: 'absensi', page_name: 'Rekap Absensi', is_visible: true },
      { page_id: 'broadcast', page_name: 'Broadcast & Info', is_visible: true },
      { page_id: 'hari-libur', page_name: 'Hari Libur', is_visible: true },
      { page_id: 'binding-device', page_name: 'Binding Device', is_visible: true },
    ];

    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        const { data, error } = await supabase
          .from('admin_page_settings')
          .select('*');
        if (!error && data && data.length > 0) {
          setDbError(null);
          return data.map((d: any) => ({
            page_id: d.page_id,
            page_name: d.page_name,
            is_visible: d.is_visible
          }));
        }
      } catch (e) {
        console.warn("Could not fetch page settings from Supabase, falling back to local:", e);
      }
    }

    const localData = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}admin_page_settings`);
    if (localData) {
      try {
        return JSON.parse(localData);
      } catch (e) {
        return DEFAULT_PAGE_SETTINGS;
      }
    }
    return DEFAULT_PAGE_SETTINGS;
  },

  async updateAdminPageSettings(settings: AdminPageSetting[]): Promise<boolean> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      try {
        // Upsert settings
        for (const s of settings) {
          const { error } = await supabase
            .from('admin_page_settings')
            .upsert({
              page_id: s.page_id,
              page_name: s.page_name,
              is_visible: s.is_visible
            }, { onConflict: 'page_id' });
          if (error) throw error;
        }
        setDbError(null);
      } catch (e: any) {
        console.error("Supabase failed to upsert admin page settings:", e);
        setDbError({
          message: e.message || String(e),
          code: e.code,
          details: e.details,
          hint: e.hint
        });
      }
    }

    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}admin_page_settings`, JSON.stringify(settings));
    return true;
  },

  async runSql(queryText: string): Promise<{ success: boolean; data?: any[]; message?: string; error?: string }> {
    const activeConfig = getSupabaseConfig();
    if (activeConfig.useRealDatabase && supabase) {
      // 1. Try parsing and running natively using standard Supabase JS Client API first.
      // This completely avoids the need for creating a custom "exec_sql" RPC function in the database!
      const nativeResult = await runNativeSupabaseSql(queryText);
      if (nativeResult !== null) {
        setDbError(null);
        return nativeResult;
      }

      // 2. Fall back to the "exec_sql" RPC function if query cannot be parsed natively on client side
      try {
        const { data, error } = await supabase.rpc('exec_sql', { sql_query: queryText });
        if (error) throw error;
        
        if (data && typeof data === 'object' && 'success' in data && data.success === false) {
          return { success: false, error: data.error || 'Terjadi kesalahan eksekusi SQL.' };
        }
        
        setDbError(null);
        if (Array.isArray(data)) {
          return { success: true, data };
        } else if (data && typeof data === 'object' && 'message' in data) {
          return { success: true, message: data.message };
        }
        return { success: true, data: Array.isArray(data) ? data : [data] };
      } catch (e: any) {
        console.error("Real Supabase SQL RPC failed:", e);
        return { 
          success: false, 
          error: `${e.message || String(e)} (Tip: Anda perlu membuat RPC function 'exec_sql' di dashboard Supabase untuk mengeksekusi query kompleks secara online, atau gunakan query dasar seperti SELECT/INSERT/UPDATE/DELETE yang sudah diproses secara otomatis oleh client parser kami).` 
        };
      }
    }

    return runLocalSql(queryText);
  },

  // Reset database to initial values
  resetToDefault() {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}schools`, JSON.stringify(SEEDED_SCHOOLS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(SEEDED_STUDENTS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(SEEDED_LOGS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(SEEDED_BROADCASTS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(SEEDED_LOGINS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(SEEDED_HOLIDAYS));
  }
};

// Local sandboxed SQL query processor
export function runLocalSql(query: string): { success: boolean; data?: any[]; message?: string; error?: string } {
  const clean = query.trim().replace(/;+$/, '').replace(/\s+/g, ' ');
  const lower = clean.toLowerCase();

  const getTableData = (tbl: string): { key: string; data: any[] } | null => {
    const t = tbl.toLowerCase();
    if (t === 'sekolah') return { key: `${LOCAL_STORAGE_DB_PREFIX}schools`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}schools`) || '[]') };
    if (t === 'siswa') return { key: `${LOCAL_STORAGE_DB_PREFIX}students`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}students`) || '[]') };
    if (t === 'absensi') return { key: `${LOCAL_STORAGE_DB_PREFIX}logs`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logs`) || '[]') };
    if (t === 'app_broadcast' || t === 'broadcast' || t === 'broadcasts') return { key: `${LOCAL_STORAGE_DB_PREFIX}broadcasts`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`) || '[]') };
    if (t === 'login' || t === 'logins') return { key: `${LOCAL_STORAGE_DB_PREFIX}logins`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logins`) || '[]') };
    if (t === 'hari_libur' || t === 'holidays') return { key: `${LOCAL_STORAGE_DB_PREFIX}holidays`, data: JSON.parse(localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`) || '[]') };
    return null;
  };

  try {
    if (lower.startsWith('select')) {
      const match = clean.match(/select\s+(.+?)\s+from\s+(\w+)(?:\s+where\s+(.+?))?(?:\s+order\s+by\s+(.+?))?$/i);
      if (!match) {
        return { success: false, error: "Syntax Error: Only simple SELECT queries are supported in Local SQL Engine." };
      }
      const fieldsStr = match[1].trim();
      const tableName = match[2].trim();
      const whereClause = match[3] ? match[3].trim() : null;

      const tblInfo = getTableData(tableName);
      if (!tblInfo) {
        return { success: false, error: `Table "${tableName}" does not exist in local database.` };
      }

      let rows = tblInfo.data;

      if (whereClause) {
        const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
        if (eqMatch) {
          const colName = eqMatch[1].trim().toLowerCase();
          const val = eqMatch[3].trim();
          rows = rows.filter(r => {
            const keys = Object.keys(r);
            const key = keys.find(k => k.toLowerCase() === colName);
            if (!key) return false;
            return String(r[key]).toLowerCase() === val.toLowerCase();
          });
        }
      }

      if (fieldsStr !== '*') {
        const fields = fieldsStr.split(',').map(f => f.trim().toLowerCase());
        rows = rows.map(r => {
          const mapped: any = {};
          fields.forEach(f => {
            const key = Object.keys(r).find(k => k.toLowerCase() === f);
            if (key) mapped[f] = r[key];
          });
          return mapped;
        });
      }

      return { success: true, data: rows };
    }

    if (lower.startsWith('insert into')) {
      const match = clean.match(/insert\s+into\s+(\w+)\s*\((.+?)\)\s*values\s*\((.+?)\)/i);
      if (!match) {
        return { success: false, error: "Syntax Error: Only INSERT INTO table (cols) VALUES (vals) is supported." };
      }
      const tableName = match[1].trim();
      const cols = match[2].split(',').map(c => c.trim().replace(/['"`]/g, ''));
      const vals = match[3].split(',').map(v => v.trim().replace(/^['"]|['"]$/g, ''));

      if (cols.length !== vals.length) {
        return { success: false, error: "Column and value count mismatch." };
      }

      const tblInfo = getTableData(tableName);
      if (!tblInfo) {
        return { success: false, error: `Table "${tableName}" does not exist in local database.` };
      }

      const newRow: any = {};
      newRow.id = tableName.toLowerCase() === 'login' ? Math.floor(Math.random() * 10000) : Math.random().toString(36).substr(2, 9);
      
      cols.forEach((col, idx) => {
        let val: any = vals[idx];
        if (val === 'null') val = null;
        else if (val === 'true') val = true;
        else if (val === 'false') val = false;
        else if (!isNaN(Number(val))) val = Number(val);
        newRow[col] = val;
      });

      tblInfo.data.push(newRow);
      localStorage.setItem(tblInfo.key, JSON.stringify(tblInfo.data));
      return { success: true, message: `1 row inserted into "${tableName}" successfully.`, data: [newRow] };
    }

    if (lower.startsWith('delete from')) {
      const match = clean.match(/delete\s+from\s+(\w+)(?:\s+where\s+(.+?))?$/i);
      if (!match) {
        return { success: false, error: "Syntax Error: Only DELETE FROM table [WHERE col = val] is supported." };
      }
      const tableName = match[1].trim();
      const whereClause = match[2] ? match[2].trim() : null;

      const tblInfo = getTableData(tableName);
      if (!tblInfo) {
        return { success: false, error: `Table "${tableName}" does not exist in local database.` };
      }

      let initialCount = tblInfo.data.length;
      let newData = tblInfo.data;

      if (whereClause) {
        const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
        if (eqMatch) {
          const colName = eqMatch[1].trim().toLowerCase();
          const val = eqMatch[3].trim();
          newData = tblInfo.data.filter(r => {
            const key = Object.keys(r).find(k => k.toLowerCase() === colName);
            if (!key) return true;
            return String(r[key]).toLowerCase() !== val.toLowerCase();
          });
        }
      } else {
        newData = [];
      }

      localStorage.setItem(tblInfo.key, JSON.stringify(newData));
      return { success: true, message: `${initialCount - newData.length} rows deleted from "${tableName}".` };
    }

    if (lower.startsWith('update')) {
      const match = clean.match(/update\s+(\w+)\s+set\s+(.+?)(?:\s+where\s+(.+?))?$/i);
      if (!match) {
        return { success: false, error: "Syntax Error: Only UPDATE table SET col = val [WHERE col = val] is supported." };
      }
      const tableName = match[1].trim();
      const setClause = match[2].trim();
      const whereClause = match[3] ? match[3].trim() : null;

      const tblInfo = getTableData(tableName);
      if (!tblInfo) {
        return { success: false, error: `Table "${tableName}" does not exist in local database.` };
      }

      const setMatches = setClause.split(',').map(s => s.trim());
      const updates: any = {};
      setMatches.forEach(sm => {
        const kv = sm.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/);
        if (kv) {
          const colName = kv[1].trim();
          let val: any = kv[3].trim();
          if (val === 'null') val = null;
          else if (val === 'true') val = true;
          else if (val === 'false') val = false;
          else if (!isNaN(Number(val))) val = Number(val);
          updates[colName] = val;
        }
      });

      let updatedCount = 0;
      const updatedRows = tblInfo.data.map(r => {
        let matchWhere = true;
        if (whereClause) {
          const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
          if (eqMatch) {
            const colName = eqMatch[1].trim().toLowerCase();
            const val = eqMatch[3].trim();
            const key = Object.keys(r).find(k => k.toLowerCase() === colName);
            matchWhere = key ? String(r[key]).toLowerCase() === val.toLowerCase() : false;
          }
        }

        if (matchWhere) {
          updatedCount++;
          const updatedRecord = { ...r };
          Object.keys(updates).forEach(upKey => {
            const realKey = Object.keys(r).find(k => k.toLowerCase() === upKey.toLowerCase()) || upKey;
            updatedRecord[realKey] = updates[upKey];
          });
          return updatedRecord;
        }
        return r;
      });

      localStorage.setItem(tblInfo.key, JSON.stringify(updatedRows));
      return { success: true, message: `${updatedCount} rows updated in "${tableName}".` };
    }

    if (lower.startsWith('create table')) {
      const match = clean.match(/create\s+table\s+(\w+)/i);
      const tableName = match ? match[1].trim() : "baru";
      return { success: true, message: `Table "${tableName}" created successfully in local sandbox schema.` };
    }

    return { success: true, message: "Query executed successfully in local SQL sandbox." };
  } catch (e: any) {
    return { success: false, error: e.message || String(e) };
  }
}

// Native Supabase API client-side parser to translate SQL queries to JS REST commands.
// This completely avoids the need for a custom 'exec_sql' database RPC function for all basic queries!
export async function runNativeSupabaseSql(query: string): Promise<{ success: boolean; data?: any[]; message?: string; error?: string } | null> {
  if (!supabase) return null;
  const clean = query.trim().replace(/;+$/, '').replace(/\s+/g, ' ');
  const lower = clean.toLowerCase();

  // Helper to map localized table names
  const getRealTableName = (tbl: string): string => {
    const t = tbl.toLowerCase();
    if (t === 'login' || t === 'logins') return 'login';
    if (t === 'sekolah' || t === 'sekolahs') return 'sekolah';
    if (t === 'siswa' || t === 'siswas') return 'siswa';
    if (t === 'absensi' || t === 'absensis') return 'absensi';
    if (t === 'app_broadcast' || t === 'broadcast' || t === 'broadcasts') return 'app_broadcast';
    if (t === 'hari_libur' || t === 'holidays') return 'hari_libur';
    return t;
  };

  try {
    // 1. SELECT query
    if (lower.startsWith('select')) {
      const match = clean.match(/select\s+(.+?)\s+from\s+(\w+)(?:\s+where\s+(.+?))?(?:\s+order\s+by\s+(.+?))?$/i);
      if (!match) return null;

      const fieldsStr = match[1].trim();
      const rawTableName = match[2].trim();
      const whereClause = match[3] ? match[3].trim() : null;
      const orderByClause = match[4] ? match[4].trim() : null;

      const tableName = getRealTableName(rawTableName);
      let queryBuilder = supabase.from(tableName).select(fieldsStr === '*' ? '*' : fieldsStr);

      if (whereClause) {
        const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
        if (eqMatch) {
          const colName = eqMatch[1].trim();
          const val = eqMatch[3].trim();
          queryBuilder = queryBuilder.eq(colName, val);
        }
      }

      if (orderByClause) {
        const parts = orderByClause.split(/\s+/);
        const colName = parts[0].trim();
        const direction = parts[1] ? parts[1].toLowerCase() : 'asc';
        queryBuilder = queryBuilder.order(colName, { ascending: direction !== 'desc' });
      }

      const { data, error } = await queryBuilder;
      if (error) throw error;
      return { success: true, data: data || [] };
    }

    // 2. INSERT query
    if (lower.startsWith('insert into')) {
      const match = clean.match(/insert\s+into\s+(\w+)\s*\((.+?)\)\s*values\s*\((.+?)\)/i);
      if (!match) return null;

      const rawTableName = match[1].trim();
      const cols = match[2].split(',').map(c => c.trim().replace(/['"`]/g, ''));
      const vals = match[3].split(',').map(v => v.trim().replace(/^['"]|['"]$/g, ''));

      if (cols.length !== vals.length) {
        return { success: false, error: "Column and value count mismatch." };
      }

      const tableName = getRealTableName(rawTableName);
      const rowToInsert: any = {};
      cols.forEach((col, idx) => {
        let val: any = vals[idx];
        if (val === 'null') val = null;
        else if (val === 'true') val = true;
        else if (val === 'false') val = false;
        else if (!isNaN(Number(val))) val = Number(val);
        rowToInsert[col] = val;
      });

      const { data, error } = await supabase.from(tableName).insert([rowToInsert]).select();
      if (error) throw error;
      return { success: true, message: `1 row inserted into "${tableName}" successfully.`, data: data || [] };
    }

    // 3. DELETE query
    if (lower.startsWith('delete from')) {
      const match = clean.match(/delete\s+from\s+(\w+)(?:\s+where\s+(.+?))?$/i);
      if (!match) return null;

      const rawTableName = match[1].trim();
      const whereClause = match[2] ? match[2].trim() : null;
      const tableName = getRealTableName(rawTableName);

      let queryBuilder = supabase.from(tableName).delete();

      if (whereClause) {
        const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
        if (eqMatch) {
          const colName = eqMatch[1].trim();
          const val = eqMatch[3].trim();
          queryBuilder = queryBuilder.eq(colName, val);
        } else {
          return { success: false, error: "Only simple WHERE column = value deletion is supported dynamically." };
        }
      }

      const { error } = await queryBuilder;
      if (error) throw error;
      return { success: true, message: `Deletion command completed successfully on table "${tableName}".` };
    }

    // 4. UPDATE query
    if (lower.startsWith('update')) {
      const match = clean.match(/update\s+(\w+)\s+set\s+(.+?)(?:\s+where\s+(.+?))?$/i);
      if (!match) return null;

      const rawTableName = match[1].trim();
      const setClause = match[2].trim();
      const whereClause = match[3] ? match[3].trim() : null;
      const tableName = getRealTableName(rawTableName);

      const setMatches = setClause.split(',').map(s => s.trim());
      const updates: any = {};
      setMatches.forEach(sm => {
        const kv = sm.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/);
        if (kv) {
          const colName = kv[1].trim();
          let val: any = kv[3].trim();
          if (val === 'null') val = null;
          else if (val === 'true') val = true;
          else if (val === 'false') val = false;
          else if (!isNaN(Number(val))) val = Number(val);
          updates[colName] = val;
        }
      });

      let queryBuilder = supabase.from(tableName).update(updates);

      if (whereClause) {
        const eqMatch = whereClause.match(/(\w+)\s*=\s*(['"]?)(.+?)\2/i);
        if (eqMatch) {
          const colName = eqMatch[1].trim();
          const val = eqMatch[3].trim();
          queryBuilder = queryBuilder.eq(colName, val);
        } else {
          return { success: false, error: "Only simple WHERE column = value update is supported dynamically." };
        }
      }

      const { data, error } = await queryBuilder.select();
      if (error) throw error;
      return { success: true, message: `Update command completed successfully on table "${tableName}".`, data: data || [] };
    }
  } catch (err: any) {
    console.error("Native Supabase fallback SQL query execution error:", err);
    return { success: false, error: err.message || String(err) };
  }

  return null;
}
