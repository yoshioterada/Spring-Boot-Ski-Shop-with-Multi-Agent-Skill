'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { BarChart3, Download, FileText, Loader2, Search, TrendingUp, Users } from 'lucide-react';
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
  category: string;
  revenue: number;
}

interface SalesData {
  forecast: SalesForecast[] | null;
  topProducts: ProductPerformance[] | null;
  categoryRevenue: CategoryRevenue[] | null;
}

interface UserStats {
  visits: number | null;
  sessionDuration: number | null;
  conversionRate: number | null;
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
  [category: string]: string | number;
}

interface TrendsData {
  trends: TrendData[] | null;
  categories: string[] | null;
}

interface SearchTerm {
  rank: number;
  keyword: string;
  count: number;
  conversionRate: number;
}

interface SearchRate {
  period: string;
  hitRate: number;
  zeroHitRate: number;
}

interface SearchPerformance {
  date: string;
  searches: number;
}

interface SearchData {
  popularTerms: SearchTerm[] | null;
  hitRates: SearchRate[] | null;
  performance: SearchPerformance[] | null;
}

interface ReportResult {
  downloadUrl: string;
  fileName: string;
}

// --- Mock Data ---

const MOCK_SALES: SalesData = {
  forecast: Array.from({ length: 14 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 13 + i);
    const base = 800000 + Math.floor(Math.random() * 400000);
    return {
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      actual: i < 10 ? base : 0,
      forecast: base + Math.floor(Math.random() * 100000 - 50000),
    };
  }),
  topProducts: Array.from({ length: 10 }, (_, i) => ({
    id: `p-${i + 1}`,
    name: [
      'オールマウンテンスキー PRO',
      'パウダースキー DEEP',
      'レーシングスキー GS',
      'フリースタイルスキー PARK',
      'ツーリングスキー LITE',
      'ジュニアスキーセット',
      'スキーブーツ FLEX120',
      'ゴーグル ハイコントラスト',
      'スキーウェア 上下セット',
      'ヘルメット MIPS',
    ][i],
    revenue: 5000000 - i * 400000 + Math.floor(Math.random() * 100000),
    quantity: 200 - i * 15,
    category: [
      'スキー',
      'スキー',
      'スキー',
      'スキー',
      'スキー',
      'スキー',
      'ブーツ',
      'アクセサリー',
      'ウェア',
      'アクセサリー',
    ][i],
  })),
  categoryRevenue: [
    { category: 'スキー', revenue: 18000000 },
    { category: 'ブーツ', revenue: 8500000 },
    { category: 'ウェア', revenue: 6200000 },
    { category: 'アクセサリー', revenue: 4800000 },
    { category: 'ポール', revenue: 1500000 },
  ],
};

const MOCK_USERS: UsersData = {
  stats: { visits: 45230, sessionDuration: 342, conversionRate: 3.8 },
  segments: [
    { segment: '新規', count: 3200 },
    { segment: 'リピート', count: 5800 },
    { segment: '休眠', count: 1400 },
  ],
  registrations: Array.from({ length: 30 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 29 + i);
    return {
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      count: 30 + Math.floor(Math.random() * 50),
    };
  }),
};

const TREND_CATEGORIES = ['スキー', 'ブーツ', 'ウェア', 'アクセサリー'];

function makeMockTrends(days: number): TrendsData {
  return {
    categories: TREND_CATEGORIES,
    trends: Array.from({ length: days }, (_, i) => {
      const d = new Date();
      d.setDate(d.getDate() - days + 1 + i);
      const row: TrendData = { date: `${d.getMonth() + 1}/${d.getDate()}` };
      TREND_CATEGORIES.forEach((c) => {
        row[c] = 100000 + Math.floor(Math.random() * 200000);
      });
      return row;
    }),
  };
}

const MOCK_SEARCH: SearchData = {
  popularTerms: [
    { rank: 1, keyword: 'スキー板', count: 1840, conversionRate: 8.2 },
    { rank: 2, keyword: 'ゴーグル', count: 1520, conversionRate: 12.1 },
    { rank: 3, keyword: 'ブーツ 初心者', count: 1210, conversionRate: 6.5 },
    { rank: 4, keyword: 'ウェア セール', count: 980, conversionRate: 15.3 },
    { rank: 5, keyword: 'ヘルメット MIPS', count: 870, conversionRate: 9.8 },
    { rank: 6, keyword: 'ワックス', count: 650, conversionRate: 18.4 },
    { rank: 7, keyword: 'ポール カーボン', count: 540, conversionRate: 7.1 },
    { rank: 8, keyword: 'キッズ スキー', count: 430, conversionRate: 5.6 },
    { rank: 9, keyword: 'バインディング', count: 380, conversionRate: 11.2 },
    { rank: 10, keyword: 'インソール', count: 320, conversionRate: 22.0 },
  ],
  hitRates: [
    { period: '今週', hitRate: 82.5, zeroHitRate: 17.5 },
    { period: '先週', hitRate: 79.3, zeroHitRate: 20.7 },
    { period: '2週間前', hitRate: 80.1, zeroHitRate: 19.9 },
    { period: '3週間前', hitRate: 77.8, zeroHitRate: 22.2 },
  ],
  performance: Array.from({ length: 30 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 29 + i);
    return {
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      searches: 800 + Math.floor(Math.random() * 400),
    };
  }),
};

const PIE_COLORS = [
  'hsl(var(--primary))',
  'hsl(var(--chart-2, 160 60% 45%))',
  'hsl(var(--chart-3, 30 80% 55%))',
  'hsl(var(--chart-4, 280 65% 60%))',
  'hsl(var(--chart-5, 340 75% 55%))',
];

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
        if (type === 'sales') params.set('period', salesPeriod);
        if (type === 'trends') params.set('period', trendPeriod);

        const res = await fetch(`/api/admin/analytics?${params.toString()}`);
        const data = res.ok ? await res.json() : null;

        switch (type) {
          case 'sales':
            setSalesData(data ?? MOCK_SALES);
            break;
          case 'users':
            setUsersData(data ?? MOCK_USERS);
            break;
          case 'trends':
            setTrendsData(
              data ??
                makeMockTrends(
                  trendPeriod === '7d'
                    ? 7
                    : trendPeriod === '90d'
                      ? 90
                      : trendPeriod === '1y'
                        ? 365
                        : 30,
                ),
            );
            break;
          case 'search':
            setSearchData(data ?? MOCK_SEARCH);
            break;
        }
      } catch {
        switch (type) {
          case 'sales':
            setSalesData(MOCK_SALES);
            break;
          case 'users':
            setUsersData(MOCK_USERS);
            break;
          case 'trends':
            setTrendsData(makeMockTrends(30));
            break;
          case 'search':
            setSearchData(MOCK_SEARCH);
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
                            stroke="hsl(var(--primary))"
                            strokeWidth={2}
                            dot={false}
                          />
                          <Line
                            type="monotone"
                            dataKey="forecast"
                            name="予測"
                            stroke="hsl(var(--chart-2, 160 60% 45%))"
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
                              {salesData.categoryRevenue.map((_, idx) => (
                                <Cell key={idx} fill={PIE_COLORS[idx % PIE_COLORS.length]} />
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
                  title="訪問数"
                  value={usersData?.stats?.visits ? formatNumber(usersData.stats.visits) : null}
                  suffix="/ 月"
                  icon={Users}
                />
                <KpiCard
                  title="平均セッション時間"
                  value={
                    usersData?.stats?.sessionDuration
                      ? `${Math.floor(usersData.stats.sessionDuration / 60)}分${usersData.stats.sessionDuration % 60}秒`
                      : null
                  }
                  icon={BarChart3}
                />
                <KpiCard
                  title="コンバージョン率"
                  value={usersData?.stats?.conversionRate ?? null}
                  suffix="%"
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
                              stroke="hsl(var(--primary))"
                              strokeWidth={2}
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
              </div>
            </div>
          )}
        </TabsContent>

        {/* ===== Tab 3: トレンド分析 ===== */}
        <TabsContent value="trends">
          {loading ? (
            <SkeletonTable rows={4} columns={5} />
          ) : (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle>カテゴリ別トレンド</CardTitle>
                    <CardDescription>期間別の売上推移</CardDescription>
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
                          className="text-xs"
                          tick={{ fontSize: 12 }}
                          tickFormatter={(v) => `${(Number(v) / 10000).toFixed(0)}万`}
                        />
                        <Tooltip formatter={(v) => formatCurrency(Number(v))} />
                        <Legend />
                        {(trendsData.categories ?? TREND_CATEGORIES).map((cat, idx) => (
                          <Line
                            key={cat}
                            type="monotone"
                            dataKey={cat}
                            stroke={PIE_COLORS[idx % PIE_COLORS.length]}
                            strokeWidth={2}
                            dot={false}
                          />
                        ))}
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <DataBadge />
                )}
              </CardContent>
            </Card>
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
                          <TableHead className="text-right">CVR</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {searchData.popularTerms.map((t) => (
                          <TableRow key={t.rank}>
                            <TableCell className="font-medium">{t.rank}</TableCell>
                            <TableCell>{t.keyword}</TableCell>
                            <TableCell className="text-right">{formatNumber(t.count)}</TableCell>
                            <TableCell className="text-right">
                              {t.conversionRate.toFixed(1)}%
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
                              fill="hsl(var(--primary))"
                              radius={[4, 4, 0, 0]}
                            />
                            <Bar
                              dataKey="zeroHitRate"
                              name="ゼロヒット率"
                              fill="hsl(var(--chart-3, 30 80% 55%))"
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
                              stroke="hsl(var(--primary))"
                              strokeWidth={2}
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
      </Tabs>
    </div>
  );
}
