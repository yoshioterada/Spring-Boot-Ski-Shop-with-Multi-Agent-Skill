'use client';

import { Bot, LogIn, Send, Sparkles, User } from 'lucide-react';
import Link from 'next/link';
import { useSession } from 'next-auth/react';
import { useEffect, useRef, useState } from 'react';

import { TipPanel } from '@/components/agent/tip-panel';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAgentStream } from '@/hooks/use-agent-stream';
import { generateUUID } from '@/lib/uuid';

import type { OrchestratorResponse } from '@/lib/orchestrator-client';

interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
  data?: OrchestratorResponse;
  error?: string;
}

/**
 * AI 推奨結果から、利用者向けのアシスタント発話テキストを組み立てる。
 *
 * Multi-Agent Orchestrator は推奨商品を実カートに自動追加するため
 * (payment-cart-service の CartBuildService.persistToUserCart 参照)、
 * 利用者がそれを必ず認識できるよう、本文末尾に「カート追加 + 確認のお願い」の
 * 案内文を必ず付与する。
 */
function composeAssistantText(result: OrchestratorResponse): string {
  const head = (result.orchestrationSummary ?? '').trim() || '推奨内容を取得しました。';
  const items = result.quote?.items ?? [];
  if (items.length === 0) {
    return head;
  }
  const itemList = items
    .map((it) => `・${it.productName} × ${it.quantity}（¥${it.lineTotal.toLocaleString()}）`)
    .join('\n');
  const total = result.quote ? `合計 ¥${result.quote.totalAmount.toLocaleString()}` : '';
  const cartNotice =
    '\n\n🛒 上記の商品はお客様のカートにすでに追加されています。\n' +
    `${itemList}\n${total}\n\n` +
    'カートの中身は画面右上の「カート」アイコンからご確認いただけます。\n' +
    '内容を見て、必要に応じて数量の変更や、不要な商品の削除をお願いします。\n' +
    'そのままご購入手続きに進むこともできますので、ぜひご活用ください。';
  return head + cartNotice;
}

/**
 * Multi-Agent Orchestrator チャット画面 (SSE 対応版)。
 *
 * 待機中、右側パネルに行き先に応じた "耳寄り情報" を 8 秒ごとにストリーミング表示する。
 * 詳細仕様: design-docs/add-info-2-customer-spec.md
 */
export default function OrchestratorChatPage() {
  const { data: session, status } = useSession();
  const isAuthenticated =
    status === 'authenticated' &&
    Boolean((session as unknown as { accessToken?: string })?.accessToken);
  const [input, setInput] = useState('');
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [usePoints, setUsePoints] = useState(false);
  const [couponCode, setCouponCode] = useState('');
  // Note: sessionId is not used in rendered HTML output, so SSR/CSR UUID difference is safe
  const [sessionId] = useState(() => `web-${generateUUID()}`);

  const { start, reset, phase, intent, tips, result, error, startedAt, completedAt } =
    useAgentStream();

  const loading = phase === 'INTENT' || phase === 'ORCHESTRATING';
  const lastTurnIsUser = turns.length > 0 && turns[turns.length - 1].role === 'user';

  const lastResultRef = useRef<OrchestratorResponse | null>(null);
  const lastErrorRef = useRef<string | null>(null);

  useEffect(() => {
    if (result && result !== lastResultRef.current) {
      lastResultRef.current = result;
      setTurns((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: composeAssistantText(result),
          data: result,
        },
      ]);
    }
  }, [result]);

  useEffect(() => {
    if (error && error !== lastErrorRef.current && phase === 'ERROR') {
      lastErrorRef.current = error;
      setTurns((prev) => [
        ...prev,
        { role: 'assistant', text: '申し訳ありません、エラーが発生しました', error },
      ]);
    }
  }, [error, phase]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    setTurns((prev) => [...prev, { role: 'user', text: trimmed }]);
    setInput('');
    lastResultRef.current = null;
    lastErrorRef.current = null;

    const userId = (session?.user?.id as string | undefined) ?? 'guest';
    await start({
      userId,
      message: trimmed,
      sessionId,
      couponCode: couponCode.trim() || undefined,
      usePoints,
    });
  };

  return (
    <div className="container mx-auto max-w-7xl px-4 py-8">
      <header className="mb-6 flex items-center gap-3">
        <Sparkles className="h-7 w-7 text-blue-600" aria-hidden />
        <div>
          <h1 className="text-2xl font-bold">AI スキー装備アドバイザー</h1>
          <p className="text-sm text-muted-foreground">
            天気・スキルレベル・予算・在庫を考慮して、Multi-Agent が最適なセットを提案します
          </p>
        </div>
      </header>

      {!isAuthenticated && (
        <div
          role="alert"
          className="mb-6 flex flex-col items-start justify-between gap-3 rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-900 sm:flex-row sm:items-center"
        >
          <div className="flex items-start gap-3">
            <LogIn className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
            <div>
              <p className="font-semibold">この機能をご利用いただくにはログインが必要です</p>
              <p className="text-sm">
                AI アドバイザーは購入履歴・ティア・ポイント情報を活用するため、ログインが必須となっています。
              </p>
            </div>
          </div>
          <Link
            href={{ pathname: '/login', query: { callbackUrl: '/agent' } }}
            className="inline-flex shrink-0 items-center gap-1 rounded-md bg-amber-900 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-800"
          >
            ログイン
          </Link>
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0">
          <section
            aria-label="会話履歴"
            className="mb-4 min-h-[20rem] space-y-4 rounded-lg border bg-card p-4"
          >
            {turns.length === 0 && (
              <div className="text-center text-sm text-muted-foreground">
                <p>例: 「来週末の白馬で初心者向けのスキー一式を5万円以内で揃えたい」</p>
                <p className="mt-2 text-xs">
                  ※ 複数の AI エージェントが連携して推論するため、回答まで数分〜最大 20 分かかることがあります。
                  待ち時間中は右側に行き先のお役立ち情報をお届けします。
                </p>
              </div>
            )}
            {turns.map((turn, idx) => (
              <ChatBubble key={idx} turn={turn} />
            ))}
            {loading && lastTurnIsUser && (
              <div className="flex items-start gap-3">
                <Bot
                  className="mt-1 h-5 w-5 shrink-0 animate-pulse text-blue-600"
                  aria-hidden
                />
                <div className="rounded-lg bg-muted px-4 py-3 text-sm text-muted-foreground">
                  エージェントが推論中です。
                  {phase === 'INTENT'
                    ? ' まずは行き先を読み取っています…'
                    : ' 装備・在庫・クーポンを最適化しています…'}
                  <p className="mt-1 text-xs">
                    お待ちの間、右側に行き先のお役立ち情報をお届けしますね。
                  </p>
                </div>
              </div>
            )}
          </section>

          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="flex flex-wrap gap-3 text-sm">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={usePoints}
                  onChange={(e) => setUsePoints(e.target.checked)}
                  className="h-4 w-4"
                  disabled={loading}
                />
                ポイントを使う
              </label>
              <Input
                placeholder="クーポンコード (任意)"
                value={couponCode}
                onChange={(e) => setCouponCode(e.target.value)}
                className="w-48"
                disabled={loading}
              />
              {(phase === 'COMPLETED' || phase === 'ERROR' || phase === 'CANCELLED') && (
                <button
                  type="button"
                  onClick={reset}
                  className="text-xs text-muted-foreground underline hover:text-foreground"
                >
                  状態をリセット
                </button>
              )}
            </div>
            <div className="flex gap-2">
              <Input
                placeholder={
                  isAuthenticated
                    ? 'ご要望を自然な言葉で入力してください'
                    : 'ログイン後に利用できます'
                }
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={loading || !isAuthenticated}
                aria-label="エージェントへのメッセージ"
              />
              <Button
                type="submit"
                disabled={loading || !input.trim() || !isAuthenticated}
              >
                <Send className="mr-2 h-4 w-4" aria-hidden /> 送信
              </Button>
            </div>
          </form>
        </div>

        <aside className="min-w-0 md:sticky md:top-4 md:self-start">
          {phase === 'IDLE' ? (
            <div className="rounded-xl border border-dashed bg-muted/30 p-4 text-xs text-muted-foreground">
              <div className="mb-2 flex items-center gap-2 font-semibold text-foreground">
                <Sparkles className="h-4 w-4 text-blue-600" aria-hidden /> 待ち時間ガイド
              </div>
              <p>
                ご質問を送信すると、AI が処理中の待ち時間にこのエリアで行き先 (例: 志賀高原・白馬・ニセコ)
                のお役立ち情報を 8 秒ごとに紹介します。
              </p>
            </div>
          ) : (
            <TipPanel
              phase={phase}
              intent={intent}
              tips={tips}
              startedAt={startedAt}
              completedAt={completedAt}
            />
          )}
        </aside>
      </div>
    </div>
  );
}

function ChatBubble({ turn }: { turn: ChatTurn }) {
  const isUser = turn.role === 'user';
  const Icon = isUser ? User : Bot;
  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      {!isUser && <Icon className="mt-1 h-5 w-5 shrink-0 text-blue-600" aria-hidden />}
      <div
        className={`max-w-[80%] rounded-lg px-4 py-3 ${
          isUser ? 'bg-blue-600 text-white' : 'bg-muted'
        }`}
      >
        <p className="whitespace-pre-wrap text-sm">{turn.text}</p>
        {turn.error && (
          <p className="mt-2 text-xs text-red-600" role="alert">
            {turn.error}
          </p>
        )}
        {turn.data && <RecommendationDetail data={turn.data} />}
      </div>
      {isUser && <Icon className="mt-1 h-5 w-5 shrink-0" aria-hidden />}
    </div>
  );
}

function RecommendationDetail({ data }: { data: OrchestratorResponse }) {  return (
    <div className="mt-3 space-y-3 border-t border-border pt-3 text-xs">
      {data.intentSummary && <Section label="意図">{data.intentSummary}</Section>}
      {data.weatherSummary && <Section label="天気">{data.weatherSummary}</Section>}
      {data.equipmentRecommendation && (
        <Section label="推奨装備">{data.equipmentRecommendation}</Section>
      )}
      {data.couponSummary && <Section label="クーポン">{data.couponSummary}</Section>}
      {data.quote && (
        <div>
          <h3 className="mb-2 font-semibold">見積もり</h3>
          <ul className="space-y-1">
            {data.quote.items.map((item) => (
              <li key={item.productId} className="flex justify-between">
                <span>
                  {item.productName} × {item.quantity}
                </span>
                <span>¥{item.lineTotal.toLocaleString()}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-2 space-y-0.5">
            <Row label="小計" value={data.quote.subtotal} />
            <Row label="クーポン値引き" value={-data.quote.couponDiscount} />
            <Row label="ポイント値引き" value={-data.quote.pointDiscount} />
            <Row label="合計" value={data.quote.totalAmount} bold />
          </dl>
        </div>
      )}
    </div>
  );
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <span className="font-semibold">{label}: </span>
      <span>{children}</span>
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: number; bold?: boolean }) {
  return (
    <div className={`flex justify-between ${bold ? 'font-bold' : ''}`}>
      <dt>{label}</dt>
      <dd>¥{value.toLocaleString()}</dd>
    </div>
  );
}
