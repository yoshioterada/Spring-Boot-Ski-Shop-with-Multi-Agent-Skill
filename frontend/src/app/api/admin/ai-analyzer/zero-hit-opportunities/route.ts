import { getServerSession } from 'next-auth';
import { NextResponse, type NextRequest } from 'next/server';
import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const days = searchParams.get('days') ?? '30';
  const minSearchCount = searchParams.get('minSearchCount') ?? '1';
  const limit = searchParams.get('limit') ?? '30';

  const url = new URL(`${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/zero-hit-opportunities`);
  url.searchParams.set('days', days);
  url.searchParams.set('minSearchCount', minSearchCount);
  url.searchParams.set('limit', limit);

  const res = await safeFetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${session.accessToken}`,
    },
  });

  if (!res.ok) {
    return NextResponse.json({ error: 'Upstream error' }, { status: res.status });
  }

  return NextResponse.json(await res.json());
}
