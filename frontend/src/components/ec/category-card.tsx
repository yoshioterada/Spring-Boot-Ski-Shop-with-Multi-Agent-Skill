'use client';

import { ArrowRight, FolderOpen } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';

import { Card, CardContent } from '@/components/ui/card';

import type { CategoryResponse } from '@/types/api';

interface CategoryCardProps {
  category: CategoryResponse;
}

export function CategoryCard({ category }: CategoryCardProps) {
  return (
    <Link href={`/catalog?category=${category.id}` as never}>
      <Card className="group hover:border-primary/30 overflow-hidden transition-all duration-300 hover:shadow-lg">
        <div className="from-primary/10 to-primary/5 relative flex aspect-[3/2] items-center justify-center bg-gradient-to-br">
          {category.imageUrl ? (
            <Image src={category.imageUrl} alt={category.name} fill className="object-cover" />
          ) : (
            <FolderOpen className="text-primary/30 h-12 w-12" />
          )}
        </div>
        <CardContent className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="group-hover:text-primary font-semibold">{category.name}</h3>
              {category.description && (
                <p className="text-muted-foreground mt-1 line-clamp-1 text-sm">
                  {category.description}
                </p>
              )}
            </div>
            <ArrowRight className="text-muted-foreground group-hover:text-primary h-4 w-4 transition-transform group-hover:translate-x-1" />
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
