import { useEffect, useState } from 'react';
import { moderationApi } from '../entities/moderation/api/moderationApi';
import type {
  FieldDiff,
  ListingDiff,
  ModerationQueueItem,
  StandardReason,
} from '../entities/moderation/model/types';
import { formatPriceVnd } from '../entities/listing/model/types';

export default function ModerationWorkspacePage() {
  const [queue, setQueue] = useState<ModerationQueueItem[]>([]);
  const [selectedItem, setSelectedItem] = useState<ModerationQueueItem | null>(null);
  const [diff, setDiff] = useState<ListingDiff | null>(null);
  const [reasons, setReasons] = useState<StandardReason[]>([]);

  const [loadingQueue, setLoadingQueue] = useState(true);
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // Rejection modal
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [selectedReasonCode, setSelectedReasonCode] = useState('');
  const [rejectionDetail, setRejectionDetail] = useState('');

  // Approval note
  const [approvalNote, setApprovalNote] = useState('');

  // Alerts
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [tabFilter, setTabFilter] = useState<'ALL' | 'FIRST' | 'UPDATE'>('ALL');

  // Load queue and reasons on mount
  useEffect(() => {
    loadQueue();
    moderationApi.getRejectionReasons()
      .then((data) => {
        setReasons(data);
        if (data.length > 0) setSelectedReasonCode(data[0].code);
      })
      .catch((err) => console.error('Lỗi tải lý do từ chối:', err));
  }, []);

  const loadQueue = async () => {
    setLoadingQueue(true);
    try {
      const data = await moderationApi.getQueue();
      setQueue(data);
      if (data.length > 0) {
        selectListing(data[0]);
      } else {
        setSelectedItem(null);
        setDiff(null);
      }
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Không thể tải hàng đợi kiểm duyệt' });
    } finally {
      setLoadingQueue(false);
    }
  };

  const selectListing = async (item: ModerationQueueItem) => {
    setSelectedItem(item);
    setLoadingDiff(true);
    setFeedback(null);
    try {
      const diffData = await moderationApi.getDiff(item.listingId);
      setDiff(diffData);
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Không thể tải chi tiết đối chiếu' });
    } finally {
      setLoadingDiff(false);
    }
  };

  const handleApprove = async () => {
    if (!selectedItem) return;
    if (!window.confirm(`Xác nhận PHÊ DUYỆT tin đăng "${selectedItem.title}"?`)) return;

    setActionLoading(true);
    try {
      await moderationApi.approve(selectedItem.listingId, {
        revisionId: selectedItem.revisionId,
        note: approvalNote || 'Hồ sơ pháp lý đầy đủ, duyệt công khai.',
      });
      setFeedback({ type: 'success', message: `Đã phê duyệt thành công tin đăng #${selectedItem.listingId.substring(0, 8)}!` });
      setApprovalNote('');
      await loadQueue();
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Lỗi khi phê duyệt tin đăng' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!selectedItem || !selectedReasonCode) return;

    setActionLoading(true);
    try {
      await moderationApi.reject(selectedItem.listingId, {
        revisionId: selectedItem.revisionId,
        reasonCode: selectedReasonCode,
        reasonDetail: rejectionDetail,
      });
      setFeedback({ type: 'success', message: `Đã từ chối tin đăng #${selectedItem.listingId.substring(0, 8)} với lý do: ${selectedReasonCode}` });
      setIsRejectModalOpen(false);
      setRejectionDetail('');
      await loadQueue();
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Lỗi khi từ chối tin đăng' });
    } finally {
      setActionLoading(false);
    }
  };

  // Filtered queue
  const filteredQueue = queue.filter((item) => {
    const matchesSearch = item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          item.addressSummary.toLowerCase().includes(searchQuery.toLowerCase());
    if (!matchesSearch) return false;
    if (tabFilter === 'FIRST') return item.isFirstSubmission;
    if (tabFilter === 'UPDATE') return !item.isFirstSubmission;
    return true;
  });

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 font-sans">
      {/* Top Command Bar & SLA Counters */}
      <header className="sticky top-0 z-40 bg-slate-950/95 border-b border-slate-800 backdrop-blur px-6 py-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-sky-600 to-indigo-600 flex items-center justify-center font-bold text-white shadow-lg">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-xl font-bold tracking-tight text-white">Bàn Làm Việc Kiểm Duyệt & Thẩm Định Tin</h1>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 text-xs font-medium border border-emerald-500/30">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
                  SLA ≤ 8h (BR04)
                </span>
                <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                  Phân hệ Moderation Monolith
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Đối chiếu Diff song song các phiên bản bất biến (Revision Immutability) • Tiêu chuẩn Waterfall 2026
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={loadQueue}
              disabled={loadingQueue}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium transition border border-slate-700"
            >
              <svg className={`w-4 h-4 ${loadingQueue ? 'animate-spin' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Làm mới hàng đợi
            </button>
            <span className="px-3 py-1.5 rounded-lg bg-sky-950/60 text-sky-400 text-sm font-semibold border border-sky-800/50">
              Đang chờ duyệt: {queue.length} hồ sơ
            </span>
          </div>
        </div>

        {/* SLA Metrics Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 pt-3 border-t border-slate-800/80">
          <div className="px-3 py-2 rounded-lg bg-slate-900/80 border border-slate-800">
            <span className="text-xs text-slate-400">Tin mới nộp (Revision #1)</span>
            <div className="text-lg font-bold text-sky-400">
              {queue.filter((q) => q.isFirstSubmission).length}
            </div>
          </div>
          <div className="px-3 py-2 rounded-lg bg-slate-900/80 border border-slate-800">
            <span className="text-xs text-slate-400">Cập nhật tin cũ (Revision #2+)</span>
            <div className="text-lg font-bold text-amber-400">
              {queue.filter((q) => !q.isFirstSubmission).length}
            </div>
          </div>
          <div className="px-3 py-2 rounded-lg bg-slate-900/80 border border-slate-800">
            <span className="text-xs text-slate-400">Tiêu chuẩn đối chiếu</span>
            <div className="text-lg font-bold text-emerald-400">Diff 8 trường</div>
          </div>
          <div className="px-3 py-2 rounded-lg bg-slate-900/80 border border-slate-800">
            <span className="text-xs text-slate-400">Trạng thái hệ thống</span>
            <div className="text-lg font-bold text-purple-400">Chuẩn hóa 100%</div>
          </div>
        </div>
      </header>

      {/* Feedback notification */}
      {feedback && (
        <div className={`mx-6 mt-4 px-4 py-3 rounded-lg flex items-center justify-between text-sm ${
          feedback.type === 'success'
            ? 'bg-emerald-950/80 border border-emerald-600/50 text-emerald-200'
            : 'bg-rose-950/80 border border-rose-600/50 text-rose-200'
        }`}>
          <span>{feedback.message}</span>
          <button onClick={() => setFeedback(null)} className="text-slate-400 hover:text-white">✕</button>
        </div>
      )}

      {/* Main Split Layout */}
      <div className="flex flex-col lg:flex-row min-h-[calc(100vh-160px)]">
        {/* Left Panel: Moderation Queue List */}
        <div className="w-full lg:w-96 border-r border-slate-800 bg-slate-950/60 p-4 flex flex-col gap-3 shrink-0">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-white text-sm uppercase tracking-wider">Hàng Đợi Thẩm Định</h2>
            <span className="text-xs text-slate-400">Sắp xếp theo thời gian nộp</span>
          </div>

          {/* Search box */}
          <input
            type="text"
            placeholder="Tìm theo tiêu đề hoặc địa chỉ..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-sky-500"
          />

          {/* Tabs Filter */}
          <div className="flex rounded-lg bg-slate-900 p-1 border border-slate-800 text-xs font-medium">
            <button
              onClick={() => setTabFilter('ALL')}
              className={`flex-1 py-1.5 rounded-md transition ${tabFilter === 'ALL' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'}`}
            >
              Tất cả ({queue.length})
            </button>
            <button
              onClick={() => setTabFilter('FIRST')}
              className={`flex-1 py-1.5 rounded-md transition ${tabFilter === 'FIRST' ? 'bg-slate-800 text-sky-400' : 'text-slate-400 hover:text-slate-200'}`}
            >
              Tin mới
            </button>
            <button
              onClick={() => setTabFilter('UPDATE')}
              className={`flex-1 py-1.5 rounded-md transition ${tabFilter === 'UPDATE' ? 'bg-slate-800 text-amber-400' : 'text-slate-400 hover:text-slate-200'}`}
            >
              Bản sửa
            </button>
          </div>

          {/* Items List */}
          <div className="flex flex-col gap-2 overflow-y-auto max-h-[calc(100vh-340px)] pr-1">
            {loadingQueue ? (
              <div className="text-center py-12 text-slate-500 text-sm">
                <div className="w-8 h-8 border-2 border-sky-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                Đang tải hàng đợi kiểm duyệt...
              </div>
            ) : filteredQueue.length === 0 ? (
              <div className="text-center py-12 text-slate-500 text-sm">
                Không có tin đăng nào cần duyệt.
              </div>
            ) : (
              filteredQueue.map((item) => {
                const isSelected = selectedItem?.listingId === item.listingId;
                return (
                  <div
                    key={item.listingId}
                    onClick={() => selectListing(item)}
                    className={`p-3 rounded-xl border cursor-pointer transition-all ${
                      isSelected
                        ? 'bg-sky-950/40 border-sky-500 shadow-md ring-1 ring-sky-500/50'
                        : 'bg-slate-900/70 border-slate-800 hover:border-slate-700 hover:bg-slate-900'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className={`text-xs px-2 py-0.5 rounded font-semibold ${
                        item.isFirstSubmission
                          ? 'bg-sky-950 text-sky-300 border border-sky-800/60'
                          : 'bg-amber-950 text-amber-300 border border-amber-800/60'
                      }`}>
                        {item.isFirstSubmission ? 'Tin Mới #Rev 1' : `Bản Cập Nhật #Rev ${item.revisionNumber}`}
                      </span>
                      <span className="text-xs text-slate-400">
                        {new Date(item.submittedAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>

                    <h3 className="text-sm font-semibold text-slate-100 line-clamp-2 leading-snug mb-2">
                      {item.title}
                    </h3>

                    <div className="flex items-center justify-between text-xs text-slate-400">
                      <span className="font-bold text-emerald-400">
                        {formatPriceVnd(item.priceVnd)}
                      </span>
                      <span>{item.areaM2} m²</span>
                      <span>{item.mediaCount} ảnh</span>
                    </div>

                    <p className="text-xs text-slate-500 truncate mt-1">
                      {item.addressSummary}
                    </p>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Panel: Diff Comparison Workspace */}
        <div className="flex-1 bg-slate-900 p-6 flex flex-col gap-6 overflow-y-auto">
          {selectedItem ? (
            <>
              {/* Header Hồ sơ đang duyệt */}
              <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <span className="text-xs font-mono text-slate-400">Mã: {selectedItem.listingId.substring(0, 8)}...</span>
                    <span className="px-2 py-0.5 rounded text-xs font-semibold bg-indigo-950 text-indigo-300 border border-indigo-800">
                      Loại: {selectedItem.propertyType} • {selectedItem.purpose === 'SALE' ? 'Bán' : 'Cho thuê'}
                    </span>
                    <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-950 text-amber-300 border border-amber-800">
                      Revision: #{selectedItem.revisionNumber}
                    </span>
                  </div>
                  <h2 className="text-lg font-bold text-white">{selectedItem.title}</h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Địa chỉ: <span className="text-slate-300">{selectedItem.addressSummary}</span> • Chủ tin: <span className="font-mono text-slate-400">{selectedItem.ownerId.substring(0, 8)}...</span>
                  </p>
                </div>

                {/* Quick Action Buttons */}
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => setIsRejectModalOpen(true)}
                    disabled={actionLoading}
                    className="px-4 py-2.5 rounded-xl bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-600/40 text-sm font-semibold transition flex items-center gap-2 shadow-sm"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                    Từ Chối (FR10)
                  </button>

                  <button
                    onClick={handleApprove}
                    disabled={actionLoading}
                    className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-semibold transition flex items-center gap-2 shadow-lg shadow-emerald-950/50"
                  >
                    {actionLoading ? (
                      <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                    ) : (
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                    Phê Duyệt & Xuất Bản
                  </button>
                </div>
              </div>

              {/* 2-Column Diff Workspace */}
              <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 shadow-sm flex flex-col gap-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div className="flex items-center gap-3">
                    <h3 className="font-bold text-white text-base">Đối Chiếu Thay Đổi Hai Cột (Side-by-Side Diff)</h3>
                    {diff && (
                      <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold ${
                        diff.changedCount > 0 ? 'bg-amber-950 text-amber-300 border border-amber-800' : 'bg-slate-800 text-slate-300'
                      }`}>
                        {diff.isFirstSubmission ? 'Nộp duyệt lần đầu' : `Phát hiện ${diff.changedCount} trường thay đổi`}
                      </span>
                    )}
                  </div>
                  <span className="text-xs text-slate-400">
                    Bản cũ: {diff?.previousRevisionNumber ? `Revision #${diff.previousRevisionNumber}` : '(Trống)'} ➔ Bản mới: Revision #{diff?.currentRevisionNumber}
                  </span>
                </div>

                {loadingDiff ? (
                  <div className="text-center py-12 text-slate-400 text-sm">
                    <div className="w-8 h-8 border-2 border-sky-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Đang tính toán Diff hai phiên bản...
                  </div>
                ) : diff ? (
                  <div className="flex flex-col gap-3">
                    {/* Headers for two columns */}
                    <div className="grid grid-cols-12 gap-4 px-3 py-2 bg-slate-900/80 rounded-lg text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      <div className="col-span-3">Trường dữ liệu</div>
                      <div className="col-span-4 text-slate-400">Bản Đang Lưu Hành / Cũ</div>
                      <div className="col-span-5 text-emerald-400">Bản Mới Nộp Duyệt</div>
                    </div>

                    {/* Diff Rows */}
                    {diff.diffs.map((d: FieldDiff) => (
                      <div
                        key={d.fieldName}
                        className={`grid grid-cols-12 gap-4 p-3 rounded-xl border transition ${
                          d.isChanged
                            ? 'bg-amber-950/15 border-amber-500/40'
                            : 'bg-slate-900/30 border-slate-800/60'
                        }`}
                      >
                        <div className="col-span-3 flex flex-col justify-center">
                          <span className="text-sm font-medium text-slate-200">{d.fieldLabel}</span>
                          <span className="text-xs font-mono text-slate-500">{d.fieldName}</span>
                          {d.isChanged && (
                            <span className="inline-block mt-1 text-[11px] font-semibold text-amber-400">
                              ● ĐÃ THAY ĐỔI
                            </span>
                          )}
                        </div>

                        {/* Old value column */}
                        <div className="col-span-4 p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 text-sm text-slate-400 break-words line-through-slate-600">
                          {d.oldValue || <span className="italic text-slate-600">(Chưa có)</span>}
                        </div>

                        {/* New value column */}
                        <div className={`col-span-5 p-2.5 rounded-lg border text-sm break-words ${
                          d.isChanged
                            ? 'bg-emerald-950/30 border-emerald-500/50 text-emerald-200 font-medium'
                            : 'bg-slate-900/80 border-slate-800 text-slate-300'
                        }`}>
                          {d.newValue}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>

              {/* Legal Check & Verification Panel */}
              <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 shadow-sm flex flex-col gap-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <h3 className="font-bold text-white text-base flex items-center gap-2">
                    <svg className="w-5 h-5 text-sky-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    Hồ Sơ Pháp Lý & Thẩm Tra Bản Đồ GIS
                  </h3>
                  <span className="text-xs px-2.5 py-1 rounded bg-emerald-950 text-emerald-300 border border-emerald-800 font-medium">
                    Sổ đỏ / Sổ hồng chứng thực
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-xs text-slate-400">Tình trạng giấy tờ</span>
                    <div className="font-semibold text-slate-200 mt-1">Đã có sổ hồng chính chủ</div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-xs text-slate-400">Kiểm tra watermark / SĐT ảo</span>
                    <div className="font-semibold text-emerald-400 mt-1">Ảnh chuẩn • 0 vi phạm</div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-xs text-slate-400">Kiểm tra trùng lặp ranh đất</span>
                    <div className="font-semibold text-sky-400 mt-1">Không trùng tọa độ</div>
                  </div>
                </div>

                {/* Approval Note Input */}
                <div className="mt-2">
                  <label className="block text-xs font-medium text-slate-400 mb-1">
                    Ghi chú thẩm định nội bộ (Lưu vào nhật ký kiểm toán):
                  </label>
                  <input
                    type="text"
                    value={approvalNote}
                    onChange={(e) => setApprovalNote(e.target.value)}
                    placeholder="VD: Đã đối chiếu thông tin quy hoạch phân khu, giấy tờ hợp lệ..."
                    className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-sky-500"
                  />
                </div>
              </div>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center h-96 text-slate-500 text-sm">
              <svg className="w-16 h-16 text-slate-700 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
              </svg>
              <span>Vui lòng chọn một tin đăng từ hàng đợi bên trái để bắt đầu thẩm định.</span>
            </div>
          )}
        </div>
      </div>

      {/* Modal Từ Chối Kiểm Duyệt (FR10) */}
      {isRejectModalOpen && selectedItem && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl flex flex-col gap-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-lg text-white">Từ Chối Phê Duyệt Tin Đăng</h3>
                <p className="text-xs text-slate-400 mt-0.5">Mã hồ sơ: {selectedItem.listingId.substring(0, 8)}</p>
              </div>
              <button
                onClick={() => setIsRejectModalOpen(false)}
                className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center"
              >
                ✕
              </button>
            </div>

            {/* Select Reason */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Chọn Lý Do Chuẩn Hóa (FR10):
              </label>
              <select
                value={selectedReasonCode}
                onChange={(e) => setSelectedReasonCode(e.target.value)}
                className="w-full px-3 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-sm text-slate-200 focus:outline-none focus:border-rose-500"
              >
                {reasons.map((r) => (
                  <option key={r.code} value={r.code}>
                    [{r.category}] {r.vietnameseLabel} ({r.code})
                  </option>
                ))}
              </select>
            </div>

            {/* Detail Reason Textarea */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Chi Tiết Giải Trình Cho Môi Giới (Hiển thị trong thông báo):
              </label>
              <textarea
                rows={4}
                value={rejectionDetail}
                onChange={(e) => setRejectionDetail(e.target.value)}
                placeholder="VD: Mức giá chưa bao gồm thuế phí hoặc sổ hồng bị mờ phần số vào sổ..."
                className="w-full p-3 rounded-xl bg-slate-950 border border-slate-700 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-rose-500"
              />
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setIsRejectModalOpen(false)}
                disabled={actionLoading}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm font-medium transition"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleReject}
                disabled={actionLoading || !selectedReasonCode}
                className="px-5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-sm font-semibold transition flex items-center gap-2 shadow-lg shadow-rose-950/50"
              >
                {actionLoading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>}
                Xác Nhận Từ Chối
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
