'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const schema = z
  .object({
    newPassword: z
      .string()
      .min(8, 'パスワードは8文字以上で入力してください')
      .max(128, 'パスワードは128文字以下で入力してください')
      .regex(/[A-Z]/, '大文字を1文字以上含めてください')
      .regex(/[a-z]/, '小文字を1文字以上含めてください')
      .regex(/[0-9]/, '数字を1文字以上含めてください'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'パスワードが一致しません',
    path: ['confirmPassword'],
  });

type FormData = z.infer<typeof schema>;

function ResetPasswordContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [isSuccess, setIsSuccess] = useState(false);
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

  if (!token) {
    return (
      <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
        <Card className="w-full max-w-md text-center">
          <CardContent className="py-8">
            <p className="text-destructive">無効なリセットリンクです。</p>
            <Link
              href="/forgot-password"
              className={buttonVariants({
                variant: 'outline',
                className: 'mt-4',
              })}
            >
              パスワードリセットを再リクエスト
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (isSuccess) {
    return (
      <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
        <Card className="w-full max-w-md text-center">
          <CardContent className="space-y-4 py-8">
            <CheckCircle className="mx-auto h-12 w-12 text-green-500" />
            <p className="text-lg font-semibold">パスワードを変更しました</p>
            <p className="text-muted-foreground">新しいパスワードでログインしてください</p>
            <Link href="/login" className={buttonVariants({ variant: 'default' })}>
              ログインへ
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  const onSubmit = async (data: FormData) => {
    setIsLoading(true);
    setError('');
    try {
      const res = await fetch('/api/auth/password/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword: data.newPassword }),
      });
      if (res.ok) {
        setIsSuccess(true);
      } else {
        const err = await res.json().catch(() => null);
        setError((err as { detail?: string })?.detail || 'パスワードの変更に失敗しました');
      }
    } catch {
      setError('サーバーに接続できません');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>新しいパスワードを設定</CardTitle>
          <CardDescription>新しいパスワードを入力してください</CardDescription>
        </CardHeader>
        <CardContent>
          {error && (
            <div className="bg-destructive/10 text-destructive mb-4 rounded-md p-3 text-sm">
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="newPassword">新しいパスワード</Label>
              <Input id="newPassword" type="password" {...register('newPassword')} />
              {errors.newPassword && (
                <p className="text-destructive text-sm">{errors.newPassword.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">パスワード確認</Label>
              <Input id="confirmPassword" type="password" {...register('confirmPassword')} />
              {errors.confirmPassword && (
                <p className="text-destructive text-sm">{errors.confirmPassword.message}</p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? '変更中...' : 'パスワードを変更'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={<div className="flex min-h-screen items-center justify-center">Loading...</div>}
    >
      <ResetPasswordContent />
    </Suspense>
  );
}
