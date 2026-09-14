const UUID_AT_END = /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

export function slugifyListingTitle(title: string): string {
  return title.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 90) || 'bat-dong-san';
}

export function listingPath(listing: { slug?: string; title: string; id?: string }): string {
  return `/listings/${listing.slug || slugifyListingTitle(listing.title)}`;
}

export function listingIdFromRoute(value?: string): string | null {
  return value?.match(UUID_AT_END)?.[1] ?? null;
}
