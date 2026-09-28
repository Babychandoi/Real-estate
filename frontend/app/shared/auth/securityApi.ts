import { apiClient } from '@/shared/api/client';
import { ApiProblemException } from '@/shared/types/problem-details';

/** One open session of the signed-in account (GET /me/sessions). Never carries the token. */
export interface SessionView {
  id: string;
  current: boolean;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
  idleExpiresAt: string | null;
  device: string | null;
  ipHint: string | null;
  mfaVerified: boolean;
}

export type SecurityEventType =
  | 'LOGIN_SUCCEEDED'
  | 'LOGIN_FAILED'
  | 'MFA_SUCCEEDED'
  | 'MFA_FAILED'
  | 'MFA_RECOVERY_CODE_USED'
  | 'MFA_ENROLLED'
  | 'MFA_RECOVERY_CODES_REGENERATED'
  | 'MFA_RESET_BY_ADMIN'
  | 'MFA_CHALLENGE_LOCKED'
  | 'PASSWORD_CHANGED'
  | 'PASSWORD_RESET'
  | 'SESSION_REVOKED'
  | 'OTHER_SESSIONS_REVOKED'
  | 'SESSIONS_REVOKED_BY_ADMIN';

export interface SecurityEvent {
  id: string;
  type: SecurityEventType;
  ipHint: string | null;
  device: string | null;
  byAdmin: boolean;
  occurredAt: string;
}

export interface MfaStatus {
  enrolled: boolean;
  enrolledAt: string | null;
  recoveryCodesRemaining: number;
  required: boolean;
}

export const SECURITY_EVENT_LABELS: Record<SecurityEventType, string> = {
  LOGIN_SUCCEEDED: 'Đăng nhập thành công',
  LOGIN_FAILED: 'Nhập sai mật khẩu',
  MFA_SUCCEEDED: 'Xác thực hai lớp thành công',
  MFA_FAILED: 'Nhập sai mã xác thực',
  MFA_RECOVERY_CODE_USED: 'Đăng nhập bằng mã khôi phục',
  MFA_ENROLLED: 'Bật xác thực hai lớp',
  MFA_RECOVERY_CODES_REGENERATED: 'Tạo lại mã khôi phục',
  MFA_RESET_BY_ADMIN: 'Quản trị viên đặt lại xác thực hai lớp',
  MFA_CHALLENGE_LOCKED: 'Khóa lượt đăng nhập vì sai mã nhiều lần',
  PASSWORD_CHANGED: 'Đổi mật khẩu',
  PASSWORD_RESET: 'Đặt lại mật khẩu qua email',
  SESSION_REVOKED: 'Đăng xuất một thiết bị',
  OTHER_SESSIONS_REVOKED: 'Đăng xuất các thiết bị khác',
  SESSIONS_REVOKED_BY_ADMIN: 'Quản trị viên đăng xuất mọi thiết bị',
};

/** Events that deserve attention when the person does not recognise them. */
export const WARNING_EVENTS: ReadonlySet<SecurityEventType> = new Set([
  'LOGIN_FAILED',
  'MFA_FAILED',
  'MFA_CHALLENGE_LOCKED',
  'MFA_RESET_BY_ADMIN',
  'SESSIONS_REVOKED_BY_ADMIN',
]);

export const securityApi = {
  sessions: () => apiClient<SessionView[]>('/me/sessions'),
  revokeSession: (id: string) => apiClient<void>(`/me/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  revokeOtherSessions: () => apiClient<{ revokedSessions: number }>('/me/sessions/revoke-others', { method: 'POST' }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient<{ revokedSessions: number }>('/me/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  events: () => apiClient<SecurityEvent[]>('/me/security-events'),
  mfaStatus: () => apiClient<MfaStatus>('/me/mfa'),
  regenerateRecoveryCodes: (code: string) =>
    apiClient<{ recoveryCodes: string[] }>('/me/mfa/recovery-codes', {
      method: 'POST',
      body: JSON.stringify({ code }),
    }),
};

/** Machine code of a Problem Details error (e.g. TOKEN_EXPIRED), if any. */
export function problemCode(error: unknown): string | undefined {
  return error instanceof ApiProblemException ? error.problem.code : undefined;
}

export function problemStatus(error: unknown): number | undefined {
  return error instanceof ApiProblemException ? error.problem.status : undefined;
}

/** "203.0.113.x · Chrome trên Windows" style summary; never invents data that is missing. */
export function describeClient(device: string | null, ipHint: string | null): string {
  return [device ?? 'Thiết bị không xác định', ipHint].filter(Boolean).join(' · ');
}

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTime.format(date);
}
