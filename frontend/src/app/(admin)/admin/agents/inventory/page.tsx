'use client';

import { AlertCircle, Loader2, Package, Search } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { ProductPicker, type PickedProduct } from '@/components/admin/product-picker';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

type AvailabilityStatus = 'AVAILABLE' | 'LOW_STOCK' | 'OUT_OF_STOCK';
type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

interface InventoryAlert {
  severity: AlertSeverity;
  message: string;
}

interface InventoryStatus {
  productId: string;
  productName: string | null;
  stockQuantity: number;
  availabilityStatus: AvailabilityStatus;
  isReservable: boolean;
  estimatedRestockDate: string | null;
  alternativeProductIds: string[] | null;
  alert: InventoryAlert | null;
}

const STATUS_BADGE: Record<AvailabilityStatus, { label: string; className: string }> = {
  AVAILABLE: {
    label: '在庫あり',
    className: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  },
  LOW_STOCK: {
    label: '在庫僅少',
    className: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
  },
  OUT_OF_STOCK: {
    label: '在庫切れ',
    className: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  },
};

const SEVERITY_BADGE: Record<AlertSeverity, string> = {
  INFO: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
  WARNING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
  CRITICAL: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
};

export default function AdminInventoryAgentPage() {
  const [picked, setPicked] = useState<PickedProduct[]>([]);
  const [requiredQuantity, setRequiredQuantity] = useState<number>(1);
  const [results, setResults] = useState<InventoryStatus[] | null>(null);
  const [loading, setLoading] = useState(false);

  // 結果から「商品ID → 表示名」の即時参照マップを構築（API が name を返さない時の補完）
  const idToName = new Map<string, string>(picked.map((p) => [p.id, p.name]));

  const handleSubmit = async () => {
    const productIds = picked.map((p) => p.id);
    if (productIds.length === 0) {
      toast.error('商品を 1 件以上選択してください');
      return;
    }

    setLoading(true);
    setResults(null);
    try {
      const res = await fetch('/api/admin/agents/inventory/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productIds, requiredQuantity }),
      });
      const data = await res.json();
      if (!res.ok) {
        toast.error(data?.error ?? '在庫確認に失敗しました');
        return;
      }
      setResults(Array.isArray(data) ? data : []);
      toast.success(`${Array.isArray(data) ? data.length : 0} 件の在庫情報を取得しました`);
    } catch {
      toast.error('通信エラーが発生しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-3">
        <Package className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">在庫監視エージェント</h1>
          <p className="text-sm text-muted-foreground">
            商品名で検索した商品の在庫状況を AI で確認し、代替品提案や再入荷見込みを取得します。
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>商品在庫を確認</CardTitle>
          <CardDescription>
            商品名で検索して候補から選択してください。複数商品を一括確認できます。
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>商品（複数選択可）</Label>
            <ProductPicker
              selected={picked}
              onChange={setPicked}
              placeholder="商品名で検索（例: ATOMIC、SALOMON、ROSSIGNOL）"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="requiredQuantity">必要数量</Label>
            <Input
              id="requiredQuantity"
              type="number"
              min={1}
              value={requiredQuantity}
              onChange={(e) =>
                setRequiredQuantity(Math.max(1, Number.parseInt(e.target.value, 10) || 1))
              }
              className="w-32"
            />
          </div>
          <Button onClick={handleSubmit} disabled={loading || picked.length === 0} className="gap-2">
            {loading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Search className="h-4 w-4" />
            )}
            {loading ? '確認中…' : '在庫を確認'}
          </Button>
        </CardContent>
      </Card>

      {results !== null && (
        <Card>
          <CardHeader>
            <CardTitle>確認結果</CardTitle>
            <CardDescription>{results.length} 件の商品情報</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            {results.length === 0 ? (
              <p className="text-sm text-muted-foreground">該当する商品がありません。</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>商品名</TableHead>
                    <TableHead className="text-right">在庫数</TableHead>
                    <TableHead>状態</TableHead>
                    <TableHead>予約可</TableHead>
                    <TableHead>再入荷予定</TableHead>
                    <TableHead>代替商品</TableHead>
                    <TableHead>アラート</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {results.map((row) => {
                    const statusCfg = STATUS_BADGE[row.availabilityStatus];
                    const displayName =
                      row.productName && row.productName.length > 0
                        ? row.productName
                        : idToName.get(row.productId) ?? '(名称不明)';
                    return (
                      <TableRow key={row.productId}>
                        <TableCell className="text-sm">
                          <div className="font-medium">{displayName}</div>
                          <div className="text-xs text-muted-foreground font-mono">
                            {row.productId}
                          </div>
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {row.stockQuantity}
                        </TableCell>
                        <TableCell>
                          <Badge className={statusCfg.className}>{statusCfg.label}</Badge>
                        </TableCell>
                        <TableCell>{row.isReservable ? '✓' : '—'}</TableCell>
                        <TableCell className="text-sm">
                          {row.estimatedRestockDate ?? '—'}
                        </TableCell>
                        <TableCell className="text-xs font-mono">
                          {row.alternativeProductIds && row.alternativeProductIds.length > 0
                            ? row.alternativeProductIds.join(', ')
                            : '—'}
                        </TableCell>
                        <TableCell>
                          {row.alert ? (
                            <div className="flex items-start gap-1">
                              <AlertCircle className="h-3 w-3 mt-0.5 shrink-0" />
                              <div>
                                <Badge className={SEVERITY_BADGE[row.alert.severity]}>
                                  {row.alert.severity}
                                </Badge>
                                <p className="text-xs mt-1 max-w-xs">{row.alert.message}</p>
                              </div>
                            </div>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
