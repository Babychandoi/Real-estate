import { useSyncExternalStore } from 'react';
import { apiClient } from '@/shared/api/client';

/**
 * Which listings the signed-in account saved (for the heart on cards and the detail page). Loaded once per account on
 * first use; toggles are optimistic and rolled back when the server refuses. Kept tiny on purpose: it ships with the
 * search page.
 */
type Status = 'idle' | 'loading' | 'ready' | 'error';

interface State {
  userId: string | null;
  status: Status;
  ids: ReadonlySet<string>;
  pending: ReadonlySet<string>;
}

let state: State = { userId: null, status: 'idle', ids: new Set(), pending: new Set() };
const listeners = new Set<() => void>();
const set = (next: Partial<State>) => {
  state = { ...state, ...next };
  listeners.forEach((listener) => listener());
};

export const savedListingsStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  get: () => state,
  /** Loads the ids for this account (no-op when already loaded or loading for it); null = signed out. */
  ensure(userId: string | null) {
    if (userId === null) {
      if (state.userId !== null) set({ userId: null, status: 'idle', ids: new Set(), pending: new Set() });
      return;
    }
    if (state.userId === userId && state.status !== 'error') return;
    set({ userId, status: 'loading', ids: new Set(), pending: new Set() });
    apiClient<{ ids: string[] }>('/me/saved-listings/ids')
      .then((result) => {
        if (state.userId === userId) set({ status: 'ready', ids: new Set(result.ids) });
      })
      .catch(() => {
        if (state.userId === userId) set({ status: 'error' });
      });
  },
  /** @returns the new saved state; throws (after rolling back) when the server refused. */
  async toggle(listingId: string): Promise<boolean> {
    const wasSaved = state.ids.has(listingId);
    const optimistic = new Set(state.ids);
    if (wasSaved) optimistic.delete(listingId);
    else optimistic.add(listingId);
    set({ ids: optimistic, pending: new Set([...state.pending, listingId]) });
    const done = () => {
      const pending = new Set(state.pending);
      pending.delete(listingId);
      return pending;
    };
    try {
      await apiClient(`/me/saved-listings/${encodeURIComponent(listingId)}`, { method: wasSaved ? 'DELETE' : 'PUT' });
      set({ pending: done() });
      return !wasSaved;
    } catch (error) {
      const rollback = new Set(state.ids);
      if (wasSaved) rollback.add(listingId);
      else rollback.delete(listingId);
      set({ ids: rollback, pending: done() });
      throw error;
    }
  },
  /** Removal done elsewhere (the saved list page). */
  forget(listingId: string) {
    if (!state.ids.has(listingId)) return;
    const ids = new Set(state.ids);
    ids.delete(listingId);
    set({ ids });
  },
};

export function useSavedListings(): State {
  return useSyncExternalStore(savedListingsStore.subscribe, savedListingsStore.get, savedListingsStore.get);
}
