'use client';

import { AlertTriangle, Copy, RefreshCw } from 'lucide-react';
import { useCallback, useState } from 'react';

import { Button } from '@/components/ui/button';

interface ErrorPlaceholderProps {
  title?: string;
  message: string;
  correlationId?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorPlaceholder({
  title = 'エラーが発生しました',
  message,
  correlationId,
  onRetry,
  className = '',
}: ErrorPlaceholderProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    if (correlationId) {
      await navigator.clipboard.writeText(correlationId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }, [correlationId]);

  return (
    <div className={`flex flex-col items-center justify-center gap-4 p-8 text-center ${className}`}>
      <AlertTriangle className="text-destructive h-12 w-12" />
      <h3 className="text-lg font-semibold">{title}</h3>
      <p className="text-muted-foreground max-w-md">{message}</p>
      {correlationId && (
        <div className="bg-muted flex items-center gap-2 rounded-md px-3 py-2 text-sm">
          <span className="text-muted-foreground">Request ID:</span>
          <code className="font-mono text-xs">{correlationId}</code>
          <Button variant="ghost" size="sm" onClick={handleCopy} className="h-6 w-6 p-0">
            <Copy className="h-3 w-3" />
          </Button>
          {copied && <span className="text-xs text-green-600">コピーしました</span>}
        </div>
      )}
      {correlationId && (
        <p className="text-muted-foreground text-xs">このIDをカスタマーサポートにお伝えください</p>
      )}
      {onRetry && (
        <Button onClick={onRetry} variant="outline" className="gap-2">
          <RefreshCw className="h-4 w-4" />
          再試行
        </Button>
      )}
    </div>
  );
}
