import { getServerSession } from 'next-auth';
import { NextResponse, type NextRequest } from 'next/server';
import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function GET() {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/dead-stock`, {
    headers: {
      Authorization: `Bearer ${session.accessToken}`,
    },
  });

  if (!res.ok) {
    return NextResponse.json({ error: 'Upstream error' }, { status: res.status });
  }

  return NextResponse.json(await res.json());
}
