import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { db } from '../firebaseClient';
import { Broadcast } from '../types';
import { 
  Megaphone, 
  Plus, 
  Search, 
  Edit, 
  Trash2, 
  ExternalLink, 
  CheckCircle, 
  XCircle, 
  AlertCircle,
  Clock,
  RefreshCw,
  FileText
} from 'lucide-react';

export const BroadcastView: React.FC = () => {
  const { user } = useAuth();
  const [broadcasts, setBroadcasts] = useState<Broadcast[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [dbError, setDbError] = useState<any>(null);

  // Form Modal States
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [modalMode, setModalMode] = useState<'add' | 'edit'>('add');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  
  // Form Fields
  const [formTitle, setFormTitle] = useState<string>('');
  const [formMessage, setFormMessage] = useState<string>('');
  const [formDriveLink, setFormDriveLink] = useState<string>('');
  const [formType, setFormType] = useState<string>('INSTRUCTION');
  const [formIsActive, setFormIsActive] = useState<boolean>(true);

  // Delete Confirmation States
  const [isDeleteOpen, setIsDeleteOpen] = useState<boolean>(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  const loadBroadcasts = async () => {
    setLoading(true);
    setDbError(null);
    try {
      const data = await db.getBroadcasts();
      setBroadcasts(data);
    } catch (e: any) {
      console.error("Gagal mengambil data broadcast:", e);
      setDbError(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBroadcasts();
  }, []);

  const handleOpenAddModal = () => {
    setModalMode('add');
    setSelectedId(null);
    setFormTitle('');
    setFormMessage('');
    setFormDriveLink('');
    setFormType('INSTRUCTION');
    setFormIsActive(true);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (item: Broadcast) => {
    setModalMode('edit');
    setSelectedId(item.id);
    setFormTitle(item.title);
    setFormMessage(item.message);
    setFormDriveLink(item.drive_link || '');
    setFormType(item.type || 'INSTRUCTION');
    setFormIsActive(item.is_active);
    setIsModalOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim() || !formMessage.trim()) return;

    setLoading(true);
    try {
      const payload = {
        title: formTitle,
        message: formMessage,
        drive_link: formDriveLink,
        type: formType,
        is_active: formIsActive
      };

      if (modalMode === 'add') {
        await db.addBroadcast(payload);
      } else if (modalMode === 'edit' && selectedId !== null) {
        await db.updateBroadcast(selectedId, payload);
      }
      setIsModalOpen(false);
      await loadBroadcasts();
    } catch (err: any) {
      alert("Error saving: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const toggleActiveStatus = async (item: Broadcast) => {
    try {
      await db.updateBroadcast(item.id, { is_active: !item.is_active });
      // Update locally to make UI interactive
      setBroadcasts(prev => prev.map(b => b.id === item.id ? { ...b, is_active: !b.is_active } : b));
    } catch (err: any) {
      console.error(err);
    }
  };

  const handleOpenDelete = (id: number) => {
    setDeleteTargetId(id);
    setIsDeleteOpen(true);
  };

  const handleDeleteSubmit = async () => {
    if (deleteTargetId === null) return;
    setLoading(true);
    try {
      await db.deleteBroadcast(deleteTargetId);
      setIsDeleteOpen(false);
      setDeleteTargetId(null);
      await loadBroadcasts();
    } catch (err: any) {
      alert("Error deleting: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const filteredList = broadcasts.filter(b => 
    b.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    b.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (b.type && b.type.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <div className="space-y-8 animate-fadeIn">
      {/* Page Title & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-800 flex items-center">
            <Megaphone className="w-5 h-5 mr-2 text-indigo-600 animate-pulse" />
            Super Admin Broadcast Control
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Kirimkan informasi penting, instruksi pemindaian, atau pengumuman darurat langsung ke smartphone siswa
          </p>
        </div>

        <div>
          <button
            onClick={loadBroadcasts}
            title="Refresh database"
            className="flex items-center space-x-2 px-3.5 py-2 bg-white border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 active:scale-95 transition-all shadow-xs"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-indigo-600' : ''}`} />
            <span>Sinkronkan Firestore</span>
          </button>
        </div>
      </div>

      {/* Info Warning Bar */}
      <div className="p-4 bg-indigo-50/70 border border-indigo-100 rounded-2xl flex items-start space-x-3">
        <AlertCircle className="w-5 h-5 text-indigo-600 mt-0.5 shrink-0" />
        <div className="text-xs text-slate-600 leading-relaxed">
          <strong className="text-indigo-900 block font-bold mb-0.5">Otoritas Super Admin</strong>
          Formulir di bawah ini terhubung langsung ke database cloud Firestore koleksi <code className="bg-indigo-100 px-1 py-0.5 rounded text-indigo-700 font-mono text-[10px]">app_broadcast</code>. Data yang diaktifkan akan segera ditarik secara berkala oleh modul aplikasi mobile siswa.
        </div>
      </div>

      {/* Split Grid: Left (Composer Form), Right (Broadcast List) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        
        {/* LEFT COLUMN: SUPER ADMIN COMPOSER FORM (5 COLS) */}
        {user?.role === 'superadmin' && (
          <div className="lg:col-span-5 space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 sticky top-6">
            <div className="flex items-center space-x-2 pb-4 border-b border-slate-100 mb-5">
              <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                <Megaphone className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-xs font-black text-slate-800 uppercase tracking-wider">Broadcast Composer</h3>
                <p className="text-[10px] text-slate-400 font-semibold">Tulis & Publish Pengumuman</p>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Title Input */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Judul Broadcast / Pengumuman <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Contoh: Perubahan Jadwal Sinkronisasi..."
                  value={formTitle}
                  onChange={(e) => setFormTitle(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-semibold transition-all"
                />
              </div>

              {/* Message Textarea */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Isi Pesan Detail <span className="text-rose-500">*</span>
                </label>
                <textarea
                  required
                  rows={4}
                  placeholder="Ketik informasi lengkap yang ingin disampaikan..."
                  value={formMessage}
                  onChange={(e) => setFormMessage(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 leading-relaxed transition-all"
                ></textarea>
              </div>

              {/* Category Dropdown */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Kategori Info
                </label>
                <select
                  value={formType}
                  onChange={(e) => setFormType(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-semibold transition-all"
                >
                  <option value="INSTRUCTION">💡 Instruksi (Instruction)</option>
                  <option value="ANNOUNCEMENT">📢 Pengumuman (Announcement)</option>
                  <option value="ALERT">🚨 Peringatan Darurat (Alert)</option>
                </select>
              </div>

              {/* PDF or Drive Link */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex items-center justify-between">
                  <span>Tautan Google Drive / PDF (Opsional)</span>
                  <span className="text-[9px] text-indigo-500 font-bold lowercase tracking-normal">Harus diawali https://</span>
                </label>
                <input
                  type="url"
                  placeholder="https://drive.google.com/file/d/..."
                  value={formDriveLink}
                  onChange={(e) => setFormDriveLink(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-mono transition-all"
                />
              </div>

              {/* Direct Publish Toggle */}
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100 flex items-center justify-between">
                <div>
                  <label htmlFor="publish-toggle" className="text-xs font-bold text-slate-700 block cursor-pointer">
                    Publish Langsung
                  </label>
                  <span className="text-[10px] text-slate-400">Aktifkan segera di aplikasi mobile</span>
                </div>
                <input
                  id="publish-toggle"
                  type="checkbox"
                  checked={formIsActive}
                  onChange={(e) => setFormIsActive(e.target.checked)}
                  className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500 cursor-pointer"
                />
              </div>

              {/* Submit Buttons */}
              <div className="pt-2 flex space-x-2">
                <button
                  type="button"
                  onClick={() => {
                    setFormTitle('');
                    setFormMessage('');
                    setFormDriveLink('');
                    setFormType('INSTRUCTION');
                    setFormIsActive(true);
                  }}
                  className="flex-1 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 transition-all active:scale-95 text-center"
                >
                  Reset Form
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="flex-2 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md hover:shadow-lg active:scale-95 transition-all flex items-center justify-center space-x-1.5 disabled:opacity-50"
                >
                  <Plus className="w-4 h-4" />
                  <span>Kirim Broadcast</span>
                </button>
              </div>
            </form>
          </div>
        </div>
        )}

        {/* RIGHT COLUMN: SEARCH, CONTROL TABLE, MONITORING (7 OR 12 COLS) */}
        <div className={`${user?.role === 'superadmin' ? 'lg:col-span-7' : 'lg:col-span-12'} space-y-6`}>
          {/* Filter, Search & Counts */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-md flex flex-col sm:flex-row gap-4 items-center justify-between">
            <div className="relative w-full">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                id="broadcast-search-input"
                type="text"
                placeholder="Cari berdasarkan kata kunci judul/pesan..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 transition-all text-slate-700 font-medium"
              />
            </div>
            
            <div className="shrink-0 text-[10px] font-bold text-slate-400 uppercase tracking-wider bg-slate-100 px-3 py-1.5 rounded-lg">
              Total: {filteredList.length} Record
            </div>
          </div>

          {/* Database Output List */}
          {loading && broadcasts.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600 mb-3"></div>
              <p className="text-xs font-semibold">Memuat database dari server...</p>
            </div>
          ) : filteredList.length === 0 ? (
            <div className="bg-white rounded-2xl border border-dashed border-slate-200 p-16 text-center">
              <Megaphone className="w-12 h-12 text-slate-300 stroke-1 mx-auto mb-3 animate-pulse" />
              <h4 className="font-bold text-slate-700 text-sm">Tidak Ada Broadcast Cocok</h4>
              <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
                Silakan ketik judul baru pada form di kiri untuk menambahkan data penyiaran baru ke Firestore.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {filteredList.map((item) => {
                const formattedTime = item.updated_id 
                  ? new Date(item.updated_id).toLocaleDateString('id-ID', {
                      day: 'numeric',
                      month: 'long',
                      year: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit'
                    })
                  : 'Baru saja';

                return (
                  <div 
                    key={item.id} 
                    className={`bg-white rounded-2xl border transition-all hover:shadow-md p-5 flex flex-col justify-between space-y-4 ${
                      item.is_active 
                        ? 'border-emerald-200 shadow-emerald-50/5' 
                        : 'border-slate-200 opacity-80'
                    }`}
                  >
                    <div className="space-y-2.5">
                      {/* Top Badges and status */}
                      <div className="flex items-center justify-between">
                        <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-extrabold tracking-wider uppercase border ${
                          item.type === 'ALERT' ? 'bg-rose-50 text-rose-600 border-rose-100' :
                          item.type === 'INSTRUCTION' ? 'bg-indigo-50 text-indigo-600 border-indigo-100' :
                          'bg-sky-50 text-sky-600 border-sky-100'
                        }`}>
                          {item.type || 'INFO'}
                        </span>

                        <button
                          onClick={() => {
                            if (user?.role === 'superadmin') {
                              toggleActiveStatus(item);
                            }
                          }}
                          disabled={user?.role !== 'superadmin'}
                          className={`flex items-center space-x-1.5 focus:outline-hidden ${user?.role !== 'superadmin' ? 'cursor-default' : ''}`}
                          title={user?.role === 'superadmin' ? (item.is_active ? "Klik untuk menonaktifkan" : "Klik untuk mengaktifkan") : "Status Broadcast"}
                        >
                          {item.is_active ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[9px] font-black bg-emerald-50 text-emerald-700 border border-emerald-200 uppercase">
                              <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full mr-1 animate-ping"></span>
                              Aktif
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[9px] font-black bg-slate-150 text-slate-500 border border-slate-200 uppercase">
                              Nonaktif
                            </span>
                          )}
                        </button>
                      </div>

                      {/* Title & Body */}
                      <div>
                        <h4 className="text-sm font-black text-slate-800 leading-snug">
                          {item.title}
                        </h4>
                        <p className="text-xs text-slate-500 mt-2 leading-relaxed whitespace-pre-wrap">
                          {item.message}
                        </p>
                      </div>
                    </div>

                    <div className="pt-3.5 border-t border-slate-100 flex items-center justify-between text-[10px]">
                      {/* Footer metadata */}
                      <div className="flex items-center text-slate-400 space-x-3 font-semibold">
                        <span className="flex items-center">
                          <Clock className="w-3.5 h-3.5 mr-1 text-slate-300" />
                          {formattedTime} WIB
                        </span>
                        {item.drive_link && (
                          <a 
                            href={item.drive_link} 
                            target="_blank" 
                            rel="noopener noreferrer" 
                            className="flex items-center text-indigo-600 font-bold hover:underline"
                          >
                            <ExternalLink className="w-3 h-3 mr-0.5 text-indigo-500" />
                            Dokumen Lampiran
                          </a>
                        )}
                      </div>

                      {/* Edit / Delete Actions */}
                      {user?.role === 'superadmin' && (
                        <div className="flex items-center space-x-1">
                          <button
                            onClick={() => handleOpenEditModal(item)}
                            className="p-2 bg-slate-50 hover:bg-indigo-50 hover:text-indigo-600 text-slate-600 rounded-xl transition-all active:scale-95"
                            title="Ubah Rincian Broadcast"
                          >
                            <Edit className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleOpenDelete(item.id)}
                            className="p-2 bg-rose-50 hover:bg-rose-100 hover:text-rose-600 text-rose-500 rounded-xl transition-all active:scale-95"
                            title="Hapus Broadcast"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

      </div>

      {/* --- EDIT MODAL --- */}
      {isModalOpen && (
        <div id="broadcast-form-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-lg w-full shadow-2xl overflow-hidden animate-fadeIn">
            <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
              <h4 className="font-bold text-slate-800 text-sm flex items-center">
                <Megaphone className="w-4 h-4 mr-2 text-indigo-600" />
                Edit Rincian Broadcast
              </h4>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <XCircle className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              {/* Title Field */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Judul Pengumuman <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Masukkan judul pengumuman..."
                  value={formTitle}
                  onChange={(e) => setFormTitle(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-semibold"
                />
              </div>

              {/* Message Field */}
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Pesan Pengumuman / Detail <span className="text-rose-500">*</span>
                </label>
                <textarea
                  required
                  rows={4}
                  placeholder="Ketik isi pengumuman lengkap di sini..."
                  value={formMessage}
                  onChange={(e) => setFormMessage(e.target.value)}
                  className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 leading-relaxed"
                ></textarea>
              </div>

              {/* Grid 2 Column for Link and Type */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Type Selection */}
                <div>
                  <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                    Kategori Pengumuman
                  </label>
                  <select
                    value={formType}
                    onChange={(e) => setFormType(e.target.value)}
                    className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-semibold"
                  >
                    <option value="INSTRUCTION">Instruksi (Instruction)</option>
                    <option value="ANNOUNCEMENT">Pengumuman (Announcement)</option>
                    <option value="ALERT">Peringatan (Alert)</option>
                  </select>
                </div>

                {/* Drive Link */}
                <div>
                  <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                    Tautan Google Drive / PDF
                  </label>
                  <input
                    type="url"
                    placeholder="https://drive.google.com/..."
                    value={formDriveLink}
                    onChange={(e) => setFormDriveLink(e.target.value)}
                    className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-xs bg-slate-50 focus:bg-white focus:outline-hidden focus:border-indigo-500 text-slate-700 font-mono"
                  />
                </div>
              </div>

              {/* Status Toggle */}
              <div className="flex items-center space-x-3 pt-2">
                <input
                  id="form-is-active"
                  type="checkbox"
                  checked={formIsActive}
                  onChange={(e) => setFormIsActive(e.target.checked)}
                  className="w-4 h-4 text-indigo-600 border-slate-300 rounded focus:ring-indigo-500"
                />
                <label htmlFor="form-is-active" className="text-xs font-semibold text-slate-700 select-none">
                  Aktifkan langsung (Broadcast langsung ke aplikasi mobile)
                </label>
              </div>

              {/* Action Buttons */}
              <div className="pt-4 border-t border-slate-100 flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-50 active:scale-95 transition-all"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md active:scale-95 transition-all disabled:opacity-50"
                >
                  Simpan Perubahan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- DELETE CONFIRMATION MODAL --- */}
      {isDeleteOpen && (
        <div id="broadcast-delete-modal" className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-sm w-full shadow-2xl p-6 animate-fadeIn">
            <h4 className="font-extrabold text-slate-800 text-sm flex items-center">
              <Trash2 className="w-4 h-4 mr-2 text-rose-500" />
              Hapus Pengumuman?
            </h4>
            <p className="text-xs text-slate-500 mt-2 leading-relaxed">
              Apakah Anda yakin ingin menghapus pengumuman ini secara permanen dari Firestore? Tindakan ini tidak dapat dibatalkan.
            </p>
            <div className="mt-5 flex justify-end space-x-2">
              <button
                onClick={() => setIsDeleteOpen(false)}
                className="px-3.5 py-1.5 border border-slate-200 text-slate-600 rounded-lg text-xs font-bold hover:bg-slate-50"
              >
                Batal
              </button>
              <button
                onClick={handleDeleteSubmit}
                className="px-3.5 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-lg text-xs font-bold shadow-xs active:scale-95 transition-all"
              >
                Hapus
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
