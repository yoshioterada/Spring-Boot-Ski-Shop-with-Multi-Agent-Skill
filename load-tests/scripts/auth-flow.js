import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8090';

export default function () {
    const uniqueId = randomString(8);
    const email = `loadtest-${uniqueId}@example.com`;
    const password = `Test@${uniqueId}123`;

    let authToken = null;
    let refreshTokenValue = null;

    group('01_register', () => {
        const payload = JSON.stringify({
            email: email,
            password: password,
            firstName: 'Load',
            lastName: 'Test',
            role: 'CUSTOMER',
        });

        const res = http.post(`${BASE_URL}/api/v1/auth/register`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });

        check(res, {
            'register: status 201': (r) => r.status === 201,
            'register: has accessToken': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    authToken = body.accessToken;
                    refreshTokenValue = body.refreshToken;
                    return !!authToken;
                } catch {
                    return false;
                }
            },
        });

        sleep(1);
    });

    group('02_login', () => {
        const payload = JSON.stringify({
            email: email,
            password: password,
        });

        const res = http.post(`${BASE_URL}/api/v1/auth/login`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });

        check(res, {
            'login: status 200': (r) => r.status === 200,
            'login: has accessToken': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    authToken = body.accessToken;
                    refreshTokenValue = body.refreshToken;
                    return !!authToken;
                } catch {
                    return false;
                }
            },
        });

        sleep(1);
    });

    group('03_validate_token', () => {
        if (!authToken) return;

        const res = http.post(`${BASE_URL}/api/v1/auth/validate`, null, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${authToken}`,
            },
        });

        check(res, {
            'validate: status 200': (r) => r.status === 200,
        });

        sleep(0.5);
    });

    group('04_get_current_user', () => {
        if (!authToken) return;

        const res = http.get(`${BASE_URL}/api/v1/auth/me`, {
            headers: { 'Authorization': `Bearer ${authToken}` },
        });

        check(res, {
            'me: status 200': (r) => r.status === 200,
        });

        sleep(0.5);
    });

    group('05_refresh_token', () => {
        if (!refreshTokenValue) return;

        const payload = JSON.stringify({
            refreshToken: refreshTokenValue,
        });

        const res = http.post(`${BASE_URL}/api/v1/auth/refresh`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });

        check(res, {
            'refresh: status 200': (r) => r.status === 200,
        });

        sleep(0.5);
    });

    sleep(1);
}
