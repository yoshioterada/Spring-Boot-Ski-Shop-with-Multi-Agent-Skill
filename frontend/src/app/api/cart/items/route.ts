import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

// Docker では API Gateway 経由で payment-cart-service に到達する。
// CART_SERVICE_URL を明示的に指定すれば直接 payment-cart-service にも接続可能。
const CART_SERVICE_URL =
  process.env.CART_SERVICE_URL || process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const body = await request.json();
    const res = await fetch(`${CART_SERVICE_URL}/api/v1/cart/items?userId=${session.user.id}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.accessToken}`,
      },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
