import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

async function safeFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = {
    ...(init.headers as Record<string, string> | undefined ?? {}),
    Connection: 'close',
  };
  const merged: RequestInit = { ...init, headers, cache: 'no-store' };
  let lastErr: unknown;
  for (let i = 0; i < 2; i++) {
    try {
      return await fetch(url, merged);
    } catch (e) {
      lastErr = e;
      const code = (e as { cause?: { code?: string } })?.cause?.code;
      const transient =
        code === 'UND_ERR_SOCKET' ||
        code === 'ECONNRESET' ||
        code === 'UND_ERR_CONNECT_TIMEOUT' ||
        code === 'UND_ERR_HEADERS_TIMEOUT';
      if (transient && i === 0) continue;
      throw lastErr;
    }
  }
  throw lastErr;
}

function normalizePage<T>(data: T): T {
  if (data && typeof data === 'object' && 'content' in (data as object) && 'page' in (data as object)) {
    const d = data as Record<string, unknown>;
    const p = (d.page ?? {}) as Record<string, unknown>;
    return {
      ...(data as object),
      totalElements: p.totalElements ?? 0,
      totalPages: p.totalPages ?? 0,
      size: p.size ?? 0,
      number: p.number ?? 0,
    } as T;
  }
  return data;
}

type UserSummary = {
  id: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
};

type OrderRow = Record<string, unknown> & {
  customerId?: string;
  customerEmail?: string;
  customerName?: string;
  customerPhone?: string;
};

async function fetchUserSummary(
  id: string,
  authHeader: Record<string, string>,
): Promise<UserSummary | null> {
  try {
    const r = await safeFetch(`${API_GATEWAY_URL}/api/v1/users/${id}`, { headers: authHeader });
    if (!r.ok) return null;
    return (await r.json()) as UserSummary;
  } catch {
    return null;
  }
}

/** customerId → 顧客情報を並列で取得して各注文に email/name/phone をマージ */
async function enrichWithCustomer(
  orders: OrderRow[],
  authHeader: Record<string, string>,
): Promise<OrderRow[]> {
  const ids = Array.from(
    new Set(orders.map((o) => o.customerId).filter((v): v is string => typeof v === 'string')),
  );
  if (ids.length === 0) return orders;

  const entries = await Promise.all(
    ids.map(async (id) => [id, await fetchUserSummary(id, authHeader)] as const),
  );
  const userMap = new Map<string, UserSummary | null>(entries);

  return orders.map((o) => {
    const u = o.customerId ? userMap.get(o.customerId) : null;
    if (!u) return o;
    const fullName = [u.lastName, u.firstName].filter(Boolean).join(' ').trim();
    return {
      ...o,
      customerEmail: o.customerEmail ?? u.email ?? '',
      customerName: o.customerName ?? (fullName || u.email || ''),
      customerPhone: o.customerPhone ?? u.phoneNumber ?? '',
    };
  });
}

export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const authHeader = { Authorization: `Bearer ${session.accessToken}` };
    const { searchParams } = new URL(request.url);
    const res = await safeFetch(
      `${API_GATEWAY_URL}/api/v1/admin/orders?${searchParams.toString()}`,
      { headers: authHeader },
    );
    const data = await res.json().catch(() => null);
    if (!res.ok || !data) {
      return NextResponse.json(normalizePage(data ?? {}), { status: res.status });
    }

    const normalized = normalizePage(data) as { content?: OrderRow[] } & Record<string, unknown>;
    if (Array.isArray(normalized.content)) {
      normalized.content = await enrichWithCustomer(normalized.content, authHeader);
    }
    return NextResponse.json(normalized, { status: res.status });
  } catch {
    return NextResponse.json({ error: 'Failed' }, { status: 500 });
  }
}
