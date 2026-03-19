export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface AuthResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'CUSTOMER' | 'ADMIN' | 'MANAGER';
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
}

export interface PasswordResetRequest {
  token: string;
  newPassword: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export type UserRole = 'CUSTOMER' | 'ADMIN' | 'MANAGER';
