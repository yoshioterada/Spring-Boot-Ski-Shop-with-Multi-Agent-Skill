import { type NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

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
    const content = data.content ?? [];

    const results = content.map((p: { id: string; name: string; brand: string; regularPrice: number }) => ({
      productId: p.id,
      name: p.name,
      brand: p.brand,
      price: p.regularPrice,
    }));

    return NextResponse.json({ results }, { status: 200 });
  } catch {
    return NextResponse.json({ results: [] });
  }
}
