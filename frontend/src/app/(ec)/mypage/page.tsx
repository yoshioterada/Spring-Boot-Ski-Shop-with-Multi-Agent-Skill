'use client';

import { Gift, Package, Star, User } from 'lucide-react';
import Link from 'next/link';

import { Breadcrumb } from '@/components/layout/breadcrumb';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';

const quickLinks = [
  {
    href: '/mypage/orders',
    icon: Package,
    title: '注文履歴',
    description: '過去の注文を確認・追跡',
  },
  {
    href: '/mypage/profile',
    icon: User,
    title: 'プロフィール',
    description: 'アカウント情報の管理',
  },
  {
    href: '/mypage/points',
    icon: Star,
    title: 'ポイント',
    description: '保有ポイントの確認・利用',
  },
  {
    href: '/mypage/coupons',
    icon: Gift,
    title: 'クーポン',
    description: '利用可能なクーポンを確認',
  },
] as const;

export default function MyPage() {
  const { user } = useAuth();

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <Breadcrumb items={[{ label: 'マイページ' }]} />

      <div className="mt-6">
        <h1 className="text-2xl font-bold">
          {user ? `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim() : ''} さん、こんにちは
        </h1>
        <p className="text-muted-foreground mt-1 text-sm">
          マイページからアカウント情報や注文履歴を確認できます
        </p>
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        {quickLinks.map(({ href, icon: Icon, title, description }) => (
          <Card key={href} className="transition-shadow hover:shadow-md">
            <CardHeader>
              <div className="flex items-center gap-3">
                <div className="bg-primary/10 text-primary flex size-10 items-center justify-center rounded-lg">
                  <Icon className="size-5" />
                </div>
                <div>
                  <CardTitle>{title}</CardTitle>
                  <CardDescription>{description}</CardDescription>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <Link
                href={href as never}
                className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'w-full')}
              >
                詳細を見る
              </Link>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
