import { type NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const params = new URLSearchParams();

  for (const [key, value] of searchParams.entries()) {
    params.set(key, value);
  }

  if (!params.has('size')) params.set('size', '20');
  if (!params.has('page')) params.set('page', '0');

  try {
    const res = await fetch(`${API_BASE}/api/v1/products?${params.toString()}`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': crypto.randomUUID(),
      },
      cache: 'no-store',
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ detail: '商品の取得に失敗しました' }, { status: 502 });
  }
}
