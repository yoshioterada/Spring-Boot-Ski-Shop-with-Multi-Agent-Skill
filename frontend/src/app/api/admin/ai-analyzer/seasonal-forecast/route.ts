import { getServerSession } from 'next-auth';
import { NextResponse, type NextRequest } from 'next/server';
import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const body = await request.json();

  const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/seasonal-forecast`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.accessToken}`,
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    return NextResponse.json({ error: 'Upstream error' }, { status: res.status });
  }

  const data = await res.json();
  return NextResponse.json(data);
}
