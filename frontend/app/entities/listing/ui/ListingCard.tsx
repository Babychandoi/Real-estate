import React from 'react';
import { Link } from 'react-router-dom';
import { BedDouble, MapPin, Maximize2, TrendingDown } from 'lucide-react';
import { CompareToggleButton } from '@/features/compare/CompareControls';
import { compareItemFromSummary } from '@/features/compare/compareStore';
import { Avatar } from '@/shared/ui/Avatar';
import { Money, UnitPriceText } from '@/shared/ui/Money';
import { ResponsiveImage } from '@/shared/ui/ResponsiveImage';
import { TrustBadge } from '@/shared/ui/TrustBadge';
import { cn } from '@/shared/ui/cn';
import { listingPath } from '../model/seo';
import {
  formatArea,
  formatDate,
  propertyTypeLabel,
  purposeLabel,
  sellerRoleLabel,
  type ListingSummaryV2,
} from '../model/v2';

interface ListingCardProps {
  listing: ListingSummaryV2;
  /** Listing that is no longer public (compare, saved lists): greyed, no detail link. */
  inactive?: boolean;
  /** Highlighted from the map (split view). */
  highlighted?: boolean;
  onHoverChange?: (id: string | null) => void;
  /** Extra actions next to "So sánh" (e.g. favourite, stream S6); rendered as siblings of the title link. */
  actions?: React.ReactNode;
  /** First cards of a page: load the image eagerly. */
  priority?: boolean;
  headingLevel?: 'h2' | 'h3';
  /** Router state for the detail link (the search page passes where to go "back" to). */
  linkState?: unknown;
}

/**
 * Listing card v2 (DS-09): the title link is stretched over the card with a pseudo-element, and the compare button,
 * extra actions and the seller link are siblings above it — no interactive element is nested in another. Price keeps
 * its period ("/tháng" for rent), trust badges say what was checked (DS-10), freshness shows the last update.
 */
export function ListingCard({
  listing,
  inactive = false,
  highlighted = false,
  onHoverChange,
  actions,
  priority = false,
  headingLevel = 'h3',
  linkState,
}: ListingCardProps) {
  const Heading = headingLevel;
  const location = listing.location.addressSummary || listing.location.districtName || '';
  const updated = formatDate(listing.freshness.updatedAt);
  return (
    <article
      data-listing-id={listing.id}
      onMouseEnter={onHoverChange ? () => onHoverChange(listing.id) : undefined}
      onMouseLeave={onHoverChange ? () => onHoverChange(null) : undefined}
      onFocus={onHoverChange ? () => onHoverChange(listing.id) : undefined}
      className={cn(
        'group relative flex h-full flex-col overflow-hidden rounded-card border bg-surface-container-lowest shadow-sm transition-shadow duration-fast',
        highlighted ? 'border-primary ring-2 ring-primary/40' : 'border-outline-variant hover:shadow-md',
        inactive && 'opacity-70',
      )}
    >
      <div className="relative">
        <ResponsiveImage
          image={listing.image}
          alt={`Ảnh đại diện: ${listing.title}`}
          aspectRatio="16 / 10"
          sizes="(min-width: 1280px) 25vw, (min-width: 640px) 50vw, 100vw"
          priority={priority}
        />
        <span className="absolute left-3 top-3 rounded-pill bg-surface-container-lowest/95 px-2.5 py-1 text-label font-semibold text-primary shadow-sm">
          {purposeLabel(listing.purpose)} · {propertyTypeLabel(listing.propertyType)}
        </span>
        {listing.imageCount > 1 && (
          <span className="absolute bottom-2 right-2 rounded-pill bg-inverse-surface/75 px-2 py-0.5 text-label font-semibold text-inverse-on-surface">
            {listing.imageCount} ảnh
          </span>
        )}
      </div>
      <div className="absolute right-3 top-3 z-10 flex items-center gap-2">
        {!inactive && <CompareToggleButton listing={compareItemFromSummary(listing)} />}
        {actions}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-4">
        <p className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <Money price={listing.price} className="text-lg font-bold text-primary" />
          <UnitPriceText unitPrice={listing.unitPrice} className="text-label font-medium text-on-surface-variant" />
          {listing.priceChange?.direction === 'DOWN' && (
            <span className="inline-flex items-center gap-1 text-label font-semibold text-success">
              <TrendingDown className="h-4 w-4" aria-hidden="true" />
              Đã giảm giá
            </span>
          )}
        </p>
        <Heading className="text-body font-semibold leading-snug text-on-surface">
          {inactive ? (
            <span className="line-clamp-2">{listing.title}</span>
          ) : (
            <Link
              to={listingPath(listing)}
              state={linkState}
              aria-label={`Xem chi tiết: ${listing.title}`}
              className="line-clamp-2 after:absolute after:inset-0 after:content-[''] hover:text-primary focus-visible:outline-none focus-visible:after:rounded-card focus-visible:after:ring-2 focus-visible:after:ring-primary"
            >
              {listing.title}
            </Link>
          )}
        </Heading>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-on-surface-variant">
          <span className="inline-flex items-center gap-1">
            <Maximize2 className="h-4 w-4 text-outline" aria-hidden="true" />
            <span className="font-semibold text-on-surface">{formatArea(listing.areaM2)}</span>
          </span>
          {listing.bedrooms != null && listing.bedrooms > 0 && (
            <span className="inline-flex items-center gap-1">
              <BedDouble className="h-4 w-4 text-outline" aria-hidden="true" />
              {listing.bedrooms} phòng ngủ
            </span>
          )}
        </p>
        {location && (
          <p className="flex min-w-0 items-center gap-1 text-body-sm text-on-surface-variant">
            <MapPin className="h-4 w-4 shrink-0 text-outline" aria-hidden="true" />
            <span className="truncate">{location}</span>
          </p>
        )}
        {(listing.trust.identity.status === 'VERIFIED' || listing.trust.ownership.status === 'VERIFIED') && (
          <div className="flex flex-wrap gap-1.5">
            {listing.trust.ownership.status === 'VERIFIED' && <TrustBadge kind="ownership" status="VERIFIED" />}
            {listing.trust.identity.status === 'VERIFIED' && <TrustBadge kind="identity" status="VERIFIED" />}
          </div>
        )}
        <div className="mt-auto flex flex-wrap items-center justify-between gap-x-2 border-t border-outline-variant/60 pt-2 text-label text-on-surface-variant">
          <Link
            to={`/nguoi-dang/${listing.seller.id}`}
            aria-label={`Xem trang người đăng ${listing.seller.name ?? ''}`.trim()}
            className="relative z-10 -mx-1 flex min-h-11 min-w-0 items-center gap-2 rounded-pill px-1 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <Avatar name={listing.seller.name} src={listing.seller.avatarUrl} size="xs" />
            <span className="min-w-0 truncate">
              <span className="font-semibold text-on-surface">{listing.seller.name || 'Người đăng'}</span>
              <span> · {sellerRoleLabel(listing.seller.role)}</span>
            </span>
          </Link>
          {inactive ? (
            <span className="shrink-0 font-semibold">Tin không còn hiển thị</span>
          ) : (
            updated && <span className="shrink-0">Cập nhật {updated}</span>
          )}
        </div>
      </div>
    </article>
  );
}
