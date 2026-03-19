export interface CreateOrderRequest {
  customerId: string;
  items: OrderItemRequest[];
  shippingAddress?: string;
  notes?: string;
}

export interface OrderItemRequest {
  productId: string;
  productName: string;
  productSku?: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderResponse {
  id: string;
  orderNumber: string;
  customerId: string;
  status: 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED';
  paymentStatus: string;
  subtotalAmount: number;
  taxAmount: number;
  shippingAmount: number;
  discountAmount: number;
  totalAmount: number;
  currency: string;
  paymentMethod: string;
  shippingAddress: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderItemResponse {
  id: string;
  productId: string;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export type OrderStatus = OrderResponse['status'];
