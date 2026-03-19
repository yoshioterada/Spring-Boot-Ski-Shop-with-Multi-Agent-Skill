'use client';

import { CheckCircle } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect } from 'react';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';

function OrderCompleteContent() {
  const searchParams = useSearchParams();
  const orderId = searchParams.get('orderId') ?? '';

  useEffect(() => {
    // Prevent back-navigation to checkout
    history.replaceState(null, '', window.location.href);
  }, []);

  return (
    <div className="mx-auto max-w-lg px-4 py-16 text-center">
      <Card>
        <CardContent className="space-y-6 pt-8">
          <div className="flex justify-center">
            <CheckCircle className="size-16 text-green-500" />
          </div>

          <div className="space-y-2">
            <h1 className="text-2xl font-bold">ご注文ありがとうございます</h1>
            <p className="text-muted-foreground">ご注文が正常に完了しました</p>
          </div>

          {orderId && (
            <>
              <Separator />
              <div className="space-y-1">
                <p className="text-muted-foreground text-sm">注文番号</p>
                <p className="font-mono text-lg font-semibold">{orderId}</p>
              </div>
            </>
          )}

          <Separator />

          <p className="text-muted-foreground text-sm">
            ご登録のメールアドレスに確認メールを送信しました。
            <br />
            メールが届かない場合は、迷惑メールフォルダをご確認ください。
          </p>

          <div className="flex flex-col gap-3 pt-2">
            <Link href="/mypage/orders" className={buttonVariants({ variant: 'default' })}>
              注文履歴を見る
            </Link>
            <Link href="/catalog" className={buttonVariants({ variant: 'outline' })}>
              買い物を続ける
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export default function OrderCompletePage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[50vh] items-center justify-center">
          <div className="border-muted-foreground size-8 animate-spin rounded-full border-4 border-t-transparent" />
        </div>
      }
    >
      <OrderCompleteContent />
    </Suspense>
  );
}
