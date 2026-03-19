import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const ORDER_SERVICE_URL = process.env.ORDER_SERVICE_URL || 'http://localhost:8087';
const USER_SERVICE_URL = process.env.USER_SERVICE_URL || 'http://localhost:8081';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || 'http://localhost:8083';
const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8088';
const MAIL_SERVICE_URL = process.env.MAIL_SERVICE_URL || 'http://localhost:8089';

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });

    const [ordersRes, usersRes, lowStockRes, analyticsRes, mailRes] = await Promise.allSettled([
      fetch(`${ORDER_SERVICE_URL}/api/v1/admin/orders?page=0&size=5&sort=createdAt,desc`),
      fetch(`${USER_SERVICE_URL}/api/v1/admin/users?page=0&size=5`),
      fetch(`${INVENTORY_SERVICE_URL}/api/v1/inventory/low-stock?threshold=10`),
      fetch(`${AI_SERVICE_URL}/api/v1/analytics/dashboard?dashboardType=overview`),
      fetch(`${MAIL_SERVICE_URL}/api/v1/mail/logs?page=0&size=5`),
    ]);

    const getData = async (res: PromiseSettledResult<Response>) => {
      if (res.status === 'fulfilled' && res.value.ok) return res.value.json();
      return null;
    };

    return NextResponse.json({
      recentOrders: await getData(ordersRes),
      recentUsers: await getData(usersRes),
      lowStock: await getData(lowStockRes),
      analytics: await getData(analyticsRes),
      recentMails: await getData(mailRes),
    });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
