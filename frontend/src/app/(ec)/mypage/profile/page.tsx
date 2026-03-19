'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, KeyRound, Loader2, Save, Settings, Shield, User } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/hooks/use-auth';

// --- Schemas ---

const profileSchema = z.object({
  firstName: z.string().min(1, '名を入力してください').max(50, '50文字以内で入力してください'),
  lastName: z.string().min(1, '姓を入力してください').max(50, '50文字以内で入力してください'),
  phone: z
    .string()
    .regex(/^0\d{9,10}$/, '正しい電話番号を入力してください（例: 09012345678）')
    .or(z.literal('')),
  address: z.string().max(200, '200文字以内で入力してください'),
});

type ProfileFormData = z.infer<typeof profileSchema>;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, '現在のパスワードを入力してください'),
    newPassword: z
      .string()
      .min(8, 'パスワードは8文字以上で入力してください')
      .max(128, 'パスワードは128文字以内で入力してください')
      .regex(/[a-z]/, '小文字を含めてください')
      .regex(/[A-Z]/, '大文字を含めてください')
      .regex(/[0-9]/, '数字を含めてください'),
    confirmPassword: z.string().min(1, '確認用パスワードを入力してください'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'パスワードが一致しません',
    path: ['confirmPassword'],
  });

type PasswordFormData = z.infer<typeof passwordSchema>;

// --- Notification Preferences (localStorage) ---

interface NotificationPrefs {
  orderUpdates: boolean;
  promotions: boolean;
  pointExpiry: boolean;
}

const NOTIF_STORAGE_KEY = 'ski-shop-notification-prefs';

function loadNotificationPrefs(): NotificationPrefs {
  if (typeof window === 'undefined') {
    return { orderUpdates: true, promotions: false, pointExpiry: true };
  }
  try {
    const stored = localStorage.getItem(NOTIF_STORAGE_KEY);
    if (stored) return JSON.parse(stored) as NotificationPrefs;
  } catch {
    /* ignore */
  }
  return { orderUpdates: true, promotions: false, pointExpiry: true };
}

function saveNotificationPrefs(prefs: NotificationPrefs) {
  localStorage.setItem(NOTIF_STORAGE_KEY, JSON.stringify(prefs));
}

// --- Profile Tab ---

function ProfileTab() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: '', lastName: '', phone: '', address: '' },
  });

  useEffect(() => {
    async function fetchProfile() {
      try {
        const res = await fetch('/api/users/me');
        if (res.ok) {
          const data = await res.json();
          reset({
            firstName: data.firstName ?? '',
            lastName: data.lastName ?? '',
            phone: data.phone ?? '',
            address: data.address ?? '',
          });
        }
      } catch {
        toast.error('プロフィールの取得に失敗しました');
      } finally {
        setIsLoading(false);
      }
    }
    fetchProfile();
  }, [reset]);

  const onSubmit = useCallback(
    async (data: ProfileFormData) => {
      setIsSaving(true);
      try {
        const res = await fetch('/api/users/me', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
        });
        if (res.ok) {
          toast.success('プロフィールを更新しました');
          reset(data);
        } else {
          const err = await res.json().catch(() => null);
          toast.error(err?.detail ?? 'プロフィールの更新に失敗しました');
        }
      } catch {
        toast.error('プロフィールの更新に失敗しました');
      } finally {
        setIsSaving(false);
      }
    },
    [reset],
  );

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>プロフィール情報</CardTitle>
        <CardDescription>アカウントの基本情報を管理します</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          {/* Email (read-only) */}
          <div className="space-y-2">
            <Label htmlFor="email">メールアドレス</Label>
            <Input id="email" type="email" value={user?.email ?? ''} disabled />
            <p className="text-muted-foreground text-xs">メールアドレスは変更できません</p>
          </div>

          <Separator />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="lastName">姓</Label>
              <Input
                id="lastName"
                placeholder="山田"
                aria-invalid={!!errors.lastName}
                {...register('lastName')}
              />
              {errors.lastName && (
                <p className="text-destructive text-xs">{errors.lastName.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="firstName">名</Label>
              <Input
                id="firstName"
                placeholder="太郎"
                aria-invalid={!!errors.firstName}
                {...register('firstName')}
              />
              {errors.firstName && (
                <p className="text-destructive text-xs">{errors.firstName.message}</p>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="phone">電話番号</Label>
            <Input
              id="phone"
              type="tel"
              placeholder="09012345678"
              aria-invalid={!!errors.phone}
              {...register('phone')}
            />
            {errors.phone && <p className="text-destructive text-xs">{errors.phone.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="address">住所</Label>
            <Input
              id="address"
              placeholder="東京都渋谷区..."
              aria-invalid={!!errors.address}
              {...register('address')}
            />
            {errors.address && <p className="text-destructive text-xs">{errors.address.message}</p>}
          </div>

          <div className="flex justify-end">
            <Button type="submit" disabled={isSaving || !isDirty} size="lg">
              {isSaving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
              保存
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// --- Password Tab ---

function PasswordTab() {
  const [isSaving, setIsSaving] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PasswordFormData>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = useCallback(
    async (data: PasswordFormData) => {
      setIsSaving(true);
      try {
        const res = await fetch('/api/users/me/password', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            currentPassword: data.currentPassword,
            newPassword: data.newPassword,
          }),
        });
        if (res.ok) {
          toast.success('パスワードを変更しました');
          reset();
        } else {
          const err = await res.json().catch(() => null);
          if (err?.errorCode === 'INVALID_PASSWORD') {
            toast.error('現在のパスワードが正しくありません');
          } else {
            toast.error(err?.detail ?? 'パスワードの変更に失敗しました');
          }
        }
      } catch {
        toast.error('パスワードの変更に失敗しました');
      } finally {
        setIsSaving(false);
      }
    },
    [reset],
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle>パスワード変更</CardTitle>
        <CardDescription>
          安全なパスワードを設定してください（8文字以上、大文字・小文字・数字を含む）
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="currentPassword">現在のパスワード</Label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              aria-invalid={!!errors.currentPassword}
              {...register('currentPassword')}
            />
            {errors.currentPassword && (
              <p className="text-destructive text-xs">{errors.currentPassword.message}</p>
            )}
          </div>

          <Separator />

          <div className="space-y-2">
            <Label htmlFor="newPassword">新しいパスワード</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!errors.newPassword}
              {...register('newPassword')}
            />
            {errors.newPassword && (
              <p className="text-destructive text-xs">{errors.newPassword.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="confirmPassword">新しいパスワード（確認）</Label>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!errors.confirmPassword}
              {...register('confirmPassword')}
            />
            {errors.confirmPassword && (
              <p className="text-destructive text-xs">{errors.confirmPassword.message}</p>
            )}
          </div>

          <div className="flex justify-end">
            <Button type="submit" disabled={isSaving} size="lg">
              {isSaving ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <KeyRound className="size-4" />
              )}
              パスワードを変更
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// --- Settings Tab ---

function SettingsTab() {
  const [prefs, setPrefs] = useState<NotificationPrefs>(loadNotificationPrefs);

  const updatePref = useCallback(
    (key: keyof NotificationPrefs, checked: boolean) => {
      const next = { ...prefs, [key]: checked };
      setPrefs(next);
      saveNotificationPrefs(next);
      toast.success('通知設定を更新しました');
    },
    [prefs],
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>通知設定</CardTitle>
          <CardDescription>メール通知の受信設定を管理します</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>注文状況の通知</Label>
              <p className="text-muted-foreground text-xs">注文の確認・発送・配達に関する通知</p>
            </div>
            <Switch
              checked={prefs.orderUpdates}
              onCheckedChange={(checked) => updatePref('orderUpdates', checked)}
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>プロモーション通知</Label>
              <p className="text-muted-foreground text-xs">セール・キャンペーン情報のお知らせ</p>
            </div>
            <Switch
              checked={prefs.promotions}
              onCheckedChange={(checked) => updatePref('promotions', checked)}
            />
          </div>
          <Separator />
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>ポイント有効期限の通知</Label>
              <p className="text-muted-foreground text-xs">ポイントの有効期限が近づいた際の通知</p>
            </div>
            <Switch
              checked={prefs.pointExpiry}
              onCheckedChange={(checked) => updatePref('pointExpiry', checked)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>レコメンド履歴</CardTitle>
          <CardDescription>AI によるおすすめ商品の履歴</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm">Phase 7 で実装予定</p>
        </CardContent>
      </Card>
    </div>
  );
}

// --- Account Tab ---

function AccountTab() {
  const { logout } = useAuth();
  const router = useRouter();
  const [step, setStep] = useState<'idle' | 'confirm' | 'delete-input'>('idle');
  const [deleteInput, setDeleteInput] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  const handleFirstConfirm = useCallback(() => {
    setStep('delete-input');
  }, []);

  const handleFinalDelete = useCallback(async () => {
    setIsDeleting(true);
    try {
      const res = await fetch('/api/users/me', { method: 'DELETE' });
      if (res.ok) {
        toast.success('アカウントを削除しました');
        await logout();
        router.push('/');
      } else {
        toast.error('アカウントの削除に失敗しました');
      }
    } catch {
      toast.error('アカウントの削除に失敗しました');
    } finally {
      setIsDeleting(false);
      setStep('idle');
      setDeleteInput('');
    }
  }, [logout, router]);

  const closeAll = useCallback(() => {
    setStep('idle');
    setDeleteInput('');
  }, []);

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="text-destructive">アカウント削除</CardTitle>
          <CardDescription>
            アカウントを削除すると、すべてのデータが完全に削除され、復元できません。
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="bg-destructive/10 text-destructive rounded-lg p-4 text-sm">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <div>
                <p className="font-medium">この操作は取り消せません</p>
                <ul className="mt-1 list-inside list-disc space-y-1 text-xs">
                  <li>注文履歴・ポイント・クーポンがすべて失われます</li>
                  <li>同じメールアドレスでの再登録はできません</li>
                </ul>
              </div>
            </div>
          </div>
          <Button variant="destructive" onClick={() => setStep('confirm')} size="lg">
            <AlertTriangle className="size-4" />
            アカウントを削除
          </Button>
        </CardContent>
      </Card>

      {/* Step 1: ConfirmDialog */}
      <ConfirmDialog
        open={step === 'confirm'}
        onOpenChange={(open) => {
          if (!open) closeAll();
        }}
        title="本当にアカウントを削除しますか？"
        description="この操作は取り消せません。すべてのデータが完全に削除されます。"
        confirmLabel="次へ進む"
        cancelLabel="キャンセル"
        onConfirm={handleFirstConfirm}
        variant="destructive"
      />

      {/* Step 2: Type DELETE to confirm */}
      {step === 'delete-input' && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center">
          <div className="bg-background w-full max-w-md rounded-xl border p-6 shadow-lg">
            <h3 className="text-lg font-semibold">アカウント削除の最終確認</h3>
            <p className="text-muted-foreground mt-2 text-sm">
              確認のため <span className="font-mono font-bold">DELETE</span> と入力してください
            </p>
            <Input
              className="mt-4"
              placeholder="DELETE"
              value={deleteInput}
              onChange={(e) => setDeleteInput(e.target.value)}
              autoComplete="off"
            />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="outline" onClick={closeAll}>
                キャンセル
              </Button>
              <Button
                variant="destructive"
                disabled={deleteInput !== 'DELETE' || isDeleting}
                onClick={handleFinalDelete}
              >
                {isDeleting && <Loader2 className="size-4 animate-spin" />}
                完全に削除する
              </Button>
            </div>
          </div>
          <div
            className="fixed inset-0 -z-10 bg-black/50"
            onClick={closeAll}
            onKeyDown={() => {}}
            role="presentation"
          />
        </div>
      )}
    </>
  );
}

// --- Page ---

export default function ProfilePage() {
  return (
    <div className="container mx-auto max-w-3xl px-4 py-8">
      <Breadcrumb
        items={[{ label: 'マイページ', href: '/mypage' as never }, { label: 'プロフィール' }]}
      />

      <h1 className="mt-4 text-2xl font-bold">プロフィール設定</h1>
      <p className="text-muted-foreground mt-1 text-sm">アカウント情報の確認・変更ができます</p>

      <div className="mt-6">
        <Tabs defaultValue="profile">
          <TabsList>
            <TabsTrigger value="profile">
              <User className="size-4" />
              プロフィール
            </TabsTrigger>
            <TabsTrigger value="password">
              <KeyRound className="size-4" />
              パスワード変更
            </TabsTrigger>
            <TabsTrigger value="settings">
              <Settings className="size-4" />
              設定
            </TabsTrigger>
            <TabsTrigger value="account">
              <Shield className="size-4" />
              アカウント
            </TabsTrigger>
          </TabsList>

          <TabsContent value="profile" className="mt-6">
            <ProfileTab />
          </TabsContent>

          <TabsContent value="password" className="mt-6">
            <PasswordTab />
          </TabsContent>

          <TabsContent value="settings" className="mt-6">
            <SettingsTab />
          </TabsContent>

          <TabsContent value="account" className="mt-6">
            <AccountTab />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
