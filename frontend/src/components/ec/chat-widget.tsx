'use client';

import {
  History,
  Loader2,
  MessageCircle,
  Phone,
  Send,
  ThumbsDown,
  ThumbsUp,
  X,
} from 'lucide-react';
import Link from 'next/link';
import { useSession } from 'next-auth/react';
import { useCallback, useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  feedbackGiven?: boolean;
}

interface PastSession {
  id: string;
  createdAt: string;
  lastMessage?: string;
}

function parseProductLinks(content: string): React.ReactNode[] {
  const regex = /\[product:(\d+)\|([^\]]+)\]/g;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(content)) !== null) {
    if (match.index > lastIndex) {
      parts.push(content.slice(lastIndex, match.index));
    }
    const productId = match[1];
    const productName = match[2];
    parts.push(
      <Link
        key={`${productId}-${match.index}`}
        href={`/product/${productId}` as never}
        className="text-primary hover:text-primary/80 font-medium underline underline-offset-2"
      >
        {productName}
      </Link>,
    );
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < content.length) {
    parts.push(content.slice(lastIndex));
  }

  return parts.length > 0 ? parts : [content];
}

function TypingIndicator() {
  return (
    <div className="flex items-center gap-1 px-3 py-2">
      <span className="bg-muted-foreground/50 size-2 animate-bounce rounded-full [animation-delay:0ms]" />
      <span className="bg-muted-foreground/50 size-2 animate-bounce rounded-full [animation-delay:150ms]" />
      <span className="bg-muted-foreground/50 size-2 animate-bounce rounded-full [animation-delay:300ms]" />
    </div>
  );
}

export function ChatWidget() {
  const { data: session } = useSession();
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [chatSessionId, setChatSessionId] = useState<string | null>(null);
  const [isServiceDown, setIsServiceDown] = useState(false);
  const [intents, setIntents] = useState<string[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [pastSessions, setPastSessions] = useState<PastSession[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading, scrollToBottom]);

  useEffect(() => {
    const handler = () => setIsOpen(true);
    window.addEventListener('open-ai-chat', handler);
    return () => window.removeEventListener('open-ai-chat', handler);
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    fetch('/api/chat/intents')
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('Failed'))))
      .then((data: unknown) => {
        const items = Array.isArray(data) ? data : ((data as Record<string, unknown>).intents as unknown[] ?? []);
        const names = items.map((item: unknown) =>
          typeof item === 'string' ? item : (item as Record<string, string>).name ?? '',
        ).filter(Boolean);
        setIntents(names);
        setIsServiceDown(false);
      })
      .catch(() => {
        setIsServiceDown(true);
      });
  }, [isOpen]);

  const createSession = async (): Promise<string | null> => {
    try {
      const res = await fetch('/api/chat/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      if (!res.ok) throw new Error('Failed to create session');
      const data = (await res.json()) as { sessionId?: string; id?: string };
      const id = data.sessionId ?? data.id;
      if (id) {
        setChatSessionId(id);
        return id;
      }
      return null;
    } catch {
      setIsServiceDown(true);
      return null;
    }
  };

  const sendMessage = async (content: string) => {
    if (!content.trim()) return;

    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: content.trim(),
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    let currentSessionId = chatSessionId;
    if (!currentSessionId) {
      currentSessionId = await createSession();
      if (!currentSessionId) {
        setMessages((prev) => [
          ...prev,
          {
            id: crypto.randomUUID(),
            role: 'assistant',
            content: 'AI チャットは現在ご利用いただけません。しばらくしてからお試しください。',
            timestamp: new Date().toISOString(),
          },
        ]);
        setIsLoading(false);
        return;
      }
    }

    try {
      const res = await fetch('/api/chat/message', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: currentSessionId,
          message: content.trim(),
        }),
      });

      if (!res.ok) throw new Error('Failed to send message');
      const data = (await res.json()) as { content?: string; message?: string; reply?: string };
      const reply = data.content ?? data.message ?? data.reply ?? '';

      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: reply,
          timestamp: new Date().toISOString(),
        },
      ]);
      setIsServiceDown(false);
    } catch {
      setIsServiceDown(true);
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content:
            'AI チャットは現在ご利用いただけません。お問い合わせはお電話でも承っております。',
          timestamp: new Date().toISOString(),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    void sendMessage(input);
  };

  const handleIntentSelect = (intent: string) => {
    void sendMessage(intent);
  };

  const handleFeedback = async (messageId: string, positive: boolean) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === messageId ? { ...m, feedbackGiven: true } : m)),
    );
    try {
      await fetch('/api/chat/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: chatSessionId,
          messageId,
          positive,
        }),
      });
    } catch {
      // Feedback is best-effort
    }
  };

  const handleEscalate = async () => {
    try {
      await fetch('/api/chat/escalate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: chatSessionId }),
      });
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: 'オペレーターへ接続をリクエストしました。しばらくお待ちください。',
          timestamp: new Date().toISOString(),
        },
      ]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: 'オペレーターへの接続に失敗しました。お手数ですがお電話をご利用ください。',
          timestamp: new Date().toISOString(),
        },
      ]);
    }
  };

  const loadPastSessions = async () => {
    try {
      const res = await fetch('/api/chat/sessions');
      if (!res.ok) return;
      const data = (await res.json()) as PastSession[];
      setPastSessions(Array.isArray(data) ? data : []);
    } catch {
      // Silently fail for history
    }
  };

  const loadSessionHistory = async (sessionId: string) => {
    try {
      const res = await fetch(`/api/chat/history/${sessionId}`);
      if (!res.ok) return;
      const data = (await res.json()) as { messages?: ChatMessage[] } | ChatMessage[];
      const loaded = Array.isArray(data) ? data : (data.messages ?? []);
      setMessages(loaded);
      setChatSessionId(sessionId);
      setShowHistory(false);
    } catch {
      // Silently fail
    }
  };

  const toggleHistory = () => {
    if (!showHistory) {
      void loadPastSessions();
    }
    setShowHistory(!showHistory);
  };

  if (!session) return null;

  return (
    <>
      {/* Floating trigger button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className="bg-primary text-primary-foreground fixed right-6 bottom-6 z-50 flex size-14 items-center justify-center rounded-full shadow-lg transition-transform hover:scale-110"
          aria-label="AI チャットを開く"
        >
          <MessageCircle className="size-6" />
        </button>
      )}

      {/* Chat panel */}
      <div
        className={`bg-background fixed right-6 bottom-6 z-50 flex h-[500px] w-[400px] max-w-[calc(100vw-2rem)] flex-col rounded-2xl border shadow-2xl transition-all duration-300 ${
          isOpen
            ? 'pointer-events-auto translate-y-0 opacity-100'
            : 'pointer-events-none translate-y-4 opacity-0'
        } max-sm:right-0 max-sm:bottom-0 max-sm:h-full max-sm:w-full max-sm:max-w-none max-sm:rounded-none`}
        role="dialog"
        aria-label="AI チャット"
        aria-hidden={!isOpen}
      >
        {/* Header */}
        <div className="bg-primary text-primary-foreground flex items-center justify-between rounded-t-2xl px-4 py-3 max-sm:rounded-none">
          <span className="font-semibold">AI アシスタント</span>
          <div className="flex gap-1">
            <button
              onClick={toggleHistory}
              className="rounded p-1.5 hover:bg-white/20"
              title="過去のセッション"
            >
              <History className="size-4" />
            </button>
            <button
              onClick={() => void handleEscalate()}
              className="rounded p-1.5 hover:bg-white/20"
              title="オペレーターに繋ぐ"
            >
              <Phone className="size-4" />
            </button>
            <button
              onClick={() => setIsOpen(false)}
              className="rounded p-1.5 hover:bg-white/20"
              aria-label="チャットを閉じる"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* Past sessions view */}
        {showHistory ? (
          <div className="flex-1 overflow-y-auto p-4">
            <h3 className="text-muted-foreground mb-3 text-sm font-medium">過去のセッション</h3>
            {pastSessions.length === 0 ? (
              <p className="text-muted-foreground text-center text-sm">履歴はありません</p>
            ) : (
              <div className="space-y-2">
                {pastSessions.map((s) => (
                  <button
                    key={s.id}
                    onClick={() => void loadSessionHistory(s.id)}
                    className="hover:bg-muted w-full rounded-lg border p-3 text-left transition-colors"
                  >
                    <p className="truncate text-sm">{s.lastMessage ?? 'セッション'}</p>
                    <p className="text-muted-foreground mt-1 text-xs">
                      {new Date(s.createdAt).toLocaleString('ja-JP')}
                    </p>
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <>
            {/* Messages area */}
            <div
              className="flex-1 overflow-y-auto p-4"
              aria-live="polite"
              aria-relevant="additions"
            >
              {/* Service down banner */}
              {isServiceDown && (
                <div className="bg-destructive/10 text-destructive mb-3 rounded-lg p-3 text-center text-sm">
                  AI チャットは現在ご利用いただけません
                </div>
              )}

              {/* Intent quick selection */}
              {messages.length === 0 && !isServiceDown && (
                <div className="space-y-3">
                  <p className="text-muted-foreground text-center text-sm">
                    何についてお尋ねですか？
                  </p>
                  <div className="flex flex-wrap justify-center gap-2">
                    {intents.map((intent) => (
                      <Button
                        key={intent}
                        variant="outline"
                        size="sm"
                        onClick={() => handleIntentSelect(intent)}
                      >
                        {intent}
                      </Button>
                    ))}
                  </div>
                </div>
              )}

              {/* Message bubbles */}
              <div className="space-y-3">
                {messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                  >
                    <div className="max-w-[85%]">
                      <div
                        className={`rounded-2xl px-3 py-2 text-sm whitespace-pre-wrap ${
                          msg.role === 'user'
                            ? 'bg-primary text-primary-foreground rounded-br-sm'
                            : 'bg-muted text-foreground rounded-bl-sm'
                        }`}
                      >
                        {msg.role === 'assistant' ? parseProductLinks(msg.content) : msg.content}
                      </div>

                      {/* Feedback buttons for assistant messages */}
                      {msg.role === 'assistant' && !msg.feedbackGiven && (
                        <div className="mt-1 flex gap-1">
                          <button
                            onClick={() => void handleFeedback(msg.id, true)}
                            className="text-muted-foreground hover:text-primary rounded p-1 transition-colors"
                            title="役に立った"
                          >
                            <ThumbsUp className="size-3" />
                          </button>
                          <button
                            onClick={() => void handleFeedback(msg.id, false)}
                            className="text-muted-foreground hover:text-destructive rounded p-1 transition-colors"
                            title="役に立たなかった"
                          >
                            <ThumbsDown className="size-3" />
                          </button>
                        </div>
                      )}
                      {msg.role === 'assistant' && msg.feedbackGiven && (
                        <p className="text-muted-foreground mt-1 text-xs">
                          フィードバックありがとうございます
                        </p>
                      )}
                    </div>
                  </div>
                ))}

                {/* Typing indicator */}
                {isLoading && (
                  <div className="flex justify-start">
                    <div className="bg-muted rounded-2xl rounded-bl-sm">
                      <TypingIndicator />
                    </div>
                  </div>
                )}
              </div>

              <div ref={messagesEndRef} />
            </div>

            {/* Input area */}
            <div className="border-t p-3">
              <form onSubmit={handleSend} className="flex gap-2">
                <Input
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="メッセージを入力..."
                  disabled={isLoading || isServiceDown}
                />
                <Button type="submit" size="icon" disabled={!input.trim() || isLoading}>
                  {isLoading ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <Send className="size-4" />
                  )}
                </Button>
              </form>
            </div>
          </>
        )}
      </div>
    </>
  );
}
