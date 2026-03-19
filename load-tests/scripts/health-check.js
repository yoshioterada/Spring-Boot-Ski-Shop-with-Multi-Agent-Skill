import http from 'k6/http';
import { check, sleep } from 'k6';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8090';

const SERVICES = [
    { name: 'gateway',       url: `${GATEWAY_URL}/actuator/health` },
    { name: 'authentication', url: 'http://localhost:8080/actuator/health' },
    { name: 'user-mgmt',     url: 'http://localhost:8081/actuator/health' },
    { name: 'inventory',     url: 'http://localhost:8082/actuator/health' },
    { name: 'sales',         url: 'http://localhost:8083/actuator/health' },
    { name: 'payment-cart',  url: 'http://localhost:8084/actuator/health' },
    { name: 'point',         url: 'http://localhost:8085/actuator/health' },
    { name: 'ai-support',    url: 'http://localhost:8087/actuator/health' },
    { name: 'coupon',        url: 'http://localhost:8088/actuator/health' },
];

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        'http_req_failed': ['rate<0.01'],
        'http_req_duration': ['p(95)<2000'],
    },
};

export default function () {
    for (const svc of SERVICES) {
        const res = http.get(svc.url, { tags: { service: svc.name } });
        check(res, {
            [`${svc.name} is UP`]: (r) => r.status === 200,
            [`${svc.name} status is UP`]: (r) => {
                try {
                    return JSON.parse(r.body).status === 'UP';
                } catch {
                    return false;
                }
            },
        });
        sleep(0.5);
    }
}
