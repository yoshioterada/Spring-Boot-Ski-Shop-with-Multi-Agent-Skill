import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const ORDER_SERVICE_URL = process.env.ORDER_SERVICE_URL || 'http://localhost:8087';

export async function PUT(_request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const res = await fetch(`${ORDER_SERVICE_URL}/api/v1/orders/${id}/cancel`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: session.user.id }),
    });

    const data = await res.json().catch(() => null);
    return NextResponse.json(data ?? { status: 'ok' }, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
