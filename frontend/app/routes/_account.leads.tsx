import { useCallback, useEffect, useState } from "react";
import {
  ArrowLeft,
  Building2,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  MapPin,
  Phone,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import { Link } from "react-router-dom";
import {
  fetchLeadListings,
  revealLeadContact,
  searchLeads,
  updateLeadStatus,
} from "@/entities/lead/api/leadApi";
import type {
  LeadItem,
  LeadListingItem,
  LeadListingPage,
  LeadPage,
  LeadStatus,
} from "@/entities/lead/model/types";

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: "Mới nhận",
  CONTACTED: "Đã liên hệ",
  APPOINTED: "Đã hẹn xem",
  CLOSED: "Hoàn tất",
  SPAM: "Không hợp lệ",
};
const STATUS_STYLES: Record<LeadStatus, string> = {
  NEW: "bg-amber-50 text-amber-800",
  CONTACTED: "bg-blue-50 text-blue-800",
  APPOINTED: "bg-violet-50 text-violet-800",
  CLOSED: "bg-emerald-50 text-emerald-800",
  SPAM: "bg-slate-100 text-slate-700",
};
const formatDate = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
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
  if (totalPages <= 1) return null;
  return (
    <nav
      aria-label="Phân trang"
      className="mt-7 flex items-center justify-between border-t border-slate-200 pt-5"
    >
      <span className="text-sm text-slate-600">
        Trang {page + 1}/{totalPages}
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          onClick={() => onPage(page - 1)}
          disabled={page === 0}
          className="grid min-h-10 min-w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={() => onPage(page + 1)}
          disabled={page + 1 >= totalPages}
          className="grid min-h-10 min-w-10 place-items-center rounded-lg border border-slate-300 disabled:opacity-40"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </span>
    </nav>
  );
}

export function MyLeadsPage() {
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
  const [selectedListing, setSelectedListing] =
    useState<LeadListingItem | null>(null);
  const [listingInput, setListingInput] = useState("");
  const [listingQuery, setListingQuery] = useState("");
  const [leadInput, setLeadInput] = useState("");
  const [leadQuery, setLeadQuery] = useState("");
  const [leadStatus, setLeadStatus] = useState("");
  const [phones, setPhones] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState("");
  const [error, setError] = useState("");

  const loadListings = useCallback(
    async (page = 0) => {
      setLoading(true);
      setError("");
      try {
        setListingPage(await fetchLeadListings(page, 9, listingQuery));
      } catch {
        setError(
          "Không thể tải các bài đăng có yêu cầu liên hệ. Vui lòng thử lại.",
        );
      } finally {
        setLoading(false);
      }
    },
    [listingQuery],
  );
  useEffect(() => {
    void loadListings();
  }, [loadListings]);

  const loadLeads = useCallback(
    async (
      listing: LeadListingItem,
      page = 0,
      query = leadQuery,
      status = leadStatus,
    ) => {
      setLoading(true);
      setError("");
      try {
        setLeadPage(
          await searchLeads(listing.listingId, page, 10, query, status),
        );
      } catch {
        setError(
          "Không thể tải danh sách người đã yêu cầu liên hệ cho tin này.",
        );
      } finally {
        setLoading(false);
      }
    },
    [leadQuery, leadStatus],
  );
  const openListing = async (listing: LeadListingItem) => {
    setSelectedListing(listing);
    setLeadInput("");
    setLeadQuery("");
    setLeadStatus("");
    await loadLeads(listing, 0, "", "");
  };
  const revealPhone = async (lead: LeadItem) => {
    setBusyId(lead.id);
    setError("");
    try {
      const result = await revealLeadContact(lead.id);
      setPhones((current) => ({ ...current, [lead.id]: result.phone }));
    } catch {
      setError(
        "Không thể xem số liên hệ. Kiểm tra quyền truy cập rồi thử lại.",
      );
    } finally {
      setBusyId("");
    }
  };
  const changeStatus = async (lead: LeadItem, status: LeadStatus) => {
    setBusyId(lead.id);
    setError("");
    try {
      await updateLeadStatus(lead.id, status);
      if (selectedListing) await loadLeads(selectedListing, leadPage.page);
    } catch {
      setError(
        "Không thể cập nhật trạng thái chăm sóc. Dữ liệu chưa được thay đổi.",
      );
    } finally {
      setBusyId("");
    }
  };

  return (
    <main className="min-h-full bg-white px-4 py-8 md:px-8 lg:py-10">
      <div className="mx-auto max-w-6xl">
        <header className="flex flex-col justify-between gap-5 border-b border-slate-200 pb-7 md:flex-row md:items-end">
          <div className="max-w-3xl">
            <h1 className="text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">
              Hộp thư khách quan tâm
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600 md:text-base">
              Theo dõi yêu cầu liên hệ theo từng bài đăng để không lẫn khách của
              các tin khác nhau.
            </p>
          </div>
          <button
            type="button"
            onClick={() =>
              selectedListing
                ? void loadLeads(selectedListing, leadPage.page)
                : void loadListings(listingPage.page)
            }
            disabled={loading}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </header>
        {error && (
          <div
            role="alert"
            className="mt-6 flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-rose-800"
          >
            <X className="mt-0.5 h-4 w-4 shrink-0" />
            <p className="text-sm font-medium">{error}</p>
          </div>
        )}
        {loading ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <div
                key={index}
                className="h-80 animate-pulse rounded-xl bg-slate-100"
              />
            ))}
          </div>
        ) : !selectedListing ? (
          <section className="mt-7" aria-labelledby="listing-leads-title">
            <div className="flex flex-col gap-4 border-b border-slate-200 pb-5 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
                  <ShieldCheck className="h-4 w-4 text-blue-700" />
                  {listingPage.totalElements} bài đăng có yêu cầu liên hệ
                </div>
                <h2
                  id="listing-leads-title"
                  className="mt-2 text-xl font-bold text-slate-950"
                >
                  Chọn bài đăng để xem khách quan tâm
                </h2>
              </div>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  setListingQuery(listingInput.trim());
                }}
                className="flex w-full max-w-md gap-2"
              >
                <label className="relative min-w-0 flex-1">
                  <span className="sr-only">Tìm bài đăng</span>
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <input
                    value={listingInput}
                    onChange={(event) => setListingInput(event.target.value)}
                    placeholder="Tìm tiêu đề hoặc địa điểm"
                    className="min-h-11 w-full rounded-lg border border-slate-300 py-2 pl-10 pr-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
                  />
                </label>
                <button
                  type="submit"
                  className="min-h-11 rounded-lg bg-blue-800 px-4 text-sm font-bold text-white hover:bg-blue-900"
                >
                  Tìm
                </button>
              </form>
            </div>
            {listingPage.items.length === 0 ? (
              <div className="grid min-h-72 place-items-center text-center">
                <div>
                  <Building2 className="mx-auto h-10 w-10 text-slate-400" />
                  <h2 className="mt-4 text-lg font-bold text-slate-950">
                    Chưa có bài đăng nào có yêu cầu liên hệ
                  </h2>
                  <p className="mt-2 text-sm text-slate-600">
                    Khi có người gửi yêu cầu, bài đăng sẽ xuất hiện tại đây.
                  </p>
                </div>
              </div>
            ) : (
              <>
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {listingPage.items.map((listing) => (
                    <button
                      key={listing.listingId}
                      type="button"
                      onClick={() => void openListing(listing)}
                      className="group overflow-hidden rounded-xl border border-slate-200 bg-white text-left transition hover:border-blue-500 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600"
                    >
                      <div className="relative">
                        {listing.imageUrl ? (
                          <img
                            src={listing.imageUrl}
                            alt=""
                            className="h-40 w-full object-cover"
                          />
                        ) : (
                          <span className="grid h-40 place-items-center bg-slate-100">
                            <Building2 className="h-8 w-8 text-slate-400" />
                          </span>
                        )}
                        <span className="absolute right-3 top-3 rounded-md bg-white px-2.5 py-1 text-xs font-bold text-slate-950 shadow-sm">
                          {listing.totalLeads} yêu cầu
                        </span>
                      </div>
                      <span className="block p-4">
                        <span className="line-clamp-2 text-base font-bold text-slate-950 group-hover:text-blue-800">
                          {listing.title}
                        </span>
                        {listing.address && (
                          <span className="mt-2 flex line-clamp-1 items-center gap-1 text-sm text-slate-600">
                            <MapPin className="h-4 w-4 shrink-0" />
                            {listing.address}
                          </span>
                        )}
                        <span className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-200 pt-3 text-center text-xs">
                          <span>
                            <strong className="block text-lg text-amber-700">
                              {listing.newLeads}
                            </strong>
                            Mới
                          </span>
                          <span>
                            <strong className="block text-lg text-blue-700">
                              {listing.activeLeads}
                            </strong>
                            Đang xử lý
                          </span>
                          <span>
                            <strong className="block text-lg text-emerald-700">
                              {listing.closedLeads}
                            </strong>
                            Hoàn tất
                          </span>
                        </span>
                        <span className="mt-3 flex items-center gap-1 text-xs text-slate-500">
                          <CalendarDays className="h-3.5 w-3.5" />
                          Yêu cầu gần nhất: {formatDate(listing.lastLeadAt)}
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
                <Pagination
                  page={listingPage.page}
                  totalPages={listingPage.totalPages}
                  onPage={(page) => void loadListings(page)}
                />
              </>
            )}
          </section>
        ) : (
          <section className="mt-7" aria-labelledby="customer-leads-title">
            <button
              type="button"
              onClick={() => setSelectedListing(null)}
              className="inline-flex min-h-10 items-center gap-2 text-sm font-bold text-blue-800 hover:underline"
            >
              <ArrowLeft className="h-4 w-4" />
              Quay lại các bài đăng
            </button>
            <div className="mt-3 flex flex-col gap-3 border-b border-slate-200 pb-5 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h2
                  id="customer-leads-title"
                  className="text-xl font-bold text-slate-950"
                >
                  {selectedListing.title}
                </h2>
                <p className="mt-1 text-sm text-slate-600">
                  {selectedListing.address || "Chưa có địa điểm hiển thị"} ·{" "}
                  {leadPage.totalElements} yêu cầu phù hợp
                </p>
              </div>
              <Link
                to={`/listings/${selectedListing.slug || selectedListing.listingId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-800 hover:bg-slate-50"
              >
                <ExternalLink className="h-4 w-4" />
                Xem tin
              </Link>
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                const query = leadInput.trim();
                setLeadQuery(query);
                void loadLeads(selectedListing, 0, query, leadStatus);
              }}
              className="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_200px_auto]"
            >
              <label className="relative">
                <span className="sr-only">Tìm người liên hệ</span>
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  value={leadInput}
                  onChange={(event) => setLeadInput(event.target.value)}
                  placeholder="Tìm tên hoặc nội dung yêu cầu"
                  className="min-h-11 w-full rounded-lg border border-slate-300 py-2 pl-10 pr-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
                />
              </label>
              <select
                value={leadStatus}
                onChange={(event) => {
                  const status = event.target.value;
                  setLeadStatus(status);
                  void loadLeads(selectedListing, 0, leadQuery, status);
                }}
                className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-sm"
              >
                <option value="">Tất cả trạng thái</option>
                {Object.entries(STATUS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                className="min-h-11 rounded-lg bg-blue-800 px-4 text-sm font-bold text-white hover:bg-blue-900"
              >
                Tìm
              </button>
            </form>
            <div className="mt-5 grid gap-3">
              {leadPage.items.length === 0 ? (
                <div className="grid min-h-52 place-items-center rounded-xl border border-dashed border-slate-300 text-center">
                  <p className="text-sm text-slate-600">
                    Không có yêu cầu liên hệ phù hợp.
                  </p>
                </div>
              ) : (
                leadPage.items.map((lead) => (
                  <article
                    key={lead.id}
                    className="rounded-xl border border-slate-200 bg-white p-5"
                  >
                    <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="text-lg font-bold text-slate-950">
                            {lead.fullName}
                          </h3>
                          <span
                            className={`rounded-md px-2.5 py-1 text-xs font-bold ${STATUS_STYLES[lead.status]}`}
                          >
                            {STATUS_LABELS[lead.status]}
                          </span>
                          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs font-bold text-blue-800">
                            {lead.requestType === "VIEWING"
                              ? "Muốn hẹn xem"
                              : "Cần tư vấn"}
                          </span>
                        </div>
                        <p className="mt-2 flex items-center gap-2 text-sm text-slate-600">
                          <CalendarDays className="h-4 w-4" />
                          Gửi lúc {formatDate(lead.createdAt)}
                        </p>
                        {lead.note && (
                          <div className="mt-4 rounded-lg bg-slate-50 px-4 py-3">
                            <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                              Nội dung để lại
                            </p>
                            <p className="mt-1.5 break-words text-sm leading-6 text-slate-800">
                              {lead.note}
                            </p>
                          </div>
                        )}
                      </div>
                      <div className="grid gap-2 sm:grid-cols-2 lg:w-52 lg:grid-cols-1">
                        {phones[lead.id] ? (
                          <a
                            href={`tel:${phones[lead.id]}`}
                            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-blue-800 px-4 text-sm font-bold text-blue-900"
                          >
                            <Phone className="h-4 w-4" />
                            {phones[lead.id]}
                          </a>
                        ) : (
                          <button
                            type="button"
                            disabled={busyId === lead.id || !lead.consentPolicy}
                            onClick={() => void revealPhone(lead)}
                            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                          >
                            <Phone className="h-4 w-4" />
                            {lead.consentPolicy
                              ? "Xem số liên hệ"
                              : "Chưa đồng ý liên hệ"}
                          </button>
                        )}
                        <label className="text-sm font-semibold text-slate-700">
                          Tiến độ chăm sóc
                          <select
                            value={lead.status}
                            disabled={busyId === lead.id}
                            onChange={(event) =>
                              void changeStatus(
                                lead,
                                event.target.value as LeadStatus,
                              )
                            }
                            className="mt-1.5 min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-900"
                          >
                            <option value={lead.status}>
                              {STATUS_LABELS[lead.status]}
                            </option>
                            {(
                              Object.entries(STATUS_LABELS) as [
                                LeadStatus,
                                string,
                              ][]
                            )
                              .filter(([value]) => value !== lead.status)
                              .map(([value, label]) => (
                                <option key={value} value={value}>
                                  {label}
                                </option>
                              ))}
                          </select>
                        </label>
                      </div>
                    </div>
                  </article>
                ))
              )}
            </div>
            <Pagination
              page={leadPage.page}
              totalPages={leadPage.totalPages}
              onPage={(page) => void loadLeads(selectedListing, page)}
            />
          </section>
        )}
      </div>
    </main>
  );
}

export default MyLeadsPage;
