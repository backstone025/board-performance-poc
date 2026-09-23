# Spring Boot Backend Engineering Lab & PoC

> 단순 기능 구현을 넘어 DB 커넥션 병목 해소, 인프라 Scale-Out, 캐시 레이어 및 Eviction Policy 검증까지 
> 백엔드 성능 최적화 과정을 정량적 지표(Prometheus/Grafana/k6)로 검증하는 프로젝트입니다.

---

## 📌 주요 실험 로드맵 및 진행 상황

### 1. 기본 서버 성능 최적화 Sprint (`2026.09.05 ~ 진행 중`)
- [x] **k6 부하 테스트 환경 구축:** 베이스라인(No-Cache) 측정 및 DB CPU/Connection I/O 병목 확인
- [x] **HikariCP Connection Pool 최적화:** Pool Size 튜닝을 통한 대기열 해소 및 최적 Pool Size(20) 도출
- [x] **인프라 Scalability 검증:** WAS Scale-Up vs Nginx 기반 Scale-Out 비교 (WAS 계층 CPU 병목 완전 해소)
- [x] **DB Bottleneck Offloading (Test C1):** Local(Caffeine) vs Remote(Redis) Cache 비교 (TPS 5.4배 폭증, p95 24ms 달성)
- [x] **Cache Hit Rate 변동성 검증 (Test C2):** 적중률 단계별 DB 부하 감축 추적 및 목표 성능(`p95 < 200ms`) 달성 임계 적중률(80%) 도출
- [x] **유한 메모리 시나리오 & Eviction Policy 검증 (Test C3):** 16MB 제한 조건 하 Pareto(80:20) 트래픽 대상 삭제 정책(LRU vs LFU vs Random) 비교 (LRU 최적 검증)
- [ ] **응답 데이터 압축 & GC/메모리 관측 (진행 예정):** HTTP 응답 압축 적용에 따른 Network Payload 감축 및 JVM Heap/GC 영향 분석

---

## 🛠 Tech Stack & Tools

- **Application:** Java 21, Spring Boot 3.x, Spring Data JPA, Micrometer (Actuator)
- **Database Layer:** MySQL 8.0, HikariCP
- **Cache Layer:** Redis 7.0 (Remote Cache), Caffeine Cache (Local Cache)
- **Web Server / Load Balancer:** Nginx
- **Load Test:** k6 (CLI, Zipfian/Pareto Workload Generator)
- **Monitoring & Observability:** Prometheus, Grafana, Redis Exporter (9121)
- **DevOps / Environment:** Docker, Docker Compose, macOS (M-series)
