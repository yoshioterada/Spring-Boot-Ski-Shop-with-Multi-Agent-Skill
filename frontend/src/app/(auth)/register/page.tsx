'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { Suspense, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const registerSchema = z
  .object({
    firstName: z
      .string()
      .min(1, '名を入力してください')
      .max(50, '名は50文字以内で入力してください'),
    lastName: z.string().min(1, '姓を入力してください').max(50, '姓は50文字以内で入力してください'),
    email: z.string().email('有効なメールアドレスを入力してください'),
    password: z
      .string()
      .min(8, 'パスワードは8文字以上で入力してください')
      .max(128, 'パスワードは128文字以内で入力してください')
      .regex(/[a-z]/, 'パスワードには小文字を含めてください')
      .regex(/[A-Z]/, 'パスワードには大文字を含めてください')
      .regex(/[0-9]/, 'パスワードには数字を含めてください'),
    confirmPassword: z.string().min(1, 'パスワード（確認）を入力してください'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'パスワードが一致しません',
    path: ['confirmPassword'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

interface PasswordStrength {
  score: number;
  label: string;
  color: string;
}

function getPasswordStrength(password: string): PasswordStrength {
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[a-z]/.test(password)) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;

  if (score <= 2) return { score: 1, label: '弱い', color: 'bg-red-500' };
  if (score <= 3) return { score: 2, label: '普通', color: 'bg-yellow-500' };
  if (score <= 4) return { score: 3, label: '強い', color: 'bg-blue-500' };
  return { score: 4, label: 'とても強い', color: 'bg-green-500' };
}

function PasswordStrengthIndicator({ password }: { password: string }) {
  const strength = useMemo(() => getPasswordStrength(password), [password]);

  if (!password) return null;

  return (
    <div className="space-y-1">
      <div className="flex gap-1">
        {[1, 2, 3, 4].map((level) => (
          <div
            key={level}
            className={`h-1.5 flex-1 rounded-full transition-colors ${
              level <= strength.score ? strength.color : 'bg-muted'
            }`}
          />
        ))}
      </div>
      <p className="text-muted-foreground text-xs">
        パスワード強度: <span className="font-medium">{strength.label}</span>
      </p>
    </div>
  );
}

function RegisterForm() {
  const [serverError, setServerError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    mode: 'onBlur',
  });

  const watchedPassword = watch('password', '');

  const onSubmit = async (data: RegisterFormData) => {
    setIsLoading(true);
    setServerError('');
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          firstName: data.firstName,
          lastName: data.lastName,
          email: data.email,
          password: data.password,
        }),
      });

      if (res.ok) {
        setIsSuccess(true);
        return;
      }

      const errorData = await res.json().catch(() => null);

      if (res.status === 409 || errorData?.errorCode === 'EMAIL_ALREADY_EXISTS') {
        setError('email', { message: 'このメールアドレスは既に登録されています' });
        return;
      }

      if (res.status === 400 && errorData?.errors) {
        const fieldErrors = errorData.errors as Array<{
          field?: string;
          message?: string;
        }>;
        for (const fieldError of fieldErrors) {
          if (fieldError.field && fieldError.message) {
            const fieldName = fieldError.field as keyof RegisterFormData;
            if (['firstName', 'lastName', 'email', 'password'].includes(fieldName)) {
              setError(fieldName, { message: fieldError.message });
            }
          }
        }
        return;
      }

      setServerError(errorData?.detail || '登録に失敗しました。もう一度お試しください。');
    } catch {
      setServerError('サービスに接続できません。しばらくしてからお試しください。');
    } finally {
      setIsLoading(false);
    }
  };

  if (isSuccess) {
    return (
      <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <div className="text-primary mb-2 text-2xl font-bold">Azure SkiShop</div>
            <CardTitle>認証メールを送信しました</CardTitle>
          </CardHeader>
          <CardContent className="text-center">
            <div className="bg-primary/10 mb-4 rounded-md p-4">
              <p className="text-foreground text-sm">
                確認メールを送信しました。メール内のリンクをクリックして認証を完了してください。
              </p>
            </div>
            <p className="text-muted-foreground text-sm">
              メールが届かない場合は、迷惑メールフォルダをご確認ください。
            </p>
          </CardContent>
          <CardFooter className="justify-center">
            <Link href="/login" className="text-primary text-sm hover:underline">
              ログインページへ戻る
            </Link>
          </CardFooter>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-200px)] items-center justify-center px-4 py-8">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="text-primary mb-2 text-2xl font-bold">Azure SkiShop</div>
          <CardTitle>新規登録</CardTitle>
          <CardDescription>アカウントを作成してください</CardDescription>
        </CardHeader>
        <CardContent>
          {serverError && (
            <div
              role="alert"
              className="bg-destructive/10 text-destructive mb-4 rounded-md p-3 text-sm"
            >
              {serverError}
            </div>
          )}
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="lastName">姓</Label>
                <Input
                  id="lastName"
                  placeholder="山田"
                  autoComplete="family-name"
                  {...register('lastName')}
                />
                {errors.lastName && (
                  <p role="alert" className="text-destructive text-sm">
                    {errors.lastName.message}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="firstName">名</Label>
                <Input
                  id="firstName"
                  placeholder="太郎"
                  autoComplete="given-name"
                  {...register('firstName')}
                />
                {errors.firstName && (
                  <p role="alert" className="text-destructive text-sm">
                    {errors.firstName.message}
                  </p>
                )}
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">メールアドレス</Label>
              <Input
                id="email"
                type="email"
                placeholder="example@mail.com"
                autoComplete="email"
                {...register('email')}
              />
              {errors.email && (
                <p role="alert" className="text-destructive text-sm">
                  {errors.email.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">パスワード</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                {...register('password')}
              />
              <PasswordStrengthIndicator password={watchedPassword} />
              {errors.password && (
                <p role="alert" className="text-destructive text-sm">
                  {errors.password.message}
                </p>
              )}
              <p className="text-muted-foreground text-xs">8文字以上、大文字・小文字・数字を含む</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">パスワード（確認）</Label>
              <Input
                id="confirmPassword"
                type="password"
                autoComplete="new-password"
                {...register('confirmPassword')}
              />
              {errors.confirmPassword && (
                <p role="alert" className="text-destructive text-sm">
                  {errors.confirmPassword.message}
                </p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? '登録中...' : '登録する'}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="justify-center">
          <p className="text-muted-foreground text-sm">
            既にアカウントをお持ちですか？{' '}
            <Link href="/login" className="text-primary hover:underline">
              ログイン
            </Link>
          </p>
        </CardFooter>
      </Card>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[calc(100vh-200px)] items-center justify-center">
          読み込み中...
        </div>
      }
    >
      <RegisterForm />
    </Suspense>
  );
}
