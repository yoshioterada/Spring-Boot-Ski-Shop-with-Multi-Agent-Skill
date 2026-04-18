'use client';

import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  DollarSign,
  Loader2,
  Package,
  ShoppingCart,
  Users,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatCurrency, formatNumber } from '@/lib/format';

// --- Types ---

interface KpiData {
  todaySales: number;
  todaySalesTrend: number;
  orderCount: number;
  orderCountTrend: number;
  newMembers: number;
  newMembersTrend: number;
  activeUsers: number;
  activeUsersTrend: number;
}

interface SalesPoint {
  label: string;
  sales: number;
}

interface LowStockItem {
  productId: string;
  productName: string;
  currentStock: number;
  threshold: number;
}

interface RecentOrder {
  orderId: string;
  customerName: string;
  totalAmount: number;
  status: string;
  createdAt: string;
}

interface DashboardData {
  recentOrders: { content?: RecentOrder[] } | null;
  recentUsers: { totalElements?: number } | null;
  lowStock: LowStockItem[] | null;
  analytics: {
    kpi?: KpiData;
    salesChart?: { daily?: SalesPoint[]; weekly?: SalesPoint[]; monthly?: SalesPoint[] };
  } | null;
  recentMails: unknown;
}

// --- Fallback data ---

const fallbackKpi: KpiData = {
  todaySales: 284000,
  todaySalesTrend: 12.5,
  orderCount: 38,
  orderCountTrend: 8.2,
  newMembers: 12,
  newMembersTrend: -3.1,
  activeUsers: 156,
  activeUsersTrend: 5.4,
};

const fallbackSalesDaily: SalesPoint[] = [
  { label: '月', sales: 42000 },
  { label: '火', sales: 38000 },
  { label: '水', sales: 51000 },
  { label: '木', sales: 45000 },
  { label: '金', sales: 62000 },
  { label: '土', sales: 78000 },
  { label: '日', sales: 68000 },
];

const fallbackSalesWeekly: SalesPoint[] = [
  { label: '第1週', sales: 320000 },
  { label: '第2週', sales: 285000 },
  { label: '第3週', sales: 410000 },
  { label: '第4週', sales: 380000 },
];

const fallbackSalesMonthly: SalesPoint[] = [
  { label: '1月', sales: 1200000 },
  { label: '2月', sales: 980000 },
  { label: '3月', sales: 1450000 },
  { label: '4月', sales: 1100000 },
  { label: '5月', sales: 1380000 },
  { label: '6月', sales: 1520000 },
];

const fallbackLowStock: LowStockItem[] = [
  {
    productId: 'SKI-001',
    productName: 'パウダーグライドスキー板 170cm',
    currentStock: 3,
    threshold: 10,
  },
  {
    productId: 'BND-012',
    productName: 'プロフレックスビンディング M',
    currentStock: 5,
    threshold: 10,
  },
  {
    productId: 'PLE-008',
    productName: 'UVプロテクトゴーグル ブラック',
    currentStock: 2,
    threshold: 10,
  },
];

const fallbackOrders: RecentOrder[] = [
  {
    orderId: 'ORD-20240301-001',
    customerName: '田中 太郎',
    totalAmount: 89000,
    status: 'DELIVERED',
    createdAt: '2026-03-01T10:30:00Z',
  },
  {
    orderId: 'ORD-20240301-002',
    customerName: '佐藤 花子',
    totalAmount: 45000,
    status: 'SHIPPED',
    createdAt: '2026-03-01T11:15:00Z',
  },
  {
    orderId: 'ORD-20240301-003',
    customerName: '鈴木 一郎',
    totalAmount: 120000,
    status: 'PROCESSING',
    createdAt: '2026-03-01T12:00:00Z',
  },
  {
    orderId: 'ORD-20240301-004',
    customerName: '高橋 美咲',
    totalAmount: 67000,
    status: 'PENDING',
    createdAt: '2026-03-01T13:45:00Z',
  },
  {
    orderId: 'ORD-20240301-005',
    customerName: '渡辺 健二',
    totalAmount: 33000,
    status: 'CANCELLED',
    createdAt: '2026-03-01T14:20:00Z',
  },
];

// --- Helpers ---

function TrendIndicator({ value }: { value: number }) {
  if (value > 0)
    return (
      <span className="flex items-center gap-0.5 text-xs text-emerald-600">
        <ArrowUp className="size-3" />
        {value.toFixed(1)}%
      </span>
    );
  if (value < 0)
    return (
      <span className="flex items-center gap-0.5 text-xs text-red-600">
        <ArrowDown className="size-3" />
        {Math.abs(value).toFixed(1)}%
      </span>
    );
  return <span className="text-muted-foreground text-xs">±0%</span>;
}

const statusConfig: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  PENDING: { label: '保留中', variant: 'outline' },
  PROCESSING: { label: '処理中', variant: 'secondary' },
  SHIPPED: { label: '発送済', variant: 'default' },
  DELIVERED: { label: '配送完了', variant: 'default' },
  CANCELLED: { label: 'キャンセル', variant: 'destructive' },
};

function DataUnavailableBadge() {
  return (
    <Badge variant="destructive" className="text-xs">
      データ取得不可
    </Badge>
  );
}

// --- Main ---

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [chartRange, setChartRange] = useState<'daily' | 'weekly' | 'monthly'>('daily');

  const fetchDashboard = useCallback(async () => {
    try {
      setLoading(true);
      const res = await fetch('/api/admin/dashboard');
      if (res.ok) {
        setData(await res.json());
      }
    } catch {
      // keep data as null – fallbacks will be used
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboard();
  }, [fetchDashboard]);

  // analytics: BFF から KPI 集計済みオブジェクトが届く。null の場合のみフォールバック。
  const analyticsAvailable = data?.analytics != null;
  const kpi = data?.analytics?.kpi ?? fallbackKpi;

  const salesChartMap = {
    daily: data?.analytics?.salesChart?.daily ?? fallbackSalesDaily,
    weekly: data?.analytics?.salesChart?.weekly ?? fallbackSalesWeekly,
    monthly: data?.analytics?.salesChart?.monthly ?? fallbackSalesMonthly,
  };
  const salesData = salesChartMap[chartRange];

  // lowStock: BFF から配列が届く。null/undefined の場合のみフォールバック。
  const lowStockAvailable = Array.isArray(data?.lowStock);
  const lowStockItems = Array.isArray(data?.lowStock) ? data.lowStock : fallbackLowStock;

  // recentOrders: BFF から { content: [...] } が届く。null の場合のみフォールバック。
  const ordersAvailable = data?.recentOrders != null;
  const recentOrders = data?.recentOrders?.content ?? fallbackOrders;

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Loader2 className="text-muted-foreground size-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">ダッシュボード</h1>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          title="本日の売上"
          value={formatCurrency(kpi.todaySales)}
          trend={kpi.todaySalesTrend}
          icon={<DollarSign className="size-4" />}
          available={analyticsAvailable}
        />
        <KpiCard
          title="注文数"
          value={formatNumber(kpi.orderCount)}
          trend={kpi.orderCountTrend}
          icon={<ShoppingCart className="size-4" />}
          available={analyticsAvailable}
        />
        <KpiCard
          title="新規会員数"
          value={formatNumber(kpi.newMembers)}
          trend={kpi.newMembersTrend}
          icon={<Users className="size-4" />}
          available={analyticsAvailable}
        />
        <KpiCard
          title="アクティブユーザー"
          value={formatNumber(kpi.activeUsers)}
          trend={kpi.activeUsersTrend}
          icon={<Package className="size-4" />}
          available={analyticsAvailable}
        />
      </div>

      {/* Sales Chart + Low Stock */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Sales Chart */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>売上推移</CardTitle>
                <CardDescription>期間別の売上データ</CardDescription>
              </div>
              {!analyticsAvailable && <DataUnavailableBadge />}
            </div>
            <div className="flex gap-1 pt-2">
              {(['daily', 'weekly', 'monthly'] as const).map((range) => (
                <Button
                  key={range}
                  variant={chartRange === range ? 'default' : 'outline'}
                  size="xs"
                  onClick={() => setChartRange(range)}
                >
                  {{ daily: '日別', weekly: '週別', monthly: '月別' }[range]}
                </Button>
              ))}
            </div>
          </CardHeader>
          <CardContent>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={salesData}>
                  <defs>
                    <linearGradient id="salesGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="label" className="text-xs" tick={{ fontSize: 12 }} />
                  <YAxis
                    className="text-xs"
                    tick={{ fontSize: 12 }}
                    tickFormatter={(v: number) => `¥${(v / 1000).toFixed(0)}k`}
                  />
                  <Tooltip
                    formatter={(value) => [formatCurrency(Number(value)), '売上']}
                    contentStyle={{
                      borderRadius: '8px',
                      border: '1px solid hsl(var(--border))',
                      backgroundColor: 'hsl(var(--background))',
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="sales"
                    stroke="hsl(var(--primary))"
                    fill="url(#salesGradient)"
                    strokeWidth={2}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Low Stock Alerts */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="size-4 text-amber-500" />
                  在庫アラート
                </CardTitle>
                <CardDescription>閾値以下の商品</CardDescription>
              </div>
              {!lowStockAvailable && <DataUnavailableBadge />}
            </div>
          </CardHeader>
          <CardContent>
            {lowStockItems.length === 0 ? (
              <p className="text-muted-foreground py-8 text-center text-sm">
                在庫不足の商品はありません
              </p>
            ) : (
              <div className="space-y-3">
                {lowStockItems.map((item) => (
                  <div
                    key={item.productId}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{item.productName}</p>
                      <p className="text-muted-foreground text-xs">SKU: {item.productId}</p>
                    </div>
                    <div className="ml-2 text-right">
                      <p
                        className={`text-sm font-bold ${item.currentStock <= 3 ? 'text-red-600' : 'text-amber-600'}`}
                      >
                        {item.currentStock}
                      </p>
                      <p className="text-muted-foreground text-xs">/ {item.threshold}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Recent Orders */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>最近の注文</CardTitle>
              <CardDescription>直近5件の注文情報</CardDescription>
            </div>
            {!ordersAvailable && <DataUnavailableBadge />}
          </div>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>注文ID</TableHead>
                <TableHead>顧客名</TableHead>
                <TableHead className="text-right">金額</TableHead>
                <TableHead>ステータス</TableHead>
                <TableHead>注文日時</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recentOrders.map((order) => {
                const cfg = statusConfig[order.status] ?? {
                  label: order.status,
                  variant: 'outline' as const,
                };
                return (
                  <TableRow key={order.orderId}>
                    <TableCell className="font-mono text-xs">{order.orderId}</TableCell>
                    <TableCell>{order.customerName}</TableCell>
                    <TableCell className="text-right">
                      {formatCurrency(order.totalAmount)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={cfg.variant}>{cfg.label}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {new Date(order.createdAt).toLocaleString('ja-JP')}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

// --- Sub-components ---

function KpiCard({
  title,
  value,
  trend,
  icon,
  available,
}: {
  title: string;
  value: string;
  trend: number;
  icon: React.ReactNode;
  available: boolean;
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardDescription>{title}</CardDescription>
          <div className="flex items-center gap-2">
            {!available && <DataUnavailableBadge />}
            <span className="text-muted-foreground">{icon}</span>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        <TrendIndicator value={trend} />
      </CardContent>
    </Card>
  );
}
