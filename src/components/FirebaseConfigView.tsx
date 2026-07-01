import React, { useState, useEffect } from 'react';
import firebaseConfig from '../../firebase-applet-config.json';
import { initializeApp, deleteApp, getApp } from 'firebase/app';
import { getFirestore, doc, getDocFromServer, setDoc, collection, getDocs, limit, query } from 'firebase/firestore';
import { getAuth, signInAnonymously } from 'firebase/auth';
import { 
  Database, 
  Settings, 
  CheckCircle, 
  AlertCircle, 
  RefreshCw, 
  Save, 
  HelpCircle, 
  Copy, 
  Terminal, 
  ExternalLink,
  ShieldAlert,
  Sliders,
  Check,
  Eye,
  EyeOff
} from 'lucide-react';

export const FirebaseConfigView: React.FC = () => {
  // Load initial settings
  const [apiKey, setApiKey] = useState<string>(localStorage.getItem('firebase_custom_apiKey') || firebaseConfig.apiKey || '');
  const [authDomain, setAuthDomain] = useState<string>(localStorage.getItem('firebase_custom_authDomain') || firebaseConfig.authDomain || '');
  const [projectId, setProjectId] = useState<string>(localStorage.getItem('firebase_custom_projectId') || firebaseConfig.projectId || '');
  const [storageBucket, setStorageBucket] = useState<string>(localStorage.getItem('firebase_custom_storageBucket') || firebaseConfig.storageBucket || '');
  const [messagingSenderId, setMessagingSenderId] = useState<string>(localStorage.getItem('firebase_custom_messagingSenderId') || firebaseConfig.messagingSenderId || '');
  const [appId, setAppId] = useState<string>(localStorage.getItem('firebase_custom_appId') || firebaseConfig.appId || '');
  const [dbId, setDbId] = useState<string>(localStorage.getItem('firebase_custom_firestoreDatabaseId') || (firebaseConfig as any).firestoreDatabaseId || '(default)');

  const [showApiKey, setShowApiKey] = useState<boolean>(false);
  const [testing, setTesting] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<boolean>(false);

  // Load custom status
  const isCustomConfigActive = !!(
    localStorage.getItem('firebase_custom_apiKey') ||
    localStorage.getItem('firebase_custom_authDomain') ||
    localStorage.getItem('firebase_custom_projectId')
  );

  const handleSave = () => {
    try {
      if (apiKey.trim()) localStorage.setItem('firebase_custom_apiKey', apiKey.trim());
      else localStorage.removeItem('firebase_custom_apiKey');

      if (authDomain.trim()) localStorage.setItem('firebase_custom_authDomain', authDomain.trim());
      else localStorage.removeItem('firebase_custom_authDomain');

      if (projectId.trim()) localStorage.setItem('firebase_custom_projectId', projectId.trim());
      else localStorage.removeItem('firebase_custom_projectId');

      if (storageBucket.trim()) localStorage.setItem('firebase_custom_storageBucket', storageBucket.trim());
      else localStorage.removeItem('firebase_custom_storageBucket');

      if (messagingSenderId.trim()) localStorage.setItem('firebase_custom_messagingSenderId', messagingSenderId.trim());
      else localStorage.removeItem('firebase_custom_messagingSenderId');

      if (appId.trim()) localStorage.setItem('firebase_custom_appId', appId.trim());
      else localStorage.removeItem('firebase_custom_appId');

      if (dbId.trim() && dbId !== '(default)') localStorage.setItem('firebase_custom_firestoreDatabaseId', dbId.trim());
      else localStorage.removeItem('firebase_custom_firestoreDatabaseId');

      setSaveSuccess(true);
      setTimeout(() => {
        setSaveSuccess(false);
        // Force reload page to initialize Firebase client with new config
        window.location.reload();
      }, 1500);
    } catch (e: any) {
      alert(`Gagal menyimpan konfigurasi: ${e.message}`);
    }
  };

  const handleResetToDefault = () => {
    if (confirm("Apakah Anda yakin ingin menghapus konfigurasi kustom ini dan kembali ke Firebase bawaan aplikasi?")) {
      localStorage.removeItem('firebase_custom_apiKey');
      localStorage.removeItem('firebase_custom_authDomain');
      localStorage.removeItem('firebase_custom_projectId');
      localStorage.removeItem('firebase_custom_storageBucket');
      localStorage.removeItem('firebase_custom_messagingSenderId');
      localStorage.removeItem('firebase_custom_appId');
      localStorage.removeItem('firebase_custom_firestoreDatabaseId');

      setApiKey(firebaseConfig.apiKey || '');
      setAuthDomain(firebaseConfig.authDomain || '');
      setProjectId(firebaseConfig.projectId || '');
      setStorageBucket(firebaseConfig.storageBucket || '');
      setMessagingSenderId(firebaseConfig.messagingSenderId || '');
      setAppId(firebaseConfig.appId || '');
      setDbId((firebaseConfig as any).firestoreDatabaseId || '(default)');

      setTestResult({
        success: true,
        message: "Konfigurasi kustom dihapus. Mengembalikan ke Firebase bawaan aplikasi."
      });

      setTimeout(() => {
        window.location.reload();
      }, 1500);
    }
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);

    const testAppConfig = {
      apiKey: apiKey.trim(),
      authDomain: authDomain.trim(),
      projectId: projectId.trim(),
      storageBucket: storageBucket.trim(),
      messagingSenderId: messagingSenderId.trim(),
      appId: appId.trim(),
    };

    let testAppInstance;
    try {
      // Create a unique temporary app to test connection
      const testAppName = `temp-test-app-${Date.now()}`;
      testAppInstance = initializeApp(testAppConfig, testAppName);
      
      const testAuth = getAuth(testAppInstance);
      
      // Try anonymous authentication
      await signInAnonymously(testAuth);

      const testDb = (dbId && dbId !== '(default)') 
        ? getFirestore(testAppInstance, dbId.trim()) 
        : getFirestore(testAppInstance);

      // Try reading 'sekolah' collection to check security rules
      const colRef = collection(testDb, 'sekolah');
      const q = query(colRef, limit(1));
      await getDocs(q);

      setTestResult({
        success: true,
        message: "Koneksi Berhasil! Firebase dapat dihubungi, otentikasi anonim sukses, dan aturan keamanan Firestore mengizinkan pembacaan data."
      });
    } catch (e: any) {
      console.error("Firebase connection test failed: ", e);
      let errorDesc = e.message || String(e);
      if (errorDesc.includes("permission-denied") || errorDesc.includes("Missing or insufficient permissions")) {
        errorDesc = "Error: Aturan keamanan (Security Rules) Firestore menolak pembacaan data. Pastikan Anda telah mengunggah Security Rules yang sesuai.";
      } else if (errorDesc.includes("invalid-api-key") || errorDesc.includes("API key not valid")) {
        errorDesc = "Error: API Key Firebase tidak valid. Periksa kembali API Key Anda.";
      } else if (errorDesc.includes("project-not-found")) {
        errorDesc = "Error: Project ID tidak ditemukan di Firebase.";
      }
      setTestResult({
        success: false,
        message: `Koneksi Gagal! ${errorDesc}`
      });
    } finally {
      if (testAppInstance) {
        try {
          await deleteApp(testAppInstance);
        } catch (e) {}
      }
      setTesting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-6xl mx-auto animate-fadeIn">
      {/* Header section */}
      <div className="bg-white border border-slate-100 rounded-2xl p-6 shadow-sm flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <span className="px-2.5 py-0.8 bg-orange-50 border border-orange-100/60 text-orange-600 font-extrabold font-mono uppercase tracking-widest text-[9px] rounded-md">
            Developer Console
          </span>
          <h2 className="text-base font-bold text-slate-800 tracking-tight mt-1.5 flex items-center">
            <Database className="w-5 h-5 mr-2 text-orange-500" />
            Konfigurasi & Pengaturan Firebase
          </h2>
          <p className="text-xs text-slate-500 mt-0.5 leading-relaxed font-medium">
            Atur dan ganti kredensial database Google Cloud Firebase Firestore Anda sendiri untuk integrasi penuh.
          </p>
        </div>

        <div className="flex items-center space-x-2 shrink-0">
          <button
            type="button"
            onClick={handleResetToDefault}
            disabled={!isCustomConfigActive}
            className="px-3.5 py-2 border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 font-bold text-xs rounded-xl transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center"
          >
            <RefreshCw className="w-3.5 h-3.5 mr-2" />
            Gunakan Default App
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Form Inputs (Left & Center columns) */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white border border-slate-100 rounded-2xl p-6 shadow-sm space-y-5">
            <div className="flex items-center space-x-3 pb-4 border-b border-slate-100">
              <div className="p-2 bg-blue-50 border border-blue-100/50 rounded-xl text-blue-600">
                <Sliders className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-800">Kredensial Firebase SDK</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">Web App API Config</p>
              </div>
            </div>

            {/* Inputs Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  API Key Firebase
                </label>
                <div className="relative">
                  <input
                    type={showApiKey ? "text" : "password"}
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    placeholder="AIzaSy..."
                    className="w-full px-3 py-2 pr-10 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono font-bold"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(!showApiKey)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600"
                  >
                    {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Project ID
                </label>
                <input
                  type="text"
                  value={projectId}
                  onChange={(e) => setProjectId(e.target.value)}
                  placeholder="my-awesome-project"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Auth Domain
                </label>
                <input
                  type="text"
                  value={authDomain}
                  onChange={(e) => setAuthDomain(e.target.value)}
                  placeholder="my-awesome-project.firebaseapp.com"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Storage Bucket
                </label>
                <input
                  type="text"
                  value={storageBucket}
                  onChange={(e) => setStorageBucket(e.target.value)}
                  placeholder="my-awesome-project.appspot.com"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Messaging Sender ID
                </label>
                <input
                  type="text"
                  value={messagingSenderId}
                  onChange={(e) => setMessagingSenderId(e.target.value)}
                  placeholder="123456789012"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  App ID
                </label>
                <input
                  type="text"
                  value={appId}
                  onChange={(e) => setAppId(e.target.value)}
                  placeholder="1:123456789012:web:a1b2c3d4e5f6g7"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Firestore Database ID
                </label>
                <input
                  type="text"
                  value={dbId}
                  onChange={(e) => setDbId(e.target.value)}
                  placeholder="(default)"
                  className="w-full px-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500/10 focus:border-blue-500 transition-all font-mono font-bold"
                />
              </div>
            </div>

            {testResult && (
              <div className={`p-4 rounded-xl border flex items-start space-x-3 text-xs leading-relaxed animate-fadeIn ${
                testResult.success 
                  ? 'bg-emerald-50 border-emerald-100 text-emerald-800' 
                  : 'bg-rose-50 border-rose-100 text-rose-800'
              }`}>
                {testResult.success ? (
                  <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                ) : (
                  <AlertCircle className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
                )}
                <span>{testResult.message}</span>
              </div>
            )}

            {/* Action buttons */}
            <div className="pt-4 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <button
                type="button"
                onClick={handleTestConnection}
                disabled={testing || !apiKey.trim() || !projectId.trim()}
                className="w-full sm:w-auto px-4 py-2.5 bg-slate-100 hover:bg-slate-200 disabled:opacity-40 text-slate-700 font-bold text-xs rounded-xl transition-all flex items-center justify-center space-x-2"
              >
                <RefreshCw className={`w-4 h-4 ${testing ? 'animate-spin' : ''}`} />
                <span>{testing ? 'Menguji...' : 'Uji Koneksi'}</span>
              </button>

              <button
                type="button"
                onClick={handleSave}
                disabled={saveSuccess}
                className="w-full sm:w-auto px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/10 active:scale-98 transition-all flex items-center justify-center space-x-2"
              >
                {saveSuccess ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />}
                <span>{saveSuccess ? 'Tersimpan (Memuat Ulang...)' : 'Simpan & Terapkan'}</span>
              </button>
            </div>
          </div>
        </div>

        {/* Tutorial & Helper (Right column) */}
        <div className="space-y-6">
          <div className="bg-gradient-to-br from-slate-900 to-slate-950 text-slate-100 p-6 rounded-2xl border border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center space-x-2.5 text-orange-400">
              <HelpCircle className="w-4.5 h-4.5 shrink-0" />
              <h4 className="text-xs font-bold uppercase tracking-wider">Langkah-langkah Integrasi</h4>
            </div>

            <ol className="space-y-3.5 text-xs text-slate-300">
              <li className="flex space-x-2.5">
                <span className="w-5 h-5 shrink-0 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-bold text-white">1</span>
                <span className="leading-relaxed">
                  Buka <a href="https://console.firebase.google.com" target="_blank" rel="noreferrer" className="text-orange-400 hover:underline font-bold inline-flex items-center">Firebase Console <ExternalLink className="w-3 h-3 ml-0.5" /></a> dan buat proyek baru.
                </span>
              </li>
              <li className="flex space-x-2.5">
                <span className="w-5 h-5 shrink-0 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-bold text-white">2</span>
                <span className="leading-relaxed">
                  Daftarkan aplikasi web (Web App) di setelan proyek, lalu salin blok objek <strong>firebaseConfig</strong>.
                </span>
              </li>
              <li className="flex space-x-2.5">
                <span className="w-5 h-5 shrink-0 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-bold text-white">3</span>
                <span className="leading-relaxed">
                  Tempelkan masing-masing nilai variabel kredensial tersebut ke form di samping kiri ini.
                </span>
              </li>
              <li className="flex space-x-2.5">
                <span className="w-5 h-5 shrink-0 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-bold text-white">4</span>
                <span className="leading-relaxed">
                  Aktifkan fitur <strong>Anonymous Sign-in</strong> di tab Authentication agar otentikasi penulisan log siswa bekerja dengan aman.
                </span>
              </li>
              <li className="flex space-x-2.5">
                <span className="w-5 h-5 shrink-0 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-bold text-white">5</span>
                <span className="leading-relaxed font-bold text-emerald-400">
                  Uji Koneksi untuk memastikan database siap digunakan!
                </span>
              </li>
            </ol>
          </div>

          <div className="bg-amber-50 border border-amber-150 rounded-2xl p-5 text-amber-900 space-y-2.5 shadow-sm">
            <div className="flex items-center space-x-2 text-amber-800 font-bold text-xs">
              <ShieldAlert className="w-4 h-4 shrink-0" />
              <span>Otentikasi Anonim Diperlukan</span>
            </div>
            <p className="text-[11px] text-amber-850 leading-relaxed font-medium">
              Aplikasi ini memindai absensi secara offline, sehingga memerlukan otentikasi anonim diaktifkan di konsol Firebase Anda: 
              <br />
              <code className="block bg-amber-100/60 p-1.5 rounded-md font-mono text-[9px] mt-1 text-amber-950">
                Firebase Console &gt; Authentication &gt; Sign-in method &gt; Anonymous (Enable)
              </code>
            </p>
          </div>
        </div>

      </div>
    </div>
  );
};
