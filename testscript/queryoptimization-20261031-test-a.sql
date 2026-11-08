#	[TEST] DB 설계 및 쿼리 최적화 : B+ Tree 인덱스 구조별 Scan 범위, Table Access 및 CUD 관리 비용 검증 테스트 스크립트
# 	Test A : 단일 인덱스와 복합 인덱스 비교
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
-- 낮은 카디널리티 단일 인덱스 생성
CREATE INDEX idx_category ON document(category);

-- 높은 카디널리티 단일 인덱스 생성
CREATE INDEX idx_writer ON document(writer_id);

-- 카디널리티 낮음 -> 높음 순서 복합 인덱스
CREATE INDEX idx_cat_writer ON document(category, writer_id);

-- 카디널리티 높음 -> 낮음 순서 복합 인덱스
CREATE INDEX idx_writer_cat ON document(writer_id, category);

#	2. 단일 인덱스와 복합 인덱스 스캔 범위 비교 
-- 단일 복합 인덱스 
EXPLAIN ANALYZE
SELECT *
FROM document IGNORE INDEX(idx_cat_writer, idx_writer_cat)
WHERE category = 'FREE' AND writer_id = 500;

-- 높은 순서 복합 인덱스 
EXPLAIN ANALYZE
SELECT *
FROM document USE INDEX(idx_cat_writer)
WHERE category = 'FREE' AND writer_id = 500;

-- 낮은 순서 복합 인덱스 
EXPLAIN ANALYZE
SELECT *
FROM document USE INDEX(idx_writer_cat)
WHERE category = 'FREE' AND writer_id = 500;

-- Leftmost Prefix Rule 위배
EXPLAIN ANALYZE
SELECT *
FROM document USE INDEX(idx_cat_writer)
WHERE  writer_id = 500;

EXPLAIN ANALYZE
SELECT *
FROM document USE INDEX(idx_writer)
WHERE  writer_id = 500;
