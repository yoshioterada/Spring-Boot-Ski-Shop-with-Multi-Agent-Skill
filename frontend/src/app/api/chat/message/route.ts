import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8087';

export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();

    const res = await fetch(`${AI_SERVICE_URL}/api/v1/chat/message`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.accessToken}`,
        'X-Request-Id': crypto.randomUUID(),
      },
      body: JSON.stringify({ ...body, userId: session.user.id }),
    });

    const data = await res.json().catch(() => null);
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'メッセージの送信に失敗しました' }, { status: 500 });
  }
}
