#	[TEST] DB 설계 및 쿼리 최적화 : Offset vs Cursor 페이징의 깊이별 I/O 병목, CUD 정합성(Consistency) 한계 및 커서 키 카디널리티별 탐색 비용 검증
# 	Test C : Cursor 키의 카디널리티 및 조건절 구성에 따른 비용 분석
#	| 2026-11-04 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# 0. Test Setting
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
DROP INDEX idx_created_at ON document;
DROP INDEX idx_created_at_id ON document;

-- Case B용 단독 인덱스
CREATE INDEX idx_created_at ON document (created_at DESC);

-- Case C용 복합 커서 인덱스 (Covering & Order 보장)
CREATE INDEX idx_created_at_id ON document (created_at DESC, id DESC);

-- created_at 중복 데이터 확인 
-- 초 단위 중복이 발생한 created_at 확인 (예: 5~10건 이상 중복된 날짜 추출)
SELECT created_at, COUNT(*) 
FROM document 
GROUP BY created_at 
HAVING COUNT(*) > 1 
LIMIT 5;

# 1. Case A 
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 25000 
ORDER BY id DESC 
LIMIT 10;

# 2. Case B
EXPLAIN ANALYZE
SELECT * FROM document IGNORE INDEX (idx_created_at_id)
WHERE created_at <= '2026-11-03 15:50:22' 
ORDER BY created_at DESC 
LIMIT 10;

# 3. Case C
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE (created_at = '2026-11-03 15:50:22' AND id < 25000) 
   OR (created_at < '2026-11-03 15:50:22') 
ORDER BY created_at DESC, id DESC 
LIMIT 10;