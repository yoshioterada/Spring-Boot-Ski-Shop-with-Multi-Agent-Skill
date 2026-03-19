import { ArrowRight, TrendingUp } from 'lucide-react';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';

import { ProductCard } from './product-card';

import type { ProductResponse } from '@/types/api';

interface TrendingProduct {
  productId: string;
  reason: string;
  score: number;
}

async function getTrending(): Promise<ProductResponse[]> {
  try {
    const baseUrl = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000';
    const res = await fetch(`${baseUrl}/api/dashboard/home`, {
      next: { revalidate: 300 },
    });
    if (!res.ok) return [];
    const data = await res.json();

    return (
      (data.trending?.products as TrendingProduct[] | undefined)?.map(
        (p: TrendingProduct, i: number) => ({
          id: p.productId || `trending-${i}`,
          sku: `SKU-${i}`,
          name: p.reason || `トレンド商品 ${i + 1}`,
          description: '',
          brand: 'Azure Ski',
          categoryId: '1',
          regularPrice: 15000 + i * 5000,
          salePrice: i % 3 === 0 ? 12000 + i * 3000 : null,
          currency: 'JPY',
          stockQuantity: 10 + i,
          availableQuantity: 10 + i,
          status: 'ACTIVE' as const,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
      ) || []
    );
  } catch {
    return [];
  }
}

export async function TrendingSection() {
  const products = await getTrending();

  if (products.length === 0) {
    return null;
  }

  return (
    <section>
      <div className="mb-8 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <TrendingUp className="text-primary h-6 w-6" />
          <h2 className="text-3xl font-bold">トレンド商品</h2>
        </div>
        <Link href="/catalog" className={buttonVariants({ variant: 'ghost', className: 'gap-1' })}>
          すべて見る
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {products.slice(0, 10).map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </section>
  );
}
