import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

/**
 * AI Analyzer — 週次サマリー BFF Route (P1.2.2).
 *
 * GET  → /api/v1/admin/ai-analyzer/weekly-summary (キャッシュ付き)
 * POST → /api/v1/admin/ai-analyzer/weekly-summary/refresh (強制再生成)
 */

async function safeFetch(
  url: string,
  init: RequestInit = {},
): Promise<Response> {
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

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const url = `${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/weekly-summary`;
    const res = await safeFetch(url, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });
    const data = await res.json().catch(() => null);
    return NextResponse.json(data ?? {}, { status: res.status });
  } catch {
    return NextResponse.json(
      { error: 'Failed to fetch weekly summary' },
      { status: 500 },
    );
  }
}

export async function POST() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const url = `${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/weekly-summary/refresh`;
    const res = await safeFetch(url, {
      method: 'POST',
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });

    if (res.status === 429) {
      return NextResponse.json(
        { error: '再生成は1時間に1回までです' },
        { status: 429 },
      );
    }
    const data = await res.json().catch(() => null);
    return NextResponse.json(data ?? {}, { status: res.status });
  } catch {
    return NextResponse.json(
      { error: 'Failed to refresh weekly summary' },
      { status: 500 },
    );
  }
}
