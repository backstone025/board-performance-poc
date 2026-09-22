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

const TARGET_HIT_RATE = __ENV.HIT_RATE ? parseFloat(__ENV.HIT_RATE) : 1.0;

function getZipfianRank(max, alpha = 0.8) {
    if (Math.random() < alpha) {
        return Math.floor(Math.random() * (max * (1 - alpha))); // 상위 20% (Hotspot)
    } else {
        const coldStart = Math.floor(max * (1 - alpha));
        return coldStart + Math.floor(Math.random() * (max * alpha)); // 하위 80% (Cold)
    }
}

/**
 * 적중률 제어를 위한 페이지 번호 생성
 */
function getPageNumberByHitRate() {
    const hotspotPages = Math.floor(MAX_PAGES * 0.2); // 상위 20% 페이지 (1000)

    if (Math.random() < TARGET_HIT_RATE) {
        // Hotspot 영역 요청 (0 ~ 999 페이지) -> 반복 요청으로 Cache HIT 유발
        return getZipfianRank(hotspotPages);
    } else {
        // Cold 영역 요청 (1000 ~ 4999 페이지) -> 매번 랜덤 요청으로 Cache MISS 유발
        return hotspotPages + Math.floor(Math.random() * (MAX_PAGES - hotspotPages));
    }
}

/**
 * 적중률 제어를 위한 단건 ID 생성 (실제 존재하는 ID 범위 내 지정)
 */
function getDocIdByHitRate() {
    const hotspotDocs = Math.floor(TOTAL_DOCS * 0.2); // 상위 20% 문서 (20000)

    if (Math.random() < TARGET_HIT_RATE) {
        // Hotspot 영역 ID (1 ~ 20000) -> 반복 요청으로 Cache HIT 유발
        return getZipfianRank(hotspotDocs) + 1;
    } else {
        // DB에 존재하는 Cold 영역 ID (20001 ~ 100000) -> Cache MISS 및 200 OK 보장
        return (hotspotDocs + 1) + Math.floor(Math.random() * (TOTAL_DOCS - hotspotDocs));
    }
}

export default function () {
    const rand = Math.random() * 100;
    let res;

    if (rand < 70) {
        // 1. 목록 페이징 조회 (70%)
        const page = getPageNumberByHitRate();
        res = http.get(`${BASE_URL}?page=${page}`);
        check(res, { 'get_docs status is 200': (r) => r.status === 200 });

    } else if (rand < 90) {
        // 2. 단건 상세 조회 (20%)
        const id = getDocIdByHitRate();
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
        // 4. 게시글 수정 (3%)
        const id = getZipfianRank(TOTAL_DOCS) + 1;
        const payload = JSON.stringify({
            title: `Updated Title ${Date.now()}`,
            content: 'Updated performance test content...',
        });
        const params = { headers: { 'Content-Type': 'application/json' } };
        res = http.put(`${BASE_URL}/${id}`, payload, params);
        check(res, { 'update_doc status is 200': (r) => r.status === 200 });

    } else {
        // 5. 게시글 삭제 (2%)
        const id = getZipfianRank(TOTAL_DOCS) + 1;
        res = http.del(`${BASE_URL}/${id}`);
        check(res, { 'delete_doc status is 200': (r) => r.status === 200 });
    }

    // HTTP 200 응답이 아닐 경우만 에러로 집계
    errorRate.add(res.status !== 200);

    sleep(0.1);
}