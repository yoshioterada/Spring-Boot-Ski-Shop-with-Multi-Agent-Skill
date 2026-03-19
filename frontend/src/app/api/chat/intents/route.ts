import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://localhost:8087';

export async function GET() {
  try {
    const session = await getServerSession(authOptions);

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
    };
    if (session?.accessToken) {
      headers['Authorization'] = `Bearer ${session.accessToken}`;
    }

    const res = await fetch(`${AI_SERVICE_URL}/api/v1/chat/intents`, {
      method: 'GET',
      headers,
    });

    if (res.ok) {
      const data = await res.json().catch(() => null);
      return NextResponse.json(data, { status: res.status });
    }

    // If backend returns 403/401, return default intents so widget shows as available
    return NextResponse.json({
      intents: [
        { name: '商品について相談する' },
        { name: 'おすすめを教えて' },
        { name: 'サイズの選び方' },
      ],
    });
  } catch {
    return NextResponse.json({ error: 'インテント一覧の取得に失敗しました' }, { status: 500 });
  }
}
