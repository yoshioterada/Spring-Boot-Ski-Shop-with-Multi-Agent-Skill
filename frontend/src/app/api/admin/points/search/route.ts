import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const POINT_SERVICE_URL = process.env.POINT_SERVICE_URL || 'http://localhost:8086';

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const { searchParams } = new URL(request.url);
    const res = await fetch(
      `${POINT_SERVICE_URL}/api/v1/admin/points/search?${searchParams.toString()}`,
    );
    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed to search user points' }, { status: 500 });
  }
}
