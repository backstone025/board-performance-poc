#	[TEST] DB 설계 및 쿼리 최적화 : Offset vs Cursor 페이징의 깊이별 I/O 병목, CUD 정합성(Consistency) 한계 및 커서 키 카디널리티별 탐색 비용 검증
# 	Test D : Total Count 쿼리 처리 방식별 Latency 및 RTT 비교
#	| 2026-11-04 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# 1. Case A : Traditional Offset + Full Clustered Index Count
-- 1. 페이징 쿼리
EXPLAIN ANALYZE
SELECT * FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 40000;

-- 2. Total Count 쿼리 (전체 스캔)
EXPLAIN ANALYZE
SELECT COUNT(*) FROM document;

# 2. Case B : Cursor + Covered Count
-- 1. Cursor 페이징 쿼리
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 25000 
ORDER BY id DESC 
LIMIT 10;

-- 2. Covered Count 쿼리 (PK Index Only Scan)
EXPLAIN ANALYZE
SELECT COUNT(id) FROM document;

# 3. Case C : Cursor + No Count
-- 11개를 읽어 Next Page 존재 여부 판단
EXPLAIN ANALYZE
SELECT * FROM document 
WHERE id < 25000 
ORDER BY id DESC 
LIMIT 11;