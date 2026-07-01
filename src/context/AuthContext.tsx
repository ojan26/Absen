import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserProfile } from '../types';
import { db } from '../firebaseClient';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Preset list of mock accounts for easy evaluation
export const MOCK_ACCOUNTS: UserProfile[] = [
  { email: 'superadmin@xdegan.com', role: 'superadmin', npsn_sekolah: null },
  { email: 'admin.smkn1@xdegan.com', role: 'admin', npsn_sekolah: '50102030' },
  { email: 'admin.sman8@xdegan.com', role: 'admin', npsn_sekolah: '20104050' },
];

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    async function checkAuth() {
      // Local auth storage check
      try {
        const storedUser = localStorage.getItem('xdegan_current_user');
        if (storedUser) {
          setUser(JSON.parse(storedUser));
        }
      } catch (e) {
        console.error("Error parsing local auth user", e);
      }
      setLoading(false);
    }

    checkAuth();
  }, []);

  const login = async (email: string, password: string): Promise<{ success: boolean; error?: string }> => {
    try {
      // Query database table login (either live Firestore table or Local Storage seeded sandbox logins)
      const record = await db.verifyLoginTable(email);

      if (record) {
        if (record.password === password) {
          const profile: UserProfile = {
            email: record.email,
            role: record.role,
            npsn_sekolah: record.npsn_sekolah,
            kelas_tugas: record.kelas_tugas || null,
          };
          setUser(profile);
          localStorage.setItem('xdegan_current_user', JSON.stringify(profile));
          return { success: true };
        } else {
          return { success: false, error: 'Password salah untuk akun dari tabel login.' };
        }
      }

      // Fallback to evaluation mock accounts
      const mockUser = MOCK_ACCOUNTS.find(u => u.email.toLowerCase() === email.toLowerCase());
      if (mockUser) {
        if (password === 'admin123') {
          setUser(mockUser);
          localStorage.setItem('xdegan_current_user', JSON.stringify(mockUser));
          return { success: true };
        } else {
          return { success: false, error: 'Password salah. (Gunakan password default: admin123)' };
        }
      }

      return { success: false, error: 'Email tidak ditemukan di tabel login.' };
    } catch (e: any) {
      console.error("Authentication error using login table:", e);
      return { success: false, error: e.message || 'Gagal melakukan autentikasi dari tabel login.' };
    }
  };

  const logout = async () => {
    setUser(null);
    localStorage.removeItem('xdegan_current_user');
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
