import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';
const AI_SERVICE_URL = process.env.AI_SERVICE_URL || API_GATEWAY_URL;

const DEFAULT_INTENTS = {
  intents: [
    { name: '商品について相談する' },
    { name: 'おすすめを教えて' },
    { name: 'サイズの選び方' },
    { name: '配送・返品について' },
  ],
};

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

    const res = await safeFetch(`${AI_SERVICE_URL}/api/v1/chat/intents`, {
      method: 'GET',
      headers,
    });

    if (res.ok) {
      const data = await res.json().catch(() => null);
      return NextResponse.json(data ?? DEFAULT_INTENTS, { status: 200 });
    }
  } catch {
    // fallthrough
  }
  // 未認証や AI サービス不通でも widget が動くようデフォルトを返す
  return NextResponse.json(DEFAULT_INTENTS, { status: 200 });
}
