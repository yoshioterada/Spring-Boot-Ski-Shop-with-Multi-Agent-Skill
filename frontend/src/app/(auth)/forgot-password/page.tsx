'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, Mail } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const schema = z.object({
  email: z.string().email('有効なメールアドレスを入力してください'),
});

type FormData = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
  });

  const onSubmit = async (data: FormData) => {
    setIsLoading(true);
    setError('');
    try {
      const res = await fetch('/api/auth/password/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: data.email }),
      });
      if (res.ok || res.status === 202) {
        setIsSubmitted(true);
      } else {
        // Always show success to prevent email enumeration
        setIsSubmitted(true);
      }
    } catch {
      setError('サーバーに接続できません');
    } finally {
      setIsLoading(false);
    }
  };

  if (isSubmitted) {
    return (
      <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
        <Card className="w-full max-w-md text-center">
          <CardHeader>
            <div className="bg-primary/10 mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full">
              <Mail className="text-primary h-6 w-6" />
            </div>
            <CardTitle>メールを送信しました</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-muted-foreground">
              パスワードリセット用のリンクをメールアドレスに送信しました。メールを確認してください。
            </p>
            <Link href="/login" className="text-primary text-sm hover:underline">
              ログインページに戻る
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>パスワードをリセット</CardTitle>
          <CardDescription>登録済みのメールアドレスを入力してください</CardDescription>
        </CardHeader>
        <CardContent>
          {error && (
            <div className="bg-destructive/10 text-destructive mb-4 rounded-md p-3 text-sm">
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">メールアドレス</Label>
              <Input
                id="email"
                type="email"
                placeholder="example@mail.com"
                {...register('email')}
              />
              {errors.email && <p className="text-destructive text-sm">{errors.email.message}</p>}
            </div>
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? '送信中...' : 'リセットメールを送信'}
            </Button>
          </form>
          <div className="mt-4 text-center">
            <Link
              href="/login"
              className="text-muted-foreground hover:text-primary inline-flex items-center gap-1 text-sm"
            >
              <ArrowLeft className="h-4 w-4" />
              ログインに戻る
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
