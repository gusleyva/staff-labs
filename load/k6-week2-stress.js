import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const options = {
    stages: [
        { duration: '30s', target: 5 }, // Ramp up to 5 users
        { duration: '1m', target: 10 }, // Stay at 10 users
        { duration: '30s', target: 0 }, // Ramp down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<3000'], // 95% of requests should be below 3s
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // Configure mock to have high failure rate to trigger circuit breaker
    const payload = JSON.stringify({
        failureRate: 0.6, // 60% failure rate
        delayMs: 100
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    http.post(`${BASE_URL}/admin/mock/configure`, payload, params);
    console.log('Mock configured with high failure rate');
}

export default function () {
    const res = http.get(`${BASE_URL}/api/external`);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'is fallback': (r) => r.body && r.body.includes('Graceful Degradation'),
    });

    sleep(1);
}

export function teardown() {
    // Reset mock configuration
    const payload = JSON.stringify({
        failureRate: 0.1,
        delayMs: 50
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    http.post(`${BASE_URL}/admin/mock/configure`, payload, params);
    console.log('Mock reset to default configuration');
}
