import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

async function safeFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> | undefined ?? {}),
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

function normalizePage<T>(data: T): T {
  if (data && typeof data === 'object' && 'content' in (data as object) && 'page' in (data as object)) {
    const d = data as Record<string, unknown>;
    const p = (d.page ?? {}) as Record<string, unknown>;
    return {
      ...(data as object),
      totalElements: p.totalElements ?? 0,
      totalPages: p.totalPages ?? 0,
      size: p.size ?? 0,
      number: p.number ?? 0,
    } as T;
  }
  return data;
}

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    const { searchParams } = new URL(request.url);

    // バックエンド GET /api/v1/coupons は campaignId が必須。
    // ない場合は全キャンペーンを取得して、各キャンペーンに紐づくクーポンを集約する。
    const campaignId = searchParams.get('campaignId');
    const headers = { Authorization: `Bearer ${session.accessToken}` };

    if (campaignId) {
      const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/coupons?${searchParams.toString()}`, { headers });
      const data = await res.json().catch(() => null);
      return NextResponse.json(normalizePage(data ?? {}), { status: res.status });
    }

    // 全キャンペーンを取得
    const campaignsRes = await safeFetch(`${API_GATEWAY_URL}/api/v1/campaigns?page=0&size=100`, { headers });
    if (!campaignsRes.ok) {
      return NextResponse.json({ content: [], page: { totalElements: 0, size: 20, number: 0, totalPages: 0 } });
    }
    const campaignsBody = await campaignsRes.json().catch(() => ({ content: [] as Array<{ id: string }> }));
    const campaignIds: string[] = (campaignsBody.content ?? []).map((c: { id: string }) => c.id);

    if (campaignIds.length === 0) {
      return NextResponse.json({ content: [], page: { totalElements: 0, size: 20, number: 0, totalPages: 0 } });
    }

    // 各キャンペーンのクーポンを並列取得
    const pageSize = parseInt(searchParams.get('size') ?? '20', 10);
    const allCoupons = (
      await Promise.all(
        campaignIds.map(async (cid) => {
          const r = await safeFetch(
            `${API_GATEWAY_URL}/api/v1/coupons?campaignId=${cid}&page=0&size=${pageSize}`,
            { headers },
          );
          if (!r.ok) return [];
          const body = await r.json().catch(() => ({ content: [] }));
          return body.content ?? [];
        }),
      )
    ).flat();

    return NextResponse.json({
      content: allCoupons,
      page: { totalElements: allCoupons.length, size: pageSize, number: 0, totalPages: 1 },
    });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await request.json();
    const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/coupons`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.accessToken}` },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => null);
    return NextResponse.json(normalizePage(data ?? {}), { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
