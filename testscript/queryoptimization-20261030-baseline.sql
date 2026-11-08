#	[TEST] DB 설계 및 쿼리 최적화 : B+ Tree 인덱스 구조별 Scan 범위, Table Access 및 CUD 관리 비용 검증 테스트 스크립트
# 	Baseline 분석
#	| 2026-10-30 작성

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

#	1. 단일 인덱스 생성
-- 낮은 카디널리티 단일 인덱스 생성
CREATE INDEX idx_category ON document(category);

-- 높은 카디널리티 단일 인덱스 생성
CREATE INDEX idx_writer ON document(writer_id);

#	2. Full Table Scan(인덱스 미적용) 측정
EXPLAIN ANALYZE
SELECT * 
FROM document 
WHERE status = 'PUBLISHED';

#	3. 낮은 카디널리티 인덱스(idx_category) 측정 
EXPLAIN ANALYZE
SELECT * 
FROM document 
WHERE category = 'FREE' AND status = 'PUBLISHED';
-- 인덱스 미적용 시 
EXPLAIN ANALYZE
SELECT *
FROM document IGNORE INDEX (idx_category)
WHERE category = 'FREE' AND status = 'PUBLISHED';

#	4. 높은 카디널리티 인덱스(idx_writer) 측정 
EXPLAIN ANALYZE
SELECT * 
FROM document 
WHERE writer_id = 500;
-- 인덱스 미적용 시
EXPLAIN ANALYZE
SELECT *
FROM document IGNORE INDEX (idx_writer)
WHERE writer_id = 500;