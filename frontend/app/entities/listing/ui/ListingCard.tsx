import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Building2, MapPin, ShieldCheck } from "lucide-react";
import {
  formatListingPrice,
  formatPropertyType,
  type Listing,
} from "../model/types";
import { listingPath } from "../model/seo";
import { Avatar } from "@/shared/ui/Avatar";
export function ListingCard({
  listing,
  action,
}: {
  listing: Listing;
  action?: ReactNode;
}) {
  const [failed, setFailed] = useState("");
  return (
    <article className="ndc-listing-card">
      <div className="relative">
        <Link
          to={listingPath(listing)}
          tabIndex={-1}
          aria-hidden="true"
          className="block aspect-[4/3] overflow-hidden bg-surface-container"
        >
          {listing.primaryImageUrl && failed !== listing.primaryImageUrl ? (
            <img
              src={listing.primaryImageUrl}
              alt=""
              loading="lazy"
              decoding="async"
              onError={() => setFailed(listing.primaryImageUrl)}
              className="h-full w-full object-cover"
            />
          ) : (
            <span className="grid h-full place-items-center text-on-surface-variant">
              <Building2 className="h-12 w-12" />
            </span>
          )}
        </Link>
        <span className="absolute left-3 top-3 rounded-md bg-white px-2.5 py-1 text-xs font-semibold text-primary">
          {listing.purpose === "RENT" ? "Cho thuê" : "Cần bán"}
        </span>
        <div className="absolute right-3 top-3">{action}</div>
      </div>
      <div className="flex flex-1 flex-col p-5">
        <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-on-surface-variant">
          <span>{formatPropertyType(listing.propertyType)}</span>
          {listing.isVerified && (
            <span className="inline-flex items-center gap-1 text-emerald-800">
              <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
              Tin đã xác thực
            </span>
          )}
        </div>
        <h3 className="line-clamp-2 text-base font-semibold leading-6">
          <Link
            to={listingPath(listing)}
            aria-label={`Xem chi tiết: ${listing.title}`}
            className="hover:text-primary"
          >
            {listing.title}
          </Link>
        </h3>
        <p className="mt-2 flex items-center gap-1.5 text-sm text-on-surface-variant">
          <MapPin className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span className="truncate">{listing.addressSummary}</span>
        </p>
        <div className="mt-4 flex flex-wrap items-baseline justify-between gap-2">
          <strong className="text-xl font-bold tracking-tight text-primary">
            {formatListingPrice(listing.priceVnd, listing.purpose)}
          </strong>
          <span className="text-sm">
            {listing.areaM2} m²
            {listing.bedrooms ? ` · ${listing.bedrooms} PN` : ""}
          </span>
        </div>
        {listing.sellerId && listing.sellerName && (
          <Link
            to={`/nguoi-dang/${listing.sellerId}`}
            className="mt-4 flex min-h-11 items-center gap-2 border-t border-outline-variant/40 pt-3 text-xs font-medium"
          >
            <Avatar
              name={listing.sellerName}
              src={listing.sellerAvatarUrl}
              size="sm"
            />
            <span className="truncate">{listing.sellerName}</span>
          </Link>
        )}
      </div>
    </article>
  );
}
