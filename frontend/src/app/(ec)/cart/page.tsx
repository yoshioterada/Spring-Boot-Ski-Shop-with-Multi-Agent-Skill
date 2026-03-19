'use client';

import { Minus, Package, Plus, ShoppingCart, Tag, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatPoints } from '@/lib/format';
import { cn } from '@/lib/utils';

import type {
  CartItemResponse,
  CartResponse,
  PointBalanceResponse,
  ValidateCouponResponse,
} from '@/types/api';

interface CartPageData {
  cart: CartResponse;
  points: PointBalanceResponse | null;
  coupons: unknown;
}

const TAX_RATE = 0.1;
const FREE_SHIPPING_THRESHOLD = 10000;
const SHIPPING_FEE = 800;

export default function CartPage() {
  const [data, setData] = useState<CartPageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [couponCode, setCouponCode] = useState('');
  const [couponResult, setCouponResult] = useState<ValidateCouponResponse | null>(null);
  const [applyingCoupon, setApplyingCoupon] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<CartItemResponse | null>(null);

  const fetchCart = useCallback(async () => {
    try {
      const res = await fetch('/api/cart');
      if (!res.ok) throw new Error();
      const json: CartPageData = await res.json();
      setData(json);
    } catch {
      toast.error('カート情報の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCart();
  }, [fetchCart]);

  const items = data?.cart?.items ?? [];
  const subtotal = items.reduce((sum, item) => sum + item.totalPrice, 0);
  const discount = couponResult?.valid ? couponResult.discountAmount : 0;
  const afterDiscount = Math.max(subtotal - discount, 0);
  const shipping = afterDiscount >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
  const tax = Math.floor(afterDiscount * TAX_RATE);
  const total = afterDiscount + shipping + tax;

  const updateQuantity = async (item: CartItemResponse, delta: number) => {
    const newQty = item.quantity + delta;
    if (newQty < 1) return;

    const prev = data;
    setData((d) => {
      if (!d) return d;
      const updated = d.cart.items.map((i) =>
        i.id === item.id ? { ...i, quantity: newQty, totalPrice: i.unitPrice * newQty } : i,
      );
      return { ...d, cart: { ...d.cart, items: updated } };
    });

    try {
      const res = await fetch(`/api/cart/items/${item.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity: newQty }),
      });
      if (!res.ok) throw new Error();
    } catch {
      setData(prev);
      toast.error('数量の変更に失敗しました');
    }
  };

  const removeItem = async () => {
    if (!deleteTarget) return;
    const prev = data;
    const targetId = deleteTarget.id;
    setDeleteTarget(null);

    setData((d) => {
      if (!d) return d;
      const filtered = d.cart.items.filter((i) => i.id !== targetId);
      return { ...d, cart: { ...d.cart, items: filtered } };
    });

    try {
      const res = await fetch(`/api/cart/items/${targetId}`, { method: 'DELETE' });
      if (!res.ok) throw new Error();
      toast.success('商品を削除しました');
    } catch {
      setData(prev);
      toast.error('商品の削除に失敗しました');
    }
  };

  const applyCoupon = async () => {
    if (!couponCode.trim()) return;
    setApplyingCoupon(true);
    try {
      const res = await fetch('/api/coupons/validate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: couponCode.trim() }),
      });
      const result: ValidateCouponResponse = await res.json();
      setCouponResult(result);
      if (result.valid) {
        toast.success(`クーポンが適用されました（${formatCurrency(result.discountAmount)}割引）`);
      } else {
        toast.error(result.message ?? 'クーポンが無効です');
      }
    } catch {
      toast.error('クーポンの検証に失敗しました');
    } finally {
      setApplyingCoupon(false);
    }
  };

  if (loading) return <CartSkeleton />;

  if (items.length === 0) {
    return (
      <div className="container mx-auto max-w-4xl px-4 py-8">
        <Breadcrumb items={[{ label: 'カート' }]} className="mb-6" />
        <Card className="text-center">
          <CardContent className="flex flex-col items-center gap-4 py-16">
            <ShoppingCart className="text-muted-foreground size-16" />
            <h2 className="text-xl font-semibold">カートは空です</h2>
            <p className="text-muted-foreground">
              お気に入りの商品を見つけてカートに追加しましょう
            </p>
            <Link href="/catalog" className={cn(buttonVariants({ size: 'lg' }), 'mt-2')}>
              商品を探す
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <Breadcrumb items={[{ label: 'カート' }]} className="mb-6" />
      <h1 className="mb-6 text-2xl font-bold">ショッピングカート ({items.length}点)</h1>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Cart items */}
        <div className="lg:col-span-2">
          {/* Desktop table */}
          <div className="hidden md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[50%]">商品</TableHead>
                  <TableHead className="text-center">数量</TableHead>
                  <TableHead className="text-right">小計</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <div className="bg-muted flex size-16 shrink-0 items-center justify-center rounded-md">
                          <Package className="text-muted-foreground size-6" />
                        </div>
                        <div>
                          <p className="font-medium">{item.productName}</p>
                          <p className="text-muted-foreground text-sm">
                            {formatCurrency(item.unitPrice)}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-center gap-1">
                        <Button
                          variant="outline"
                          size="icon-xs"
                          onClick={() => updateQuantity(item, -1)}
                          disabled={item.quantity <= 1}
                        >
                          <Minus />
                        </Button>
                        <span className="w-8 text-center text-sm font-medium">{item.quantity}</span>
                        <Button
                          variant="outline"
                          size="icon-xs"
                          onClick={() => updateQuantity(item, 1)}
                        >
                          <Plus />
                        </Button>
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {formatCurrency(item.unitPrice * item.quantity)}
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon-xs" onClick={() => setDeleteTarget(item)}>
                        <Trash2 className="text-destructive size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* Mobile cards */}
          <div className="flex flex-col gap-3 md:hidden">
            {items.map((item) => (
              <Card key={item.id}>
                <CardContent className="flex items-start gap-3 py-3">
                  <div className="bg-muted flex size-14 shrink-0 items-center justify-center rounded-md">
                    <Package className="text-muted-foreground size-5" />
                  </div>
                  <div className="flex-1 space-y-2">
                    <div className="flex items-start justify-between">
                      <p className="text-sm leading-tight font-medium">{item.productName}</p>
                      <Button variant="ghost" size="icon-xs" onClick={() => setDeleteTarget(item)}>
                        <Trash2 className="text-destructive size-4" />
                      </Button>
                    </div>
                    <p className="text-muted-foreground text-xs">
                      {formatCurrency(item.unitPrice)} / 個
                    </p>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1">
                        <Button
                          variant="outline"
                          size="icon-xs"
                          onClick={() => updateQuantity(item, -1)}
                          disabled={item.quantity <= 1}
                        >
                          <Minus />
                        </Button>
                        <span className="w-8 text-center text-sm font-medium">{item.quantity}</span>
                        <Button
                          variant="outline"
                          size="icon-xs"
                          onClick={() => updateQuantity(item, 1)}
                        >
                          <Plus />
                        </Button>
                      </div>
                      <p className="text-sm font-semibold">
                        {formatCurrency(item.unitPrice * item.quantity)}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <div className="mt-4">
            <Link href="/catalog" className={cn(buttonVariants({ variant: 'outline' }), 'gap-1.5')}>
              <ShoppingCart className="size-4" />
              お買い物を続ける
            </Link>
          </div>
        </div>

        {/* Order summary sidebar */}
        <div className="space-y-4">
          {/* Coupon */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Tag className="size-4" />
                クーポン
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex gap-2">
                <Input
                  placeholder="クーポンコード"
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && applyCoupon()}
                />
                <Button
                  variant="outline"
                  onClick={applyCoupon}
                  disabled={applyingCoupon || !couponCode.trim()}
                >
                  適用
                </Button>
              </div>
              {couponResult?.valid && (
                <p className="mt-2 text-sm text-green-600">
                  {formatCurrency(couponResult.discountAmount)} 割引適用中
                </p>
              )}
            </CardContent>
          </Card>

          {/* Points */}
          {data?.points && (
            <Card>
              <CardContent className="py-3">
                <p className="text-muted-foreground text-sm">保有ポイント</p>
                <p className="text-lg font-semibold">{formatPoints(data.points.currentBalance)}</p>
              </CardContent>
            </Card>
          )}

          {/* Summary */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">注文サマリー</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">小計</span>
                <span>{formatCurrency(subtotal)}</span>
              </div>
              {discount > 0 && (
                <div className="flex justify-between text-green-600">
                  <span>クーポン割引</span>
                  <span>-{formatCurrency(discount)}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-muted-foreground">配送料</span>
                <span>{shipping === 0 ? '無料' : formatCurrency(shipping)}</span>
              </div>
              {shipping > 0 && (
                <p className="text-muted-foreground text-xs">
                  {formatCurrency(FREE_SHIPPING_THRESHOLD)}以上で送料無料
                </p>
              )}
              <div className="flex justify-between">
                <span className="text-muted-foreground">消費税 (10%)</span>
                <span>{formatCurrency(tax)}</span>
              </div>
              <Separator />
              <div className="flex justify-between text-lg font-bold">
                <span>合計</span>
                <span>{formatCurrency(total)}</span>
              </div>
            </CardContent>
            <CardFooter className="flex-col gap-2">
              <Link href="/checkout" className={cn(buttonVariants({ size: 'lg' }), 'w-full')}>
                購入手続きへ
              </Link>
            </CardFooter>
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="商品を削除"
        description={`「${deleteTarget?.productName ?? ''}」をカートから削除しますか？`}
        confirmLabel="削除する"
        cancelLabel="キャンセル"
        onConfirm={removeItem}
        variant="destructive"
      />
    </div>
  );
}

function CartSkeleton() {
  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <Skeleton className="mb-6 h-5 w-32" />
      <Skeleton className="mb-6 h-8 w-64" />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex items-center gap-4">
              <Skeleton className="size-16 rounded-md" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-48" />
                <Skeleton className="h-4 w-24" />
              </div>
              <Skeleton className="h-8 w-24" />
            </div>
          ))}
        </div>
        <div className="space-y-4">
          <Skeleton className="h-36 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </div>
    </div>
  );
}
