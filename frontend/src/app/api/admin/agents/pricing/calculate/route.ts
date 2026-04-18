import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const AGENT_TIMEOUT_MS = 120_000;

/**
 * 管理者用：動的価格エージェント `calculate` を呼び出す BFF。
 * 1 商品の価格を再計算する。
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
  if (!body || typeof body.productId !== 'string' || !body.productId) {
    return NextResponse.json({ error: 'productId は必須です' }, { status: 400 });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), AGENT_TIMEOUT_MS);

  try {
    const res = await fetch(`${API_GATEWAY_URL}/api/v1/admin/agents/pricing/calculate`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        productId: body.productId,
        userId: session.user.id,
        customerTier: typeof body.customerTier === 'string' ? body.customerTier : 'STANDARD',
        resortLocation: typeof body.resortLocation === 'string' ? body.resortLocation : null,
        quantity: typeof body.quantity === 'number' && body.quantity > 0 ? body.quantity : 1,
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
