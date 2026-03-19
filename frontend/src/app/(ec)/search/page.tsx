'use client';

import { Search, ThumbsDown, ThumbsUp } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';

import { SkeletonCard } from '@/components/common/skeleton-card';
import { ProductCard } from '@/components/ec/product-card';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Button, buttonVariants } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Separator } from '@/components/ui/separator';

import type { ProductResponse, SearchResponse } from '@/types/api';

function SearchContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const query = searchParams.get('q') || '';
  const [results, setResults] = useState<ProductResponse[]>([]);
  const [totalHits, setTotalHits] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState(query);
  const [feedbackGiven, setFeedbackGiven] = useState(false);

  const fetchResults = useCallback(async (q: string) => {
    if (!q.trim()) {
      setResults([]);
      setTotalHits(0);
      return;
    }
    setIsLoading(true);
    try {
      const res = await fetch(`/api/search?query=${encodeURIComponent(q)}&size=20`);
      if (res.ok) {
        const data: SearchResponse = await res.json();
        const products: ProductResponse[] = (data.results || []).map((r, i) => ({
          id: r.productId,
          sku: `SKU-${String(i)}`,
          name: r.name,
          description: r.description,
          brand: r.brand,
          categoryId: '',
          regularPrice: r.price,
          salePrice: null,
          currency: 'JPY',
          stockQuantity: 10,
          availableQuantity: 10,
          status: 'ACTIVE' as const,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }));
        setResults(products);
        setTotalHits(data.totalHits || products.length);
      }
    } catch {
      setResults([]);
      setTotalHits(0);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (query) {
      setSearchQuery(query);
      fetchResults(query);
    }
  }, [query, fetchResults]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  const handleFeedback = (positive: boolean) => {
    setFeedbackGiven(true);
    // TODO: Send feedback to API
    void fetch('/api/search/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, positive }),
    }).catch(() => {});
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <Breadcrumb items={[{ label: `検索結果: ${query}` }]} className="mb-6" />

      {/* Search bar */}
      <form onSubmit={handleSearch} className="mb-8">
        <div className="relative max-w-xl">
          <Search className="text-muted-foreground absolute top-1/2 left-3 h-5 w-5 -translate-y-1/2" />
          <Input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="スキー用品を検索..."
            className="h-12 pl-10 text-lg"
          />
        </div>
      </form>

      {query && (
        <div className="mb-6">
          <h1 className="text-2xl font-bold">「{query}」の検索結果</h1>
          {!isLoading && (
            <p className="text-muted-foreground mt-1 text-sm">
              {totalHits > 0
                ? `${totalHits.toLocaleString()} 件の商品が見つかりました`
                : '該当する商品はありません'}
            </p>
          )}
        </div>
      )}

      <Separator className="mb-8" />

      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          <SkeletonCard count={8} />
        </div>
      ) : results.length > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
            {results.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>

          {/* Search Feedback */}
          {!feedbackGiven ? (
            <div className="mt-12 text-center">
              <p className="text-muted-foreground mb-3 text-sm">この検索結果は役に立ちましたか？</p>
              <div className="flex justify-center gap-3">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-2"
                  onClick={() => handleFeedback(true)}
                >
                  <ThumbsUp className="h-4 w-4" />
                  はい
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-2"
                  onClick={() => handleFeedback(false)}
                >
                  <ThumbsDown className="h-4 w-4" />
                  いいえ
                </Button>
              </div>
            </div>
          ) : (
            <div className="mt-12 text-center">
              <p className="text-muted-foreground text-sm">フィードバックありがとうございます</p>
            </div>
          )}
        </>
      ) : query ? (
        <NoResults query={query} />
      ) : (
        <div className="py-20 text-center">
          <Search className="text-muted-foreground mx-auto mb-4 h-16 w-16" />
          <p className="text-muted-foreground text-lg">キーワードを入力して検索してください</p>
        </div>
      )}
    </div>
  );
}

function NoResults({ query }: { query: string }) {
  const [trendingProducts, setTrendingProducts] = useState<ProductResponse[]>([]);

  useEffect(() => {
    fetch('/api/dashboard/home')
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('Failed'))))
      .then((data: { trending?: ProductResponse[] }) => {
        if (data.trending) setTrendingProducts(data.trending.slice(0, 5));
      })
      .catch(() => {});
  }, []);

  return (
    <div className="py-12 text-center">
      <Search className="text-muted-foreground mx-auto mb-4 h-12 w-12" />
      <h2 className="text-xl font-semibold">「{query}」に一致する商品が見つかりませんでした</h2>
      <p className="text-muted-foreground mt-2">
        検索キーワードを変更するか、カテゴリから探してみてください
      </p>
      <div className="mt-6 flex justify-center gap-4">
        <Link href="/catalog" className={buttonVariants({ variant: 'outline' })}>
          全商品を見る
        </Link>
      </div>

      {/* Trending fallback */}
      {trendingProducts.length > 0 && (
        <div className="mt-12 text-left">
          <h3 className="mb-6 text-xl font-bold">人気の商品</h3>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5">
            {trendingProducts.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function SearchPage() {
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
      <SearchContent />
    </Suspense>
  );
}
