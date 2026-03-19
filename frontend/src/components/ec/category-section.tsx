import Link from 'next/link';

import { CategoryCard } from './category-card';

import type { CategoryResponse } from '@/types/api';

async function getCategories(): Promise<CategoryResponse[]> {
  try {
    const baseUrl = process.env.NEXT_PUBLIC_APP_URL || `http://localhost:${process.env.PORT || '3001'}`;
    const res = await fetch(`${baseUrl}/api/dashboard/home`, {
      next: { revalidate: 300 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return (data.categories as CategoryResponse[]) || [];
  } catch {
    return [];
  }
}

export async function CategorySection() {
  const categories = await getCategories();

  if (categories.length === 0) {
    return <PlaceholderCategories />;
  }

  return (
    <section>
      <div className="mb-8 text-center">
        <h2 className="text-3xl font-bold">カテゴリから探す</h2>
        <p className="text-muted-foreground mt-2">お気に入りのスポーツカテゴリを選択</p>
      </div>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {categories.slice(0, 8).map((category) => (
          <CategoryCard key={category.id} category={category} />
        ))}
      </div>
    </section>
  );
}

function PlaceholderCategories() {
  const placeholders = [
    { id: 'cat-ski', name: 'スキー板', description: 'オールマウンテンからレーシングまで', icon: '⛷️' },
    { id: 'cat-boots', name: 'スキーブーツ', description: '快適なフィット感を追求', icon: '🥾' },
    { id: 'cat-wear', name: 'ウェア', description: '高機能ジャケット&パンツ', icon: '🧥' },
    { id: 'cat-goggles', name: 'アクセサリー', description: 'ゴーグル、グローブ、ヘルメット', icon: '🥽' },
  ];

  return (
    <section>
      <div className="mb-8 text-center">
        <h2 className="text-3xl font-bold">カテゴリから探す</h2>
        <p className="text-muted-foreground mt-2">お気に入りのスポーツカテゴリを選択</p>
      </div>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {placeholders.map((cat) => (
          <Link key={cat.id} href={`/catalog?category=${cat.id}`}>
            <div
              className="group bg-card hover:border-primary/30 cursor-pointer rounded-lg border p-6 text-center transition-all hover:shadow-lg"
            >
              <div className="bg-primary/10 mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full text-3xl">
                {cat.icon}
              </div>
              <h3 className="group-hover:text-primary font-semibold">{cat.name}</h3>
              <p className="text-muted-foreground mt-1 text-sm">{cat.description}</p>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
