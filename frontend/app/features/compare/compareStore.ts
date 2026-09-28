import { useSyncExternalStore } from 'react';
import { moneyFromLegacy, type Money } from '@/shared/format/money';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';

export const MAX_COMPARE = 3;
const STORAGE_KEY = 'nhadatchuan.compare.v1';

/**
 * What the compare tray remembers about a listing (a snapshot for the tray only: the compare page always reloads
 * the current public detail of every id, so a changed price or a hidden listing shows up there).
 */
export interface CompareItem {
  id: string;
  slug: string;
  title: string;
  purpose: 'SALE' | 'RENT';
  price?: Money | null;
  areaM2?: number | null;
  imageUrl?: string | null;
  addressSummary?: string | null;
}
export type CompareAddResult = 'added' | 'removed' | 'full' | 'purpose-mismatch';

export function compareItemFromSummary(listing: ListingSummaryV2): CompareItem {
  return {
    id: listing.id,
    slug: listing.slug,
    title: listing.title,
    purpose: listing.purpose,
    price: listing.price,
    areaM2: listing.areaM2,
    imageUrl: listing.image?.url ?? null,
    addressSummary: listing.location.addressSummary ?? null,
  };
}

/** Entries saved before API v2 carried `priceVnd`/`primaryImageUrl`; they are read as the v2 shape. */
function fromStored(value: unknown): CompareItem | null {
  if (!value || typeof value !== 'object') return null;
  const item = value as Record<string, unknown>;
  if (typeof item.id !== 'string' || typeof item.title !== 'string') return null;
  const purpose = item.purpose === 'RENT' ? 'RENT' : 'SALE';
  const price =
    item.price && typeof item.price === 'object'
      ? (item.price as Money)
      : typeof item.priceVnd === 'number'
        ? moneyFromLegacy(item.priceVnd, purpose)
        : null;
  return {
    id: item.id,
    slug: typeof item.slug === 'string' ? item.slug : item.id,
    title: item.title,
    purpose,
    price,
    areaM2: typeof item.areaM2 === 'number' ? item.areaM2 : null,
    imageUrl:
      typeof item.imageUrl === 'string'
        ? item.imageUrl
        : typeof item.primaryImageUrl === 'string'
          ? item.primaryImageUrl
          : null,
    addressSummary: typeof item.addressSummary === 'string' ? item.addressSummary : null,
  };
}

function readStorage(): CompareItem[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed)
      ? parsed
          .map(fromStored)
          .filter((item): item is CompareItem => item !== null)
          .slice(0, MAX_COMPARE)
      : [];
  } catch {
    return [];
  }
}

let items: CompareItem[] = readStorage();
const listeners = new Set<() => void>();

function commit(next: CompareItem[]) {
  items = next;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    /* storage unavailable: keep in memory */
  }
  listeners.forEach((listener) => listener());
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) {
      items = readStorage();
      listeners.forEach((listener) => listener());
    }
  });
}

function toItem(listing: CompareItem): CompareItem {
  const { id, slug, title, purpose, price, areaM2, imageUrl, addressSummary } = listing;
  return { id, slug, title, purpose, price, areaM2, imageUrl, addressSummary };
}

export const compareStore = {
  get: () => items,
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  has: (id: string) => items.some((item) => item.id === id),
  toggle(listing: CompareItem): CompareAddResult {
    if (items.some((item) => item.id === listing.id)) {
      commit(items.filter((item) => item.id !== listing.id));
      return 'removed';
    }
    if (items.length && items[0].purpose !== listing.purpose) return 'purpose-mismatch';
    if (items.length >= MAX_COMPARE) return 'full';
    commit([...items, toItem(listing)]);
    return 'added';
  },
  remove(id: string) {
    commit(items.filter((item) => item.id !== id));
  },
  replaceAll(next: CompareItem[]) {
    commit(next.slice(0, MAX_COMPARE).map(toItem));
  },
  clear() {
    commit([]);
  },
};

export function useCompareItems(): CompareItem[] {
  return useSyncExternalStore(compareStore.subscribe, compareStore.get, compareStore.get);
}

export function compareResultMessage(result: CompareAddResult): string | null {
  if (result === 'full') return `Chỉ so sánh tối đa ${MAX_COMPARE} tin. Hãy bỏ bớt một tin trước khi thêm.`;
  if (result === 'purpose-mismatch')
    return 'Chỉ so sánh các tin cùng mục đích. Danh sách hiện tại khác mục đích bán/cho thuê với tin này.';
  return null;
}
