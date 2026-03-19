import { type NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get('query') || '';

  if (query.length < 2) {
    return NextResponse.json({ results: [] });
  }

  try {
    const res = await fetch(
      `${API_BASE}/api/v1/search/autocomplete?query=${encodeURIComponent(query)}`,
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Request-Id': crypto.randomUUID(),
        },
      },
    );

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ results: [] });
  }
}
