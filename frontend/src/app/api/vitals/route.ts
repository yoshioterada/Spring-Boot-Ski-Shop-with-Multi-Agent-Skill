import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    // In production, forward to analytics service
    if (process.env.NODE_ENV === 'development') {
      console.log('[WebVitals]', body.name, body.value, body.rating);
    }
    return NextResponse.json({ received: true });
  } catch {
    return NextResponse.json({ error: 'Invalid data' }, { status: 400 });
  }
}
