/**
 * F1 対話分析 BFF — SSE パイプ (spec § 4.2.1).
 * JWT を転送し、Gateway 経由で ai-support-service の /chat SSE を中継する。
 */
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

import type { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';

export async function POST(req: NextRequest) {
  const session = await getServerSession(authOptions);
  const accessToken = (session as unknown as { accessToken?: string } | null)
    ?.accessToken;
  if (!accessToken) {
    return new Response(
      JSON.stringify({
        error: 'AUTH_REQUIRED',
        message: 'ログインが必要です。',
      }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    );
  }

  const body = await req.json().catch(() => ({}));

  const abortCtrl = new AbortController();
  const timeout = setTimeout(() => abortCtrl.abort(), 60_000);

  try {
    const upstream = await fetch(
      `${API_GATEWAY_URL}/api/v1/admin/ai-analyzer/chat`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(body),
        signal: abortCtrl.signal,
      },
    );

    if (!upstream.ok || !upstream.body) {
      const text = await upstream.text().catch(() => '');
      return new Response(text || `HTTP ${upstream.status}`, {
        status: upstream.status,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // SSE パイプスルー
    return new Response(upstream.body, {
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    });
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    return new Response(JSON.stringify({ error: 'UPSTREAM_ERROR', message: msg }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    });
  } finally {
    clearTimeout(timeout);
  }
}
