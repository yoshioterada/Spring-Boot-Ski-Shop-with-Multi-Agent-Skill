import { NextResponse } from 'next/server';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET() {
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID(),
  };

  const [categoriesRes, trendingRes, campaignsRes] = await Promise.allSettled([
    fetch(`${API_BASE}/api/v1/categories`, { headers, next: { revalidate: 300 } }),
    fetch(`${API_BASE}/api/v1/recommendations/trending`, { headers, next: { revalidate: 300 } }),
    fetch(`${API_BASE}/api/v1/campaigns/active`, { headers, next: { revalidate: 300 } }),
  ]);

  const categories =
    categoriesRes.status === 'fulfilled' && categoriesRes.value.ok
      ? await categoriesRes.value.json()
      : [];

  const trending =
    trendingRes.status === 'fulfilled' && trendingRes.value.ok
      ? await trendingRes.value.json()
      : null;

  const campaigns =
    campaignsRes.status === 'fulfilled' && campaignsRes.value.ok
      ? await campaignsRes.value.json()
      : [];

  return NextResponse.json({
    categories,
    trending,
    campaigns,
  });
}
