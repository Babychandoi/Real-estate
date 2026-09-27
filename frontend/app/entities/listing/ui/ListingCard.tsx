import React from 'react';
import { type Listing, formatPriceVnd, calculateUnitPrice } from '../model/types';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { Building2, MapPin, ShieldCheck, Maximize2 } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { listingPath } from '../model/seo';
import { CompareToggleButton } from '@/features/compare/CompareControls';
import { Avatar } from '@/shared/ui/Avatar';

interface ListingCardProps {
  listing: Listing;
}

export const ListingCard: React.FC<ListingCardProps> = ({ listing }) => {
  const navigate = useNavigate();
  return (
    <div className="relative h-full">
      <Link
        to={listingPath(listing)}
        aria-label={`Xem chi tiết: ${listing.title}`}
        className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
      >
        <Card hoverable className="p-0 overflow-hidden flex flex-col h-full group">
          {/* Khung ảnh đại diện với tỉ lệ 16:9 */}
          <div className="relative aspect-[16/10] w-full overflow-hidden bg-surface-container">
            {listing.primaryImageUrl ? (
              <img
                src={listing.primaryImageUrl}
                alt={listing.title}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                loading="lazy"
                decoding="async"
              />
            ) : (
              <div
                className="grid h-full place-items-center bg-surface-container-high text-on-surface-variant"
                role="img"
                aria-label="Tin đăng chưa có ảnh"
              >
                <Building2 className="h-12 w-12" aria-hidden="true" />
              </div>
            )}
            <div className="absolute top-3 left-3 flex gap-2">
              {listing.isVerified && (
                <Badge variant="verified" icon={<ShieldCheck className="w-3.5 h-3.5 text-secondary" />}>
                  Đã xác thực
                </Badge>
              )}
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-container-lowest/90 text-primary backdrop-blur-sm shadow-sm">
                {listing.purpose === 'SALE' ? 'Bán' : 'Cho thuê'}
              </span>
            </div>
          </div>

          {/* Thông tin nội dung */}
          <div className="p-4 flex flex-col flex-1 justify-between gap-3">
            <div>
              <div className="flex items-baseline justify-between gap-2">
                <span className="text-xl font-bold text-primary tracking-tight">
                  {formatPriceVnd(listing.priceVnd)}
                </span>
                <span className="text-xs font-medium text-on-surface-variant">
                  {calculateUnitPrice(listing.priceVnd, listing.areaM2)}
                </span>
              </div>

              <h3 className="text-base font-semibold text-on-surface mt-1 line-clamp-2 group-hover:text-primary transition-colors">
                {listing.title}
              </h3>
            </div>

            {listing.sellerName && (
              // The whole card is already a link, so the seller row navigates programmatically instead of nesting <a>.
              <span
                role="link"
                tabIndex={0}
                aria-label={`Xem trang cá nhân của ${listing.sellerName}`}
                onClick={(event) => {
                  if (!listing.sellerId) return;
                  event.preventDefault();
                  event.stopPropagation();
                  navigate(`/nguoi-dang/${listing.sellerId}`);
                }}
                onKeyDown={(event) => {
                  if (listing.sellerId && (event.key === 'Enter' || event.key === ' ')) {
                    event.preventDefault();
                    event.stopPropagation();
                    navigate(`/nguoi-dang/${listing.sellerId}`);
                  }
                }}
                className="-mx-1 flex w-fit max-w-full items-center gap-2 rounded-full px-1 py-0.5 text-xs text-on-surface-variant hover:bg-surface-container-high focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <Avatar name={listing.sellerName} src={listing.sellerAvatarUrl} size="xs" />
                <span className="truncate font-medium text-on-surface hover:underline">{listing.sellerName}</span>
                {listing.isVerified && (
                  <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-secondary" aria-label="Người đăng đã xác thực" />
                )}
              </span>
            )}

            <div className="pt-2 border-t border-outline-variant/30 flex items-center justify-between text-xs text-on-surface-variant">
              <div className="flex items-center gap-1">
                <Maximize2 className="w-3.5 h-3.5 text-outline" />
                <span className="font-semibold text-on-surface">{listing.areaM2} m²</span>
              </div>

              <div className="flex items-center gap-1 max-w-[65%] truncate">
                <MapPin className="w-3.5 h-3.5 text-outline flex-shrink-0" />
                <span className="truncate">{listing.addressSummary}</span>
              </div>
            </div>
          </div>
        </Card>
      </Link>
      <div className="absolute right-3 top-3 z-10">
        <CompareToggleButton listing={listing} />
      </div>
    </div>
  );
};
