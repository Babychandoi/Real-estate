import { useSyncExternalStore } from 'react';
import type { Listing } from '@/entities/listing/model/types';

export const MAX_COMPARE = 3;
const STORAGE_KEY = 'nhadatchuan.compare.v1';

export type CompareItem = Pick<
  Listing,
  'id' | 'slug' | 'title' | 'purpose' | 'priceVnd' | 'areaM2' | 'primaryImageUrl' | 'addressSummary'
>;
export type CompareAddResult = 'added' | 'removed' | 'full' | 'purpose-mismatch';

function readStorage(): CompareItem[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed)
      ? parsed.filter((item) => item && typeof item.id === 'string').slice(0, MAX_COMPARE)
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
  const { id, slug, title, purpose, priceVnd, areaM2, primaryImageUrl, addressSummary } = listing;
  return { id, slug, title, purpose, priceVnd, areaM2, primaryImageUrl, addressSummary };
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
