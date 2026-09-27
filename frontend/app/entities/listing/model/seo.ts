import type { DocumentMeta } from '@/shared/seo/useDocumentMeta';
import { formatPriceVnd, formatPropertyType, type ListingDetail } from './types';

const UUID_AT_END = /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

export function slugifyListingTitle(title: string): string {
  return (
    title
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[đĐ]/g, 'd')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 90) || 'bat-dong-san'
  );
}

export function listingPath(listing: { slug?: string; title: string; id?: string }): string {
  return `/listings/${listing.slug || slugifyListingTitle(listing.title)}`;
}

export function listingIdFromRoute(value?: string): string | null {
  return value?.match(UUID_AT_END)?.[1] ?? null;
}

/** Route metadata of a public listing: title, description, canonical slug URL, Open Graph and JSON-LD. */
export function listingDocumentMeta(listing: ListingDetail, origin: string): DocumentMeta {
  const canonicalPath = listingPath(listing);
  const canonicalUrl = new URL(canonicalPath, origin).toString();
  const description = `${formatPropertyType(listing.propertyType)} ${listing.purpose === 'SALE' ? 'cần bán' : 'cho thuê'} tại ${listing.addressSummary}, diện tích ${listing.areaM2} m², giá ${formatPriceVnd(listing.priceVnd)}.`;
  return {
    title: `${listing.title} | Nhà Đất Chuẩn`,
    description,
    canonical: canonicalUrl,
    og: { title: listing.title, description, type: 'product', url: canonicalUrl, image: listing.imageUrls[0] },
    jsonLd: {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: listing.title,
      category: 'Bất động sản',
      description: listing.description,
      url: canonicalUrl,
      image: listing.imageUrls,
      datePosted: listing.createdAt,
      address: { '@type': 'PostalAddress', streetAddress: listing.addressSummary, addressCountry: 'VN' },
      offers: {
        '@type': 'Offer',
        price: listing.priceVnd,
        priceCurrency: 'VND',
        availability: 'https://schema.org/InStock',
      },
    },
  };
}
