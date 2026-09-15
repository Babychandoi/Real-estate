import React from 'react';
import { type Listing, formatPriceVnd, calculateUnitPrice } from '../model/types';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { Building2, MapPin, ShieldCheck, Maximize2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { listingPath } from '../model/seo';

interface ListingCardProps {
  listing: Listing;
}

export const ListingCard: React.FC<ListingCardProps> = ({ listing }) => {
  return (
    <Link to={listingPath(listing)} aria-label={`Xem chi tiết: ${listing.title}`} className="block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2">
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
          <div className="grid h-full place-items-center bg-surface-container-high text-on-surface-variant" role="img" aria-label="Tin đăng chưa có ảnh">
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
  );
};
