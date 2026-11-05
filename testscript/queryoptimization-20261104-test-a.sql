#	[TEST] DB 설계 및 쿼리 최적화 : Offset vs Cursor 페이징의 깊이별 I/O 병목, CUD 정합성(Consistency) 한계 및 커서 키 카디널리티별 탐색 비용 검증
# 	Test A : 페이지 깊이(Depth)에 따른 Latency 및 스캔 행 수 측정
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

-- PRIMARY 제외한 모든 인덱스 제거
DROP INDEX idx_cat_status ON document;
DROP INDEX idx_covering ON document;

-- dummy data 50,000건 생성
CALL InsertDummyDocuments(50000);

-- dummy data(document 레코드) 초기화 
TRUNCATE TABLE document;

-- 전체 데이터 건수 및 샘플 확인
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

-- 컬럼별 카디널리티(유니크 값 개수) 확인
SELECT 
    COUNT(DISTINCT category) AS cat_cardinality,
    COUNT(DISTINCT status) AS stat_cardinality,
    COUNT(DISTINCT writer_id) AS writer_cardinality
FROM document;

# 1. offset 0 : 첫 페이지 
-- BaseLine : Full Scan pagination
EXPLAIN ANALYZE
SELECT * FROM document 
IGNORE INDEX (PRIMARY) ORDER BY id DESC 
LIMIT 10 OFFSET 0;

-- Case A : Offset-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 0;

-- Case B : Cursor-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 500001
ORDER BY id DESC 
LIMIT 10;

# 2. offset 1,000 : 얕은 페이지 

-- BaseLine : Full Scan pagination
EXPLAIN ANALYZE
SELECT * FROM document 
IGNORE INDEX (PRIMARY) ORDER BY id DESC 
LIMIT 10 OFFSET 1000;

-- Case A : Offset-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 1000;

-- Case B : Cursor-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 49001
ORDER BY id DESC 
LIMIT 10;

# 3. offset 10,000 : 중간 페이지 

-- BaseLine : Full Scan pagination
EXPLAIN ANALYZE
SELECT * FROM document 
IGNORE INDEX (PRIMARY) ORDER BY id DESC 
LIMIT 10 OFFSET 10000;

-- Case A : Offset-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 10000;

-- Case B : Cursor-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 40001
ORDER BY id DESC 
LIMIT 10;

# 4. offset 40,000 : 깊은 페이지 

-- BaseLine : Full Scan pagination
EXPLAIN ANALYZE
SELECT * FROM document 
IGNORE INDEX (PRIMARY) ORDER BY id DESC 
LIMIT 10 OFFSET 40000;

-- Case A : Offset-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 40000;

-- Case B : Cursor-based pagination
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 10001
ORDER BY id DESC 
LIMIT 10;