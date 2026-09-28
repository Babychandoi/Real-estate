import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { useToast } from '@/shared/ui/Toast';
import { useNotificationState } from './notificationStore';
import type { StreamNotification } from './sseStream';

/**
 * Header link to the notification centre with the unread count (never colour alone: the count is in the accessible
 * name and in the badge text). Live notifications also show a short toast.
 */
export function NotificationBell() {
  const { unreadCount } = useNotificationState();
  const toast = useToast();

  useEffect(() => {
    const onNotification = (event: Event) => {
      const notification = (event as CustomEvent<StreamNotification>).detail;
      if (!notification?.title) return;
      toast.show({ kind: 'info', title: notification.title, description: notification.message, duration: 5000 });
    };
    window.addEventListener('bds:notification', onNotification);
    return () => window.removeEventListener('bds:notification', onNotification);
  }, [toast]);

  const shown = unreadCount > 99 ? '99+' : String(unreadCount);
  return (
    <Link
      to="/notifications"
      aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
      className="relative inline-grid min-h-11 min-w-11 place-items-center rounded-lg border border-outline-variant hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <Bell className="h-5 w-5" aria-hidden="true" />
      {unreadCount > 0 && (
        <span
          aria-hidden="true"
          className="absolute -right-1 -top-1 min-w-5 rounded-pill bg-error px-1 text-center text-xs font-bold leading-5 text-error-on"
        >
          {shown}
        </span>
      )}
    </Link>
  );
}
