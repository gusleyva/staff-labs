import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const dbLatency = new Trend('db_latency');
const cpuLatency = new Trend('cpu_latency');
const externalLatency = new Trend('external_latency');
const dbErrors = new Counter('db_errors');
const externalErrors = new Counter('external_errors');

export const options = {
    scenarios: {
        // Scenario 1: CPU-heavy load
        cpu_saturation: {
            executor: 'ramping-vus',
            exec: 'cpuHeavy',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
            tags: { scenario: 'cpu' },
        },

        // Scenario 2: Database-heavy load
        db_saturation: {
            executor: 'ramping-vus',
            exec: 'dbHeavy',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 5 },
                { duration: '1m', target: 5 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
            tags: { scenario: 'database' },
            startTime: '2m30s', // Start after CPU test
        },

        // Scenario 3: Mixed load (realistic)
        mixed_load: {
            executor: 'ramping-arrival-rate',
            exec: 'mixedLoad',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            stages: [
                { duration: '1m', target: 50 },  // Ramp up to 50 RPS
                { duration: '2m', target: 100 }, // Push to 100 RPS
                { duration: '1m', target: 100 }, // Hold at 100 RPS
                { duration: '30s', target: 0 },   // Ramp down
            ],
            tags: { scenario: 'mixed' },
            startTime: '5m', // Start after DB test
        },
    },

    thresholds: {
        'http_req_duration': ['p(95)<5000', 'p(99)<10000'], // 95th percentile < 5s, 99th < 10s
        'http_req_failed': ['rate<0.15'], // Error rate < 15%
        'errors': ['rate<0.15'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// CPU-heavy scenario
export function cpuHeavy() {
    const cpuDurations = [50, 100, 200, 500];
    const duration = cpuDurations[Math.floor(Math.random() * cpuDurations.length)];

    const res = http.get(`${BASE_URL}/api/cpu?ms=${duration}`, {
        tags: { endpoint: 'cpu' },
    });

    const success = check(res, {
        'CPU: status is 200': (r) => r.status === 200,
        'CPU: response time OK': (r) => r.timings.duration < 10000,
    });

    cpuLatency.add(res.timings.duration);
    errorRate.add(!success);

    sleep(Math.random() * 2 + 1); // 1-3 seconds think time
}

// Database-heavy scenario
export function dbHeavy() {
    const res = http.get(`${BASE_URL}/api/db/search`, {
        tags: { endpoint: 'db' },
    });

    const success = check(res, {
        'DB: status is 200': (r) => r.status === 200,
        'DB: response time OK': (r) => r.timings.duration < 30000,
        'DB: has results': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.resultCount !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    dbLatency.add(res.timings.duration);
    errorRate.add(!success);
    if (!success) dbErrors.add(1);

    sleep(Math.random() * 1 + 0.5); // 0.5-1.5 seconds think time
}

// Mixed load scenario - realistic user behavior
export function mixedLoad() {
    const rand = Math.random();

    // 40% CPU operations
    if (rand < 0.4) {
        const duration = Math.floor(Math.random() * 200) + 50; // 50-250ms
        const res = http.get(`${BASE_URL}/api/cpu?ms=${duration}`, {
            tags: { endpoint: 'cpu', scenario: 'mixed' },
        });

        check(res, {
            'Mixed/CPU: status OK': (r) => r.status === 200,
        });

        cpuLatency.add(res.timings.duration);
        errorRate.add(res.status !== 200);
    }
    // 30% External calls
    else if (rand < 0.7) {
        const res = http.get(`${BASE_URL}/api/external`, {
            tags: { endpoint: 'external', scenario: 'mixed' },
        });

        const success = check(res, {
            'Mixed/External: status OK': (r) => r.status === 200 || r.status === 503, // 503 is expected
        });

        externalLatency.add(res.timings.duration);
        errorRate.add(!success);
        if (res.status === 503) externalErrors.add(1);
    }
    // 30% DB operations
    else {
        const res = http.get(`${BASE_URL}/api/db/search`, {
            tags: { endpoint: 'db', scenario: 'mixed' },
        });

        const success = check(res, {
            'Mixed/DB: status OK': (r) => r.status === 200,
        });

        dbLatency.add(res.timings.duration);
        errorRate.add(!success);
        if (!success) dbErrors.add(1);
    }

    sleep(Math.random() * 0.5); // 0-0.5 seconds think time
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'load/summary.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const indent = options.indent || '';
    const enableColors = options.enableColors || false;

    let summary = '\n';
    summary += `${indent}================== LOAD TEST SUMMARY ==================\n`;
    summary += `${indent}Total requests: ${data.metrics.http_reqs?.values?.count || 0}\n`;
    summary += `${indent}Request rate: ${data.metrics.http_reqs?.values?.rate?.toFixed(2) || 0} req/s\n`;
    summary += `${indent}Failed requests: ${data.metrics.http_req_failed?.values?.rate?.toFixed(4) || 0}\n`;
    summary += `${indent}\nLatency:\n`;
    summary += `${indent}  p50: ${data.metrics.http_req_duration?.values['p(50)']?.toFixed(2) || 0} ms\n`;
    summary += `${indent}  p95: ${data.metrics.http_req_duration?.values['p(95)']?.toFixed(2) || 0} ms\n`;
    summary += `${indent}  p99: ${data.metrics.http_req_duration?.values['p(99)']?.toFixed(2) || 0} ms\n`;
    summary += `${indent}=======================================================\n\n`;

    return summary;
}
