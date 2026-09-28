import { useEffect } from 'react';
import { Heart } from 'lucide-react';
import { useAuth } from '@/shared/auth/AuthContext';
import { useToast } from '@/shared/ui/Toast';
import { cn } from '@/shared/ui/cn';
import { savedListingsStore, useSavedListings } from './savedListingsStore';

interface FavoriteButtonProps {
  listingId: string;
  title: string;
  /** `card`: round icon button over a listing card; `inline`: labelled button (detail page). */
  appearance?: 'card' | 'inline';
  className?: string;
}

function problemDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'problem' in error) {
    return (error as { problem?: { detail?: string } }).problem?.detail;
  }
  return undefined;
}

/**
 * "Lưu tin" (audit P-02): a toggle (`aria-pressed`) that saves the listing to the account. Signed out, it opens the
 * sign-in dialog instead. It is a sibling of the card's stretched title link, never nested in it (DS-09).
 */
export function FavoriteButton({ listingId, title, appearance = 'card', className }: FavoriteButtonProps) {
  const { user, setIsLoginModalOpen } = useAuth();
  const saved = useSavedListings();
  const toast = useToast();
  useEffect(() => savedListingsStore.ensure(user?.id ?? null), [user?.id]);

  const isSaved = saved.ids.has(listingId);
  const busy = saved.pending.has(listingId);
  const label = isSaved ? `Bỏ lưu tin: ${title}` : `Lưu tin: ${title}`;

  const onClick = async () => {
    if (!user) {
      setIsLoginModalOpen(true);
      return;
    }
    try {
      const now = await savedListingsStore.toggle(listingId);
      toast.show({
        kind: 'success',
        title: now ? 'Đã lưu tin' : 'Đã bỏ lưu tin',
        description: now ? 'Xem lại trong mục Tin đã lưu. Bạn sẽ được báo khi tin giảm giá.' : undefined,
        duration: 3000,
      });
    } catch (error) {
      toast.show({
        kind: 'error',
        title: 'Chưa lưu được tin',
        description: problemDetail(error) ?? 'Vui lòng thử lại.',
      });
    }
  };

  if (appearance === 'inline') {
    return (
      <button
        type="button"
        aria-pressed={isSaved}
        aria-label={label}
        disabled={busy}
        onClick={onClick}
        className={cn(
          'inline-flex min-h-control-md items-center gap-2 rounded-lg border px-4 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60',
          isSaved
            ? 'border-error/40 bg-error-container text-error-on-container'
            : 'border-outline text-on-surface hover:bg-surface-container',
          className,
        )}
      >
        <Heart className={cn('h-5 w-5', isSaved && 'fill-current')} aria-hidden="true" />
        {isSaved ? 'Đã lưu' : 'Lưu tin'}
      </button>
    );
  }
  return (
    <button
      type="button"
      aria-pressed={isSaved}
      aria-label={label}
      title={isSaved ? 'Bỏ lưu tin' : 'Lưu tin'}
      disabled={busy}
      onClick={onClick}
      className={cn(
        'relative z-10 inline-grid h-control-md w-control-md place-items-center rounded-full border bg-surface-container-lowest/95 shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60',
        isSaved ? 'border-error/40 text-error' : 'border-outline-variant text-on-surface-variant hover:text-error',
        className,
      )}
    >
      <Heart className={cn('h-5 w-5', isSaved && 'fill-current')} aria-hidden="true" />
    </button>
  );
}
