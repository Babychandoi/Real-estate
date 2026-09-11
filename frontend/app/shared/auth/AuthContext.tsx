import React, { createContext, useContext, useState, useEffect } from 'react';

export type UserRole = 'ADMIN' | 'MODERATOR' | 'BROKER' | 'USER';

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  roleLabel: string;
  phone?: string;
  avatarInitial?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdminOrModerator: boolean;
  isBroker: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  register: (email: string, password: string, name: string, accountType: 'BROKER' | 'USER') => Promise<{ success: boolean; error?: string }>;
  logout: () => void;
  isLoginModalOpen: boolean;
  setIsLoginModalOpen: (open: boolean) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/** Label hiển thị cho từng vai trò (chỉ dùng nội bộ, không lộ ra ngoài cho khách) */
const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Quản trị viên Hệ thống',
  MODERATOR: 'Chuyên viên Thẩm định & Kiểm duyệt',
  BROKER: 'Môi giới BĐS chuyên nghiệp',
  USER: 'Người tìm nhà',
};

/**
 * Giả lập danh sách tài khoản trong hệ thống.
 * - ADMIN & MODERATOR: Chỉ Super Admin tạo (không ai tự đăng ký được)
 * - BROKER & USER: Người dùng tự đăng ký
 */
const SEED_ACCOUNTS: Array<{
  email: string;
  password: string;
  name: string;
  role: UserRole;
  phone?: string;
}> = [
  {
    email: 'admin@bdswf.vn',
    password: 'admin2026',
    name: 'Nguyễn Văn Quản Trị',
    role: 'ADMIN',
    phone: '0901***001',
  },
  {
    email: 'moderator@bdswf.vn',
    password: 'mod2026',
    name: 'Trần Thị Thẩm Định',
    role: 'MODERATOR',
    phone: '0901***002',
  },
  {
    email: 'broker@bdswf.vn',
    password: 'broker2026',
    name: 'Lê Hoàng Môi Giới',
    role: 'BROKER',
    phone: '0988***888',
  },
  {
    email: 'user@bdswf.vn',
    password: 'user2026',
    name: 'Phạm Minh Khách Hàng',
    role: 'USER',
    phone: '0977***999',
  },
];

/** Lưu danh sách tài khoản đã đăng ký (giả lập, sẽ thay bằng API thật) */
const getRegisteredAccounts = () => {
  try {
    const stored = localStorage.getItem('bds_registered_accounts');
    return stored ? JSON.parse(stored) as typeof SEED_ACCOUNTS : [];
  } catch {
    return [];
  }
};

const saveRegisteredAccount = (account: typeof SEED_ACCOUNTS[0]) => {
  const existing = getRegisteredAccounts();
  existing.push(account);
  localStorage.setItem('bds_registered_accounts', JSON.stringify(existing));
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const cached = localStorage.getItem('bds_auth_user');
      return cached ? JSON.parse(cached) : null;
    } catch {
      return null;
    }
  });

  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  useEffect(() => {
    if (user) {
      localStorage.setItem('bds_auth_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('bds_auth_user');
    }
  }, [user]);

  /**
   * Đăng nhập: Chỉ nhận email + mật khẩu.
   * Backend (giả lập) tự xác định role dựa trên tài khoản trong DB.
   */
  const login = async (email: string, password: string): Promise<{ success: boolean; error?: string }> => {
    // Giả lập gọi API: POST /api/v1/auth/login
    await new Promise((r) => setTimeout(r, 600)); // Simulate network delay

    const allAccounts = [...SEED_ACCOUNTS, ...getRegisteredAccounts()];
    const found = allAccounts.find(
      (acc) => acc.email.toLowerCase() === email.toLowerCase() && acc.password === password
    );

    if (!found) {
      return { success: false, error: 'Email hoặc mật khẩu không chính xác.' };
    }

    const newUser: AuthUser = {
      id: `usr-${found.role.toLowerCase()}-${Date.now().toString(36)}`,
      name: found.name,
      email: found.email,
      role: found.role,
      roleLabel: ROLE_LABELS[found.role],
      phone: found.phone,
      avatarInitial: found.name.charAt(0).toUpperCase(),
    };
    setUser(newUser);
    setIsLoginModalOpen(false);
    return { success: true };
  };

  /**
   * Đăng ký: Chỉ cho phép chọn BROKER hoặc USER.
   * Admin/Moderator phải do Super Admin tạo trong trang quản trị.
   */
  const register = async (
    email: string,
    password: string,
    name: string,
    accountType: 'BROKER' | 'USER'
  ): Promise<{ success: boolean; error?: string }> => {
    // Giả lập gọi API: POST /api/v1/auth/register
    await new Promise((r) => setTimeout(r, 800));

    const allAccounts = [...SEED_ACCOUNTS, ...getRegisteredAccounts()];
    if (allAccounts.some((acc) => acc.email.toLowerCase() === email.toLowerCase())) {
      return { success: false, error: 'Email đã được sử dụng bởi tài khoản khác.' };
    }

    const newAccount = {
      email,
      password,
      name,
      role: accountType,
      phone: undefined,
    };

    saveRegisteredAccount(newAccount);

    // Tự động đăng nhập sau đăng ký
    const newUser: AuthUser = {
      id: `usr-${accountType.toLowerCase()}-${Date.now().toString(36)}`,
      name,
      email,
      role: accountType,
      roleLabel: ROLE_LABELS[accountType],
      avatarInitial: name.charAt(0).toUpperCase(),
    };
    setUser(newUser);
    setIsLoginModalOpen(false);
    return { success: true };
  };

  const logout = () => {
    setUser(null);
    setIsLoginModalOpen(false);
  };

  const isAuthenticated = !!user;
  const isAdminOrModerator = user?.role === 'ADMIN' || user?.role === 'MODERATOR';
  const isBroker = user?.role === 'BROKER' || user?.role === 'ADMIN';

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        isAdminOrModerator,
        isBroker,
        login,
        register,
        logout,
        isLoginModalOpen,
        setIsLoginModalOpen,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
