import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { MailX } from 'lucide-react';
import { CATEGORY_LABELS, engagementApi, type UnsubscribeTarget } from '@/features/engagement/api';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Skeleton } from '@/shared/ui/Skeleton';

/**
 * `/unsubscribe?token=…` from an alert e-mail: shows what the link stops, then applies it on confirmation (no sign-in).
 * Mail clients that support RFC 8058 unsubscribe with one click without opening this page.
 */
export function UnsubscribePage() {
  useDocumentMeta({ title: 'Ngừng nhận email | Nhà Đất Chuẩn', robots: 'noindex, nofollow' });
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [target, setTarget] = useState<UnsubscribeTarget | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'invalid' | 'error' | 'done'>('loading');
  const [busy, setBusy] = useState(false);

  const load = () => {
    if (!token) {
      setStatus('invalid');
      return;
    }
    setStatus('loading');
    engagementApi
      .describeUnsubscribe(token)
      .then((value) => {
        setTarget(value);
        setStatus('ready');
      })
      .catch((error: unknown) => {
        const code =
          error && typeof error === 'object' && 'problem' in error
            ? (error as { problem?: { status?: number } }).problem?.status
            : undefined;
        setStatus(code === 404 ? 'invalid' : 'error');
      });
  };
  useEffect(load, [token]);

  const confirm = async () => {
    setBusy(true);
    try {
      setTarget(await engagementApi.unsubscribe(token));
      setStatus('done');
    } catch {
      setStatus('error');
    } finally {
      setBusy(false);
    }
  };

  const what =
    target?.scope === 'SAVED_SEARCH'
      ? `cảnh báo của tìm kiếm “${target.savedSearchName ?? 'đã lưu'}”`
      : `email về “${target?.category ? CATEGORY_LABELS[target.category].title : 'mục này'}”`;

  return (
    <div
      className="mx-auto flex w-full max-w-xl flex-col gap-4 px-4 py-12"
      data-ready={status === 'loading' ? undefined : 'true'}
    >
      {status === 'loading' ? (
        <Skeleton className="h-40 rounded-card" />
      ) : status === 'invalid' ? (
        <EmptyState
          icon={MailX}
          headingLevel={1}
          title="Liên kết không hợp lệ hoặc đã hết hạn"
          description="Bạn vẫn có thể tắt email trong Tùy chọn thông báo của tài khoản."
          actions={<ButtonLink to="/account#thong-bao">Mở tùy chọn thông báo</ButtonLink>}
        />
      ) : status === 'error' ? (
        <ErrorState headingLevel={1} title="Chưa xử lý được yêu cầu" onRetry={load} />
      ) : (
        <section className="flex flex-col gap-4 rounded-card border border-outline-variant p-6">
          <h1 className="text-headline-sm text-on-surface">Ngừng nhận email</h1>
          {status === 'done' || target?.applied ? (
            <InlineFeedback kind="success" title={`Đã ngừng ${what}.`}>
              Thông báo trong ứng dụng không thay đổi. Bạn có thể bật lại bất cứ lúc nào trong tài khoản.
            </InlineFeedback>
          ) : (
            <>
              <p className="text-body text-on-surface">Bạn muốn ngừng nhận {what}?</p>
              <div className="flex flex-wrap gap-2">
                <Button onClick={confirm} isLoading={busy}>
                  Ngừng nhận
                </Button>
                <ButtonLink to="/" variant="ghost">
                  Giữ nguyên
                </ButtonLink>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
