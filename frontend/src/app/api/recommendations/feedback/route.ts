import { type NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';
const INVENTORY_SERVICE_URL = process.env.INVENTORY_SERVICE_URL || API_GATEWAY_URL;

type RecommendationFeedbackPayload = {
  userId?: string | null;
  sessionId?: string | null;
  recommendationId?: string | null;
  productId?: string | null;
  feedbackType?: 'CLICK' | 'CONVERSION' | string;
  source?: string | null;
};

export async function POST(request: NextRequest) {
  let payload: RecommendationFeedbackPayload;

  try {
    payload = await request.json();
  } catch {
    return NextResponse.json({ detail: 'Invalid JSON body' }, { status: 400 });
  }

  if (!payload.productId || !payload.feedbackType) {
    return NextResponse.json({ detail: 'productId and feedbackType are required' }, { status: 400 });
  }

  try {
    const res = await fetch(`${INVENTORY_SERVICE_URL}/api/v1/recommendations/feedback`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': request.headers.get('X-Request-Id') || crypto.randomUUID(),
      },
      body: JSON.stringify(payload),
    });

    const text = await res.text();
    const data = text ? parseJson(text) : {};
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ detail: '推薦フィードバックの保存に失敗しました' }, { status: 502 });
  }
}

function parseJson(text: string) {
  try {
    return JSON.parse(text);
  } catch {
    return { detail: text };
  }
}
