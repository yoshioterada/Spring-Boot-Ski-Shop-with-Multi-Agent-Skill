import { Suspense } from 'react';

import { SkeletonCard } from '@/components/common/skeleton-card';
import { CategorySection } from '@/components/ec/category-section';
import { HeroBanner } from '@/components/ec/hero-banner';
import { PersonalizedSection } from '@/components/ec/personalized-section';
import { TrendingSection } from '@/components/ec/trending-section';

export default function HomePage() {
  return (
    <div>
      <HeroBanner />
      <div className="mx-auto max-w-7xl space-y-16 px-4 py-12 sm:px-6 lg:px-8">
        <Suspense
          fallback={
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              <SkeletonCard count={4} />
            </div>
          }
        >
          <CategorySection />
        </Suspense>
        <Suspense
          fallback={
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              <SkeletonCard count={5} />
            </div>
          }
        >
          <TrendingSection />
        </Suspense>
        <PersonalizedSection />
      </div>
    </div>
  );
}
