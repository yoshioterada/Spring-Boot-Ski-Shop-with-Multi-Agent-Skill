import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8090';

export default function () {
    group('01_list_products', () => {
        const res = http.get(`${BASE_URL}/api/v1/products?page=0&size=20`);

        check(res, {
            'products list: status 200': (r) => r.status === 200,
            'products list: has content': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.content !== undefined;
                } catch {
                    return false;
                }
            },
        });

        sleep(1);
    });

    group('02_search_products', () => {
        const queries = ['ski', 'boot', 'jacket', 'helmet', 'goggle'];
        const query = queries[Math.floor(Math.random() * queries.length)];

        const res = http.get(`${BASE_URL}/api/v1/products/search?q=${encodeURIComponent(query)}&page=0&size=20`);

        check(res, {
            'search: status 200': (r) => r.status === 200,
        });

        sleep(1);
    });

    group('03_list_categories', () => {
        const res = http.get(`${BASE_URL}/api/v1/categories?page=0&size=20`);

        check(res, {
            'categories: status 200': (r) => r.status === 200,
        });

        sleep(0.5);
    });

    group('04_product_detail', () => {
        // First, get product list to find a valid product ID
        const listRes = http.get(`${BASE_URL}/api/v1/products?page=0&size=5`);

        if (listRes.status === 200) {
            try {
                const body = JSON.parse(listRes.body);
                if (body.content && body.content.length > 0) {
                    const productId = body.content[0].id;

                    const detailRes = http.get(`${BASE_URL}/api/v1/products/${productId}`);

                    check(detailRes, {
                        'product detail: status 200': (r) => r.status === 200,
                    });
                }
            } catch {
                // Product list parse failed — skip detail
            }
        }

        sleep(1);
    });

    group('05_paginate_products', () => {
        // Simulate user browsing multiple pages
        for (let page = 0; page < 3; page++) {
            const res = http.get(`${BASE_URL}/api/v1/products?page=${page}&size=10`);

            check(res, {
                [`page ${page}: status 200`]: (r) => r.status === 200,
            });

            sleep(0.5);
        }
    });

    sleep(1);
}
