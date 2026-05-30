import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Load Test Configurations
export const options = {
    stages: [
        { duration: '15s', target: 50 },  // Ramp up from 1 to 50 virtual users (VUs)
        { duration: '30s', target: 200 }, // Sustained high load at 200 virtual users (simulates 500+ RPS)
        { duration: '15s', target: 0 },   // Ramp down to 0 VUs
    ],
    thresholds: {
        http_req_failed: ['rate<0.15'], // Less than 15% failed requests (excluding intentional rate limits)
        http_req_duration: ['p(95)<100'], // 95% of requests must complete under 100ms
    },
};

const BASE_URL = 'http://localhost:8080/api/v1/notifications';

export default function () {
    // 1. Generate unique transactional payload to bypass Redis Idempotency checks
    const txId = uuidv4();
    
    // 2. Rotate recipient ID to simulate real-world user distribution
    // Rotating across 1000 users keeps sliding window checks clear of spam triggers
    const recipientNum = Math.floor(Math.random() * 1000);
    const recipientId = `user_${recipientNum}`;

    const payload = JSON.stringify({
        transactionId: txId,
        recipientId: recipientId,
        channel: 'EMAIL',
        destination: `user_${recipientNum}@example.com`,
        subject: 'Real-Time Alert Dispatch',
        message: 'Security warning: A new login was detected from your account.'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 3. Dispatch POST request
    const response = http.post(BASE_URL, payload, params);

    // 4. Validate output
    // Both 202 (Accepted) and 429 (Too Many Requests - due to simulated rate limits) are valid semantic states
    check(response, {
        'status is 202 or 429': (r) => r.status === 202 || r.status === 429,
        'response time P95 < 100ms': (r) => r.timings.duration < 100,
    });

    // Pause briefly between virtual user iterations to simulate natural client pacing
    sleep(0.05); // 50ms pacing
}
