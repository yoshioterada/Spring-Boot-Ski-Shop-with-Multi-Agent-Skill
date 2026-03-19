import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const CART_SERVICE_URL = process.env.CART_SERVICE_URL || 'http://localhost:8084';
const POINT_SERVICE_URL = process.env.POINT_SERVICE_URL || 'http://localhost:8085';
const COUPON_SERVICE_URL = process.env.COUPON_SERVICE_URL || 'http://localhost:8088';

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const userId = session.user.id;

    const authHeader = { Authorization: `Bearer ${session.accessToken}` };

    const [cartRes, pointsRes, couponsRes] = await Promise.allSettled([
      fetch(`${CART_SERVICE_URL}/api/v1/cart?userId=${userId}`, { headers: authHeader }),
      fetch(`${POINT_SERVICE_URL}/api/v1/points/balance/${userId}`, { headers: authHeader }),
      fetch(`${COUPON_SERVICE_URL}/api/v1/coupons/user/available?userId=${userId}`, { headers: authHeader }),
    ]);

    const cart =
      cartRes.status === 'fulfilled' && cartRes.value.ok
        ? await cartRes.value.json()
        : { items: [], totalAmount: 0 };
    const points =
      pointsRes.status === 'fulfilled' && pointsRes.value.ok ? await pointsRes.value.json() : null;
    const coupons =
      couponsRes.status === 'fulfilled' && couponsRes.value.ok
        ? await couponsRes.value.json()
        : null;

    return NextResponse.json({ cart, points, coupons });
  } catch {
    return NextResponse.json({ error: 'Failed to fetch cart' }, { status: 500 });
  }
}

export async function DELETE() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const res = await fetch(`${CART_SERVICE_URL}/api/v1/cart?userId=${session.user.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });
    return NextResponse.json({ status: 'ok' }, { status: res.ok ? 200 : res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
