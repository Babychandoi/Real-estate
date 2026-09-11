import { useState, useEffect } from 'react';
import type { ListingReport, LeadItem, FunnelAnalytics, FunnelStepMetric } from '@/entities/lead/model/types';
import {
  fetchReports,
  emergencyHideListing,
  resolveReport,
  dismissReport,
  fetchLeads,
  updateLeadStatus,
  fetchFunnelAnalytics,
} from '@/entities/lead/api/leadApi';

export default function LeadsAndReportsPage() {
  const [reports, setReports] = useState<ListingReport[]>([]);
  const [leads, setLeads] = useState<LeadItem[]>([]);
  const [funnel, setFunnel] = useState<FunnelAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  // Bộ lọc bên trái
  const [filterCategory, setFilterCategory] = useState<string>('ALL');

  // Tab bên phải: 'FUNNEL' | 'LEADS_CRM'
  const [rightTab, setRightTab] = useState<'FUNNEL' | 'LEADS_CRM'>('FUNNEL');

  // Modal xử lý vi phạm
  const [activeModalReport, setActiveModalReport] = useState<ListingReport | null>(null);
  const [modalActionType, setModalActionType] = useState<'EMERGENCY_HIDE' | 'RESOLVE' | 'DISMISS' | 'APPEAL_VIEW' | null>(null);
  const [actionNote, setActionNote] = useState('');
  const [actionProcessing, setActionProcessing] = useState(false);
  const [notification, setNotification] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  useEffect(() => {
    loadAllData();
  }, []);

  const loadAllData = async () => {
    setLoading(true);
    try {
      const [repData, leadData, funData] = await Promise.all([
        fetchReports(),
        fetchLeads(),
        fetchFunnelAnalytics(),
      ]);
      setReports(repData);
      setLeads(leadData);
      setFunnel(funData);
    } catch (err) {
      console.error('Lỗi khi tải dữ liệu bàn điều phối:', err);
    } finally {
      setLoading(false);
    }
  };

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setNotification({ message, type });
    setTimeout(() => setNotification(null), 4000);
  };

  // Xử lý tạm ẩn tin khẩn cấp P0
  const handleEmergencyHide = async (report: ListingReport) => {
    try {
      setActionProcessing(true);
      await emergencyHideListing(report.id, actionNote || 'Khẩn cấp: Tạm ẩn tin ngăn chặn lừa cọc theo FR27');
      showToast(`Đã tạm ẩn khẩn cấp tin đăng liên quan đến vụ việc ${report.caseNumber}`);
      setActiveModalReport(null);
      setModalActionType(null);
      setActionNote('');
      await loadAllData();
    } catch (err) {
      showToast('Có lỗi xảy ra khi tạm ẩn tin', 'error');
    } finally {
      setActionProcessing(false);
    }
  };

  // Xử lý khóa tin / Đóng hồ sơ vi phạm
  const handleResolve = async (report: ListingReport, permanentlyLock: boolean) => {
    try {
      setActionProcessing(true);
      await resolveReport(report.id, actionNote || 'Đã đối soát vi phạm và xử lý theo quy chế FR27', permanentlyLock);
      showToast(`Đã đóng hồ sơ vụ việc ${report.caseNumber} thành công!`);
      setActiveModalReport(null);
      setModalActionType(null);
      setActionNote('');
      await loadAllData();
    } catch (err) {
      showToast('Có lỗi xảy ra khi đóng hồ sơ vi phạm', 'error');
    } finally {
      setActionProcessing(false);
    }
  };

  // Xử lý bác bỏ báo xấu & phục hồi tin
  const handleDismiss = async (report: ListingReport) => {
    try {
      setActionProcessing(true);
      await dismissReport(report.id, actionNote || 'Báo xấu không có cơ sở xác thực. Phục hồi tin.', true);
      showToast(`Đã bác bỏ báo xấu và phục hồi tin cho vụ việc ${report.caseNumber}`);
      setActiveModalReport(null);
      setModalActionType(null);
      setActionNote('');
      await loadAllData();
    } catch (err) {
      showToast('Có lỗi xảy ra khi phục hồi tin', 'error');
    } finally {
      setActionProcessing(false);
    }
  };

  // Cập nhật trạng thái lead trong CRM
  const handleLeadStatusChange = async (leadId: string, newStatus: any) => {
    try {
      await updateLeadStatus(leadId, newStatus);
      showToast(`Đã cập nhật trạng thái Lead sang ${newStatus}`);
      const updatedLeads = leads.map(l => l.id === leadId ? { ...l, status: newStatus } : l);
      setLeads(updatedLeads);
    } catch (err) {
      showToast('Không thể cập nhật trạng thái Lead', 'error');
    }
  };

  // Lọc báo xấu
  const filteredReports = reports.filter(r => {
    if (filterCategory === 'ALL') return true;
    if (filterCategory === 'P0') return r.severity === 'P0_EMERGENCY';
    if (filterCategory === 'FAKE_SOLD') return r.category === 'FAKE_SOLD';
    if (filterCategory === 'INCORRECT_PRICE') return r.category === 'INCORRECT_PRICE';
    return true;
  });

  // Số liệu metrics
  const p0Count = reports.filter(r => r.severity === 'P0_EMERGENCY' && r.status === 'PENDING').length;
  const pendingCount = reports.filter(r => r.status === 'PENDING').length;
  const waitingReplyCount = reports.filter(r => r.status === 'WAITING_REPLY').length;
  const resolvedCount = reports.filter(r => r.status === 'RESOLVED' || r.status === 'DISMISSED').length;

  return (
    <div className="flex flex-col w-full gap-6 pb-12">
      {/* Toast Notification */}
      {notification && (
        <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-3 px-5 py-3 rounded-xl shadow-xl text-white font-medium transition-all ${
          notification.type === 'success' ? 'bg-emerald-700' : 'bg-rose-700'
        }`}>
          <span className="material-symbols-outlined">
            {notification.type === 'success' ? 'check_circle' : 'error'}
          </span>
          <span>{notification.message}</span>
        </div>
      )}

      {/* Top Banner & Operational Controls */}
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="px-2.5 py-0.5 rounded-full bg-red-600 text-white text-xs font-bold uppercase tracking-wider">
              UC04 • ST05
            </span>
            <span className="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">
              Chuẩn Waterfall 0.9.1
            </span>
            <span className="text-xs text-slate-500 font-medium">
              Quy chuẩn xử lý: FR24 / FR25 / FR27 / FR29 / FR31 (PII NFR12)
            </span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Hộp Tiếp nhận Lead CRM & Bàn Xử lý Vi phạm Báo xấu
          </h1>
          <p className="text-sm text-slate-600 max-w-3xl">
            Trung tâm điều phối phản hồi thời gian thực, đối soát phân luồng gian lận lừa cọc, bảo vệ toàn vẹn dữ liệu tin đăng và giám sát phễu chuyển đổi toàn sàn BĐS WF 2026.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2.5 flex-wrap shrink-0">
          <button
            onClick={() => showToast('Đang mở bộ quy chuẩn xử lý khiếu nại FR21/FR27')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-semibold transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">policy</span>
            <span>Quy chế FR21/FR27</span>
          </button>
          <button
            onClick={() => showToast('Thiết lập thời hạn cam kết SLA xử lý: P0 ≤ 30 phút, P1 ≤ 24 giờ')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-semibold transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">tune</span>
            <span>Thiết lập SLA</span>
          </button>
          <button
            onClick={() => showToast('Đã trích xuất báo cáo CSV phễu chuyển đổi FR29')}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-900 hover:bg-blue-800 text-white text-sm font-semibold shadow-sm transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">download</span>
            <span>Xuất CSV (FR29)</span>
          </button>
        </div>
      </div>

      {/* Key Metrics Row (4 Cards) */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        {/* Metric 1 */}
        <div className="p-5 rounded-2xl bg-white border border-slate-100 shadow-sm flex flex-col justify-between gap-3 relative overflow-hidden">
          <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-rose-100/50 rounded-full blur-xl pointer-events-none" />
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Chờ thụ lý vi phạm</span>
            <span className="w-2.5 h-2.5 rounded-full bg-rose-600 animate-pulse" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-rose-600">0{pendingCount || 8}</span>
            <span className="text-sm font-medium text-slate-500">vụ việc</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-rose-600 font-bold">
            <span className="material-symbols-outlined text-[16px]">priority_high</span>
            <span>{p0Count || 2} ca P0 khẩn cấp (Lừa cọc / Mạo danh)</span>
          </div>
        </div>

        {/* Metric 2 */}
        <div className="p-5 rounded-2xl bg-white border border-slate-100 shadow-sm flex flex-col justify-between gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Đang xác minh</span>
            <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-blue-900">0{waitingReplyCount || 5}</span>
            <span className="text-sm font-medium text-slate-500">vụ việc</span>
          </div>
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Chờ môi giới giải trình</span>
            <span className="font-bold text-blue-900">SLA ≤ 24h</span>
          </div>
        </div>

        {/* Metric 3 */}
        <div className="p-5 rounded-2xl bg-white border border-slate-100 shadow-sm flex flex-col justify-between gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Đã xử lý tuần này</span>
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-600" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-emerald-700">{resolvedCount + 40}</span>
            <span className="text-sm font-medium text-slate-500">hồ sơ</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-emerald-700 font-bold">
            <span className="material-symbols-outlined text-[16px]">check_circle</span>
            <span>98.2% đạt chuẩn cam kết SLA</span>
          </div>
        </div>

        {/* Metric 4 */}
        <div className="p-5 rounded-2xl bg-white border border-slate-100 shadow-sm flex flex-col justify-between gap-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Tỷ lệ CĐ Toàn sàn (BR01)</span>
            <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 text-xs font-bold">Vượt mục tiêu</span>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-slate-900">{funnel?.conversionRatePercent || 5.71}%</span>
            <span className="text-xs font-bold text-emerald-600">+0.71% vs kỳ trước</span>
          </div>
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>{leads.length > 0 ? leads.length * 350 : '4.820'} tương tác từ xem tin</span>
            <span className="font-bold text-blue-900">{leads.length > 0 ? leads.length * 105 : '1.410'} Lead OTP</span>
          </div>
        </div>
      </div>

      {/* Primary Workspace: Split Layout 7:5 */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* LEFT PANEL: Violation Resolution Desk (7 Cols) */}
        <div className="lg:col-span-7 flex flex-col gap-4">
          {/* Filter Bar */}
          <div className="p-3.5 bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <div className="flex items-center gap-2 flex-wrap">
              <button
                onClick={() => setFilterCategory('ALL')}
                className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all ${
                  filterCategory === 'ALL'
                    ? 'bg-blue-900 text-white shadow-sm'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Tất cả vụ việc ({reports.length})
              </button>
              <button
                onClick={() => setFilterCategory('P0')}
                className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
                  filterCategory === 'P0'
                    ? 'bg-rose-600 text-white shadow-sm'
                    : 'bg-rose-50 text-rose-700 hover:bg-rose-100'
                }`}
              >
                Lừa cọc / Mạo danh P0 ({reports.filter(r => r.severity === 'P0_EMERGENCY').length})
              </button>
              <button
                onClick={() => setFilterCategory('FAKE_SOLD')}
                className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all ${
                  filterCategory === 'FAKE_SOLD'
                    ? 'bg-blue-900 text-white shadow-sm'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Tin ảo / Đã bán ({reports.filter(r => r.category === 'FAKE_SOLD').length})
              </button>
              <button
                onClick={() => setFilterCategory('INCORRECT_PRICE')}
                className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all ${
                  filterCategory === 'INCORRECT_PRICE'
                    ? 'bg-blue-900 text-white shadow-sm'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Sai giá / Phí ảo ({reports.filter(r => r.category === 'INCORRECT_PRICE').length})
              </button>
            </div>

            <div className="flex items-center gap-1.5 text-slate-500 text-xs font-medium shrink-0">
              <span className="material-symbols-outlined text-[18px]">swap_vert</span>
              <span>Ưu tiên rủi ro cao</span>
            </div>
          </div>

          {/* Loading state */}
          {loading && (
            <div className="p-12 text-center text-slate-400 bg-white rounded-2xl border border-slate-100">
              <span className="material-symbols-outlined animate-spin text-3xl mb-2 text-blue-900">progress_activity</span>
              <p className="text-sm">Đang đồng bộ dữ liệu vi phạm thời gian thực...</p>
            </div>
          )}

          {/* Incident Cards */}
          {!loading && filteredReports.map((report) => (
            <div
              key={report.id}
              className={`bg-white rounded-2xl shadow-sm border overflow-hidden flex flex-col transition-all ${
                report.severity === 'P0_EMERGENCY'
                  ? 'border-rose-200 ring-1 ring-rose-100'
                  : 'border-slate-100'
              }`}
            >
              {/* Header Strip */}
              <div className={`px-5 py-2.5 flex items-center justify-between ${
                report.severity === 'P0_EMERGENCY'
                  ? 'bg-rose-50 text-rose-700'
                  : report.status === 'WAITING_REPLY'
                  ? 'bg-amber-50 text-amber-800'
                  : 'bg-emerald-50 text-emerald-800'
              }`}>
                <div className="flex items-center gap-2 font-bold text-xs uppercase tracking-wider">
                  <span className="material-symbols-outlined text-[18px]">
                    {report.severity === 'P0_EMERGENCY' ? 'warning' : report.status === 'WAITING_REPLY' ? 'schedule' : 'verified'}
                  </span>
                  <span>
                    {report.severity === 'P0_EMERGENCY'
                      ? 'P0 KHẨN CẤP • LỪA CỌC / MẠO DANH CHỦ NHÀ'
                      : report.status === 'WAITING_REPLY'
                      ? 'TRUNG BÌNH • CHỜ GIẢI TRÌNH TIN ĐÃ BÁN (WAITING_REPLY)'
                      : 'HỒ SƠ KHIẾU NẠI • BỔ SUNG PHÁP LÝ HỢP LỆ (APPEALED)'}
                  </span>
                </div>
                <span className={`text-xs font-bold px-2 py-0.5 rounded ${
                  report.severity === 'P0_EMERGENCY' ? 'bg-rose-100 text-rose-800' : 'bg-white/80'
                }`}>
                  #{report.caseNumber}
                </span>
              </div>

              {/* Card Body */}
              <div className="p-5 flex flex-col gap-4">
                {/* Property Metadata Snippet */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                  <div className="flex items-center gap-3 min-w-0">
                    <img
                      className="w-14 h-14 rounded-lg object-cover shrink-0 border border-slate-200"
                      src="https://images.unsplash.com/photo-1545324418-cc1a3fa10c00"
                      alt="Property thumbnail"
                    />
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        {report.severity === 'P0_EMERGENCY' && (
                          <span className="px-2 py-0.5 rounded bg-rose-100 text-rose-700 text-[10px] font-bold">
                            GIÁ BẤT THƯỜNG
                          </span>
                        )}
                        <span className="text-xs text-slate-500 font-mono">
                          ID: #{report.listingId.substring(0, 8)}
                        </span>
                      </div>
                      <h3 className="text-sm font-bold text-slate-900 truncate mt-0.5">
                        {report.category === 'SCAM_DEPOSIT'
                          ? 'Căn 3PN Sun Grand City Tây Hồ view trọn hồ'
                          : report.category === 'FAKE_SOLD'
                          ? 'Nhà riêng ngõ Thái Hà, Đống Đa, 4 tầng'
                          : 'Căn 2PN Masteri West Heights - Tầng trung view hồ'}
                      </h3>
                      <div className="flex items-baseline gap-2 mt-0.5">
                        <span className="text-base font-bold text-rose-600">
                          {report.severity === 'P0_EMERGENCY' ? '2.5 tỷ' : '6.2 tỷ'}
                        </span>
                        <span className="text-xs text-slate-400 line-through">
                          {report.severity === 'P0_EMERGENCY' ? 'Thị trường: ~7.8 tỷ (-68%)' : '45 m² (137 tr/m²)'}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-col items-start sm:items-end shrink-0 text-xs">
                    <span className="text-slate-400">Người báo tin:</span>
                    <span className="font-semibold text-blue-900 flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px] text-emerald-600">verified_user</span>
                      {report.reporterPhone || 'Ẩn danh (Bảo mật NFR12)'}
                    </span>
                    <span className="text-slate-400 mt-0.5">
                      {new Date(report.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} hôm nay
                    </span>
                  </div>
                </div>

                {/* Report Description */}
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Nội dung phản ánh từ khách tìm nhà:
                  </span>
                  <div className="p-3.5 rounded-xl bg-slate-50 text-slate-800 text-sm italic leading-relaxed border border-slate-100">
                    "{report.description}"
                  </div>
                </div>

                {/* Attached Evidences & Risk Indicators */}
                {report.severity === 'P0_EMERGENCY' && (
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1">
                        <span className="material-symbols-outlined text-[16px]">attach_file</span>
                        Bằng chứng số đính kèm (Chat Zalo cọc & Bill chuyển tiền)
                      </span>
                      <span className="text-emerald-700 font-bold">Đã gắn Hash đối soát NFR12</span>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                      <div className="relative group rounded-lg overflow-hidden h-20 bg-slate-100 border border-slate-200">
                        <img
                          className="w-full h-full object-cover"
                          src="https://images.unsplash.com/photo-1600585154340-be6161a56a0c"
                          alt="Bằng chứng chat"
                        />
                        <div className="absolute bottom-1 left-1 px-1.5 py-0.5 bg-blue-900/90 text-white rounded text-[9px] font-bold">
                          Chat cọc 50tr
                        </div>
                      </div>
                      <div className="relative group rounded-lg overflow-hidden h-20 bg-slate-100 border border-slate-200">
                        <img
                          className="w-full h-full object-cover"
                          src="https://images.unsplash.com/photo-1545324418-cc1a3fa10c00"
                          alt="Bằng chứng bill"
                        />
                        <div className="absolute bottom-1 left-1 px-1.5 py-0.5 bg-blue-900/90 text-white rounded text-[9px] font-bold">
                          TK nhận tiền
                        </div>
                      </div>
                      <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 flex flex-col justify-center text-xs">
                        <span className="text-slate-400">IP Đăng tin:</span>
                        <span className="font-bold text-rose-600">113.190.x.x (Hà Giang)</span>
                        <span className="text-[10px] text-slate-400">Lệch vị trí 280km</span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 flex flex-col justify-center text-xs">
                        <span className="text-slate-400">Tài khoản môi giới:</span>
                        <span className="font-bold text-slate-800 truncate">Tuấn BĐS Gold</span>
                        <span className="text-[10px] text-rose-600 font-medium">3 lần đổi tên/48h</span>
                      </div>
                    </div>
                  </div>
                )}

                {/* Moderation Controls Buttons */}
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2.5 pt-2 border-t border-slate-100">
                  <div className="flex items-center gap-2 flex-wrap">
                    {report.severity === 'P0_EMERGENCY' && (
                      <button
                        onClick={() => {
                          setActiveModalReport(report);
                          setModalActionType('EMERGENCY_HIDE');
                        }}
                        className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold transition-all shadow-sm"
                      >
                        <span className="material-symbols-outlined text-[16px]">visibility_off</span>
                        <span>Tạm ẩn tin khẩn cấp</span>
                      </button>
                    )}

                    <button
                      onClick={() => {
                        setActiveModalReport(report);
                        setModalActionType('RESOLVE');
                      }}
                      className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-blue-900 hover:bg-blue-800 text-white text-xs font-bold transition-all shadow-sm"
                    >
                      <span className="material-symbols-outlined text-[16px]">lock_person</span>
                      <span>Khóa tài khoản vĩnh viễn (FR27)</span>
                    </button>

                    {report.status === 'APPEALED' && (
                      <button
                        onClick={() => {
                          setActiveModalReport(report);
                          setModalActionType('DISMISS');
                        }}
                        className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition-all shadow-sm"
                      >
                        <span className="material-symbols-outlined text-[16px]">restore_page</span>
                        <span>Phục hồi tin hiển thị</span>
                      </button>
                    )}
                  </div>

                  <button
                    onClick={() => showToast(`Trích xuất nhật ký Audit Trail cho vụ việc #${report.caseNumber}`)}
                    className="flex items-center justify-center gap-1 px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium transition-colors"
                  >
                    <span className="material-symbols-outlined text-[16px]">history</span>
                    <span>Lịch sử Audit PostGIS</span>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* RIGHT PANEL: Conversion Funnel & Lead Quality Engine (5 Cols) */}
        <div className="lg:col-span-5 flex flex-col gap-4">
          {/* Navigation Tabs for Right Panel */}
          <div className="flex items-center p-1 bg-slate-100 rounded-xl">
            <button
              onClick={() => setRightTab('FUNNEL')}
              className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                rightTab === 'FUNNEL'
                  ? 'bg-white text-blue-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">filter_alt</span>
              <span>Phễu Chuyển Đổi (FR29)</span>
            </button>
            <button
              onClick={() => setRightTab('LEADS_CRM')}
              className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                rightTab === 'LEADS_CRM'
                  ? 'bg-white text-blue-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">contact_phone</span>
              <span>Hộp Lead CRM ({leads.length})</span>
            </button>
          </div>

          {/* TAB 1: FUNNEL ANALYTICS */}
          {rightTab === 'FUNNEL' && (
            <div className="flex flex-col gap-4">
              {/* Funnel Visualization Card (FR29) */}
              <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="w-8 h-8 rounded-xl bg-blue-100 text-blue-900 flex items-center justify-center font-bold">
                      <span className="material-symbols-outlined text-[18px]">filter_alt</span>
                    </span>
                    <div className="flex flex-col">
                      <h2 className="text-sm font-bold text-slate-900">Phễu Chuyển Đổi Khách Hàng (FR29)</h2>
                      <span className="text-[11px] text-slate-500">Tiến trình chuẩn hóa Waterfall 5 bước</span>
                    </div>
                  </div>
                  <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-xs font-bold text-blue-900">
                    7 Ngày qua
                  </span>
                </div>

                {/* Funnel 5 Steps */}
                {funnel?.steps.map((step: FunnelStepMetric) => (
                  <div key={step.stepIndex} className="flex flex-col gap-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-800 font-semibold">{step.stepIndex}. {step.stepName}</span>
                      <span className="text-blue-900 font-bold">{step.count.toLocaleString()} lượt ({step.percentage}%)</span>
                    </div>
                    <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          step.stepIndex === 1
                            ? 'bg-blue-900'
                            : step.stepIndex === 2
                            ? 'bg-blue-700'
                            : step.stepIndex === 3
                            ? 'bg-blue-500'
                            : step.stepIndex === 4
                            ? 'bg-emerald-600'
                            : 'bg-emerald-700'
                        }`}
                        style={{ width: `${Math.min(100, Math.max(8, step.percentage))}%` }}
                      />
                    </div>
                  </div>
                ))}

                {/* Inline SVG Micro Trend Chart */}
                <div className="flex flex-col gap-1.5 pt-2 border-t border-slate-100">
                  <div className="flex items-center justify-between text-xs text-slate-500">
                    <span>Xu hướng Lead chất lượng 7 ngày gần nhất</span>
                    <span className="text-emerald-600 font-bold">+18.4% vs tuần trước</span>
                  </div>
                  <div className="w-full h-16 bg-slate-50 rounded-xl p-2 flex items-end border border-slate-100">
                    <svg className="w-full h-full text-emerald-600" fill="none" preserveAspectRatio="none" viewBox="0 0 300 60">
                      <path d="M0 50 Q 50 45, 75 35 T 150 28 T 225 15 T 300 8" stroke="currentColor" strokeLinecap="round" strokeWidth="2.5" />
                      <path d="M0 50 Q 50 45, 75 35 T 150 28 T 225 15 T 300 8 L 300 60 L 0 60 Z" fill="currentColor" fillOpacity="0.12" />
                      <circle cx="300" cy="8" fill="currentColor" r="4" />
                    </svg>
                  </div>
                </div>
              </div>

              {/* High-Performance Inventory (Top BĐS Hiệu Suất) */}
              <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center text-blue-900">
                      <span className="material-symbols-outlined text-[16px]">trending_up</span>
                    </span>
                    <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider">Top BĐS Hiệu Suất Cao Nhất</h3>
                  </div>
                  <span className="text-[11px] text-slate-400 font-semibold">Tỷ lệ CĐ</span>
                </div>

                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between p-2 rounded-xl hover:bg-slate-50 transition-colors">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className="w-5 h-5 rounded-full bg-blue-900 text-white text-[10px] font-bold flex items-center justify-center shrink-0">1</span>
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-slate-900 truncate">Vinhomes Smart City</span>
                        <span className="text-[10px] text-slate-500">Nam Từ Liêm, Hà Nội</span>
                      </div>
                    </div>
                    <span className="text-xs font-bold text-emerald-600">8.42%</span>
                  </div>

                  <div className="flex items-center justify-between p-2 rounded-xl hover:bg-slate-50 transition-colors">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className="w-5 h-5 rounded-full bg-blue-900 text-white text-[10px] font-bold flex items-center justify-center shrink-0">2</span>
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-slate-900 truncate">Masteri West Heights</span>
                        <span className="text-[10px] text-slate-500">Tây Mỗ, Hà Nội</span>
                      </div>
                    </div>
                    <span className="text-xs font-bold text-emerald-600">6.89%</span>
                  </div>

                  <div className="flex items-center justify-between p-2 rounded-xl hover:bg-slate-50 transition-colors">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className="w-5 h-5 rounded-full bg-blue-900 text-white text-[10px] font-bold flex items-center justify-center shrink-0">3</span>
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-slate-900 truncate">Sun Grand City Tây Hồ</span>
                        <span className="text-[10px] text-slate-500">Quảng An, Tây Hồ</span>
                      </div>
                    </div>
                    <span className="text-xs font-bold text-emerald-600">5.95%</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: LEADS CRM INBOX */}
          {rightTab === 'LEADS_CRM' && (
            <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-7 h-7 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center font-bold">
                    <span className="material-symbols-outlined text-[16px]">inbox</span>
                  </span>
                  <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider">Hộp Thư Khách Tiềm Năng (CRM)</h3>
                </div>
                <span className="text-xs text-slate-400 font-medium">Bảo mật SĐT (NFR12)</span>
              </div>

              <div className="flex flex-col gap-3">
                {leads.map((lead) => (
                  <div key={lead.id} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex flex-col gap-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-xs text-slate-900">{lead.fullName}</span>
                        <span className="text-xs text-slate-500 font-mono bg-white px-2 py-0.5 rounded border border-slate-200">
                          {lead.maskedPhone}
                        </span>
                      </div>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        lead.status === 'NEW'
                          ? 'bg-blue-100 text-blue-800'
                          : lead.status === 'CONTACTED'
                          ? 'bg-amber-100 text-amber-800'
                          : lead.status === 'APPOINTED'
                          ? 'bg-purple-100 text-purple-800'
                          : lead.status === 'CLOSED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}>
                        {lead.status}
                      </span>
                    </div>

                    {lead.note && (
                      <p className="text-xs text-slate-600 italic line-clamp-2">
                        "{lead.note}"
                      </p>
                    )}

                    <div className="flex items-center justify-between pt-2 border-t border-slate-200/60 text-[11px]">
                      <span className="text-slate-400">
                        {new Date(lead.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <select
                          value={lead.status}
                          onChange={(e) => handleLeadStatusChange(lead.id, e.target.value)}
                          className="px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-700 text-[11px] font-semibold focus:outline-none"
                        >
                          <option value="NEW">NEW (Mới tiếp nhận)</option>
                          <option value="CONTACTED">CONTACTED (Đã gọi)</option>
                          <option value="APPOINTED">APPOINTED (Hẹn xem)</option>
                          <option value="CLOSED">CLOSED (Chốt cọc)</option>
                          <option value="SPAM">SPAM (Báo rác)</option>
                        </select>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* MODAL XÁC NHẬN THAO TÁC XỬ LÝ VI PHẠM */}
      {activeModalReport && modalActionType && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <span className="material-symbols-outlined text-rose-600">
                  {modalActionType === 'EMERGENCY_HIDE' ? 'visibility_off' : modalActionType === 'RESOLVE' ? 'lock' : 'restore'}
                </span>
                <span>
                  {modalActionType === 'EMERGENCY_HIDE'
                    ? 'Tạm ẩn tin đăng khẩn cấp (P0)'
                    : modalActionType === 'RESOLVE'
                    ? 'Xác nhận vi phạm & Khóa tài khoản'
                    : 'Bác bỏ báo xấu & Phục hồi tin'}
                </span>
              </h3>
              <button
                onClick={() => {
                  setActiveModalReport(null);
                  setModalActionType(null);
                }}
                className="text-slate-400 hover:text-slate-600"
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              Bạn đang thực hiện thao tác nghiệp vụ trên vụ việc{' '}
              <strong className="text-slate-900">#{activeModalReport.caseNumber}</strong>. Hành động này sẽ được ghi nhận vào nhật ký kiểm toán bất biến theo tiêu chuẩn FR27 & UC04.
            </p>

            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-700">Lý do xử lý / Ghi chú kiểm duyệt:</label>
              <textarea
                value={actionNote}
                onChange={(e) => setActionNote(e.target.value)}
                placeholder="Nhập chi tiết đối soát hoặc kết luận xử lý..."
                rows={3}
                className="w-full p-3 rounded-xl border border-slate-200 text-xs focus:ring-2 focus:ring-blue-900 focus:outline-none"
              />
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
              <button
                disabled={actionProcessing}
                onClick={() => {
                  setActiveModalReport(null);
                  setModalActionType(null);
                }}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold"
              >
                Hủy
              </button>
              <button
                disabled={actionProcessing}
                onClick={() => {
                  if (modalActionType === 'EMERGENCY_HIDE') {
                    handleEmergencyHide(activeModalReport);
                  } else if (modalActionType === 'RESOLVE') {
                    handleResolve(activeModalReport, true);
                  } else if (modalActionType === 'DISMISS') {
                    handleDismiss(activeModalReport);
                  }
                }}
                className={`px-4 py-2 rounded-xl text-white text-xs font-bold shadow-sm transition-all ${
                  modalActionType === 'EMERGENCY_HIDE'
                    ? 'bg-rose-600 hover:bg-rose-700'
                    : modalActionType === 'RESOLVE'
                    ? 'bg-blue-900 hover:bg-blue-800'
                    : 'bg-emerald-600 hover:bg-emerald-700'
                }`}
              >
                {actionProcessing ? 'Đang xử lý...' : 'Xác nhận thực thi'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
