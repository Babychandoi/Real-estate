import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  Building2,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  EyeOff,
  MessageSquareText,
  Phone,
  RefreshCw,
  Search,
  ShieldAlert,
  UserRound,
  Users,
  XCircle,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  dismissReport,
  emergencyHideListing,
  fetchLeadListings,
  fetchReports,
  revealLeadContact,
  resolveReport,
  searchLeads,
  updateLeadStatus,
} from '@/entities/lead/api/leadApi';
import type {
  LeadListingItem,
  LeadListingPage,
  LeadPage,
  ListingReport,
  LeadStatus,
} from '@/entities/lead/model/types';
import { Button } from '@/shared/ui/Button';

type View = 'leads' | 'reports';

const REPORT_LABELS: Record<ListingReport['status'], string> = {
  PENDING: 'Chờ xử lý',
  WAITING_REPLY: 'Chờ phản hồi',
  RESOLVED: 'Đã giải quyết',
  DISMISSED: 'Đã bác bỏ',
  APPEALED: 'Có khiếu nại',
};
const LEAD_LABELS: Record<LeadStatus, string> = {
  NEW: 'Mới nhận',
  CONTACTED: 'Đang liên hệ',
  APPOINTED: 'Đã hẹn xem',
  CLOSED: 'Hoàn tất',
  SPAM: 'Không hợp lệ',
  WITHDRAWN: 'Khách đã rút',
};
const LEAD_BADGES: Record<LeadStatus, string> = {
  NEW: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONTACTED: 'bg-blue-50 text-blue-800 ring-blue-200',
  APPOINTED: 'bg-violet-50 text-violet-800 ring-violet-200',
  CLOSED: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  SPAM: 'bg-slate-100 text-slate-600 ring-slate-200',
  WITHDRAWN: 'bg-slate-100 text-slate-600 ring-slate-200',
};

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));

function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  return (
    <nav aria-label="Phân trang" className="mt-6 flex items-center justify-between border-t border-slate-200 pt-4">
      <span className="text-sm text-slate-600">
        Trang {page + 1}/{totalPages}
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          aria-label="Trang trước"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
          className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 bg-white disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <button
          type="button"
          aria-label="Trang sau"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
          className="grid h-10 w-10 place-items-center rounded-lg border border-slate-300 bg-white disabled:opacity-40"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </span>
    </nav>
  );
}

export default function LeadsAndReportsPage() {
  const [view, setView] = useState<View>('leads');
  const [reports, setReports] = useState<ListingReport[]>([]);
  const [listingPage, setListingPage] = useState<LeadListingPage>({
    items: [],
    totalElements: 0,
    page: 0,
    size: 9,
    totalPages: 0,
  });
  const [leadPage, setLeadPage] = useState<LeadPage>({
    items: [],
    totalElements: 0,
    page: 0,
    size: 10,
    totalPages: 0,
    statusCounts: {},
  });
  const [selectedListing, setSelectedListing] = useState<LeadListingItem | null>(null);
  const [listingQueryInput, setListingQueryInput] = useState('');
  const [listingQuery, setListingQuery] = useState('');
  const [leadQueryInput, setLeadQueryInput] = useState('');
  const [leadQuery, setLeadQuery] = useState('');
  const [leadStatus, setLeadStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [phones, setPhones] = useState<Record<string, string>>({});
  const [error, setError] = useState('');

  const load = useCallback(
    async (page = 0) => {
      setLoading(true);
      setError('');
      try {
        const [reportItems, listings] = await Promise.all([fetchReports(), fetchLeadListings(page, 9, listingQuery)]);
        setReports(reportItems);
        setListingPage(listings);
      } catch {
        setError('Không thể tải dữ liệu từ máy chủ. Kiểm tra kết nối rồi thử lại.');
      } finally {
        setLoading(false);
      }
    },
    [listingQuery],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const leadCounts = useMemo(
    () => ({
      new: leadPage.statusCounts.NEW ?? 0,
      active: (leadPage.statusCounts.CONTACTED ?? 0) + (leadPage.statusCounts.APPOINTED ?? 0),
      closed: leadPage.statusCounts.CLOSED ?? 0,
    }),
    [leadPage.statusCounts],
  );
  const pendingReports = reports.filter((report) =>
    ['PENDING', 'WAITING_REPLY', 'APPEALED'].includes(report.status),
  ).length;

  const act = async (report: ListingReport, action: 'hide' | 'resolve' | 'dismiss') => {
    const note = notes[report.id]?.trim() ?? '';
    if (!note) {
      setError(`Nhập ghi chú xử lý cho vụ việc #${report.caseNumber}.`);
      return;
    }
    setBusyId(report.id);
    setError('');
    try {
      if (action === 'hide') await emergencyHideListing(report.id, note);
      if (action === 'resolve') await resolveReport(report.id, note, false);
      if (action === 'dismiss') await dismissReport(report.id, note, true);
      setNotes((current) => ({ ...current, [report.id]: '' }));
      await load(listingPage.page);
    } catch {
      setError(`Không thể cập nhật vụ việc #${report.caseNumber}. Dữ liệu máy chủ chưa thay đổi.`);
    } finally {
      setBusyId('');
    }
  };

  const loadListingLeads = useCallback(
    async (listing: LeadListingItem, page = 0, query = leadQuery, status = leadStatus) => {
      setLoading(true);
      setError('');
      try {
        setLeadPage(await searchLeads(listing.listingId, page, 10, query, status));
      } catch {
        setError('Không thể tải danh sách người yêu cầu liên hệ của tin này.');
      } finally {
        setLoading(false);
      }
    },
    [leadQuery, leadStatus],
  );

  const openListing = async (listing: LeadListingItem) => {
    setSelectedListing(listing);
    setLeadQuery('');
    setLeadQueryInput('');
    setLeadStatus('');
    await loadListingLeads(listing, 0, '', '');
  };

  const changeLead = async (id: string, status: LeadStatus) => {
    setBusyId(id);
    setError('');
    try {
      await updateLeadStatus(id, status);
      if (selectedListing) await loadListingLeads(selectedListing, leadPage.page);
    } catch {
      setError('Không thể cập nhật trạng thái khách quan tâm. Vui lòng thử lại.');
    } finally {
      setBusyId('');
    }
  };

  const revealPhone = async (id: string) => {
    setBusyId(id);
    setError('');
    try {
      const result = await revealLeadContact(id);
      setPhones((current) => ({ ...current, [id]: result.phone }));
    } catch {
      setError('Không thể xem số liên hệ của khách. Vui lòng kiểm tra quyền và thử lại.');
    } finally {
      setBusyId('');
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950">Khách quan tâm và báo cáo vi phạm</h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-600">
            Theo dõi người muốn liên hệ tin đăng và xử lý phản ánh vi phạm tại hai khu vực riêng biệt.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Tải lại
        </Button>
      </header>

      <nav className="mt-7 grid gap-3 sm:grid-cols-2" aria-label="Loại dữ liệu">
        <button
          type="button"
          onClick={() => setView('leads')}
          aria-current={view === 'leads' ? 'page' : undefined}
          className={`flex min-h-20 items-center gap-4 rounded-xl border px-5 text-left transition-colors ${
            view === 'leads'
              ? 'border-blue-700 bg-blue-50 text-blue-950'
              : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
          }`}
        >
          <span
            className={`grid h-11 w-11 shrink-0 place-items-center rounded-lg ${view === 'leads' ? 'bg-blue-700 text-white' : 'bg-blue-50 text-blue-700'}`}
          >
            <Users className="h-5 w-5" />
          </span>
          <span className="min-w-0">
            <span className="block font-bold">
              Khách quan tâm <span className="tabular-nums">({listingPage.totalElements} tin)</span>
            </span>
            <span className="mt-0.5 block text-sm opacity-75">Quản lý yêu cầu liên hệ theo từng bài đăng</span>
          </span>
        </button>
        <button
          type="button"
          onClick={() => setView('reports')}
          aria-current={view === 'reports' ? 'page' : undefined}
          className={`flex min-h-20 items-center gap-4 rounded-xl border px-5 text-left transition-colors ${
            view === 'reports'
              ? 'border-rose-700 bg-rose-50 text-rose-950'
              : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
          }`}
        >
          <span
            className={`grid h-11 w-11 shrink-0 place-items-center rounded-lg ${view === 'reports' ? 'bg-rose-700 text-white' : 'bg-rose-50 text-rose-700'}`}
          >
            <ShieldAlert className="h-5 w-5" />
          </span>
          <span className="min-w-0">
            <span className="block font-bold">
              Báo cáo vi phạm <span className="tabular-nums">({reports.length})</span>
            </span>
            <span className="mt-0.5 block text-sm opacity-75">
              Phản ánh tin sai, lừa đảo hoặc nội dung không phù hợp
            </span>
          </span>
        </button>
      </nav>

      {error && (
        <div
          className="mt-5 flex items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-800"
          role="alert"
        >
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}
      {loading && (
        <div className="mt-6 rounded-xl border border-slate-200 bg-white p-12 text-center text-slate-600" role="status">
          Đang tải dữ liệu…
        </div>
      )}

      {!loading && view === 'leads' && (
        <section className="mt-6" aria-labelledby="leads-title">
          {!selectedListing ? (
            <>
              <div className="flex flex-col gap-4 border-b border-slate-200 pb-5 lg:flex-row lg:items-end lg:justify-between">
                <div>
                  <h2 id="leads-title" className="text-xl font-bold text-slate-950">
                    Bài đăng có yêu cầu liên hệ
                  </h2>
                  <p className="mt-1 text-sm text-slate-600">
                    Chọn một bài đăng để xem và xử lý những người đã để lại thông tin.
                  </p>
                </div>
                <form
                  onSubmit={(event) => {
                    event.preventDefault();
                    setListingQuery(listingQueryInput.trim());
                  }}
                  className="flex w-full max-w-md gap-2"
                >
                  <label className="relative min-w-0 flex-1">
                    <span className="sr-only">Tìm bài đăng</span>
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                    <input
                      value={listingQueryInput}
                      onChange={(event) => setListingQueryInput(event.target.value)}
                      maxLength={150}
                      placeholder="Tìm theo tiêu đề hoặc địa chỉ"
                      className="min-h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 text-base outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
                    />
                  </label>
                  <Button type="submit">Tìm</Button>
                </form>
              </div>
              {listingPage.items.length === 0 ? (
                <div className="py-16 text-center">
                  <Building2 className="mx-auto h-9 w-9 text-slate-400" />
                  <p className="mt-3 font-medium text-slate-700">Không tìm thấy bài đăng có yêu cầu liên hệ</p>
                </div>
              ) : (
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {listingPage.items.map((listing) => (
                    <button
                      key={listing.listingId}
                      type="button"
                      onClick={() => void openListing(listing)}
                      className="overflow-hidden rounded-xl border border-slate-200 bg-white text-left transition hover:border-blue-400 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600"
                    >
                      {listing.imageUrl ? (
                        <img src={listing.imageUrl} alt="" className="h-40 w-full object-cover" />
                      ) : (
                        <span className="grid h-40 place-items-center bg-slate-100">
                          <Building2 className="h-8 w-8 text-slate-400" />
                        </span>
                      )}
                      <span className="block p-4">
                        <span className="line-clamp-2 font-bold text-slate-950">{listing.title}</span>
                        {listing.address && (
                          <span className="mt-1 line-clamp-1 block text-sm text-slate-600">{listing.address}</span>
                        )}
                        <span className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-200 pt-3 text-center text-xs">
                          <span>
                            <strong className="block text-lg text-amber-700">{listing.newLeads}</strong>
                            Mới
                          </span>
                          <span>
                            <strong className="block text-lg text-blue-700">{listing.activeLeads}</strong>
                            Đang xử lý
                          </span>
                          <span>
                            <strong className="block text-lg text-emerald-700">{listing.closedLeads}</strong>
                            Hoàn tất
                          </span>
                        </span>
                        <span className="mt-3 flex items-center justify-between text-xs text-slate-500">
                          <span>{listing.totalLeads} yêu cầu</span>
                          <span>{formatDate(listing.lastLeadAt)}</span>
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
              {listingPage.totalPages > 1 && (
                <Pagination
                  page={listingPage.page}
                  totalPages={listingPage.totalPages}
                  onPage={(page) => void load(page)}
                />
              )}
            </>
          ) : (
            <>
              <div className="border-b border-slate-200 pb-5">
                <button
                  type="button"
                  onClick={() => setSelectedListing(null)}
                  className="inline-flex min-h-10 items-center gap-2 text-sm font-bold text-blue-800"
                >
                  <ArrowLeft className="h-4 w-4" />
                  Quay lại danh sách bài đăng
                </button>
                <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 id="leads-title" className="text-xl font-bold text-slate-950">
                      {selectedListing.title}
                    </h2>
                    <p className="mt-1 text-sm text-slate-600">
                      {selectedListing.address || 'Chưa có địa chỉ hiển thị'} · {leadPage.totalElements} yêu cầu phù hợp
                    </p>
                  </div>
                  <Link
                    to={`/listings/${selectedListing.slug || selectedListing.listingId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800"
                  >
                    <ExternalLink className="h-4 w-4" />
                    Xem bài đăng
                  </Link>
                </div>
              </div>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  const query = leadQueryInput.trim();
                  setLeadQuery(query);
                  void loadListingLeads(selectedListing, 0, query, leadStatus);
                }}
                className="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_200px_auto]"
              >
                <label className="relative">
                  <span className="sr-only">Tìm người liên hệ</span>
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <input
                    value={leadQueryInput}
                    onChange={(event) => setLeadQueryInput(event.target.value)}
                    maxLength={150}
                    placeholder="Tìm theo tên hoặc nội dung yêu cầu"
                    className="min-h-11 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 text-base outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
                  />
                </label>
                <select
                  aria-label="Lọc trạng thái yêu cầu"
                  value={leadStatus}
                  onChange={(event) => {
                    const status = event.target.value;
                    setLeadStatus(status);
                    void loadListingLeads(selectedListing, 0, leadQuery, status);
                  }}
                  className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-base"
                >
                  <option value="">Tất cả trạng thái</option>
                  {Object.entries(LEAD_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <Button type="submit">Tìm kiếm</Button>
              </form>
              <div className="mt-4 flex flex-wrap gap-2 text-sm">
                <span className="rounded-md bg-amber-50 px-3 py-1.5 font-semibold text-amber-800">
                  Mới {leadCounts.new}
                </span>
                <span className="rounded-md bg-blue-50 px-3 py-1.5 font-semibold text-blue-800">
                  Đang xử lý {leadCounts.active}
                </span>
                <span className="rounded-md bg-emerald-50 px-3 py-1.5 font-semibold text-emerald-800">
                  Hoàn tất {leadCounts.closed}
                </span>
              </div>
              {leadPage.items.length === 0 ? (
                <div className="py-16 text-center">
                  <UserRound className="mx-auto h-9 w-9 text-slate-400" />
                  <p className="mt-3 font-medium text-slate-700">Không tìm thấy yêu cầu liên hệ phù hợp</p>
                </div>
              ) : (
                <div className="mt-3 divide-y divide-slate-200">
                  {leadPage.items.map((lead) => (
                    <article
                      key={lead.id}
                      className="grid gap-4 py-5 md:grid-cols-[minmax(0,1fr)_220px] md:items-center"
                    >
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-bold text-slate-950">{lead.fullName}</h3>
                          <span
                            className={`rounded-md px-2 py-1 text-xs font-semibold ring-1 ring-inset ${LEAD_BADGES[lead.status]}`}
                          >
                            {LEAD_LABELS[lead.status]}
                          </span>
                          <span className="rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-800">
                            {lead.requestType === 'VIEWING' ? 'Muốn hẹn xem' : 'Cần tư vấn'}
                          </span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-600">
                          <span className="inline-flex items-center gap-1.5">
                            <Phone className="h-4 w-4" />
                            {phones[lead.id] || lead.maskedPhone}
                          </span>
                          <span className="inline-flex items-center gap-1.5">
                            <CalendarDays className="h-4 w-4" />
                            {formatDate(lead.createdAt)}
                          </span>
                        </div>
                        <div className="mt-3 flex items-center gap-3 rounded-lg border border-slate-200 p-3">
                          {lead.listingImageUrl ? (
                            <img src={lead.listingImageUrl} alt="" className="h-14 w-20 rounded-md object-cover" />
                          ) : (
                            <span className="grid h-14 w-20 place-items-center rounded-md bg-slate-100">
                              <Building2 className="h-5 w-5 text-slate-500" />
                            </span>
                          )}
                          <div className="min-w-0">
                            <p className="font-semibold text-slate-900">{lead.listingTitle}</p>
                            {lead.listingAddress && (
                              <p className="mt-0.5 text-xs text-slate-600">{lead.listingAddress}</p>
                            )}
                          </div>
                        </div>
                        {lead.note && (
                          <p className="mt-3 flex gap-2 text-sm text-slate-700">
                            <MessageSquareText className="mt-0.5 h-4 w-4 shrink-0" />
                            <span className="break-words">{lead.note}</span>
                          </p>
                        )}
                      </div>
                      <div className="grid gap-2">
                        <Link
                          to={`/listings/${lead.listingSlug || lead.listingId}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-800"
                        >
                          <ExternalLink className="h-4 w-4" />
                          Xem tin
                        </Link>
                        {!phones[lead.id] && (
                          <button
                            type="button"
                            onClick={() => void revealPhone(lead.id)}
                            disabled={busyId === lead.id || !lead.consentPolicy}
                            className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-800 disabled:opacity-50"
                          >
                            <Phone className="h-4 w-4" />
                            Xem số liên hệ
                          </button>
                        )}
                        <label className="text-sm font-semibold text-slate-700">
                          Tiến độ chăm sóc
                          <select
                            aria-label={`Trạng thái khách ${lead.fullName}`}
                            disabled={busyId === lead.id}
                            value={lead.status}
                            onChange={(event) => void changeLead(lead.id, event.target.value as LeadStatus)}
                            className="mt-1.5 min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-base font-medium text-slate-900 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:opacity-50"
                          >
                            {Object.entries(LEAD_LABELS)
                              // WITHDRAWN is set by the requester only; shown when it is the current state.
                              .filter(([value]) => value !== 'WITHDRAWN' || lead.status === 'WITHDRAWN')
                              .map(([value, label]) => (
                                <option key={value} value={value} disabled={value === 'WITHDRAWN'}>
                                  {label}
                                </option>
                              ))}
                          </select>
                        </label>
                      </div>
                    </article>
                  ))}
                </div>
              )}
              {leadPage.totalPages > 1 && (
                <Pagination
                  page={leadPage.page}
                  totalPages={leadPage.totalPages}
                  onPage={(page) => void loadListingLeads(selectedListing, page)}
                />
              )}
            </>
          )}
        </section>
      )}

      {!loading && view === 'reports' && (
        <section className="mt-6" aria-labelledby="reports-title">
          <div className="border-b border-slate-200 pb-5">
            <h2 id="reports-title" className="text-xl font-bold text-slate-950">
              Báo cáo cần kiểm tra
            </h2>
            <p className="mt-1 text-sm text-slate-600">
              {pendingReports} vụ việc đang chờ quyết định. Mọi thao tác đều được lưu vết.
            </p>
          </div>
          {reports.length === 0 ? (
            <div className="py-16 text-center">
              <CheckCircle2 className="mx-auto h-9 w-9 text-emerald-600" />
              <p className="mt-3 font-medium text-slate-700">Không có báo cáo vi phạm cần xử lý</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-200">
              {reports.map((report) => {
                const actionable = ['PENDING', 'WAITING_REPLY', 'APPEALED'].includes(report.status);
                return (
                  <article key={report.id} className="py-6">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="font-bold text-slate-950">Vụ việc #{report.caseNumber}</h3>
                        <p className="mt-1 text-xs text-slate-500">
                          Tin {report.listingId.slice(0, 8)} · {formatDate(report.createdAt)}
                        </p>
                      </div>
                      <span className="rounded-md bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-800 ring-1 ring-inset ring-rose-200">
                        {REPORT_LABELS[report.status]}
                      </span>
                    </div>
                    <p className="mt-4 break-words text-sm text-slate-800">{report.description}</p>
                    {report.resolutionNote && (
                      <p className="mt-3 rounded-lg bg-slate-100 p-3 text-sm text-slate-700">
                        <strong>Ghi chú xử lý:</strong> {report.resolutionNote}
                      </p>
                    )}
                    {actionable && (
                      <div className="mt-5 max-w-3xl">
                        <label htmlFor={`note-${report.id}`} className="text-sm font-semibold text-slate-800">
                          Căn cứ xử lý *
                        </label>
                        <textarea
                          id={`note-${report.id}`}
                          maxLength={1000}
                          value={notes[report.id] ?? ''}
                          onChange={(event) =>
                            setNotes((current) => ({
                              ...current,
                              [report.id]: event.target.value,
                            }))
                          }
                          className="mt-1.5 min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-base outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
                          placeholder="Nhập lý do để lưu vào lịch sử kiểm tra"
                        />
                        <div className="mt-3 flex flex-wrap gap-2">
                          <Button
                            variant="outline"
                            disabled={busyId === report.id}
                            onClick={() => void act(report, 'hide')}
                          >
                            <EyeOff className="mr-1 h-4 w-4" />
                            Tạm ẩn tin
                          </Button>
                          <Button disabled={busyId === report.id} onClick={() => void act(report, 'resolve')}>
                            <CheckCircle2 className="mr-1 h-4 w-4" />
                            Xác nhận đã xử lý
                          </Button>
                          <Button
                            variant="outline"
                            disabled={busyId === report.id}
                            onClick={() => void act(report, 'dismiss')}
                          >
                            <XCircle className="mr-1 h-4 w-4" />
                            Bác bỏ báo cáo
                          </Button>
                        </div>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
