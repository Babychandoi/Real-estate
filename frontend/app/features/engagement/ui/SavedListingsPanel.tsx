import { useCallback, useEffect, useState } from 'react';
import { HeartOff } from 'lucide-react';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useToast } from '@/shared/ui/Toast';
import { engagementApi, type SavedListingItem, type ShortlistSummary } from '../api';
import { savedListingsStore } from '../savedListingsStore';
import { FavoriteButton } from '../FavoriteButton';

/** "Tin đã lưu": newest first, listings that are no longer public shown as such, add to a shortlist, remove. */
export function SavedListingsPanel({ shortlists }: { shortlists: ShortlistSummary[] }) {
  const toast = useToast();
  const [items, setItems] = useState<SavedListingItem[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadingMore, setLoadingMore] = useState(false);

  const load = useCallback(async () => {
    setStatus('loading');
    try {
      const page = await engagementApi.savedPage();
      setItems(page.items);
      setCursor(page.nextCursor);
      setTotal(page.total);
      setStatus('ready');
    } catch {
      setStatus('error');
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  const more = async () => {
    setLoadingMore(true);
    try {
      const page = await engagementApi.savedPage(cursor);
      setItems((current) => [...current, ...page.items]);
      setCursor(page.nextCursor);
    } catch {
      toast.show({ kind: 'error', title: 'Không tải thêm được tin đã lưu' });
    } finally {
      setLoadingMore(false);
    }
  };

  const remove = async (item: SavedListingItem) => {
    try {
      await engagementApi.unsave(item.listingId);
      savedListingsStore.forget(item.listingId);
      setItems((current) => current.filter((c) => c.listingId !== item.listingId));
      setTotal((value) => Math.max(0, value - 1));
    } catch {
      toast.show({ kind: 'error', title: 'Chưa bỏ lưu được tin' });
    }
  };

  const addTo = async (shortlistId: string, item: SavedListingItem) => {
    try {
      await engagementApi.addToShortlist(shortlistId, item.listingId);
      toast.show({ kind: 'success', title: 'Đã thêm vào danh sách' });
    } catch (error) {
      const detail =
        error && typeof error === 'object' && 'problem' in error
          ? (error as { problem?: { detail?: string } }).problem?.detail
          : undefined;
      toast.show({ kind: 'error', title: 'Chưa thêm được', description: detail });
    }
  };

  if (status === 'loading') {
    return (
      <div role="status" aria-label="Đang tải tin đã lưu" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-80 rounded-card" />
        ))}
      </div>
    );
  }
  if (status === 'error') return <ErrorState title="Không tải được tin đã lưu" onRetry={() => void load()} />;
  if (items.length === 0) {
    return (
      <EmptyState
        icon={HeartOff}
        title="Bạn chưa lưu tin nào"
        description="Nhấn biểu tượng trái tim trên tin đăng để lưu và nhận thông báo khi tin giảm giá hoặc hiển thị lại."
        actions={<ButtonLink to="/search">Tìm nhà đất</ButtonLink>}
      />
    );
  }
  const editable = shortlists.filter((list) => list.role !== 'VIEWER');
  return (
    <div className="flex flex-col gap-4">
      <p className="text-body-sm text-on-surface-variant" aria-live="polite">
        {total} tin đã lưu
      </p>
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {items.map((item) => (
          <li key={item.listingId} className="flex min-w-0 flex-col gap-2">
            {item.listing ? (
              <ListingCard
                listing={item.listing}
                actions={<FavoriteButton listingId={item.listingId} title={item.listing.title} />}
              />
            ) : (
              <div className="flex h-full flex-col justify-between gap-3 rounded-card border border-dashed border-outline-variant p-4">
                <div>
                  <p className="text-body font-semibold text-on-surface">{item.unavailable?.title ?? 'Tin đăng'}</p>
                  <p className="text-body-sm text-on-surface-variant">
                    Tin không còn hiển thị. Bạn sẽ được báo nếu tin hiển thị trở lại.
                  </p>
                </div>
                <Button variant="ghost" size="sm" onClick={() => void remove(item)}>
                  Bỏ lưu
                </Button>
              </div>
            )}
            {item.listing && editable.length > 0 && (
              <label className="flex items-center gap-2 text-sm text-on-surface-variant">
                <span className="shrink-0">Thêm vào</span>
                <select
                  className="min-h-11 min-w-0 flex-1 rounded-lg border border-outline-variant bg-surface px-2 text-sm"
                  defaultValue=""
                  onChange={(event) => {
                    const id = event.target.value;
                    event.target.value = '';
                    if (id) void addTo(id, item);
                  }}
                  aria-label={`Thêm “${item.listing.title}” vào danh sách chia sẻ`}
                >
                  <option value="">Chọn danh sách…</option>
                  {editable.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </li>
        ))}
      </ul>
      {cursor && (
        <Button variant="outline" className="self-center" isLoading={loadingMore} onClick={more}>
          Xem thêm tin đã lưu
        </Button>
      )}
    </div>
  );
}
