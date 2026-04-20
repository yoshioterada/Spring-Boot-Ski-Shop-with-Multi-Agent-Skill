'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { BarChart3, Brain, Download, FileText, Loader2, Search, TrendingUp, Users } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { toast } from 'sonner';
import { z } from 'zod';

import { AiChatPanel } from '@/components/admin/ai-analyzer/AiChatPanel';
import { DeadStockRadar } from '@/components/admin/ai-analyzer/DeadStockRadar';
import { SeasonalForecastPanel } from '@/components/admin/ai-analyzer/SeasonalForecastPanel';
import { WeeklySummaryCard } from '@/components/admin/ai-analyzer/WeeklySummaryCard';
import { ZeroHitRadar } from '@/components/admin/ai-analyzer/ZeroHitRadar';
import { SkeletonTable } from '@/components/common/skeleton-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatCurrency, formatNumber } from '@/lib/format';

import type { PieLabelRenderProps } from 'recharts';

// --- Types ---

interface SalesForecast {
  date: string;
  actual: number;
  forecast: number;
}

interface ProductPerformance {
  id: string;
  name: string;
  revenue: number;
  quantity: number;
  category: string;
}

interface CategoryRevenue {
  categoryId: string;
  category: string;
  revenue: number;
}

interface SalesData {
  forecast: SalesForecast[] | null;
  topProducts: ProductPerformance[] | null;
  categoryRevenue: CategoryRevenue[] | null;
}

interface UserStats {
  totalUsers: number | null;
  newUsersInPeriod: number | null;
  activeUsers: number | null;
}

interface CustomerSegment {
  segment: string;
  count: number;
}

interface RegistrationData {
  date: string;
  count: number;
}

interface UsersData {
  stats: UserStats | null;
  segments: CustomerSegment[] | null;
  registrations: RegistrationData[] | null;
}

interface TrendData {
  date: string;
  revenue: number;
  orders: number;
  uniqueCustomers: number;
}

interface TrendsData {
  trends: TrendData[] | null;
  categories: { categoryId: string; category: string; revenue: number; orders: number }[] | null;
}

interface SearchTerm {
  rank: number;
  keyword: string;
  count: number;
  avgHits: number;
}

interface SearchRate {
  period: string;
  hitRate: number;
  zeroHitRate: number;
}

interface SearchPerformance {
  date: string;
  searches: number;
  zeroHits: number;
  avgDurationMs: number;
}

interface SearchSummaryStats {
  totalSearches: number;
  uniqueKeywords: number;
  zeroHitRatio: number;
  averageDurationMs: number;
}

interface SearchData {
  stats: SearchSummaryStats | null;
  popularTerms: SearchTerm[] | null;
  hitRates: SearchRate[] | null;
  performance: SearchPerformance[] | null;
}

interface ReportResult {
  downloadUrl: string;
  fileName: string;
}

// --- Backend response shapes & adapters ---

interface BackendSalesSummary {
  days: number;
  totalRevenue: number;
  totalOrders: number;
  averageOrderValue: number;
  dailyRevenue: { date: string; revenue: number; orders: number }[];
  topProducts: { productId: string; productName: string; quantity: number; revenue: number }[];
  categoryRevenue: { categoryId: string; categoryName: string; revenue: number; orders: number }[];
}

interface BackendUserSummary {
  days: number;
  totalUsers: number;
  newUsersInPeriod: number;
  activeUsers: number;
  statusBreakdown: Record<string, number>;
  registrations: { date: string; registrations: number }[];
  segments: { segmentId: string; label: string; count: number; ratio: number }[];
}

interface BackendTrendsResponse {
  days: number;
  trends: { date: string; revenue: number; orders: number; uniqueCustomers: number }[];
  categories: { categoryId: string; categoryName: string; revenue: number; orders: number }[];
}

interface BackendSearchSummary {
  days: number;
  totalSearches: number;
  uniqueKeywords: number;
  zeroHitRatio: number;
  averageDurationMs: number;
  popularKeywords: { keyword: string; searches: number; totalHits: number; avgHitRate: number }[];
  trend: { date: string; searches: number; zeroHits: number; avgDurationMs: number }[];
}

function fmtMonthDay(isoDate: string): string {
  // 'YYYY-MM-DD' -> 'M/D'
  const parts = isoDate.split('-');
  if (parts.length === 3) return `${Number(parts[1])}/${Number(parts[2])}`;
  return isoDate;
}

function adaptSales(d: BackendSalesSummary | null): SalesData {
  if (!d) return { forecast: null, topProducts: null, categoryRevenue: null };
  const daily = d.dailyRevenue ?? [];
  // 単純な「予測」: 7 日後方移動平均を全期間に適用 (実績値 0 の未来日は 0、過去日は revenue)
  const forecast: SalesForecast[] = daily.map((p, i) => {
    const window = daily.slice(Math.max(0, i - 6), i + 1);
    const avg = window.reduce((s, w) => s + (w.revenue ?? 0), 0) / Math.max(1, window.length);
    return { date: fmtMonthDay(p.date), actual: p.revenue ?? 0, forecast: Math.round(avg) };
  });
  const topProducts: ProductPerformance[] = (d.topProducts ?? []).map((p) => ({
    id: p.productId,
    name: p.productName,
    revenue: p.revenue ?? 0,
    quantity: p.quantity ?? 0,
    category: '-',
  }));
  const categoryRevenue: CategoryRevenue[] = (d.categoryRevenue ?? []).map((c) => ({
    categoryId: c.categoryId,
    category: c.categoryName,
    revenue: c.revenue ?? 0,
  }));
  return {
    forecast: forecast.length > 0 ? forecast : null,
    topProducts: topProducts.length > 0 ? topProducts : null,
    categoryRevenue: categoryRevenue.length > 0 ? categoryRevenue : null,
  };
}

function adaptUsers(d: BackendUserSummary | null): UsersData {
  if (!d) return { stats: null, segments: null, registrations: null };
  return {
    stats: {
      totalUsers: d.totalUsers ?? null,
      newUsersInPeriod: d.newUsersInPeriod ?? null,
      activeUsers: d.activeUsers ?? null,
    },
    segments: (d.segments ?? []).map((s) => ({ segment: s.label, count: s.count })),
    registrations: (d.registrations ?? []).map((r) => ({
      date: fmtMonthDay(r.date),
      count: r.registrations ?? 0,
    })),
  };
}

function adaptTrends(d: BackendTrendsResponse | null): TrendsData {
  if (!d) return { trends: null, categories: null };
  return {
    trends: (d.trends ?? []).map((t) => ({
      date: fmtMonthDay(t.date),
      revenue: t.revenue ?? 0,
      orders: t.orders ?? 0,
      uniqueCustomers: t.uniqueCustomers ?? 0,
    })),
    categories: (d.categories ?? []).map((c) => ({
      categoryId: c.categoryId,
      category: c.categoryName,
      revenue: c.revenue ?? 0,
      orders: c.orders ?? 0,
    })),
  };
}

function adaptSearch(d: BackendSearchSummary | null): SearchData {
  if (!d) return { stats: null, popularTerms: null, hitRates: null, performance: null };
  const popularTerms: SearchTerm[] = (d.popularKeywords ?? []).map((k, i) => ({
    rank: i + 1,
    keyword: k.keyword,
    count: k.searches ?? 0,
    avgHits: k.avgHitRate ?? 0,
  }));
  // 週次バケットにまとめてヒット率を算出
  const trend = d.trend ?? [];
  const buckets: { period: string; searches: number; zeroHits: number }[] = [];
  for (let i = trend.length - 1, w = 0; i >= 0; i -= 7, w++) {
    const slice = trend.slice(Math.max(0, i - 6), i + 1);
    const searches = slice.reduce((s, t) => s + (t.searches ?? 0), 0);
    const zeroHits = slice.reduce((s, t) => s + (t.zeroHits ?? 0), 0);
    const label = w === 0 ? '今週' : w === 1 ? '先週' : `${w + 1}週前`;
    buckets.push({ period: label, searches, zeroHits });
    if (buckets.length >= 4) break;
  }
  const hitRates: SearchRate[] = buckets.map((b) => {
    const hit = b.searches > 0 ? ((b.searches - b.zeroHits) / b.searches) * 100 : 0;
    const zero = b.searches > 0 ? (b.zeroHits / b.searches) * 100 : 0;
    return { period: b.period, hitRate: Number(hit.toFixed(1)), zeroHitRate: Number(zero.toFixed(1)) };
  });
  const performance: SearchPerformance[] = trend.map((t) => ({
    date: fmtMonthDay(t.date),
    searches: t.searches ?? 0,
    zeroHits: t.zeroHits ?? 0,
    avgDurationMs: t.avgDurationMs ?? 0,
  }));
  return {
    stats: {
      totalSearches: d.totalSearches ?? 0,
      uniqueKeywords: d.uniqueKeywords ?? 0,
      zeroHitRatio: d.zeroHitRatio ?? 0,
      averageDurationMs: d.averageDurationMs ?? 0,
    },
    popularTerms: popularTerms.length > 0 ? popularTerms : null,
    hitRates: hitRates.length > 0 ? hitRates : null,
    performance: performance.length > 0 ? performance : null,
  };
}

// カテゴリ別の顔色。AI スキー装備アドバイザーの「待ち時間ガイド」パネルと同じトーンで
// 一貫したカテゴリ認識を与える。Tailwind カラーパレットの 500 番台。
const CATEGORY_COLOR: Record<string, string> = {
  'cat-ski':     '#0ea5e9', // sky-500    — 雪・空
  'cat-boots':   '#f59e0b', // amber-500  — 足元・レザー
  'cat-wear':    '#10b981', // emerald-500 — アウトドア
  'cat-helmets': '#ef4444', // red-500    — 安全
  'cat-goggles': '#06b6d4', // cyan-500   — レンズ
  'cat-gloves':  '#6366f1', // indigo-500 — 手元
  'cat-poles':   '#d946ef', // fuchsia-500 — ストック
};

const PIE_COLORS = [
  '#0ea5e9',
  '#f59e0b',
  '#10b981',
  '#ef4444',
  '#06b6d4',
  '#6366f1',
  '#d946ef',
  '#64748b',
];

function colorForCategory(id: string | undefined, fallbackIdx: number): string {
  if (id && CATEGORY_COLOR[id]) return CATEGORY_COLOR[id];
  return PIE_COLORS[fallbackIdx % PIE_COLORS.length];
}

// --- Report Schema ---

const reportSchema = z.object({
  reportType: z.string().min(1, 'レポートタイプを選択してください'),
  startDate: z.string().min(1, '開始日を入力してください'),
  endDate: z.string().min(1, '終了日を入力してください'),
  format: z.string().min(1, 'フォーマットを選択してください'),
});

type ReportFormData = z.infer<typeof reportSchema>;

// --- Helper ---

function DataBadge() {
  return (
    <Badge variant="secondary" className="text-xs">
      データ取得不可
    </Badge>
  );
}

function KpiCard({
  title,
  value,
  suffix,
  icon: Icon,
}: {
  title: string;
  value: string | number | null;
  suffix?: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardDescription>{title}</CardDescription>
        <Icon className="text-muted-foreground size-4" />
      </CardHeader>
      <CardContent>
        {value !== null ? (
          <div className="text-2xl font-bold">
            {value}
            {suffix && (
              <span className="text-muted-foreground ml-1 text-sm font-normal">{suffix}</span>
            )}
          </div>
        ) : (
          <DataBadge />
        )}
      </CardContent>
    </Card>
  );
}

// --- Page ---

export default function AdminAnalyticsPage() {
  const [salesData, setSalesData] = useState<SalesData | null>(null);
  const [usersData, setUsersData] = useState<UsersData | null>(null);
  const [trendsData, setTrendsData] = useState<TrendsData | null>(null);
  const [searchData, setSearchData] = useState<SearchData | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<string>('sales');
  const [salesPeriod, setSalesPeriod] = useState('day');
  const [trendPeriod, setTrendPeriod] = useState('30d');
  const [reportResult, setReportResult] = useState<ReportResult | null>(null);
  const [reportLoading, setReportLoading] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<ReportFormData>({
    resolver: zodResolver(reportSchema),
    defaultValues: { reportType: '', startDate: '', endDate: '', format: '' },
  });

  const fetchAnalytics = useCallback(
    async (type: string) => {
      setLoading(true);
      try {
        const params = new URLSearchParams({ type });
        // 売上タブ: 期間 (日数) を渡す。'day'=14, 'week'=8(週)*7=56, 'month'=12*30
        if (type === 'sales') {
          const days = salesPeriod === 'week' ? 56 : salesPeriod === 'month' ? 360 : 14;
          params.set('days', String(days));
        }
        // トレンドタブ: 7d/30d/90d/1y → days
        if (type === 'trends') {
          const days =
            trendPeriod === '7d' ? 7 : trendPeriod === '90d' ? 90 : trendPeriod === '1y' ? 365 : 30;
          params.set('days', String(days));
        }
        // ユーザー / 検索タブ: 30日固定
        if (type === 'users' || type === 'search') {
          params.set('days', '30');
        }

        const res = await fetch(`/api/admin/analytics?${params.toString()}`);
        const data = res.ok ? await res.json() : null;

        switch (type) {
          case 'sales':
            setSalesData(adaptSales(data as BackendSalesSummary | null));
            break;
          case 'users':
            setUsersData(adaptUsers(data as BackendUserSummary | null));
            break;
          case 'trends':
            setTrendsData(adaptTrends(data as BackendTrendsResponse | null));
            break;
          case 'search':
            setSearchData(adaptSearch(data as BackendSearchSummary | null));
            break;
        }
      } catch {
        switch (type) {
          case 'sales':
            setSalesData({ forecast: null, topProducts: null, categoryRevenue: null });
            break;
          case 'users':
            setUsersData({ stats: null, segments: null, registrations: null });
            break;
          case 'trends':
            setTrendsData({ trends: null, categories: null });
            break;
          case 'search':
            setSearchData({ stats: null, popularTerms: null, hitRates: null, performance: null });
            break;
        }
      } finally {
        setLoading(false);
      }
    },
    [salesPeriod, trendPeriod],
  );

  useEffect(() => {
    fetchAnalytics(activeTab);
  }, [activeTab, fetchAnalytics]);

  const onSubmitReport = async (data: ReportFormData) => {
    setReportLoading(true);
    setReportResult(null);
    try {
      const res = await fetch('/api/admin/analytics/report', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error('Report generation failed');
      const result = await res.json();
      setReportResult(result);
      toast.success('レポートを生成しました');
    } catch {
      toast.error('レポート生成に失敗しました');
    } finally {
      setReportLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">アナリティクス</h1>
        <p className="text-muted-foreground">売上・ユーザー・トレンド・検索の分析データ</p>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="sales">
            <TrendingUp className="mr-1 size-4" />
            売上分析
          </TabsTrigger>
          <TabsTrigger value="users">
            <Users className="mr-1 size-4" />
            ユーザー分析
          </TabsTrigger>
          <TabsTrigger value="trends">
            <BarChart3 className="mr-1 size-4" />
            トレンド分析
          </TabsTrigger>
          <TabsTrigger value="search">
            <Search className="mr-1 size-4" />
            検索分析
          </TabsTrigger>
          <TabsTrigger value="report">
            <FileText className="mr-1 size-4" />
            レポート生成
          </TabsTrigger>
          <TabsTrigger value="ai-analyzer">
            <Brain className="mr-1 size-4" />
            AI 分析
          </TabsTrigger>
        </TabsList>

        {/* ===== Tab 1: 売上分析 ===== */}
        <TabsContent value="sales">
          {loading ? (
            <SkeletonTable rows={6} columns={4} />
          ) : (
            <div className="space-y-6">
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle>売上予測</CardTitle>
                      <CardDescription>実績と予測トレンド</CardDescription>
                    </div>
                    <div className="flex gap-1">
                      {(['day', 'week', 'month'] as const).map((p) => (
                        <Button
                          key={p}
                          size="sm"
                          variant={salesPeriod === p ? 'default' : 'outline'}
                          onClick={() => setSalesPeriod(p)}
                        >
                          {{ day: '日', week: '週', month: '月' }[p]}
                        </Button>
                      ))}
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {salesData?.forecast ? (
                    <div className="h-72">
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={salesData.forecast}>
                          <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                          <XAxis dataKey="date" className="text-xs" tick={{ fontSize: 12 }} />
                          <YAxis
                            className="text-xs"
                            tick={{ fontSize: 12 }}
                            tickFormatter={(v) => `${(Number(v) / 10000).toFixed(0)}万`}
                          />
                          <Tooltip formatter={(v) => formatCurrency(Number(v))} />
                          <Legend />
                          <Line
                            type="monotone"
                            dataKey="actual"
                            name="実績"
                            stroke="#0ea5e9"
                            strokeWidth={2}
                            dot={{ r: 3, fill: '#0ea5e9' }}
                            activeDot={{ r: 5 }}
                          />
                          <Line
                            type="monotone"
                            dataKey="forecast"
                            name="予測"
                            stroke="#f59e0b"
                            strokeWidth={2}
                            strokeDasharray="5 5"
                            dot={{ r: 3, fill: '#f59e0b' }}
                            activeDot={{ r: 5 }}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  ) : (
                    <DataBadge />
                  )}
                </CardContent>
              </Card>

              <div className="grid gap-6 md:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>売上上位10商品</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {salesData?.topProducts ? (
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>#</TableHead>
                            <TableHead>商品名</TableHead>
                            <TableHead>カテゴリ</TableHead>
                            <TableHead className="text-right">売上</TableHead>
                            <TableHead className="text-right">数量</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {salesData.topProducts.map((p, i) => (
                            <TableRow key={p.id}>
                              <TableCell className="font-medium">{i + 1}</TableCell>
                              <TableCell>{p.name}</TableCell>
                              <TableCell>
                                <Badge variant="secondary">{p.category}</Badge>
                              </TableCell>
                              <TableCell className="text-right">
                                {formatCurrency(p.revenue)}
                              </TableCell>
                              <TableCell className="text-right">
                                {formatNumber(p.quantity)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>カテゴリ別売上</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {salesData?.categoryRevenue ? (
                      <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={salesData.categoryRevenue}
                              dataKey="revenue"
                              nameKey="category"
                              cx="50%"
                              cy="50%"
                              outerRadius={100}
                              label={(props: PieLabelRenderProps) =>
                                `${props.name} ${((props.percent ?? 0) * 100).toFixed(0)}%`
                              }
                            >
                              {salesData.categoryRevenue.map((c, idx) => (
                                <Cell key={idx} fill={colorForCategory(c.categoryId, idx)} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(v) => formatCurrency(Number(v))} />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>
              </div>
            </div>
          )}
        </TabsContent>

        {/* ===== Tab 2: ユーザー分析 ===== */}
        <TabsContent value="users">
          {loading ? (
            <SkeletonTable rows={4} columns={3} />
          ) : (
            <div className="space-y-6">
              <div className="grid gap-4 md:grid-cols-3">
                <KpiCard
                  title="総ユーザー数"
                  value={usersData?.stats?.totalUsers != null ? formatNumber(usersData.stats.totalUsers) : null}
                  icon={Users}
                />
                <KpiCard
                  title="期間内 新規登録"
                  value={
                    usersData?.stats?.newUsersInPeriod != null
                      ? formatNumber(usersData.stats.newUsersInPeriod)
                      : null
                  }
                  suffix="/ 30日"
                  icon={BarChart3}
                />
                <KpiCard
                  title="アクティブユーザー"
                  value={
                    usersData?.stats?.activeUsers != null ? formatNumber(usersData.stats.activeUsers) : null
                  }
                  icon={TrendingUp}
                />
              </div>

              <div className="grid gap-6 md:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>顧客セグメント</CardTitle>
                    <CardDescription>新規 / リピート / 休眠</CardDescription>
                  </CardHeader>
                  <CardContent>
                    {usersData?.segments ? (
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={usersData.segments}
                              dataKey="count"
                              nameKey="segment"
                              cx="50%"
                              cy="50%"
                              innerRadius={50}
                              outerRadius={90}
                              label={(props: PieLabelRenderProps) =>
                                `${props.name} ${((props.percent ?? 0) * 100).toFixed(0)}%`
                              }
                            >
                              {usersData.segments.map((_, idx) => (
                                <Cell key={idx} fill={PIE_COLORS[idx % PIE_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>新規ユーザー登録</CardTitle>
                    <CardDescription>過去30日間</CardDescription>
                  </CardHeader>
                  <CardContent>
                    {usersData?.registrations ? (
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={usersData.registrations}>
                            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                            <XAxis dataKey="date" className="text-xs" tick={{ fontSize: 12 }} />
                            <YAxis className="text-xs" tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Line
                              type="monotone"
                              dataKey="count"
                              name="登録数"
                              stroke="#0ea5e9"
                              strokeWidth={2}
                              dot={{ r: 3, fill: '#0ea5e9' }}
                              activeDot={{ r: 5 }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>
              </div>
            </div>
          )}
        </TabsContent>

        {/* ===== Tab 3: トレンド分析 ===== */}
        <TabsContent value="trends">
          {loading ? (
            <SkeletonTable rows={4} columns={5} />
          ) : (
            <div className="space-y-6">
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle>売上トレンド</CardTitle>
                      <CardDescription>日別の売上・注文数・ユニーク購入者数</CardDescription>
                    </div>
                    <div className="flex gap-1">
                      {(['7d', '30d', '90d', '1y'] as const).map((p) => (
                        <Button
                          key={p}
                          size="sm"
                          variant={trendPeriod === p ? 'default' : 'outline'}
                          onClick={() => setTrendPeriod(p)}
                        >
                          {{ '7d': '7日', '30d': '30日', '90d': '90日', '1y': '1年' }[p]}
                        </Button>
                      ))}
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {trendsData?.trends ? (
                    <div className="h-80">
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={trendsData.trends}>
                          <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                          <XAxis dataKey="date" className="text-xs" tick={{ fontSize: 12 }} />
                          <YAxis
                            yAxisId="left"
                            className="text-xs"
                            tick={{ fontSize: 12 }}
                            tickFormatter={(v) => `${(Number(v) / 10000).toFixed(0)}万`}
                          />
                          <YAxis
                            yAxisId="right"
                            orientation="right"
                            className="text-xs"
                            tick={{ fontSize: 12 }}
                          />
                          <Tooltip />
                          <Legend />
                          <Line
                            yAxisId="left"
                            type="monotone"
                            dataKey="revenue"
                            name="売上"
                            stroke="#0ea5e9"
                            strokeWidth={2}
                            dot={false}
                          />
                          <Line
                            yAxisId="right"
                            type="monotone"
                            dataKey="orders"
                            name="注文数"
                            stroke="#10b981"
                            strokeWidth={2}
                            dot={false}
                          />
                          <Line
                            yAxisId="right"
                            type="monotone"
                            dataKey="uniqueCustomers"
                            name="ユニーク購入者"
                            stroke="#f59e0b"
                            strokeWidth={2}
                            strokeDasharray="5 5"
                            dot={false}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  ) : (
                    <DataBadge />
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>カテゴリ別売上</CardTitle>
                  <CardDescription>期間集計</CardDescription>
                </CardHeader>
                <CardContent>
                  {trendsData?.categories && trendsData.categories.length > 0 ? (
                    <div className="h-72">
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={trendsData.categories}>
                          <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                          <XAxis dataKey="category" className="text-xs" tick={{ fontSize: 12 }} />
                          <YAxis
                            className="text-xs"
                            tick={{ fontSize: 12 }}
                            tickFormatter={(v) => `${(Number(v) / 10000).toFixed(0)}万`}
                          />
                          <Tooltip formatter={(v) => formatCurrency(Number(v))} />
                          <Bar dataKey="revenue" name="売上" radius={[4, 4, 0, 0]}>
                            {trendsData.categories.map((c, idx) => (
                              <Cell key={idx} fill={colorForCategory(c.categoryId, idx)} />
                            ))}
                          </Bar>
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  ) : (
                    <DataBadge />
                  )}
                </CardContent>
              </Card>
            </div>
          )}
        </TabsContent>

        {/* ===== Tab 4: 検索分析 ===== */}
        <TabsContent value="search">
          {loading ? (
            <SkeletonTable rows={6} columns={4} />
          ) : (
            <div className="space-y-6">
              <Card>
                <CardHeader>
                  <CardTitle>人気検索キーワード</CardTitle>
                </CardHeader>
                <CardContent>
                  {searchData?.popularTerms ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>順位</TableHead>
                          <TableHead>キーワード</TableHead>
                          <TableHead className="text-right">検索数</TableHead>
                          <TableHead className="text-right">平均ヒット件数</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {searchData.popularTerms.map((t) => (
                          <TableRow key={t.rank}>
                            <TableCell className="font-medium">{t.rank}</TableCell>
                            <TableCell>{t.keyword}</TableCell>
                            <TableCell className="text-right">{formatNumber(t.count)}</TableCell>
                            <TableCell className="text-right">
                              {t.avgHits.toFixed(1)}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <DataBadge />
                  )}
                </CardContent>
              </Card>

              <div className="grid gap-6 md:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>ヒット率 / ゼロヒット率</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {searchData?.hitRates ? (
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={searchData.hitRates}>
                            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                            <XAxis dataKey="period" className="text-xs" tick={{ fontSize: 12 }} />
                            <YAxis className="text-xs" tick={{ fontSize: 12 }} unit="%" />
                            <Tooltip formatter={(v) => `${v}%`} />
                            <Legend />
                            <Bar
                              dataKey="hitRate"
                              name="ヒット率"
                              fill="#3b82f6"
                              radius={[4, 4, 0, 0]}
                            />
                            <Bar
                              dataKey="zeroHitRate"
                              name="ゼロヒット率"
                              fill="#ef4444"
                              radius={[4, 4, 0, 0]}
                            />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>検索パフォーマンス</CardTitle>
                    <CardDescription>過去30日間の検索数推移</CardDescription>
                  </CardHeader>
                  <CardContent>
                    {searchData?.performance ? (
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={searchData.performance}>
                            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                            <XAxis dataKey="date" className="text-xs" tick={{ fontSize: 12 }} />
                            <YAxis className="text-xs" tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Line
                              type="monotone"
                              dataKey="searches"
                              name="検索数"
                              stroke="#0ea5e9"
                              strokeWidth={2}
                              dot={{ r: 3, fill: '#0ea5e9' }}
                              activeDot={{ r: 5 }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <DataBadge />
                    )}
                  </CardContent>
                </Card>
              </div>
            </div>
          )}
        </TabsContent>

        {/* ===== Tab 5: レポート生成 ===== */}
        <TabsContent value="report">
          <Card>
            <CardHeader>
              <CardTitle>カスタムレポート生成</CardTitle>
              <CardDescription>条件を指定してレポートを生成・ダウンロードできます</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit(onSubmitReport)} className="space-y-6">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="reportType">レポートタイプ</Label>
                    <Select onValueChange={(v) => setValue('reportType', String(v ?? ''))}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="選択してください" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="sales">売上レポート</SelectItem>
                        <SelectItem value="users">ユーザーレポート</SelectItem>
                        <SelectItem value="products">商品レポート</SelectItem>
                        <SelectItem value="inventory">在庫レポート</SelectItem>
                      </SelectContent>
                    </Select>
                    {errors.reportType && (
                      <p className="text-destructive text-xs">{errors.reportType.message}</p>
                    )}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="format">フォーマット</Label>
                    <Select onValueChange={(v) => setValue('format', String(v ?? ''))}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="選択してください" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="csv">CSV</SelectItem>
                        <SelectItem value="pdf">PDF</SelectItem>
                      </SelectContent>
                    </Select>
                    {errors.format && (
                      <p className="text-destructive text-xs">{errors.format.message}</p>
                    )}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="startDate">開始日</Label>
                    <Input id="startDate" type="date" {...register('startDate')} />
                    {errors.startDate && (
                      <p className="text-destructive text-xs">{errors.startDate.message}</p>
                    )}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="endDate">終了日</Label>
                    <Input id="endDate" type="date" {...register('endDate')} />
                    {errors.endDate && (
                      <p className="text-destructive text-xs">{errors.endDate.message}</p>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-4">
                  <Button type="submit" disabled={reportLoading}>
                    {reportLoading && <Loader2 className="mr-2 size-4 animate-spin" />}
                    レポート生成
                  </Button>

                  {reportResult && (
                    <a
                      href={reportResult.downloadUrl}
                      download={reportResult.fileName}
                      className="text-primary inline-flex items-center gap-1 text-sm hover:underline"
                    >
                      <Download className="size-4" />
                      {reportResult.fileName}
                    </a>
                  )}
                </div>
              </form>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ===== Tab 6: AI 分析 ===== */}
        <TabsContent value="ai-analyzer">
          <div className="space-y-6">
            <WeeklySummaryCard />
            <SeasonalForecastPanel />
            <DeadStockRadar />
            <ZeroHitRadar />
            <AiChatPanel />
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
