import { httpFetch, HttpError } from './http';

const API_BASE_URL = process.env.API_GATEWAY_URL || 'http://localhost:8090';

interface ApiRequestOptions {
  params?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  timeout?: number;
}

class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private buildUrl(path: string): string {
    return `${this.baseUrl}${path}`;
  }

  async get<T>(path: string, options: ApiRequestOptions = {}) {
    return httpFetch<T>(this.buildUrl(path), {
      method: 'GET',
      ...options,
    });
  }

  async post<T>(path: string, data?: unknown, options: ApiRequestOptions = {}) {
    return httpFetch<T>(this.buildUrl(path), {
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
      ...options,
    });
  }

  async put<T>(path: string, data?: unknown, options: ApiRequestOptions = {}) {
    return httpFetch<T>(this.buildUrl(path), {
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
      ...options,
    });
  }

  async patch<T>(path: string, data?: unknown, options: ApiRequestOptions = {}) {
    return httpFetch<T>(this.buildUrl(path), {
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
      ...options,
    });
  }

  async delete<T>(path: string, options: ApiRequestOptions = {}) {
    return httpFetch<T>(this.buildUrl(path), {
      method: 'DELETE',
      ...options,
    });
  }
}

export const apiClient = new ApiClient(API_BASE_URL);
export { HttpError };
