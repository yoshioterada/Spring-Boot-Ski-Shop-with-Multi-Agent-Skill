'use client';

import { Bot, LogIn, Send, Sparkles, User } from 'lucide-react';
import Link from 'next/link';
import { useSession } from 'next-auth/react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { recommendWithAgents, type OrchestratorResponse } from '@/lib/orchestrator-client';

interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
  data?: OrchestratorResponse;
  error?: string;
}

/**
 * Multi-Agent Orchestrator チャット画面。
 * 自然言語で要望を送信すると、Orchestrator が天気・装備・クーポン・在庫の各 Worker を
 * 統合した結果を JSON として返却し、構造化表示する。
 */
export default function OrchestratorChatPage() {
  const { data: session, status } = useSession();
  const isAuthenticated = status === 'authenticated' && Boolean((session as unknown as { accessToken?: string })?.accessToken);
  const [input, setInput] = useState('');
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [loading, setLoading] = useState(false);
  const [usePoints, setUsePoints] = useState(false);
  const [couponCode, setCouponCode] = useState('');

  // セッション ID は会話単位で固定（Orchestrator 側で会話履歴を将来活用予定）
  const [sessionId] = useState(() => `web-${crypto.randomUUID()}`);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    const userTurn: ChatTurn = { role: 'user', text: trimmed };
    setTurns((prev) => [...prev, userTurn]);
    setInput('');
    setLoading(true);

    try {
      // 未ログイン時は guest を userId として送信（Orchestrator 側はゲスト推奨にフォールバック）
      const userId = (session?.user?.id as string | undefined) ?? 'guest';
      const bearer = (session as unknown as { accessToken?: string })?.accessToken;
      const response = await recommendWithAgents(
        {
          userId,
          message: trimmed,
          sessionId,
          couponCode: couponCode.trim() || undefined,
          usePoints,
        },
        bearer,
      );
      setTurns((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: response.orchestrationSummary || '回答を取得しました',
          data: response,
        },
      ]);
    } catch (err) {
      const message = err instanceof Error ? err.message : '通信エラーが発生しました';
      setTurns((prev) => [
        ...prev,
        { role: 'assistant', text: '申し訳ありません、エラーが発生しました', error: message },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
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

      <section
        aria-label="会話履歴"
        className="mb-6 min-h-[20rem] space-y-4 rounded-lg border bg-card p-4"
      >
        {turns.length === 0 && (
          <div className="text-center text-sm text-muted-foreground">
            <p>例: 「来週末の白馬で初心者向けのスキー一式を5万円以内で揃えたい」</p>
            <p className="mt-2 text-xs">
              ※ 複数の AI エージェントが連携して推論するため、回答まで 10～20 分かかることがあります。送信後はタブを閉じずにお待ちください。
            </p>
          </div>
        )}
        {turns.map((turn, idx) => (
          <ChatBubble key={idx} turn={turn} />
        ))}
        {loading && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Bot className="h-4 w-4 animate-pulse" aria-hidden /> エージェントが推論中... <span className="text-xs">(最大 20 分程度かかります)</span>
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
            />
            ポイントを使う
          </label>
          <Input
            placeholder="クーポンコード (任意)"
            value={couponCode}
            onChange={(e) => setCouponCode(e.target.value)}
            className="w-48"
          />
        </div>
        <div className="flex gap-2">
          <Input
            placeholder={isAuthenticated ? 'ご要望を自然な言葉で入力してください' : 'ログイン後に利用できます'}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={loading || !isAuthenticated}
            aria-label="エージェントへのメッセージ"
          />
          <Button type="submit" disabled={loading || !input.trim() || !isAuthenticated}>
            <Send className="mr-2 h-4 w-4" aria-hidden /> 送信
          </Button>
        </div>
      </form>
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

function RecommendationDetail({ data }: { data: OrchestratorResponse }) {
  return (
    <div className="mt-3 space-y-3 border-t border-border pt-3 text-xs">
      {data.intentSummary && (
        <Section label="意図">{data.intentSummary}</Section>
      )}
      {data.weatherSummary && (
        <Section label="天気">{data.weatherSummary}</Section>
      )}
      {data.equipmentRecommendation && (
        <Section label="推奨装備">{data.equipmentRecommendation}</Section>
      )}
      {data.couponSummary && (
        <Section label="クーポン">{data.couponSummary}</Section>
      )}
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
