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

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await request.json();
    const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/coupons/bulk`, {
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
