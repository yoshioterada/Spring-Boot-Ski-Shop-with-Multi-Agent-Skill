import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';
const AI_SERVICE_URL = process.env.AI_SERVICE_URL || API_GATEWAY_URL;

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30000);

    const res = await safeFetch(`${AI_SERVICE_URL}/api/v1/chat/message`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.accessToken}`,
        'X-Request-Id': crypto.randomUUID(),
      },
      body: JSON.stringify({ ...body, userId: session.user.id }),
      signal: controller.signal,
    });

    clearTimeout(timeout);

    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'メッセージの送信に失敗しました' }, { status: 500 });
  }
}
