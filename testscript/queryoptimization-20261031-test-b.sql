#	[TEST] DB 설계 및 쿼리 최적화 : B+ Tree 인덱스 구조별 Scan 범위, Table Access 및 CUD 관리 비용 검증 테스트 스크립트
# 	Test B : 복합 인덱스 커버링 인덱스 비교 및 커버링 깨짐 현상 
#	| 2026-10-31 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

-- 기본적인 상세 인덱스 조회
SHOW INDEX FROM document;
-- 인덱스 이름과 묶여있는 컬럼 구성만 한눈에 보기 (information_schema)
SELECT 
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS indexed_columns,
    non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'document'
GROUP BY index_name, non_unique;
-- 인덱스 삭제 
DROP INDEX idx_category ON document;
DROP INDEX idx_writer ON document;

#	1. 인덱스 생성

-- 카디널리티 낮음 -> 높음 순서 복합 인덱스
CREATE INDEX idx_cat_writer ON document(category, writer_id);

-- 커버링 인덱스
CREATE INDEX idx_covering ON document(category, status, writer_id, created_at);

#	2. 복합 인덱스와 커버링 인덱스 비교
-- 복합 인덱스
EXPLAIN ANALYZE
SELECT writer_id, created_at
FROM document USE INDEX(idx_cat_writer)
WHERE category = 'FREE' AND status = 'PUBLISHED';

-- 커버링 인덱스 
EXPLAIN ANALYZE
SELECT writer_id, created_at
FROM document USE INDEX(idx_covering)
WHERE category = 'FREE' AND status = 'PUBLISHED';

#	3. 대용량 컬럼 포함 시 커버링 깨짐 현상 
EXPLAIN ANALYZE
SELECT writer_id, created_at, content
FROM document USE INDEX(idx_covering)
WHERE category = 'FREE' AND status = 'PUBLISHED';

