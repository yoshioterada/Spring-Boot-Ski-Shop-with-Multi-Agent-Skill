import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8090';

function authHeaders(token) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
    };
}

export default function () {
    const uniqueId = randomString(8);
    const email = `cart-test-${uniqueId}@example.com`;
    const password = `Cart@${uniqueId}123`;

    let authToken = null;
    let userId = null;

    // Step 1: Register and login to get auth token
    group('01_setup_user', () => {
        const regPayload = JSON.stringify({
            email: email,
            password: password,
            firstName: 'Cart',
            lastName: 'Test',
            role: 'CUSTOMER',
        });

        const regRes = http.post(`${BASE_URL}/api/v1/auth/register`, regPayload, {
            headers: { 'Content-Type': 'application/json' },
        });

        if (regRes.status === 201) {
            try {
                const body = JSON.parse(regRes.body);
                authToken = body.accessToken;
                userId = body.userId;
            } catch {
                // parse failed
            }
        }

        check(regRes, {
            'setup: registered': (r) => r.status === 201,
        });

        sleep(0.5);
    });

    if (!authToken || !userId) return;

    // Step 2: Browse products and pick one
    let productId = null;
    let productPrice = 0;

    group('02_browse_and_select', () => {
        const res = http.get(`${BASE_URL}/api/v1/products?page=0&size=5`);

        check(res, {
            'browse: status 200': (r) => r.status === 200,
        });

        if (res.status === 200) {
            try {
                const body = JSON.parse(res.body);
                if (body.content && body.content.length > 0) {
                    const product = body.content[0];
                    productId = product.id;
                    productPrice = product.price || 1000;
                }
            } catch {
                // parse failed
            }
        }

        sleep(1);
    });

    if (!productId) return;

    // Step 3: Add item to cart
    group('03_add_to_cart', () => {
        const payload = JSON.stringify({
            productId: productId,
            quantity: 2,
            unitPrice: productPrice,
        });

        const res = http.post(
            `${BASE_URL}/api/v1/cart/items?userId=${userId}`,
            payload,
            authHeaders(authToken)
        );

        check(res, {
            'add to cart: status 200': (r) => r.status === 200,
        });

        sleep(0.5);
    });

    // Step 4: View cart
    group('04_view_cart', () => {
        const res = http.get(
            `${BASE_URL}/api/v1/cart?userId=${userId}`,
            authHeaders(authToken)
        );

        check(res, {
            'view cart: status 200': (r) => r.status === 200,
            'view cart: has items': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.items && body.items.length > 0;
                } catch {
                    return false;
                }
            },
        });

        sleep(0.5);
    });

    // Step 5: Create order
    group('05_create_order', () => {
        const payload = JSON.stringify({
            customerId: userId,
            items: [
                {
                    productId: productId,
                    productName: 'Load Test Product',
                    quantity: 2,
                    unitPrice: productPrice,
                },
            ],
            shippingAddress: '東京都渋谷区テスト町1-1-1',
        });

        const res = http.post(
            `${BASE_URL}/api/v1/orders`,
            payload,
            authHeaders(authToken)
        );

        check(res, {
            'create order: status 201': (r) => r.status === 201,
        });

        if (res.status === 201) {
            try {
                const body = JSON.parse(res.body);
                const orderId = body.id;

                // Step 6: View order
                group('06_view_order', () => {
                    const orderRes = http.get(
                        `${BASE_URL}/api/v1/orders/${orderId}`,
                        authHeaders(authToken)
                    );

                    check(orderRes, {
                        'view order: status 200': (r) => r.status === 200,
                    });
                });
            } catch {
                // parse failed
            }
        }

        sleep(0.5);
    });

    // Step 7: Clear cart
    group('07_clear_cart', () => {
        const res = http.del(
            `${BASE_URL}/api/v1/cart?userId=${userId}`,
            null,
            authHeaders(authToken)
        );

        check(res, {
            'clear cart: status 204': (r) => r.status === 204,
        });

        sleep(0.5);
    });

    sleep(1);
}
