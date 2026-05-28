import { type NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

type InventoryProduct = {
  id: string;
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: string;
  regularPrice: number;
  salePrice?: number | null;
  currency: string;
  stockQuantity: number;
  availableQuantity: number;
  status: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
  attributes?: Record<string, string>;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
};

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const query = searchParams.get('query') || searchParams.get('q') || '';
  const size = searchParams.get('size') || '20';
  const page = searchParams.get('page') || '0';

  if (!query.trim()) {
    return NextResponse.json({ results: [], totalHits: 0, query });
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
    const content: InventoryProduct[] = (data.content ?? []).filter((p: InventoryProduct) => (
      p.status === 'ACTIVE' && (p.availableQuantity ?? 0) > 0
    ));
    const totalElements = data.page?.totalElements ?? data.totalElements ?? content.length;

    // Transform to SearchResponse format expected by the frontend
    const results = content.map((p, index) => ({
      productId: p.id,
      sku: p.sku,
      name: p.name,
      description: p.description,
      brand: p.brand,
      categoryId: p.categoryId,
      price: p.salePrice ?? p.regularPrice,
      regularPrice: p.regularPrice,
      salePrice: p.salePrice ?? null,
      inStock: p.availableQuantity > 0,
      score: 1 / (index + 1),
    }));

    return NextResponse.json({
      results,
      totalHits: totalElements,
      query,
      source: 'inventory',
    }, { status: 200 });
  } catch {
    return NextResponse.json({ detail: '検索に失敗しました' }, { status: 502 });
  }
}
