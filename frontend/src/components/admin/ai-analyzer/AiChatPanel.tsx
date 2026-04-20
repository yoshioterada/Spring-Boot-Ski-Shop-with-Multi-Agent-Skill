'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Bot, Loader2, Send, AlertTriangle, Package } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { toast } from 'sonner';

// ── Types ──
type ChatPhase = 'IDLE' | 'INTENT' | 'FETCHING_DATA' | 'STREAMING' | 'COMPLETED' | 'ERROR';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ActionHint {
  type: string;
  sku: string;
  reason: string;
  severity: string;
}

// ── Component ──
export function AiChatPanel() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [phase, setPhase] = useState<ChatPhase>('IDLE');
  const [actions, setActions] = useState<ActionHint[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [streamingText, setStreamingText] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, streamingText]);

  const sendMessage = useCallback(async () => {
    const text = input.trim();
    if (!text || phase === 'STREAMING' || phase === 'FETCHING_DATA') return;

    setInput('');
    setMessages((prev) => [...prev, { role: 'user', content: text, timestamp: new Date() }]);
    setStreamingText('');
    setActions([]);

    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setPhase('INTENT');

    try {
      const res = await fetch('/api/admin/ai-analyzer/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ sessionId, message: text }),
        signal: ctrl.signal,
      });

      if (!res.ok || !res.body) {
        toast.error(`エラー: HTTP ${res.status}`);
        setPhase('ERROR');
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let fullText = '';

      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let idx: number;
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);

          const { event, data } = parseSseFrame(frame);
          if (!data) continue;

          switch (event) {
            case 'phase': {
              const p = data as { phase?: string; tool?: string };
              if (p.phase === 'FETCHING_DATA') setPhase('FETCHING_DATA');
              else if (p.phase === 'COMPLETED') setPhase('COMPLETED');
              break;
            }
            case 'token': {
              setPhase('STREAMING');
              const t = data as { text?: string };
              if (t.text) {
                fullText += t.text;
                setStreamingText(fullText);
              }
              break;
            }
            case 'action': {
              const a = data as ActionHint;
              setActions((prev) => [...prev, a]);
              break;
            }
            case 'error': {
              const e = data as { message?: string };
              toast.error(e.message || '不明なエラー');
              setPhase('ERROR');
              break;
            }
          }
        }
      }

      // ストリーム完了 → メッセージ追加
      if (fullText) {
        setMessages((prev) => [...prev, { role: 'assistant', content: fullText, timestamp: new Date() }]);
        setStreamingText('');
      }
      if (phase !== 'ERROR') setPhase('COMPLETED');
    } catch (err) {
      if ((err as Error).name === 'AbortError') return;
      toast.error('通信エラーが発生しました');
      setPhase('ERROR');
    }
  }, [input, phase, sessionId]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div className="flex flex-col h-[600px] rounded-xl border bg-card shadow-sm">
      {/* Header */}
      <div className="border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <Bot className="h-5 w-5 text-primary" />
          <h3 className="font-semibold">AI に質問</h3>
          <PhaseIndicator phase={phase} />
        </div>
        <p className="text-muted-foreground text-sm mt-1">
          売上・在庫・顧客データについて自然言語で質問できます。AI が複数のデータソースを横断的に分析し、回答を生成します。
        </p>
      </div>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-4" aria-live="polite">
        {messages.length === 0 && phase === 'IDLE' && (
          <div className="text-center text-muted-foreground mt-8">
            <Bot className="h-12 w-12 mx-auto mb-2 opacity-50" />
            <p>売上・在庫・顧客データについて質問できます</p>
            <p className="text-sm mt-1">例: 「直近 30 日のスキーブーツの売上を分析して」</p>
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[80%] rounded-lg px-4 py-2 text-sm ${
                msg.role === 'user'
                  ? 'bg-primary text-primary-foreground whitespace-pre-wrap'
                  : 'bg-muted'
              }`}
              aria-label={msg.role === 'user' ? 'あなたのメッセージ' : 'AI の回答'}
            >
              {msg.role === 'user' ? (
                msg.content
              ) : (
                <MarkdownContent>{msg.content}</MarkdownContent>
              )}
            </div>
          </div>
        ))}

        {/* Streaming text */}
        {streamingText && (
          <div className="flex justify-start">
            <div className="max-w-[80%] rounded-lg px-4 py-2 text-sm bg-muted">
              <MarkdownContent>{streamingText}</MarkdownContent>
              <span className="inline-block w-2 h-4 bg-primary animate-pulse ml-1 align-middle" />
            </div>
          </div>
        )}

        {/* Loading */}
        {(phase === 'INTENT' || phase === 'FETCHING_DATA') && (
          <div className="flex items-center gap-2 text-muted-foreground text-sm">
            <Loader2 className="h-4 w-4 animate-spin" />
            {phase === 'INTENT' ? '質問を解析中...' : 'データを取得中...'}
          </div>
        )}
      </div>

      {/* Action hints */}
      {actions.length > 0 && (
        <div className="border-t px-4 py-2 space-y-1" aria-label="アクション提案">
          {actions.map((a, i) => (
            <div key={i} className="flex items-center gap-2 rounded bg-amber-50 dark:bg-amber-950 px-3 py-1.5 text-sm">
              <Package className="h-4 w-4 text-amber-600" />
              <span className="font-medium">{a.sku}</span>
              <span className="text-muted-foreground">{a.reason}</span>
            </div>
          ))}
        </div>
      )}

      {/* Input */}
      <div className="border-t px-4 py-3">
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="質問を入力..."
            disabled={phase === 'STREAMING' || phase === 'FETCHING_DATA'}
            className="flex-1 rounded-lg border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label="AI への質問入力"
          />
          <button
            onClick={sendMessage}
            disabled={!input.trim() || phase === 'STREAMING' || phase === 'FETCHING_DATA'}
            className="rounded-lg bg-primary px-3 py-2 text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            aria-label="送信"
          >
            <Send className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

function PhaseIndicator({ phase }: { phase: ChatPhase }) {
  if (phase === 'IDLE' || phase === 'COMPLETED') return null;
  if (phase === 'ERROR') {
    return (
      <span className="ml-auto flex items-center gap-1 text-xs text-destructive">
        <AlertTriangle className="h-3 w-3" /> エラー
      </span>
    );
  }
  return (
    <span className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
      <Loader2 className="h-3 w-3 animate-spin" />
      {phase === 'INTENT' ? '解析中' : phase === 'FETCHING_DATA' ? 'データ取得中' : '生成中'}
    </span>
  );
}

function parseSseFrame(frame: string): { event: string; data: unknown } {
  const lines = frame.split('\n');
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of lines) {
    if (!line || line.startsWith(':')) continue;
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return { event, data: null };
  try {
    return { event, data: JSON.parse(dataLines.join('\n')) };
  } catch {
    return { event, data: null };
  }
}

// ── Markdown レンダラー ──
const mdComponents: React.ComponentProps<typeof ReactMarkdown>['components'] = {
  table: ({ children }) => (
    <div className="overflow-x-auto my-2">
      <table className="min-w-full border-collapse text-xs">{children}</table>
    </div>
  ),
  thead: ({ children }) => (
    <thead className="border-b border-border font-semibold">{children}</thead>
  ),
  th: ({ children }) => (
    <th className="border border-border px-2 py-1 text-left font-semibold bg-muted-foreground/10">{children}</th>
  ),
  td: ({ children }) => (
    <td className="border border-border px-2 py-1">{children}</td>
  ),
  tr: ({ children, ...props }) => (
    <tr className="even:bg-muted/40" {...(props as React.HTMLAttributes<HTMLTableRowElement>)}>{children}</tr>
  ),
  p: ({ children }) => <p className="mb-2 last:mb-0 leading-relaxed">{children}</p>,
  ul: ({ children }) => <ul className="list-disc pl-4 mb-2 space-y-0.5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-4 mb-2 space-y-0.5">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  h1: ({ children }) => <h1 className="text-base font-bold mt-3 mb-1">{children}</h1>,
  h2: ({ children }) => <h2 className="text-sm font-bold mt-2 mb-1">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-semibold mt-2 mb-0.5">{children}</h3>,
  code: ({ children }) => (
    <code className="bg-muted-foreground/10 rounded px-1 text-xs font-mono">{children}</code>
  ),
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-primary pl-3 italic my-2 text-muted-foreground">{children}</blockquote>
  ),
  hr: () => <hr className="my-3 border-border" />,
};

function MarkdownContent({ children }: { children: string }) {
  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
      {children}
    </ReactMarkdown>
  );
}
