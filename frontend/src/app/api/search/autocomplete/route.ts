import { type NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

type InventoryProduct = {
  id: string;
  name: string;
  brand: string;
  regularPrice: number;
  salePrice?: number | null;
  availableQuantity: number;
  status: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
};

export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get('query') || '';

  if (query.length < 2) {
    return NextResponse.json({ results: [] });
  }

  try {
    const res = await fetch(
      `${INVENTORY_SERVICE_URL}/api/v1/products/search?q=${encodeURIComponent(query)}&page=0&size=5`,
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

    const results = content.map((p) => ({
      productId: p.id,
      name: p.name,
      brand: p.brand,
      price: p.salePrice ?? p.regularPrice,
    }));

    return NextResponse.json({ results }, { status: 200 });
  } catch {
    return NextResponse.json({ results: [] });
  }
}
