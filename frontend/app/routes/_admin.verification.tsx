import { useState, useEffect } from 'react';
import type { ListingVerification, VerificationStatus } from '@/entities/verification/model/types';
import {
  fetchVerificationQueue,
  approveVerification,
  rejectVerification,
} from '@/entities/verification/api/verificationApi';

export default function VerificationDeskPage() {
  const [queue, setQueue] = useState<ListingVerification[]>([]);
  const [selectedVerification, setSelectedVerification] = useState<ListingVerification | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<VerificationStatus | 'ALL'>('ALL');

  // Modal xử lý
  const [modalAction, setModalAction] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [actionNote, setActionNote] = useState('');
  const [processing, setProcessing] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  useEffect(() => {
    loadQueue();
  }, []);

  const loadQueue = async () => {
    setLoading(true);
    try {
      const data = await fetchVerificationQueue();
      setQueue(data);
      if (data.length > 0 && !selectedVerification) {
        setSelectedVerification(data[0]);
      }
    } catch (err) {
      console.error('Lỗi khi tải hàng đợi thẩm định:', err);
    } finally {
      setLoading(false);
    }
  };

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const handleApprove = async () => {
    if (!selectedVerification) return;
    setProcessing(true);
    try {
      const updated = await approveVerification(selectedVerification.id, actionNote);
      showToast(`Đã phê duyệt và cấp nhãn Tin Chính Chủ cho BĐS: ${selectedVerification.listingTitle}`);
      setSelectedVerification({ ...selectedVerification, status: 'VERIFIED_OWNER', verifierNote: updated.verifierNote });
      setQueue(queue.map(q => q.id === selectedVerification.id ? { ...q, status: 'VERIFIED_OWNER' } : q));
      setModalAction(null);
      setActionNote('');
    } catch (err) {
      showToast('Có lỗi xảy ra khi phê duyệt', 'error');
    } finally {
      setProcessing(false);
    }
  };

  const handleReject = async () => {
    if (!selectedVerification) return;
    if (!actionNote.trim()) {
      showToast('Vui lòng nhập lý do từ chối', 'error');
      return;
    }
    setProcessing(true);
    try {
      const updated = await rejectVerification(selectedVerification.id, actionNote);
      showToast(`Đã từ chối hồ sơ thẩm định của BĐS: ${selectedVerification.listingTitle}`, 'error');
      setSelectedVerification({ ...selectedVerification, status: 'REJECTED', verifierNote: updated.verifierNote });
      setQueue(queue.map(q => q.id === selectedVerification.id ? { ...q, status: 'REJECTED' } : q));
      setModalAction(null);
      setActionNote('');
    } catch (err) {
      showToast('Có lỗi xảy ra khi từ chối', 'error');
    } finally {
      setProcessing(false);
    }
  };

  const filteredQueue = queue.filter(item => {
    if (statusFilter === 'ALL') return true;
    return item.status === statusFilter;
  });

  return (
    <div className="flex flex-col w-full gap-6 pb-12">
      {/* Toast Notification */}
      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-3 px-5 py-3 rounded-xl shadow-xl text-white font-medium transition-all ${
          toast.type === 'success' ? 'bg-emerald-700' : 'bg-rose-700'
        }`}>
          <span className="material-symbols-outlined">
            {toast.type === 'success' ? 'check_circle' : 'error'}
          </span>
          <span>{toast.message}</span>
        </div>
      )}

      {/* Top Moderator Command Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-900 text-white flex items-center justify-center font-bold shadow-sm shrink-0">
            <span className="material-symbols-outlined text-2xl">verified</span>
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-xl font-bold text-slate-900 tracking-tight">
                Bàn Thẩm Định Tin Chính Chủ & Đối Soát eKYC
              </h1>
              <span className="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                98.4% Đạt SLA (≤ 8h BR04)
              </span>
              <span className="px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-800 text-xs font-bold">
                Quy chuẩn FR01 / FR03 / NFR12
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Phân quyền: Chuyên viên Pháp lý Cấp cao • Đối chiếu đối soát 2 cột CCCD gắn chip VNeID và Giấy chứng nhận quyền sở hữu đất
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap shrink-0">
          <button
            onClick={() => showToast('Đang tải quy trình nghiệp vụ thẩm định sổ đỏ 2026')}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">gavel</span>
            <span>Quy trình Thẩm định</span>
          </button>
          <button
            onClick={loadQueue}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-blue-900 hover:bg-blue-800 text-white text-xs font-semibold shadow-sm transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            <span>Làm mới hàng đợi</span>
          </button>
        </div>
      </div>

      {/* Main Verification Workspace (Split Layout) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* LEFT COLUMN: Queue of pending verifications (4 cols) */}
        <div className="lg:col-span-4 flex flex-col gap-3">
          <div className="p-3.5 bg-white rounded-2xl shadow-sm border border-slate-100 flex items-center justify-between">
            <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
              Hàng Đợi Hồ Sơ ({queue.length})
            </span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setStatusFilter('ALL')}
                className={`px-2.5 py-1 rounded-lg text-[11px] font-bold ${
                  statusFilter === 'ALL' ? 'bg-blue-900 text-white' : 'text-slate-500 hover:bg-slate-100'
                }`}
              >
                Tất cả
              </button>
              <button
                onClick={() => setStatusFilter('PENDING')}
                className={`px-2.5 py-1 rounded-lg text-[11px] font-bold ${
                  statusFilter === 'PENDING' ? 'bg-amber-500 text-white' : 'text-slate-500 hover:bg-slate-100'
                }`}
              >
                Chờ duyệt
              </button>
              <button
                onClick={() => setStatusFilter('VERIFIED_OWNER')}
                className={`px-2.5 py-1 rounded-lg text-[11px] font-bold ${
                  statusFilter === 'VERIFIED_OWNER' ? 'bg-emerald-600 text-white' : 'text-slate-500 hover:bg-slate-100'
                }`}
              >
                Đã cấp nhãn
              </button>
            </div>
          </div>

          {loading && (
            <div className="p-8 text-center text-slate-400 bg-white rounded-2xl border border-slate-100 text-xs">
              Đang tải hàng đợi thẩm định...
            </div>
          )}

          {!loading && filteredQueue.map((item) => (
            <div
              key={item.id}
              onClick={() => setSelectedVerification(item)}
              className={`p-4 rounded-2xl cursor-pointer transition-all border ${
                selectedVerification?.id === item.id
                  ? 'bg-blue-50/70 border-blue-900 ring-2 ring-blue-900/20 shadow-sm'
                  : 'bg-white hover:bg-slate-50 border-slate-100'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-500 font-mono">
                  #{item.certificateNumber?.substring(0, 16) || item.id.substring(0, 8)}
                </span>
                <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                  item.status === 'VERIFIED_OWNER'
                    ? 'bg-emerald-100 text-emerald-800'
                    : item.status === 'PENDING'
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-rose-100 text-rose-800'
                }`}>
                  {item.status === 'VERIFIED_OWNER' ? '✓ ĐÃ CẤP NHÃN' : item.status === 'PENDING' ? 'CHỜ THẨM ĐỊNH' : 'TỪ CHỐI'}
                </span>
              </div>

              <h4 className="text-xs font-bold text-slate-900 mt-1.5 line-clamp-1">
                {item.listingTitle || 'Bất động sản nộp hồ sơ chính chủ'}
              </h4>

              <div className="flex items-center justify-between text-[11px] text-slate-500 mt-2">
                <span>Chủ sở hữu: <strong className="text-slate-800">{item.ownerNameOnDoc}</strong></span>
                <span className="font-bold text-rose-600">{item.listingPrice || 'Thỏa thuận'}</span>
              </div>
            </div>
          ))}
        </div>

        {/* RIGHT COLUMN: Side-by-Side 2-Column Comparison Desk (8 cols) */}
        {selectedVerification && (
          <div className="lg:col-span-8 flex flex-col gap-4">
            {/* Property Overview Header */}
            <div className="p-4 bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex flex-col">
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-900 text-[10px] font-bold uppercase">
                    {selectedVerification.verificationType}
                  </span>
                  <span className="text-xs text-slate-400 font-mono">ID: #{selectedVerification.listingId.substring(0, 8)}</span>
                </div>
                <h2 className="text-base font-bold text-slate-900 mt-0.5">
                  {selectedVerification.listingTitle}
                </h2>
                <span className="text-xs text-slate-500">{selectedVerification.listingAddress}</span>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => setModalAction('REJECT')}
                  className="px-3.5 py-2 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-700 text-xs font-bold transition-colors"
                >
                  Từ chối
                </button>
                <button
                  onClick={() => setModalAction('APPROVE')}
                  className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-[16px]">verified</span>
                  <span>Phê duyệt & Gắn nhãn Chính Chủ</span>
                </button>
              </div>
            </div>

            {/* SIDE-BY-SIDE DIFF PANELS */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* PANEL 1: eKYC Chân Dung & CCCD Chủ Tài Khoản */}
              <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-3">
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-lg bg-blue-100 text-blue-900 flex items-center justify-center font-bold text-xs">
                      1
                    </span>
                    <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                      Hồ Sơ Định Danh eKYC (FR01)
                    </span>
                  </div>
                  {selectedVerification.userKyc && (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[10px] font-bold flex items-center gap-1">
                      <span className="material-symbols-outlined text-[12px]">face</span>
                      AI Match {selectedVerification.userKyc.faceMatchScore}%
                    </span>
                  )}
                </div>

                {/* CCCD Image Previews */}
                <div className="grid grid-cols-2 gap-2">
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] text-slate-500 font-medium">Mặt trước CCCD gắn chip</span>
                    <div className="h-28 rounded-xl bg-slate-100 overflow-hidden border border-slate-200 group relative">
                      <img
                        className="w-full h-full object-cover"
                        src="https://images.unsplash.com/photo-1545324418-cc1a3fa10c00"
                        alt="Mặt trước CCCD"
                      />
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white text-xs">
                        Phóng to
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] text-slate-500 font-medium">Ảnh chân dung sinh trắc học</span>
                    <div className="h-28 rounded-xl bg-slate-100 overflow-hidden border border-slate-200 group relative">
                      <img
                        className="w-full h-full object-cover"
                        src="https://images.unsplash.com/photo-1534528741775-53994a69daeb"
                        alt="Ảnh chân dung"
                      />
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white text-xs">
                        Phóng to
                      </div>
                    </div>
                  </div>
                </div>

                {/* Extracted OCR Information */}
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 flex flex-col gap-1.5 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Số CCCD (Bảo mật NFR12):</span>
                    <span className="font-mono font-bold text-slate-900">
                      {selectedVerification.userKyc?.maskedIdNumber || '001****3888'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Họ và tên chủ tài khoản:</span>
                    <span className="font-bold text-blue-900">
                      {selectedVerification.userKyc?.fullName || selectedVerification.ownerNameOnDoc}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Ngày sinh:</span>
                    <span className="font-medium text-slate-800">
                      {selectedVerification.userKyc?.dob || '20/11/1992'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Nơi thường trú:</span>
                    <span className="font-medium text-slate-800 text-right truncate max-w-[180px]">
                      {selectedVerification.userKyc?.address || 'Mễ Trì, Nam Từ Liêm, Hà Nội'}
                    </span>
                  </div>
                </div>
              </div>

              {/* PANEL 2: Giấy Chứng Nhận Quyền Sở Hữu (Sổ Đỏ / Sổ Hồng) */}
              <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-3">
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center font-bold text-xs">
                      2
                    </span>
                    <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                      Giấy Tờ Pháp Lý Thửa Đất (FR03)
                    </span>
                  </div>
                  <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 text-[10px] font-bold">
                    Bản gốc quét màu
                  </span>
                </div>

                {/* Sổ đỏ Image Previews */}
                <div className="flex flex-col gap-1">
                  <span className="text-[11px] text-slate-500 font-medium">Trang 2 & 3 Giấy chứng nhận quyền sở hữu</span>
                  <div className="h-28 rounded-xl bg-slate-100 overflow-hidden border border-slate-200 group relative">
                    <img
                      className="w-full h-full object-cover"
                      src="https://images.unsplash.com/photo-1600585154340-be6161a56a0c"
                      alt="Bản chụp Sổ đỏ"
                    />
                    <div className="absolute bottom-2 left-2 px-2 py-0.5 bg-black/60 text-white rounded text-[10px] font-mono">
                      Watermark Đã đóng dấu kiểm định
                    </div>
                  </div>
                </div>

                {/* Extracted Certificate Information */}
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 flex flex-col gap-1.5 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Số phát hành / Vào sổ:</span>
                    <span className="font-mono font-bold text-slate-900">
                      {selectedVerification.certificateNumber}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Tên chủ sở hữu trên GCN:</span>
                    <span className="font-bold text-emerald-700">
                      {selectedVerification.ownerNameOnDoc}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Thửa đất số / Tờ bản đồ:</span>
                    <span className="font-medium text-slate-800">
                      Thửa {selectedVerification.landPlotNumber || '108'} • Tờ {selectedVerification.mapSheetNumber || '24'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">Diện tích hợp pháp:</span>
                    <span className="font-medium text-slate-800">
                      {selectedVerification.landArea || '128.5 m²'}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* VERIFICATION MATCH MATRIX */}
            <div className="p-4 rounded-2xl bg-emerald-50/70 border border-emerald-200 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-800 uppercase tracking-wider flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[18px]">rule</span>
                  Kết quả Đối Soát Tự Động (Matching Verification Score)
                </span>
                <span className="px-2 py-0.5 rounded-full bg-emerald-600 text-white text-xs font-bold">
                  Khớp 100% (Đủ điều kiện cấp nhãn)
                </span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs pt-1">
                <div className="flex items-center gap-2 p-2 rounded-lg bg-white border border-emerald-100">
                  <span className="material-symbols-outlined text-emerald-600 text-[18px]">check_circle</span>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-slate-400">Đối chiếu danh tính</span>
                    <span className="font-bold text-slate-800">Trùng khớp Họ tên</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 p-2 rounded-lg bg-white border border-emerald-100">
                  <span className="material-symbols-outlined text-emerald-600 text-[18px]">check_circle</span>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-slate-400">Định danh công dân</span>
                    <span className="font-bold text-slate-800">eKYC Đã xác thực</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 p-2 rounded-lg bg-white border border-emerald-100">
                  <span className="material-symbols-outlined text-emerald-600 text-[18px]">check_circle</span>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-slate-400">Tranh chấp / Thế chấp</span>
                    <span className="font-bold text-slate-800">Sạch quy hoạch</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* MODAL PHÊ DUYỆT / TỪ CHỐI */}
      {modalAction && selectedVerification && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <span className={`material-symbols-outlined ${modalAction === 'APPROVE' ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {modalAction === 'APPROVE' ? 'verified' : 'cancel'}
                </span>
                <span>
                  {modalAction === 'APPROVE'
                    ? 'Xác nhận Phê duyệt & Cấp nhãn Tin Chính Chủ'
                    : 'Từ chối cấp nhãn Tin Chính Chủ'}
                </span>
              </h3>
              <button onClick={() => setModalAction(null)} className="text-slate-400 hover:text-slate-600">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              Thao tác này sẽ gắn huy hiệu độc quyền{' '}
              <strong className="text-emerald-700">"Sổ hồng chính chủ - Đã xác thực eKYC"</strong> lên tin đăng và ưu tiên vị trí hiển thị trên bản đồ GIS.
            </p>

            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-700">
                {modalAction === 'APPROVE' ? 'Ghi chú kiểm duyệt thẩm định:' : 'Lý do từ chối hồ sơ (Bắt buộc):'}
              </label>
              <textarea
                value={actionNote}
                onChange={(e) => setActionNote(e.target.value)}
                placeholder={modalAction === 'APPROVE' ? 'Thông tin hợp lệ, đủ điều kiện cấp nhãn chính chủ...' : 'Hình ảnh sổ đỏ mờ, không khớp số tờ bản đồ...'}
                rows={3}
                className="w-full p-3 rounded-xl border border-slate-200 text-xs focus:ring-2 focus:ring-blue-900 focus:outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
              <button
                disabled={processing}
                onClick={() => setModalAction(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold"
              >
                Hủy
              </button>
              <button
                disabled={processing}
                onClick={modalAction === 'APPROVE' ? handleApprove : handleReject}
                className={`px-4 py-2 rounded-xl text-white text-xs font-bold shadow-sm transition-all ${
                  modalAction === 'APPROVE' ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                }`}
              >
                {processing ? 'Đang lưu...' : 'Xác nhận'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
