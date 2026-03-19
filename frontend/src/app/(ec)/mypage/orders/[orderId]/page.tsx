'use client';

import { ArrowLeft, Package, Truck } from 'lucide-react';
import Link from 'next/link';
import { useCallback, useEffect, useState, use } from 'react';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatDate } from '@/lib/format';

interface OrderItem {
  id: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal?: number;
}

interface Order {
  id: string;
  orderNumber: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  items: OrderItem[];
}

interface Shipment {
  id: string;
  trackingNumber: string;
  carrier: string;
  status: string;
  estimatedDeliveryDate?: string;
}

const STATUS_CONFIG: Record<string, { label: string; className: string }> = {
  PENDING: {
    label: '処理待ち',
    className:
      'border-yellow-300 bg-yellow-50 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-400',
  },
  CONFIRMED: {
    label: '確認済み',
    className: 'border-blue-300 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-400',
  },
  SHIPPED: {
    label: '発送済み',
    className:
      'border-purple-300 bg-purple-50 text-purple-700 dark:bg-purple-950 dark:text-purple-400',
  },
  DELIVERED: {
    label: '配達完了',
    className: 'border-green-300 bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400',
  },
  CANCELLED: {
    label: 'キャンセル',
    className: 'border-red-300 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400',
  },
};

function OrderStatusBadge({ status }: { status: string }) {
  const config = STATUS_CONFIG[status] ?? { label: status, className: '' };
  return (
    <Badge variant="outline" className={config.className}>
      {config.label}
    </Badge>
  );
}

export default function OrderDetailPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = use(params);

  const [order, setOrder] = useState<Order | null>(null);
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // Cancel dialog
  const [cancelOpen, setCancelOpen] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);

  // Return form
  const [showReturnForm, setShowReturnForm] = useState(false);
  const [returnReason, setReturnReason] = useState('');
  const [isSubmittingReturn, setIsSubmittingReturn] = useState(false);
  const [returnSuccess, setReturnSuccess] = useState(false);

  const fetchOrder = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const res = await fetch(`/api/orders/${orderId}`);
      if (!res.ok) throw new Error('Order not found');
      const data = await res.json();
      setOrder(data.order);
      setShipment(data.shipment);
    } catch {
      setError('注文情報の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    void fetchOrder();
  }, [fetchOrder]);

  const handleCancel = async () => {
    setIsCancelling(true);
    try {
      const res = await fetch(`/api/orders/${orderId}/cancel`, { method: 'PUT' });
      if (res.ok) {
        await fetchOrder();
      }
    } finally {
      setIsCancelling(false);
      setCancelOpen(false);
    }
  };

  const handleReturn = async () => {
    if (!returnReason.trim()) return;
    setIsSubmittingReturn(true);
    try {
      const res = await fetch('/api/returns', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, reason: returnReason.trim() }),
      });
      if (res.ok) {
        setReturnSuccess(true);
        setShowReturnForm(false);
        setReturnReason('');
      }
    } finally {
      setIsSubmittingReturn(false);
    }
  };

  const canCancel = order?.status === 'PENDING' || order?.status === 'CONFIRMED';
  const canReturn = order?.status === 'DELIVERED';

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-5xl px-4 py-8">
        <div className="flex justify-center py-16">
          <div className="border-primary h-8 w-8 animate-spin rounded-full border-2 border-t-transparent" />
        </div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="container mx-auto max-w-5xl px-4 py-8">
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <Package className="text-muted-foreground mb-4 h-12 w-12" />
            <p className="text-muted-foreground mb-4 text-lg">{error || '注文が見つかりません'}</p>
            <Link href="/mypage/orders" className={buttonVariants({ variant: 'outline' })}>
              注文履歴に戻る
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <Breadcrumb
        items={[
          { label: 'マイページ', href: '/mypage' as never },
          { label: '注文履歴', href: '/mypage/orders' as never },
          { label: '注文詳細' },
        ]}
        className="mb-6"
      />

      {/* Back link */}
      <Link
        href="/mypage/orders"
        className="text-muted-foreground hover:text-foreground mb-6 inline-flex items-center gap-1 text-sm transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        注文履歴に戻る
      </Link>

      {/* Order header */}
      <Card className="mb-6">
        <CardHeader>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-xl">注文 {order.orderNumber}</CardTitle>
              <p className="text-muted-foreground mt-1 text-sm">
                注文日: {formatDate(order.createdAt)}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <OrderStatusBadge status={order.status} />
              <span className="text-xl font-bold">{formatCurrency(order.totalAmount)}</span>
            </div>
          </div>
        </CardHeader>
      </Card>

      {/* Order items */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Package className="h-5 w-5" />
            注文商品
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>商品名</TableHead>
                <TableHead className="text-right">数量</TableHead>
                <TableHead className="text-right">単価</TableHead>
                <TableHead className="text-right">小計</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(order.items ?? []).map((item) => (
                <TableRow key={item.id}>
                  <TableCell>{item.productName}</TableCell>
                  <TableCell className="text-right">{item.quantity}</TableCell>
                  <TableCell className="text-right">{formatCurrency(item.unitPrice)}</TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(item.subtotal ?? item.unitPrice * item.quantity)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell colSpan={3} className="text-right font-bold">
                  合計
                </TableCell>
                <TableCell className="text-right font-bold">
                  {formatCurrency(order.totalAmount)}
                </TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </CardContent>
      </Card>

      {/* Shipping info */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Truck className="h-5 w-5" />
            配送情報
          </CardTitle>
        </CardHeader>
        <CardContent>
          {shipment ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <p className="text-muted-foreground text-sm">配送業者</p>
                <p className="font-medium">{shipment.carrier}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">追跡番号</p>
                <p className="font-medium">{shipment.trackingNumber}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">配送ステータス</p>
                <p className="font-medium">{shipment.status}</p>
              </div>
              {shipment.estimatedDeliveryDate && (
                <div>
                  <p className="text-muted-foreground text-sm">配達予定日</p>
                  <p className="font-medium">{formatDate(shipment.estimatedDeliveryDate)}</p>
                </div>
              )}
            </div>
          ) : (
            <p className="text-muted-foreground">未発送</p>
          )}
        </CardContent>
      </Card>

      {/* Actions */}
      {(canCancel || canReturn) && (
        <>
          <Separator className="mb-6" />
          <div className="flex flex-wrap gap-3">
            {canCancel && (
              <Button
                variant="destructive"
                onClick={() => setCancelOpen(true)}
                disabled={isCancelling}
              >
                注文をキャンセル
              </Button>
            )}
            {canReturn && !returnSuccess && (
              <Button variant="outline" onClick={() => setShowReturnForm(!showReturnForm)}>
                返品を申請
              </Button>
            )}
          </div>
        </>
      )}

      {/* Return success message */}
      {returnSuccess && (
        <Card className="mt-4 border-green-300 bg-green-50 dark:bg-green-950">
          <CardContent className="py-4">
            <p className="text-green-700 dark:text-green-400">
              返品申請を受け付けました。担当者より連絡いたします。
            </p>
          </CardContent>
        </Card>
      )}

      {/* Return form */}
      {showReturnForm && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="text-lg">返品理由</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div>
                <Label htmlFor="return-reason">理由を入力してください</Label>
                <Input
                  id="return-reason"
                  placeholder="返品の理由..."
                  value={returnReason}
                  onChange={(e) => setReturnReason(e.target.value)}
                  className="mt-1"
                />
              </div>
              <div className="flex gap-2">
                <Button
                  onClick={() => void handleReturn()}
                  disabled={!returnReason.trim() || isSubmittingReturn}
                >
                  {isSubmittingReturn ? '送信中...' : '申請する'}
                </Button>
                <Button variant="outline" onClick={() => setShowReturnForm(false)}>
                  キャンセル
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Cancel confirmation dialog */}
      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title="注文のキャンセル"
        description="この注文をキャンセルしてもよろしいですか？この操作は取り消せません。"
        confirmLabel="キャンセルする"
        cancelLabel="戻る"
        onConfirm={() => void handleCancel()}
        variant="destructive"
      />
    </div>
  );
}
