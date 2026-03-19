import { type NextRequest, NextResponse } from 'next/server';

const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || 'http://localhost:8082';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const query = searchParams.get('query') || searchParams.get('q') || '';
  const size = searchParams.get('size') || '20';
  const page = searchParams.get('page') || '0';

  if (!query.trim()) {
    return NextResponse.json({ results: [], totalHits: 0 });
  }

  try {
    const res = await fetch(
      `${INVENTORY_SERVICE_URL}/api/v1/products/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`,
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Request-Id': crypto.randomUUID(),
        },
      },
    );

    const data = await res.json();
    const content = data.content ?? [];
    const totalElements = data.page?.totalElements ?? data.totalElements ?? content.length;

    // Transform to SearchResponse format expected by the frontend
    const results = content.map((p: { id: string; name: string; description: string; brand: string; regularPrice: number; salePrice?: number | null; sku: string }) => ({
      productId: p.id,
      name: p.name,
      description: p.description,
      brand: p.brand,
      price: p.salePrice ?? p.regularPrice,
      sku: p.sku,
    }));

    return NextResponse.json({ results, totalHits: totalElements }, { status: 200 });
  } catch {
    return NextResponse.json({ detail: '検索に失敗しました' }, { status: 502 });
  }
}
