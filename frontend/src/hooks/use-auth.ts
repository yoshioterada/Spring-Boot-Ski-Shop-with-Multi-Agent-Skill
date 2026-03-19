'use client';

import { useRouter } from 'next/navigation';
import { signIn, signOut, useSession } from 'next-auth/react';
import { useCallback, useEffect } from 'react';

import { useAuthStore } from '@/stores/auth-store';

import type { Route } from 'next';

export function useAuth() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const { setUser, clearUser, isAuthenticated, isAdmin, isManager } = useAuthStore();

  // Sync session to zustand store
  useEffect(() => {
    if (session?.user) {
      setUser({
        userId: session.user.id,
        email: session.user.email ?? '',
        firstName: session.user.firstName,
        lastName: session.user.lastName,
        role: session.user.role,
      });
    } else if (status === 'unauthenticated') {
      clearUser();
    }
  }, [session, status, setUser, clearUser]);

  // Handle refresh token errors
  useEffect(() => {
    if (session?.error === 'RefreshTokenError') {
      signOut({ redirect: false }).then(() => {
        router.push('/login');
      });
    }
  }, [session, router]);

  const login = useCallback(
    async (email: string, password: string, redirect?: string) => {
      const result = await signIn('credentials', {
        email,
        password,
        redirect: false,
      });

      if (result?.error) {
        throw new Error('メールアドレスまたはパスワードが正しくありません');
      }

      router.push((redirect || '/') as Route);
      router.refresh();
    },
    [router],
  );

  const logout = useCallback(async () => {
    clearUser();
    await signOut({ redirect: false });
    router.push('/');
    router.refresh();
  }, [clearUser, router]);

  return {
    user: session?.user ?? null,
    isLoading: status === 'loading',
    isAuthenticated,
    isAdmin,
    isManager,
    login,
    logout,
  };
}
