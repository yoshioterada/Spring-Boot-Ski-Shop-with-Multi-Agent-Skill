'use client';

import type { ProductResponse } from '@/types/api';

export function ProductJsonLd({ product }: { product: ProductResponse }) {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description,
    sku: product.sku,
    brand: { '@type': 'Brand', name: product.brand },
    offers: {
      '@type': 'Offer',
      price: product.salePrice ?? product.regularPrice,
      priceCurrency: product.currency || 'JPY',
      availability:
        product.availableQuantity > 0
          ? 'https://schema.org/InStock'
          : 'https://schema.org/OutOfStock',
    },
  };

  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
    />
  );
}
