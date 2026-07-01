import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously } from 'firebase/auth';
import {
  getFirestore,
  doc,
  setDoc,
  getDoc,
  getDocs,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
  orderBy,
  getDocFromServer,
  writeBatch
} from 'firebase/firestore';
import { School, Student, AttendanceLog, Broadcast, LoginTableRecord, AdminPageSetting, Holiday } from './types';
import firebaseConfig from '../firebase-applet-config.json';

// Initialize Firebase with dynamic environment override capability
const getFirebaseOverride = (key: string, envVal: string, fileVal: string) => {
  try {
    const custom = localStorage.getItem(`firebase_custom_${key}`);
    if (custom) return custom;
  } catch (e) {}
  return envVal || fileVal;
};

const resolvedConfig = {
  apiKey: getFirebaseOverride('apiKey', import.meta.env.VITE_FIREBASE_API_KEY, firebaseConfig.apiKey),
  authDomain: getFirebaseOverride('authDomain', import.meta.env.VITE_FIREBASE_AUTH_DOMAIN, firebaseConfig.authDomain),
  projectId: getFirebaseOverride('projectId', import.meta.env.VITE_FIREBASE_PROJECT_ID, firebaseConfig.projectId),
  storageBucket: getFirebaseOverride('storageBucket', import.meta.env.VITE_FIREBASE_STORAGE_BUCKET, firebaseConfig.storageBucket),
  messagingSenderId: getFirebaseOverride('messagingSenderId', import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID, firebaseConfig.messagingSenderId),
  appId: getFirebaseOverride('appId', import.meta.env.VITE_FIREBASE_APP_ID, firebaseConfig.appId),
};

const app = initializeApp(resolvedConfig);
const dbId = getFirebaseOverride('firestoreDatabaseId', import.meta.env.VITE_FIREBASE_DATABASE_ID, (firebaseConfig as any).firestoreDatabaseId || '');
export const firestore = (dbId && dbId !== '(default)') ? getFirestore(app, dbId) : getFirestore(app);
export const auth = getAuth(app);

// Sign in anonymously to populate request.auth for security rules
signInAnonymously(auth).catch(err => {
  console.warn("Firebase Anonymous auth failed:", err);
});

// Seed data defaults for local copy / fallback
const LOCAL_STORAGE_DB_PREFIX = 'xdegan_db_';

export const SEEDED_SCHOOLS: School[] = [
  { npsn: 'SCH-DEFAULT', nama: 'Sekolah Default (Locked State)', alamat: 'Jl. Utama No. 1, Kota Default', is_default: true },
];

export const SEEDED_STUDENTS: Student[] = [];

export const SEEDED_LOGS: AttendanceLog[] = [];

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

// Verify or initialize local copies
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

initializeLocalStorageDB();

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

// Test Connection
async function testConnection() {
  try {
    await getDocFromServer(doc(firestore, 'sekolah', 'test-connection'));
  } catch (error) {
    if (error instanceof Error && error.message.includes('the client is offline')) {
      console.warn("Firestore client appears to be offline. Verify Firebase rules or setup.");
    }
  }
}
testConnection();

// Dual-Engine Firestore Database Access Object
export const db = {
  // --- Schools ---
  async getSchools(): Promise<School[]> {
    try {
      const querySnapshot = await getDocs(collection(firestore, 'sekolah'));
      const schoolsList: School[] = [];
      querySnapshot.forEach((docSnap) => {
        const data = docSnap.data();
        schoolsList.push({
          npsn: docSnap.id,
          nama: data.nama,
          alamat: data.alamat || 'Alamat Sekolah Terintegrasi'
        });
      });

      // Merge local defaults
      const combined = [...SEEDED_SCHOOLS];
      schoolsList.forEach(ps => {
        if (!combined.some(s => s.npsn === ps.npsn)) {
          combined.push(ps);
        }
      });
      setDbError(null);
      return combined;
    } catch (e: any) {
      console.error("Firestore failed to fetch schools, falling back to local storage:", e);
      setDbError({ message: e.message || String(e) });
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}schools`);
      return raw ? JSON.parse(raw) : SEEDED_SCHOOLS;
    }
  },

  async addSchool(school: School): Promise<School> {
    try {
      await setDoc(doc(firestore, 'sekolah', school.npsn), {
        nama: school.nama,
        alamat: school.alamat
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to insert school record:", e);
      setDbError({ message: e.message || String(e) });
    }

    // Keep local copy
    const schools = await this.getSchools();
    if (!schools.some(s => s.npsn === school.npsn)) {
      schools.push(school);
      localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}schools`, JSON.stringify(schools));
    }
    return school;
  },

  // --- Students ---
  async getStudents(npsn?: string): Promise<Student[]> {
    try {
      let q = query(collection(firestore, 'siswa'), orderBy('name', 'asc'));
      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        q = query(collection(firestore, 'siswa'), where('school_id', '==', npsn));
      }
      const querySnapshot = await getDocs(q);
      const studentList: Student[] = [];
      querySnapshot.forEach((docSnap) => {
        const s = docSnap.data();
        studentList.push({
          id: docSnap.id,
          nama: s.name,
          nisn: docSnap.id,
          kelas: s.kelas || 'Umum',
          npsn_sekolah: s.school_id,
          created_at: s.created_at || new Date().toISOString()
        });
      });
      setDbError(null);
      return studentList;
    } catch (e: any) {
      console.error("Firestore failed to fetch students, falling back to local:", e);
      setDbError({ message: e.message || String(e) });
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}students`);
      const students: Student[] = raw ? JSON.parse(raw) : SEEDED_STUDENTS;
      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        return students.filter(s => s.npsn_sekolah === npsn);
      }
      return students;
    }
  },

  async addStudent(student: Omit<Student, 'id' | 'created_at'>): Promise<Student> {
    const id = student.nisn || 'stud-' + Math.random().toString(36).substr(2, 9);
    const createdAt = new Date().toISOString();
    const newStudent: Student = {
      ...student,
      id,
      created_at: createdAt
    };

    try {
      const schools = await this.getSchools();
      const foundSchool = schools.find(s => s.npsn === student.npsn_sekolah);
      const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';

      await setDoc(doc(firestore, 'siswa', id), {
        name: student.nama,
        role: 'Siswa',
        school_id: student.npsn_sekolah,
        school_name: schoolName,
        kelas: student.kelas || 'Umum',
        created_at: createdAt
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to add student:", e);
      setDbError({ message: e.message || String(e) });
    }

    // Keep local copy
    const students = await this.getStudents();
    students.push(newStudent);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(students));
    return newStudent;
  },

  async updateStudent(id: string, updated: Partial<Omit<Student, 'id' | 'created_at'>>): Promise<Student> {
    try {
      const payload: any = {};
      if (updated.nama) payload.name = updated.nama;
      if (updated.kelas) payload.kelas = updated.kelas;
      if (updated.npsn_sekolah) {
        payload.school_id = updated.npsn_sekolah;
        const schools = await this.getSchools();
        const foundSchool = schools.find(s => s.npsn === updated.npsn_sekolah);
        if (foundSchool) {
          payload.school_name = foundSchool.nama;
        }
      }

      await updateDoc(doc(firestore, 'siswa', id), payload);
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to update student:", e);
      setDbError({ message: e.message || String(e) });
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
    try {
      await deleteDoc(doc(firestore, 'siswa', id));
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to delete student:", e);
      setDbError({ message: e.message || String(e) });
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

    try {
      const schools = await this.getSchools();
      const batch = writeBatch(firestore);

      for (const s of newStudents) {
        const foundSchool = schools.find(sch => sch.npsn === s.npsn_sekolah);
        const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';
        
        const docRef = doc(firestore, 'siswa', s.id);
        batch.set(docRef, {
          name: s.nama,
          role: 'Siswa',
          school_id: s.npsn_sekolah,
          school_name: schoolName,
          kelas: s.kelas || 'Umum',
          created_at: s.created_at
        });
      }

      await batch.commit();
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore batch import failed:", e);
      setDbError({ message: e.message || String(e) });
    }

    const students = await this.getStudents();
    const combined = [...students, ...newStudents];
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(combined));
    return newStudents;
  },

  // --- Attendance Logs ---
  async getLogs(npsn?: string): Promise<AttendanceLog[]> {
    try {
      let q = query(collection(firestore, 'kehadiran'), orderBy('timestamp', 'desc'));
      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        q = query(collection(firestore, 'kehadiran'), where('school_id', '==', npsn));
      }
      const querySnapshot = await getDocs(q);
      const mapped: AttendanceLog[] = [];
      querySnapshot.forEach((docSnap) => {
        const item = docSnap.data();
        let isoWaktu = new Date().toISOString();
        if (item.timestamp) {
          isoWaktu = new Date(Number(item.timestamp)).toISOString();
        }
        mapped.push({
          id: docSnap.id,
          student_id: item.uid,
          nama: item.name || 'Tidak Dikenal',
          nisn: item.uid || '-',
          kelas: item.kelas || 'Umum',
          npsn_sekolah: item.school_id,
          status: item.status || 'Hadir',
          waktu: isoWaktu,
          scan_method: item.scan_method || 'Manual'
        });
      });
      setDbError(null);
      return mapped;
    } catch (e: any) {
      console.error("Firestore failed to fetch logs, falling back to local:", e);
      setDbError({ message: e.message || String(e) });
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logs`);
      const logs: AttendanceLog[] = raw ? JSON.parse(raw) : SEEDED_LOGS;
      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        return logs.filter(l => l.npsn_sekolah === npsn);
      }
      return logs;
    }
  },

  async addLog(log: Omit<AttendanceLog, 'id' | 'waktu'>): Promise<AttendanceLog> {
    const id = 'REQ-' + Math.random().toString(36).substr(2, 9).toUpperCase();
    const waktu = new Date().toISOString();
    const newLog: AttendanceLog = {
      ...log,
      id,
      waktu,
      scan_method: log.scan_method || 'Manual'
    };

    try {
      const schools = await this.getSchools();
      const foundSchool = schools.find(s => s.npsn === log.npsn_sekolah);
      const schoolName = foundSchool ? foundSchool.nama : 'Sekolah Terintegrasi';

      await setDoc(doc(firestore, 'kehadiran', id), {
        uid: log.student_id,
        name: log.nama,
        role: 'Siswa',
        kelas: log.kelas || 'Umum',
        timestamp: Date.now(),
        type: 'MASUK',
        status: log.status || 'Hadir',
        school_id: log.npsn_sekolah,
        school_name: schoolName,
        scan_method: log.scan_method || 'Manual'
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to add attendance log:", e);
      setDbError({ message: e.message || String(e) });
    }

    const logs = await this.getLogs();
    logs.unshift(newLog);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(logs));
    return newLog;
  },

  async deleteLog(id: string): Promise<boolean> {
    try {
      await deleteDoc(doc(firestore, 'kehadiran', id));
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to delete log:", e);
      setDbError({ message: e.message || String(e) });
    }

    const logs = await this.getLogs();
    const filtered = logs.filter(l => l.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(filtered));
    return true;
  },

  // --- Broadcasts ---
  async getBroadcasts(): Promise<Broadcast[]> {
    try {
      const querySnapshot = await getDocs(query(collection(firestore, 'app_broadcast'), orderBy('id', 'asc')));
      const broadcastList: Broadcast[] = [];
      querySnapshot.forEach((docSnap) => {
        const b = docSnap.data();
        broadcastList.push({
          id: Number(docSnap.id),
          title: b.title,
          message: b.message,
          drive_link: b.drive_link || '',
          type: b.type || 'INSTRUCTION',
          is_active: b.is_active,
          updated_id: b.updated_id || Date.now()
        });
      });
      setDbError(null);
      return broadcastList.length > 0 ? broadcastList : SEEDED_BROADCASTS;
    } catch (e: any) {
      console.error("Firestore failed to fetch broadcasts:", e);
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`);
      return raw ? JSON.parse(raw) : SEEDED_BROADCASTS;
    }
  },

  async addBroadcast(broadcast: Omit<Broadcast, 'id' | 'updated_id'>): Promise<Broadcast> {
    const id = Math.floor(Math.random() * 1000000);
    const newBroadcast: Broadcast = {
      ...broadcast,
      id,
      updated_id: Date.now()
    };

    try {
      await setDoc(doc(firestore, 'app_broadcast', String(id)), {
        title: broadcast.title,
        message: broadcast.message,
        drive_link: broadcast.drive_link || '',
        type: broadcast.type || 'INSTRUCTION',
        is_active: broadcast.is_active,
        updated_id: Date.now()
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to add broadcast:", e);
      setDbError({ message: e.message || String(e) });
    }

    const broadcasts = await this.getBroadcasts();
    broadcasts.push(newBroadcast);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(broadcasts));
    return newBroadcast;
  },

  async updateBroadcast(id: number, updated: Partial<Omit<Broadcast, 'id' | 'updated_id'>>): Promise<Broadcast> {
    try {
      const payload = {
        ...updated,
        updated_id: Date.now()
      };
      await updateDoc(doc(firestore, 'app_broadcast', String(id)), payload);
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to update broadcast:", e);
      setDbError({ message: e.message || String(e) });
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
    throw new Error("Broadcast not found");
  },

  async deleteBroadcast(id: number): Promise<boolean> {
    try {
      await deleteDoc(doc(firestore, 'app_broadcast', String(id)));
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to delete broadcast:", e);
      setDbError({ message: e.message || String(e) });
    }

    const broadcasts = await this.getBroadcasts();
    const filtered = broadcasts.filter(b => b.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(filtered));
    return true;
  },

  // --- Holidays ---
  async getHolidays(npsn?: string): Promise<Holiday[]> {
    try {
      const querySnapshot = await getDocs(query(collection(firestore, 'hari_libur'), orderBy('tanggal', 'asc')));
      const holidayList: Holiday[] = [];
      querySnapshot.forEach((docSnap) => {
        const d = docSnap.data();
        holidayList.push({
          id: docSnap.id,
          tanggal: d.tanggal,
          nama: d.nama,
          npsn_sekolah: d.npsn_sekolah || 'ALL',
          keterangan: d.keterangan || ''
        });
      });
      setDbError(null);

      // Robust dual-engine merge:
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`);
      const localHols: Holiday[] = raw ? JSON.parse(raw) : SEEDED_HOLIDAYS;

      let output: Holiday[];
      if (holidayList.length > 0) {
        // Firestore has data, sync it to localStorage
        localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(holidayList));
        output = holidayList;
      } else {
        // Firestore is empty or not configured yet, fallback to localStorage
        output = localHols;
      }

      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        return output.filter(h => h.npsn_sekolah === 'ALL' || h.npsn_sekolah === npsn);
      }
      return output;
    } catch (e: any) {
      console.error("Firestore failed to fetch holidays, using local fallback:", e);
      setDbError({ message: e.message || String(e) });
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`);
      const hols: Holiday[] = raw ? JSON.parse(raw) : SEEDED_HOLIDAYS;
      if (npsn && npsn !== 'SCH-DEFAULT' && npsn !== 'ALL') {
        return hols.filter(h => h.npsn_sekolah === 'ALL' || h.npsn_sekolah === npsn);
      }
      return hols;
    }
  },

  async addHoliday(holiday: Omit<Holiday, 'id'>): Promise<Holiday> {
    const id = 'hol-' + Math.random().toString(36).substr(2, 9);
    const newHoliday: Holiday = {
      ...holiday,
      id
    };

    try {
      await setDoc(doc(firestore, 'hari_libur', id), {
        tanggal: holiday.tanggal,
        nama: holiday.nama,
        npsn_sekolah: holiday.npsn_sekolah,
        keterangan: holiday.keterangan || ''
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to add holiday:", e);
      setDbError({ message: e.message || String(e) });
    }

    const holidays = await this.getHolidays();
    holidays.push(newHoliday);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(holidays));
    return newHoliday;
  },

  async deleteHoliday(id: string): Promise<boolean> {
    try {
      await deleteDoc(doc(firestore, 'hari_libur', id));
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to delete holiday:", e);
      setDbError({ message: e.message || String(e) });
    }

    const holidays = await this.getHolidays();
    const filtered = holidays.filter(h => h.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(filtered));
    return true;
  },

  // --- Logins & Users ---
  async getLogins(): Promise<LoginTableRecord[]> {
    try {
      const querySnapshot = await getDocs(collection(firestore, 'login'));
      const loginList: LoginTableRecord[] = [];
      querySnapshot.forEach((docSnap) => {
        const d = docSnap.data();
        loginList.push({
          id: Number(docSnap.id),
          email: d.email,
          password: d.password,
          role: d.role,
          npsn_sekolah: d.npsn_sekolah || null,
          kelas_tugas: d.kelas_tugas || null
        });
      });
      setDbError(null);
      return loginList.length > 0 ? loginList : SEEDED_LOGINS;
    } catch (e: any) {
      console.error("Firestore failed to fetch logins:", e);
      const raw = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}logins`);
      return raw ? JSON.parse(raw) : SEEDED_LOGINS;
    }
  },

  async verifyLoginTable(email: string): Promise<LoginTableRecord | null> {
    try {
      const q = query(collection(firestore, 'login'), where('email', '==', email.toLowerCase()));
      const querySnapshot = await getDocs(q);
      if (!querySnapshot.empty) {
        const docSnap = querySnapshot.docs[0];
        const d = docSnap.data();
        return {
          id: Number(docSnap.id),
          email: d.email,
          password: d.password,
          role: d.role,
          npsn_sekolah: d.npsn_sekolah || null,
          kelas_tugas: d.kelas_tugas || null
        };
      }
    } catch (e: any) {
      console.error("Firestore failed to verify login table:", e);
    }

    const logins = await this.getLogins();
    return logins.find(l => l.email.toLowerCase() === email.toLowerCase()) || null;
  },

  async addLoginRecord(record: Omit<LoginTableRecord, 'id'>): Promise<LoginTableRecord> {
    const id = Math.floor(Math.random() * 100000);
    const newRecord: LoginTableRecord = {
      ...record,
      id
    };

    try {
      await setDoc(doc(firestore, 'login', String(id)), {
        email: record.email.toLowerCase(),
        password: record.password,
        role: record.role,
        npsn_sekolah: record.npsn_sekolah || null,
        kelas_tugas: record.kelas_tugas || null
      });
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to add login record:", e);
      setDbError({ message: e.message || String(e) });
    }

    const logins = await this.getLogins();
    logins.push(newRecord);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(logins));
    return newRecord;
  },

  async updateLoginRecord(id: number, record: Partial<Omit<LoginTableRecord, 'id'>>): Promise<boolean> {
    try {
      const payload: any = {};
      if (record.email) payload.email = record.email.toLowerCase();
      if (record.password) payload.password = record.password;
      if (record.role) payload.role = record.role;
      if (record.npsn_sekolah !== undefined) payload.npsn_sekolah = record.npsn_sekolah;
      if (record.kelas_tugas !== undefined) payload.kelas_tugas = record.kelas_tugas;

      await updateDoc(doc(firestore, 'login', String(id)), payload);
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to update login record:", e);
      setDbError({ message: e.message || String(e) });
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
    try {
      await deleteDoc(doc(firestore, 'login', String(id)));
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to delete login record:", e);
      setDbError({ message: e.message || String(e) });
    }

    const logins = await this.getLogins();
    const filtered = logins.filter(l => l.id !== id);
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(filtered));
    return true;
  },

  // --- Admin Page Settings ---
  async getAdminPageSettings(): Promise<AdminPageSetting[]> {
    const DEFAULT_PAGE_SETTINGS: AdminPageSetting[] = [
      { page_id: 'dashboard', page_name: 'Dashboard', is_visible: true },
      { page_id: 'siswa', page_name: 'Siswa / Anggota', is_visible: true },
      { page_id: 'absensi', page_name: 'Rekap Absensi', is_visible: true },
      { page_id: 'broadcast', page_name: 'Broadcast & Info', is_visible: true },
      { page_id: 'hari-libur', page_name: 'Hari Libur', is_visible: true },
      { page_id: 'binding-device', page_name: 'Binding Device', is_visible: true },
    ];

    try {
      const querySnapshot = await getDocs(collection(firestore, 'admin_page_settings'));
      const settingsList: AdminPageSetting[] = [];
      querySnapshot.forEach((docSnap) => {
        const d = docSnap.data();
        settingsList.push({
          page_id: docSnap.id,
          page_name: d.page_name,
          is_visible: d.is_visible
        });
      });
      setDbError(null);
      return settingsList.length > 0 ? settingsList : DEFAULT_PAGE_SETTINGS;
    } catch (e: any) {
      console.warn("Could not fetch page settings from Firestore, falling back to local:", e);
      const localData = localStorage.getItem(`${LOCAL_STORAGE_DB_PREFIX}admin_page_settings`);
      if (localData) {
        try {
          return JSON.parse(localData);
        } catch (err) {
          return DEFAULT_PAGE_SETTINGS;
        }
      }
      return DEFAULT_PAGE_SETTINGS;
    }
  },

  async updateAdminPageSettings(settings: AdminPageSetting[]): Promise<boolean> {
    try {
      const batch = writeBatch(firestore);
      for (const s of settings) {
        const docRef = doc(firestore, 'admin_page_settings', s.page_id);
        batch.set(docRef, {
          page_name: s.page_name,
          is_visible: s.is_visible
        });
      }
      await batch.commit();
      setDbError(null);
    } catch (e: any) {
      console.error("Firestore failed to upsert admin page settings:", e);
      setDbError({ message: e.message || String(e) });
    }

    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}admin_page_settings`, JSON.stringify(settings));
    return true;
  },

  async runSql(queryText: string): Promise<{ success: boolean; data?: any[]; message?: string; error?: string }> {
    // Firestore SQL simulator to allow the SQL Editor console to interact cleanly
    const clean = queryText.trim().replace(/;+$/, '').replace(/\s+/g, ' ');
    const lower = clean.toLowerCase();

    if (lower.startsWith('select')) {
      const match = clean.match(/select\s+(.+?)\s+from\s+(\w+)/i);
      if (match) {
        const tableName = match[2].trim().toLowerCase();
        let records: any[] = [];
        try {
          if (tableName === 'sekolah') records = await this.getSchools();
          else if (tableName === 'siswa') records = await this.getStudents();
          else if (tableName === 'kehadiran') records = await this.getLogs();
          else if (tableName === 'app_broadcast') records = await this.getBroadcasts();
          else if (tableName === 'login') records = await this.getLogins();
          else if (tableName === 'hari_libur') records = await this.getHolidays();
          else return { success: false, error: `Table "${tableName}" not found.` };

          return { success: true, data: records };
        } catch (err: any) {
          return { success: false, error: err.message || String(err) };
        }
      }
    }

    return {
      success: false,
      error: "Command not recognized. Note that Firestore is a NoSQL database, so SQL editor runs in sandboxed mock mode. Supported tables: sekolah, siswa, kehadiran, app_broadcast, login, hari_libur."
    };
  },

  resetToDefault() {
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}schools`, JSON.stringify(SEEDED_SCHOOLS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}students`, JSON.stringify(SEEDED_STUDENTS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logs`, JSON.stringify(SEEDED_LOGS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}broadcasts`, JSON.stringify(SEEDED_BROADCASTS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}logins`, JSON.stringify(SEEDED_LOGINS));
    localStorage.setItem(`${LOCAL_STORAGE_DB_PREFIX}holidays`, JSON.stringify(SEEDED_HOLIDAYS));
  }
};
