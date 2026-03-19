'use client';

import {
  ChevronDown,
  ChevronRight,
  Package,
  RefreshCw,
  RotateCcw,
  Search,
  Truck,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatDateTime } from '@/lib/format';

type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

interface Order {
  id: number;
  orderNumber: string;
  userId: number;
  customerEmail: string;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItem[];
  paymentId?: string;
  shippingAddress?: string;
  createdAt: string;
  updatedAt: string;
}

interface OrdersResponse {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const STATUS_CONFIG: Record<OrderStatus, { label: string; variant: string; className: string }> = {
  PENDING: {
    label: '保留中',
    variant: 'secondary',
    className: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
  },
  CONFIRMED: {
    label: '確認済',
    variant: 'secondary',
    className: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
  },
  SHIPPED: {
    label: '発送済',
    variant: 'secondary',
    className: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  },
  DELIVERED: {
    label: '配達完了',
    variant: 'secondary',
    className: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  },
  CANCELLED: {
    label: 'キャンセル',
    variant: 'destructive',
    className: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  },
};

const ALL_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);

  // Status update
  const [statusDialogOpen, setStatusDialogOpen] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [newStatus, setNewStatus] = useState<OrderStatus>('CONFIRMED');
  const [statusUpdating, setStatusUpdating] = useState(false);

  // Shipment dialog
  const [shipDialogOpen, setShipDialogOpen] = useState(false);
  const [trackingNumber, setTrackingNumber] = useState('');
  const [carrier, setCarrier] = useState('');
  const [shipmentCreating, setShipmentCreating] = useState(false);

  // Refund dialog
  const [refundConfirmOpen, setRefundConfirmOpen] = useState(false);
  const [refundOrder, setRefundOrder] = useState<Order | null>(null);
  const [refunding, setRefunding] = useState(false);

  // Return status
  const [returnStatusConfirmOpen, setReturnStatusConfirmOpen] = useState(false);
  const [returnOrder, setReturnOrder] = useState<Order | null>(null);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(currentPage),
        size: String(pageSize),
      });
      if (statusFilter !== 'ALL') {
        params.set('status', statusFilter);
      }
      const res = await fetch(`/api/admin/orders?${params.toString()}`);
      if (!res.ok) throw new Error('注文の取得に失敗しました');
      const data: OrdersResponse = await res.json();
      setOrders(data.content ?? []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '注文の取得に失敗しました');
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, statusFilter]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  const handleStatusUpdate = async () => {
    if (!selectedOrder) return;
    setStatusUpdating(true);
    try {
      const res = await fetch(`/api/admin/orders/${selectedOrder.id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus }),
      });
      if (!res.ok) throw new Error('ステータス更新に失敗しました');
      toast.success(`注文 ${selectedOrder.orderNumber} のステータスを更新しました`);
      setStatusDialogOpen(false);
      fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'ステータス更新に失敗しました');
    } finally {
      setStatusUpdating(false);
    }
  };

  const handleCreateShipment = async () => {
    if (!selectedOrder || !trackingNumber || !carrier) return;
    setShipmentCreating(true);
    try {
      const res = await fetch('/api/admin/shipments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderId: selectedOrder.id,
          trackingNumber,
          carrier,
        }),
      });
      if (!res.ok) throw new Error('出荷情報の作成に失敗しました');
      toast.success(`注文 ${selectedOrder.orderNumber} の出荷情報を作成しました`);
      setShipDialogOpen(false);
      setTrackingNumber('');
      setCarrier('');
      fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '出荷情報の作成に失敗しました');
    } finally {
      setShipmentCreating(false);
    }
  };

  const handleRefund = async () => {
    if (!refundOrder?.paymentId) return;
    setRefunding(true);
    try {
      const res = await fetch(`/api/admin/payments/${refundOrder.paymentId}/refund`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: refundOrder.id }),
      });
      if (!res.ok) throw new Error('返金処理に失敗しました');
      toast.success(`注文 ${refundOrder.orderNumber} の返金処理を実行しました`);
      setRefundConfirmOpen(false);
      setRefundOrder(null);
      fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '返金処理に失敗しました');
    } finally {
      setRefunding(false);
    }
  };

  const handleReturnStatusUpdate = async () => {
    if (!returnOrder) return;
    try {
      const res = await fetch(`/api/admin/returns/${returnOrder.id}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'APPROVED' }),
      });
      if (!res.ok) throw new Error('返品ステータスの更新に失敗しました');
      toast.success(`注文 ${returnOrder.orderNumber} の返品を承認しました`);
      setReturnStatusConfirmOpen(false);
      setReturnOrder(null);
      fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '返品ステータスの更新に失敗しました');
    }
  };

  const openStatusDialog = (order: Order) => {
    setSelectedOrder(order);
    setNewStatus(order.status === 'PENDING' ? 'CONFIRMED' : order.status);
    setStatusDialogOpen(true);
  };

  const openShipDialog = (order: Order) => {
    setSelectedOrder(order);
    setTrackingNumber('');
    setCarrier('');
    setShipDialogOpen(true);
  };

  const openRefundDialog = (order: Order) => {
    setRefundOrder(order);
    setRefundConfirmOpen(true);
  };

  const openReturnDialog = (order: Order) => {
    setReturnOrder(order);
    setReturnStatusConfirmOpen(true);
  };

  const toggleExpand = (orderId: number) => {
    setExpandedOrderId((prev) => (prev === orderId ? null : orderId));
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">注文管理</h1>
          <p className="text-muted-foreground text-sm">注文の確認・ステータス管理を行います</p>
        </div>
        <Button variant="outline" onClick={fetchOrders} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          更新
        </Button>
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Search className="text-muted-foreground h-4 w-4" />
            <span className="text-sm font-medium">フィルター:</span>
          </div>
          <Select
            value={statusFilter}
            onValueChange={(val) => {
              setStatusFilter(val ?? 'ALL');
              setCurrentPage(0);
            }}
          >
            <SelectTrigger className="w-48">
              <SelectValue placeholder="ステータス" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">全て</SelectItem>
              {ALL_STATUSES.map((s) => (
                <SelectItem key={s} value={s}>
                  {STATUS_CONFIG[s].label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </Card>

      {/* Orders Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10" />
              <TableHead>注文番号</TableHead>
              <TableHead>メールアドレス</TableHead>
              <TableHead>注文日</TableHead>
              <TableHead className="text-right">合計金額</TableHead>
              <TableHead>ステータス</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} className="text-muted-foreground py-12 text-center">
                  読み込み中...
                </TableCell>
              </TableRow>
            ) : orders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-muted-foreground py-12 text-center">
                  注文が見つかりません
                </TableCell>
              </TableRow>
            ) : (
              orders.map((order) => {
                const isExpanded = expandedOrderId === order.id;
                const cfg = STATUS_CONFIG[order.status] ?? STATUS_CONFIG.PENDING;
                return (
                  <OrderRow
                    key={order.id}
                    order={order}
                    cfg={cfg}
                    isExpanded={isExpanded}
                    onToggle={() => toggleExpand(order.id)}
                    onStatusChange={() => openStatusDialog(order)}
                    onShip={() => openShipDialog(order)}
                    onRefund={() => openRefundDialog(order)}
                    onReturnApprove={() => openReturnDialog(order)}
                  />
                );
              })
            )}
          </TableBody>
        </Table>

        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={pageSize}
          onPageChange={setCurrentPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setCurrentPage(0);
          }}
        />
      </Card>

      {/* Status Update Dialog */}
      <Dialog open={statusDialogOpen} onOpenChange={setStatusDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>ステータス変更</DialogTitle>
            <DialogDescription>
              注文 {selectedOrder?.orderNumber} のステータスを変更します
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>新しいステータス</Label>
              <Select value={newStatus} onValueChange={(val) => setNewStatus(val as OrderStatus)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ALL_STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {STATUS_CONFIG[s].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusDialogOpen(false)}>
              キャンセル
            </Button>
            <Button onClick={handleStatusUpdate} disabled={statusUpdating}>
              {statusUpdating ? '更新中...' : '更新'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Shipment Dialog */}
      <Dialog open={shipDialogOpen} onOpenChange={setShipDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>出荷情報の登録</DialogTitle>
            <DialogDescription>
              注文 {selectedOrder?.orderNumber} の出荷情報を登録します
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="tracking">追跡番号</Label>
              <Input
                id="tracking"
                value={trackingNumber}
                onChange={(e) => setTrackingNumber(e.target.value)}
                placeholder="例: 1234-5678-9012"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="carrier">配送業者</Label>
              <Input
                id="carrier"
                value={carrier}
                onChange={(e) => setCarrier(e.target.value)}
                placeholder="例: ヤマト運輸"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShipDialogOpen(false)}>
              キャンセル
            </Button>
            <Button
              onClick={handleCreateShipment}
              disabled={shipmentCreating || !trackingNumber || !carrier}
            >
              {shipmentCreating ? '登録中...' : '登録'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Refund Confirm */}
      <ConfirmDialog
        open={refundConfirmOpen}
        onOpenChange={setRefundConfirmOpen}
        title="返金処理の実行"
        description={`注文 ${refundOrder?.orderNumber ?? ''} に対して返金処理を実行します。この操作は取り消せません。`}
        confirmLabel={refunding ? '処理中...' : '返金する'}
        onConfirm={handleRefund}
        variant="destructive"
      />

      {/* Return Status Confirm */}
      <ConfirmDialog
        open={returnStatusConfirmOpen}
        onOpenChange={setReturnStatusConfirmOpen}
        title="返品承認"
        description={`注文 ${returnOrder?.orderNumber ?? ''} の返品を承認します。`}
        confirmLabel="承認する"
        onConfirm={handleReturnStatusUpdate}
      />
    </div>
  );
}

function OrderRow({
  order,
  cfg,
  isExpanded,
  onToggle,
  onStatusChange,
  onShip,
  onRefund,
  onReturnApprove,
}: {
  order: Order;
  cfg: { label: string; className: string };
  isExpanded: boolean;
  onToggle: () => void;
  onStatusChange: () => void;
  onShip: () => void;
  onRefund: () => void;
  onReturnApprove: () => void;
}) {
  return (
    <>
      <TableRow className="hover:bg-muted/50 cursor-pointer" onClick={onToggle}>
        <TableCell>
          {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </TableCell>
        <TableCell className="font-mono text-sm">{order.orderNumber}</TableCell>
        <TableCell className="text-sm">{order.customerEmail}</TableCell>
        <TableCell className="text-sm">{formatDateTime(order.createdAt)}</TableCell>
        <TableCell className="text-right font-medium">
          {formatCurrency(order.totalAmount)}
        </TableCell>
        <TableCell>
          <Badge className={cfg.className}>{cfg.label}</Badge>
        </TableCell>
        <TableCell className="text-right">
          <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
            <Button variant="outline" size="sm" onClick={onStatusChange}>
              <RefreshCw className="mr-1 h-3 w-3" />
              変更
            </Button>
            {order.status === 'CONFIRMED' && (
              <Button variant="outline" size="sm" onClick={onShip}>
                <Truck className="mr-1 h-3 w-3" />
                出荷
              </Button>
            )}
            {order.paymentId && order.status !== 'CANCELLED' && (
              <Button variant="outline" size="sm" onClick={onRefund}>
                <RotateCcw className="mr-1 h-3 w-3" />
                返金
              </Button>
            )}
          </div>
        </TableCell>
      </TableRow>
      {isExpanded && (
        <TableRow>
          <TableCell colSpan={7} className="bg-muted/30 p-4">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="flex items-center gap-2 text-sm font-semibold">
                  <Package className="h-4 w-4" />
                  注文明細
                </h3>
                {order.status === 'DELIVERED' && (
                  <Button variant="outline" size="sm" onClick={onReturnApprove}>
                    返品承認
                  </Button>
                )}
              </div>
              {order.shippingAddress && (
                <p className="text-muted-foreground text-sm">配送先: {order.shippingAddress}</p>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>商品名</TableHead>
                    <TableHead className="text-right">単価</TableHead>
                    <TableHead className="text-right">数量</TableHead>
                    <TableHead className="text-right">小計</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(order.items ?? []).map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>{item.productName}</TableCell>
                      <TableCell className="text-right">{formatCurrency(item.unitPrice)}</TableCell>
                      <TableCell className="text-right">{item.quantity}</TableCell>
                      <TableCell className="text-right font-medium">
                        {formatCurrency(item.subtotal)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
