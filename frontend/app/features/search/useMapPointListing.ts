import { useCallback, useEffect, useState } from 'react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';

/** A late detail response must never replace the currently selected map point. */
export function useMapPointListing(id: string | null, items: ListingSummaryV2[]) {
  const known = items.find((item) => item.id === id) ?? null;
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{
    id: string;
    attempt: number;
    listing: ListingSummaryV2 | null;
    error: boolean;
  } | null>(null);
  const retry = useCallback(() => setAttempt((value) => value + 1), []);

  useEffect(() => {
    if (!id || known) return;
    const controller = new AbortController();
    void listingV2Api.detail(id, controller.signal).then(
      (listing) => {
        if (!controller.signal.aborted) setResult({ id, attempt, listing, error: false });
      },
      () => {
        if (!controller.signal.aborted) setResult({ id, attempt, listing: null, error: true });
      },
    );
    return () => controller.abort();
  }, [id, known, attempt]);

  const current = result?.id === id && result.attempt === attempt ? result : null;
  return { listing: known ?? current?.listing ?? null, error: !known && !!current?.error, retry };
}
