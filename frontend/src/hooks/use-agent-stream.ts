/**
 * /agent ページの SSE ストリーム購読フック。
 *
 *   const { start, stop, phase, intent, tips, result, error } = useAgentStream();
 *   start({ userId, message, ... });
 *
 * 詳細仕様: design-docs/add-info-2-customer-spec.md §9.1
 */
'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import type { OrchestratorRequest, OrchestratorResponse } from '@/lib/orchestrator-client';
import type { RenderedTip, TipCategory } from '@/lib/tips';

export type AgentPhase =
  | 'IDLE'
  | 'INTENT'
  | 'ORCHESTRATING'
  | 'COMPLETED'
  | 'ERROR'
  | 'CANCELLED';

export interface AgentIntent {
  userId?: string;
  sessionId?: string;
  intentSummary?: string;
  constraints?: {
    destination?: string | null;
    skillLevel?: string | null;
    tripStartDate?: string | null;
    tripEndDate?: string | null;
    groupSize?: number | null;
    budgetYen?: number | null;
  } | null;
}

export interface AgentTip extends RenderedTip {
  deliveredAt: string;
  category: TipCategory;
}

interface UseAgentStreamReturn {
  start: (req: OrchestratorRequest) => Promise<void>;
  stop: () => void;
  reset: () => void;
  phase: AgentPhase;
  intent: AgentIntent | null;
  tips: AgentTip[];
  result: OrchestratorResponse | null;
  error: string | null;
  startedAt: number | null;
  completedAt: number | null;
}

export function useAgentStream(): UseAgentStreamReturn {
  const [phase, setPhase] = useState<AgentPhase>('IDLE');
  const [intent, setIntent] = useState<AgentIntent | null>(null);
  const [tips, setTips] = useState<AgentTip[]>([]);
  const [result, setResult] = useState<OrchestratorResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [completedAt, setCompletedAt] = useState<number | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const reset = useCallback(() => {
    stop();
    setPhase('IDLE');
    setIntent(null);
    setTips([]);
    setResult(null);
    setError(null);
    setStartedAt(null);
    setCompletedAt(null);
  }, [stop]);

  // クリーンアップ: アンマウント時に必ず abort
  useEffect(() => () => abortRef.current?.abort(), []);

  const start = useCallback(async (req: OrchestratorRequest) => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    setPhase('INTENT');
    setIntent(null);
    setTips([]);
    setResult(null);
    setError(null);
    setStartedAt(Date.now());
    setCompletedAt(null);

    try {
      const res = await fetch('/api/agent/recommend-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(req),
        signal: ctrl.signal,
      });

      if (!res.ok || !res.body) {
        let msg = `HTTP ${res.status}`;
        try {
          const j = (await res.json()) as { error?: string; message?: string };
          msg = j.message ?? j.error ?? msg;
        } catch {
          /* ignore */
        }
        setError(msg);
        setPhase('ERROR');
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          handleSseFrame(frame);
        }
      }
    } catch (e) {
      if ((e as Error).name === 'AbortError') {
        setPhase('CANCELLED');
        return;
      }
      setError(e instanceof Error ? e.message : String(e));
      setPhase('ERROR');
    } finally {
      if (abortRef.current === ctrl) abortRef.current = null;
    }

    function handleSseFrame(frame: string) {
      const lines = frame.split('\n');
      let event = 'message';
      const dataLines: string[] = [];
      for (const line of lines) {
        if (!line || line.startsWith(':')) continue; // コメント (heartbeat) は無視
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
      }
      if (dataLines.length === 0) return;
      let data: unknown;
      try {
        data = JSON.parse(dataLines.join('\n'));
      } catch {
        return;
      }
      switch (event) {
        case 'status': {
          const s = data as { phase?: AgentPhase };
          if (s.phase) setPhase(s.phase);
          break;
        }
        case 'intent':
          setIntent(data as AgentIntent);
          break;
        case 'tip':
          setTips((prev) => [...prev, data as AgentTip]);
          break;
        case 'result':
          setResult(data as OrchestratorResponse);
          setCompletedAt(Date.now());
          break;
        case 'warn':
          // 致命的でないので state は変えず console に出すのみ
           
          console.warn('[agent-stream] warn', data);
          break;
        case 'error': {
          const e = data as { message?: string; code?: string };
          setError(e.message ?? e.code ?? 'unknown error');
          setPhase('ERROR');
          break;
        }
        case 'done':
          // ストリーム終了。phase は result/error 側で確定済み
          break;
        default:
          break;
      }
    }
  }, []);

  return { start, stop, reset, phase, intent, tips, result, error, startedAt, completedAt };
}
