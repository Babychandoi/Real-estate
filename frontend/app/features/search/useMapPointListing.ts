import { useEffect, useState } from 'react';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';

/** A late detail response must never replace the currently selected map point. */
export function useMapPointListing(id: string | null, items: ListingSummaryV2[]) {
  const known = items.find((item) => item.id === id) ?? null;
  const [loaded, setLoaded] = useState<ListingSummaryV2 | null>(null);

  useEffect(() => {
    if (!id || known) return;
    const controller = new AbortController();
    void listingV2Api.detail(id, controller.signal).then(
      (listing) => {
        if (!controller.signal.aborted) setLoaded(listing);
      },
      () => {
        if (!controller.signal.aborted) setLoaded(null);
      },
    );
    return () => controller.abort();
  }, [id, known]);

  return known ?? (loaded?.id === id ? loaded : null);
}
