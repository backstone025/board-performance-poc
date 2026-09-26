import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const errorRate = new Rate('errors');

export const options = {
    stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 50 },
        { duration: '3m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<200'],
        errors: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080/api/docs';

export default function () {
    const params = {
        headers: { 'Accept-Encoding': 'gzip, deflate' },
    };

    const res = http.get(`${BASE_URL}?page=1`, params);
    check(res, { 'get_docs status is 200': (r) => r.status === 200 });

    errorRate.add(res.status !== 200);
    sleep(0.1);
}