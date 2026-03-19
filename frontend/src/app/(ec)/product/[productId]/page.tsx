import type { Metadata } from 'next';

import ProductDetailClient from './product-detail-client';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8090';

export async function generateMetadata({
  params,
}: {
  params: Promise<{ productId: string }>;
}): Promise<Metadata> {
  const { productId } = await params;

  try {
    const res = await fetch(`${API_BASE}/api/v1/products/${productId}`, {
      next: { revalidate: 300 },
    });

    if (!res.ok) {
      return { title: '商品詳細 | Azure SkiShop' };
    }

    const data = await res.json();
    const product = data.product ?? data;

    return {
      title: `${product.name} | Azure SkiShop`,
      description: product.description?.slice(0, 160) || `${product.name} - Azure SkiShop`,
      openGraph: {
        title: product.name,
        description: product.description?.slice(0, 160),
        type: 'website',
        locale: 'ja_JP',
      },
    };
  } catch {
    return { title: '商品詳細 | Azure SkiShop' };
  }
}

export default function ProductDetailPage() {
  return <ProductDetailClient />;
}
