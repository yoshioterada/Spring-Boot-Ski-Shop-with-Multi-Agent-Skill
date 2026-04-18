import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

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

interface DashboardPayload {
  analytics: {
    kpi: KpiData;
    salesChart: { daily: SalesPoint[]; weekly: SalesPoint[]; monthly: SalesPoint[] };
  } | null;
  lowStock: LowStockItem[] | null;
  recentOrders: { content: RecentOrder[] } | null;
  recentUsers: { totalElements: number } | null;
  recentMails: unknown;
}

interface BackendOrder {
  id: string;
  orderNumber: string;
  customerId: string;
  status: string;
  totalAmount: number | string;
  createdAt: string;
}

interface BackendUser {
  id: string;
  firstName: string;
  lastName: string;
  status: string;
  createdAt: string;
}

interface BackendProduct {
  id: string;
  sku: string;
  name: string;
  stockQuantity: number;
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

async function callJson<T>(
  url: string,
  authHeaders: Record<string, string>,
  label: string,
): Promise<T | null> {
  // Spring Cloud Gateway は keep-alive のアイドル接続を短時間で切断するため、
  // undici の接続プール再利用で `UND_ERR_SOCKET: other side closed` が発生することがある。
  // 1) Connection: close で毎回新規接続を強制
  // 2) ソケット切断系エラーは 1 回だけリトライ
  const headers: Record<string, string> = {
    ...authHeaders,
    Connection: 'close',
  };
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(url, { headers, cache: 'no-store' });
      if (!res.ok) {
        console.warn(`[admin/dashboard] ${label} HTTP ${res.status} ${res.statusText} (${url})`);
        return null;
      }
      return (await res.json()) as T;
    } catch (e) {
      const cause = (e as { cause?: { code?: string } })?.cause;
      const isSocketErr =
        cause?.code === 'UND_ERR_SOCKET' || cause?.code === 'ECONNRESET' || cause?.code === 'UND_ERR_CONNECT_TIMEOUT';
      if (isSocketErr && attempt === 0) {
        continue; // リトライ
      }
      console.error(`[admin/dashboard] ${label} fetch error (${url}):`, e);
      return null;
    }
  }
  return null;
}

/**
 * Spring Data Pageable はデフォルトで最大ページサイズが 2000 に制限されているため、
 * 月別グラフに必要な過去 6 ヶ月分（数千〜数万件）を 1 リクエストで取得しきれない。
 * 全ページを順次取得して content を結合する。
 */
async function fetchAllOrders(
  baseUrl: string,
  authHeaders: Record<string, string>,
  pageSize = 2000,
  maxPages = 20,
): Promise<{ content: BackendOrder[]; totalElements: number } | null> {
  const all: BackendOrder[] = [];
  let totalElements = 0;
  for (let page = 0; page < maxPages; page++) {
    const url = `${baseUrl}?page=${page}&size=${pageSize}&sort=createdAt,desc`;
    const res = await callJson<Page<BackendOrder>>(url, authHeaders, `orders[p${page}]`);
    if (res === null) {
      // 1 ページ目で失敗した場合は完全失敗扱い
      if (page === 0) return null;
      break;
    }
    if (page === 0) totalElements = res.totalElements ?? 0;
    all.push(...res.content);
    if (res.content.length < pageSize) break; // 最終ページに到達
    // 6 ヶ月より古いデータが先頭に出てきたら早期終了（chart は最大 6 ヶ月）
    const oldest = res.content[res.content.length - 1];
    if (oldest && new Date(oldest.createdAt).getTime() < Date.now() - 200 * 24 * 60 * 60 * 1000) {
      break;
    }
  }
  return { content: all, totalElements };
}

function startOfDay(d: Date): Date {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

function trend(current: number, previous: number): number {
  if (previous === 0) return current === 0 ? 0 : 100;
  return ((current - previous) / previous) * 100;
}

function toNumber(v: number | string | null | undefined): number {
  if (v === null || v === undefined) return 0;
  if (typeof v === 'number') return v;
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

function buildSalesChart(orders: BackendOrder[]): {
  daily: SalesPoint[];
  weekly: SalesPoint[];
  monthly: SalesPoint[];
} {
  const dayMs = 24 * 60 * 60 * 1000;
  const today = startOfDay(new Date());
  const dayOfWeek = ['日', '月', '火', '水', '木', '金', '土'];

  const sumIn = (from: Date, to: Date): number =>
    orders
      .filter((o) => {
        const ts = new Date(o.createdAt).getTime();
        return ts >= from.getTime() && ts < to.getTime();
      })
      .reduce((acc, o) => acc + toNumber(o.totalAmount), 0);

  const daily: SalesPoint[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today.getTime() - i * dayMs);
    daily.push({
      label: dayOfWeek[d.getDay()],
      sales: sumIn(d, new Date(d.getTime() + dayMs)),
    });
  }

  const weekly: SalesPoint[] = [];
  for (let w = 3; w >= 0; w--) {
    const start = new Date(today.getTime() - (w + 1) * 7 * dayMs);
    const end = new Date(today.getTime() - w * 7 * dayMs);
    weekly.push({ label: `第${4 - w}週`, sales: sumIn(start, end) });
  }

  const monthly: SalesPoint[] = [];
  for (let m = 5; m >= 0; m--) {
    const start = new Date(today.getFullYear(), today.getMonth() - m, 1);
    const end = new Date(today.getFullYear(), today.getMonth() - m + 1, 1);
    monthly.push({ label: `${start.getMonth() + 1}月`, sales: sumIn(start, end) });
  }

  return { daily, weekly, monthly };
}

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const authHeaders = {
      Authorization: `Bearer ${session.accessToken}`,
      'Content-Type': 'application/json',
    };

    const [ordersPage, usersPage, lowStockPage] = await Promise.all([
      fetchAllOrders(`${API_GATEWAY_URL}/api/v1/admin/orders`, authHeaders),
      callJson<Page<BackendUser>>(
        `${API_GATEWAY_URL}/api/v1/admin/users?page=0&size=500&sort=createdAt,desc`,
        authHeaders,
        'users',
      ),
      callJson<Page<BackendProduct>>(
        `${API_GATEWAY_URL}/api/v1/inventory/low-stock?threshold=10&page=0&size=10`,
        authHeaders,
        'lowStock',
      ),
    ]);

    const top5Orders = (ordersPage?.content ?? []).slice(0, 5);
    const userIds = Array.from(new Set(top5Orders.map((o) => o.customerId).filter(Boolean)));
    const userLookup = new Map<string, BackendUser>();
    if (userIds.length > 0) {
      const userResults = await Promise.all(
        userIds.map((id) =>
          callJson<BackendUser>(
            `${API_GATEWAY_URL}/api/v1/users/${id}`,
            authHeaders,
            `user:${id}`,
          ),
        ),
      );
      userResults.forEach((u, i) => {
        if (u) userLookup.set(userIds[i], u);
      });
    }

    const today = startOfDay(new Date());
    const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000);
    const tomorrow = new Date(today.getTime() + 24 * 60 * 60 * 1000);

    const ordersInRange = (
      from: Date,
      to: Date,
    ): { count: number; sum: number } => {
      const list = (ordersPage?.content ?? []).filter((o) => {
        const ts = new Date(o.createdAt).getTime();
        return ts >= from.getTime() && ts < to.getTime();
      });
      return {
        count: list.length,
        sum: list.reduce((acc, o) => acc + toNumber(o.totalAmount), 0),
      };
    };
    const todayStats = ordersInRange(today, tomorrow);
    const yesterdayStats = ordersInRange(yesterday, today);

    const usersInRange = (from: Date, to: Date): number =>
      (usersPage?.content ?? []).filter((u) => {
        const ts = new Date(u.createdAt).getTime();
        return ts >= from.getTime() && ts < to.getTime();
      }).length;

    const newMembersToday = usersInRange(today, tomorrow);
    const newMembersYesterday = usersInRange(yesterday, today);

    const activeUsers = (usersPage?.content ?? []).filter((u) => u.status === 'ACTIVE').length;
    const totalUsers = usersPage?.totalElements ?? activeUsers;
    const activeRatio = totalUsers > 0 ? (activeUsers / totalUsers) * 100 : 0;

    const kpi: KpiData = {
      todaySales: todayStats.sum,
      todaySalesTrend: trend(todayStats.sum, yesterdayStats.sum),
      orderCount: todayStats.count,
      orderCountTrend: trend(todayStats.count, yesterdayStats.count),
      newMembers: newMembersToday,
      newMembersTrend: trend(newMembersToday, newMembersYesterday),
      activeUsers,
      activeUsersTrend: activeRatio,
    };

    const recentOrders: RecentOrder[] = top5Orders.map((o) => {
      const u = userLookup.get(o.customerId);
      const customerName = u
        ? `${u.lastName ?? ''} ${u.firstName ?? ''}`.trim() ||
          `顧客 #${(o.customerId ?? '').slice(0, 8)}`
        : `顧客 #${(o.customerId ?? '').slice(0, 8)}`;
      return {
        orderId: o.orderNumber || o.id,
        customerName,
        totalAmount: toNumber(o.totalAmount),
        status: o.status,
        createdAt: o.createdAt,
      };
    });

    const lowStock: LowStockItem[] = (lowStockPage?.content ?? []).map((p) => ({
      productId: p.sku ?? p.id,
      productName: p.name,
      currentStock: p.stockQuantity,
      threshold: 10,
    }));

    const payload: DashboardPayload = {
      analytics:
        ordersPage || usersPage
          ? {
              kpi,
              salesChart: buildSalesChart(ordersPage?.content ?? []),
            }
          : null,
      lowStock: lowStockPage ? lowStock : null,
      recentOrders: ordersPage ? { content: recentOrders } : null,
      recentUsers: usersPage ? { totalElements: usersPage.totalElements ?? 0 } : null,
      recentMails: null,
    };

    return NextResponse.json(payload);
  } catch (e) {
    console.error('[admin/dashboard] unexpected error:', e);
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
