import { type NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.INVENTORY_SERVICE_URL || 'http://localhost:8082';

export async function GET(_request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID(),
  };

  const [productRes, similarRes] = await Promise.allSettled([
    fetch(`${API_BASE}/api/v1/products/${id}`, { headers }),
    fetch(`${API_BASE}/api/v1/recommendations/similar/${id}`, { headers }),
  ]);

  if (productRes.status === 'rejected' || !productRes.value.ok) {
    const status = productRes.status === 'fulfilled' ? productRes.value.status : 502;
    return NextResponse.json({ detail: '商品が見つかりません' }, { status });
  }

  const product = await productRes.value.json();
  const similar =
    similarRes.status === 'fulfilled' && similarRes.value.ok ? await similarRes.value.json() : null;

  return NextResponse.json({ product, similar });
}
