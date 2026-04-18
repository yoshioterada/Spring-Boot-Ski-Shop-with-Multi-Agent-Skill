import { NextResponse } from 'next/server';
import { getServerSession } from 'next-auth';

import { authOptions } from '@/lib/auth-options';

export const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

type FetchInit = Omit<RequestInit, 'headers'> & { headers?: Record<string, string> };

/**
 * Spring Cloud Gateway は keep-alive のアイドル接続を短時間で切断するため、
 * undici 接続プール再利用時に `UND_ERR_SOCKET: other side closed` が発生することがある。
 * - Connection: close で毎回新規接続を強制
 * - ソケット切断系エラーは 1 回だけリトライ
 * - cache: 'no-store' で Next.js のフェッチキャッシュを無効化
 */
async function rawFetch(url: string, init: FetchInit): Promise<Response> {
  const headers: Record<string, string> = {
    ...(init.headers ?? {}),
    Connection: 'close',
  };
  let lastErr: unknown;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      return await fetch(url, { ...init, headers, cache: 'no-store' });
    } catch (e) {
      lastErr = e;
      const cause = (e as { cause?: { code?: string } })?.cause;
      const code = cause?.code;
      const isSocketErr =
        code === 'UND_ERR_SOCKET' ||
        code === 'ECONNRESET' ||
        code === 'UND_ERR_CONNECT_TIMEOUT' ||
        code === 'UND_ERR_HEADERS_TIMEOUT';
      if (isSocketErr && attempt === 0) continue;
      throw lastErr;
    }
  }
  throw lastErr;
}

type ProxyOptions = {
  /** API Gateway へのパス。例: '/api/v1/categories' */
  path: string;
  /** GET 以外の HTTP メソッド */
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** リクエストボディ（JSON シリアライズされる） */
  body?: unknown;
  /** 追加クエリ文字列（'?' は不要） */
  search?: string;
  /** 認可不要の場合は false（デフォルト true） */
  requireAuth?: boolean;
};

/**
 * 認可済みセッションを使って API Gateway を呼び出し、結果を NextResponse として返す共通ヘルパ。
 * - 401 セッション欠如時は Unauthorized を返す
 * - 上流エラー（fetch 失敗）は 502 を返し、null を本文として返さない
 * - 上流が JSON 以外を返した場合や本文無しの場合も、フロントが `.content` 等にアクセスしても落ちないよう
 *   既知のページ形 / 配列 / 単純オブジェクトのいずれかをデフォルトとして返す
 */
export async function proxyToGateway(opts: ProxyOptions): Promise<NextResponse> {
  const { path, method = 'GET', body, search, requireAuth = true } = opts;
  let authHeader: Record<string, string> = {};
  if (requireAuth) {
    const session = await getServerSession(authOptions);
    if (!session?.user?.id || !session.accessToken) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    authHeader = { Authorization: `Bearer ${session.accessToken}` };
  }

  const qs = search ? (search.startsWith('?') ? search : `?${search}`) : '';
  const url = `${API_GATEWAY_URL}${path}${qs}`;

  const init: FetchInit = {
    method,
    headers: {
      ...authHeader,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  };

  let res: Response;
  try {
    res = await rawFetch(url, init);
  } catch (e) {
    console.error(`[bff] gateway fetch failed: ${method} ${url}`, e);
    return NextResponse.json(
      { error: 'Bad Gateway', detail: e instanceof Error ? e.message : String(e) },
      { status: 502 },
    );
  }

  // 204 や 1xx は本文なし
  if (res.status === 204 || (res.status >= 100 && res.status < 200)) {
    return new NextResponse(null, { status: res.status });
  }

  const text = await res.text();
  if (!text) {
    // 上流が本文を返さない場合は空オブジェクトで返す（null は返さない）
    return NextResponse.json({}, { status: res.status });
  }

  try {
    const data = JSON.parse(text);
    // null をそのまま返すとフロントの .content 参照で落ちるため空オブジェクトに正規化
    return NextResponse.json(data ?? {}, { status: res.status });
  } catch {
    // JSON でない場合は text として包んで返す
    return NextResponse.json({ error: 'Invalid JSON from upstream', body: text }, { status: res.status });
  }
}

/** クエリ文字列を NextRequest から抜き出す簡易ヘルパ */
export function searchOf(request: { url: string }): string {
  return new URL(request.url).search.replace(/^\?/, '');
}
