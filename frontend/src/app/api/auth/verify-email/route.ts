import { NextRequest, NextResponse } from 'next/server';

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const res = await fetch(`${AUTH_SERVICE_URL}/api/v1/auth/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => null);
    return NextResponse.json(data ?? { status: 'ok' }, { status: res.status });
  } catch {
    return NextResponse.json({ detail: 'サービスに接続できません' }, { status: 503 });
  }
}
