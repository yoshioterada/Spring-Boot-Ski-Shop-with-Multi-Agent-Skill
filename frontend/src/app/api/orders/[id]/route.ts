import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const ORDER_SERVICE_URL = process.env.ORDER_SERVICE_URL || 'http://localhost:8083';
const SHIPMENT_SERVICE_URL = process.env.ORDER_SERVICE_URL || 'http://localhost:8083';

export async function GET(_request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const [orderRes, shipmentRes] = await Promise.allSettled([
      fetch(`${ORDER_SERVICE_URL}/api/v1/orders/${id}`),
      fetch(`${SHIPMENT_SERVICE_URL}/api/v1/shipments/order/${id}`),
    ]);

    if (orderRes.status !== 'fulfilled' || !orderRes.value.ok) {
      return NextResponse.json({ error: 'Order not found' }, { status: 404 });
    }

    const order = await orderRes.value.json();
    const shipment =
      shipmentRes.status === 'fulfilled' && shipmentRes.value.ok
        ? await shipmentRes.value.json()
        : null;

    return NextResponse.json({ order, shipment });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
