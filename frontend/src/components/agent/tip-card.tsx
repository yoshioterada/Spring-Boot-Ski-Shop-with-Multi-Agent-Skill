'use client';

import type { AgentTip } from '@/hooks/use-agent-stream';

const CATEGORY_BG: Record<AgentTip['category'], string> = {
  SNOW: 'from-sky-50 to-blue-50 border-l-sky-400',
  LIFT: 'from-violet-50 to-indigo-50 border-l-indigo-400',
  ACCESS: 'from-stone-50 to-zinc-50 border-l-zinc-400',
  RENTAL: 'from-amber-50 to-orange-50 border-l-orange-400',
  SCHOOL: 'from-emerald-50 to-teal-50 border-l-emerald-400',
  LODGING: 'from-rose-50 to-amber-50 border-l-rose-400',
  DINING: 'from-yellow-50 to-amber-50 border-l-amber-400',
  SAFETY: 'from-red-50 to-rose-50 border-l-red-400',
  GEAR: 'from-cyan-50 to-blue-50 border-l-cyan-400',
  EVENT: 'from-fuchsia-50 to-pink-50 border-l-fuchsia-400',
};

const CATEGORY_LABEL: Record<AgentTip['category'], string> = {
  SNOW: '雪質コンディション',
  LIFT: 'リフト・コース',
  ACCESS: 'アクセス・交通',
  RENTAL: 'レンタル・チューン',
  SCHOOL: 'スクール',
  LODGING: '宿泊・温泉',
  DINING: 'ゲレ食・周辺グルメ',
  SAFETY: '安全',
  GEAR: 'ギア・装備',
  EVENT: 'イベント',
};

interface Props {
  tip: AgentTip;
  /** 何件目か (アニメーション用 key 兼) */
  index?: number;
}

export function TipCard({ tip }: Props) {
  return (
    <article
      role="status"
      aria-live="polite"
      className={`relative animate-tip-in rounded-xl border border-transparent border-l-4 bg-gradient-to-br ${
        CATEGORY_BG[tip.category]
      } p-3 shadow-sm`}
    >
      <header className="mb-1 flex items-center justify-between text-xs">
        <span className="flex items-center gap-1.5 font-semibold text-foreground/80">
          <span aria-hidden className="text-base">
            {tip.icon}
          </span>
          {CATEGORY_LABEL[tip.category]}
        </span>
      </header>
      <h3 className="mb-1 text-sm font-semibold text-foreground">{tip.title}</h3>
      <p className="text-xs leading-relaxed text-foreground/85">{tip.message}</p>
    </article>
  );
}
