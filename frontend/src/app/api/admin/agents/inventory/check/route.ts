import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const AGENT_TIMEOUT_MS = 120_000;

/**
 * 管理者用：在庫監視エージェント `check` を呼び出す BFF。
 * フロントから受け取った productIds[] と requiredQuantity を、
 * api-gateway 経由で `/api/v1/admin/agents/inventory/check` にプロキシする。
 */
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions);
  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }
  if (session.user.role !== 'ADMIN') {
    return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
  }

  const body = await request.json().catch(() => null);
  if (!body || !Array.isArray(body.productIds) || body.productIds.length === 0) {
    return NextResponse.json({ error: 'productIds は必須（配列・1件以上）です' }, { status: 400 });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), AGENT_TIMEOUT_MS);

  try {
    const payload = {
      productIds: body.productIds,
      requiredQuantity: typeof body.requiredQuantity === 'number' ? body.requiredQuantity : 1,
    };
    console.info('[admin/agents/inventory/check] request', payload);
    const res = await fetch(`${API_GATEWAY_URL}/api/v1/admin/agents/inventory/check`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
      cache: 'no-store',
    });
    const text = await res.text();
    console.info(
      '[admin/agents/inventory/check] response',
      res.status,
      text.slice(0, 500),
    );
    let data: unknown = null;
    try {
      data = JSON.parse(text);
    } catch {
      // keep null
    }
    return NextResponse.json(data ?? {}, { status: res.status });
  } catch (err) {
    const aborted = (err as { name?: string })?.name === 'AbortError';
    return NextResponse.json(
      { error: aborted ? 'タイムアウトしました' : 'エージェント呼び出しに失敗しました' },
      { status: aborted ? 504 : 502 },
    );
  } finally {
    clearTimeout(timeout);
  }
}
