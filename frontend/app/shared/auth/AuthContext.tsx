import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiClient, apiFetch, clearAccessToken, setAccessToken } from '@/shared/api/client';

export type UserRole = 'ADMIN' | 'MODERATOR' | 'BROKER' | 'USER';
export interface AuthUser { id: string; name: string; email: string; phone?: string; role: UserRole; planCode:string; planExpiresAt?:string; listingQuotaRemaining:number; avatarMediaUrl?: string; roleLabel: string; avatarInitial?: string; }
interface ServerUser { id: string; name: string; email: string; phone?: string; role: UserRole; planCode:string; planExpiresAt?:string; listingQuotaRemaining:number; avatarMediaUrl?: string; }
interface AuthResult { accessToken: string; expiresAt: string; user: ServerUser; }
interface AuthContextType {
  user: AuthUser | null; isAuthenticated: boolean; isAuthLoading: boolean;
  isAdminOrModerator: boolean; isBroker: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  adminLogin: (email: string, password: string, mfaCode: string) => Promise<{ success: boolean; error?: string }>;
  register: (email: string, password: string, name: string, accountType: 'BROKER' | 'USER') => Promise<{ success: boolean; email?: string; error?: string }>;
  resendVerification: (email: string) => Promise<{ success:boolean; error?:string }>;
  refreshUser: () => Promise<void>;
  logout: () => void; isLoginModalOpen: boolean; setIsLoginModalOpen: (open: boolean) => void;
}
const AuthContext = createContext<AuthContextType | undefined>(undefined);
const ROLE_LABELS: Record<UserRole, string> = { ADMIN: 'Quản trị viên', MODERATOR: 'Chuyên viên kiểm duyệt', BROKER: 'Môi giới BĐS', USER: 'Người tìm nhà' };

function toUser(user: ServerUser): AuthUser {
  return { ...user, roleLabel: ROLE_LABELS[user.role], avatarInitial: user.name.charAt(0).toUpperCase() };
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

  useEffect(() => {
    if (!sessionStorage.getItem('bds_access_token')) { setIsAuthLoading(false); return; }
    apiClient<ServerUser>('/auth/me').then((value) => setUser(toUser(value))).catch(() => clearAccessToken()).finally(() => setIsAuthLoading(false));
  }, []);

  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    const connect = async () => {
      try {
        const response = await apiFetch('/notifications/stream', { headers: { Accept: 'text/event-stream' }, signal: controller.signal });
        const reader=response.body?.getReader(); if(!reader)return; const decoder=new TextDecoder(); let buffer='';
        while(true){const{done,value}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true});const events=buffer.split('\n\n');buffer=events.pop()||'';for(const event of events){if(event.includes('event:notification')&&event.includes('PLAN_UPGRADED')){const fresh=await apiClient<ServerUser>('/auth/me');setUser(toUser(fresh));window.dispatchEvent(new CustomEvent('bds:notification',{detail:event}));}}}
      } catch { if(!controller.signal.aborted)setTimeout(connect,2000); }
    }; connect(); return()=>controller.abort();
  }, [user?.id]);

  const accept = (result: AuthResult) => {
    setAccessToken(result.accessToken);
    setUser(toUser(result.user));
    setIsLoginModalOpen(false);
  };
  const login = async (email: string, password: string) => {
    try { accept(await apiClient<AuthResult>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })); return { success: true }; }
    catch (error) { return { success: false, error: errorMessage(error) }; }
  };
  const adminLogin = async (email: string, password: string, mfaCode: string) => {
    try { accept(await apiClient<AuthResult>('/auth/admin/login', { method: 'POST', body: JSON.stringify({ email, password, mfaCode }) })); return { success: true }; }
    catch (error) { return { success: false, error: errorMessage(error) }; }
  };
  const register = async (email: string, password: string, name: string, accountType: 'BROKER' | 'USER') => {
    try { const result=await apiClient<{email:string;requiresEmailVerification:boolean}>('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, name, accountType }) }); return { success: true, email: result.email }; }
    catch (error) { return { success: false, error: errorMessage(error) }; }
  };
  const logout = () => {
    apiClient<void>('/auth/logout', { method: 'POST' }).catch(() => undefined);
    clearAccessToken(); setUser(null); setIsLoginModalOpen(false);
  };
  const resendVerification = async (email:string) => {
    try { await apiClient<void>('/auth/resend-verification',{method:'POST',body:JSON.stringify({email})}); return {success:true}; }
    catch(error){ return {success:false,error:errorMessage(error)}; }
  };
  const refreshUser = async () => { const fresh = await apiClient<ServerUser>('/auth/me'); setUser(toUser(fresh)); };
  const value = useMemo<AuthContextType>(() => ({ user, isAuthenticated: !!user, isAuthLoading,
    isAdminOrModerator: user?.role === 'ADMIN' || user?.role === 'MODERATOR',
    isBroker: user?.role === 'BROKER' || user?.role === 'ADMIN', login, adminLogin, register, logout,
    resendVerification, refreshUser, isLoginModalOpen, setIsLoginModalOpen }), [user, isAuthLoading, isLoginModalOpen]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};
