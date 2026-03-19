import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const POINT_SERVICE_URL = process.env.POINT_SERVICE_URL || 'http://localhost:8086';

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const userId = session.user.id;

    const [balanceResult, tierResult, historyResult, expiringResult] = await Promise.allSettled([
      fetch(`${POINT_SERVICE_URL}/api/v1/points/balance/${userId}`).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch(`${POINT_SERVICE_URL}/api/v1/tiers/user/${userId}`).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch(`${POINT_SERVICE_URL}/api/v1/points/history/${userId}?page=0&size=20`).then((r) =>
        r.ok ? r.json() : null,
      ),
      fetch(`${POINT_SERVICE_URL}/api/v1/points/expiring/${userId}`).then((r) =>
        r.ok ? r.json() : null,
      ),
    ]);

    return NextResponse.json({
      balance: balanceResult.status === 'fulfilled' ? balanceResult.value : null,
      tier: tierResult.status === 'fulfilled' ? tierResult.value : null,
      history: historyResult.status === 'fulfilled' ? historyResult.value : null,
      expiring: expiringResult.status === 'fulfilled' ? expiringResult.value : null,
    });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
