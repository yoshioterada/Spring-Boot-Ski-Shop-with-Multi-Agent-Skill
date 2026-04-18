import { Suspense } from 'react';
import Link from 'next/link';
import { Bot, ArrowRight } from 'lucide-react';

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
        {/* AI アドバイザーへの導線 */}
        <Link
          href="/agent"
          className="from-primary to-primary/80 group flex flex-col items-start justify-between gap-4 rounded-2xl bg-gradient-to-r p-6 text-white shadow-lg transition hover:shadow-xl sm:flex-row sm:items-center sm:p-8"
        >
          <div className="flex items-start gap-4 sm:items-center">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-white/20">
              <Bot className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold sm:text-2xl">AI スキー装備アドバイザー</h2>
              <p className="mt-1 text-sm text-white/90 sm:text-base">
                行き先・予算・レベルを伝えるだけで、複数 AI エージェントが最適な板・ウェア・小物を提案します。
              </p>
            </div>
          </div>
          <span className="inline-flex items-center gap-1 rounded-full bg-white px-4 py-2 text-sm font-semibold text-primary transition group-hover:gap-2">
            相談を始める <ArrowRight className="h-4 w-4" />
          </span>
        </Link>

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
