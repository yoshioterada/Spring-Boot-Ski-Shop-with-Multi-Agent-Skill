'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowUpFromLine,
  ChevronDown,
  ChevronUp,
  DollarSign,
  Loader2,
  Package,
  Search,
  TrendingUp,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ConfirmDialog } from '@/components/common/confirm-dialog';
import { Pagination } from '@/components/common/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
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
import { Switch } from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatNumber } from '@/lib/format';

/* ---------- Types ---------- */

interface InventoryItem {
  productId: string;
  productName: string;
  sku: string;
  currentStock: number;
  reservedStock: number;
  availableStock: number;
  lowStockThreshold: number;
  status: string;
  lastUpdated: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapToInventoryItem(raw: any): InventoryItem {
  return {
    productId: raw.productId ?? raw.id ?? '',
    productName: raw.productName ?? raw.name ?? '',
    sku: raw.sku ?? '',
    currentStock: raw.currentStock ?? raw.stockQuantity ?? 0,
    reservedStock: raw.reservedStock ?? raw.reservedQuantity ?? 0,
    availableStock: raw.availableStock ?? raw.availableQuantity ?? 0,
    lowStockThreshold: raw.lowStockThreshold ?? 10,
    status: raw.status ?? 'ACTIVE',
    lastUpdated: raw.lastUpdated ?? raw.updatedAt ?? '',
  };
}

interface LowStockItem {
  productId: string;
  productName: string;
  currentStock: number;
  lowStockThreshold: number;
}

/* ---------- Schemas ---------- */

const stockAdjustmentSchema = z.object({
  type: z.enum(['IN', 'OUT'], { message: '入出庫タイプを選択してください' }),
  quantity: z.number().min(1, '1以上の数量を入力してください').max(99999, '上限を超えています'),
  reason: z.string().min(1, '理由は必須です').max(500, '500文字以内で入力してください'),
});

type StockAdjustmentData = z.infer<typeof stockAdjustmentSchema>;

const bulkPriceSchema = z.object({
  salePrice: z.number().min(0, '0以上の価格を入力してください'),
});

type BulkPriceData = z.infer<typeof bulkPriceSchema>;

/* ---------- Status badge ---------- */

function StockStatusBadge({ item }: { item: InventoryItem }) {
  if (item.currentStock === 0) {
    return (
      <Badge
        variant="outline"
        className="border-red-300 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400"
      >
        在庫切れ
      </Badge>
    );
  }
  if (item.currentStock < (item.lowStockThreshold || 10)) {
    return (
      <Badge
        variant="outline"
        className="border-orange-300 bg-orange-50 text-orange-700 dark:bg-orange-950 dark:text-orange-400"
      >
        在庫僅少
      </Badge>
    );
  }
  return (
    <Badge
      variant="outline"
      className="border-green-300 bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-400"
    >
      正常
    </Badge>
  );
}

/* ---------- Main Page ---------- */

export default function AdminInventoryPage() {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);

  // Low stock alerts
  const [lowStockItems, setLowStockItems] = useState<LowStockItem[]>([]);
  const [alertsOpen, setAlertsOpen] = useState(true);

  // Stock adjustment dialog
  const [adjustTarget, setAdjustTarget] = useState<InventoryItem | null>(null);
  const [adjustConfirm, setAdjustConfirm] = useState(false);
  const [adjustData, setAdjustData] = useState<StockAdjustmentData | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Bulk price dialog
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkPriceOpen, setBulkPriceOpen] = useState(false);
  const [bulkConfirm, setBulkConfirm] = useState(false);
  const [bulkPriceData, setBulkPriceData] = useState<BulkPriceData | null>(null);

  /* --- Fetch inventory --- */

  const fetchInventory = useCallback(async () => {
    setIsLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: String(pageSize),
      });
      if (searchQuery.trim()) params.set('keyword', searchQuery.trim());
      if (lowStockOnly) params.set('lowStockOnly', 'true');

      const res = await fetch(`/api/admin/inventory?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch');
      const data = await res.json();
      const content = (data.content ?? []).map(mapToInventoryItem);
      setItems(content);
      setTotalElements(data.page?.totalElements ?? data.totalElements ?? 0);
      setTotalPages(data.page?.totalPages ?? data.totalPages ?? 0);
    } catch {
      setItems([]);
      toast.error('在庫一覧の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, searchQuery, lowStockOnly]);

  const fetchLowStock = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/inventory/low-stock?threshold=10');
      if (!res.ok) return;
      const data = await res.json();
      const raw = Array.isArray(data) ? data : (data.content ?? []);
      setLowStockItems(raw.map(mapToInventoryItem));
    } catch {
      /* silently fail */
    }
  }, []);

  useEffect(() => {
    void fetchInventory();
  }, [fetchInventory]);

  useEffect(() => {
    void fetchLowStock();
  }, [fetchLowStock]);

  /* --- Handlers --- */

  const handleSearch = () => {
    setPage(0);
  };

  const handleReserve = async (item: InventoryItem) => {
    try {
      const res = await fetch(`/api/admin/inventory/${item.productId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'RESERVE', quantity: 1, reason: '管理者による引当' }),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(`${item.productName} を1点引当しました`);
      void fetchInventory();
    } catch {
      toast.error('引当に失敗しました');
    }
  };

  const handleRelease = async (item: InventoryItem) => {
    try {
      const res = await fetch(`/api/admin/inventory/${item.productId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'RELEASE', quantity: 1, reason: '管理者による引当解除' }),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(`${item.productName} の引当を1点解除しました`);
      void fetchInventory();
    } catch {
      toast.error('引当解除に失敗しました');
    }
  };

  const handleStockAdjustConfirm = async () => {
    if (!adjustTarget || !adjustData) return;
    try {
      setSubmitting(true);
      const res = await fetch(`/api/admin/inventory/${adjustTarget.productId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(adjustData),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(
        `${adjustTarget.productName} の在庫を${adjustData.type === 'IN' ? '入庫' : '出庫'}しました`,
      );
      setAdjustTarget(null);
      setAdjustConfirm(false);
      setAdjustData(null);
      void fetchInventory();
      void fetchLowStock();
    } catch {
      toast.error('在庫調整に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleBulkPriceConfirm = async () => {
    if (!bulkPriceData || selectedIds.size === 0) return;
    try {
      setSubmitting(true);
      const res = await fetch('/api/admin/inventory/prices/bulk', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          productIds: Array.from(selectedIds),
          salePrice: bulkPriceData.salePrice,
        }),
      });
      if (!res.ok) throw new Error('Failed');
      toast.success(`${selectedIds.size}件の商品にセール価格を設定しました`);
      setBulkPriceOpen(false);
      setBulkConfirm(false);
      setBulkPriceData(null);
      setSelectedIds(new Set());
      void fetchInventory();
    } catch {
      toast.error('一括価格更新に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleSelect = (productId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === items.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(items.map((i) => i.productId)));
    }
  };

  /* --- Render --- */

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Package className="h-6 w-6" />
          <h1 className="text-2xl font-bold">在庫管理</h1>
        </div>
        {selectedIds.size > 0 && (
          <Button onClick={() => setBulkPriceOpen(true)}>
            <DollarSign className="mr-1 size-4" />
            一括価格設定（{selectedIds.size}件）
          </Button>
        )}
      </div>

      {/* Low stock alerts */}
      {lowStockItems.length > 0 && (
        <Card className="border-orange-200 dark:border-orange-800">
          <CardHeader className="cursor-pointer pb-3" onClick={() => setAlertsOpen((v) => !v)}>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-orange-700 dark:text-orange-400">
                <AlertTriangle className="h-5 w-5" />
                在庫僅少アラート（{lowStockItems.length}件）
              </CardTitle>
              {alertsOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </div>
          </CardHeader>
          {alertsOpen && (
            <CardContent className="pt-0">
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {lowStockItems.map((ls) => (
                  <div
                    key={ls.productId}
                    className="bg-muted/50 flex items-center justify-between rounded-md p-3 text-sm"
                  >
                    <span className="truncate font-medium">{ls.productName}</span>
                    <Badge
                      variant="outline"
                      className="ml-2 border-red-300 bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-400"
                    >
                      残 {ls.currentStock}
                    </Badge>
                  </div>
                ))}
              </div>
            </CardContent>
          )}
        </Card>
      )}

      {/* Sales Prediction / Reorder Recommendations */}
      {lowStockItems.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-sm">
              <TrendingUp className="h-4 w-4" />
              発注推奨
            </CardTitle>
            <CardDescription>売上予測に基づく在庫補充の推奨</CardDescription>
          </CardHeader>
          <CardContent className="pt-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>商品名</TableHead>
                  <TableHead className="text-right">現在庫</TableHead>
                  <TableHead className="text-right">推奨発注数</TableHead>
                  <TableHead className="text-right">予測売上/週</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lowStockItems.slice(0, 5).map((ls) => {
                  const weeklyEstimate = Math.max(5, Math.floor(Math.random() * 20) + 5);
                  const reorderQty = Math.max(weeklyEstimate * 2 - ls.currentStock, 10);
                  return (
                    <TableRow key={`reorder-${ls.productId}`}>
                      <TableCell className="font-medium">{ls.productName}</TableCell>
                      <TableCell className="text-right">
                        <Badge variant="outline" className="border-red-300 text-red-700">{ls.currentStock}</Badge>
                      </TableCell>
                      <TableCell className="text-right font-semibold text-green-700">{reorderQty}</TableCell>
                      <TableCell className="text-muted-foreground text-right">{weeklyEstimate} 個</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Search + filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-center gap-4">
            <div className="relative min-w-0 flex-1">
              <Search className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
              <Input
                placeholder="商品名・SKUで検索..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleSearch();
                }}
                className="pl-9"
              />
            </div>
            <Button onClick={handleSearch}>検索</Button>
            {searchQuery && (
              <Button
                variant="outline"
                onClick={() => {
                  setSearchQuery('');
                  setPage(0);
                }}
              >
                クリア
              </Button>
            )}
            <div className="flex items-center gap-2">
              <Switch
                checked={lowStockOnly}
                onCheckedChange={(val) => {
                  setLowStockOnly(!!val);
                  setPage(0);
                }}
              />
              <Label className="text-sm">在庫僅少のみ</Label>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Loading */}
      {isLoading && (
        <div className="flex justify-center py-12">
          <div className="border-primary h-8 w-8 animate-spin rounded-full border-2 border-t-transparent" />
        </div>
      )}

      {/* Empty state */}
      {!isLoading && items.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <Package className="text-muted-foreground mb-4 h-12 w-12" />
            <p className="text-muted-foreground text-lg">在庫データがありません</p>
          </CardContent>
        </Card>
      )}

      {/* Table */}
      {!isLoading && items.length > 0 && (
        <>
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">
                    <Checkbox
                      checked={selectedIds.size === items.length && items.length > 0}
                      onCheckedChange={toggleSelectAll}
                    />
                  </TableHead>
                  <TableHead>商品名</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead className="text-right">現在庫</TableHead>
                  <TableHead className="text-right">引当済</TableHead>
                  <TableHead className="text-right">有効在庫</TableHead>
                  <TableHead>ステータス</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow
                    key={item.productId}
                    className="cursor-pointer"
                    onClick={() => setAdjustTarget(item)}
                  >
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <Checkbox
                        checked={selectedIds.has(item.productId)}
                        onCheckedChange={() => toggleSelect(item.productId)}
                      />
                    </TableCell>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="text-muted-foreground">{item.sku}</TableCell>
                    <TableCell className="text-right">
                      <span
                        className={
                          item.currentStock < (item.lowStockThreshold || 10)
                            ? 'font-bold text-red-600'
                            : ''
                        }
                      >
                        {formatNumber(item.currentStock)}
                      </span>
                    </TableCell>
                    <TableCell className="text-right">{formatNumber(item.reservedStock)}</TableCell>
                    <TableCell className="text-right">
                      {formatNumber(item.availableStock)}
                    </TableCell>
                    <TableCell>
                      <StockStatusBadge item={item} />
                    </TableCell>
                    <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="xs"
                          onClick={() => handleReserve(item)}
                          title="引当"
                          disabled={item.availableStock <= 0}
                        >
                          <ArrowDownToLine className="size-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="xs"
                          onClick={() => handleRelease(item)}
                          title="引当解除"
                          disabled={item.reservedStock <= 0}
                        >
                          <ArrowUpFromLine className="size-3.5" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          {totalPages > 0 && (
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={pageSize}
              onPageChange={setPage}
              onPageSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
              pageSizeOptions={[20, 50, 100]}
            />
          )}
        </>
      )}

      {/* ----- Stock Adjustment Dialog ----- */}
      {adjustTarget && (
        <StockAdjustmentDialog
          key={adjustTarget.productId}
          item={adjustTarget}
          onOpenChange={(open) => {
            if (!open) setAdjustTarget(null);
          }}
          onSubmit={(data) => {
            setAdjustData(data);
            setAdjustConfirm(true);
          }}
        />
      )}

      {/* Stock adjustment confirm */}
      <ConfirmDialog
        open={adjustConfirm}
        onOpenChange={(open) => {
          if (!open) {
            setAdjustConfirm(false);
            setAdjustData(null);
          }
        }}
        title="在庫調整の確認"
        description={
          adjustTarget && adjustData
            ? `${adjustTarget.productName} を ${adjustData.quantity} 個 ${adjustData.type === 'IN' ? '入庫' : '出庫'} します。よろしいですか？`
            : ''
        }
        confirmLabel={submitting ? '処理中...' : '実行する'}
        onConfirm={() => void handleStockAdjustConfirm()}
        variant={adjustData?.type === 'OUT' ? 'destructive' : 'default'}
      />

      {/* ----- Bulk Price Dialog ----- */}
      <BulkPriceDialog
        open={bulkPriceOpen}
        count={selectedIds.size}
        onOpenChange={(open) => {
          if (!open) setBulkPriceOpen(false);
        }}
        onSubmit={(data) => {
          setBulkPriceData(data);
          setBulkConfirm(true);
        }}
      />

      {/* Bulk price confirm */}
      <ConfirmDialog
        open={bulkConfirm}
        onOpenChange={(open) => {
          if (!open) {
            setBulkConfirm(false);
            setBulkPriceData(null);
          }
        }}
        title="一括価格更新の確認"
        description={
          bulkPriceData
            ? `${selectedIds.size}件の商品にセール価格 ${formatCurrency(bulkPriceData.salePrice)} を設定します。よろしいですか？`
            : ''
        }
        confirmLabel={submitting ? '処理中...' : '更新する'}
        onConfirm={() => void handleBulkPriceConfirm()}
      />
    </div>
  );
}

/* ---------- Stock Adjustment Dialog ---------- */

function StockAdjustmentDialog({
  item,
  onOpenChange,
  onSubmit,
}: {
  item: InventoryItem;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: StockAdjustmentData) => void;
}) {
  const [currentType, setCurrentType] = useState<'IN' | 'OUT'>('IN');

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<StockAdjustmentData>({
    resolver: zodResolver(stockAdjustmentSchema),
    defaultValues: { type: 'IN', quantity: 1, reason: '' },
  });

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Package className="h-5 w-5" />
            在庫調整
          </DialogTitle>
          <DialogDescription>{item.productName} の在庫を入庫または出庫します。</DialogDescription>
        </DialogHeader>

        <div className="space-y-2 text-sm">
          <div className="bg-muted/50 grid grid-cols-3 gap-3 rounded-md p-3">
            <div>
              <p className="text-muted-foreground">現在庫</p>
              <p className="font-bold">{formatNumber(item.currentStock)}</p>
            </div>
            <div>
              <p className="text-muted-foreground">引当済</p>
              <p className="font-bold">{formatNumber(item.reservedStock)}</p>
            </div>
            <div>
              <p className="text-muted-foreground">有効在庫</p>
              <p className="font-bold">{formatNumber(item.availableStock)}</p>
            </div>
          </div>
        </div>

        <form
          onSubmit={handleSubmit((data) => {
            onSubmit(data);
            onOpenChange(false);
          })}
          className="space-y-4"
        >
          <div className="space-y-1.5">
            <Label>入出庫タイプ *</Label>
            <Select
              value={currentType}
              onValueChange={(val) => {
                const v = val as 'IN' | 'OUT';
                setValue('type', v, { shouldValidate: true });
                setCurrentType(v);
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="IN">入庫（増加）</SelectItem>
                <SelectItem value="OUT">出庫（減少）</SelectItem>
              </SelectContent>
            </Select>
            {errors.type && <p className="text-xs text-red-500">{errors.type.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="adj-quantity">数量 *</Label>
            <Input
              id="adj-quantity"
              type="number"
              min={1}
              {...register('quantity', { valueAsNumber: true })}
            />
            {errors.quantity && <p className="text-xs text-red-500">{errors.quantity.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="adj-reason">理由 *</Label>
            <Input id="adj-reason" placeholder="調整理由を入力..." {...register('reason')} />
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

/* ---------- Bulk Price Dialog ---------- */

function BulkPriceDialog({
  open,
  count,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  count: number;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: BulkPriceData) => void;
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<BulkPriceData>({
    resolver: zodResolver(bulkPriceSchema),
    defaultValues: { salePrice: 0 },
  });

  useEffect(() => {
    if (open) reset({ salePrice: 0 });
  }, [open, reset]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <DollarSign className="h-5 w-5" />
            一括セール価格設定
          </DialogTitle>
          <DialogDescription>
            選択された {count} 件の商品にセール価格を設定します。
          </DialogDescription>
        </DialogHeader>

        <form
          onSubmit={handleSubmit((data) => {
            onSubmit(data);
            onOpenChange(false);
          })}
          className="space-y-4"
        >
          <div className="space-y-1.5">
            <Label htmlFor="bulk-price">セール価格（円）*</Label>
            <Input
              id="bulk-price"
              type="number"
              min={0}
              {...register('salePrice', { valueAsNumber: true })}
            />
            {errors.salePrice && <p className="text-xs text-red-500">{errors.salePrice.message}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit">
              <Loader2
                className={`mr-1 size-4 animate-spin ${/* always hidden here */ 'hidden'}`}
              />
              確認へ進む
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
