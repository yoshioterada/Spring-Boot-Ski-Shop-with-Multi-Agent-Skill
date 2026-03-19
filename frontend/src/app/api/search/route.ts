import { type NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const params = new URLSearchParams();

  for (const [key, value] of searchParams.entries()) {
    params.set(key, value);
  }

  try {
    const res = await fetch(`${API_BASE}/api/v1/search?${params.toString()}`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': crypto.randomUUID(),
      },
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ detail: '検索に失敗しました' }, { status: 502 });
  }
}
