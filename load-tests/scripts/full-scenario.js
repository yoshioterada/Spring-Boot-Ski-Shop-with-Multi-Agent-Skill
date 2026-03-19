import { group } from 'k6';
import authFlow from './auth-flow.js';
import browseProducts from './browse-products.js';
import cartOrderFlow from './cart-order-flow.js';

export const options = {
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000', 'avg<200'],
        'http_req_failed': ['rate<0.01'],
        'group_duration{group:::01_register}': ['avg<2000'],
        'group_duration{group:::02_login}': ['avg<1000'],
        'group_duration{group:::01_list_products}': ['avg<500'],
        'group_duration{group:::05_create_order}': ['avg<2000'],
    },
};

export default function () {
    const scenario = Math.random();

    if (scenario < 0.3) {
        // 30%: Authentication flow
        group('auth_flow', () => {
            authFlow();
        });
    } else if (scenario < 0.7) {
        // 40%: Browse products (most common)
        group('browse_flow', () => {
            browseProducts();
        });
    } else {
        // 30%: Cart + Order flow
        group('cart_order_flow', () => {
            cartOrderFlow();
        });
    }
}
