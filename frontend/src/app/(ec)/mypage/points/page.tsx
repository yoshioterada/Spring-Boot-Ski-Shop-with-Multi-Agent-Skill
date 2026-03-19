'use client';

import { format } from 'date-fns';
import { ja } from 'date-fns/locale';
import {
  AlertTriangleIcon,
  ArrowDownIcon,
  ArrowUpIcon,
  CoinsIcon,
  Loader2Icon,
  MinusIcon,
  SendIcon,
  TrophyIcon,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress, ProgressLabel, ProgressValue } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

import type {
  PointBalanceResponse,
  PointTransactionResponse,
  UserTierResponse,
} from '@/types/api/point';

interface ExpiringPoint {
  amount: number;
  expiresAt: string;
}

interface PointsDashboard {
  balance: PointBalanceResponse | null;
  tier: UserTierResponse | null;
  history: { content: PointTransactionResponse[] } | null;
  expiring: ExpiringPoint[] | null;
}

const TIER_THRESHOLDS: Record<string, number> = {
  BRONZE: 0,
  SILVER: 1000,
  GOLD: 5000,
  PLATINUM: 20000,
};

const TIER_ORDER = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'] as const;

function getTierProgress(tier: UserTierResponse) {
  const currentIndex = TIER_ORDER.indexOf(tier.tierLevel);
  if (currentIndex === TIER_ORDER.length - 1) {
    return {
      current: tier.totalEarned,
      max: tier.totalEarned,
      nextTierName: null,
      percentage: 100,
    };
  }
  const nextTier = TIER_ORDER[currentIndex + 1];
  const nextThreshold = TIER_THRESHOLDS[nextTier];
  const currentThreshold = TIER_THRESHOLDS[tier.tierLevel];
  const progress = tier.totalEarned - currentThreshold;
  const needed = nextThreshold - currentThreshold;
  return {
    current: progress,
    max: needed,
    nextTierName: nextTier,
    percentage: Math.min(Math.round((progress / needed) * 100), 100),
  };
}

function TransactionTypeBadge({ type }: { type: PointTransactionResponse['transactionType'] }) {
  switch (type) {
    case 'EARNED':
      return (
        <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
          獲得
        </Badge>
      );
    case 'REDEEMED':
      return (
        <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
          利用
        </Badge>
      );
    case 'EXPIRED':
      return <Badge variant="destructive">失効</Badge>;
    case 'ADJUSTED':
      return <Badge variant="secondary">調整</Badge>;
    case 'TRANSFERRED':
      return <Badge variant="outline">送付</Badge>;
    default:
      return <Badge variant="secondary">{type}</Badge>;
  }
}

export default function PointsHistoryPage() {
  const [dashboard, setDashboard] = useState<PointsDashboard | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [filteredHistory, setFilteredHistory] = useState<PointTransactionResponse[] | null>(null);
  const [isFiltering, setIsFiltering] = useState(false);

  // Transfer form
  const [recipientEmail, setRecipientEmail] = useState('');
  const [transferAmount, setTransferAmount] = useState('');
  const [isTransferring, setIsTransferring] = useState(false);
  const [showTransferConfirm, setShowTransferConfirm] = useState(false);
  const [transferError, setTransferError] = useState('');

  const fetchDashboard = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetch('/api/points/dashboard');
      if (!res.ok) throw new Error('Failed to fetch');
      const data: PointsDashboard = await res.json();
      setDashboard(data);
    } catch {
      toast.error('ポイント情報の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchDashboard();
  }, [fetchDashboard]);

  const handleDateFilter = async () => {
    if (!dateFrom || !dateTo) return;
    setIsFiltering(true);
    try {
      const res = await fetch(`/api/points/history/range?from=${dateFrom}&to=${dateTo}`);
      if (!res.ok) throw new Error('Failed');
      const data = await res.json();
      setFilteredHistory(data.content ?? data);
    } catch {
      toast.error('履歴の取得に失敗しました');
    } finally {
      setIsFiltering(false);
    }
  };

  const clearDateFilter = () => {
    setDateFrom('');
    setDateTo('');
    setFilteredHistory(null);
  };

  const handleTransfer = async () => {
    setShowTransferConfirm(false);
    setIsTransferring(true);
    setTransferError('');
    try {
      const res = await fetch('/api/points/transfer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipientEmail,
          amount: Number(transferAmount),
        }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) {
        if (data?.errorCode === 'PNT-4224') {
          setTransferError('自分自身にポイントを送ることはできません');
        } else {
          setTransferError(data?.message ?? 'ポイント送付に失敗しました');
        }
        return;
      }
      toast.success('ポイントを送付しました');
      setRecipientEmail('');
      setTransferAmount('');
      void fetchDashboard();
    } catch {
      setTransferError('ポイント送付に失敗しました');
    } finally {
      setIsTransferring(false);
    }
  };

  const history = filteredHistory ?? dashboard?.history?.content ?? [];
  const expiringWithin30Days = (dashboard?.expiring ?? []).filter((ep) => {
    const expiresDate = new Date(ep.expiresAt);
    const thirtyDaysFromNow = new Date();
    thirtyDaysFromNow.setDate(thirtyDaysFromNow.getDate() + 30);
    return expiresDate <= thirtyDaysFromNow;
  });
  const totalExpiringAmount = expiringWithin30Days.reduce((sum, ep) => sum + ep.amount, 0);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Balance Header Card */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <CoinsIcon className="size-5" />
            ポイント残高
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <p className="text-muted-foreground text-sm">現在の残高</p>
              <p className="text-4xl font-bold tabular-nums">
                {(dashboard?.balance?.currentBalance ?? 0).toLocaleString()}
                <span className="text-muted-foreground ml-1 text-base font-normal">pt</span>
              </p>
            </div>
            <div>
              <p className="text-muted-foreground text-sm">累計獲得ポイント</p>
              <p className="text-2xl font-semibold tabular-nums">
                {(dashboard?.balance?.totalEarned ?? 0).toLocaleString()}
                <span className="text-muted-foreground ml-1 text-sm font-normal">pt</span>
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Tier Progress */}
      {dashboard?.tier && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrophyIcon className="size-5" />
              会員ランク
            </CardTitle>
            <CardDescription>
              現在のランク: {dashboard.tier.tierName} (×{dashboard.tier.pointMultiplier}倍)
            </CardDescription>
          </CardHeader>
          <CardContent>
            {(() => {
              const tp = getTierProgress(dashboard.tier!);
              return (
                <div className="space-y-2">
                  {tp.nextTierName ? (
                    <>
                      <div className="flex items-center justify-between text-sm">
                        <span>{dashboard.tier!.tierName}</span>
                        <span>{tp.nextTierName}</span>
                      </div>
                      <Progress value={tp.percentage}>
                        <ProgressLabel>
                          {dashboard.tier!.tierName} → {tp.nextTierName}
                        </ProgressLabel>
                        <ProgressValue>
                          {() => `${tp.current.toLocaleString()} / ${tp.max.toLocaleString()} pt`}
                        </ProgressValue>
                      </Progress>
                    </>
                  ) : (
                    <p className="text-muted-foreground text-sm">最高ランクに到達しています 🎉</p>
                  )}
                </div>
              );
            })()}
          </CardContent>
        </Card>
      )}

      {/* Expiring Points Warning */}
      {totalExpiringAmount > 0 && (
        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/30">
          <AlertTriangleIcon className="mt-0.5 size-5 shrink-0 text-amber-600 dark:text-amber-400" />
          <div>
            <p className="font-medium text-amber-800 dark:text-amber-300">
              まもなく失効するポイントがあります
            </p>
            <p className="text-sm text-amber-700 dark:text-amber-400">
              30日以内に{' '}
              <span className="font-bold">{totalExpiringAmount.toLocaleString()} pt</span>{' '}
              が失効します。お早めにご利用ください。
            </p>
          </div>
        </div>
      )}

      {/* Date Range Filter */}
      <Card>
        <CardHeader>
          <CardTitle>ポイント履歴</CardTitle>
          <CardDescription>ポイントの獲得・利用履歴を確認できます</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="space-y-1">
              <Label htmlFor="date-from">開始日</Label>
              <Input
                id="date-from"
                type="date"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="date-to">終了日</Label>
              <Input
                id="date-to"
                type="date"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
              />
            </div>
            <Button
              variant="outline"
              onClick={handleDateFilter}
              disabled={!dateFrom || !dateTo || isFiltering}
            >
              {isFiltering && <Loader2Icon className="animate-spin" />}
              絞り込み
            </Button>
            {filteredHistory && (
              <Button variant="ghost" onClick={clearDateFilter}>
                クリア
              </Button>
            )}
          </div>

          {/* History Table */}
          {history.length === 0 ? (
            <p className="text-muted-foreground py-8 text-center">ポイント履歴がありません</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>日時</TableHead>
                  <TableHead>種別</TableHead>
                  <TableHead className="text-right">ポイント</TableHead>
                  <TableHead>説明</TableHead>
                  <TableHead className="text-right">残高</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {history.map((tx) => (
                  <TableRow key={tx.id}>
                    <TableCell className="text-muted-foreground">
                      {format(new Date(tx.createdAt), 'yyyy/MM/dd HH:mm', { locale: ja })}
                    </TableCell>
                    <TableCell>
                      <TransactionTypeBadge type={tx.transactionType} />
                    </TableCell>
                    <TableCell className="text-right font-medium tabular-nums">
                      <span className="inline-flex items-center gap-1">
                        {tx.transactionType === 'EARNED' || tx.transactionType === 'ADJUSTED' ? (
                          <>
                            <ArrowUpIcon className="size-3 text-emerald-600" />
                            <span className="text-emerald-600">+{tx.amount.toLocaleString()}</span>
                          </>
                        ) : tx.transactionType === 'REDEEMED' ||
                          tx.transactionType === 'TRANSFERRED' ? (
                          <>
                            <ArrowDownIcon className="size-3 text-blue-600" />
                            <span className="text-blue-600">-{tx.amount.toLocaleString()}</span>
                          </>
                        ) : (
                          <>
                            <MinusIcon className="size-3 text-red-600" />
                            <span className="text-red-600">-{tx.amount.toLocaleString()}</span>
                          </>
                        )}
                      </span>
                    </TableCell>
                    <TableCell className="text-muted-foreground max-w-[200px] truncate">
                      {tx.description}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {tx.balanceAfter.toLocaleString()} pt
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Points Transfer */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <SendIcon className="size-5" />
            ポイント送付
          </CardTitle>
          <CardDescription>他のユーザーにポイントを送付できます</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="recipient-email">送付先メールアドレス</Label>
              <Input
                id="recipient-email"
                type="email"
                placeholder="example@mail.com"
                value={recipientEmail}
                onChange={(e) => setRecipientEmail(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="transfer-amount">送付ポイント数</Label>
              <Input
                id="transfer-amount"
                type="number"
                min={1}
                placeholder="0"
                value={transferAmount}
                onChange={(e) => setTransferAmount(e.target.value)}
              />
            </div>
          </div>
          {transferError && <p className="text-destructive text-sm">{transferError}</p>}
          <Button
            onClick={() => setShowTransferConfirm(true)}
            disabled={
              !recipientEmail || !transferAmount || Number(transferAmount) <= 0 || isTransferring
            }
          >
            {isTransferring && <Loader2Icon className="animate-spin" />}
            ポイントを送付
          </Button>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={showTransferConfirm}
        onOpenChange={setShowTransferConfirm}
        title="ポイント送付の確認"
        description={`${recipientEmail} に ${Number(transferAmount).toLocaleString()} pt を送付しますか？この操作は取り消せません。`}
        confirmLabel="送付する"
        onConfirm={handleTransfer}
      />
    </div>
  );
}
