import { apiClient } from '@/shared/api/client';
import type { ListingSummaryV2 } from '@/entities/listing/model/v2';

/** Backend: `com.company.bds.engagement.api` and `notification.api` (stream S6). */

export interface Unavailable {
  slug: string;
  /** Null when the listing must not be shown any more (e.g. locked by moderation). */
  title: string | null;
}

export interface SavedListingItem {
  listingId: string;
  savedAt: string;
  listing: ListingSummaryV2 | null;
  unavailable: Unavailable | null;
}

export interface SavedListingPage {
  items: SavedListingItem[];
  nextCursor: string | null;
  total: number;
  limit: number;
}

export type ShortlistRole = 'OWNER' | 'EDITOR' | 'VIEWER';
export type ShareRole = 'VIEWER' | 'EDITOR';

export interface ShortlistSummary {
  id: string;
  name: string;
  role: ShortlistRole;
  itemCount: number;
  memberCount: number;
  shared: boolean;
  muted: boolean;
  version: number;
  updatedAt: string;
}

export interface ShortlistItem {
  listingId: string;
  addedAt: string;
  addedByMe: boolean;
  listing: ListingSummaryV2 | null;
  unavailable: Unavailable | null;
}

export interface ShortlistMember {
  userId: string | null;
  name: string;
  role: ShareRole;
  muted: boolean | null;
  joinedAt: string;
}

export interface ShortlistDetail {
  id: string;
  name: string;
  role: ShortlistRole;
  muted: boolean;
  version: number;
  share: { shared: boolean; role: ShareRole | null } | null;
  items: ShortlistItem[];
  members: ShortlistMember[];
  itemCount: number;
  itemLimit: number;
}

export interface PublicShortlist {
  name: string;
  ownerGivenName: string | null;
  role: ShareRole;
  items: ListingSummaryV2[];
}

export type AlertFrequency = 'INSTANT' | 'DAILY' | 'WEEKLY' | 'OFF';

export interface SavedSearch {
  id: string;
  name: string;
  filter: Record<string, string>;
  query: string;
  filterHash: string;
  frequency: AlertFrequency;
  alertNew: boolean;
  alertPriceDrop: boolean;
  alertBackOnMarket: boolean;
  paused: boolean;
  pendingMatches: number;
  nextDigestAt: string | null;
  lastDigestAt: string | null;
  version: number;
  createdAt: string;
}

export interface SavedSearchSettings {
  name?: string;
  frequency?: AlertFrequency;
  alertNew?: boolean;
  alertPriceDrop?: boolean;
  alertBackOnMarket?: boolean;
  paused?: boolean;
}

export type NotificationCategory = 'ACCOUNT' | 'LISTINGS' | 'LEADS' | 'ALERTS' | 'SAVED_LISTINGS' | 'SHORTLIST';

export interface NotificationPreference {
  category: NotificationCategory;
  inApp: boolean;
  email: boolean;
  mandatoryInApp: boolean;
}

export interface NotificationItem {
  id: string;
  seq: number;
  type: string;
  category: NotificationCategory;
  title: string;
  message: string;
  link: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationFeed {
  items: NotificationItem[];
  nextBefore: number | null;
  unreadCount: number;
}

export interface UnsubscribeTarget {
  scope: 'SAVED_SEARCH' | 'CATEGORY';
  category: NotificationCategory | null;
  savedSearchName: string | null;
  applied: boolean;
}

const json = (body: unknown): RequestInit => ({ body: JSON.stringify(body) });
const enc = encodeURIComponent;

export const engagementApi = {
  savedIds: () => apiClient<{ ids: string[]; limit: number }>('/me/saved-listings/ids'),
  savedPage: (cursor?: string | null, size = 24) =>
    apiClient<SavedListingPage>(`/me/saved-listings?size=${size}${cursor ? `&cursor=${enc(cursor)}` : ''}`),
  save: (listingId: string) =>
    apiClient<{ listingId: string; saved: boolean; savedAt: string }>(`/me/saved-listings/${enc(listingId)}`, {
      method: 'PUT',
    }),
  unsave: (listingId: string) => apiClient<void>(`/me/saved-listings/${enc(listingId)}`, { method: 'DELETE' }),

  shortlists: () => apiClient<ShortlistSummary[]>('/me/shortlists'),
  shortlist: (id: string) => apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}`),
  createShortlist: (name: string) =>
    apiClient<ShortlistDetail>('/me/shortlists', { method: 'POST', ...json({ name }) }),
  renameShortlist: (id: string, name: string, expectedVersion: number) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}`, { method: 'PATCH', ...json({ name, expectedVersion }) }),
  deleteShortlist: (id: string) => apiClient<void>(`/me/shortlists/${enc(id)}`, { method: 'DELETE' }),
  addToShortlist: (id: string, listingId: string) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}/items/${enc(listingId)}`, { method: 'PUT' }),
  removeFromShortlist: (id: string, listingId: string) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}/items/${enc(listingId)}`, { method: 'DELETE' }),
  share: (id: string, role: ShareRole) =>
    apiClient<{ token: string; path: string; role: ShareRole }>(`/me/shortlists/${enc(id)}/share`, {
      method: 'POST',
      ...json({ role }),
    }),
  revokeShare: (id: string) => apiClient<void>(`/me/shortlists/${enc(id)}/share`, { method: 'DELETE' }),
  join: (token: string) => apiClient<ShortlistDetail>('/me/shortlists/join', { method: 'POST', ...json({ token }) }),
  setMemberRole: (id: string, memberId: string, role: ShareRole) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}/members/${enc(memberId)}`, {
      method: 'PATCH',
      ...json({ role }),
    }),
  removeMember: (id: string, memberId: string) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}/members/${enc(memberId)}`, { method: 'DELETE' }),
  leave: (id: string) => apiClient<void>(`/me/shortlists/${enc(id)}/membership`, { method: 'DELETE' }),
  mute: (id: string, muted: boolean) =>
    apiClient<ShortlistDetail>(`/me/shortlists/${enc(id)}/mute`, { method: 'PUT', ...json({ muted }) }),
  publicShortlist: (token: string) => apiClient<PublicShortlist>(`/public/shortlists/${enc(token)}`),

  savedSearches: () => apiClient<{ items: SavedSearch[]; limit: number }>('/me/saved-searches'),
  createSavedSearch: (filter: Record<string, string>, settings: SavedSearchSettings) =>
    apiClient<SavedSearch>('/me/saved-searches', { method: 'POST', ...json({ filter, ...settings }) }),
  updateSavedSearch: (id: string, expectedVersion: number, settings: SavedSearchSettings) =>
    apiClient<SavedSearch>(`/me/saved-searches/${enc(id)}`, {
      method: 'PATCH',
      ...json({ expectedVersion, ...settings }),
    }),
  deleteSavedSearch: (id: string) => apiClient<void>(`/me/saved-searches/${enc(id)}`, { method: 'DELETE' }),

  preferences: () => apiClient<NotificationPreference[]>('/me/notification-preferences'),
  savePreferences: (items: Array<Pick<NotificationPreference, 'category' | 'inApp' | 'email'>>) =>
    apiClient<NotificationPreference[]>('/me/notification-preferences', { method: 'PUT', ...json(items) }),

  feed: (before?: number | null, unread = false, size = 20) =>
    apiClient<NotificationFeed>(
      `/notifications/feed?size=${size}${unread ? '&unread=true' : ''}${before ? `&before=${before}` : ''}`,
    ),
  markRead: (id: string) => apiClient<void>(`/notifications/${enc(id)}/read`, { method: 'POST' }),
  markAllRead: (upToSeq: number) =>
    apiClient<{ count: number; latestSeq: number }>('/notifications/read-all', {
      method: 'POST',
      ...json({ upToSeq }),
    }),
  deleteNotification: (id: string) => apiClient<void>(`/notifications/${enc(id)}`, { method: 'DELETE' }),

  describeUnsubscribe: (token: string) => apiClient<UnsubscribeTarget>(`/public/unsubscribe?token=${enc(token)}`),
  unsubscribe: (token: string) =>
    apiClient<UnsubscribeTarget>(`/public/unsubscribe?token=${enc(token)}`, { method: 'POST' }),
};

export const CATEGORY_LABELS: Record<NotificationCategory, { title: string; description: string }> = {
  ACCOUNT: { title: 'Tài khoản và thanh toán', description: 'Gói đăng tin, đối soát thanh toán, xác minh danh tính.' },
  LISTINGS: { title: 'Tin đăng của tôi', description: 'Duyệt tin, hết hạn, tạm ẩn, đối chiếu giấy tờ.' },
  LEADS: { title: 'Khách quan tâm và lịch xem', description: 'Yêu cầu liên hệ mới, lịch hẹn xem nhà.' },
  ALERTS: { title: 'Cảnh báo tìm kiếm đã lưu', description: 'Tin mới, giảm giá, hiển thị lại khớp tìm kiếm của bạn.' },
  SAVED_LISTINGS: { title: 'Tin đã lưu', description: 'Tin bạn đã lưu vừa giảm giá hoặc hiển thị trở lại.' },
  SHORTLIST: { title: 'Danh sách chia sẻ', description: 'Thành viên thêm hoặc bỏ tin trong danh sách chung.' },
};

export const FREQUENCY_LABELS: Record<AlertFrequency, string> = {
  INSTANT: 'Ngay khi có (tối đa 15 phút một lần)',
  DAILY: 'Hằng ngày lúc 7:00',
  WEEKLY: 'Hằng tuần, sáng thứ Hai',
  OFF: 'Tắt cảnh báo',
};

export function problemCode(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'problem' in error) {
    return (error as { problem?: { code?: string } }).problem?.code;
  }
  return undefined;
}

export function problemDetail(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'problem' in error) {
    const detail = (error as { problem?: { detail?: string } }).problem?.detail;
    if (detail) return detail;
  }
  return fallback;
}
