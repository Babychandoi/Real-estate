import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BellOff, CheckCheck, Settings2, Trash2 } from 'lucide-react';
import { engagementApi, CATEGORY_LABELS, type NotificationItem } from '@/features/engagement/api';
import { notificationStore, useNotificationState } from '@/shared/notifications/notificationStore';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { Badge } from '@/shared/ui/Badge';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Chip } from '@/shared/ui/Chip';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { IconButton } from '@/shared/ui/IconButton';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useToast } from '@/shared/ui/Toast';
import { cn } from '@/shared/ui/cn';

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });

/** Notification centre (contract §11, audit P-02/UI-13): unread filter, mark read / all read, delete, deep links. */
export function NotificationCenterPage() {
  useDocumentMeta({ title: 'Thông báo | Nhà Đất Chuẩn', robots: 'noindex' });
  const navigate = useNavigate();
  const toast = useToast();
  const { live } = useNotificationState();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [next, setNext] = useState<number | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadingMore, setLoadingMore] = useState(false);

  const load = useCallback(async () => {
    setStatus('loading');
    try {
      const feed = await engagementApi.feed(null, unreadOnly);
      setItems(feed.items);
      setNext(feed.nextBefore);
      const newest = feed.items[0]?.seq ?? 0;
      notificationStore.setUnread(feed.unreadCount, newest);
      setStatus('ready');
    } catch {
      setStatus('error');
    }
  }, [unreadOnly]);

  useEffect(() => {
    void load();
  }, [load]);

  // Live frames that arrive while the page is open are prepended (the stream already dropped duplicates).
  useEffect(() => {
    if (status !== 'ready' || live.length === 0) return;
    setItems((current) => {
      const known = new Set(current.map((item) => item.id));
      const fresh = live
        .filter((item) => !known.has(item.id))
        .map<NotificationItem>((item) => ({
          ...item,
          category: item.category as NotificationItem['category'],
          readAt: null,
        }));
      return fresh.length ? [...fresh, ...current] : current;
    });
  }, [live, status]);

  const loadMore = async () => {
    if (next === null) return;
    setLoadingMore(true);
    try {
      const feed = await engagementApi.feed(next, unreadOnly);
      setItems((current) => [...current, ...feed.items.filter((item) => !current.some((c) => c.id === item.id))]);
      setNext(feed.nextBefore);
    } catch {
      toast.show({ kind: 'error', title: 'Không tải thêm được thông báo' });
    } finally {
      setLoadingMore(false);
    }
  };

  const markRead = async (item: NotificationItem) => {
    if (item.readAt) return;
    setItems((current) => current.map((c) => (c.id === item.id ? { ...c, readAt: new Date().toISOString() } : c)));
    try {
      await engagementApi.markRead(item.id);
    } finally {
      void notificationStore.refresh();
    }
  };

  const open = async (item: NotificationItem) => {
    await markRead(item);
    if (item.link) navigate(item.link);
  };

  const markAll = async () => {
    const upTo = Math.max(0, ...items.map((item) => item.seq));
    try {
      const count = await engagementApi.markAllRead(upTo);
      notificationStore.setUnread(count.count, count.latestSeq);
      setItems((current) =>
        current.map((c) => (c.seq <= upTo && !c.readAt ? { ...c, readAt: new Date().toISOString() } : c)),
      );
      if (unreadOnly) void load();
    } catch {
      toast.show({ kind: 'error', title: 'Chưa đánh dấu được. Vui lòng thử lại.' });
    }
  };

  const remove = async (item: NotificationItem) => {
    try {
      await engagementApi.deleteNotification(item.id);
      setItems((current) => current.filter((c) => c.id !== item.id));
      void notificationStore.refresh();
    } catch {
      toast.show({ kind: 'error', title: 'Chưa xóa được thông báo' });
    }
  };

  return (
    <div
      className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-8"
      data-ready={status === 'loading' ? undefined : 'true'}
    >
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-headline-md text-on-surface">Thông báo</h1>
          <p className="text-body-sm text-on-surface-variant">
            Cập nhật về tin đăng, khách quan tâm, tìm kiếm và tin bạn đã lưu.
          </p>
        </div>
        <ButtonLink to="/account#thong-bao" variant="ghost" size="sm" leftIcon={<Settings2 className="h-4 w-4" />}>
          Tùy chọn thông báo
        </ButtonLink>
      </header>
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Lọc thông báo" className="flex gap-2">
          <Chip selected={!unreadOnly} onClick={() => setUnreadOnly(false)}>
            Tất cả
          </Chip>
          <Chip selected={unreadOnly} onClick={() => setUnreadOnly(true)}>
            Chưa đọc
          </Chip>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="ml-auto"
          leftIcon={<CheckCheck className="h-4 w-4" />}
          onClick={markAll}
          disabled={status !== 'ready' || items.every((item) => item.readAt)}
        >
          Đánh dấu đã đọc tất cả
        </Button>
      </div>

      {status === 'loading' ? (
        <div role="status" aria-label="Đang tải thông báo" className="flex flex-col gap-3">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-20 rounded-card" />
          ))}
        </div>
      ) : status === 'error' ? (
        <ErrorState title="Không tải được thông báo" onRetry={() => void load()} />
      ) : items.length === 0 ? (
        <EmptyState
          icon={BellOff}
          title={unreadOnly ? 'Bạn đã đọc hết thông báo' : 'Chưa có thông báo nào'}
          description="Lưu tin hoặc lưu tìm kiếm để nhận cảnh báo khi có tin mới hay tin giảm giá."
          actions={
            <ButtonLink to="/search" variant="outline">
              Tìm nhà đất
            </ButtonLink>
          }
        />
      ) : (
        <>
          <ul className="flex flex-col gap-2" aria-label="Danh sách thông báo">
            {items.map((item) => (
              <li
                key={item.id}
                className={cn(
                  'flex items-start gap-3 rounded-card border p-4',
                  item.readAt ? 'border-outline-variant bg-surface' : 'border-primary/40 bg-primary/5',
                )}
              >
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2">
                    {!item.readAt && <Badge variant="primary">Chưa đọc</Badge>}
                    <span className="text-label text-on-surface-variant">
                      {CATEGORY_LABELS[item.category]?.title ?? 'Thông báo'} ·{' '}
                      <time dateTime={item.createdAt}>{dateFormat.format(new Date(item.createdAt))}</time>
                    </span>
                  </p>
                  <h2 className="mt-1 text-body font-semibold text-on-surface">
                    {item.link ? (
                      <Link
                        to={item.link}
                        onClick={(event) => {
                          event.preventDefault();
                          void open(item);
                        }}
                        className="hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      >
                        {item.title}
                      </Link>
                    ) : (
                      item.title
                    )}
                  </h2>
                  <p className="text-body-sm text-on-surface-variant">{item.message}</p>
                  {!item.readAt && !item.link && (
                    <button
                      type="button"
                      onClick={() => void markRead(item)}
                      className="mt-1 min-h-11 text-sm font-semibold text-primary hover:underline"
                    >
                      Đánh dấu đã đọc
                    </button>
                  )}
                </div>
                <IconButton
                  icon={Trash2}
                  aria-label={`Xóa thông báo: ${item.title}`}
                  variant="ghost"
                  onClick={() => void remove(item)}
                />
              </li>
            ))}
          </ul>
          {next !== null && (
            <Button variant="outline" onClick={loadMore} isLoading={loadingMore} className="self-center">
              Xem thông báo cũ hơn
            </Button>
          )}
        </>
      )}
    </div>
  );
}
