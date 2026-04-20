'use client';

import {
  ArrowDown,
  ArrowUp,
  Brain,
  Minus,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

// ── Types (spec § 4.2.2) ─────────────────────────────────

interface KpiValue {
  value: number;
  wow: number;
  yoy: number;
}

interface Highlight {
  icon: string;
  text: string;
}

interface ProductMovement {
  sku: string;
  name: string;
  sales: number;
  changeRate: number;
}

interface WeeklySummaryData {
  weekStart: string;
  weekEnd: string;
  generatedAt: string;
  cacheHit: boolean;
  kpis: {
    revenue: KpiValue;
    orders: KpiValue;
    uniqueCustomers: KpiValue;
    avgOrderValue: KpiValue;
  };
  highlights: Highlight[];
  narrative: string;
  topRisingProducts: ProductMovement[];
  topFallingProducts: ProductMovement[];
}

// ── Helpers ───────────────────────────────────────────────

function formatCurrency(val: number): string {
  return `¥${val.toLocaleString('ja-JP')}`;
}

function formatPct(val: number): string {
  const pct = val * 100;
  const sign = pct >= 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}%`;
}

function TrendBadge({ value, label }: { value: number; label: string }) {
  const isPositive = value > 0;
  const isNeutral = Math.abs(value) < 0.005;
  const Icon = isNeutral ? Minus : isPositive ? ArrowUp : ArrowDown;
  const variant = isNeutral
    ? 'outline'
    : isPositive
      ? 'default'
      : 'destructive';

  return (
    <Badge variant={variant} aria-label={`${label} ${formatPct(value)}`}>
      <Icon className="mr-0.5 size-3" />
      {formatPct(value)}
    </Badge>
  );
}

// ── KPI Card ──────────────────────────────────────────────

function AiKpiCard({
  title,
  value,
  formattedValue,
  wow,
  yoy,
}: {
  title: string;
  value: number;
  formattedValue: string;
  wow: number;
  yoy: number;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardDescription>{title}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold" aria-label={`${title}: ${formattedValue}`}>
          {formattedValue}
        </div>
        <div className="mt-2 flex gap-2">
          <TrendBadge value={wow} label={`${title} 前週比`} />
          <span className="text-muted-foreground text-xs">WoW</span>
          <TrendBadge value={yoy} label={`${title} 前年比`} />
          <span className="text-muted-foreground text-xs">YoY</span>
        </div>
      </CardContent>
    </Card>
  );
}

// ── Main Component ────────────────────────────────────────

export function WeeklySummaryCard() {
  const [data, setData] = useState<WeeklySummaryData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchSummary = useCallback(async () => {
    try {
      setLoading(true);
      const res = await fetch('/api/admin/ai-analyzer/weekly-summary');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json: WeeklySummaryData = await res.json();
      setData(json);
    } catch (err) {
      toast.error('週次サマリーの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  const handleRefresh = async () => {
    try {
      setRefreshing(true);
      const res = await fetch('/api/admin/ai-analyzer/weekly-summary', {
        method: 'POST',
      });
      if (res.status === 429) {
        toast.error('再生成は1時間に1回までです');
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json: WeeklySummaryData = await res.json();
      setData(json);
      toast.success('週次サマリーを再生成しました');
    } catch {
      toast.error('再生成に失敗しました');
    } finally {
      setRefreshing(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <RefreshCw className="text-muted-foreground size-6 animate-spin" />
        <span className="text-muted-foreground ml-2">読み込み中…</span>
      </div>
    );
  }

  if (!data) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <p className="text-muted-foreground">週次サマリーデータがありません</p>
          <Button variant="outline" className="mt-4" onClick={handleRefresh}>
            生成する
          </Button>
        </CardContent>
      </Card>
    );
  }

  const { kpis, highlights, narrative } = data;

  return (
    <div className="space-y-6" aria-live="polite">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold">
            週次サマリー（{data.weekStart} 〜 {data.weekEnd}）
          </h3>
          <p className="text-muted-foreground text-sm mt-1 max-w-2xl">
            AI が直近1週間の売上・注文・顧客データを自動分析し、前週比・前年比の変動や注目トレンドをナラティブ形式でレポートします。
          </p>
          <p className="text-muted-foreground text-sm">
            生成: {new Date(data.generatedAt).toLocaleString('ja-JP')}
            {data.cacheHit && (
              <Badge variant="outline" className="ml-2">
                キャッシュ
              </Badge>
            )}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={handleRefresh}
          disabled={refreshing}
        >
          <RefreshCw className={`mr-1 size-4 ${refreshing ? 'animate-spin' : ''}`} />
          再生成
        </Button>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <AiKpiCard
          title="売上"
          value={kpis.revenue.value}
          formattedValue={formatCurrency(kpis.revenue.value)}
          wow={kpis.revenue.wow}
          yoy={kpis.revenue.yoy}
        />
        <AiKpiCard
          title="注文数"
          value={kpis.orders.value}
          formattedValue={kpis.orders.value.toLocaleString('ja-JP')}
          wow={kpis.orders.wow}
          yoy={kpis.orders.yoy}
        />
        <AiKpiCard
          title="ユニーク顧客数"
          value={kpis.uniqueCustomers.value}
          formattedValue={kpis.uniqueCustomers.value.toLocaleString('ja-JP')}
          wow={kpis.uniqueCustomers.wow}
          yoy={kpis.uniqueCustomers.yoy}
        />
        <AiKpiCard
          title="平均注文単価"
          value={kpis.avgOrderValue.value}
          formattedValue={formatCurrency(kpis.avgOrderValue.value)}
          wow={kpis.avgOrderValue.wow}
          yoy={kpis.avgOrderValue.yoy}
        />
      </div>

      {/* Highlights */}
      {highlights.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="size-5" />
              ハイライト
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2">
              {highlights.map((h, i) => (
                <li key={i} className="flex items-start gap-2">
                  <span className="text-lg" role="img" aria-hidden="true">
                    {h.icon}
                  </span>
                  <span>{h.text}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* Narrative */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Brain className="size-5" />
            AI による分析
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground leading-relaxed whitespace-pre-wrap">
            {narrative}
          </p>
        </CardContent>
      </Card>

      {/* Top Rising / Falling Products */}
      {(data.topRisingProducts.length > 0 || data.topFallingProducts.length > 0) && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {data.topRisingProducts.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-green-600">
                  <TrendingUp className="size-5" />
                  急上昇商品
                </CardTitle>
              </CardHeader>
              <CardContent>
                <ul className="space-y-1">
                  {data.topRisingProducts.map((p) => (
                    <li key={p.sku} className="flex justify-between text-sm">
                      <span>
                        {p.name} ({p.sku})
                      </span>
                      <Badge variant="default">{formatPct(p.changeRate)}</Badge>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
          {data.topFallingProducts.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-red-600">
                  <TrendingDown className="size-5" />
                  下降商品
                </CardTitle>
              </CardHeader>
              <CardContent>
                <ul className="space-y-1">
                  {data.topFallingProducts.map((p) => (
                    <li key={p.sku} className="flex justify-between text-sm">
                      <span>
                        {p.name} ({p.sku})
                      </span>
                      <Badge variant="destructive">{formatPct(p.changeRate)}</Badge>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
