import CredentialsProvider from 'next-auth/providers/credentials';

import type { NextAuthOptions } from 'next-auth';

const API_BASE_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export const authOptions: NextAuthOptions = {
  providers: [
    CredentialsProvider({
      name: 'credentials',
      credentials: {
        email: { label: 'Email', type: 'email' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) {
          return null;
        }

        try {
          const loginUrl = `${API_BASE_URL}/api/v1/auth/login`;
          const res = await fetch(loginUrl, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Connection: 'close',
            },
            body: JSON.stringify({
              email: credentials.email,
              password: credentials.password,
            }),
          });

          if (!res.ok) {
            return null;
          }

          const text = await res.text();
          const data = JSON.parse(text);
          return {
            id: data.userId,
            email: data.email,
            name: `${data.firstName} ${data.lastName}`,
            firstName: data.firstName,
            lastName: data.lastName,
            role: data.role,
            accessToken: data.accessToken,
            refreshToken: data.refreshToken,
            expiresAt: data.expiresAt,
          };
        } catch (error) {
          console.error('[NextAuth] authorize error:', error);
          return null;
        }
      },
    }),
  ],
  session: {
    strategy: 'jwt',
    maxAge: 7 * 24 * 60 * 60, // 7 days
  },
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.userId = user.id;
        token.email = user.email ?? '';
        token.firstName = user.firstName;
        token.lastName = user.lastName;
        token.role = user.role;
        token.accessToken = user.accessToken;
        token.refreshToken = user.refreshToken;
        token.expiresAt = user.expiresAt;
      }

      // Refresh token if access token is about to expire (5 min before)
      const expiresAt = new Date(token.expiresAt as string).getTime();
      const fiveMinBefore = expiresAt - 5 * 60 * 1000;
      if (Date.now() > fiveMinBefore && token.refreshToken) {
        try {
          const res = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Connection: 'close',
            },
            body: JSON.stringify({ refreshToken: token.refreshToken }),
          });

          if (res.ok) {
            const text = await res.text();
            const refreshed = JSON.parse(text);
            token.accessToken = refreshed.accessToken;
            token.refreshToken = refreshed.refreshToken;
            token.expiresAt = refreshed.expiresAt;
          } else {
            // Refresh failed - mark token as expired
            token.error = 'RefreshTokenError';
          }
        } catch {
          token.error = 'RefreshTokenError';
        }
      }

      return token;
    },
    async session({ session, token }) {
      session.user = {
        ...session.user,
        id: token.userId,
        email: token.email,
        firstName: token.firstName,
        lastName: token.lastName,
        role: token.role,
      };
      session.accessToken = token.accessToken;
      session.error = token.error;
      return session;
    },
  },
  pages: {
    signIn: '/login',
    error: '/login',
  },
  cookies: {
    sessionToken: {
      name: 'azure-skishop.session-token',
      options: {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'strict',
        path: '/',
      },
    },
  },
};
