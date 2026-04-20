/**
 * Multi-Agent Orchestrator 待ち時間ストリーミング BFF。
 *
 * 単一の SSE 応答で以下を配信する:
 *   - status (INTENT / ORCHESTRATING / COMPLETED)
 *   - intent (CustomerIntentResult)
 *   - tip   (RenderedTip) ... 8 秒ごと
 *   - result (OrchestratorResponse)
 *   - warn / error
 *   - done
 *
 * 詳細仕様: design-docs/add-info-2-customer-spec.md §6
 */
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';
import { safeFetch } from '@/lib/safe-fetch';
import { buildTipPool, createTipPicker } from '@/lib/tips';

import type { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 1200; // 20 分

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8090';
const TIP_INTERVAL_MS = 8_000;
const HEARTBEAT_INTERVAL_MS = 15_000;
const MAX_TIPS_PER_STREAM = 60;
const ORCH_TIMEOUT_MS = 1_200_000; // 20 min
const INTENT_TIMEOUT_MS = 60_000; // 60 秒

interface IntentConstraints {
  destination?: string | null;
  skillLevel?: string | null;
}
interface CustomerIntentResult {
  userId?: string;
  sessionId?: string;
  constraints?: IntentConstraints | null;
  intentSummary?: string;
}

export async function POST(req: NextRequest) {
  const session = await getServerSession(authOptions);
  const accessToken = (session as unknown as { accessToken?: string } | null)?.accessToken;
  if (!accessToken) {
    return new Response(
      JSON.stringify({ error: 'AUTH_REQUIRED', message: 'ログインが必要です。' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    );
  }

  const body = await req.json().catch(() => ({}));
  if (!body.userId && session?.user?.id) body.userId = session.user.id;

  const requestId = crypto.randomUUID();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Request-Id': requestId,
    Authorization: `Bearer ${accessToken}`,
  };

  // 親 AbortController: クライアント切断 / 全体タイムアウトで全ての子を中断
  const abortAll = new AbortController();
  const wholeTimeoutTimer = setTimeout(
    () => abortAll.abort('whole-timeout'),
    ORCH_TIMEOUT_MS + 30_000,
  );

  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const encoder = new TextEncoder();
      const send = (event: string, data: unknown) => {
        try {
          controller.enqueue(encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));
        } catch {
          // 既に close 済み
        }
      };
      const sendHeartbeat = () => {
        try {
          controller.enqueue(encoder.encode(`: ping ${Date.now()}\n\n`));
        } catch {
          /* closed */
        }
      };

      const heartbeatTimer = setInterval(sendHeartbeat, HEARTBEAT_INTERVAL_MS);
      let stopped = false;
      let tipsSent = 0;
      let tipTimer: ReturnType<typeof setInterval> | null = null;

      const cleanup = () => {
        stopped = true;
        clearInterval(heartbeatTimer);
        if (tipTimer) clearInterval(tipTimer);
        clearTimeout(wholeTimeoutTimer);
      };

      try {
        // ===== Phase 1: Intent =====
        send('status', { phase: 'INTENT', requestId });

        let intent: CustomerIntentResult | null = null;
        const intentCtrl = new AbortController();
        const intentTimer = setTimeout(
          () => intentCtrl.abort('intent-timeout'),
          INTENT_TIMEOUT_MS,
        );
        abortAll.signal.addEventListener(
          'abort',
          () => intentCtrl.abort('parent'),
          { once: true },
        );
        try {
          const intentRes = await safeFetch(
            `${API_GATEWAY_URL}/api/v1/orchestrator/intent-only`,
            {
              method: 'POST',
              headers,
              body: JSON.stringify(body),
              signal: intentCtrl.signal,
            },
          );
          if (intentRes.ok) {
            intent = (await intentRes.json()) as CustomerIntentResult;
            send('intent', intent);
          } else {
            send('warn', { code: 'INTENT_FAILED', status: intentRes.status });
          }
        } catch (e) {
          send('warn', {
            code: 'INTENT_ERROR',
            message: e instanceof Error ? e.message : String(e),
          });
        } finally {
          clearTimeout(intentTimer);
        }

        // ===== Phase 2: Tip ループ + Orchestrator 並列 =====
        const destination = intent?.constraints?.destination ?? null;
        const skillLevel = intent?.constraints?.skillLevel ?? null;
        const month = new Date().getMonth() + 1;
        const tipPool = buildTipPool({ destination, skillLevel, month });
        const pickTip = createTipPicker(tipPool);

        // 即座に 1 件目を送る (体感の改善)
        const firstTip = pickTip({ destination, skillLevel, month });
        if (firstTip) {
          send('tip', { ...firstTip, deliveredAt: new Date().toISOString() });
          tipsSent++;
        }

        tipTimer = setInterval(() => {
          if (stopped) return;
          if (tipsSent >= MAX_TIPS_PER_STREAM) return;
          const tip = pickTip({ destination, skillLevel, month });
          if (tip) {
            send('tip', { ...tip, deliveredAt: new Date().toISOString() });
            tipsSent++;
          }
        }, TIP_INTERVAL_MS);

        send('status', { phase: 'ORCHESTRATING', requestId });

        const orchCtrl = new AbortController();
        abortAll.signal.addEventListener(
          'abort',
          () => orchCtrl.abort('parent'),
          { once: true },
        );

        const orchRes = await safeFetch(
          `${API_GATEWAY_URL}/api/v1/orchestrator/recommend`,
          {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
            signal: orchCtrl.signal,
          },
        );
        const text = await orchRes.text();
        if (!orchRes.ok) {
          let detail: unknown = text;
          try {
            detail = text ? JSON.parse(text) : null;
          } catch {
            /* keep raw text */
          }
          send('error', {
            code: `HTTP_${orchRes.status}`,
            status: orchRes.status,
            detail,
          });
        } else {
          const result = JSON.parse(text);
          send('result', result);
          send('status', { phase: 'COMPLETED', requestId });
        }
      } catch (err) {
        send('error', {
          code: 'STREAM_ERROR',
          message: err instanceof Error ? err.message : String(err),
        });
      } finally {
        cleanup();
        send('done', { requestId });
        try {
          controller.close();
        } catch {
          /* already closed */
        }
      }
    },
    cancel(reason) {
      abortAll.abort(reason ?? 'client-cancel');
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
      'X-Content-Type-Options': 'nosniff',
      'X-Request-Id': requestId,
    },
  });
}
