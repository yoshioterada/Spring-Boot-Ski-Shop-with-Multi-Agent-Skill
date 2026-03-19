import { describe, expect, it, vi } from 'vitest';

import { useAuthStore } from '@/stores/auth-store';

describe('useAuthStore', () => {
  it('initializes with unauthenticated state', () => {
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.userId).toBeNull();
    expect(state.email).toBeNull();
    expect(state.role).toBeNull();
    expect(state.isAdmin).toBe(false);
    expect(state.isManager).toBe(false);
  });

  it('setUser updates auth state correctly', () => {
    const { setUser } = useAuthStore.getState();
    setUser({
      userId: 'user-123',
      email: 'test@example.com',
      firstName: 'Test',
      lastName: 'User',
      role: 'CUSTOMER',
    });

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.userId).toBe('user-123');
    expect(state.email).toBe('test@example.com');
    expect(state.firstName).toBe('Test');
    expect(state.lastName).toBe('User');
    expect(state.role).toBe('CUSTOMER');
    expect(state.isAdmin).toBe(false);
    expect(state.isManager).toBe(false);
  });

  it('setUser with ADMIN role sets isAdmin and isManager', () => {
    const { setUser } = useAuthStore.getState();
    setUser({
      userId: 'admin-1',
      email: 'admin@example.com',
      firstName: 'Admin',
      lastName: 'User',
      role: 'ADMIN',
    });

    const state = useAuthStore.getState();
    expect(state.isAdmin).toBe(true);
    expect(state.isManager).toBe(true);
  });

  it('setUser with MANAGER role sets isManager but not isAdmin', () => {
    const { setUser } = useAuthStore.getState();
    setUser({
      userId: 'mgr-1',
      email: 'manager@example.com',
      firstName: 'Manager',
      lastName: 'User',
      role: 'MANAGER',
    });

    const state = useAuthStore.getState();
    expect(state.isAdmin).toBe(false);
    expect(state.isManager).toBe(true);
  });

  it('clearUser resets all auth state', () => {
    const { setUser, clearUser } = useAuthStore.getState();

    setUser({
      userId: 'user-123',
      email: 'test@example.com',
      firstName: 'Test',
      lastName: 'User',
      role: 'CUSTOMER',
    });

    clearUser();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.userId).toBeNull();
    expect(state.email).toBeNull();
    expect(state.firstName).toBeNull();
    expect(state.lastName).toBeNull();
    expect(state.role).toBeNull();
    expect(state.isAdmin).toBe(false);
    expect(state.isManager).toBe(false);
  });
});
