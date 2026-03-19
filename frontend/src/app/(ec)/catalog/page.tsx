'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';

import { SkeletonCard } from '@/components/common/skeleton-card';
import { ProductCard } from '@/components/ec/product-card';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Button } from '@/components/ui/button';
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
  { value: 'salesCount,desc', label: '人気順' },
  { value: 'name,asc', label: '名前順' },
];

const CATEGORIES = [
  { id: '', label: '全て' },
  { id: 'ski', label: 'スキー板' },
  { id: 'boots', label: 'ブーツ' },
  { id: 'wear', label: 'ウェア' },
  { id: 'accessories', label: 'アクセサリー' },
  { id: 'poles', label: 'ポール' },
  { id: 'goggles', label: 'ゴーグル' },
];

function CatalogContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const observerRef = useRef<IntersectionObserver | null>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  const sort = searchParams.get('sort') || 'createdAt,desc';
  const category = searchParams.get('category') || '';

  const fetchProducts = useCallback(
    async (pageNum: number, append: boolean = false) => {
      setIsLoading(true);
      try {
        const params = new URLSearchParams({
          page: String(pageNum),
          size: '20',
          sort,
        });
        if (category) params.set('categoryId', category);

        const res = await fetch(`/api/products?${params.toString()}`);
        if (res.ok) {
          const data: PaginatedResponse<ProductResponse> = await res.json();
          setProducts((prev) => (append ? [...prev, ...data.content] : data.content));
          setHasMore(data.content.length > 0 && data.page.number < data.page.totalPages - 1);
          setTotalElements(data.page.totalElements);
        } else {
          setHasMore(false);
        }
      } catch {
        setHasMore(false);
      } finally {
        setIsLoading(false);
      }
    },
    [sort, category],
  );

  useEffect(() => {
    setPage(0);
    setProducts([]);
    fetchProducts(0);
  }, [fetchProducts]);

  // Infinite scroll
  useEffect(() => {
    if (!loadMoreRef.current || !hasMore) return;

    observerRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !isLoading && hasMore) {
          const nextPage = page + 1;
          setPage(nextPage);
          fetchProducts(nextPage, true);
        }
      },
      { threshold: 0.1 },
    );

    observerRef.current.observe(loadMoreRef.current);
    return () => observerRef.current?.disconnect();
  }, [hasMore, isLoading, page, fetchProducts]);

  const handleSortChange = (value: string | null) => {
    if (!value) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set('sort', value);
    router.push(`/catalog?${params.toString()}` as never);
  };

  const handleCategoryChange = (categoryId: string) => {
    const params = new URLSearchParams(searchParams.toString());
    if (categoryId) {
      params.set('category', categoryId);
    } else {
      params.delete('category');
    }
    params.delete('page');
    router.push(`/catalog?${params.toString()}` as never);
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <Breadcrumb items={[{ label: '全商品' }]} className="mb-6" />

      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">全商品</h1>
          <p className="text-muted-foreground mt-1 text-sm">
            {totalElements > 0 ? `${totalElements.toLocaleString()} 件の商品` : ''}
          </p>
        </div>
        <div className="flex items-center gap-4">
          <Select value={sort} onValueChange={handleSortChange}>
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
      </div>

      {/* Category filter chips */}
      <div className="mb-6 flex gap-2 overflow-x-auto pb-2">
        {CATEGORIES.map((cat) => (
          <Button
            key={cat.id}
            variant={category === cat.id ? 'default' : 'outline'}
            size="sm"
            className="shrink-0"
            onClick={() => handleCategoryChange(cat.id)}
          >
            {cat.label}
          </Button>
        ))}
      </div>

      <Separator className="mb-8" />

      {products.length === 0 && !isLoading ? (
        <div className="py-20 text-center">
          <p className="text-muted-foreground text-lg">商品が見つかりませんでした</p>
          <Button
            variant="outline"
            className="mt-4"
            onClick={() => router.push('/catalog' as never)}
          >
            フィルターをクリア
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
          {isLoading && <SkeletonCard count={4} />}
        </div>
      )}

      {/* Infinite scroll trigger */}
      <div ref={loadMoreRef} className="h-10" />
    </div>
  );
}

export default function CatalogPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
            <SkeletonCard count={8} />
          </div>
        </div>
      }
    >
      <CatalogContent />
    </Suspense>
  );
}
