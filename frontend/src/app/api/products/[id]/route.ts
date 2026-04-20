import { type NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const API_BASE = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

export async function GET(_request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID(),
  };

  // まず商品詳細を取得
  const productRes = await fetch(`${API_BASE}/api/v1/products/${id}`, { headers });
  if (!productRes.ok) {
    return NextResponse.json({ detail: '商品が見つかりません' }, { status: productRes.status });
  }
  const product = await productRes.json();

  // 同カテゴリ商品を inventory から直接取得（LLM 呼び出しを回避して高速化）
  let similar: { products: unknown[] } | null = null;
  if (product.categoryId) {
    try {
      const similarRes = await fetch(
        `${API_BASE}/api/v1/products?categoryId=${encodeURIComponent(product.categoryId)}&size=5`,
        { headers }
      );
      if (similarRes.ok) {
        const data = await similarRes.json();
        const products = (data.content ?? []).filter((p: { id: string }) => p.id !== id).slice(0, 4);
        similar = { products };
      }
    } catch {
      // 類似商品はオプション。エラーは無視する
    }
  }

  return NextResponse.json({ product, similar });
}
