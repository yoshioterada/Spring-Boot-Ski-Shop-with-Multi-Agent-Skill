'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SessionProvider } from 'next-auth/react';
import { useState } from 'react';

import { ToastProvider } from '@/components/common/toast-provider';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 5 * 60 * 1000, // 5 minutes
            gcTime: 10 * 60 * 1000, // 10 minutes
            retry: (failureCount, error) => {
              // Don't retry on 4xx errors (except 429)
              if (error instanceof Error && 'status' in error) {
                const status = (error as { status: number }).status;
                if (status >= 400 && status < 500 && status !== 429) {
                  return false;
                }
              }
              return failureCount < 3;
            },
            refetchOnWindowFocus: false,
          },
          mutations: {
            retry: false,
          },
        },
      }),
  );

  return (
    <SessionProvider>
      <QueryClientProvider client={queryClient}>
        {children}
        <ToastProvider />
      </QueryClientProvider>
    </SessionProvider>
  );
}
