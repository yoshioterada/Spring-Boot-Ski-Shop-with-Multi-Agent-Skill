'use client';

import { useEffect } from 'react';

import { ErrorPlaceholder } from '@/components/common/error-placeholder';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('Global error:', error);
  }, [error]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <ErrorPlaceholder
        title="予期しないエラーが発生しました"
        message="しばらくしてから再度お試しください"
        correlationId={error.digest}
        onRetry={reset}
      />
    </div>
  );
}
