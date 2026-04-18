import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

async function safeFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = {
    ...((init.headers as Record<string, string> | undefined) ?? {}),
    Connection: 'close',
  };
  const merged: RequestInit = { ...init, headers, cache: 'no-store' };
  let lastErr: unknown;
  for (let i = 0; i < 2; i++) {
    try {
      return await fetch(url, merged);
    } catch (e) {
      lastErr = e;
      const code = (e as { cause?: { code?: string } })?.cause?.code;
      const transient =
        code === 'UND_ERR_SOCKET' ||
        code === 'ECONNRESET' ||
        code === 'UND_ERR_CONNECT_TIMEOUT' ||
        code === 'UND_ERR_HEADERS_TIMEOUT';
      if (transient && i === 0) continue;
      throw lastErr;
    }
  }
  throw lastErr;
}

type Page = { page?: { totalElements?: number } };

async function jsonOrNull<T>(url: string, headers: Record<string, string>): Promise<T | null> {
  try {
    const res = await safeFetch(url, { headers });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

/**
 * 管理画面のユーザー詳細用に、ユーザーに紐づく集計値を集約して返す。
 *   - orderCount    : /api/v1/orders/customer/{id} の totalElements
 *   - pointsBalance : /api/v1/points/balance/{id}.balance（404 のときは 0）
 *   - tier          : /api/v1/tiers/user/{id}.tierLevel（404 のときは null）
 *
 * 上流の 1 つでも失敗してもサマリ全体は壊さず、判明分だけを返す。
 */
export async function GET(
  _request: Request,
  context: { params: Promise<{ id: string }> },
): Promise<NextResponse> {
  const { id } = await context.params;
  const session = await getServerSession(authOptions);
  if (!session?.user?.id || !session.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
  const headers = { Authorization: `Bearer ${session.accessToken}` };

  const [ordersPage, balance, tier] = await Promise.all([
    jsonOrNull<Page>(`${API_GATEWAY_URL}/api/v1/orders/customer/${id}?page=0&size=1`, headers),
    jsonOrNull<{ balance?: number; totalBalance?: number }>(
      `${API_GATEWAY_URL}/api/v1/points/balance/${id}`,
      headers,
    ),
    jsonOrNull<{ tierLevel?: string; tierName?: string }>(
      `${API_GATEWAY_URL}/api/v1/tiers/user/${id}`,
      headers,
    ),
  ]);

  return NextResponse.json({
    orderCount: ordersPage?.page?.totalElements ?? 0,
    pointsBalance: balance?.balance ?? balance?.totalBalance ?? 0,
    tier: tier?.tierLevel ?? tier?.tierName ?? null,
  });
}
