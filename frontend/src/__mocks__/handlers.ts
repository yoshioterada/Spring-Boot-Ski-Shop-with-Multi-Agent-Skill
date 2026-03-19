import { http, HttpResponse } from 'msw';

const API_BASE = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export const handlers = [
  // Health check
  http.get(`${API_BASE}/actuator/health`, () => {
    return HttpResponse.json({ status: 'UP' });
  }),

  // Products
  http.get(`${API_BASE}/api/v1/products`, () => {
    return HttpResponse.json({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
  }),
];
