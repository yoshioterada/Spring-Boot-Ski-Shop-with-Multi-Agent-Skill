'use client';

import { useEffect, useState } from 'react';

import type { AgentPhase } from '@/hooks/use-agent-stream';

interface Props {
  phase: AgentPhase;
  startedAt: number | null;
  completedAt: number | null;
  /** 想定所要時間 (秒)。デフォルト 120 秒 */
  expectedSeconds?: number;
}

const PHASE_LABEL: Record<AgentPhase, string> = {
  IDLE: '待機中',
  INTENT: '行き先を読み取っています',
  ORCHESTRATING: 'エージェントが装備を選定中',
  COMPLETED: '完了しました',
  ERROR: 'エラーが発生しました',
  CANCELLED: 'キャンセルしました',
};

function formatMmSs(sec: number): string {
  const m = Math.floor(sec / 60).toString();
  const s = (sec % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

export function WaitProgressBar({ phase, startedAt, completedAt, expectedSeconds = 120 }: Props) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (phase === 'COMPLETED' || phase === 'ERROR' || phase === 'CANCELLED' || !startedAt) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [phase, startedAt]);

  const elapsedMs = startedAt
    ? (completedAt ?? now) - startedAt
    : 0;
  const elapsedSec = Math.max(0, Math.floor(elapsedMs / 1000));
  const ratio =
    phase === 'COMPLETED'
      ? 1
      : Math.min(0.95, elapsedSec / expectedSeconds);

  const dotsTotal = 10;
  const filled = Math.round(ratio * dotsTotal);
  const dots = Array.from({ length: dotsTotal }, (_, i) =>
    i < filled ? '●' : '○',
  ).join('');

  return (
    <div className="space-y-1.5 rounded-lg bg-muted/40 p-3 text-xs">
      <div className="flex items-center justify-between">
        <span aria-hidden className="font-mono text-blue-600">
          [{dots}]
        </span>
        <span className="font-mono tabular-nums text-muted-foreground">
          {formatMmSs(elapsedSec)} / 想定 {formatMmSs(expectedSeconds)}
        </span>
      </div>
      <div className="text-muted-foreground" role="status" aria-live="polite">
        状態: {PHASE_LABEL[phase]}
      </div>
    </div>
  );
}
