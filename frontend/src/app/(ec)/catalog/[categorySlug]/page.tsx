'use client';

import { useParams, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';

import { SkeletonCard } from '@/components/common/skeleton-card';
import { ProductCard } from '@/components/ec/product-card';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';

import type { PaginatedResponse, ProductResponse } from '@/types/api';

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '新着順' },
  { value: 'regularPrice,asc', label: '価格が安い順' },
  { value: 'regularPrice,desc', label: '価格が高い順' },
];

function CategoryContent() {
  const params = useParams();
  const searchParams = useSearchParams();
  const categorySlug = params.categorySlug as string;
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  // TODO: fetch category name from API
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [categoryName, _setCategoryName] = useState('');
  const sort = searchParams.get('sort') || 'createdAt,desc';

  const fetchProducts = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetch(
        `/api/products?categoryId=${categorySlug}&sort=${sort}&size=20&page=0`,
      );
      if (res.ok) {
        const data: PaginatedResponse<ProductResponse> = await res.json();
        setProducts(data.content);
      }
    } catch {
      // Silently fail
    } finally {
      setIsLoading(false);
    }
  }, [categorySlug, sort]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <Breadcrumb
        items={[
          { label: '全商品', href: '/catalog' as never },
          { label: categoryName || categorySlug },
        ]}
        className="mb-6"
      />

      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">{categoryName || 'カテゴリ商品'}</h1>
        <Select value={sort} onValueChange={() => {}}>
          <SelectTrigger className="w-44">
            <SelectValue placeholder="並び替え" />
          </SelectTrigger>
          <SelectContent>
            {SORT_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Separator className="mb-8" />

      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          <SkeletonCard count={8} />
        </div>
      ) : products.length === 0 ? (
        <div className="py-20 text-center">
          <p className="text-muted-foreground text-lg">このカテゴリには商品がありません</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}

export default function CategoryPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-7xl px-4 py-8">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
            <SkeletonCard count={8} />
          </div>
        </div>
      }
    >
      <CategoryContent />
    </Suspense>
  );
}
