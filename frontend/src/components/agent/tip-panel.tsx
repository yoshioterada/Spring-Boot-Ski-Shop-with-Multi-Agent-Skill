'use client';

import { Sparkles } from 'lucide-react';
import { useMemo } from 'react';

import { canonicalizeResort } from '@/lib/tips';

import { TipCard } from './tip-card';
import { WaitProgressBar } from './wait-progress-bar';

import type { AgentIntent, AgentPhase, AgentTip } from '@/hooks/use-agent-stream';

interface Props {
  phase: AgentPhase;
  intent: AgentIntent | null;
  tips: AgentTip[];
  startedAt: number | null;
  completedAt: number | null;
}

const MAX_VISIBLE = 5;

export function TipPanel({ phase, intent, tips, startedAt, completedAt }: Props) {
  const isActive = phase === 'INTENT' || phase === 'ORCHESTRATING';
  const isCompleted = phase === 'COMPLETED';

  const resortLabel = useMemo(() => {
    const raw = intent?.constraints?.destination;
    return canonicalizeResort(raw) ?? raw ?? null;
  }, [intent]);

  // 最新 5 件を新しいものを上に
  const visible = tips.slice(-MAX_VISIBLE).reverse();

  if (phase === 'IDLE') {
    return null;
  }

  return (
    <aside
      role="region"
      aria-label="待ち時間ガイド"
      className="space-y-3 rounded-xl border bg-card p-4 shadow-sm"
    >
      <header className="space-y-1">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-blue-600" aria-hidden />
          <h2 className="text-sm font-bold">
            {resortLabel ? `${resortLabel} 待ち時間ガイド` : '待ち時間ガイド'}
          </h2>
        </div>
        <p className="text-xs text-muted-foreground">
          {phase === 'INTENT'
            ? '行き先を読み取っています…'
            : isCompleted
              ? `${tips.length} 件のお役立ち情報をお届けしました。`
              : resortLabel
                ? `お待ちいただいている間、${resortLabel}にまつわる耳寄り情報をお届けします。`
                : 'お待ちいただいている間、スキー旅行に役立つ情報をお届けしますね。'}
        </p>
      </header>

      <WaitProgressBar
        phase={phase}
        startedAt={startedAt}
        completedAt={completedAt}
      />

      {phase === 'ERROR' && (
        <div
          role="alert"
          className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800"
        >
          すみません、いま AI アドバイザーの調子が良くないようです。お待ちの間、{resortLabel ?? 'スキー旅行'} のお役立ち情報をご紹介しますね。
        </div>
      )}

      {visible.length === 0 && isActive && (
        <div className="rounded-lg bg-muted/40 p-3 text-xs text-muted-foreground">
          まもなく最初のお役立ち情報をお届けします…
        </div>
      )}

      <div className="space-y-2">
        {visible.map((tip, idx) => (
          <TipCard key={`${tip.id}-${tip.deliveredAt}-${idx}`} tip={tip} />
        ))}
      </div>

      {isCompleted && (
        <div className="rounded-lg bg-emerald-50 p-3 text-center text-xs font-semibold text-emerald-800">
          🎉 結果が届きました！左の回答をご確認くださいね。
        </div>
      )}
    </aside>
  );
}
