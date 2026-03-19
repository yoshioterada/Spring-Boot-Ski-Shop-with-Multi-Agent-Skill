import { create } from 'zustand';

interface AuthState {
  userId: string | null;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  role: 'ADMIN' | 'MANAGER' | 'STAFF' | 'EMPLOYEE' | 'USER' | 'CUSTOMER' | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isManager: boolean;
  setUser: (user: {
    userId: string;
    email: string;
    firstName: string;
    lastName: string;
    role: 'ADMIN' | 'MANAGER' | 'STAFF' | 'EMPLOYEE' | 'USER' | 'CUSTOMER';
  }) => void;
  clearUser: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  userId: null,
  email: null,
  firstName: null,
  lastName: null,
  role: null,
  isAuthenticated: false,
  isAdmin: false,
  isManager: false,
  setUser: (user) =>
    set({
      userId: user.userId,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      role: user.role,
      isAuthenticated: true,
      isAdmin: user.role === 'ADMIN',
      isManager: user.role === 'MANAGER' || user.role === 'ADMIN',
    }),
  clearUser: () =>
    set({
      userId: null,
      email: null,
      firstName: null,
      lastName: null,
      role: null,
      isAuthenticated: false,
      isAdmin: false,
      isManager: false,
    }),
}));
