import type { DefaultSession } from 'next-auth';

declare module 'next-auth' {
  interface Session extends DefaultSession {
    user: {
      id: string;
      email: string;
      firstName: string;
      lastName: string;
      role: 'CUSTOMER' | 'ADMIN' | 'MANAGER';
    } & DefaultSession['user'];
    accessToken: string;
    error?: string;
  }

  interface User {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: 'CUSTOMER' | 'ADMIN' | 'MANAGER';
    accessToken: string;
    refreshToken: string;
    expiresAt: string;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    userId: string;
    email: string;
    firstName: string;
    lastName: string;
    role: 'CUSTOMER' | 'ADMIN' | 'MANAGER';
    accessToken: string;
    refreshToken: string;
    expiresAt: string;
    error?: string;
  }
}
