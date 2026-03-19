import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const ORDER_SERVICE_URL = process.env.ORDER_SERVICE_URL || 'http://localhost:8087';

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const { searchParams } = new URL(request.url);
    const orderNumber = searchParams.get('orderNumber');
    if (!orderNumber) {
      return NextResponse.json({ error: 'orderNumber required' }, { status: 400 });
    }

    const res = await fetch(`${ORDER_SERVICE_URL}/api/v1/orders/number/${orderNumber}`);
    if (!res.ok) {
      return NextResponse.json({ error: 'Not found' }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
