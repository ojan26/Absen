export type UserRole = 'superadmin' | 'admin' | 'guru';

export interface UserProfile {
  email: string;
  role: UserRole;
  npsn_sekolah: string | null; // Null if superadmin, otherwise specific NPSN
  kelas_tugas?: string | null;
}

export interface School {
  npsn: string;
  nama: string;
  alamat: string;
  is_default?: boolean;
}

export interface Student {
  id: string;
  nama: string;
  nisn: string;
  kelas: string;
  npsn_sekolah: string;
  created_at: string;
}

export interface AttendanceLog {
  id: string;
  student_id: string;
  nama: string; // Name of the student
  nisn: string; // NISN of the student
  kelas: string;
  npsn_sekolah: string;
  status: 'Hadir' | 'Sakit' | 'Izin' | 'Alpa';
  waktu: string; // ISO String or HH:MM format
  scan_method: 'QR Code' | 'Manual';
}

export interface SupabaseConfig {
  url: string;
  anonKey: string;
  useRealDatabase: boolean;
}

export interface Broadcast {
  id: number;
  title: string;
  message: string;
  drive_link: string;
  type: string;
  is_active: boolean;
  updated_id: number;
}

export interface LoginTableRecord {
  id: number;
  email: string;
  password: string;
  role: UserRole;
  npsn_sekolah: string | null;
  kelas_tugas?: string | null;
}

export interface AdminPageSetting {
  page_id: string;
  page_name: string;
  is_visible: boolean;
}

export interface Holiday {
  id: string;
  tanggal: string; // Format YYYY-MM-DD
  nama: string; // Nama Hari Libur
  npsn_sekolah: string; // 'ALL' untuk semua sekolah, atau NPSN spesifik
  keterangan?: string;
}



