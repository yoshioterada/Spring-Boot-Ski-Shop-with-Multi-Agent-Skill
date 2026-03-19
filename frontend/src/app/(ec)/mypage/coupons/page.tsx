'use client';

import { format } from 'date-fns';
import { ja } from 'date-fns/locale';
import {
  CheckCircleIcon,
  CopyIcon,
  Loader2Icon,
  PercentIcon,
  TagIcon,
  TicketIcon,
  XCircleIcon,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
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
import { Skeleton } from '@/components/ui/skeleton';

import type { CouponResponse, UserAvailableCouponsResponse } from '@/types/api/coupon';

interface CouponDetail extends CouponResponse {
  conditions?: string;
  applicableProducts?: string[];
}

export default function CouponsPage() {
  const [coupons, setCoupons] = useState<CouponResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedCoupon, setSelectedCoupon] = useState<CouponDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [showDetail, setShowDetail] = useState(false);

  // Coupon code input
  const [codeInput, setCodeInput] = useState('');
  const [isValidating, setIsValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<{
    valid: boolean;
    message: string;
  } | null>(null);

  const fetchCoupons = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetch('/api/coupons/available');
      if (!res.ok) throw new Error('Failed to fetch');
      const data: UserAvailableCouponsResponse = await res.json();
      setCoupons(data.coupons ?? []);
    } catch {
      toast.error('クーポン情報の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchCoupons();
  }, [fetchCoupons]);

  const handleCouponClick = async (coupon: CouponResponse) => {
    setShowDetail(true);
    setIsDetailLoading(true);
    setSelectedCoupon(coupon);
    try {
      const res = await fetch(`/api/coupons/${encodeURIComponent(coupon.code)}`);
      if (res.ok) {
        const detail: CouponDetail = await res.json();
        setSelectedCoupon(detail);
      }
    } catch {
      // Keep the basic coupon data if detail fetch fails
    } finally {
      setIsDetailLoading(false);
    }
  };

  const handleValidate = async () => {
    if (!codeInput.trim()) return;
    setIsValidating(true);
    setValidationResult(null);
    try {
      const res = await fetch('/api/coupons/validate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: codeInput.trim() }),
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.valid !== false) {
        setValidationResult({ valid: true, message: 'クーポンは有効です' });
        void fetchCoupons();
      } else {
        setValidationResult({
          valid: false,
          message: data?.message ?? 'このクーポンコードは無効です',
        });
      }
    } catch {
      setValidationResult({ valid: false, message: 'クーポンの検証に失敗しました' });
    } finally {
      setIsValidating(false);
    }
  };

  const copyCode = (code: string) => {
    void navigator.clipboard.writeText(code);
    toast.success('クーポンコードをコピーしました');
  };

  const formatDiscount = (coupon: CouponResponse) => {
    if (coupon.discountType === 'PERCENTAGE') {
      return `${coupon.discountValue}%OFF`;
    }
    return `¥${coupon.discountValue.toLocaleString()}OFF`;
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-24 w-full" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Code Input Section */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TagIcon className="size-5" />
            コードを入力
          </CardTitle>
          <CardDescription>お持ちのクーポンコードを入力して適用できます</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex gap-3">
            <div className="flex-1">
              <Label htmlFor="coupon-code" className="sr-only">
                クーポンコード
              </Label>
              <Input
                id="coupon-code"
                placeholder="クーポンコードを入力"
                value={codeInput}
                onChange={(e) => {
                  setCodeInput(e.target.value);
                  setValidationResult(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleValidate();
                }}
              />
            </div>
            <Button onClick={handleValidate} disabled={!codeInput.trim() || isValidating}>
              {isValidating && <Loader2Icon className="animate-spin" />}
              確認
            </Button>
          </div>
          {validationResult && (
            <div
              className={`flex items-center gap-2 text-sm ${
                validationResult.valid
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : 'text-destructive'
              }`}
            >
              {validationResult.valid ? (
                <CheckCircleIcon className="size-4" />
              ) : (
                <XCircleIcon className="size-4" />
              )}
              {validationResult.message}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Coupons Grid */}
      <div>
        <h2 className="mb-4 text-lg font-medium">利用可能なクーポン</h2>
        {coupons.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-12">
              <TicketIcon className="text-muted-foreground mb-3 size-12" />
              <p className="text-muted-foreground text-lg font-medium">
                利用可能なクーポンはありません
              </p>
              <p className="text-muted-foreground mt-1 text-sm">
                クーポンコードをお持ちの場合は上のフォームから入力してください
              </p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {coupons.map((coupon) => (
              <Card
                key={coupon.id}
                className="cursor-pointer transition-shadow hover:shadow-md"
                onClick={() => handleCouponClick(coupon)}
              >
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <CardTitle className="text-xl">
                      {coupon.discountType === 'PERCENTAGE' ? (
                        <span className="flex items-center gap-1">
                          <PercentIcon className="size-5" />
                          {formatDiscount(coupon)}
                        </span>
                      ) : (
                        formatDiscount(coupon)
                      )}
                    </CardTitle>
                    <Badge variant="secondary">
                      {coupon.couponType === 'SINGLE_USE' ? '1回限り' : '複数回'}
                    </Badge>
                  </div>
                  <CardDescription>
                    {coupon.maximumDiscount
                      ? `最大 ¥${coupon.maximumDiscount.toLocaleString()} 割引`
                      : null}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2 text-sm">
                    {coupon.minimumAmount > 0 && (
                      <p className="text-muted-foreground">
                        ¥{coupon.minimumAmount.toLocaleString()} 以上のお買い物で利用可能
                      </p>
                    )}
                    <p className="text-muted-foreground">
                      有効期限: {format(new Date(coupon.expiresAt), 'yyyy/MM/dd', { locale: ja })}
                    </p>
                  </div>
                </CardContent>
                <CardFooter>
                  <div className="flex w-full items-center justify-between">
                    <code className="bg-muted rounded px-2 py-1 font-mono text-xs">
                      {coupon.code}
                    </code>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        copyCode(coupon.code);
                      }}
                    >
                      <CopyIcon className="size-4" />
                    </Button>
                  </div>
                </CardFooter>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* Detail Dialog */}
      <Dialog open={showDetail} onOpenChange={setShowDetail}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>クーポン詳細</DialogTitle>
            <DialogDescription>
              {selectedCoupon ? formatDiscount(selectedCoupon) : ''}
            </DialogDescription>
          </DialogHeader>
          {isDetailLoading ? (
            <div className="space-y-3 py-4">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-4 w-3/4" />
            </div>
          ) : selectedCoupon ? (
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-2">
                <div className="text-muted-foreground">コード</div>
                <div className="font-mono font-medium">{selectedCoupon.code}</div>
                <div className="text-muted-foreground">割引タイプ</div>
                <div>
                  {selectedCoupon.discountType === 'PERCENTAGE'
                    ? `${selectedCoupon.discountValue}%`
                    : `¥${selectedCoupon.discountValue.toLocaleString()}`}
                </div>
                {selectedCoupon.minimumAmount > 0 && (
                  <>
                    <div className="text-muted-foreground">最低購入額</div>
                    <div>¥{selectedCoupon.minimumAmount.toLocaleString()}</div>
                  </>
                )}
                {selectedCoupon.maximumDiscount != null && (
                  <>
                    <div className="text-muted-foreground">最大割引額</div>
                    <div>¥{selectedCoupon.maximumDiscount.toLocaleString()}</div>
                  </>
                )}
                <div className="text-muted-foreground">有効期限</div>
                <div>
                  {format(new Date(selectedCoupon.expiresAt), 'yyyy/MM/dd HH:mm', {
                    locale: ja,
                  })}
                </div>
                <div className="text-muted-foreground">残り使用回数</div>
                <div>
                  {selectedCoupon.usageLimit - selectedCoupon.usedCount} /{' '}
                  {selectedCoupon.usageLimit}
                </div>
              </div>
              {selectedCoupon.conditions && (
                <div>
                  <p className="text-muted-foreground mb-1">適用条件</p>
                  <p>{selectedCoupon.conditions}</p>
                </div>
              )}
              {selectedCoupon.applicableProducts &&
                selectedCoupon.applicableProducts.length > 0 && (
                  <div>
                    <p className="text-muted-foreground mb-1">対象商品</p>
                    <ul className="list-inside list-disc">
                      {selectedCoupon.applicableProducts.map((p) => (
                        <li key={p}>{p}</li>
                      ))}
                    </ul>
                  </div>
                )}
            </div>
          ) : null}
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                if (selectedCoupon) copyCode(selectedCoupon.code);
              }}
            >
              <CopyIcon className="size-4" />
              コードをコピー
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
