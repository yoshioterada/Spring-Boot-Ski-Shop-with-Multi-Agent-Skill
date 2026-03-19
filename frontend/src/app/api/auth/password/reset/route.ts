import { NextRequest, NextResponse } from 'next/server';

const AUTH_SERVICE_URL = process.env.AUTH_SERVICE_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    await fetch(`${AUTH_SERVICE_URL}/api/v1/auth/password/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    // Always return 202 to prevent email enumeration
    return NextResponse.json({ message: 'リセットメールを送信しました' }, { status: 202 });
  } catch {
    return NextResponse.json({ message: 'リセットメールを送信しました' }, { status: 202 });
  }
}
