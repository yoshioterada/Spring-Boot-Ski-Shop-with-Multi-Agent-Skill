export interface CartResponse {
  id: string;
  userId: string;
  totalAmount: number;
  currency: string;
  items: CartItemResponse[];
  updatedAt: string;
}

export interface CartItemResponse {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface CartItemRequest {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface CreatePaymentIntentRequest {
  userId: string;
  amount: number;
  paymentMethod: string;
  orderId?: string;
}

export interface PaymentResponse {
  id: string;
  userId: string;
  orderId: string;
  paymentIntentId: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  amount: number;
  currency: string;
  paymentMethod: string;
  gatewayProvider: string;
  refundedAmount: number;
  completedAt?: string;
  createdAt: string;
}

export interface ProcessPaymentRequest {
  paymentIntentId: string;
}

export interface ValidateCouponRequest {
  code: string;
  orderId?: string;
}

export interface ValidateCouponResponse {
  valid: boolean;
  code: string;
  discountAmount: number;
  message?: string;
}
