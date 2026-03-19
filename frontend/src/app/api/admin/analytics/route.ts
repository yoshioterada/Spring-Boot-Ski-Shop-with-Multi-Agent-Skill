import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8087';

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });

    const { searchParams } = new URL(request.url);
    const type = searchParams.get('type') || 'sales';

    const endpointMap: Record<string, string> = {
      sales: '/api/v1/analytics/sales',
      users: '/api/v1/analytics/users',
      trends: '/api/v1/analytics/trends',
      search: '/api/v1/analytics/search',
    };

    const endpoint = endpointMap[type];
    if (!endpoint) {
      return NextResponse.json({ error: 'Invalid analytics type' }, { status: 400 });
    }

    searchParams.delete('type');
    const qs = searchParams.toString();
    const url = `${AI_SERVICE_URL}${endpoint}${qs ? `?${qs}` : ''}`;

    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });
    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
