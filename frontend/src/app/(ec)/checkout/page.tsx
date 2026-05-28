'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, ShoppingBag } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { useAuth } from '@/hooks/use-auth';
import { formatCurrency } from '@/lib/format';
import { generateUUID } from '@/lib/uuid';

import type { CartResponse, OrderResponse, PaymentResponse } from '@/types/api';

const SHIPPING_FREE_THRESHOLD = 10000;
const SHIPPING_FEE = 500;
const TAX_RATE = 0.1;
const ORDER_POLL_INTERVAL = 3000;
const ORDER_POLL_MAX = 10;
const PROCESS_TIMEOUT = 30000;

const shippingSchema = z.object({
  name: z.string().min(1, 'お名前を入力してください').max(50, '50文字以内で入力してください'),
  postalCode: z.string().regex(/^\d{3}-?\d{4}$/, '正しい郵便番号を入力してください'),
  prefecture: z.string().min(1, '都道府県を選択してください'),
  city: z.string().min(1, '市区町村を入力してください').max(100, '100文字以内で入力してください'),
  address: z.string().min(1, '番地を入力してください').max(200, '200文字以内で入力してください'),
  phone: z.string().regex(/^0\d{9,10}$/, '正しい電話番号を入力してください'),
});

type ShippingFormData = z.infer<typeof shippingSchema>;

const PREFECTURES = [
  '北海道',
  '青森県',
  '岩手県',
  '宮城県',
  '秋田県',
  '山形県',
  '福島県',
  '茨城県',
  '栃木県',
  '群馬県',
  '埼玉県',
  '千葉県',
  '東京都',
  '神奈川県',
  '新潟県',
  '富山県',
  '石川県',
  '福井県',
  '山梨県',
  '長野県',
  '岐阜県',
  '静岡県',
  '愛知県',
  '三重県',
  '滋賀県',
  '京都府',
  '大阪府',
  '兵庫県',
  '奈良県',
  '和歌山県',
  '鳥取県',
  '島根県',
  '岡山県',
  '広島県',
  '山口県',
  '徳島県',
  '香川県',
  '愛媛県',
  '高知県',
  '福岡県',
  '佐賀県',
  '長崎県',
  '熊本県',
  '大分県',
  '宮崎県',
  '鹿児島県',
  '沖縄県',
];

const PAYMENT_METHODS = [
  { value: 'CREDIT_CARD', label: 'クレジットカード' },
  { value: 'CONVENIENCE_STORE', label: 'コンビニ決済' },
  { value: 'BANK_TRANSFER', label: '銀行振込' },
] as const;

export default function CheckoutPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const [cart, setCart] = useState<CartResponse | null>(null);
  const [cartLoading, setCartLoading] = useState(true);
  const [paymentMethod, setPaymentMethod] = useState('CREDIT_CARD');
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState('');
  const [timeoutMessage, setTimeoutMessage] = useState('');
  const [showOrderHistoryLink, setShowOrderHistoryLink] = useState(false);
  const processingRef = useRef(false);

  const {
    register,
    handleSubmit,
    formState: { errors: formErrors },
  } = useForm<ShippingFormData>({
    resolver: zodResolver(shippingSchema),
    mode: 'onBlur',
  });

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login?redirect=/checkout');
    }
  }, [authLoading, isAuthenticated, router]);

  useEffect(() => {
    const fetchCart = async () => {
      try {
        const res = await fetch('/api/cart');
        if (!res.ok) throw new Error('カートの取得に失敗しました');
        const data = await res.json();
        setCart(data.cart as CartResponse);
      } catch {
        setError('カート情報の読み込みに失敗しました');
      } finally {
        setCartLoading(false);
      }
    };
    if (isAuthenticated) fetchCart();
  }, [isAuthenticated]);

  useEffect(() => {
    if (!processing) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [processing]);

  const subtotal = cart?.totalAmount ?? 0;
  const tax = Math.floor(subtotal * TAX_RATE);
  const shipping = subtotal >= SHIPPING_FREE_THRESHOLD ? 0 : SHIPPING_FEE;
  const total = subtotal + tax + shipping;

  const pollOrders = useCallback(async (): Promise<OrderResponse | null> => {
    for (let i = 0; i < ORDER_POLL_MAX; i++) {
      await new Promise((r) => setTimeout(r, ORDER_POLL_INTERVAL));
      try {
        const res = await fetch('/api/orders?size=1&sort=createdAt,desc');
        if (res.ok) {
          const data = await res.json();
          const orders = data.content ?? data;
          if (Array.isArray(orders) && orders.length > 0) return orders[0];
        }
      } catch {
        /* continue polling */
      }
    }
    return null;
  }, []);

  const onSubmit = async (shipping: ShippingFormData) => {
    if (processingRef.current || !cart?.items?.length) return;
    processingRef.current = true;
    setProcessing(true);
    setError('');
    setTimeoutMessage('');

    const idempotencyKey = generateUUID();
    const shippingAddress = `〒${shipping.postalCode} ${shipping.prefecture}${shipping.city}${shipping.address}`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), PROCESS_TIMEOUT);

    try {
      // Step 1: Create a pending order so payment events can reconcile against it.
      const orderRes = await fetch('/api/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          items: cart.items.map((item) => ({
            productId: item.productId,
            productName: item.productName,
            quantity: item.quantity,
            unitPrice: item.unitPrice,
          })),
          shippingAddress,
          paymentMethod,
          notes: `TEL: ${shipping.phone}`,
        }),
        signal: controller.signal,
      });
      if (!orderRes.ok) throw new Error('注文の作成に失敗しました');
      const order: OrderResponse = await orderRes.json();

      // Step 2: Create payment intent linked to the pending order
      const intentRes = await fetch('/api/payments/intent', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          amount: total,
          paymentMethod,
          orderId: order.id,
        }),
        signal: controller.signal,
      });
      if (!intentRes.ok) throw new Error('決済の準備に失敗しました');
      const intent: PaymentResponse = await intentRes.json();

      // Step 3: Process payment. Webhook/Outbox will also reconcile this order asynchronously.
      const processRes = await fetch(`/api/payments/${intent.id}/process`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({ paymentMethodId: `pm_${paymentMethod.toLowerCase()}` }),
        signal: controller.signal,
      });
      if (!processRes.ok) throw new Error('決済処理に失敗しました');
      const payment: PaymentResponse = await processRes.json();
      if (payment.status !== 'CAPTURED') throw new Error('決済が完了していません');

      clearTimeout(timeout);
      router.push(`/checkout/complete?orderId=${order.id}`);
    } catch (err) {
      clearTimeout(timeout);
      if (err instanceof DOMException && err.name === 'AbortError') {
        setTimeoutMessage('処理中です。しばらくお待ちください...');
        const polled = await pollOrders();
        if (polled) {
          router.push(`/checkout/complete?orderId=${polled.id}`);
          return;
        }
        setTimeoutMessage('');
        setError('注文が完了した可能性があります。注文履歴をご確認ください。');
        setShowOrderHistoryLink(true);
      } else {
        setError(err instanceof Error ? err.message : '注文処理中にエラーが発生しました');
      }
    } finally {
      setProcessing(false);
      processingRef.current = false;
    }
  };

  if (authLoading || cartLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="text-muted-foreground size-8 animate-spin" />
      </div>
    );
  }

  if (!cart?.items?.length && !error) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center">
        <ShoppingBag className="text-muted-foreground mx-auto size-16" />
        <h2 className="mt-4 text-xl font-semibold">カートが空です</h2>
        <p className="text-muted-foreground mt-2">商品を追加してからチェックアウトしてください</p>
        <Link href="/catalog" className={buttonVariants({ variant: 'default', className: 'mt-6' })}>
          商品を探す
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-8 text-2xl font-bold">ご注文手続き</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="grid gap-8 lg:grid-cols-5">
        {/* Left: Forms */}
        <div className="space-y-6 lg:col-span-3">
          {/* Shipping Address */}
          <Card>
            <CardHeader>
              <CardTitle>配送先情報</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <Label htmlFor="name">お名前</Label>
                <Input
                  id="name"
                  placeholder="山田 太郎"
                  {...register('name')}
                  aria-invalid={!!formErrors.name}
                />
                {formErrors.name && (
                  <p className="text-destructive mt-1 text-sm">{formErrors.name.message}</p>
                )}
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <Label htmlFor="postalCode">郵便番号</Label>
                  <Input
                    id="postalCode"
                    placeholder="100-0001"
                    {...register('postalCode')}
                    aria-invalid={!!formErrors.postalCode}
                  />
                  {formErrors.postalCode && (
                    <p className="text-destructive mt-1 text-sm">{formErrors.postalCode.message}</p>
                  )}
                </div>
                <div>
                  <Label htmlFor="prefecture">都道府県</Label>
                  <select
                    id="prefecture"
                    className="border-input bg-background ring-ring/30 flex h-8 w-full rounded-lg border px-3 text-sm transition outline-none focus:ring-2"
                    defaultValue=""
                    {...register('prefecture')}
                    aria-invalid={!!formErrors.prefecture}
                  >
                    <option value="" disabled>
                      選択してください
                    </option>
                    {PREFECTURES.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                  {formErrors.prefecture && (
                    <p className="text-destructive mt-1 text-sm">{formErrors.prefecture.message}</p>
                  )}
                </div>
              </div>
              <div>
                <Label htmlFor="city">市区町村</Label>
                <Input
                  id="city"
                  placeholder="千代田区千代田"
                  {...register('city')}
                  aria-invalid={!!formErrors.city}
                />
                {formErrors.city && (
                  <p className="text-destructive mt-1 text-sm">{formErrors.city.message}</p>
                )}
              </div>
              <div>
                <Label htmlFor="address">番地・建物名</Label>
                <Input
                  id="address"
                  placeholder="1-1 スキーマンション 101"
                  {...register('address')}
                  aria-invalid={!!formErrors.address}
                />
                {formErrors.address && (
                  <p className="text-destructive mt-1 text-sm">{formErrors.address.message}</p>
                )}
              </div>
              <div>
                <Label htmlFor="phone">電話番号</Label>
                <Input
                  id="phone"
                  placeholder="09012345678"
                  {...register('phone')}
                  aria-invalid={!!formErrors.phone}
                />
                {formErrors.phone && (
                  <p className="text-destructive mt-1 text-sm">{formErrors.phone.message}</p>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Payment Method */}
          <Card>
            <CardHeader>
              <CardTitle>お支払い方法</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {PAYMENT_METHODS.map((method) => (
                <label
                  key={method.value}
                  className="border-input hover:bg-muted has-[:checked]:border-primary flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition"
                >
                  <input
                    type="radio"
                    name="paymentMethod"
                    value={method.value}
                    checked={paymentMethod === method.value}
                    onChange={(e) => setPaymentMethod(e.target.value)}
                    className="accent-primary"
                  />
                  <span className="text-sm font-medium">{method.label}</span>
                </label>
              ))}
            </CardContent>
          </Card>
        </div>

        {/* Right: Order Summary */}
        <div className="lg:col-span-2">
          <Card className="sticky top-24">
            <CardHeader>
              <CardTitle>注文内容</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {cart?.items?.map((item) => (
                <div key={item.id} className="flex justify-between text-sm">
                  <span className="line-clamp-1 flex-1">
                    {item.productName}
                    <span className="text-muted-foreground"> × {item.quantity}</span>
                  </span>
                  <span className="ml-4 shrink-0">{formatCurrency(item.totalPrice)}</span>
                </div>
              ))}

              <Separator />

              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span>小計</span>
                  <span>{formatCurrency(subtotal)}</span>
                </div>
                <div className="flex justify-between">
                  <span>消費税（10%）</span>
                  <span>{formatCurrency(tax)}</span>
                </div>
                <div className="flex justify-between">
                  <span>送料</span>
                  <span>{shipping === 0 ? '無料' : formatCurrency(shipping)}</span>
                </div>
              </div>

              <Separator />

              <div className="flex justify-between text-base font-bold">
                <span>合計</span>
                <span>{formatCurrency(total)}</span>
              </div>

              {shipping > 0 && (
                <p className="text-muted-foreground text-xs">
                  あと{formatCurrency(SHIPPING_FREE_THRESHOLD - subtotal)}
                  で送料無料
                </p>
              )}
            </CardContent>
            <CardFooter className="flex-col gap-3">
              {error && (
                <div className="border-destructive/50 bg-destructive/10 text-destructive w-full rounded-lg border p-3 text-sm">
                  <p>{error}</p>
                  {showOrderHistoryLink && (
                    <Link href="/mypage/orders" className="mt-1 inline-block underline">
                      注文履歴を確認する
                    </Link>
                  )}
                </div>
              )}

              {timeoutMessage && (
                <div className="flex w-full items-center gap-2 rounded-lg border border-yellow-500/50 bg-yellow-50 p-3 text-sm text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400">
                  <Loader2 className="size-4 animate-spin" />
                  {timeoutMessage}
                </div>
              )}

              <Button type="submit" size="lg" disabled={processing} className="w-full">
                {processing && <Loader2 className="size-4 animate-spin" />}
                {processing ? '処理中...' : '注文を確定する'}
              </Button>
            </CardFooter>
          </Card>
        </div>
      </form>
    </div>
  );
}
