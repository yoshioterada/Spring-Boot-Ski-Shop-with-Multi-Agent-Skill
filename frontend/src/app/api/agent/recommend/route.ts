import { NextRequest, NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';

/**
 * Multi-Agent Orchestrator BFF。
 * ブラウザ → Next.js → api-gateway → agent-runtime-monolith:8100 と中継する。
 * LLM 呼び出しを含むため長めのタイムアウト (120s) を設定。
 */
export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions);
    const body = await request.json();

    // Orchestrator は JWT 認証必須。未ログインなら早期リターンして分かりやすいメッセージを返す。
    if (!session?.accessToken) {
      return NextResponse.json(
        {
          error: 'ログインが必要です。ログインしてから再度お試しください。',
          code: 'AUTH_REQUIRED',
        },
        { status: 401 },
      );
    }

    // userId が body に未設定なら session から補完
    if (!body.userId && session.user?.id) {
      body.userId = session.user.id;
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
      Authorization: `Bearer ${session.accessToken}`,
    };

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 1_200_000);

    const res = await safeFetch(`${API_GATEWAY_URL}/api/v1/orchestrator/recommend`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    clearTimeout(timer);

    // gateway が 4xx/5xx を返した場合、本文が空のことがあるため status に応じて固定メッセージを返す
    const text = await res.text();
    if (!res.ok) {
      let detail: unknown = text;
      try {
        detail = text ? JSON.parse(text) : null;
      } catch {
        /* keep raw text */
      }
      const messageByStatus: Record<number, string> = {
        401: 'ログインセッションが切れました。再度ログインしてください。',
        403: 'この機能を利用する権限がありません。',
        503: 'AI エージェントが一時的に利用できません。少し時間をおいて再度お試しください。',
        504: 'AI エージェントの応答がタイムアウトしました。時間をおいて再度お試しください。',
      };
      return NextResponse.json(
        {
          error: messageByStatus[res.status] ?? `エラーが発生しました (HTTP ${res.status})`,
          status: res.status,
          detail,
        },
        { status: res.status },
      );
    }

    const data = text ? JSON.parse(text) : null;
    return NextResponse.json(data ?? { error: 'Empty response' }, { status: res.status });
  } catch (e) {
    return NextResponse.json(
      { error: 'Multi-Agent Orchestrator への接続に失敗しました', detail: String(e) },
      { status: 502 },
    );
  }
}
