import { ListingCard } from '@/entities/listing/ui/ListingCard';
import type { Listing } from '@/entities/listing/model/types';
import { CompareToggleButton } from './CompareControls';
export function ComparableListingCard({ listing }: { listing: Listing }) { return <ListingCard listing={listing} action={<CompareToggleButton listing={listing} />} />; }
