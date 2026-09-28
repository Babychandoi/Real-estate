import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';

/** The v2 ListingCard already renders the compare toggle; kept as the UI design's named entry point. */
export function ComparableListingCard({ listing }: { listing: ListingSummaryV2 }) {
  return <ListingCard listing={listing} />;
}
