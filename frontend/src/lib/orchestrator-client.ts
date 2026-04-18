/**
 * Multi-Agent Orchestrator (POST /api/v1/orchestrator/recommend) との通信ラッパー。
 * api-gateway-service 経由で agent-runtime-monolith:8100 に到達する。
 */

export interface OrchestratorRequest {
  userId: string;
  message: string;
  sessionId?: string;
  couponCode?: string;
  usePoints?: boolean;
}

export interface QuoteItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  matchReason: string;
}

export interface QuoteSummary {
  orderId: string;
  items: QuoteItem[];
  subtotal: number;
  couponDiscount: number;
  pointDiscount: number;
  totalAmount: number;
  reservationId: string;
  reservationExpiresAt: string;
}

export interface OrchestratorResponse {
  userId: string;
  sessionId: string;
  quote: QuoteSummary | null;
  intentSummary: string;
  weatherSummary: string;
  equipmentRecommendation: string;
  couponSummary: string;
  orchestrationSummary: string;
  generatedAt: string;
}

export async function recommendWithAgents(
  request: OrchestratorRequest,
  bearerToken?: string,
): Promise<OrchestratorResponse> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (bearerToken) {
    headers.Authorization = `Bearer ${bearerToken}`;
  }
  // ブラウザから直接 gateway を叩くと CORS / 認証ヘッダ伝搬が複雑になるため、
  // 同一オリジンの Next.js BFF (/api/agent/recommend) に中継する。
  const res = await fetch('/api/agent/recommend', {
    method: 'POST',
    headers,
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({}));
    throw new Error(
      typeof errBody === 'object' && errBody && 'error' in errBody
        ? String((errBody as { error: unknown }).error)
        : `HTTP ${res.status}`,
    );
  }
  return (await res.json()) as OrchestratorResponse;
}
