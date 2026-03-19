'use client';

import { Package } from 'lucide-react';
import Link from 'next/link';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { formatCurrency } from '@/lib/format';

import type { ProductResponse } from '@/types/api';

interface ProductCardProps {
  product: ProductResponse;
}

export function ProductCard({ product }: ProductCardProps) {
  const stockStatus = getStockStatus(product.availableQuantity);

  return (
    <Link
      href={`/product/${product.id}` as never}
      aria-label={`${product.name} - ${product.salePrice ? formatCurrency(product.salePrice) : formatCurrency(product.regularPrice)}`}
    >
      <Card className="group overflow-hidden transition-all duration-300 hover:scale-[1.02] hover:shadow-lg">
        {/* Image */}
        <div className="from-primary/5 to-primary/10 relative aspect-[4/3] overflow-hidden bg-gradient-to-br">
          <div className="flex h-full items-center justify-center">
            <Package className="text-primary/20 h-16 w-16" aria-hidden="true" />
          </div>
          {product.salePrice && (
            <Badge variant="destructive" className="absolute top-2 left-2">
              SALE
            </Badge>
          )}
          <Badge
            variant={stockStatus.variant as 'default' | 'secondary' | 'destructive' | 'outline'}
            className={`absolute top-2 right-2 ${stockStatus.className}`}
          >
            {stockStatus.label}
          </Badge>
        </div>

        {/* Info */}
        <CardContent className="space-y-2 p-4">
          <p className="text-muted-foreground text-xs">{product.brand}</p>
          <h3 className="group-hover:text-primary line-clamp-2 text-sm leading-tight font-medium">
            {product.name}
          </h3>
          <div className="flex items-baseline gap-2">
            {product.salePrice ? (
              <>
                <span className="text-lg font-bold text-red-600">
                  {formatCurrency(product.salePrice)}
                </span>
                <span className="text-muted-foreground text-sm line-through">
                  {formatCurrency(product.regularPrice)}
                </span>
              </>
            ) : (
              <span className="text-lg font-bold">{formatCurrency(product.regularPrice)}</span>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

function getStockStatus(quantity: number) {
  if (quantity === 0) {
    return { label: '在庫切れ', variant: 'destructive', className: '' };
  }
  if (quantity <= 5) {
    return {
      label: '残りわずか',
      variant: 'outline',
      className: 'border-orange-500 text-orange-600 bg-orange-50',
    };
  }
  return {
    label: '在庫あり',
    variant: 'outline',
    className: 'border-green-500 text-green-600 bg-green-50',
  };
}
