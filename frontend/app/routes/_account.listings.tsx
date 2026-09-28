import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Building2, Plus } from "lucide-react";
import { Button } from "@/shared/ui/Button";
import { StatePanel, ListingSkeleton } from "@/shared/ui/Feedback";
import { apiClient } from "@/shared/api/client";
import {
  formatListingPrice,
  formatListingStatus,
  type ListingDetail,
} from "@/entities/listing/model/types";
import { listingPath } from "@/entities/listing/model/seo";
const tabs = [
  ["ALL", "Tất cả"],
  ["ACTIVE", "Đang hiển thị"],
  ["PENDING_REVIEW", "Chờ duyệt"],
  ["DRAFT", "Bản nháp"],
  ["PAUSED", "Tạm ẩn"],
  ["REJECTED", "Bị từ chối"],
] as const;
export function MyListingsPage() {
  const [filter, setFilter] = useState("ALL"),
    [page, setPage] = useState(1);
  const [items, setItems] = useState<ListingDetail[]>([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(""),
    [notice, setNotice] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setItems(await apiClient<ListingDetail[]>("/listings/my-listings"));
    } catch {
      setError("Không tải được kho tin. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);
  const act = async (
    item: ListingDetail,
    action: "submit" | "hide" | "show",
  ) => {
    setBusy(item.id);
    setError("");
    setNotice("");
    try {
      await apiClient(
        `/listings/${item.id}/${action === "submit" ? "submit" : "visibility"}`,
        {
          method: "POST",
          ...(action === "submit"
            ? {}
            : { body: JSON.stringify({ hidden: action === "hide" }) }),
        },
      );
      setNotice(
        action === "submit"
          ? "Tin đã được gửi duyệt."
          : action === "hide"
            ? "Đã tạm ẩn tin."
            : "Tin đã được hiển thị lại.",
      );
      await load();
    } catch {
      setError("Thao tác chưa được lưu. Vui lòng thử lại.");
    } finally {
      setBusy("");
    }
  };
  const filtered = items.filter(
    (item) => filter === "ALL" || item.status === filter,
  );
  const pages = Math.max(1, Math.ceil(filtered.length / 9));
  const current = Math.min(page, pages);
  return (
    <div className="ndc-page py-8">
      <div className="ndc-section-heading">
        <div>
          <h1 className="text-3xl font-semibold">Quản lý kho tin đăng</h1>
          <p>Theo dõi kiểm duyệt, cập nhật nội dung và quản lý hiển thị.</p>
        </div>
        <Link to="/listings/new" className="ndc-primary-link">
          <Plus className="h-4 w-4" aria-hidden="true" />
          Đăng tin mới
        </Link>
      </div>
      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {[
          ["Tổng tin", items.length],
          [
            "Đang hiển thị",
            items.filter((item) => item.status === "ACTIVE").length,
          ],
          [
            "Chờ duyệt",
            items.filter((item) => item.status === "PENDING_REVIEW").length,
          ],
          ["Bản nháp", items.filter((item) => item.status === "DRAFT").length],
        ].map(([name, count]) => (
          <div key={String(name)} className="rounded-xl border bg-white p-5">
            <p className="text-sm text-on-surface-variant">{name}</p>
            <strong className="mt-2 block text-3xl text-primary">
              {loading || (error && !items.length) ? "—" : count}
            </strong>
          </div>
        ))}
      </div>
      <div
        className="mb-6 flex gap-2 overflow-x-auto"
        aria-label="Trạng thái tin"
      >
        {tabs.map(([value, label]) => (
          <button
            type="button"
            key={value}
            className={`ndc-nav-link shrink-0 border ${filter === value ? "is-active" : "bg-white"}`}
            aria-pressed={filter === value}
            onClick={() => {
              setFilter(value);
              setPage(1);
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {notice && (
        <p
          role="status"
          className="mb-4 rounded-lg bg-emerald-50 p-4 text-emerald-900"
        >
          {notice}
        </p>
      )}
      {error && (
        <p
          role="alert"
          className="mb-4 rounded-lg bg-rose-50 p-4 text-rose-900"
        >
          {error}{" "}
          <button
            className="underline"
            type="button"
            onClick={() => void load()}
          >
            Tải lại
          </button>
        </p>
      )}
      {loading ? (
        <ListingSkeleton />
      ) : !filtered.length && !error ? (
        <StatePanel
          title="Chưa có tin trong mục này"
          description="Tạo tin đăng để giới thiệu bất động sản tới người tìm nhà."
          action={
            <Link to="/listings/new" className="ndc-primary-link">
              Tạo tin mới
            </Link>
          }
        />
      ) : (
        <div className="ndc-listing-grid">
          {filtered.slice((current - 1) * 9, current * 9).map((item) => (
            <article className="ndc-listing-card" key={item.id}>
              <div className="aspect-video bg-surface-container">
                {item.imageUrls[0] ? (
                  <img
                    src={item.imageUrls[0]}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="grid h-full place-items-center">
                    <Building2
                      className="h-10 w-10 text-outline"
                      aria-hidden="true"
                    />
                  </span>
                )}
              </div>
              <div className="flex flex-1 flex-col p-5">
                <p className="text-xs font-semibold text-secondary">
                  {formatListingStatus(item.status)}
                </p>
                <h2 className="mt-2 line-clamp-2 font-semibold">
                  {item.title || "Tin chưa đặt tiêu đề"}
                </h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                  {item.addressSummary}
                </p>
                <p className="my-4 font-semibold text-primary">
                  {formatListingPrice(item.priceVnd, item.purpose)} ·{" "}
                  {item.areaM2} m²
                </p>
                <div className="mt-auto flex flex-wrap gap-2 border-t pt-3">
                  <Link className="ndc-nav-link border" to={listingPath(item)}>
                    Xem
                  </Link>
                  {item.status !== "PENDING_REVIEW" && (
                    <Link
                      className="ndc-nav-link border"
                      to={`/listings/new?edit=${item.id}`}
                    >
                      Chỉnh sửa
                    </Link>
                  )}
                  {item.status === "DRAFT" && (
                    <Button
                      disabled={!!busy}
                      isLoading={busy === item.id}
                      onClick={() => void act(item, "submit")}
                    >
                      Nộp duyệt
                    </Button>
                  )}
                  {item.status === "ACTIVE" && (
                    <Button
                      variant="outline"
                      disabled={!!busy}
                      onClick={() => void act(item, "hide")}
                    >
                      Ẩn tin
                    </Button>
                  )}
                  {item.status === "PAUSED" && (
                    <Button
                      disabled={!!busy}
                      onClick={() => void act(item, "show")}
                    >
                      Hiện lại
                    </Button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      {!loading && pages > 1 && (
        <nav className="ndc-pagination" aria-label="Phân trang kho tin">
          <button
            className="ndc-nav-link border"
            disabled={current === 1}
            onClick={() => setPage(current - 1)}
          >
            Trang trước
          </button>
          <span>
            Trang {current}/{pages}
          </span>
          <button
            className="ndc-nav-link border"
            disabled={current === pages}
            onClick={() => setPage(current + 1)}
          >
            Trang tiếp
          </button>
        </nav>
      )}
    </div>
  );
}
