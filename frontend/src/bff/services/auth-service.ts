import { apiClient } from '@/lib/api-client';

import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types/api';

export const authService = {
  async login(data: LoginRequest) {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/login', data);
    return res.data;
  },

  async register(data: RegisterRequest) {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/register', data);
    return res.data;
  },

  async refresh(refreshToken: string) {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken });
    return res.data;
  },

  async verifyEmail(token: string) {
    const res = await apiClient.post<void>('/api/v1/auth/verify-email', { token });
    return res.data;
  },

  async forgotPassword(email: string) {
    const res = await apiClient.post<void>('/api/v1/auth/forgot-password', { email });
    return res.data;
  },

  async resetPassword(token: string, newPassword: string) {
    const res = await apiClient.post<void>('/api/v1/auth/reset-password', { token, newPassword });
    return res.data;
  },
};
