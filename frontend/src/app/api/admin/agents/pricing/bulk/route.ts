import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const AGENT_TIMEOUT_MS = 180_000;

/**
 * 管理者用：動的価格エージェント `bulk` を呼び出す BFF。
 * 複数商品を一括で価格再計算する。
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
  if (!body || !Array.isArray(body.items) || body.items.length === 0) {
    return NextResponse.json({ error: 'items は必須（配列・1件以上）です' }, { status: 400 });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), AGENT_TIMEOUT_MS);

  try {
    const res = await fetch(`${API_GATEWAY_URL}/api/v1/admin/agents/pricing/bulk`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        userId: session.user.id,
        customerTier: typeof body.customerTier === 'string' ? body.customerTier : 'STANDARD',
        resortLocation: typeof body.resortLocation === 'string' ? body.resortLocation : null,
        items: body.items.map((it: { productId: string; quantity?: number }) => ({
          productId: it.productId,
          quantity: typeof it.quantity === 'number' && it.quantity > 0 ? it.quantity : 1,
        })),
      }),
      signal: controller.signal,
      cache: 'no-store',
    });
    const data = await res.json().catch(() => null);
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
