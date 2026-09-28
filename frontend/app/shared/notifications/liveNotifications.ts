import { apiUrl, apiFetch } from '@/shared/api/client';
import { notificationStore } from './notificationStore';
import { startNotificationStream, type StreamNotification } from './sseStream';

/**
 * Starts the signed-in account's live notifications: loads the unread count, opens the SSE stream (sseStream.ts) and
 * re-broadcasts each new notification as a `bds:notification` window event. Loaded on demand after sign-in so the
 * stream code is not part of the initial page script. Returns a stop function (sign-out, account change).
 */
export function startLiveNotifications(onNotification?: (notification: StreamNotification) => void): () => void {
  void notificationStore.refresh();
  const stream = startNotificationStream({
    url: apiUrl('/notifications/stream'),
    fetchImpl: (url, init) => apiFetch(url, init),
    onResync: () => void notificationStore.refresh(),
    onNotification: (notification) => {
      notificationStore.receive(notification);
      onNotification?.(notification);
      window.dispatchEvent(new CustomEvent('bds:notification', { detail: notification }));
    },
  });
  return () => {
    stream.stop();
    notificationStore.reset();
  };
}
