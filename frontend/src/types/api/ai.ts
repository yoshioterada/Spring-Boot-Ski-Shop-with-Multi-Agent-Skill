export interface ChatMessageRequest {
  userId?: string;
  sessionId?: string;
  message: string;
}

export interface ChatMessageResponse {
  sessionId: string;
  messageId: string;
  content: string;
  role: 'assistant' | 'user';
  timestamp?: string;
}

export interface RecommendationResponse {
  id: string;
  userId?: string | null;
  type: 'TRENDING' | 'PERSONALIZED' | 'SIMILAR' | 'CROSS_SELL';
  products: ProductRecommendation[];
  confidenceScore: number;
  algorithm: string;
  createdAt: string;
}

export interface ProductRecommendation {
  productId: string;
  score: number;
  reason: string;
  features?: string[];
  attributes?: Record<string, unknown>;
  rank: number;
}

export interface SearchResponse {
  results: SearchResult[];
  totalHits: number;
  query: string;
  suggestions?: string[];
}

export interface SearchResult {
  productId: string;
  name: string;
  description: string;
  brand: string;
  price: number;
  score: number;
  highlights?: Record<string, string[]>;
}
