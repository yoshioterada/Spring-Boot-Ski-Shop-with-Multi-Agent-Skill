'use client';

import { Bot, ShoppingBag } from 'lucide-react';
import Link from 'next/link';
import { useCallback } from 'react';

import { buttonVariants } from '@/components/ui/button';

export function HeroBanner() {
  const month = new Date().getMonth() + 1;
  const isWinterSeason = month >= 10 || month <= 3;

  const handleOpenChat = useCallback(() => {
    window.dispatchEvent(new Event('open-ai-chat'));
  }, []);

  return (
    <section className="relative overflow-hidden" aria-label="ヒーローバナー">
      {/* Background gradient - LCP element with priority rendering */}
      <div
        role="img"
        aria-hidden="true"
        className={`absolute inset-0 ${
          isWinterSeason
            ? 'bg-gradient-to-br from-[#0078D4] via-[#1e3a5f] to-[#0a1628]'
            : 'bg-gradient-to-br from-[#0078D4] via-[#2d8cf0] to-[#60b0f4]'
        }`}
      />

      {/* Snow/mountain decoration */}
      <div className="absolute inset-0 opacity-10">
        <div className="absolute -top-20 -right-20 h-96 w-96 rounded-full bg-white/20 blur-3xl" />
        <div className="absolute bottom-0 -left-20 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
      </div>

      <div className="relative mx-auto max-w-7xl px-4 py-20 sm:px-6 sm:py-28 lg:px-8 lg:py-36">
        <div className="max-w-2xl">
          <h1 className="text-4xl font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
            {isWinterSeason ? (
              <>
                Azure SkiShop へ
                <br />
                <span className="text-blue-200">ようこそ</span>
              </>
            ) : (
              <>
                オフシーズン
                <br />
                <span className="text-blue-200">スペシャルセール</span>
              </>
            )}
          </h1>
          <p className="mt-6 text-lg text-blue-100 sm:text-xl">
            {isWinterSeason
              ? 'プレミアムスキー用品で、最高の滑りを。厳選されたギアがあなたのスキーライフを変える。'
              : '来シーズンに向けて、今がお得にギアを揃えるチャンス。最大50%OFFのアイテムも。'}
          </p>
          <div className="mt-8 flex flex-wrap gap-4">
            <Link
              href="/catalog"
              className={buttonVariants({
                size: 'lg',
                className: 'gap-2 bg-orange-500 text-white hover:bg-orange-600 font-semibold shadow-lg',
              })}
            >
              <ShoppingBag className="h-5 w-5" />
              商品を見る
            </Link>
            <button
              type="button"
              onClick={handleOpenChat}
              className={buttonVariants({
                size: 'lg',
                className: 'gap-2 bg-orange-500 text-white hover:bg-orange-600 font-semibold shadow-lg cursor-pointer',
              })}
            >
              <Bot className="h-5 w-5" />
              AI相談を始める
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
