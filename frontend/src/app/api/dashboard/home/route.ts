import { NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;
const COUPON_SERVICE_URL = process.env.COUPON_SERVICE_URL || API_GATEWAY_URL;

export async function GET() {
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID(),
  };

  const [categoriesRes, productsRes, campaignsRes] = await Promise.allSettled([
    fetch(`${INVENTORY_SERVICE_URL}/api/v1/categories`, { headers, next: { revalidate: 300 } }),
    fetch(`${INVENTORY_SERVICE_URL}/api/v1/products?page=0&size=8&sort=createdAt,desc`, { headers, next: { revalidate: 300 } }),
    fetch(`${COUPON_SERVICE_URL}/api/v1/campaigns/active`, { headers, next: { revalidate: 300 } }),
  ]);

  let categories: unknown[] = [];
  if (categoriesRes.status === 'fulfilled' && categoriesRes.value.ok) {
    const catData = await categoriesRes.value.json();
    categories = Array.isArray(catData) ? catData : (catData.content ?? []);
  }

  let personalizedProducts: unknown[] = [];
  if (productsRes.status === 'fulfilled' && productsRes.value.ok) {
    const prodData = await productsRes.value.json();
    personalizedProducts = Array.isArray(prodData) ? prodData : (prodData.content ?? []);
  }

  const campaigns =
    campaignsRes.status === 'fulfilled' && campaignsRes.value.ok
      ? await campaignsRes.value.json()
      : [];

  return NextResponse.json({
    categories,
    trending: null,
    campaigns,
    personalizedProducts,
  });
}
