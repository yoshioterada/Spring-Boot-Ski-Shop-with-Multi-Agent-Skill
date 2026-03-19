import { toast } from 'sonner';

interface RequestOptions extends RequestInit {
  timeout?: number;
  params?: Record<string, string | number | boolean | undefined>;
}

interface HttpResponse<T> {
  data: T;
  status: number;
  headers: Headers;
  correlationId?: string;
  responseTime?: string;
}

const DEFAULT_TIMEOUT = 10_000;
const MAX_RATE_LIMIT_RETRIES = 3;

function isPaymentApi(url: string): boolean {
  return url.includes('/payments/');
}

async function fetchWithRateLimitRetry(
  url: string,
  init: RequestInit,
  maxRetries: number,
): Promise<Response> {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const response = await fetch(url, init);

    if (response.status === 429 && attempt < maxRetries) {
      if (isPaymentApi(url)) {
        return response;
      }

      const retryAfter = response.headers.get('Retry-After');
      const waitMs = retryAfter ? parseInt(retryAfter, 10) * 1000 : Math.pow(2, attempt) * 1000;

      if (attempt === 0) {
        toast.warning('リクエストが集中しています。しばらくお待ちください');
      }

      await new Promise((resolve) => setTimeout(resolve, waitMs));
      continue;
    }

    return response;
  }

  throw new Error('Max retries exceeded');
}

export async function httpFetch<T>(
  url: string,
  options: RequestOptions = {},
): Promise<HttpResponse<T>> {
  const { timeout = DEFAULT_TIMEOUT, params, ...fetchOptions } = options;

  const requestId = crypto.randomUUID();

  // Build URL with query params
  const urlObj = new URL(url, url.startsWith('http') ? undefined : 'http://localhost');
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
        urlObj.searchParams.set(key, String(value));
      }
    });
  }

  // Setup timeout via AbortController
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  const headers = new Headers(fetchOptions.headers);
  headers.set('X-Request-Id', requestId);
  if (!headers.has('Content-Type') && fetchOptions.body) {
    headers.set('Content-Type', 'application/json');
  }

  const fullUrl = urlObj.toString();
  const retries = isPaymentApi(fullUrl) ? 0 : MAX_RATE_LIMIT_RETRIES;

  try {
    const response = await fetchWithRateLimitRetry(
      fullUrl,
      {
        ...fetchOptions,
        headers,
        signal: controller.signal,
      },
      retries,
    );

    const correlationId = response.headers.get('x-correlation-id') ?? undefined;
    const responseTime = response.headers.get('x-response-time') ?? undefined;

    // Dev logging
    if (process.env.NODE_ENV === 'development') {
      console.log(
        `[HTTP] ${fetchOptions.method ?? 'GET'} ${urlObj.pathname} → ${response.status} (${responseTime ?? '?'}ms) [${correlationId ?? requestId}]`,
      );
    }

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({
        type: 'about:blank',
        title: 'Request Failed',
        status: response.status,
        detail: `HTTP ${response.status}`,
        instance: urlObj.pathname,
        timestamp: new Date().toISOString(),
      }));

      const error = new HttpError(response.status, errorBody, correlationId);
      throw error;
    }

    if (response.status === 204) {
      return {
        data: undefined as T,
        status: response.status,
        headers: response.headers,
        correlationId,
        responseTime,
      };
    }

    const data = await response.json();
    return {
      data,
      status: response.status,
      headers: response.headers,
      correlationId,
      responseTime,
    };
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new HttpError(408, {
        type: 'about:blank',
        title: 'Request Timeout',
        status: 408,
        detail: `リクエストがタイムアウトしました（${timeout}ms）`,
        instance: urlObj.pathname,
        timestamp: new Date().toISOString(),
      });
    }
    throw new HttpError(0, {
      type: 'about:blank',
      title: 'Network Error',
      status: 0,
      detail: 'ネットワークエラーが発生しました',
      instance: urlObj.pathname,
      timestamp: new Date().toISOString(),
    });
  } finally {
    clearTimeout(timeoutId);
  }
}

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly problemDetail: import('@/types/api').ProblemDetail,
    public readonly correlationId?: string,
  ) {
    super(problemDetail.detail);
    this.name = 'HttpError';
  }

  get isValidationError(): boolean {
    return this.status === 400 && Array.isArray(this.problemDetail.errors);
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isRateLimited(): boolean {
    return this.status === 429;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }
}
