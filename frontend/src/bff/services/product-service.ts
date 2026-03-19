import { apiClient } from '@/lib/api-client';

import type {
  CategoryResponse,
  CreateCategoryRequest,
  CreateProductRequest,
  PaginatedResponse,
  PaginationParams,
  ProductResponse,
} from '@/types/api';

export const productService = {
  async list(params?: PaginationParams & { categoryId?: string; status?: string }) {
    const res = await apiClient.get<PaginatedResponse<ProductResponse>>('/api/v1/products', {
      params: params as Record<string, string | number | boolean | undefined>,
    });
    return res.data;
  },

  async getById(id: string) {
    const res = await apiClient.get<ProductResponse>(`/api/v1/products/${id}`);
    return res.data;
  },

  async getByCategory(categoryId: string, params?: PaginationParams) {
    const res = await apiClient.get<PaginatedResponse<ProductResponse>>(
      `/api/v1/products/category/${categoryId}`,
      { params: params as Record<string, string | number | boolean | undefined> },
    );
    return res.data;
  },

  async create(data: CreateProductRequest) {
    const res = await apiClient.post<ProductResponse>('/api/v1/products', data);
    return res.data;
  },

  async update(id: string, data: Partial<CreateProductRequest>) {
    const res = await apiClient.put<ProductResponse>(`/api/v1/products/${id}`, data);
    return res.data;
  },

  async delete(id: string) {
    await apiClient.delete(`/api/v1/products/${id}`);
  },

  async listCategories() {
    const res = await apiClient.get<CategoryResponse[]>('/api/v1/categories');
    return res.data;
  },

  async createCategory(data: CreateCategoryRequest) {
    const res = await apiClient.post<CategoryResponse>('/api/v1/categories', data);
    return res.data;
  },
};
