'use client';

import { CheckCircle, Loader2, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type VerifyStatus = 'loading' | 'success' | 'error';

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [status, setStatus] = useState<VerifyStatus>(token ? 'loading' : 'error');
  const [errorMessage, setErrorMessage] = useState(token ? '' : '認証トークンが見つかりません');

  useEffect(() => {
    if (!token) return;

    fetch('/api/auth/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    })
      .then((res) => {
        if (res.ok) {
          setStatus('success');
        } else {
          setStatus('error');
          setErrorMessage('認証トークンが無効または期限切れです');
        }
      })
      .catch(() => {
        setStatus('error');
        setErrorMessage('サーバーに接続できません。もう一度お試しください。');
      });
  }, [token]);

  return (
    <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <CardTitle>メール認証</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {status === 'loading' && (
            <div className="flex flex-col items-center gap-4 py-8">
              <Loader2 className="text-primary h-12 w-12 animate-spin" />
              <p className="text-muted-foreground">認証を確認中...</p>
            </div>
          )}
          {status === 'success' && (
            <div className="flex flex-col items-center gap-4 py-8">
              <CheckCircle className="h-12 w-12 text-green-500" />
              <p className="text-lg font-semibold">メール認証が完了しました</p>
              <p className="text-muted-foreground">ログインしてサービスをご利用ください</p>
              <Link href="/login" className={buttonVariants({ variant: 'default' })}>
                ログインへ
              </Link>
            </div>
          )}
          {status === 'error' && (
            <div className="flex flex-col items-center gap-4 py-8">
              <XCircle className="text-destructive h-12 w-12" />
              <p className="text-lg font-semibold">認証に失敗しました</p>
              <p className="text-muted-foreground">{errorMessage}</p>
              <Link href="/login" className={buttonVariants({ variant: 'outline' })}>
                ログインページへ戻る
              </Link>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin" />
        </div>
      }
    >
      <VerifyEmailContent />
    </Suspense>
  );
}
