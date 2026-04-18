'use client';

import { Calculator, Loader2, Trash2, TrendingUp } from 'lucide-react';
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

interface PriceBreakdown {
  basePrice: number;
  demandMultiplier: number;
  demandLevel: string;
  weatherMultiplier: number;
  weatherCondition: string;
  inventoryMultiplier: number;
  inventoryStatus: string;
  customerTierDiscount: number;
  customerTier: string;
  finalPrice: number;
}

interface PricingResult {
  productId: string;
  userId: string;
  finalPrice: number;
  originalBasePrice: number;
  totalDiscountRate: number;
  savingsAmount: number;
  breakdown: PriceBreakdown | null;
  priceJustification: string | null;
  calculatedAt: string;
  validUntil: string;
}

interface PickedItem extends PickedProduct {
  quantity: number;
}

const TIER_OPTIONS = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'];

function formatJpy(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return `¥${Math.round(n).toLocaleString('ja-JP')}`;
}

function formatPct(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return `${(n * 100).toFixed(1)}%`;
}

export default function AdminPricingAgentPage() {
  const [items, setItems] = useState<PickedItem[]>([]);
  const [customerTier, setCustomerTier] = useState<string>('BRONZE');
  const [resortLocation, setResortLocation] = useState<string>('');
  const [results, setResults] = useState<PricingResult[] | null>(null);
  const [loading, setLoading] = useState(false);

  const handlePickerChange = (next: PickedProduct[]) => {
    // 既存の数量を維持しつつ、新しい選択にマージ
    const prevById = new Map(items.map((it) => [it.id, it]));
    setItems(
      next.map((p) => ({
        ...p,
        quantity: prevById.get(p.id)?.quantity ?? 1,
      })),
    );
  };

  const updateQuantity = (id: string, qty: number) => {
    setItems((prev) =>
      prev.map((it) => (it.id === id ? { ...it, quantity: Math.max(1, qty) } : it)),
    );
  };

  const removeItem = (id: string) => {
    setItems((prev) => prev.filter((it) => it.id !== id));
  };

  const idToName = new Map<string, string>(items.map((it) => [it.id, it.name]));

  const handleSubmit = async () => {
    if (items.length === 0) {
      toast.error('商品を 1 件以上選択してください');
      return;
    }

    setLoading(true);
    setResults(null);
    try {
      const endpoint =
        items.length === 1
          ? '/api/admin/agents/pricing/calculate'
          : '/api/admin/agents/pricing/bulk';
      const body =
        items.length === 1
          ? {
              productId: items[0].id,
              quantity: items[0].quantity,
              customerTier,
              resortLocation: resortLocation || null,
            }
          : {
              items: items.map((it) => ({ productId: it.id, quantity: it.quantity })),
              customerTier,
              resortLocation: resortLocation || null,
            };

      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) {
        toast.error(data?.error ?? '価格算出に失敗しました');
        return;
      }
      const rows: PricingResult[] = Array.isArray(data) ? data : [data];
      setResults(rows);
      toast.success(`${rows.length} 件の価格を算出しました`);
    } catch {
      toast.error('通信エラーが発生しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-3">
        <TrendingUp className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">動的価格エージェント</h1>
          <p className="text-sm text-muted-foreground">
            需要・天候・在庫・顧客ティアに基づく動的価格を AI で算出します。
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>価格を算出</CardTitle>
          <CardDescription>
            商品名で検索して候補から選択。複数選択時は一括算出 API を使用します。
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="customerTier">顧客ティア</Label>
              <select
                id="customerTier"
                className="w-full h-9 rounded-md border bg-background px-3 text-sm"
                value={customerTier}
                onChange={(e) => setCustomerTier(e.target.value)}
              >
                {TIER_OPTIONS.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="resort">リゾート（任意）</Label>
              <Input
                id="resort"
                placeholder="例: 苗場スキー場"
                value={resortLocation}
                onChange={(e) => setResortLocation(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label>商品</Label>
            <ProductPicker
              selected={items}
              onChange={handlePickerChange}
              placeholder="商品名で検索（例: ATOMIC、SALOMON、ROSSIGNOL）"
            />
          </div>

          {items.length > 0 && (
            <div className="space-y-2">
              <Label>数量</Label>
              <div className="space-y-2">
                {items.map((it) => (
                  <div key={it.id} className="flex items-center gap-2">
                    <span className="flex-1 text-sm truncate">{it.name}</span>
                    <Input
                      type="number"
                      min={1}
                      value={it.quantity}
                      onChange={(e) =>
                        updateQuantity(
                          it.id,
                          Number.parseInt(e.target.value, 10) || 1,
                        )
                      }
                      className="w-24"
                    />
                    <Button
                      variant="outline"
                      size="icon"
                      onClick={() => removeItem(it.id)}
                      aria-label={`${it.name} を削除`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          <Button onClick={handleSubmit} disabled={loading || items.length === 0} className="gap-2">
            {loading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Calculator className="h-4 w-4" />
            )}
            {loading ? '算出中…' : '価格を算出'}
          </Button>
        </CardContent>
      </Card>

      {results !== null && (
        <Card>
          <CardHeader>
            <CardTitle>算出結果</CardTitle>
            <CardDescription>{results.length} 件</CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto space-y-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>商品名</TableHead>
                  <TableHead className="text-right">基準価格</TableHead>
                  <TableHead className="text-right">最終価格</TableHead>
                  <TableHead className="text-right">割引率</TableHead>
                  <TableHead className="text-right">節約額</TableHead>
                  <TableHead>需要</TableHead>
                  <TableHead>天候</TableHead>
                  <TableHead>在庫</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {results.map((r) => (
                  <TableRow key={r.productId}>
                    <TableCell className="text-sm">
                      <div className="font-medium">
                        {idToName.get(r.productId) ?? '(名称不明)'}
                      </div>
                      <div className="text-xs text-muted-foreground font-mono">
                        {r.productId}
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {formatJpy(r.originalBasePrice)}
                    </TableCell>
                    <TableCell className="text-right font-mono font-semibold">
                      {formatJpy(r.finalPrice)}
                    </TableCell>
                    <TableCell className="text-right">{formatPct(r.totalDiscountRate)}</TableCell>
                    <TableCell className="text-right font-mono">
                      {formatJpy(r.savingsAmount)}
                    </TableCell>
                    <TableCell>
                      {r.breakdown ? <Badge variant="outline">{r.breakdown.demandLevel}</Badge> : '—'}
                    </TableCell>
                    <TableCell>
                      {r.breakdown ? <Badge variant="outline">{r.breakdown.weatherCondition}</Badge> : '—'}
                    </TableCell>
                    <TableCell>
                      {r.breakdown ? <Badge variant="outline">{r.breakdown.inventoryStatus}</Badge> : '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <div className="space-y-3">
              {results.map((r) =>
                r.priceJustification ? (
                  <div key={`just-${r.productId}`} className="rounded-md border p-3">
                    <p className="text-xs text-muted-foreground">
                      <span className="font-medium">{idToName.get(r.productId) ?? r.productId}</span>
                    </p>
                    <p className="text-sm mt-1 whitespace-pre-wrap">{r.priceJustification}</p>
                  </div>
                ) : null,
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
