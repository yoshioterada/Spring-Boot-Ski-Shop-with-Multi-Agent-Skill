import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8087';

export async function GET() {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const res = await fetch(`${AI_SERVICE_URL}/api/v1/chat/sessions/${session.user.id}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.accessToken}`,
        'X-Request-Id': crypto.randomUUID(),
      },
    });

    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'セッション一覧の取得に失敗しました' }, { status: 500 });
  }
}
