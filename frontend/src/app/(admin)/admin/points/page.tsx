'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import {
  AlertTriangle,
  Award,
  Clock,
  Gift,
  Loader2,
  Search,
  Star,
  Timer,
  Trophy,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDateTime, formatNumber, formatPoints } from '@/lib/format';

/* ---------- Types ---------- */

interface TierDefinition {
  id: string;
  name: string;
  level: string;
  minPoints: number;
  maxPoints: number;
  multiplier: number;
  benefits: string[];
}

interface UserPointResult {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  currentBalance: number;
  totalEarned: number;
  tierLevel: string;
  tierName: string;
  recentHistory: PointTransaction[];
}

interface PointTransaction {
  id: string;
  transactionType: string;
  amount: number;
  balanceAfter: number;
  description: string;
  createdAt: string;
}

/* ---------- Schemas ---------- */

const grantSchema = z.object({
  userIdentifier: z
    .string()
    .min(1, 'ユーザーID またはメールアドレスは必須です')
    .max(255, '255文字以内で入力してください'),
  points: z
    .number()
    .min(1, '1以上のポイント数を入力してください')
    .max(1000000, '上限を超えています'),
  reason: z.string().min(1, '付与理由は必須です').max(500, '500文字以内で入力してください'),
});

type GrantFormData = z.infer<typeof grantSchema>;

/* ---------- Tier badge ---------- */

const TIER_CONFIG: Record<string, { icon: typeof Star; className: string }> = {
  BRONZE: {
    icon: Award,
    className: 'border-amber-400 bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-400',
  },
  SILVER: {
    icon: Star,
    className: 'border-gray-400 bg-gray-50 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  },
  GOLD: {
    icon: Trophy,
    className:
      'border-yellow-400 bg-yellow-50 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-400',
  },
  PLATINUM: {
    icon: Trophy,
    className:
      'border-purple-400 bg-purple-50 text-purple-700 dark:bg-purple-950 dark:text-purple-400',
  },
};

function TierBadge({ level }: { level: string }) {
  const cfg = TIER_CONFIG[level];
  return (
    <Badge variant="outline" className={cfg?.className ?? ''}>
      {level}
    </Badge>
  );
}

const TX_TYPE_LABEL: Record<string, { label: string; className: string }> = {
  EARNED: {
    label: '獲得',
    className: 'text-green-600 dark:text-green-400',
  },
  REDEEMED: {
    label: '使用',
    className: 'text-blue-600 dark:text-blue-400',
  },
  EXPIRED: {
    label: '期限切れ',
    className: 'text-red-600 dark:text-red-400',
  },
  ADJUSTED: {
    label: '調整',
    className: 'text-orange-600 dark:text-orange-400',
  },
  TRANSFERRED: {
    label: '移行',
    className: 'text-purple-600 dark:text-purple-400',
  },
};

/* ---------- Main Page ---------- */

export default function AdminPointsPage() {
  // Tiers
  const [tiers, setTiers] = useState<TierDefinition[]>([]);
  const [tiersLoading, setTiersLoading] = useState(true);

  // Grant dialog
  const [grantOpen, setGrantOpen] = useState(false);
  const [grantConfirm, setGrantConfirm] = useState(false);
  const [grantData, setGrantData] = useState<GrantFormData | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Expire confirm
  const [expireConfirm, setExpireConfirm] = useState(false);

  // User search
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResult, setSearchResult] = useState<UserPointResult | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchPerformed, setSearchPerformed] = useState(false);

  /* --- Fetch tiers --- */

  const fetchTiers = useCallback(async () => {
    setTiersLoading(true);
    try {
      const res = await fetch('/api/admin/points/tiers');
      if (!res.ok) throw new Error('Failed');
      const data = await res.json();
      setTiers(Array.isArray(data) ? data : (data.tiers ?? []));
    } catch {
      setTiers([]);
      toast.error('ティア定義の取得に失敗しました');
    } finally {
      setTiersLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchTiers();
  }, [fetchTiers]);

  /* --- Grant handler --- */

  const handleGrantConfirm = async () => {
    if (!grantData) return;
    try {
      setSubmitting(true);
      const res = await fetch('/api/admin/points/grant', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(grantData),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(`${formatPoints(grantData.points)} を付与しました`);
      setGrantOpen(false);
      setGrantConfirm(false);
      setGrantData(null);
    } catch {
      toast.error('ポイント付与に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  /* --- Batch expire handler --- */

  const handleBatchExpire = async () => {
    try {
      setSubmitting(true);
      const res = await fetch('/api/admin/points/expire', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      if (!res.ok) throw new Error('Failed');
      const data = await res.json().catch(() => null);
      const expiredCount = data?.expiredCount ?? 0;
      toast.success(`期限切れポイントの一括失効を実行しました（${expiredCount}件処理）`);
      setExpireConfirm(false);
    } catch {
      toast.error('一括失効の実行に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  /* --- Search handler --- */

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setSearchLoading(true);
    setSearchPerformed(true);
    try {
      const params = new URLSearchParams({ query: searchQuery.trim() });
      const res = await fetch(`/api/admin/points/search?${params.toString()}`);
      if (!res.ok) {
        setSearchResult(null);
        if (res.status === 404) {
          toast.error('ユーザーが見つかりません');
        } else {
          throw new Error('Failed');
        }
        return;
      }
      const data: UserPointResult = await res.json();
      setSearchResult(data);
    } catch {
      setSearchResult(null);
      toast.error('ユーザー検索に失敗しました');
    } finally {
      setSearchLoading(false);
    }
  };

  /* --- Render --- */

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Gift className="h-6 w-6" />
          <h1 className="text-2xl font-bold">ポイント管理</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={() => setGrantOpen(true)}>
            <Gift className="mr-1 size-4" />
            手動ポイント付与
          </Button>
          <Button variant="destructive" onClick={() => setExpireConfirm(true)}>
            <Timer className="mr-1 size-4" />
            一括失効実行
          </Button>
        </div>
      </div>

      {/* Manual Point Grant Dialog */}
      <GrantPointsDialog
        open={grantOpen}
        onOpenChange={(open) => {
          if (!open) setGrantOpen(false);
        }}
        onSubmit={(data) => {
          setGrantData(data);
          setGrantConfirm(true);
        }}
      />

      {/* Grant confirm */}
      <ConfirmDialog
        open={grantConfirm}
        onOpenChange={(open) => {
          if (!open) {
            setGrantConfirm(false);
            setGrantData(null);
          }
        }}
        title="ポイント付与の確認"
        description={
          grantData
            ? `ユーザー「${grantData.userIdentifier}」に ${formatPoints(grantData.points)} を付与します。理由: ${grantData.reason}`
            : ''
        }
        confirmLabel={submitting ? '処理中...' : '付与する'}
        onConfirm={() => void handleGrantConfirm()}
      />

      {/* Expire confirm */}
      <ConfirmDialog
        open={expireConfirm}
        onOpenChange={(open) => {
          if (!open) setExpireConfirm(false);
        }}
        title="一括失効の確認"
        description="期限切れのポイントを一括で失効させます。この操作は取り消せません。本当に実行しますか？"
        confirmLabel={submitting ? '処理中...' : '一括失効を実行'}
        onConfirm={() => void handleBatchExpire()}
        variant="destructive"
      />

      {/* User search */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Search className="h-5 w-5" />
            ユーザーポイント検索
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-2">
            <div className="relative min-w-0 flex-1">
              <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
              <Input
                placeholder="メールアドレスまたはユーザーIDで検索..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleSearch();
                }}
                className="pl-9"
              />
            </div>
            <Button onClick={() => void handleSearch()} disabled={searchLoading}>
              {searchLoading && <Loader2 className="mr-1 size-4 animate-spin" />}
              検索
            </Button>
            {searchPerformed && (
              <Button
                variant="outline"
                onClick={() => {
                  setSearchQuery('');
                  setSearchResult(null);
                  setSearchPerformed(false);
                }}
              >
                クリア
              </Button>
            )}
          </div>

          {/* Search result */}
          {searchPerformed && !searchLoading && !searchResult && (
            <div className="mt-6 flex flex-col items-center py-8">
              <Search className="text-muted-foreground mb-3 h-10 w-10" />
              <p className="text-muted-foreground">ユーザーが見つかりませんでした</p>
            </div>
          )}

          {searchResult && (
            <div className="mt-6 space-y-4">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <Card>
                  <CardContent className="p-4">
                    <p className="text-muted-foreground text-xs">ユーザー</p>
                    <p className="mt-1 font-medium">
                      {searchResult.lastName} {searchResult.firstName}
                    </p>
                    <p className="text-muted-foreground text-xs">{searchResult.email}</p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-4">
                    <p className="text-muted-foreground text-xs">現在のポイント</p>
                    <p className="mt-1 text-xl font-bold">
                      {formatPoints(searchResult.currentBalance)}
                    </p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-4">
                    <p className="text-muted-foreground text-xs">累計獲得</p>
                    <p className="mt-1 text-xl font-bold">
                      {formatPoints(searchResult.totalEarned)}
                    </p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-4">
                    <p className="text-muted-foreground text-xs">会員ランク</p>
                    <div className="mt-1 flex items-center gap-2">
                      <TierBadge level={searchResult.tierLevel} />
                      <span className="text-sm font-medium">{searchResult.tierName}</span>
                    </div>
                  </CardContent>
                </Card>
              </div>

              {/* Recent history */}
              {searchResult.recentHistory?.length > 0 && (
                <>
                  <Separator />
                  <div>
                    <h3 className="mb-3 flex items-center gap-2 font-semibold">
                      <Clock className="h-4 w-4" />
                      最近の履歴
                    </h3>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>日時</TableHead>
                          <TableHead>種別</TableHead>
                          <TableHead>説明</TableHead>
                          <TableHead className="text-right">ポイント</TableHead>
                          <TableHead className="text-right">残高</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {searchResult.recentHistory.map((tx) => {
                          const txCfg = TX_TYPE_LABEL[tx.transactionType];
                          return (
                            <TableRow key={tx.id}>
                              <TableCell className="text-muted-foreground text-sm">
                                {formatDateTime(tx.createdAt)}
                              </TableCell>
                              <TableCell>
                                <span className={`text-sm font-medium ${txCfg?.className ?? ''}`}>
                                  {txCfg?.label ?? tx.transactionType}
                                </span>
                              </TableCell>
                              <TableCell className="max-w-[200px] truncate text-sm">
                                {tx.description}
                              </TableCell>
                              <TableCell className="text-right">
                                <span
                                  className={
                                    tx.amount > 0
                                      ? 'font-medium text-green-600'
                                      : 'font-medium text-red-600'
                                  }
                                >
                                  {tx.amount > 0 ? '+' : ''}
                                  {formatNumber(tx.amount)}
                                </span>
                              </TableCell>
                              <TableCell className="text-right text-sm">
                                {formatPoints(tx.balanceAfter)}
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </div>
                </>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Tier definitions table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Trophy className="h-5 w-5" />
            会員ティア定義
          </CardTitle>
        </CardHeader>
        <CardContent>
          {tiersLoading ? (
            <div className="flex justify-center py-8">
              <div className="border-primary h-8 w-8 animate-spin rounded-full border-2 border-t-transparent" />
            </div>
          ) : tiers.length === 0 ? (
            <div className="flex flex-col items-center py-8">
              <AlertTriangle className="text-muted-foreground mb-3 h-10 w-10" />
              <p className="text-muted-foreground">ティア定義が登録されていません</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ティア名</TableHead>
                  <TableHead>レベル</TableHead>
                  <TableHead className="text-right">最低ポイント</TableHead>
                  <TableHead className="text-right">最大ポイント</TableHead>
                  <TableHead className="text-right">倍率</TableHead>
                  <TableHead>特典</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tiers.map((tier) => (
                  <TableRow key={tier.id}>
                    <TableCell className="font-medium">{tier.name}</TableCell>
                    <TableCell>
                      <TierBadge level={tier.level} />
                    </TableCell>
                    <TableCell className="text-right">{formatNumber(tier.minPoints)}</TableCell>
                    <TableCell className="text-right">
                      {tier.maxPoints === -1 || tier.maxPoints === 0
                        ? '∞'
                        : formatNumber(tier.maxPoints)}
                    </TableCell>
                    <TableCell className="text-right font-medium">×{tier.multiplier}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {(Array.isArray(tier.benefits) ? tier.benefits : []).map((b) => (
                          <Badge key={b} variant="outline" className="text-xs">
                            {b}
                          </Badge>
                        ))}
                        {(!Array.isArray(tier.benefits) || tier.benefits.length === 0) && (
                          <span className="text-muted-foreground text-xs">—</span>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/* ---------- Grant Points Dialog ---------- */

function GrantPointsDialog({
  open,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: GrantFormData) => void;
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<GrantFormData>({
    resolver: zodResolver(grantSchema),
    defaultValues: { userIdentifier: '', points: 100, reason: '' },
  });

  useEffect(() => {
    if (open) {
      reset({ userIdentifier: '', points: 100, reason: '' });
    }
  }, [open, reset]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Gift className="h-5 w-5" />
            手動ポイント付与
          </DialogTitle>
          <DialogDescription>ユーザーにポイントを手動で付与します。</DialogDescription>
        </DialogHeader>

        <form
          onSubmit={handleSubmit((data) => {
            onSubmit(data);
            onOpenChange(false);
          })}
          className="space-y-4"
        >
          <div className="space-y-1.5">
            <Label htmlFor="grant-user">ユーザーID / メールアドレス *</Label>
            <Input
              id="grant-user"
              placeholder="user@example.com または ユーザーID"
              {...register('userIdentifier')}
            />
            {errors.userIdentifier && (
              <p className="text-xs text-red-500">{errors.userIdentifier.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="grant-points">ポイント数 *</Label>
            <Input
              id="grant-points"
              type="number"
              min={1}
              {...register('points', { valueAsNumber: true })}
            />
            {errors.points && <p className="text-xs text-red-500">{errors.points.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="grant-reason">付与理由 *</Label>
            <Input
              id="grant-reason"
              placeholder="例: キャンペーン特別ポイント"
              {...register('reason')}
            />
            {errors.reason && <p className="text-xs text-red-500">{errors.reason.message}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit">確認へ進む</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
