import { NextRequest, NextResponse } from 'next/server';

const API_BASE_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    const res = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': crypto.randomUUID(),
      },
      body: JSON.stringify(body),
    });

    const data = await res.json();

    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }

    return NextResponse.json(data);
  } catch {
    return NextResponse.json({ detail: 'トークンリフレッシュに失敗しました' }, { status: 500 });
  }
}
