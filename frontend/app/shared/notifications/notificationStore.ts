import { useSyncExternalStore } from 'react';
import { apiClient } from '@/shared/api/client';
import type { StreamNotification } from './sseStream';

/**
 * Unread count and the live notifications received on this page since sign-in. The server is the source of truth: the
 * count is (re)loaded over REST at sign-in, after a resync and when the centre changes read state; live SSE frames only
 * move it forward in between.
 */
interface NotificationState {
  unreadCount: number;
  latestSeq: number;
  /** Newest first, at most 20. */
  live: StreamNotification[];
}

const EMPTY: NotificationState = { unreadCount: 0, latestSeq: 0, live: [] };
let state: NotificationState = EMPTY;
const listeners = new Set<() => void>();

function set(next: NotificationState) {
  state = next;
  listeners.forEach((listener) => listener());
}

export const notificationStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  get: () => state,
  reset: () => set(EMPTY),
  setUnread(unreadCount: number, latestSeq: number) {
    set({ ...state, unreadCount: Math.max(0, unreadCount), latestSeq: Math.max(state.latestSeq, latestSeq) });
  },
  /** A live frame (already deduplicated by the stream). */
  receive(notification: StreamNotification) {
    if (state.live.some((item) => item.id === notification.id)) return;
    set({
      unreadCount: state.unreadCount + 1,
      latestSeq: Math.max(state.latestSeq, notification.seq),
      live: [notification, ...state.live].slice(0, 20),
    });
  },
  async refresh() {
    try {
      const count = await apiClient<{ count: number; latestSeq: number }>('/notifications/unread-count');
      notificationStore.setUnread(count.count, count.latestSeq);
    } catch {
      // Offline or signed out: keep the last known count.
    }
  },
};

export function useNotificationState(): NotificationState {
  return useSyncExternalStore(notificationStore.subscribe, notificationStore.get, notificationStore.get);
}
