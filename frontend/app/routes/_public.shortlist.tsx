import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Link2Off } from 'lucide-react';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { engagementApi, problemDetail, type PublicShortlist } from '@/features/engagement/api';
import { useAuth } from '@/shared/auth/AuthContext';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Skeleton } from '@/shared/ui/Skeleton';

/**
 * `/shortlists/:token`: a shared shortlist for whoever holds the link. Read-only without sign-in (public listings, the
 * owner's given name); signed in, the visitor can join with the link's role. Never indexed (the token is a secret).
 */
export function SharedShortlistPage() {
  useDocumentMeta({ title: 'Danh sách được chia sẻ | Nhà Đất Chuẩn', robots: 'noindex, nofollow' });
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const { user, setIsLoginModalOpen } = useAuth();
  const [view, setView] = useState<PublicShortlist | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'gone' | 'error'>('loading');
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  const load = () => {
    setStatus('loading');
    engagementApi
      .publicShortlist(token)
      .then((value) => {
        setView(value);
        setStatus('ready');
      })
      .catch((error: unknown) => {
        const code =
          error && typeof error === 'object' && 'problem' in error
            ? (error as { problem?: { status?: number } }).problem?.status
            : undefined;
        setStatus(code === 404 ? 'gone' : 'error');
      });
  };
  useEffect(load, [token]);

  const join = async () => {
    if (!user) {
      setIsLoginModalOpen(true);
      return;
    }
    setJoining(true);
    setJoinError(null);
    try {
      const detail = await engagementApi.join(token);
      navigate(`/saved?tab=shortlists&list=${detail.id}`);
    } catch (error) {
      setJoinError(problemDetail(error, 'Chưa tham gia được danh sách.'));
    } finally {
      setJoining(false);
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-5 px-4 py-8">
      {status === 'loading' ? (
        <Skeleton className="h-64 rounded-card" />
      ) : status === 'gone' ? (
        <EmptyState
          icon={Link2Off}
          title="Liên kết chia sẻ không còn hiệu lực"
          description="Người chia sẻ có thể đã tạo liên kết mới hoặc ngừng chia sẻ. Hãy xin họ liên kết mới."
          actions={<ButtonLink to="/search">Tìm nhà đất</ButtonLink>}
        />
      ) : status === 'error' || !view ? (
        <ErrorState title="Không tải được danh sách" onRetry={load} />
      ) : (
        <>
          <header className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-label text-on-surface-variant">
                Danh sách được chia sẻ{view.ownerGivenName ? ` bởi ${view.ownerGivenName}` : ''}
              </p>
              <h1 className="text-headline-md text-on-surface">{view.name}</h1>
              <p className="text-body-sm text-on-surface-variant">
                {view.items.length} tin đang hiển thị · Liên kết cho quyền{' '}
                {view.role === 'EDITOR' ? 'xem và sửa' : 'chỉ xem'}
              </p>
            </div>
            <Button onClick={join} isLoading={joining}>
              {user ? 'Thêm vào danh sách của tôi' : 'Đăng nhập để tham gia'}
            </Button>
          </header>
          {joinError && <InlineFeedback kind="error" title={joinError} />}
          {view.items.length === 0 ? (
            <EmptyState title="Danh sách chưa có tin đang hiển thị" />
          ) : (
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {view.items.map((listing) => (
                <li key={listing.id} className="min-w-0">
                  <ListingCard listing={listing} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
