import { type NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const USER_SERVICE_URL = process.env.USER_SERVICE_URL || 'http://localhost:8081';

export async function PUT(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();
    const res = await fetch(`${USER_SERVICE_URL}/api/v1/users/${session.user.id}/password`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': crypto.randomUUID(),
      },
      body: JSON.stringify(body),
    });

    const data = await res.json().catch(() => null);
    return NextResponse.json(data ?? { status: 'ok' }, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
