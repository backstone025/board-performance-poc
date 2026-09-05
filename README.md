# Spring Boot Backend Engineering Lab & PoC

> 단순 기능 구현을 넘어 서버 성능 최적화, DB 쿼리 개선, 비동기 아키텍쳐 등
> 실무 백엔드 핵심 기술을 정량적으로 검증하고 기록하는 실험 프로젝트입니다.

## 주요 실험 로드맵

### 1. 서버 성능 개선 기초 `(2026.09.05 ~ )`
- [ ] k6 부하 테스트 환경 구축 및 기본 베이스라인 측정
- [ ] Scale-up / Scale-out에 따른 병목 지점 및 확장 검증
- [ ] DB 커넥션 풀(HikariCP) 최적화
- [ ] 서버 캐시(Caffeine/Redis) 도입
- [ ] 정적 자원 및 캐시/CDN 적용
- [ ] 대기 처리 및 지연 시간 감소 실험

## Tech Stack & Tools
- App: Java 21, Spring Boot, Spring Data JPA
- Database: MySQL
- Load Test: k6
- Monitoring: Prometheus, Grafana (혹은 Pinpoint)
