export interface ProductResponse {
  id: string;
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: string;
  regularPrice: number;
  salePrice?: number | null;
  currency: string;
  stockQuantity: number;
  availableQuantity: number;
  status: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
  attributes?: Record<string, string>;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CategoryResponse {
  id: string;
  name: string;
  description: string;
  parentId?: string | null;
  childIds?: string[];
  imageUrl?: string;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  sku: string;
  name: string;
  description: string;
  brand: string;
  categoryId: string;
  regularPrice: number;
  salePrice?: number;
  currency?: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
  attributes?: Record<string, string>;
  tags?: string[];
}

export interface CreateCategoryRequest {
  name: string;
  description: string;
  parentId?: string;
  imageUrl?: string;
  sortOrder?: number;
  active?: boolean;
}
