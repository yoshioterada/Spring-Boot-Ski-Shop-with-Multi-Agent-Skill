'use client';

import { useEffect, useState } from 'react';

interface DegradedSectionProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
  isError?: boolean;
  onRetry?: () => Promise<void>;
  retryInterval?: number;
}

export function DegradedSection({
  children,
  fallback = null,
  isError = false,
  onRetry,
  retryInterval = 30_000,
}: DegradedSectionProps) {
  const [showContent, setShowContent] = useState(!isError);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Syncing prop to local state for retry override
    setShowContent(!isError);
  }, [isError]);

  // Auto-retry in background
  useEffect(() => {
    if (!isError || !onRetry) return;

    const timer = setInterval(async () => {
      try {
        await onRetry();
        setShowContent(true);
      } catch {
        // Silently retry
      }
    }, retryInterval);

    return () => clearInterval(timer);
  }, [isError, onRetry, retryInterval]);

  if (!showContent) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
