import { type NextRequest, NextResponse } from 'next/server';

import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const params = new URLSearchParams();

  for (const [key, value] of searchParams.entries()) {
    params.set(key, value);
  }

  const categoryId = params.get('categoryId');
  if (categoryId) {
    params.delete('categoryId');
  }

  if (!params.has('size')) params.set('size', '20');
  if (!params.has('page')) params.set('page', '0');

  try {
    const endpoint = categoryId
      ? `${INVENTORY_SERVICE_URL}/api/v1/products/category/${encodeURIComponent(categoryId)}?${params.toString()}`
      : `${INVENTORY_SERVICE_URL}/api/v1/products?${params.toString()}`;
    const res = await safeFetch(endpoint, {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': crypto.randomUUID(),
      },
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (e) {
    return NextResponse.json({ detail: '商品の取得に失敗しました', error: String(e) }, { status: 502 });
  }
}
