import { getServerSession } from 'next-auth';
import { NextResponse, type NextRequest } from 'next/server';
import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ sku: string }> }
) {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { sku } = await params;
  const body = await request.json();

  const res = await safeFetch(
    `${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/dead-stock/${encodeURIComponent(sku)}/issue-coupon`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.accessToken}`,
      },
      body: JSON.stringify(body),
    }
  );

  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error');
    return NextResponse.json({ error: text }, { status: res.status });
  }

  return NextResponse.json(await res.json());
}
