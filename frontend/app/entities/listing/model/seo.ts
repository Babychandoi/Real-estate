import { formatMoney } from '@/shared/format/money';
import type { DocumentMeta } from '@/shared/seo/useDocumentMeta';
import { formatArea, propertyTypeLabel, type ListingDetailV2 } from './v2';

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

/** Absolute URL for an image path that may already be absolute or may be relative to `origin` (m13: `og:image`
 * must be an absolute URL — crawlers and chat previews do not resolve a relative one against the page URL). */
function absoluteImageUrl(url: string, origin: string): string {
  try {
    return new URL(url, origin).toString();
  } catch {
    return url;
  }
}

/** Route metadata of a public listing (API v2 detail): title, description, canonical slug URL, Open Graph and JSON-LD. */
export function listingDocumentMeta(listing: ListingDetailV2, origin: string): DocumentMeta {
  const canonicalPath = listingPath(listing);
  const canonicalUrl = new URL(canonicalPath, origin).toString();
  const isRent = listing.purpose === 'RENT';
  // The API price carries its period, so a rent keeps "/tháng" in the description and OG card too (m13).
  const price = formatMoney(listing.price);
  const place = listing.location.addressSummary || listing.location.districtName || 'Hà Nội';
  const description = `${propertyTypeLabel(listing.propertyType)} ${isRent ? 'cho thuê' : 'cần bán'} tại ${place}, diện tích ${formatArea(listing.areaM2)}, giá ${price}.`;
  const images = listing.images.map((image) => absoluteImageUrl(image.url, origin));
  return {
    title: `${listing.title} | Nhà Đất Chuẩn`,
    description,
    canonical: canonicalUrl,
    og: { title: listing.title, description, type: 'product', url: canonicalUrl, image: images[0] },
    jsonLd: {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: listing.title,
      category: 'Bất động sản',
      description: listing.description ?? undefined,
      url: canonicalUrl,
      image: images,
      datePosted: listing.freshness.publishedAt,
      address: { '@type': 'PostalAddress', streetAddress: place, addressLocality: 'Hà Nội', addressCountry: 'VN' },
      offers: {
        '@type': 'Offer',
        price: listing.price.amount,
        priceCurrency: listing.price.currency,
        availability: 'https://schema.org/InStock',
        // A rent is per month (m13): UnitPriceSpecification with unitCode "MON" says so machine-readably.
        ...(isRent
          ? {
              priceSpecification: {
                '@type': 'UnitPriceSpecification',
                price: listing.price.amount,
                priceCurrency: listing.price.currency,
                unitCode: 'MON',
                referenceQuantity: { '@type': 'QuantitativeValue', value: 1, unitCode: 'MON' },
              },
            }
          : {}),
      },
    },
  };
}
