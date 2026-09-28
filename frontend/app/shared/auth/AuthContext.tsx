import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiClient, clearAccessToken, SESSION_ENDED_EVENT, setAccessToken } from '@/shared/api/client';
import {
  canUseBrokerWorkspace,
  hasRole as roleIn,
  isPoster as roleIsPoster,
  isStaff as roleIsStaff,
  ROLE_LABELS,
  type Role,
  type SelfServiceRole,
} from './roles';

export type UserRole = Role;
export interface AuthUser {
  id: string;
  name: string;
  email: string;
  phone?: string;
  role: UserRole;
  planCode: string;
  planExpiresAt?: string;
  listingQuotaRemaining: number;
  avatarMediaUrl?: string;
  roleLabel: string;
  avatarInitial?: string;
}
interface ServerUser {
  id: string;
  name: string;
  email: string;
  phone?: string;
  role: UserRole;
  planCode: string;
  planExpiresAt?: string;
  listingQuotaRemaining: number;
  avatarMediaUrl?: string;
}
export interface AuthResult {
  accessToken: string;
  expiresAt: string;
  /** Staff sessions end after this much inactivity (sliding); null for members. */
  idleExpiresAt?: string | null;
  user: ServerUser;
}
/** Second step of the staff login: verify a code, or enrol an authenticator first. */
export interface MfaChallenge {
  state: 'VERIFY' | 'ENROLL';
  challengeToken: string;
  challengeExpiresAt: string;
}
interface AdminLoginResponse extends Partial<AuthResult> {
  mfaRequired: boolean;
  mfaState?: 'VERIFY' | 'ENROLL' | null;
  challengeToken?: string | null;
  challengeExpiresAt?: string | null;
}
export type AdminLoginOutcome =
  { success: true; mfa?: undefined } | { success: true; mfa: MfaChallenge } | { success: false; error: string };
interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAuthLoading: boolean;
  /** STAFF: admin desks (moderation, verification, CMS…). */
  isAdminOrModerator: boolean;
  /** BROKER_WORKSPACE: brokers and admins (the broker workspace, "Môi giới Pro"). */
  isBroker: boolean;
  /** POSTERS: brokers, owners and admins can post listings and see their leads and billing. */
  isPoster: boolean;
  hasRole: (allowed: readonly Role[]) => boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  adminLogin: (email: string, password: string) => Promise<AdminLoginOutcome>;
  /** Signs the tab in with a session obtained from the MFA step. */
  acceptSession: (result: AuthResult) => void;
  register: (
    email: string,
    password: string,
    name: string,
    accountType: SelfServiceRole,
    returnTo?: string,
  ) => Promise<{ success: boolean; email?: string; error?: string }>;
  /** Set when the server ended the session (idle timeout, revoked elsewhere); cleared at the next sign-in. */
  sessionEnded: boolean;
  resendVerification: (email: string) => Promise<{ success: boolean; error?: string }>;
  refreshUser: () => Promise<void>;
  logout: () => void;
  isLoginModalOpen: boolean;
  setIsLoginModalOpen: (open: boolean) => void;
}
const AuthContext = createContext<AuthContextType | undefined>(undefined);
function toUser(user: ServerUser): AuthUser {
  return {
    ...user,
    roleLabel: ROLE_LABELS[user.role] ?? 'Thành viên',
    avatarInitial: user.name.charAt(0).toUpperCase(),
  };
}
function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'problem' in error) {
    const problem = (error as { problem?: { detail?: string } }).problem;
    if (problem?.detail) return problem.detail;
  }
  return 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.';
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(true);
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  // Bumped at every sign-in: a stream stopped by an expired session restarts even for the same account.
  const [sessionKey, setSessionKey] = useState(0);
  const [sessionEnded, setSessionEnded] = useState(false);

  useEffect(() => {
    const ended = () => {
      setUser((current) => {
        if (current) setSessionEnded(true);
        return null;
      });
    };
    window.addEventListener(SESSION_ENDED_EVENT, ended);
    return () => window.removeEventListener(SESSION_ENDED_EVENT, ended);
  }, []);

  useEffect(() => {
    if (!sessionStorage.getItem('bds_access_token')) {
      setIsAuthLoading(false);
      return;
    }
    apiClient<ServerUser>('/auth/me')
      .then((value) => setUser(toUser(value)))
      .catch(() => clearAccessToken())
      .finally(() => setIsAuthLoading(false));
  }, []);

  useEffect(() => {
    if (!user) return;
    // One stream per signed-in account (audit F12.2): reconnects on errors and on a normal end of stream with
    // backoff + jitter, replays with Last-Event-ID and drops duplicates. Loaded on demand (not in the initial script).
    let stop: (() => void) | null = null;
    let cancelled = false;
    void import('@/shared/notifications/liveNotifications').then(({ startLiveNotifications }) => {
      if (cancelled) return;
      stop = startLiveNotifications((notification) => {
        if (notification.type !== 'PLAN_UPGRADED') return;
        apiClient<ServerUser>('/auth/me')
          .then((fresh) => setUser(toUser(fresh)))
          .catch(() => undefined);
      });
    });
    return () => {
      cancelled = true;
      stop?.();
    };
    // One stream per signed-in account: reconnect when the user id changes, not when profile fields update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, sessionKey]);

  // Every action below only touches state setters and module functions, so a stable identity is safe and keeps
  // the context value from changing on each render.
  const accept = useCallback((result: AuthResult) => {
    setAccessToken(result.accessToken);
    setUser(toUser(result.user));
    setSessionKey((key) => key + 1);
    setSessionEnded(false);
    setIsLoginModalOpen(false);
  }, []);
  const login = useCallback(
    async (email: string, password: string) => {
      try {
        accept(
          await apiClient<AuthResult>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
        );
        return { success: true };
      } catch (error) {
        return { success: false, error: errorMessage(error) };
      }
    },
    [accept],
  );
  const adminLogin = useCallback(
    async (email: string, password: string): Promise<AdminLoginOutcome> => {
      try {
        const result = await apiClient<AdminLoginResponse>('/auth/admin/login', {
          method: 'POST',
          body: JSON.stringify({ email, password }),
        });
        if (result.mfaRequired && result.challengeToken && result.mfaState && result.challengeExpiresAt) {
          return {
            success: true,
            mfa: {
              state: result.mfaState,
              challengeToken: result.challengeToken,
              challengeExpiresAt: result.challengeExpiresAt,
            },
          };
        }
        if (!result.accessToken || !result.user || !result.expiresAt) {
          return { success: false, error: 'Máy chủ trả về phản hồi đăng nhập không hợp lệ.' };
        }
        accept({
          accessToken: result.accessToken,
          expiresAt: result.expiresAt,
          idleExpiresAt: result.idleExpiresAt,
          user: result.user,
        });
        return { success: true };
      } catch (error) {
        return { success: false, error: errorMessage(error) };
      }
    },
    [accept],
  );
  const register = useCallback(
    async (email: string, password: string, name: string, accountType: SelfServiceRole, returnTo?: string) => {
      try {
        const result = await apiClient<{ email: string; requiresEmailVerification: boolean }>('/auth/register', {
          method: 'POST',
          body: JSON.stringify({ email, password, name, accountType, returnTo }),
        });
        return { success: true, email: result.email };
      } catch (error) {
        return { success: false, error: errorMessage(error) };
      }
    },
    [],
  );
  const logout = useCallback(() => {
    apiClient<void>('/auth/logout', { method: 'POST' }).catch(() => undefined);
    clearAccessToken();
    setUser(null);
    setIsLoginModalOpen(false);
  }, []);
  const resendVerification = useCallback(async (email: string) => {
    try {
      await apiClient<void>('/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email }) });
      return { success: true };
    } catch (error) {
      return { success: false, error: errorMessage(error) };
    }
  }, []);
  const refreshUser = useCallback(async () => {
    const fresh = await apiClient<ServerUser>('/auth/me');
    setUser(toUser(fresh));
  }, []);
  const value = useMemo<AuthContextType>(
    () => ({
      user,
      isAuthenticated: !!user,
      isAuthLoading,
      isAdminOrModerator: roleIsStaff(user?.role),
      isBroker: canUseBrokerWorkspace(user?.role),
      isPoster: roleIsPoster(user?.role),
      hasRole: (allowed: readonly Role[]) => roleIn(user?.role, allowed),
      login,
      adminLogin,
      acceptSession: accept,
      register,
      sessionEnded,
      logout,
      resendVerification,
      refreshUser,
      isLoginModalOpen,
      setIsLoginModalOpen,
    }),
    [
      user,
      isAuthLoading,
      isLoginModalOpen,
      login,
      adminLogin,
      accept,
      register,
      sessionEnded,
      logout,
      resendVerification,
      refreshUser,
    ],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};
