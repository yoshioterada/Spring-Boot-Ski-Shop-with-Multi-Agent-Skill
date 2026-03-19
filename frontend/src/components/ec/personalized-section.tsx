'use client';

import { useEffect, useRef, useState } from 'react';

import { SkeletonCard } from '@/components/common/skeleton-card';
import { ProductCard } from '@/components/ec/product-card';
import { useAuth } from '@/hooks/use-auth';

import type { ProductResponse } from '@/types/api';

export function PersonalizedSection() {
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [fetchState, setFetchState] = useState<'idle' | 'done'>('idle');
  const fetchedRef = useRef(false);

  useEffect(() => {
    if (authLoading || !isAuthenticated || fetchedRef.current) return;
    fetchedRef.current = true;

    let cancelled = false;

    fetch('/api/dashboard/home')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!cancelled && data?.personalizedProducts) {
          setProducts(data.personalizedProducts);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) {
          setFetchState('done');
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, authLoading]);

  if (!isAuthenticated) return null;
  if (fetchState === 'done' && products.length === 0) return null;

  const isLoading = authLoading || fetchState === 'idle';

  return (
    <section>
      <h2 className="mb-8 text-2xl font-bold">あなたへのおすすめ</h2>
      {isLoading ? (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          <SkeletonCard count={4} />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {products.slice(0, 8).map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </section>
  );
}
