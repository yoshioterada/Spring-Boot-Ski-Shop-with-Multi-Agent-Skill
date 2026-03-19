'use client';

import { Minus, Package, Plus, ShoppingCart } from 'lucide-react';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { ErrorPlaceholder } from '@/components/common/error-placeholder';
import { ProductCard } from '@/components/ec/product-card';
import { ProductJsonLd } from '@/components/ec/product-jsonld';
import { Breadcrumb } from '@/components/layout/breadcrumb';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/hooks/use-auth';
import { useCart } from '@/hooks/use-cart';
import { formatCurrency } from '@/lib/format';

import type { ProductResponse } from '@/types/api';

interface ProductDetailData {
  product: ProductResponse;
  similar: { products: Array<{ productId: string; score: number; reason: string }> } | null;
}

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const productId = params.productId as string;
  const [data, setData] = useState<ProductDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();

  const fetchProduct = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/products/${productId}`);
      if (!res.ok) {
        setError('商品が見つかりません');
        return;
      }
      const result = await res.json();
      setData(result);
    } catch {
      setError('商品の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  }, [productId]);

  useEffect(() => {
    fetchProduct();
  }, [fetchProduct]);

  if (isLoading) {
    return <ProductDetailSkeleton />;
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8">
        <ErrorPlaceholder message={error || '商品が見つかりません'} onRetry={fetchProduct} />
      </div>
    );
  }

  const { product, similar } = data;
  const stockStatus = getStockStatus(product.availableQuantity);
  const maxQuantity = Math.min(product.availableQuantity, 10);
  const isOutOfStock = product.availableQuantity === 0;

  const similarProducts: ProductResponse[] = (similar?.products || []).slice(0, 4).map((s, i) => ({
    id: s.productId || `similar-${i}`,
    sku: `SKU-SIM-${i}`,
    name: s.reason || `関連商品 ${i + 1}`,
    description: '',
    brand: 'Azure Ski',
    categoryId: product.categoryId,
    regularPrice: product.regularPrice + (i - 2) * 2000,
    salePrice: null,
    currency: 'JPY',
    stockQuantity: 10,
    availableQuantity: 10,
    status: 'ACTIVE' as const,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }));

  const handleAddToCart = () => {
    if (!isAuthenticated) {
      router.push(`/login?redirect=${encodeURIComponent(`/product/${productId}`)}` as never);
      return;
    }
    if (quantity > maxQuantity) {
      toast.error(`在庫数を超えています（最大${maxQuantity}個）`);
      return;
    }
    addItem({
      productId: product.id,
      productName: product.name,
      quantity,
      unitPrice: product.salePrice ?? product.regularPrice,
    });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <ProductJsonLd product={product} />
      <Breadcrumb
        items={[{ label: '全商品', href: '/catalog' as never }, { label: product.name }]}
        className="mb-6"
      />

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
        {/* Image Gallery */}
        <div className="space-y-4">
          <Card className="overflow-hidden">
            <div className="from-primary/5 to-primary/10 flex aspect-square items-center justify-center bg-gradient-to-br">
              <Package className="text-primary/20 h-32 w-32" />
            </div>
          </Card>
        </div>

        {/* Product Info */}
        <div className="space-y-6">
          <div>
            <p className="text-muted-foreground text-sm">{product.brand}</p>
            <h1 className="mt-1 text-3xl font-bold">{product.name}</h1>
            <p className="text-muted-foreground mt-1 text-sm">SKU: {product.sku}</p>
          </div>

          {/* Price */}
          <div className="flex items-baseline gap-3">
            {product.salePrice ? (
              <>
                <span className="text-3xl font-bold text-red-600">
                  {formatCurrency(product.salePrice)}
                </span>
                <span className="text-muted-foreground text-xl line-through">
                  {formatCurrency(product.regularPrice)}
                </span>
                <Badge variant="destructive">
                  {Math.round((1 - product.salePrice / product.regularPrice) * 100)}% OFF
                </Badge>
              </>
            ) : (
              <span className="text-3xl font-bold">{formatCurrency(product.regularPrice)}</span>
            )}
          </div>

          {/* Stock Status */}
          <div className="flex items-center gap-2">
            <div className={`h-2 w-2 rounded-full ${stockStatus.dotColor}`} />
            <span className={`text-sm font-medium ${stockStatus.textColor}`}>
              {stockStatus.label}
            </span>
            {product.availableQuantity > 0 && product.availableQuantity <= 5 && (
              <span className="text-muted-foreground text-sm">
                （残り {product.availableQuantity} 点）
              </span>
            )}
          </div>

          <Separator />

          {/* Quantity + Add to Cart */}
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <span className="text-sm font-medium">数量:</span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  disabled={quantity <= 1 || isOutOfStock}
                >
                  <Minus className="h-4 w-4" />
                </Button>
                <span className="w-12 text-center font-medium">{quantity}</span>
                <Button
                  variant="outline"
                  size="icon"
                  className="h-8 w-8"
                  onClick={() => setQuantity(Math.min(maxQuantity, quantity + 1))}
                  disabled={quantity >= maxQuantity || isOutOfStock}
                >
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <Button
              size="lg"
              className="w-full gap-2"
              disabled={isOutOfStock}
              onClick={handleAddToCart}
            >
              <ShoppingCart className="h-5 w-5" />
              {isOutOfStock ? '在庫切れ' : 'カートに追加'}
            </Button>
          </div>
        </div>
      </div>

      {/* Tabs: Description / Specs */}
      <div className="mt-12">
        <Tabs defaultValue="description">
          <TabsList>
            <TabsTrigger value="description">商品説明</TabsTrigger>
            <TabsTrigger value="specs">仕様</TabsTrigger>
          </TabsList>
          <TabsContent value="description" className="mt-4">
            <Card>
              <CardContent className="prose max-w-none p-6">
                <p>{product.description || '商品説明はまだありません。'}</p>
              </CardContent>
            </Card>
          </TabsContent>
          <TabsContent value="specs" className="mt-4">
            <Card>
              <CardContent className="p-6">
                {product.attributes && Object.keys(product.attributes).length > 0 ? (
                  <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    {Object.entries(product.attributes).map(([key, value]) => (
                      <div key={key} className="flex justify-between border-b pb-2">
                        <dt className="text-muted-foreground text-sm">{key}</dt>
                        <dd className="text-sm font-medium">{value}</dd>
                      </div>
                    ))}
                  </dl>
                ) : (
                  <p className="text-muted-foreground">仕様情報はまだありません。</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {/* Similar Products */}
      {similarProducts.length > 0 && (
        <div className="mt-16">
          <h2 className="mb-6 text-2xl font-bold">関連商品</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
            {similarProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function getStockStatus(quantity: number) {
  if (quantity === 0) {
    return { label: '在庫切れ', dotColor: 'bg-red-500', textColor: 'text-red-600' };
  }
  if (quantity <= 5) {
    return { label: '残りわずか', dotColor: 'bg-orange-500', textColor: 'text-orange-600' };
  }
  return { label: '在庫あり', dotColor: 'bg-green-500', textColor: 'text-green-600' };
}

function ProductDetailSkeleton() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <Skeleton className="mb-6 h-4 w-48" />
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
        <Skeleton className="aspect-square w-full rounded-lg" />
        <div className="space-y-4">
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="h-10 w-40" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-12 w-full" />
        </div>
      </div>
    </div>
  );
}
