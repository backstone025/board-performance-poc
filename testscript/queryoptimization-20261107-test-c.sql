#	[TEST] DB 설계 및 쿼리 최적화 : 인덱스 이탈(Index Invalidation) 10가지 유발 케이스 분석 및 SARGable 쿼리 리팩토링 PoC
# 	Test C 옵티마이저 비용 계산 및 선택도 임계점 (Optimizer Cost & Selectivity)
#	| 2026-11-07 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# Test Setting

-- 인덱스 이름과 묶여있는 컬럼 구성만 한눈에 보기 (information_schema)
SELECT 
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS indexed_columns,
    non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'document'
GROUP BY index_name, non_unique;

-- 인덱스 추가 
CREATE INDEX idx_content ON document (content(255));
CREATE INDEX idx_writer_id ON document (writer_id);
CREATE INDEX idx_created_at ON document (created_at);

DROP INDEX idx_category ON document;
DROP INDEX idx_category_status ON document;
DROP INDEX idx_status ON document;
DROP INDEX idx_title ON document;
DROP INDEX idx_view_count ON document;

# [Case 1] IS NULL / IS NOT NULL 조건과 선택도(Selectivity) 변화
-- IS NULL (선택도 1% 미만 -> Index Scan 유도)
EXPLAIN ANALYZE
SELECT * FROM document WHERE content IS NULL;

-- IS NOT NULL (선택도 99% 이상 -> Full Scan 강제)
EXPLAIN ANALYZE
SELECT * FROM document WHERE content IS NOT NULL;

# [Case 2] IN 연산자 항목 과다에 따른 Random Read 오버헤드
-- Anti-pattern (대량 IN 항목 전달)
EXPLAIN ANALYZE
SELECT * FROM document
WHERE writer_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96,
97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130,
131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163,
164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196,
197, 198, 199, 200);

-- SARGable (범위 조건 또는 임시 테이블 JOIN 분할)
EXPLAIN ANALYZE
SELECT * FROM document
WHERE writer_id >= 1 AND writer_id <= 200;

# [Case 3] 대량 데이터 조회 시 Full Table Scan 자동 전환 (Selectivity Threshold)
-- 소량 조회 
EXPLAIN ANALYZE
SELECT * FROM document
WHERE created_at >= '2026-11-01 00:00:00' AND created_at < '2026-11-05 00:00:00';

-- 대량 조회 
EXPLAIN ANALYZE
SELECT * FROM document
WHERE created_at >= '2025-11-01 00:00:00' AND created_at < '2026-11-05 00:00:00';
