import { formatMoney, moneyFromLegacy } from '@/shared/format/money';
import type { DocumentMeta } from '@/shared/seo/useDocumentMeta';
import { formatPropertyType, type ListingDetail } from './types';

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

/** Route metadata of a public listing: title, description, canonical slug URL, Open Graph and JSON-LD. */
export function listingDocumentMeta(listing: ListingDetail, origin: string): DocumentMeta {
  const canonicalPath = listingPath(listing);
  const canonicalUrl = new URL(canonicalPath, origin).toString();
  const isRent = listing.purpose === 'RENT';
  // formatMoney (not the purpose-agnostic formatPriceVnd) so a rent price keeps its "/tháng" suffix everywhere it
  // is shown, including here (m13): a description or OG card that silently drops the period reads like a one-off
  // sale price and misleads anyone sharing or previewing the link.
  const price = formatMoney(moneyFromLegacy(listing.priceVnd, listing.purpose));
  const description = `${formatPropertyType(listing.propertyType)} ${isRent ? 'cho thuê' : 'cần bán'} tại ${listing.addressSummary}, diện tích ${listing.areaM2} m², giá ${price}.`;
  const image = listing.imageUrls[0] ? absoluteImageUrl(listing.imageUrls[0], origin) : undefined;
  return {
    title: `${listing.title} | Nhà Đất Chuẩn`,
    description,
    canonical: canonicalUrl,
    og: { title: listing.title, description, type: 'product', url: canonicalUrl, image },
    jsonLd: {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: listing.title,
      category: 'Bất động sản',
      description: listing.description,
      url: canonicalUrl,
      image: listing.imageUrls.map((url) => absoluteImageUrl(url, origin)),
      datePosted: listing.createdAt,
      address: { '@type': 'PostalAddress', streetAddress: listing.addressSummary, addressCountry: 'VN' },
      offers: {
        '@type': 'Offer',
        price: listing.priceVnd,
        priceCurrency: 'VND',
        availability: 'https://schema.org/InStock',
        // A rent listing's price is per month (m13): priceSpecification with unitCode "MON" (UN/CEFACT common
        // code for month) and referenceQuantity 1 says so machine-readably, instead of the bare `price` reading
        // like a one-time sale amount.
        ...(isRent
          ? {
              priceSpecification: {
                '@type': 'UnitPriceSpecification',
                price: listing.priceVnd,
                priceCurrency: 'VND',
                unitCode: 'MON',
                referenceQuantity: { '@type': 'QuantitativeValue', value: 1, unitCode: 'MON' },
              },
            }
          : {}),
      },
    },
  };
}
