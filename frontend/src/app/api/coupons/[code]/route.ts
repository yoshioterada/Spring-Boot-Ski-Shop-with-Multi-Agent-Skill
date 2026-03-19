import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const COUPON_SERVICE_URL = process.env.COUPON_SERVICE_URL || 'http://localhost:8085';

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ code: string }> },
) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const { code } = await params;

    const res = await fetch(`${COUPON_SERVICE_URL}/api/v1/coupons/${encodeURIComponent(code)}`);

    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
