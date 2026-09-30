import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const errorRate = new Rate('errors');

export const options = {
    stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 10 },
        { duration: '3m', target: 10 },
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '1m', target: 150 },
        { duration: '3m', target: 150 },
        { duration: '1m', target: 200 },
        { duration: '3m', target: 200 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'],
        errors: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080/api/docs';
const TOTAL_DOCS = 100000;
const PAGE_SIZE = 20;
const MAX_PAGES = TOTAL_DOCS / PAGE_SIZE; // 5000

/**
 * Zipfian (Pareto 80:20) 난수 분포 생성기
 * - 80% 확률로 상위 20% (Hotspot) 범위에서 난수 생성
 * - 20% 확률로 하위 80% (Cold) 범위에서 난수 생성
 */
function getZipfianRank(max, alpha = 0.8) {
    if (Math.random() < alpha) {
        return Math.floor(Math.random() * (max * (1 - alpha))); // 상위 20% (Hotspot)
    } else {
        const coldStart = Math.floor(max * (1 - alpha));
        return coldStart + Math.floor(Math.random() * (max * alpha)); // 하위 80% (Cold)
    }
}

export default function () {
    const rand = Math.random() * 100;
    let res;

    if (rand < 70) {
        // 1. 목록 페이징 조회 (70%) - 전체 5,000 페이지 중 Pareto 80:20 요청
        const page = getZipfianRank(MAX_PAGES);
        res = http.get(`${BASE_URL}?page=${page}`);
        check(res, { 'get_docs status is 200': (r) => r.status === 200 });

    } else if (rand < 90) {
        // 2. 단건 상세 조회 (20%) - 전체 100,000건 ID 중 Pareto 80:20 요청
        const id = getZipfianRank(TOTAL_DOCS) + 1;
        res = http.get(`${BASE_URL}/${id}`);
        check(res, { 'get_doc status is 200': (r) => r.status === 200 });

    } else if (rand < 95) {
        // 3. 게시글 생성 (5%)
        const payload = JSON.stringify({
            title: `Test Title ${Date.now()}`,
            content: 'Performance test dummy content text...',
        });
        const params = { headers: { 'Content-Type': 'application/json' } };
        res = http.post(BASE_URL, payload, params);
        check(res, { 'create_doc status is 200': (r) => r.status === 200 });

    } else if (rand < 98) {
        // 4. 게시글 수정 (3%) - Pareto 분포 기반 ID 수정
        const id = getZipfianRank(TOTAL_DOCS) + 1;
        const payload = JSON.stringify({
            title: `Updated Title ${Date.now()}`,
            content: 'Updated performance test content...',
        });
        const params = { headers: { 'Content-Type': 'application/json' } };
        res = http.put(`${BASE_URL}/${id}`, payload, params);
        check(res, { 'update_doc status is 200': (r) => r.status === 200 });

    } else {
        // 5. 게시글 삭제 (2%) - Pareto 분포 기반 ID 삭제
        const id = getZipfianRank(TOTAL_DOCS) + 1;
        res = http.del(`${BASE_URL}/${id}`);
        check(res, { 'delete_doc status is 200': (r) => r.status === 200 });
    }

    // HTTP 200 응답이 아닐 경우만 에러로 집계
    errorRate.add(res.status !== 200);

    sleep(0.1);
}