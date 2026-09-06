import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 에러율 측정을 위한 커스텀 메트릭
export const errorRate = new Rate('errors');

// 부하 단계(Ramp-up) 및 목표 지표 설정
export const options = {
    stages: [
        // 1. Smoke Test (VU 1 ~ 5)
        { duration: '30s', target: 5 },

        // 2. Load Test (VU 10 -> 50 -> 100 -> 150)
        { duration: '1m', target: 10 },
        { duration: '3m', target: 10 },
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '1m', target: 150 },
        { duration: '3m', target: 150 },

        // 3. Stress Test (VU 200 이상)
        { duration: '1m', target: 200 },
        { duration: '3m', target: 200 },

        // 종료 (Ramp-down)
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'], // p95 < 200ms, p99 < 500ms
        errors: ['rate<0.01'],                          // 에러율 < 1%
    },
};

const BASE_URL = 'http://localhost:8080/api/docs';
const TOTAL_DOCS = 100000; // 사전 적재된 더미 데이터 수

export default function () {
    // 1 ~ 100 사이의 난수를 발생시켜 요청 비율 분기
    const rand = Math.random() * 100;
    let res;

    if (rand < 70) {
        // 1. 목록 페이징 조회 (70%) - 0 ~ 5000 페이지 임의 조회
        const page = Math.floor(Math.random() * 5000);
        res = http.get(`${BASE_URL}?page=${page}`);
        check(res, { 'get_docs status is 200': (r) => r.status === 200 });

    } else if (rand < 90) {
        // 2. 단건 상세 조회 (20%) - 1 ~ 10만번 ID 임의 조회
        const id = Math.floor(Math.random() * TOTAL_DOCS) + 1;
        res = http.get(`${BASE_URL}/${id}`);
        check(res, { 'get_doc_by_id status is 200': (r) => r.status === 200 });

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
        const id = Math.floor(Math.random() * TOTAL_DOCS) + 1;
        const payload = JSON.stringify({
            title: `Updated Title ${Date.now()}`,
            content: 'Updated performance test content...',
        });
        const params = { headers: { 'Content-Type': 'application/json' } };
        res = http.put(`${BASE_URL}/${id}`, payload, params);
        check(res, { 'update_doc status is 200': (r) => r.status === 200 });

    } else {
        // 5. 게시글 삭제 (2%)
        const id = Math.floor(Math.random() * TOTAL_DOCS) + 1;
        res = http.del(`${BASE_URL}/${id}`);
        check(res, { 'delete_doc status is 200': (r) => r.status === 200 });
    }

    // 요청 결과가 200이 아니면 에러로 처리
    errorRate.add(res.status !== 200);

    // 사용자 행동 간격 (0.1초 대기)
    sleep(0.1);
}